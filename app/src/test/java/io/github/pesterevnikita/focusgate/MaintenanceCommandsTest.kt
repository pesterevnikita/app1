package io.github.pesterevnikita.focusgate

import io.github.pesterevnikita.focusgate.maintenance.*
import io.github.pesterevnikita.focusgate.data.*
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.policy.Target
import org.junit.Assert.*
import org.junit.Test

class MaintenanceCommandsTest {
    private val state = AppState(policy=PolicySnapshot(blockers=listOf(Blocker("r","Rule",targets=listOf(Target.App("one.app")),quotaGroupId="g")),groups=listOf(QuotaGroup("g",QuotaPeriod.HOUR,900000))))
    @Test fun parserRejectsMissingOverflowUnknownAndAmbiguousInputs() {
        for(args in listOf(emptyMap(),mapOf("groupId" to "g","allowanceMinutes" to "999999999999999999999"),mapOf("groupId" to "g","allowanceMinutes" to "1","period" to "WEEK"),mapOf("groupId" to "g","allowanceMinutes" to "1","password" to "x"))) {
            assertTrue(runCatching{MaintenanceCommands.plan("quota-set",args,state)}.isFailure)
        }
        assertTrue(runCatching{MaintenanceCommands.plan("rule-set",mapOf("ruleId" to "r","enabled" to "yes"),state)}.isFailure)
        assertTrue(runCatching{MaintenanceCommands.plan("release",emptyMap(),state)}.isFailure)
    }
    @Test fun lockedQuotaWeakeningFailsButTargetAdditionRetainsLedgerAndCredentials() {
        val locked=state.copy(session=LockedSession("s",ReleasePolicy.TIMER,60000,60000,"b","UTC"),ledger=LedgerState(consumed=mapOf("g" to 5000)))
        assertTrue(runCatching{MaintenanceCommands.plan("quota-set",mapOf("groupId" to "g","allowanceMinutes" to "16"),locked)}.isFailure)
        assertTrue(runCatching{MaintenanceCommands.plan("rule-set",mapOf("ruleId" to "r","enabled" to "false"),locked)}.isFailure)
        val command=MaintenanceCommands.plan("target-add",mapOf("ruleId" to "r","kind" to "host","value" to "example.com"),locked) as MaintenanceCommand.Policy
        val next=StateGuard.policyChange(locked,command.value,command.expectedRevision)
        assertEquals(locked.ledger,next.ledger); assertEquals(locked.session,next.session)
        assertEquals(2,next.policy.blockers.single().targets.size)
        assertTrue(runCatching{StateGuard.policyChange(locked.copy(policy=locked.policy.copy(revision=1)),command.value,command.expectedRevision)}.isFailure)
    }
    @Test fun timerIsShortAndHasNoReleaseOrPasswordSurface() {
        assertEquals(60000L,(MaintenanceCommands.plan("lock-timer",mapOf("minutes" to "1"),state) as MaintenanceCommand.Lock).durationMillis)
        for(value in listOf("0","6","-1","1.5")) assertTrue(runCatching{MaintenanceCommands.plan("lock-timer",mapOf("minutes" to value),state)}.isFailure)
        assertTrue(runCatching{MaintenanceCommands.plan("lock-timer",mapOf("minutes" to "1","password" to "x"),state)}.isFailure)
    }
    @Test fun onlyShellCallerIsAcceptedIncludingUnsupportedOperations() {
        MaintenanceCaller.requireShell(2000)
        for(uid in listOf(0,1000,10000,102000)) assertTrue(runCatching{MaintenanceCaller.requireShell(uid)}.isFailure)
    }
    @Test fun protectionsAndCapDisablingRespectLockBoundary() {
        val locked=state.copy(session=LockedSession("s",ReleasePolicy.TIMER,60000,60000,"b","UTC"))
        assertTrue(runCatching{MaintenanceCommands.plan("protections-set",mapOf("recents" to "true"),locked)}.isFailure)
        val capState=locked.copy(policy=locked.policy.copy(groups=listOf(locked.policy.groups.single().copy(continuousCapMillis=60000))))
        assertTrue(runCatching{MaintenanceCommands.plan("quota-set",mapOf("groupId" to "g","allowanceMinutes" to "15","capMinutes" to "0"),capState)}.isFailure)
    }
    @Test fun numericUnitsPreservePeriodAndNoAdditionSessionRejectsTargets() {
        val command=MaintenanceCommands.plan("quota-set",mapOf("groupId" to "g","allowanceMinutes" to "1","capMinutes" to "1","breakMinutes" to "2"),state) as MaintenanceCommand.Policy
        assertEquals(QuotaGroup("g",QuotaPeriod.HOUR,60000,60000,120000),command.value.groups.single())
        val locked=state.copy(session=LockedSession("s",ReleasePolicy.TIMER,60000,60000,"b","UTC",false))
        assertTrue(runCatching{MaintenanceCommands.plan("target-add",mapOf("ruleId" to "r","kind" to "app","value" to "two.app"),locked)}.isFailure)
        assertTrue(runCatching{MaintenanceCommands.plan("target-add",mapOf("ruleId" to "r","kind" to "regex","value" to "["),state)}.isFailure)
    }
    @Test fun capOnlyEditPreservesAllowanceAndPeriod() {
        val command=MaintenanceCommands.plan("quota-set",mapOf("groupId" to "g","capMinutes" to "1"),state) as MaintenanceCommand.Policy
        assertEquals(state.policy.groups.single().copy(continuousCapMillis=60000),command.value.groups.single())
        assertTrue(runCatching{MaintenanceCommands.plan("quota-set",mapOf("groupId" to "g"),state)}.isFailure)
    }
    @Test fun concurrentProtectionCommandsRejectStaleBaselineInsteadOfLosingAnEdit() {
        val first=MaintenanceCommands.plan("protections-set",mapOf("recents" to "true"),state) as MaintenanceCommand.Protections
        val second=MaintenanceCommands.plan("protections-set",mapOf("settingsMode" to "1"),state) as MaintenanceCommand.Protections
        val changed=StateGuard.settingsChange(state,first.value,first.expectedSettings)
        assertTrue(runCatching{StateGuard.settingsChange(changed,second.value,second.expectedSettings)}.isFailure)
        val retry=MaintenanceCommands.plan("protections-set",mapOf("settingsMode" to "1"),changed) as MaintenanceCommand.Protections
        val merged=StateGuard.settingsChange(changed,retry.value,retry.expectedSettings)
        assertTrue(merged.settings.recents); assertEquals(1,merged.settings.settingsMode)
        assertEquals(state.ledger,merged.ledger)
    }
    @Test fun importAcceptsNormalExportAndExplicitMergeWithoutPrivateState() {
        val json=ConfigurationTransfer.export(state)
        val replace=MaintenanceCommands.plan("config-import",mapOf("json" to json),state) as MaintenanceCommand.Import
        assertEquals(json,replace.json); assertFalse(replace.merge)
        assertTrue((MaintenanceCommands.plan("config-import",mapOf("json" to json,"merge" to "true"),state) as MaintenanceCommand.Import).merge)
        assertTrue(runCatching{MaintenanceCommands.plan("config-import",mapOf("json" to json,"merge" to "yes"),state)}.isFailure)
    }
    @Test fun importRejectsMalformedMissingOversizeAndLockedRequests() {
        val json=ConfigurationTransfer.export(state)
        val locked=state.copy(session=LockedSession("s",ReleasePolicy.TIMER,60000,60000,"b","UTC"))
        assertTrue(runCatching{MaintenanceCommands.plan("config-import",mapOf("json" to json),locked)}.isFailure)
        for(args in listOf(emptyMap(),mapOf("json" to "{bad"),mapOf("json" to json,"password" to "x"),mapOf("json" to json+" ".repeat(65536)))) {
            assertTrue(runCatching{MaintenanceCommands.plan("config-import",args,state)}.isFailure)
        }
    }
    @Test fun importTransportLimitCountsUtf8BytesAndAllowsNormalDocumentsAboveOneKilobyte() {
        val json=ConfigurationTransfer.export(state.copy(policy=state.policy.copy(blockers=state.policy.blockers.map{it.copy(message="x".repeat(500),targets=it.targets+(1..10).map{index->Target.Host("example$index.com")})})))
        assertTrue(json.length>1024)
        assertEquals("Import",MaintenanceCommands.plan("config-import",mapOf("json" to json),state)::class.simpleName)
        val oversized=ConfigurationTransfer.export(state.copy(policy=state.policy.copy(blockers=(1..80).map{state.policy.blockers.single().copy(id="r$it",message="я".repeat(400))})))
        assertTrue(oversized.length<65536); assertTrue(oversized.toByteArray(Charsets.UTF_8).size>65536)
        assertTrue(runCatching{MaintenanceCommands.plan("config-import",mapOf("json" to oversized),state)}.isFailure)
    }
}
