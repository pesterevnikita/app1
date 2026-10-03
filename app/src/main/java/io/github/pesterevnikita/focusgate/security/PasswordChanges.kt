package io.github.pesterevnikita.focusgate.security

import io.github.pesterevnikita.focusgate.data.AppState
import java.time.Instant

/** Refusals can still carry a durable failed-attempt counter; no result contains plaintext credentials. */
data class PasswordChangeResult(val state: AppState, val error: String? = null)

/** Shared persisted throttle for unlocking and changing an existing password. */
object PasswordAttempts {
    fun failed(state: AppState, nowUtcMillis: Long): AppState {
        val failures=(state.failures+1).coerceAtMost(20)
        val delay=minOf(900000L,1000L shl minOf(failures,20))
        return state.copy(failures=failures,retryAfterUtcMillis=nowUtcMillis+delay)
    }
}

/** Password lifecycle rules operate on the transaction's latest state, rather than a stale UI snapshot. */
object PasswordChanges {
    /**
     * Initial setup needs confirmation; replacement additionally verifies the current password.
     * Consume every caller-owned character buffer on success, refusal or crypto failure.
     */
    fun apply(state: AppState, currentPassword: CharArray, newPassword: CharArray, confirmation: CharArray, nowUtcMillis: Long): PasswordChangeResult = try {
        when {
            state.session!=null -> PasswordChangeResult(state,"Release Restricted Mode before changing the password.")
            newPassword.size<6 -> PasswordChangeResult(state,"Use at least six characters.")
            !newPassword.contentEquals(confirmation) -> PasswordChangeResult(state,"New passwords do not match.")
            state.password!=null && nowUtcMillis<state.retryAfterUtcMillis -> PasswordChangeResult(state,"Try again after ${Instant.ofEpochMilli(state.retryAfterUtcMillis)}.")
            state.password!=null && !PasswordVerifier.verify(currentPassword,state.password) -> PasswordChangeResult(PasswordAttempts.failed(state,nowUtcMillis),"Incorrect current password.")
            else -> PasswordChangeResult(state.copy(password=PasswordVerifier.create(newPassword),failures=0,retryAfterUtcMillis=0))
        }
    } finally {
        currentPassword.fill('\u0000'); newPassword.fill('\u0000'); confirmation.fill('\u0000')
    }
}
