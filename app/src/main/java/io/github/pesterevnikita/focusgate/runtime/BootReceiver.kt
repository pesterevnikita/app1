package io.github.pesterevnikita.focusgate.runtime
import android.content.*
import android.os.UserManager
import io.github.pesterevnikita.focusgate.Graph
import io.github.pesterevnikita.focusgate.health.HealthWorker
import kotlinx.coroutines.launch
class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_LOCKED_BOOT_COMPLETED,Intent.ACTION_USER_UNLOCKED,Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if(!context.getSystemService(UserManager::class.java).isUserUnlocked) return
        val pending=goAsync(); Graph.initialize(context)
        Graph.scope.launch { try { Graph.store.load(); HealthWorker.schedule(context) } finally { pending.finish() } }
    }
}
