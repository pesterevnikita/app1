package io.github.pesterevnikita.focusgate.runtime

import io.github.pesterevnikita.focusgate.policy.*

/** Display values derived from the last persisted accounting checkpoint. */
data class QuotaDisplay(val remainingMillis: Long, val breakRemainingMillis: Long)

object QuotaPresentation {
    /**
     * Project expiry from a durable checkpoint without changing the real ledger.
     * A fresh UsageLedger has no active foreground groups, so this advances hour/day
     * buckets and completed breaks without charging time that the UI never observed.
     */
    fun project(group: QuotaGroup, ledger: LedgerState, clock: ClockSnapshot): QuotaDisplay {
        val usage = UsageLedger(ledger).snapshot(listOf(group), clock)
        val session = usage.sessions[group.id] ?: ContinuousSession()
        val capRemaining = group.continuousCapMillis?.minus(session.usedMillis) ?: Long.MAX_VALUE
        return QuotaDisplay(
            minOf((group.allowanceMillis - (usage.consumed[group.id] ?: 0)).coerceAtLeast(0), capRemaining.coerceAtLeast(0)),
            if (capRemaining <= 0) session.breakRemaining(group.requiredBreakMillis, clock) else 0,
        )
    }
}
