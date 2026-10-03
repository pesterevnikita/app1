package io.github.pesterevnikita.focusgate.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.app.KeyguardManager
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import io.github.pesterevnikita.focusgate.Graph
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.runtime.*
import io.github.pesterevnikita.focusgate.diagnostics.LocalDiagnostics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object ServiceStatus {
    val connected=MutableStateFlow(false)
    val foreground=MutableStateFlow(false)
    val retryForeground=MutableSharedFlow<Unit>(extraBufferCapacity=1)
    val browser=MutableStateFlow("No browser observation yet; unknown pages are allowed.")
    val remaining=MutableStateFlow<Map<String,Long>>(emptyMap())
}
/** Android owns this service's binding; the activity is only a configuration UI. */
class FocusGateAccessibilityService: AccessibilityService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var controller: EnforcementController?=null
    private var revision=-1L
    private val observations=ObservationDispatcher(scope) { inspect() }
    private val timer=TransitionScheduler(scope)
    private var lastEventClass=""
    private var lastPackage=""
    private var lastWindowId=-1
    private var uncertainUntil=0L
    private var lastSystemHome=-10000L
    private val screenReceiver=object: BroadcastReceiver() { override fun onReceive(context: Context?,intent: Intent?) { observe() } }
    override fun onServiceConnected() {
        Graph.initialize(this)
        registerReceiver(screenReceiver,IntentFilter().apply{addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)})
        scope.launch {
            Graph.store.ready.first{it}
            ServiceStatus.connected.value=true
            diagnostic("SERVICE_CONNECTED")
            Graph.store.state.collect { state ->
                updateForeground(needsForeground(state))
                if(revision!=state.policy.revision) observe()
            }
        }
        scope.launch {
            Graph.store.ready.first { it }
            ServiceStatus.retryForeground.collect {
                updateForeground(needsForeground(Graph.store.state.value))
            }
        }
    }
    /** Promote only while rules need enforcement; this does not start a polling loop. */
    private fun updateForeground(required: Boolean) {
        if(required && !ServiceStatus.foreground.value) {
            ServiceStatus.foreground.value=EnforcementNotification.start(this)
        } else if(!required && ServiceStatus.foreground.value) {
            EnforcementNotification.stop(this)
            ServiceStatus.foreground.value=false
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(event==null) return
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastEventClass=event.className?.toString().orEmpty(); lastPackage=event.packageName?.toString().orEmpty(); lastWindowId=event.windowId
            uncertainUntil=SystemClock.elapsedRealtime()+200
        }
        observe()
    }
    private fun observe() {
        // No URL/body tree is retained; coalescing preserves one pending refresh during IO.
        if(!Graph.store.ready.value) return
        observations.request()
    }
    /** One serialized observation: match, redirect, commit usage, then schedule the next transition. */
    private suspend fun inspect() {
        timer.cancel()
        val state=Graph.store.state.value
        if(revision!=state.policy.revision || controller==null) {
            controller=EnforcementController(state.ledger) { val success=performGlobalAction(GLOBAL_ACTION_HOME); if(!success) diagnostic("HOME_FAILED"); success }
            revision=state.policy.revision
        }
        val power=getSystemService(PowerManager::class.java)
        val interactive=power.isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val root=if(interactive) rootInActiveWindow else null
        val pkg=root?.packageName?.toString().orEmpty()
        val homePackage=packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)?.activityInfo?.packageName
        val exempt=pkg in setOf(packageName,homePackage,"com.android.dialer","com.google.android.dialer","com.android.phone","com.android.emergency")
        var url: String?=null
        if(pkg in BrowserAdapter.browsers && root!=null) {
            for(id in listOf("$pkg:id/url_bar","$pkg:id/url_bar_text")) {
                val node=root.findAccessibilityNodeInfosByViewId(id).firstOrNull() ?: continue
                val stable=SystemClock.elapsedRealtime()>=uncertainUntil && root.windowId==lastWindowId && lastPackage==pkg && !lastEventClass.lowercase().let{it.contains("tabswitcher")||it.contains("overview")||it.contains("dialog")}
                url=BrowserAdapter.resolve(pkg,id,node.text?.toString(),node.isEditable && node.isFocused,node.isVisibleToUser && node.windowId==root.windowId,stable)
                if(url!=null) break
            }
            ServiceStatus.browser.value=if(url==null) "URL unknown in this browser; website rules allow it." else "Address bar recognized. Website matching active."
        }
        val clock=AndroidClock(this).now(state.session?.zoneId ?: java.time.ZoneId.systemDefault().id)
        var systemDenied=false
        if(interactive && !exempt && pkg=="com.android.settings") {
            val c=if(lastPackage==pkg) lastEventClass.lowercase() else ""
            val screen=when {
                c.contains("wifi") -> SettingsScreen.WIFI
                c.contains("mobile") || c.contains("networkdashboard") -> SettingsScreen.MOBILE
                listOf("accessibility","deviceadmin","applications","appinfo","installedappdetails","datetime","development","manageapps").any{c.contains(it)} -> SettingsScreen.SENSITIVE
                else -> SettingsScreen.UNKNOWN
            }
            systemDenied=SystemProtection.shouldBlockSettings(state.settings.settingsMode,screen,state.settings.networkExceptions)
        }
        // Xiaomi Recents normally lives in the launcher. Only explicit Recents activity names count.
        if(interactive && state.settings.recents && lastPackage==pkg && lastEventClass.lowercase().let{it.contains("recentsactivity") || it.contains("overviewactivity")}) systemDenied=true
        val observation=Observation(pkg,url,interactive && pkg.isNotBlank() && !exempt && !systemDenied,revision)
        val decision=controller!!.evaluate(state.policy,observation,clock)
        if(systemDenied && clock.elapsedMillis-lastSystemHome>=750) { lastSystemHome=clock.elapsedMillis; performGlobalAction(GLOBAL_ACTION_HOME) }
        if(decision.denied) { diagnostic("DENY"); if(state.popup && clock.elapsedMillis-lastSystemHome>=750) { lastSystemHome=clock.elapsedMillis; Toast.makeText(this,state.policy.blockers.firstOrNull{it.id in decision.denyingRuleIds}?.message ?: "Blocked by your rule.",Toast.LENGTH_SHORT).show() } }
        val usage=controller!!.ledger.snapshot(state.policy.groups,clock)
        ServiceStatus.remaining.value=state.policy.groups.associate { group -> group.id to minOf((group.allowanceMillis-(usage.consumed[group.id] ?: 0)).coerceAtLeast(0),group.continuousCapMillis?.let{(it-(usage.sessions[group.id]?.usedMillis ?: 0)).coerceAtLeast(0)} ?: Long.MAX_VALUE) }
        if(state.ledger!=controller!!.ledger.state()) Graph.store.saveLedger(controller!!.ledger.state(),revision)
        RemainingTimeNotification.update(this,state,ServiceStatus.remaining.value)
        val next=decision.nextTransitionAt?.let{(it.toEpochMilli()-clock.instant.toEpochMilli()).coerceAtLeast(100)}
        var delayMillis=if(decision.billableGroupIds.isNotEmpty()) minOf(5000,next ?: 5000) else next
        if(pkg in BrowserAdapter.browsers && clock.elapsedMillis<uncertainUntil) delayMillis=minOf(delayMillis ?: 1000,(uncertainUntil-clock.elapsedMillis).coerceAtLeast(100))
        if(delayMillis!=null) timer.schedule(delayMillis) { observe() }
    }
    private fun diagnostic(category: String) { if(Graph.store.ready.value && Graph.store.state.value.diagnostics) Graph.scope.launch { LocalDiagnostics(filesDir).record(category,System.currentTimeMillis()) } }
    // Android interrupts feedback (such as speech), not the service binding.
    // We produce no continuous feedback, so keep enforcement and quota timers alive.
    override fun onInterrupt() {}
    override fun onDestroy() {
        ServiceStatus.connected.value=false
        ServiceStatus.foreground.value=false
        runCatching{unregisterReceiver(screenReceiver)}
        scope.cancel()
        super.onDestroy()
    }
}
