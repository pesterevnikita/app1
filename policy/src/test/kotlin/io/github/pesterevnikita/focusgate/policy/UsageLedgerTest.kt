package io.github.pesterevnikita.focusgate.policy
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class UsageLedgerTest {
    @Test fun clockChangesDoNotShortenOrExtendSameBootBreak() {
        for(jump in listOf(-3600000L,3600000L)) {
            val capped=group.copy(continuousCapMillis=900000)
            val ledger=UsageLedger();ledger.transition(setOf("g"),listOf(capped),clock(0));ledger.transition(emptySet(),listOf(capped),clock(100000))
            val early=clock(399999).copy(instant=clock(399999+jump).instant)
            assertEquals(100000L,ledger.snapshot(listOf(capped),early).sessions["g"]!!.usedMillis)
            val full=clock(400000).copy(instant=clock(400000+jump).instant)
            assertEquals(0L,ledger.snapshot(listOf(capped),full).sessions["g"]!!.usedMillis)
        }
    }
    private fun clock(ms: Long) = ClockSnapshot(Instant.parse("2026-10-03T10:00:00Z").plusMillis(ms), ms, "b", "UTC")
    private val group = QuotaGroup("g", QuotaPeriod.HOUR, 900000)
    @Test fun sharedUsageSurvivesAppSwitch() {
        val ledger = UsageLedger()
        ledger.transition(setOf("g"), listOf(group), clock(0))
        ledger.transition(setOf("g"), listOf(group), clock(420000))
        ledger.transition(emptySet(), listOf(group), clock(900000))
        assertEquals(900000L, ledger.snapshot(listOf(group), clock(900000)).consumed["g"])
    }
    @Test fun deniedAndScreenOffDoNotConsume() {
        val ledger = UsageLedger(); ledger.transition(emptySet(), listOf(group), clock(0))
        ledger.transition(emptySet(), listOf(group), clock(900000))
        assertEquals(0L, ledger.snapshot(listOf(group), clock(900000)).consumed["g"])
    }
    @Test fun hourResetDoesNotResetContinuousSession() {
        val capped = group.copy(continuousCapMillis=900000)
        val ledger = UsageLedger(); ledger.transition(setOf("g"), listOf(capped), clock(2700000))
        ledger.transition(emptySet(), listOf(capped), clock(3600000))
        assertEquals(0L, ledger.snapshot(listOf(capped), clock(3600000)).consumed["g"])
        assertEquals(900000L, ledger.snapshot(listOf(capped), clock(3600000)).sessions["g"]!!.usedMillis)
    }
    @Test fun fullBreakResetsSessionButBriefAbsenceDoesNot() {
        val capped = group.copy(continuousCapMillis=900000)
        val ledger = UsageLedger(); ledger.transition(setOf("g"), listOf(capped), clock(0))
        ledger.transition(emptySet(), listOf(capped), clock(100000))
        assertEquals(100000L, ledger.snapshot(listOf(capped), clock(399999)).sessions["g"]!!.usedMillis)
        assertEquals(0L, ledger.snapshot(listOf(capped), clock(400000)).sessions["g"]!!.usedMillis)
    }
    @Test fun reopenPreservesCountersWithoutChargingDowntime() {
        val ledger = UsageLedger(); ledger.transition(setOf("g"), listOf(group), clock(0)); ledger.checkpoint(listOf(group), clock(7000))
        val restored = UsageLedger(ledger.state())
        assertEquals(7000L, restored.snapshot(listOf(group), clock(100000)).consumed["g"])
    }
    @Test fun backwardClockDoesNotReplayBucket() {
        val ledger = UsageLedger(); ledger.transition(setOf("g"), listOf(group), clock(3600000)); ledger.checkpoint(listOf(group), clock(3610000))
        assertEquals(10000L, ledger.snapshot(listOf(group), clock(1000)).consumed["g"])
    }
}
