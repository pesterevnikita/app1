package io.github.pesterevnikita.focusgate
import io.github.pesterevnikita.focusgate.runtime.TransitionScheduler
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class TransitionSchedulerTest {
    @Test fun runningTickCanCancelPendingTimerWithoutCancellingItsOwnCheckpoint() = runBlocking {
        var checkpoints=0
        val scheduler=TransitionScheduler(this)
        scheduler.schedule(1) {
            scheduler.cancel()
            yield() // Checkpoints suspend for database IO in the actual service.
            checkpoints++
            scheduler.schedule(1) {scheduler.cancel();yield();checkpoints++}
        }
        delay(50)
        assertEquals(2,checkpoints)
        scheduler.cancel()
    }
}
