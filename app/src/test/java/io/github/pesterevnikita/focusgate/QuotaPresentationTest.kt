package io.github.pesterevnikita.focusgate

import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.runtime.QuotaPresentation
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class QuotaPresentationTest {
    private fun clock(ms: Long) = ClockSnapshot(Instant.parse("2026-10-03T10:00:00Z").plusMillis(ms), ms, "boot", "UTC")
    private val group = QuotaGroup("shared", QuotaPeriod.HOUR, 900000, 60000, 60000)
    private fun cappedLedger() = LedgerState(
        buckets = mapOf(group.id to clock(0).instant.toEpochMilli()),
        consumed = mapOf(group.id to 60000L),
        sessions = mapOf(group.id to ContinuousSession(60000, clock(60000).instant.toEpochMilli(), 60000, "boot")),
    )

    @Test fun completedBreakRestoresDisplayedCapWithoutWritingLedger() {
        val ledger = cappedLedger()
        val display = QuotaPresentation.project(group, ledger, clock(120000))
        assertEquals(60000L, display.remainingMillis)
        assertEquals(0L, display.breakRemainingMillis)
        assertEquals(60000L, ledger.sessions[group.id]!!.usedMillis)
    }

    @Test fun nextHourRestoresDisplayedAllowanceFromStaleCheckpoint() {
        val uncapped = group.copy(continuousCapMillis = null)
        val ledger = cappedLedger().copy(consumed = mapOf(group.id to 900000L))
        assertEquals(900000L, QuotaPresentation.project(uncapped, ledger, clock(3600000)).remainingMillis)
        assertEquals(900000L, ledger.consumed[group.id])
    }

    @Test fun unfinishedBreakCountsDownWithoutGrantingUseEarly() {
        val display = QuotaPresentation.project(group, cappedLedger(), clock(90000))
        assertEquals(0L, display.remainingMillis)
        assertEquals(30000L, display.breakRemainingMillis)
    }

    @Test fun projectionNeverBillsUnobservedTime() {
        val uncapped = group.copy(continuousCapMillis = null)
        assertEquals(840000L, QuotaPresentation.project(uncapped, cappedLedger(), clock(180000)).remainingMillis)
    }
}
