package io.github.pesterevnikita.focusgate

import com.google.gson.Gson
import io.github.pesterevnikita.focusgate.data.*
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.security.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PasswordLifecycleTest {
    private fun saved()=AppState(password=PasswordVerifier.create("old-secret".toCharArray(),10000))
    private fun change(state:AppState,old:String="old-secret",next:String="new-secret",repeat:String=next,now:Long=100000)=
        PasswordChanges.apply(state,old.toCharArray(),next.toCharArray(),repeat.toCharArray(),now)
    @Test fun initialSetupRequiresConfirmedMinimumLengthAndClearsEveryBuffer() {
        assertNotNull(change(AppState(),old="",next="short").error)
        assertNotNull(change(AppState(),old="",repeat="different").error)
        val old="".toCharArray(); val next="new-secret".toCharArray(); val repeat="new-secret".toCharArray()
        val result=PasswordChanges.apply(AppState(),old,next,repeat,100000)
        assertNull(result.error); assertTrue(PasswordVerifier.verify("new-secret".toCharArray(),result.state.password!!))
        assertTrue(next.all{it=='\u0000'}); assertTrue(repeat.all{it=='\u0000'}); assertTrue(old.all{it=='\u0000'})
    }
    @Test fun existingPasswordMustBeVerifiedAndWrongAttemptsSharePersistedThrottle() {
        val state=saved(); val wrong=change(state,old="wrong")
        assertNotNull(wrong.error); assertEquals(state.password,wrong.state.password)
        assertEquals(1,wrong.state.failures); assertEquals(102000L,wrong.state.retryAfterUtcMillis)
        val early=change(wrong.state,now=101000)
        assertNotNull(early.error); assertEquals(wrong.state,early.state)
        val success=change(wrong.state,now=102000)
        assertNull(success.error); assertEquals(0,success.state.failures); assertEquals(0L,success.state.retryAfterUtcMillis)
        assertTrue(PasswordVerifier.verify("new-secret".toCharArray(),success.state.password!!))
        assertFalse(PasswordVerifier.verify("old-secret".toCharArray(),success.state.password!!))
    }
    @Test fun lockedOrMismatchedChangesDoNotReplaceCredentialAndAlwaysClearBuffers() {
        val state=saved(); val locked=state.copy(session=LockedSession("s",ReleasePolicy.PASSWORD,null,null,"b","UTC"))
        assertEquals(locked,change(locked).state)
        assertEquals(state,change(state,repeat="other-password").state)
        val old="wrong".toCharArray();val next="new-secret".toCharArray();val repeat="new-secret".toCharArray()
        assertNotNull(PasswordChanges.apply(locked,old,next,repeat,100000).error)
        assertTrue(listOf(old,next,repeat).all{chars->chars.all{it=='\u0000'}})
    }
    @Test fun relockKeepsVerifierAndPreferencesButUsesFreshDeadlineAndSessionId() {
        val state=saved().copy(policy=PresetFactory.create())
        val first=LockSessionChanges.start(state,ReleasePolicy.PASSWORD_OR_TIMER,60000,true,true,clock(100000,1000))
        val second=LockSessionChanges.start(first.copy(session=null),ReleasePolicy.PASSWORD_OR_TIMER,60000,true,true,clock(300000,201000))
        assertEquals(state.password,second.password); assertEquals(first.lockPreferences,second.lockPreferences)
        assertNotEquals(first.session!!.id,second.session!!.id)
        assertEquals(360000L,second.session!!.deadlineUtcMillis); assertEquals(261000L,second.session!!.deadlineElapsedMillis)
        assertFalse(RestrictedSession.expired(second.session!!,clock(300001,201001)))
        val debug=LockSessionChanges.start(second.copy(session=null),ReleasePolicy.TIMER,120000,true,true,clock(400000,301000),rememberPreferences=false)
        assertEquals(second.lockPreferences,debug.lockPreferences)
    }
    @Test fun existingAndSessionStillNeedsBothButNewAndLocksAreRefused() {
        val state=saved().copy(policy=PresetFactory.create())
        assertTrue(runCatching{LockSessionChanges.start(state,ReleasePolicy.PASSWORD_AND_TIMER,60000,true,true,clock(100000,1000))}.isFailure)
        assertFalse(RestrictedSession.canRelease(ReleasePolicy.PASSWORD_AND_TIMER,true,false))
        assertFalse(RestrictedSession.canRelease(ReleasePolicy.PASSWORD_AND_TIMER,false,true))
        assertTrue(RestrictedSession.canRelease(ReleasePolicy.PASSWORD_AND_TIMER,true,true))
    }
    @Test fun olderDocumentsHaveNoRememberedPreferencesAndExportsExcludeThem() {
        val old=Gson().fromJson("{}",AppState::class.java)
        assertNull(old.lockPreferences)
        val state=AppState(lockPreferences=LockPreferences())
        assertEquals(ReleasePolicy.PASSWORD_OR_TIMER,state.lockPreferences!!.releasePolicy)
        assertFalse(ConfigurationTransfer.export(state).contains("lockPreferences"))
    }
    @Test fun competingPasswordReplacementRejectsStaleOldPasswordAndKeepsFirstVerifier() {
        val first=change(saved(),next="first-replacement")
        assertNull(first.error)
        val stale=change(first.state,next="second-replacement")
        assertNotNull(stale.error); assertEquals(first.state.password,stale.state.password)
        assertTrue(PasswordVerifier.verify("first-replacement".toCharArray(),stale.state.password!!))
        assertFalse(PasswordVerifier.verify("second-replacement".toCharArray(),stale.state.password!!))
        assertEquals(1,stale.state.failures)
    }
    @Test fun releasedStateRoundTripPreservesVerifierAndRelativeLockPreferences() {
        val state=saved().copy(policy=PresetFactory.create())
        val locked=LockSessionChanges.start(state,ReleasePolicy.PASSWORD_OR_TIMER,300000,false,true,clock(100000,1000))
        val released=locked.copy(session=null)
        val restored=Gson().fromJson(Gson().toJson(released),AppState::class.java)
        assertNull(restored.session); assertEquals(released.password,restored.password)
        assertEquals(LockPreferences(ReleasePolicy.PASSWORD_OR_TIMER,300000,false),restored.lockPreferences)
        assertTrue(PasswordVerifier.verify("old-secret".toCharArray(),restored.password!!))
        val relocked=LockSessionChanges.start(restored,restored.lockPreferences!!.releasePolicy,restored.lockPreferences!!.durationMillis,
            restored.lockPreferences!!.allowRestrictiveAdditions,true,clock(900000,801000))
        assertEquals(1200000L,relocked.session!!.deadlineUtcMillis)
        assertEquals(released.password,relocked.password)
    }
    @Test fun confirmedRemovalPreservesConfigurationAndAllowsFreshSetupWithoutOldPassword() {
        val state=saved().copy(policy=PresetFactory.create(),ledger=LedgerState(consumed=mapOf("g" to 5000)),lockPreferences=LockPreferences())
        val old="old-secret".toCharArray()
        val removed=PasswordChanges.remove(state,old,100000)
        assertNull(removed.error); assertNull(removed.state.password); assertTrue(old.all{it=='\u0000'})
        assertEquals(state.policy,removed.state.policy); assertEquals(state.ledger,removed.state.ledger)
        assertEquals(state.lockPreferences,removed.state.lockPreferences)
        assertEquals(0,removed.state.failures); assertEquals(0L,removed.state.retryAfterUtcMillis)
        for(mode in listOf(ReleasePolicy.PASSWORD,ReleasePolicy.PASSWORD_OR_TIMER)) {
            assertTrue(runCatching{LockSessionChanges.start(removed.state,mode,60000,true,true,clock(100000,1000))}.isFailure)
        }
        val timer=LockSessionChanges.start(removed.state,ReleasePolicy.TIMER,60000,true,true,clock(100000,1000),rememberPreferences=false)
        assertEquals(ReleasePolicy.TIMER,timer.session!!.releasePolicy)
        assertEquals(state.lockPreferences,timer.lockPreferences)
        val replaced=change(removed.state,old="",next="fresh-secret")
        assertNull(replaced.error); assertTrue(PasswordVerifier.verify("fresh-secret".toCharArray(),replaced.state.password!!))
        val choices=replaced.state.lockPreferences!!
        val resumed=LockSessionChanges.start(replaced.state,choices.releasePolicy,choices.durationMillis,choices.allowRestrictiveAdditions,true,clock(200000,101000))
        assertEquals(ReleasePolicy.PASSWORD_OR_TIMER,resumed.session!!.releasePolicy)
        assertEquals(choices,resumed.lockPreferences)
    }
    @Test fun wrongOrMissingRemovalPasswordRetainsVerifierAndUsesExistingThrottle() {
        val state=saved()
        val missing=PasswordChanges.remove(state,charArrayOf(),100000)
        assertNotNull(missing.error); assertEquals(state.password,missing.state.password)
        assertEquals(1,missing.state.failures); assertEquals(102000L,missing.state.retryAfterUtcMillis)
        val old="old-secret".toCharArray()
        val retry=PasswordChanges.remove(missing.state,old,101000)
        assertNotNull(retry.error); assertEquals(missing.state,retry.state); assertTrue(old.all{it=='\u0000'})
        val wrong="incorrect".toCharArray()
        val second=PasswordChanges.remove(missing.state,wrong,102000)
        assertNotNull(second.error); assertEquals(2,second.state.failures)
        assertEquals(106000L,second.state.retryAfterUtcMillis); assertTrue(wrong.all{it=='\u0000'})
        assertNull(PasswordChanges.remove(second.state,"old-secret".toCharArray(),106000).state.password)
    }
    @Test fun removalRefusesEveryActiveLockIncludingTimerAndClearsInput() {
        for(mode in ReleasePolicy.entries) {
            val state=saved().copy(session=LockedSession("s",mode,60000,60000,"b","UTC"))
            val old="old-secret".toCharArray()
            val result=PasswordChanges.remove(state,old,100000)
            assertNotNull(result.error); assertEquals(state,result.state); assertTrue(old.all{it=='\u0000'})
        }
    }
    @Test fun staleRemovalAfterPasswordChangeFailsAndMissingVerifierIsHarmless() {
        val changed=change(saved(),next="replacement-secret").state
        val stale="old-secret".toCharArray()
        val result=PasswordChanges.remove(changed,stale,100000)
        assertNotNull(result.error); assertEquals(changed.password,result.state.password)
        assertTrue(stale.all{it=='\u0000'})
        val absent=AppState();val unused="unused".toCharArray()
        val noOp=PasswordChanges.remove(absent,unused,100000)
        assertEquals(absent,noOp.state); assertNull(noOp.error)
        assertTrue(unused.all{it=='\u0000'})
    }
    private fun clock(utc:Long,elapsed:Long)=ClockSnapshot(Instant.ofEpochMilli(utc),elapsed,"boot","UTC")
}
