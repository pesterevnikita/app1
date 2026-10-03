package io.github.pesterevnikita.focusgate.data
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.security.PasswordHash
import io.github.pesterevnikita.focusgate.policy.Target
import com.google.gson.Gson
import java.util.UUID
import java.time.*
import java.net.IDN
/** settingsMode: 0=off, 1=sensitive Settings screens, 2=all Settings (subject to network exceptions). */
data class ProtectionSettings(val settingsMode: Int = 0, val networkExceptions: Boolean = true, val recents: Boolean = false, val uninstallResistance: Boolean = false)
/** Entire private database document. Configuration export intentionally selects only policy/settings. */
data class AppState(val policy: PolicySnapshot = PolicySnapshot(), val ledger: LedgerState = LedgerState(), val session: LockedSession? = null, val password: PasswordHash? = null, val failures: Int = 0, val retryAfterUtcMillis: Long = 0, val settings: ProtectionSettings = ProtectionSettings(), val diagnostics: Boolean = false, val countdown: Boolean = false, val popup: Boolean = true)
object StateGuard {
    fun canUpdateSettings(state: AppState, settings: ProtectionSettings): Boolean = state.session==null || state.settings==settings
    /** Compare the editor's baseline inside the durable transaction, preventing concurrent partial edits from being lost. */
    fun settingsChange(state: AppState, settings: ProtectionSettings, expectedSettings: ProtectionSettings? = null): AppState {
        require(expectedSettings==null || state.settings==expectedSettings) { "Protections changed. Read the current settings and retry." }
        require(canUpdateSettings(state,settings)) { "Release Restricted Mode to change protections." }
        return state.copy(settings=settings)
    }
    /** Central guard used regardless of which UI action originated a policy edit. */
    fun policyChange(state: AppState, proposed: PolicySnapshot, expectedRevision: Long): AppState {
        require(state.policy.revision==expectedRevision) { "Configuration changed. Reopen the editor." }
        ConfigurationTransfer.validate(proposed)
        val session=state.session
        require(session==null || (session.allowRestrictiveAdditions && MutationGuard.isRestrictive(state.policy,proposed))) { "Restricted Mode allows only stronger rules." }
        return state.copy(policy=proposed.copy(revision=state.policy.revision+1))
    }
}
object PresetFactory {
    /** Ordinary editable starter rules with fresh IDs; nothing in enforcement special-cases these apps. */
    fun create(): PolicySnapshot {
        val group=UUID.randomUUID().toString()
        return PolicySnapshot(blockers=listOf(
            Blocker(UUID.randomUUID().toString(),"No YouTube or Instagram",targets=listOf(Target.App("com.google.android.youtube"),Target.App("com.instagram.android"))+listOf("youtube.com","youtu.be","youtube-nocookie.com","instagram.com").map{Target.Host(it)}),
            Blocker(UUID.randomUUID().toString(),"Shared distraction budget",targets=listOf("com.microsoft.emmx","com.android.chrome","org.telegram.messenger","ru.ozon.app.android").map{Target.App(it)},quotaGroupId=group)
        ),groups=listOf(QuotaGroup(group,QuotaPeriod.HOUR,900000)))
    }
}
data class ConfigurationDocument(val version: Int = 1, val policy: PolicySnapshot, val settings: ProtectionSettings = ProtectionSettings())
object ConfigurationTransfer {
    private val gson=Gson()
    fun readBounded(input: java.io.InputStream): ByteArray {
        // readNBytes is API 33 on Android. This bounded loop also works on Android 10.
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) {val count=input.read(buffer);if(count<0)break;require(output.size()+count<=1048576){"File exceeds 1 MB"};output.write(buffer,0,count)}
        return output.toByteArray()
    }
    /** Portable configuration only: excludes private password verifiers, counters, and active locks. */
    fun export(state: AppState): String = gson.toJson(ConfigurationDocument(policy=state.policy,settings=state.settings))
    /** Treat imported JSON as untrusted; size, version, and nested rule validation precede saving. */
    fun parseDocument(json: String): ConfigurationDocument {
        require(json.toByteArray(Charsets.UTF_8).size<=1048576) { "Configuration must be at most 1 MB." }
        val document=gson.fromJson(json,ConfigurationDocument::class.java) ?: error("Empty configuration")
        require(document.version==1) { "Unsupported configuration version" }
        validate(document.policy)
        require(document.settings.settingsMode in 0..2)
        return document
    }
    fun parse(json: String): PolicySnapshot = parseDocument(json).policy
    /** Keep imported/editor data within bounds understood by the policy engine and Android UI. */
    fun validate(policy: PolicySnapshot) {
        require(policy.blockers.size<=500 && policy.groups.size<=500)
        require(policy.blockers.map{it.id}.distinct().size==policy.blockers.size && policy.groups.map{it.id}.distinct().size==policy.groups.size)
        policy.groups.forEach {
            // Gson maps unknown/missing enums to null despite Kotlin's non-null declaration.
            require(QuotaPeriod.entries.contains(it.period)) { "Unknown quota period" }
            require(it.id.isNotBlank() && it.allowanceMillis in 1000L..86400000L && it.requiredBreakMillis in 1000L..86400000L)
            val cap=it.continuousCapMillis; require(cap==null || cap in 1000L..86400000L)
        }
        policy.blockers.forEach { rule ->
            require(rule.id.isNotBlank() && rule.name.isNotBlank() && rule.name.length<=120 && rule.message.length<=500)
            require(rule.targets.size in 1..500 && rule.schedule.days.all{it in 1..7})
            require(rule.quotaGroupId==null || policy.groups.any{it.id==rule.quotaGroupId})
            rule.schedule.startDate?.let{LocalDate.parse(it)}; rule.schedule.endDateExclusive?.let{LocalDate.parse(it)}
            val start=rule.schedule.startDate; val end=rule.schedule.endDateExclusive
            if(start!=null && end!=null) require(start<end)
            rule.schedule.windows.forEach{ LocalTime.parse(it.start); LocalTime.parse(it.end) }
            rule.targets.forEach { target -> require(target.value.isNotBlank() && target.value.length<=1024); when(target.kind) {
                "app" -> require(target.value.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
                "host" -> { val host=IDN.toASCII(target.value); require(host.contains('.') && !host.contains('/') && !host.contains(':') && !host.contains(' ')) }
                "regex" -> require(UrlMatcher.validPattern(target.value)) { "Invalid or unsupported linear-time regex" }
                else -> error("Unknown target type")
            } }
        }
    }
    /** Remap imported IDs together with quota references so a merge cannot collide with existing rules. */
    fun merge(current: PolicySnapshot, imported: PolicySnapshot): PolicySnapshot {
        val groupIds=imported.groups.associate{it.id to UUID.randomUUID().toString()}
        return current.copy(blockers=current.blockers+imported.blockers.map{it.copy(id=UUID.randomUUID().toString(),quotaGroupId=it.quotaGroupId?.let(groupIds::getValue))},groups=current.groups+imported.groups.map{it.copy(id=groupIds.getValue(it.id))})
    }
}
