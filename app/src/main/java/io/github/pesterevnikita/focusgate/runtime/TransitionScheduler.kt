package io.github.pesterevnikita.focusgate.runtime
import kotlinx.coroutines.*
class TransitionScheduler(private val scope: CoroutineScope) {
    private var pending: Job?=null
    fun cancel() { pending?.cancel(); pending=null }
    fun schedule(delayMillis: Long,action: suspend()->Unit) {
        cancel(); pending=scope.launch {
            delay(delayMillis)
            // Detach before running: the action cancels future timers, not its own database IO.
            pending=null
            action()
        }
    }
}
