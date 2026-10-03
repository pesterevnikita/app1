package io.github.pesterevnikita.focusgate
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.policy.Target
import io.github.pesterevnikita.focusgate.data.*
import io.github.pesterevnikita.focusgate.security.*
import io.github.pesterevnikita.focusgate.accessibility.*
import org.junit.Assert.*
import org.junit.Test

class CoreAppTest {
    @Test fun hiddenAndTransitioningAddressBarsCannotDenyAnUnknownPage() {
        assertNull(BrowserAdapter.resolve("com.android.chrome","com.android.chrome:id/url_bar","youtube.com",false,visible=false))
        assertNull(BrowserAdapter.resolve("com.android.chrome","com.android.chrome:id/url_bar","youtube.com",false,stableWindow=false))
    }
    @Test fun unknownMissingAndNullQuotaPeriodsCannotBeImported() {
        for(period in listOf("\"WEEK\"","null","MISSING")) {
            val field=if(period=="MISSING") "" else "\"period\":$period,"
            val json="""{"version":1,"policy":{"revision":0,"blockers":[],"groups":[{"id":"g",${field}"allowanceMillis":900000,"requiredBreakMillis":300000}]},"settings":{"settingsMode":0}}"""
            assertTrue("Reject $period",runCatching{ConfigurationTransfer.parse(json)}.isFailure)
        }
    }
    @Test fun boundedReadPreservesDataAndRejectsOversizedFilesOnOlderAndroid() {
        assertArrayEquals("hello".toByteArray(),ConfigurationTransfer.readBounded("hello".byteInputStream()))
        assertTrue(runCatching{ConfigurationTransfer.readBounded(ByteArray(1048577).inputStream())}.isFailure)
    }
    @Test fun policyTransactionRejectsStaleRevisionAndPersistsValidEdit() {
        val state=AppState()
        val proposed=PolicySnapshot(blockers=listOf(Blocker("custom","Editable",false,listOf(Target.App("example.app")))))
        val changed=StateGuard.policyChange(state,proposed,0)
        assertEquals("Editable",changed.policy.blockers.single().name)
        assertEquals(1L,changed.policy.revision)
        assertTrue(runCatching{StateGuard.policyChange(changed,proposed,0)}.isFailure)
    }
    @Test fun lockedPolicyCannotBeDisabledButCanGainTargetWithoutResettingLedger() {
        val policy=PolicySnapshot(blockers=listOf(Blocker("a","A",targets=listOf(Target.App("one.app")))))
        val state=AppState(policy=policy,session=LockedSession("s",ReleasePolicy.PASSWORD,null,null,"b","UTC"),ledger=LedgerState(consumed=mapOf("g" to 7000)))
        assertTrue(runCatching{StateGuard.policyChange(state,policy.copy(blockers=policy.blockers.map{it.copy(enabled=false)}),0)}.isFailure)
        val changed=StateGuard.policyChange(state,policy.copy(blockers=policy.blockers.map{it.copy(targets=it.targets+Target.App("two.app"))}),0)
        assertEquals(2,changed.policy.blockers.single().targets.size); assertEquals(state.ledger,changed.ledger)
    }
    @Test fun passwordVerifierAcceptsCorrectAndRejectsWrong() {
        val verifier = PasswordVerifier.create("correct horse".toCharArray(),10000)
        assertTrue(PasswordVerifier.verify("correct horse".toCharArray(),verifier))
        assertFalse(PasswordVerifier.verify("wrong".toCharArray(),verifier))
    }
    @Test fun exportedConfigurationDoesNotContainPrivateState() {
        val state = AppState(session=LockedSession("secret-session",ReleasePolicy.PASSWORD,null,null,"b","UTC"), password=PasswordHash("secret-salt","secret-hash",10000))
        val json = ConfigurationTransfer.export(state)
        assertFalse(json.contains("secret-session")); assertFalse(json.contains("secret-hash")); assertFalse(json.contains("secret-salt"))
        assertEquals(state.policy,ConfigurationTransfer.parse(json))
    }
    @Test fun malformedAndFutureImportsFailWithoutStateChange() {
        assertTrue(runCatching { ConfigurationTransfer.parse("{\"version\":999}") }.isFailure)
        assertTrue(runCatching { ConfigurationTransfer.parse("{bad") }.isFailure)
        assertTrue(runCatching { ConfigurationTransfer.parse("x".repeat(1048577)) }.isFailure)
    }
    @Test fun editablePresetsAreNotMandatoryAndBankIsExcluded() {
        val preset=PresetFactory.create()
        assertTrue(preset.blockers.any { b -> b.targets.any { it.value == "com.google.android.youtube" } })
        assertFalse(preset.blockers.any { b -> b.targets.any { it.value == "ru.ozon.fintech.finance" } })
        assertTrue(AppState().policy.blockers.isEmpty())
    }
    @Test fun unknownAndEditingBrowserUrlsAreAllowed() {
        assertNull(BrowserAdapter.resolve("com.android.chrome", "com.android.chrome:id/url_bar", "youtube.com", true))
        assertNull(BrowserAdapter.resolve("com.android.chrome", "page_body", "youtube.com", false))
        assertEquals("https://youtube.com/",BrowserAdapter.resolve("com.android.chrome", "com.android.chrome:id/url_bar", "youtube.com", false))
    }
    @Test fun lockedSettingsCannotWeakenProtection() {
        val locked=AppState(session=LockedSession("id",ReleasePolicy.PASSWORD,null,null,"b","UTC"),settings=ProtectionSettings(settingsMode=2))
        assertFalse(StateGuard.canUpdateSettings(locked,locked.settings.copy(settingsMode=0)))
    }
    @Test fun unknownSettingsAllowedSelectiveButBlockedWhole() {
        assertFalse(SystemProtection.shouldBlockSettings(1, SettingsScreen.UNKNOWN,false))
        assertTrue(SystemProtection.shouldBlockSettings(2, SettingsScreen.UNKNOWN,false))
        assertFalse(SystemProtection.shouldBlockSettings(2, SettingsScreen.WIFI,true))
    }
}
