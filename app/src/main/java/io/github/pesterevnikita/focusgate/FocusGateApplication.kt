package io.github.pesterevnikita.focusgate
import android.app.Application
import android.content.Context
import io.github.pesterevnikita.focusgate.data.AppStore
import kotlinx.coroutines.*
/** Start credential-protected storage only after Android has unlocked the user. */
class FocusGateApplication: Application() {
    override fun onCreate() { super.onCreate(); if(getSystemService(android.os.UserManager::class.java).isUserUnlocked) Graph.initialize(this) }
}
/** Process-local dependencies. Durable state belongs to Room, not to these singleton fields. */
object Graph {
    lateinit var store: AppStore; private set
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile var storageError: String?=null
    /** Idempotent across activity, Accessibility and WorkManager startup races. */
    @Synchronized fun initialize(context: Context) {
        if(::store.isInitialized) return
        store=AppStore(context.applicationContext)
        scope.launch { runCatching{store.load()}.onFailure{storageError="Local storage could not be loaded. Configuration has not been reset."} }
    }
}
