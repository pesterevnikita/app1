package io.github.pesterevnikita.focusgate
import io.github.pesterevnikita.focusgate.runtime.ObservationDispatcher
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class ObservationDispatcherTest {
    @Test fun eventDuringCheckpointIsObservedBeforeReturningIdle() = runBlocking {
        val entered=CompletableDeferred<Unit>(); val checkpoint=CompletableDeferred<Unit>();var calls=0
        val dispatcher=ObservationDispatcher(this) {calls++; if(calls==1){entered.complete(Unit);checkpoint.await()}}
        dispatcher.request();entered.await();dispatcher.request();checkpoint.complete(Unit);yield();yield()
        assertEquals(2,calls)
    }
}
