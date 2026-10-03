package io.github.pesterevnikita.focusgate.policy
data class LedgerState(val buckets: Map<String, Long> = emptyMap(), val consumed: Map<String, Long> = emptyMap(), val sessions: Map<String, ContinuousSession> = emptyMap())
class UsageLedger(initial: LedgerState = LedgerState()) {
    private val buckets = initial.buckets.toMutableMap()
    private val consumed = initial.consumed.toMutableMap()
    private val sessions = initial.sessions.toMutableMap()
    private var active = emptySet<String>()
    private var previous: ClockSnapshot? = null
    fun transition(active: Set<String>, groups: List<QuotaGroup>, clock: ClockSnapshot) {
        checkpoint(groups,clock)
        this.active = active
    }
    fun checkpoint(groups: List<QuotaGroup>, clock: ClockSnapshot) {
        val last = previous
        val delta = if (last != null && last.bootId == clock.bootId) (clock.elapsedMillis-last.elapsedMillis).coerceAtLeast(0) else 0
        groups.forEach { group ->
            val current = BucketClock.start(group.period,clock)
            val oldBucket = buckets[group.id]
            // A backward wall-clock adjustment must not replay a bucket's allowance.
            val bucket = maxOf(current,oldBucket ?: current)
            val billable = if (group.id in active) delta else 0L
            if (oldBucket != bucket) {
                consumed[group.id] = if (last != null && kotlin.math.abs(clock.instant.toEpochMilli()-last.instant.toEpochMilli()-delta) < 2000) minOf(billable,(clock.instant.toEpochMilli()-bucket).coerceAtLeast(0)) else billable
            } else consumed[group.id] = (consumed[group.id] ?: 0) + billable
            buckets[group.id] = bucket
            var session = sessions[group.id] ?: ContinuousSession()
            if (group.id !in active && session.breakRemaining(group.requiredBreakMillis,clock)==0L) session = ContinuousSession()
            if (billable > 0) session = ContinuousSession(session.usedMillis+billable,clock.instant.toEpochMilli(),clock.elapsedMillis,clock.bootId)
            sessions[group.id] = session
        }
        previous = clock
    }
    fun snapshot(groups: List<QuotaGroup>, clock: ClockSnapshot): UsageSnapshot {
        checkpoint(groups,clock)
        return UsageSnapshot(consumed.toMap(),sessions.toMap())
    }
    fun state(): LedgerState = LedgerState(buckets.toMap(),consumed.toMap(),sessions.toMap())
}
