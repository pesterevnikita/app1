package io.github.pesterevnikita.focusgate.runtime
import kotlinx.coroutines.*
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
