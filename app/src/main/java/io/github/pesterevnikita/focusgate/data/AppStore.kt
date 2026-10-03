package io.github.pesterevnikita.focusgate.data
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.runtime.AndroidClock
import io.github.pesterevnikita.focusgate.security.PasswordVerifier
import io.github.pesterevnikita.focusgate.health.SetupPreflight
import java.util.UUID
/**
 * Single durable write boundary shared by the UI and enforcement service.
 * Public mutations return null on success or a user-facing refusal/error, rather than partial state.
 */
class AppStore(private val context: Context, name: String = "focusgate.db") {
    private val database=Room.databaseBuilder(context,FocusGateDatabase::class.java,name).build()
    private val mutex=Mutex(); private val gson=Gson(); private val clock=AndroidClock(context)
    val state = MutableStateFlow(AppState())
    val ready=MutableStateFlow(false)
    /**
     * Read the latest database state, check guards, and save all fields in one transaction.
     * Publish to observers only after commit so the UI cannot advertise an unsaved lock or quota.
     * The mutex also keeps concurrent coroutine writes from publishing out of order.
     */
    private suspend fun transact(change: (AppState)->AppState): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val updated=database.withTransaction {
                    val row=database.stateDao().read()
                    val stored=if(row==null) AppState() else gson.fromJson(row.json,AppState::class.java)
                    val current=autoRelease(stored)
                    val next=change(current)
                    database.stateDao().write(StateEntity(json=gson.toJson(next)))
                    next
                }
                state.value=updated; ready.value=true
                // Minimal metadata is readable before unlock; credentials/policy remain protected.
                context.createDeviceProtectedStorageContext().getSharedPreferences("boot-session",Context.MODE_PRIVATE).edit().putBoolean("locked",updated.session!=null).commit()
            }.exceptionOrNull()?.let{it.message ?: "Could not save configuration"}
        }
    }
    /** Reconcile timer-only/OR locks on every access; AND locks still require a current password attempt. */
    private fun autoRelease(current: AppState): AppState {
        val session=current.session ?: return current
        val expired=RestrictedSession.expired(session,clock.now(session.zoneId))
        return if(RestrictedSession.canRelease(session.releasePolicy,false,expired)) current.copy(session=null) else current
    }
    suspend fun load() { val error=transact{it}; check(error==null) { error ?: "Storage unavailable" } }
    suspend fun refresh(): String? = transact{it}
    /** Revision checking prevents an old editor from overwriting a newer rule or a stricter addition. */
    suspend fun updatePolicy(policy: PolicySnapshot, expectedRevision: Long): String? = transact{StateGuard.policyChange(it,policy,expectedRevision)}
    /** Reject usage computed against rules that changed while the service was processing an observation. */
    suspend fun saveLedger(ledger: LedgerState, revision: Long): String? = transact { require(it.policy.revision==revision) { "Stale usage observation" }; it.copy(ledger=ledger) }
    /** Optional baseline prevents maintenance requests planned concurrently from replacing one another's protection edits. */
    suspend fun updateSettings(settings: ProtectionSettings, expectedSettings: ProtectionSettings? = null): String? = transact { StateGuard.settingsChange(it,settings,expectedSettings) }
    /** Store only a salted verifier; consume and clear the caller's character buffer even on failure. */
    suspend fun setPassword(password: CharArray): String? = try { transact { require(it.session==null) { "Password is locked." }; it.copy(password=PasswordVerifier.create(password),failures=0,retryAfterUtcMillis=0) } } finally { password.fill('\u0000') }
    suspend fun preferences(diagnostics: Boolean, countdown: Boolean, popup: Boolean): String? = transact{it.copy(diagnostics=diagnostics,countdown=countdown,popup=popup)}
    /** Lock configuration, not blocker activation. Both deadline clocks are persisted to survive restarts. */
    suspend fun start(mode: ReleasePolicy, durationMillis: Long, additions: Boolean, accessibilityConnected: Boolean): String? = transact {
        require(it.session==null) { "Already locked." }
        val needsPassword=mode!=ReleasePolicy.TIMER
        val refusal=SetupPreflight.refusal(accessibilityConnected,needsPassword,it.password!=null); require(refusal==null){refusal ?: "Setup incomplete"}
        require(it.policy.blockers.any{rule->rule.enabled}) { "Enable a blocker before locking." }
        require(durationMillis in 60000L..31536000000L) { "Choose a duration from one minute to one year." }
        val now=clock.now(); val timed=mode!=ReleasePolicy.PASSWORD
        it.copy(session=LockedSession(UUID.randomUUID().toString(),mode,if(timed) now.instant.toEpochMilli()+durationMillis else null,if(timed) now.elapsedMillis+durationMillis else null,now.bootId,now.zoneId,additions))
    }
    /**
     * Release only the configuration lock; enabled blockers remain active afterward.
     * Failed attempts persist an exponentially increasing retry delay, capped at fifteen minutes.
     */
    suspend fun release(password: CharArray): String? = try {
        var refused: String?=null
        val failure=transact { current ->
            val session=current.session ?: return@transact current
            val now=clock.now(session.zoneId); val expired=RestrictedSession.expired(session,now)
            if(RestrictedSession.canRelease(session.releasePolicy,false,expired)) return@transact current.copy(session=null)
            if(session.releasePolicy==ReleasePolicy.TIMER) { refused="The timer has not ended."; return@transact current }
            if(now.instant.toEpochMilli()<current.retryAfterUtcMillis) { refused="Try again after ${java.time.Instant.ofEpochMilli(current.retryAfterUtcMillis)}."; return@transact current }
            val valid=current.password?.let{PasswordVerifier.verify(password,it)}==true
            if(RestrictedSession.canRelease(session.releasePolicy,valid,expired)) current.copy(session=null,failures=0,retryAfterUtcMillis=0)
            else if(valid) { refused="The deadline must also be reached. Enter the password again then."; current.copy(failures=0,retryAfterUtcMillis=0) }
            else { refused="Incorrect password."; val failures=(current.failures+1).coerceAtMost(20); current.copy(failures=failures,retryAfterUtcMillis=now.instant.toEpochMilli()+minOf(900000,1000L shl minOf(failures,20))) }
        }
        failure ?: refused
    } finally { password.fill('\u0000') }
    /** Imports never bring credentials, lock sessions, or usage counters from another installation. */
    suspend fun importConfiguration(json: String, merge: Boolean): String? {
        val document=runCatching{ConfigurationTransfer.parseDocument(json)}.getOrElse{return it.message ?: "Invalid configuration"}
        return transact{require(it.session==null){"Import is unavailable while locked."}; val policy=if(merge) ConfigurationTransfer.merge(it.policy,document.policy) else document.policy; ConfigurationTransfer.validate(policy); it.copy(policy=policy.copy(revision=it.policy.revision+1),settings=if(merge) it.settings else document.settings.copy(settingsMode=0,recents=false,uninstallResistance=false))}
    }
    fun close() { database.close() }
}
