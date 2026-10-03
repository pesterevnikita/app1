package io.github.pesterevnikita.focusgate
import io.github.pesterevnikita.focusgate.runtime.TransitionScheduler
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class TransitionSchedulerTest {
    @Test fun runningTickCanCancelPendingTimerWithoutCancellingItsOwnCheckpoint() = runBlocking {
        var checkpoints=0
        val completed=CompletableDeferred<Unit>()
        val scheduler=TransitionScheduler(this)
        scheduler.schedule(1) {
            scheduler.cancel()
            yield() // Checkpoints suspend for database IO in the actual service.
            checkpoints++
            scheduler.schedule(1) {scheduler.cancel();yield();checkpoints++;completed.complete(Unit)}
        }
        try {
            // Wait for the actual second checkpoint, not an assumed 50 ms of
            // wall time. A busy build machine must not create a false failure.
            withTimeout(5000) { completed.await() }
            assertEquals(2,checkpoints)
        } finally { scheduler.cancel() }
    }
}
