package io.github.pesterevnikita.focusgate.policy

import java.time.ZoneId
object PolicyEngine {
    fun evaluate(policy: PolicySnapshot, usage: UsageSnapshot, observation: Observation, clock: ClockSnapshot): Decision {
        if (!observation.interactive) return Decision(false)
        val zone = ZoneId.of(clock.zoneId)
        val matching = policy.blockers.filter { it.enabled && it.targets.any { target -> if (target.kind == "app") target.value == observation.packageName else UrlMatcher.matches(target, observation.visibleUrl) } }
        val deadlines = matching.mapNotNull { ScheduleEvaluator.nextTransition(it.schedule, clock.instant, zone) }.toMutableList()
        val denying = mutableListOf<String>(); val billing = linkedSetOf<String>()
        matching.filter { ScheduleEvaluator.isActive(it.schedule, clock.instant, zone) }.forEach { rule ->
            val group = policy.groups.find { it.id == rule.quotaGroupId }
            if (group == null) { denying += rule.id; return@forEach }
            val remaining = group.allowanceMillis - (usage.consumed[group.id] ?: 0)
            val session = usage.sessions[group.id] ?: ContinuousSession()
            val capRemaining = group.continuousCapMillis?.minus(session.usedMillis) ?: Long.MAX_VALUE
            if (remaining <= 0 || capRemaining <= 0) {
                denying += rule.id
                if (capRemaining <= 0) deadlines += clock.instant.plusMillis(session.breakRemaining(group.requiredBreakMillis,clock))
            } else {
                billing += group.id
                deadlines += clock.instant.plusMillis(minOf(remaining, capRemaining))
            }
            deadlines += BucketClock.next(group.period,clock)
        }
        return Decision(denying.isNotEmpty(),denying.sorted(),if (denying.isEmpty()) billing else emptySet(),deadlines.filter { it > clock.instant }.minOrNull())
    }
}
