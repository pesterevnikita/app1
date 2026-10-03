package io.github.pesterevnikita.focusgate.policy

import java.time.Instant

// Plain data models deliberately avoid Android dependencies and are easy to export/version.
data class Target(val kind: String, val value: String, val includeSubdomains: Boolean = true) {
    companion object {
        fun App(packageName: String) = Target("app", packageName, false)
        fun Host(domain: String, includeSubdomains: Boolean = true) = Target("host", domain, includeSubdomains)
        fun UrlRegex(pattern: String) = Target("regex", pattern, false)
    }
}
data class TimeWindow(val start: String, val end: String)
data class Schedule(val days: Set<Int> = (1..7).toSet(), val windows: List<TimeWindow> = emptyList(), val startDate: String? = null, val endDateExclusive: String? = null)
enum class QuotaPeriod { HOUR, DAY }
data class QuotaGroup(val id: String, val period: QuotaPeriod, val allowanceMillis: Long, val continuousCapMillis: Long? = null, val requiredBreakMillis: Long = 300000)
data class Blocker(val id: String, val name: String, val enabled: Boolean = true, val targets: List<Target> = emptyList(), val schedule: Schedule = Schedule(), val quotaGroupId: String? = null, val message: String = "Time for something you chose to do.")
data class PolicySnapshot(val revision: Long = 0, val blockers: List<Blocker> = emptyList(), val groups: List<QuotaGroup> = emptyList())
data class UsageSnapshot(val consumed: Map<String, Long> = emptyMap(), val sessions: Map<String, ContinuousSession> = emptyMap())
data class ContinuousSession(val usedMillis: Long = 0, val lastUseEndUtcMillis: Long = 0, val lastUseEndElapsedMillis: Long? = null, val bootId: String? = null) {
    fun breakRemaining(requiredBreakMillis: Long,clock: ClockSnapshot): Long {
        val elapsed=lastUseEndElapsedMillis
        val absence=if(bootId==clock.bootId && elapsed!=null) clock.elapsedMillis-elapsed else clock.instant.toEpochMilli()-lastUseEndUtcMillis
        return (requiredBreakMillis-absence.coerceAtLeast(0)).coerceAtLeast(0)
    }
}
data class Observation(val packageName: String, val visibleUrl: String? = null, val interactive: Boolean = true, val revision: Long = -1)
data class ClockSnapshot(val instant: Instant, val elapsedMillis: Long, val bootId: String, val zoneId: String)
data class Decision(val denied: Boolean, val denyingRuleIds: List<String> = emptyList(), val billableGroupIds: Set<String> = emptySet(), val nextTransitionAt: Instant? = null)
