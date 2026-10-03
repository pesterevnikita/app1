package io.github.pesterevnikita.focusgate.maintenance

import io.github.pesterevnikita.focusgate.data.*
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.policy.Target

/** Typed requests contain configuration only; there is deliberately no credential or release command. */
sealed interface MaintenanceCommand {
    data class Read(val method: String): MaintenanceCommand
    data class Policy(val value: PolicySnapshot, val expectedRevision: Long): MaintenanceCommand
    data class Protections(val value: ProtectionSettings, val expectedSettings: ProtectionSettings): MaintenanceCommand
    data class Lock(val durationMillis: Long): MaintenanceCommand
    data class Import(val json: String, val merge: Boolean): MaintenanceCommand
}

/** Android's shell is UID 2000. Root, system, ordinary apps, and even our own UID are refused. */
object MaintenanceCaller {
    fun requireShell(uid: Int) { if(uid != 2000) throw SecurityException("ADB shell caller required") }
}

/** Pure parsing/planning keeps hostile extras and lock-boundary tests independent of Android Binder. */
object MaintenanceCommands {
    fun plan(method: String, args: Map<String,String>, state: AppState): MaintenanceCommand {
        val allowed = when(method) {
            "status", "config", "refresh" -> emptySet()
            "config-import" -> setOf("json","merge")
            "quota-set" -> setOf("groupId","allowanceMinutes","period","capMinutes","breakMinutes")
            "rule-set" -> setOf("ruleId","enabled")
            "target-add" -> setOf("ruleId","kind","value")
            "protections-set" -> setOf("settingsMode","recents","networkExceptions")
            "lock-timer" -> setOf("minutes")
            else -> error("Unsupported maintenance method")
        }
        require(args.keys.all{it in allowed}) { "Unknown argument" }
        require(args.all{(key,value)->if(method=="config-import" && key=="json")value.toByteArray(Charsets.UTF_8).size<=65536 else value.length<=1024}) { "Argument too long" }
        fun required(key: String)=args[key]?.takeIf{it.isNotBlank()} ?: error("Missing $key")
        fun number(key: String, range: LongRange): Long {
            val value=required(key).toLongOrNull() ?: error("Invalid $key")
            require(value in range) { "Out of range: $key" }; return value
        }
        fun boolean(key: String): Boolean = when(required(key)) { "true" -> true; "false" -> false; else -> error("Use true or false for $key") }
        fun policy(value: PolicySnapshot): MaintenanceCommand {
            // Early feedback only: AppStore repeats this against the latest durable state inside its transaction.
            StateGuard.policyChange(state,value,state.policy.revision)
            return MaintenanceCommand.Policy(value,state.policy.revision)
        }
        return when(method) {
            "status", "config", "refresh" -> MaintenanceCommand.Read(method)
            "config-import" -> {
                require(state.session==null) { "Import is unavailable while locked." }
                val json=required("json")
                // The shell transport is bounded more tightly than SAF; content validation is identical.
                ConfigurationTransfer.parseDocument(json)
                MaintenanceCommand.Import(json,if("merge" in args)boolean("merge") else false)
            }
            "lock-timer" -> MaintenanceCommand.Lock(number("minutes",1L..5L)*60000L)
            "quota-set" -> {
                val id=required("groupId"); val group=state.policy.groups.singleOrNull{it.id==id} ?: error("Unknown groupId")
                require(args.keys.any{it!="groupId"}) { "Specify a quota change" }
                val updated=group.copy(
                    allowanceMillis=if("allowanceMinutes" in args)number("allowanceMinutes",1L..1440L)*60000L else group.allowanceMillis,
                    period=args["period"]?.let{QuotaPeriod.entries.find{entry->entry.name==it} ?: error("Use HOUR or DAY")} ?: group.period,
                    continuousCapMillis=if("capMinutes" in args) number("capMinutes",0L..1440L).let{if(it==0L)null else it*60000L} else group.continuousCapMillis,
                    requiredBreakMillis=if("breakMinutes" in args) number("breakMinutes",1L..1440L)*60000L else group.requiredBreakMillis,
                )
                policy(state.policy.copy(groups=state.policy.groups.map{if(it.id==id)updated else it}))
            }
            "rule-set", "target-add" -> {
                val id=required("ruleId"); val rule=state.policy.blockers.singleOrNull{it.id==id} ?: error("Unknown ruleId")
                val updated=if(method=="rule-set") rule.copy(enabled=boolean("enabled")) else {
                    val target=when(required("kind")) { "app" -> Target.App(required("value")); "host" -> Target.Host(required("value")); "regex" -> Target.UrlRegex(required("value")); else -> error("Use app, host or regex") }
                    rule.copy(targets=(rule.targets+target).distinct())
                }
                policy(state.policy.copy(blockers=state.policy.blockers.map{if(it.id==id)updated else it}))
            }
            else -> {
                require(args.isNotEmpty()) { "Specify a protection" }
                val settings=state.settings.copy(settingsMode=if("settingsMode" in args)number("settingsMode",0L..2L).toInt() else state.settings.settingsMode,
                    recents=if("recents" in args)boolean("recents") else state.settings.recents,
                    networkExceptions=if("networkExceptions" in args)boolean("networkExceptions") else state.settings.networkExceptions)
                require(StateGuard.canUpdateSettings(state,settings)) { "Release Restricted Mode to change protections." }
                MaintenanceCommand.Protections(settings,state.settings)
            }
        }
    }
}
