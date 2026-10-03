package io.github.pesterevnikita.focusgate.health
import android.content.Context
import android.os.UserManager
import androidx.work.*
import io.github.pesterevnikita.focusgate.Graph
import io.github.pesterevnikita.focusgate.accessibility.ServiceStatus
import io.github.pesterevnikita.focusgate.diagnostics.LocalDiagnostics
import java.util.concurrent.TimeUnit
class HealthWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        if(!applicationContext.getSystemService(UserManager::class.java).isUserUnlocked) return Result.success()
        Graph.initialize(applicationContext)
        Graph.store.load(); Graph.store.refresh()
        if(!ServiceStatus.connected.value && Graph.store.state.value.diagnostics) LocalDiagnostics(applicationContext.filesDir).record("HEALTH_DEGRADED",System.currentTimeMillis())
        return Result.success()
    }
    companion object { fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("setup-health",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<HealthWorker>(30,TimeUnit.MINUTES).build()) } }
}
