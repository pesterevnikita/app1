package io.github.pesterevnikita.focusgate.maintenance

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.*
import com.google.gson.Gson
import io.github.pesterevnikita.focusgate.Graph
import io.github.pesterevnikita.focusgate.accessibility.ServiceStatus
import io.github.pesterevnikita.focusgate.data.*
import io.github.pesterevnikita.focusgate.policy.ReleasePolicy
import io.github.pesterevnikita.focusgate.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Offline maintenance endpoint retained in release builds at the user's request.
 * Manifest DUMP permission excludes ordinary apps; each Binder entry also checks the exact shell UID.
 * It never grants Android permissions, releases a lock, resets usage, or handles passwords.
 */
class MaintenanceProvider: ContentProvider() {
    private val gson=Gson()
    override fun onCreate(): Boolean {
        val app=requireNotNull(context)
        if(app.getSystemService(UserManager::class.java).isUserUnlocked) Graph.initialize(app)
        return true
    }

    /** Binder worker waits are bounded; all actual Room reads/writes run on Dispatchers.IO. */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        guard()
        val response=try {
            val namedExtras=extras?.keySet()?.associateWith{key ->
                @Suppress("DEPRECATION") val value=extras.get(key)
                require(value is String) { "Extras must use string values" }; value
            } ?: emptyMap()
            val args=MaintenanceArguments.resolve(arg,namedExtras)
            runBlocking {
                withTimeout(5000) { withContext(Dispatchers.IO) {
                    val app=requireNotNull(context)
                    require(app.getSystemService(UserManager::class.java).isUserUnlocked) { "Unlock the phone first" }
                    Graph.initialize(app)
                    Graph.store.ready.first{it}
                    check(Graph.storageError==null) { "Storage unavailable" }
                    // Reconcile ordinary timer expiry before planning, never circumvent a live session.
                    checkSaved(Graph.store.refresh())
                    val command=MaintenanceCommands.plan(method,args,Graph.store.state.value)
                    when(command) {
                        is MaintenanceCommand.Policy -> checkSaved(Graph.store.updatePolicy(command.value,command.expectedRevision))
                        is MaintenanceCommand.Protections -> checkSaved(Graph.store.updateSettings(command.value,command.expectedSettings))
                        is MaintenanceCommand.Lock -> checkSaved(Graph.store.start(ReleasePolicy.TIMER,command.durationMillis,true,ServiceStatus.connected.value,rememberPreferences=false))
                        is MaintenanceCommand.Import -> checkSaved(Graph.store.importConfiguration(command.json,command.merge))
                        is MaintenanceCommand.Read -> Unit
                    }
                    val state=Graph.store.state.value
                    when(method) {
                        "config" -> mapOf("apiVersion" to 1,"ok" to true,"config" to gson.fromJson(ConfigurationTransfer.export(state),Any::class.java))
                        else -> status(state)
                    }
                } }
            }
        } catch(error: Exception) {
            // Do not echo caller arguments, URL values or exception traces into shell output.
            mapOf("apiVersion" to 1,"ok" to false,"error" to if(error is TimeoutCancellationException) "Storage operation timed out; query status before retrying" else (error.message ?: "Maintenance command failed"))
        }
        return Bundle().apply{putString("json",gson.toJson(response))}
    }

    /** Only configured values and projected counters are exposed; observed browser text is never included. */
    private fun status(state: AppState): Map<String,Any?> {
        val clock=AndroidClock(requireNotNull(context)).now(state.session?.zoneId ?: java.time.ZoneId.systemDefault().id)
        val session=state.session
        return mapOf("apiVersion" to 1,"ok" to true,"revision" to state.policy.revision,
            "accessibilityConnected" to ServiceStatus.connected.value,"foreground" to ServiceStatus.foreground.value,
            "locked" to (session!=null),"session" to session?.let{mapOf("releasePolicy" to it.releasePolicy.name,"deadlineUtcMillis" to it.deadlineUtcMillis,"allowRestrictiveAdditions" to it.allowRestrictiveAdditions)},
            "rules" to state.policy.blockers.map{mapOf("id" to it.id,"name" to it.name,"enabled" to it.enabled,"quotaGroupId" to it.quotaGroupId)},
            "groups" to state.policy.groups.map{group -> val display=QuotaPresentation.project(group,state.ledger,clock)
                mapOf("id" to group.id,"period" to group.period.name,"allowanceMillis" to group.allowanceMillis,"continuousCapMillis" to group.continuousCapMillis,"requiredBreakMillis" to group.requiredBreakMillis,"remainingMillis" to display.remainingMillis,"breakRemainingMillis" to display.breakRemainingMillis)},
            "protections" to state.settings)
    }
    private fun checkSaved(error: String?) { check(error==null) { error ?: "Save failed" } }
    private fun guard() {
        MaintenanceCaller.requireShell(Binder.getCallingUid())
        // ContentProvider.call does not uniformly apply read/write permission checks on every Android version.
        requireNotNull(context).enforceCallingPermission("android.permission.DUMP","ADB shell permission required")
    }
    private fun unsupported(): Nothing { guard(); throw UnsupportedOperationException("Use content call") }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = unsupported()
    override fun getType(uri: Uri): String? = unsupported()
    override fun insert(uri: Uri, values: ContentValues?): Uri? = unsupported()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()
    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int = unsupported()
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = unsupported()
    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> = unsupported()
    override fun canonicalize(uri: Uri): Uri? = unsupported()
    override fun uncanonicalize(uri: Uri): Uri? = unsupported()
    override fun refresh(uri: Uri, args: Bundle?, cancellationSignal: CancellationSignal?): Boolean = unsupported()
    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? = unsupported()
    override fun getTypeAnonymous(uri: Uri): String? = unsupported()
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>?) { unsupported() }
}
