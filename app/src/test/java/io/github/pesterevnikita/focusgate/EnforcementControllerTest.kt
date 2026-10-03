package io.github.pesterevnikita.focusgate
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.policy.Target
import io.github.pesterevnikita.focusgate.runtime.EnforcementController
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class EnforcementControllerTest {
    private fun clock(ms: Long)=ClockSnapshot(Instant.parse("2026-10-03T10:00:00Z").plusMillis(ms),ms,"b","UTC")
    @Test fun staticForegroundExhaustionReturnsHomeAndStopsBilling() {
        var homeCount=0
        val controller=EnforcementController{homeCount++; true}
        val policy=PolicySnapshot(0,listOf(Blocker("a","A",targets=listOf(Target.App("example.app")),quotaGroupId="g")),listOf(QuotaGroup("g",QuotaPeriod.HOUR,1000)))
        val observation=Observation("example.app")
        assertFalse(controller.evaluate(policy,observation,clock(0)).denied)
        assertTrue(controller.evaluate(policy,observation,clock(1000)).denied)
        assertEquals(1,homeCount)
        controller.evaluate(policy,observation,clock(1100))
        assertEquals(1,homeCount)
        assertEquals(1000L,controller.ledger.snapshot(policy.groups,clock(2000)).consumed["g"])
    }
    @Test fun screenOffDoesNotReturnHome() {
        var calls=0; val controller=EnforcementController{calls++; true}
        val policy=PolicySnapshot(blockers=listOf(Blocker("a","A",targets=listOf(Target.App("example.app")))))
        controller.evaluate(policy,Observation("example.app",interactive=false),clock(0)); assertEquals(0,calls)
    }
}
