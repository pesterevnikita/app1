package io.github.pesterevnikita.focusgate.data

import io.github.pesterevnikita.focusgate.health.SetupPreflight
import io.github.pesterevnikita.focusgate.policy.*
import java.util.UUID

/** Pure lock construction shares preflight and fresh-deadline rules with the real durable transaction. */
object LockSessionChanges {
    /** Reuse only preferences/verifier; every activation receives a new identity and relative deadline. */
    fun start(state: AppState, mode: ReleasePolicy, durationMillis: Long, additions: Boolean, accessibilityConnected: Boolean, now: ClockSnapshot, rememberPreferences: Boolean = true): AppState {
        require(state.session==null) { "Already locked." }
        // Legacy persisted AND sessions retain their release truth table, but no new UI/maintenance lock can create one.
        require(mode!=ReleasePolicy.PASSWORD_AND_TIMER) { "Password AND timer is unavailable for new locks." }
        val refusal=SetupPreflight.refusal(accessibilityConnected,mode!=ReleasePolicy.TIMER,state.password!=null)
        require(refusal==null) { refusal ?: "Setup incomplete" }
        require(state.policy.blockers.any{it.enabled}) { "Enable a blocker before locking." }
        require(durationMillis in 60000L..31536000000L) { "Choose a duration from one minute to one year." }
        val timed=mode!=ReleasePolicy.PASSWORD
        val session=LockedSession(UUID.randomUUID().toString(),mode,
            if(timed)Math.addExact(now.instant.toEpochMilli(),durationMillis) else null,
            if(timed)Math.addExact(now.elapsedMillis,durationMillis) else null,
            now.bootId,now.zoneId,additions)
        return state.copy(session=session,lockPreferences=if(rememberPreferences)LockPreferences(mode,durationMillis,additions) else state.lockPreferences)
    }
}
