package io.github.pesterevnikita.focusgate.runtime
import kotlinx.coroutines.*
/**
 * Coalesce accessibility event bursts without losing an event that arrives during suspended IO.
 * Call on the same dispatcher as [scope]; mutable flags are intentionally confined to that thread.
 */
class ObservationDispatcher(private val scope: CoroutineScope,private val inspect: suspend()->Unit) {
    private var task: Job?=null
    private var dirty=false
    fun request() {
        dirty=true
        if(task?.isActive==true)return
        task=scope.launch {
            // Events arriving during a suspended checkpoint get another fresh foreground read.
            do {dirty=false;inspect()} while(dirty)
        }
    }
}
