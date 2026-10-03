package io.github.pesterevnikita.focusgate.policy

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PolicyEngineTest {
    private val now = Instant.parse("2026-10-05T10:00:00Z")
    private fun evaluate(vararg rules: Blocker, url: String? = null, usage: Map<String, Long> = emptyMap()) =
        PolicyEngine.evaluate(PolicySnapshot(1, rules.toList(), listOf(QuotaGroup("g", QuotaPeriod.HOUR, 900000))),
            UsageSnapshot(usage), Observation("app", url, true), ClockSnapshot(now, 0, "boot", "UTC"))
    @Test fun enabledRuleDeniesOutsideRestrictedMode() { assertTrue(evaluate(Blocker("a", "A", targets=listOf(Target.App("app")))).denied) }
    @Test fun disabledRuleDoesNotDeny() { assertFalse(evaluate(Blocker("a", "A", false, listOf(Target.App("app")))).denied) }
    @Test fun denyWinsOverQuotaAllowance() {
        assertTrue(evaluate(Blocker("q", "Q", targets=listOf(Target.App("app")), quotaGroupId="g"), Blocker("b", "B", targets=listOf(Target.App("app")))).denied)
    }
    @Test fun unknownUrlDoesNotMatch() { assertFalse(evaluate(Blocker("a", "A", targets=listOf(Target.Host("youtube.com")))).denied) }
    @Test fun hostBoundary() { assertTrue(UrlMatcher.matches(Target.Host("youtube.com"), "https://m.youtube.com/watch?v=1")); assertFalse(UrlMatcher.matches(Target.Host("youtube.com"), "https://notyoutube.com")) }
    @Test fun overnightMondayWindow() {
        val schedule = Schedule(days=setOf(1), windows=listOf(TimeWindow("22:00", "02:00")))
        assertTrue(ScheduleEvaluator.isActive(schedule, Instant.parse("2026-10-06T01:00:00Z"), ZoneId.of("UTC")))
        assertFalse(ScheduleEvaluator.isActive(schedule, Instant.parse("2026-10-06T02:00:00Z"), ZoneId.of("UTC")))
    }
}
