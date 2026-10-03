package io.github.pesterevnikita.focusgate.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.pesterevnikita.focusgate.Graph
import io.github.pesterevnikita.focusgate.accessibility.ServiceStatus
import io.github.pesterevnikita.focusgate.admin.FocusGateAdminReceiver
import io.github.pesterevnikita.focusgate.data.*
import io.github.pesterevnikita.focusgate.donations.DonationConfig
import io.github.pesterevnikita.focusgate.diagnostics.LocalDiagnostics
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.policy.Target
import io.github.pesterevnikita.focusgate.runtime.AndroidClock
import io.github.pesterevnikita.focusgate.runtime.QuotaPresentation
import io.github.pesterevnikita.focusgate.health.SetupScreens
import kotlinx.coroutines.*
import java.time.*
import java.util.UUID

private fun minutes(ms: Long)=String.format(java.util.Locale.getDefault(),"%d:%02d",ms.coerceAtLeast(0)/60000,(ms.coerceAtLeast(0)/1000)%60)

/** Three configuration tabs. All durable writes still pass through AppStore's guards. */
@Composable fun FocusGateApp() {
    val context=LocalContext.current
    val store=Graph.store
    val state by store.state.collectAsState()
    val ready by store.ready.collectAsState()
    val connected by ServiceStatus.connected.collectAsState()
    var tab by remember {mutableIntStateOf(0)}
    var editor by remember {mutableStateOf<Blocker?>(null)}
    var showEditor by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf("")}
    var disclosure by remember{mutableStateOf(false)}
    var importText by remember{mutableStateOf<String?>(null)}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    // Read the existing ticker on every tab. The exempt configuration screen
    // needs its own countdown tick instead of waiting for Accessibility events.
    val displayClock=remember(now,state.session?.zoneId) { AndroidClock(context).now(state.session?.zoneId ?: ZoneId.systemDefault().id) }
    val quotaDisplay=remember(state.policy.groups,state.ledger,displayClock) {
        state.policy.groups.associate { it.id to QuotaPresentation.project(it,state.ledger,displayClock) }
    }
    val scope=rememberCoroutineScope()
    fun action(operation: suspend()->String?) {scope.launch{notice=withContext(Dispatchers.IO){runCatching{operation()}.getOrElse{it.message ?: "Action failed"}} ?: "Saved."}}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null) action {
        context.contentResolver.openOutputStream(uri)?.use{it.write(ConfigurationTransfer.export(store.state.value).toByteArray())} ?: error("Could not write file"); null
    }}
    val exportLogs=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null) action {
        context.contentResolver.openOutputStream(uri)?.use{it.write(LocalDiagnostics(context.filesDir).export().toByteArray())} ?: error("Could not write diagnostics"); null
    }}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null) scope.launch {
        val result=withContext(Dispatchers.IO){runCatching{
            val bytes=context.contentResolver.openInputStream(uri)?.use{ConfigurationTransfer.readBounded(it)} ?: error("Could not read file")
            require(bytes.size<=1048576){"File exceeds 1 MB"}; val text=bytes.toString(Charsets.UTF_8); ConfigurationTransfer.parseDocument(text); text
        }}
        result.onSuccess{importText=it}.onFailure{notice=it.message ?: "Invalid configuration"}
    }}
    LaunchedEffect(state.session?.id) {
        while(isActive) {
            now=System.currentTimeMillis()
            val session=store.state.value.session
            if(session!=null && RestrictedSession.canRelease(session.releasePolicy,false,RestrictedSession.expired(session,AndroidClock(context).now(session.zoneId)))) store.refresh()
            delay(1000)
        }
    }
    Scaffold(bottomBar={NavigationBar {listOf("Blockers","Restricted Mode","More").forEachIndexed { index,label -> NavigationBarItem(selected=tab==index,onClick={tab=index},icon={Text(listOf("□","◷","⋯")[index])},label={Text(label)})}}}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
            Text("FocusGate",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(vertical=12.dp))
            if(!ready) Text(Graph.storageError ?: "Loading local configuration…")
            else {
                if(notice.isNotBlank()) {Text(notice,color=MaterialTheme.colorScheme.primary); TextButton(onClick={notice=""}){Text("Dismiss")}}
                when(tab) {
                    0 -> LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        item {Text(if(state.session==null) "Rules enforce now. Configuration is editable." else "Configuration locked. Enabled rules still enforce.")}
                        item {Row {Button(onClick={editor=null; showEditor=true}){Text("Add blocker")}; Spacer(Modifier.width(8.dp)); OutlinedButton(enabled=state.session==null,onClick={val presets=PresetFactory.create(); action{store.updatePolicy(ConfigurationTransfer.merge(state.policy,presets),state.policy.revision)}}){Text("Add presets")}}}
                        if(state.policy.blockers.isEmpty()) item{Text("Start with editable presets or create your own blocker. No rule is hardcoded.")}
                        items(state.policy.blockers,key={it.id}) { rule ->
                            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(rule.name,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f)); Switch(checked=rule.enabled,enabled=state.session==null,onCheckedChange={enabled->action{store.updatePolicy(state.policy.copy(blockers=state.policy.blockers.map{if(it.id==rule.id) it.copy(enabled=enabled) else it}),state.policy.revision)}})}
                                val clock=displayClock
                                val active=rule.enabled && ScheduleEvaluator.isActive(rule.schedule,clock.instant,ZoneId.of(clock.zoneId))
                                Text(when { !rule.enabled->"Disabled"; !active->"Schedule inactive"; rule.quotaGroupId==null->"Blocks while schedule is active"; else->"Shared time left: ${minutes(quotaDisplay[rule.quotaGroupId]?.remainingMillis ?: 0)}"})
                                rule.quotaGroupId?.let {id->state.policy.groups.find{it.id==id}?.let { group ->
                                    Text("${group.allowanceMillis/60000} min per clock ${group.period.name.lowercase()} · resets ${BucketClock.next(group.period,clock).atZone(ZoneId.of(clock.zoneId)).toLocalTime()}")
                                    group.continuousCapMillis?.let{cap->
                                        Text("Continuous cap ${cap/60000} min; break ${group.requiredBreakMillis/60000} min")
                                        val breakLeft=quotaDisplay[id]?.breakRemainingMillis ?: 0
                                        if(breakLeft>0) Text("Break left: ${minutes(breakLeft)}")
                                    }
                                }}
                                Text(rule.targets.joinToString(", "){it.value},style=MaterialTheme.typography.bodySmall)
                                Text("Days ${rule.schedule.days.sorted().joinToString()} · ${rule.schedule.windows.joinToString{it.start+"–"+it.end}.ifBlank{"all day"}}")
                                ScheduleEvaluator.nextTransition(rule.schedule,clock.instant,ZoneId.of(clock.zoneId))?.let{Text("Next schedule change: ${it.atZone(ZoneId.of(clock.zoneId))}",style=MaterialTheme.typography.bodySmall)}
                                Row {TextButton(onClick={editor=rule;showEditor=true}){Text(if(state.session==null) "Edit" else "Add targets / strengthen")}; TextButton(enabled=state.session==null,onClick={action{store.updatePolicy(state.policy.copy(blockers=state.policy.blockers.filter{it.id!=rule.id}),state.policy.revision)}}){Text("Delete")}}
                            }}
                        }
                    }
                    1 -> Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(if(state.session==null) "Configuration unlocked" else "Restricted Mode active",style=MaterialTheme.typography.titleLarge)
                        Text(if(connected) "Accessibility connected" else "Enforcement unavailable: Accessibility disconnected",color=if(connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        val session=state.session
                        if(session==null) LockControls(state,connected,{disclosure=true},::action)
                        else {
                            Text("Release: ${session.releasePolicy.name.replace('_',' ')}")
                            Text(session.deadlineUtcMillis?.let{"Lock time left: ${minutes(it-now)}"} ?: "No automatic deadline")
                            Text("Release unlocks editing. Blockers stay enabled until you change them.")
                            PasswordField("Trusted-person password","Release",{chars->action{store.release(chars)}},enabled=session.releasePolicy!=ReleasePolicy.TIMER)
                            if(session.releasePolicy==ReleasePolicy.TIMER) Text("This session releases at its deadline; there is no password override.")
                        }
                        ProtectionControls(state,::action)
                    }
                    2 -> Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("Setup and health",style=MaterialTheme.typography.titleLarge)
                        Text(if(connected) "Accessibility: connected" else "Accessibility: missing/disconnected")
                        val foreground by ServiceStatus.foreground.collectAsState()
                        Text(if(foreground) "Background protection: active" else "Background protection: inactive. With enabled blockers, reopen FocusGate to retry.")
                        Button(onClick={disclosure=true}){Text("Accessibility setup")}
                        Text("Xiaomi / HyperOS setup: enable FocusGate in Background autostart, then choose No restrictions in Battery saver. Background autostart is separate from Other permissions → Start in background. These switches cannot be reliably verified by a normal app.")
                        OutlinedButton(enabled=state.session==null && state.settings.settingsMode==0,onClick={SetupScreens.autostart(context)}){Text("Open Background autostart")}
                        OutlinedButton(enabled=state.session==null && state.settings.settingsMode==0,onClick={SetupScreens.battery(context)}){Text("Open Battery saver")}
                        Text("If Accessibility says malfunctioning, turn FocusGate off and on in Accessibility settings. Reopening the app alone may not reconnect it. After setup, remove FocusGate from Recents and test a blocked app again.",style=MaterialTheme.typography.bodySmall)
                        Text("A persistent service notification helps keep enforcement alive without extra polling. Android or HyperOS can still interrupt it. Notification visibility is optional; granting notifications lets you see the status.",style=MaterialTheme.typography.bodySmall)
                        if(Build.VERSION.SDK_INT>=33) OutlinedButton(onClick={notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Allow status notifications")}
                        OutlinedButton(enabled=state.session==null && state.settings.settingsMode==0,onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}){Text("Open app setup settings")}
                        Text("If Settings protection prevents repair, release the session with its configured password/deadline first. No general repair bypass is offered.",style=MaterialTheme.typography.bodySmall)
                        val browser by ServiceStatus.browser.collectAsState(); Text(browser)
                        Text("Sensitive Settings and Recents: experimental, phone verification pending. Whole-Settings mode is broader; select it explicitly.",style=MaterialTheme.typography.bodySmall)
                        PreferenceRow("Remaining-time notification",state.countdown){enabled->if(enabled && Build.VERSION.SDK_INT>=33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS); action{store.preferences(state.diagnostics,enabled,state.popup)}}
                        PreferenceRow("Show block explanation",state.popup){action{store.preferences(state.diagnostics,state.countdown,it)}}
                        PreferenceRow("Local developer diagnostics",state.diagnostics){action{store.preferences(it,state.countdown,state.popup)}}
                        Row {OutlinedButton(onClick={export.launch("focusgate-config.json")}){Text("Export")}; Spacer(Modifier.width(8.dp)); OutlinedButton(enabled=state.session==null,onClick={import.launch(arrayOf("application/json","text/plain"))}){Text("Import")}}
                        OutlinedButton(onClick={exportLogs.launch("focusgate-diagnostics.txt")}){Text("Export local diagnostics")}
                        MatchTester()
                        Text("Why FocusGate?",style=MaterialTheme.typography.titleLarge)
                        Text("FocusGate adds a pause between an impulse and another hour of scrolling. Choose your rules, test them, then lock them for a commitment you want to keep. Your settings stay on this phone.")
                        Text("Quick guide",style=MaterialTheme.typography.titleLarge)
                        Text("1. Grant Accessibility after reading the disclosure.\n2. Add/edit presets and test redirection.\n3. Ask a trusted person to set the password.\n4. Test a short Restricted Mode session before a day/week.\n5. Release with the selected policy, edit blockers, and lock again anytime.")
                        Text("FAQ",style=MaterialTheme.typography.titleLarge)
                        Text("Why does blocking continue after expiry? Expiry unlocks configuration; it does not disable blockers.\n\nIs 15 minutes shared? Yes, across all targets in that quota group. A clock-hour reset permits another allowance; enable a continuous cap to prevent long sessions across boundaries.\n\nUnknown browser URL? Allowed by website rules. App quotas still apply. Embedded/private browsers may not expose URLs.\n\nCan I uninstall? Device Admin may require deactivation first. Settings protection adds friction while Accessibility runs, but this is not a device-owner security guarantee.\n\nForgot password? Your selected timer policy still applies. Password-only / password AND timer needs the password. There is no built-in reset bypass.\n\nDoes background playback count? No; the app redirects foreground UI and does not stop background audio.\n\nIs it offline? FocusGate has no networking permission. A cloud file provider or another app can use its own network. Choose local files for exports.\n\nCan it survive force-stop? Policy persists, but Android can stop enforcement. Reopen and check Accessibility after suspension.")
                        Text("Support development",style=MaterialTheme.typography.titleLarge)
                        if(DonationConfig.DONATION_ADDRESS.isBlank()) Text("Donation details can be added in a future release. No payments or network requests are made.")
                        else {Text(DonationConfig.DONATION_NETWORK); Text(DonationConfig.DONATION_ADDRESS); OutlinedButton(onClick={context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Donation address",DonationConfig.DONATION_ADDRESS))}){Text("Copy address")}}
                        Text("0.1.0 development · phone acceptance pending",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if(disclosure) AlertDialog(onDismissRequest={disclosure=false},title={Text("Accessibility disclosure")},text={Text("FocusGate observes foreground app identities and supported browser address bars, then returns Home when your rules deny access. It does not collect page bodies, messages, screenshots or browsing history, and has no network permissions. Disabling this grant interrupts enforcement. Enable it yourself in the next system screen.")},confirmButton={TextButton(onClick={disclosure=false;context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text("Continue to Android settings")}},dismissButton={TextButton(onClick={disclosure=false}){Text("Cancel")}})
    if(showEditor) BlockerEditor(editor,state,onDismiss={showEditor=false},onSave={policy->scope.launch{val error=withContext(Dispatchers.IO){store.updatePolicy(policy,state.policy.revision)}; if(error==null) {showEditor=false;notice="Saved."} else notice=error}})
    importText?.let {text-> val preview=ConfigurationTransfer.parseDocument(text); AlertDialog(onDismissRequest={importText=null},title={Text("Import preview")},text={Text("${preview.policy.blockers.size} blockers, ${preview.policy.groups.size} quota groups. No password, active session, counters or grants will be imported. Protection settings require fresh review. Choose merge or replace.")},confirmButton={TextButton(onClick={action{store.importConfiguration(text,true)};importText=null}){Text("Merge")}},dismissButton={Row{TextButton(onClick={action{store.importConfiguration(text,false)};importText=null}){Text("Replace")};TextButton(onClick={importText=null}){Text("Cancel")}}}) }
}

@Composable private fun PreferenceRow(label: String,checked: Boolean,enabled: Boolean=true,onChange: (Boolean)->Unit) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f));Switch(checked=checked,onCheckedChange=onChange,enabled=enabled)}}
@Composable private fun PasswordField(label: String,button: String,onSubmit:(CharArray)->Unit,enabled: Boolean=true) {
    var value by remember{mutableStateOf("")}
    OutlinedTextField(value,onValueChange={value=it},label={Text(label)},visualTransformation=PasswordVisualTransformation(),enabled=enabled,modifier=Modifier.fillMaxWidth())
    Button(enabled=enabled && value.isNotEmpty(),onClick={val chars=value.toCharArray();value="";onSubmit(chars)}){Text(button)}
}
/** Collect release conditions before starting a lock; the store repeats the safety checks. */
@Composable private fun LockControls(state: AppState,connected: Boolean,setup:()->Unit,action: (suspend()->String?)->Unit) {
    val store=Graph.store
    var mode by remember{mutableStateOf(ReleasePolicy.PASSWORD_OR_TIMER)}
    var duration by remember{mutableStateOf("1440")}
    var additions by remember{mutableStateOf(true)}
    var acknowledged by remember{mutableStateOf(false)}
    PasswordField(if(state.password==null) "Set trusted-person password (6+ chars)" else "Replace trusted-person password","Save password",{action{store.setPassword(it)}})
    Text("Release condition")
    ReleasePolicy.entries.forEach{entry->Row(Modifier.clickable{mode=entry}.fillMaxWidth()){RadioButton(selected=mode==entry,onClick={mode=entry});Text(entry.name.replace('_',' '),Modifier.padding(top=12.dp))}}
    Row {TextButton(onClick={duration="5"}){Text("5-min test")};TextButton(onClick={duration="1440"}){Text("Day")};TextButton(onClick={duration="10080"}){Text("Week")}}
    OutlinedTextField(duration,{duration=it},label={Text("Duration in minutes")},enabled=mode!=ReleasePolicy.PASSWORD)
    PreferenceRow("Allow stronger additions while locked",additions){additions=it}
    Text("${state.policy.blockers.count{it.enabled}} enabled blockers will keep enforcing. Editing/disabling will be locked. Password-only has no automatic expiry; AND still requires the password after its deadline.")
    Row {Checkbox(acknowledged,{acknowledged=it});Text("I tested my rules and accept the listed browser/OEM coverage limits.",Modifier.padding(top=10.dp).weight(1f))}
    if(!connected) OutlinedButton(onClick=setup){Text("Set up Accessibility")}
    Button(enabled=connected && acknowledged,onClick={action{val ms=duration.toLongOrNull()?.let{Math.multiplyExact(it,60000)} ?: return@action "Enter a valid duration.";store.start(mode,ms,additions,ServiceStatus.connected.value)}}){Text("Start Restricted Mode")}
}
/** Optional normal-Android friction; these controls never claim device-owner privileges. */
@Composable private fun ProtectionControls(state: AppState,action:(suspend()->String?)->Unit) {
    val context=LocalContext.current; val store=Graph.store
    val manager=context.getSystemService(DevicePolicyManager::class.java)
    val component=ComponentName(context,FocusGateAdminReceiver::class.java)
    Text("Device protections",style=MaterialTheme.typography.titleLarge)
    Text("Device Admin: ${if(manager.isAdminActive(component)) "active" else "not active"}. Adds ordinary uninstall friction, not a privileged uninstall guarantee.")
    PreferenceRow("Uninstall resistance",state.settings.uninstallResistance,state.session==null){enabled->action{
        val error=store.updateSettings(state.settings.copy(uninstallResistance=enabled))
        if(error==null) withContext(Dispatchers.Main) {
            if(enabled) context.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,component).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Adds an administrator deactivation step before uninstalling FocusGate. No wipe or device-password policies are requested."))
            else if(manager.isAdminActive(component)) manager.removeActiveAdmin(component)
        }
        error
    }}
    Text("Settings protection")
    listOf("Off","Sensitive screens (experimental)","Whole Settings").forEachIndexed{index,label->Row(Modifier.fillMaxWidth()){RadioButton(selected=state.settings.settingsMode==index,enabled=state.session==null,onClick={action{store.updateSettings(state.settings.copy(settingsMode=index))}});Text(label,Modifier.padding(top=12.dp))}}
    PreferenceRow("Wi-Fi/mobile screen exceptions (when recognized)",state.settings.networkExceptions,state.session==null){action{store.updateSettings(state.settings.copy(networkExceptions=it))}}
    PreferenceRow("Return Home from recognized Recents",state.settings.recents,state.session==null){action{store.updateSettings(state.settings.copy(recents=it))}}
    Text("Whole Settings can prevent permission repair; release the lock first when no safely scoped repair route is available. Recents and sensitive-screen detection vary by HyperOS version.",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun MatchTester() {
    var host by remember{mutableStateOf("youtube.com")};var url by remember{mutableStateOf("")};var result by remember{mutableStateOf("")};var regex by remember{mutableStateOf(false)}
    Text("Local website rule tester",style=MaterialTheme.typography.titleMedium)
    PreferenceRow("Advanced regex (full match)",regex){regex=it}
    OutlinedTextField(host,{host=it},label={Text(if(regex) "Linear-time regex" else "Domain")})
    OutlinedTextField(url,{url=it},label={Text("URL to test (not saved)")})
    OutlinedButton(onClick={result=if(UrlMatcher.matches(if(regex) Target.UrlRegex(host) else Target.Host(host),url)) "Matches" else "Does not match / unidentified URL"}){Text("Test locally")}; if(result.isNotBlank()) Text(result)
}

/** Build an editable policy proposal. Validation and locked-mode mutation checks occur on save. */
@Composable private fun BlockerEditor(existing: Blocker?,state: AppState,onDismiss:()->Unit,onSave:(PolicySnapshot)->Unit) {
    val context=LocalContext.current
    val locked=state.session!=null
    val ruleId=remember{existing?.id ?: UUID.randomUUID().toString()}
    val group=state.policy.groups.find{it.id==existing?.quotaGroupId}
    val groupId=remember{group?.id ?: UUID.randomUUID().toString()}
    var name by remember{mutableStateOf(existing?.name ?: "New blocker")}
    var targets by remember{mutableStateOf(existing?.targets?.joinToString("\n"){"${it.kind}:${it.value}"} ?: "")}
    var days by remember{mutableStateOf(existing?.schedule?.days?.sorted()?.joinToString(",") ?: "1,2,3,4,5,6,7")}
    var windows by remember{mutableStateOf(existing?.schedule?.windows?.joinToString(";"){it.start+"-"+it.end} ?: "")}
    var start by remember{mutableStateOf(existing?.schedule?.startDate ?: "")};var end by remember{mutableStateOf(existing?.schedule?.endDateExclusive ?: "")}
    var message by remember{mutableStateOf(existing?.message ?: "Time for something you chose to do.")}
    var quota by remember{mutableStateOf(group!=null)};var allowance by remember{mutableStateOf((group?.allowanceMillis?.div(60000) ?: 15).toString())}
    var period by remember{mutableStateOf(group?.period ?: QuotaPeriod.HOUR)}
    var cap by remember{mutableStateOf(group?.continuousCapMillis!=null)};var capMinutes by remember{mutableStateOf((group?.continuousCapMillis?.div(60000) ?: 15).toString())};var breakMinutes by remember{mutableStateOf((group?.requiredBreakMillis?.div(60000) ?: 5).toString())}
    var picker by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null) "Create blocker" else "Edit blocker")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(locked) Text("Only stronger additions can be saved; the mutation guard checks this independently of the UI.")
        OutlinedTextField(name,{name=it},label={Text("Name")},enabled=!locked || existing==null)
        OutlinedButton(onClick={picker=true}){Text("Choose installed apps")}
        OutlinedTextField(targets,{targets=it},label={Text("One target per line")},supportingText={Text("app:package.id\nhost:youtube.com (includes subdomains)\nregex:https://example\\.com/.* (full match)")},minLines=3)
        OutlinedTextField(days,{days=it},label={Text("Weekdays: Mon=1 … Sun=7")},enabled=!locked || existing==null)
        Row {TextButton(enabled=!locked || existing==null,onClick={days="1,2,3,4,5"}){Text("Weekdays")};TextButton(enabled=!locked || existing==null,onClick={days="6,7"}){Text("Weekend")}}
        OutlinedTextField(windows,{windows=it},label={Text("Active windows; blank = all day")},supportingText={Text("09:00-17:00;22:00-02:00. Outside these windows this rule does nothing.")},enabled=!locked || existing==null)
        OutlinedTextField(start,{start=it},label={Text("Start date YYYY-MM-DD (optional)")},enabled=!locked || existing==null)
        OutlinedTextField(end,{end=it},label={Text("End date exclusive (optional)")},enabled=!locked || existing==null)
        PreferenceRow("Usage allowance instead of continuous block",quota,!locked || existing==null){quota=it}
        if(quota) {
            OutlinedTextField(allowance,{allowance=it},label={Text("Shared allowance in minutes")})
            Row {QuotaPeriod.entries.forEach{entry->FilterChip(selected=period==entry,onClick={period=entry},enabled=!locked || existing==null,label={Text("Per clock ${entry.name.lowercase()}")})}}
            PreferenceRow("Maximum continuous session",cap){cap=it}
            if(cap) {OutlinedTextField(capMinutes,{capMinutes=it},label={Text("Maximum session in minutes")});OutlinedTextField(breakMinutes,{breakMinutes=it},label={Text("Uninterrupted break in minutes")})}
            Text("Allowance is shared across every app/site selected here. Switching targets does not reset it; there is no rollover.",style=MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(message,{message=it},label={Text("Block explanation")},enabled=!locked || existing==null)
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={runCatching{
        val parsed=targets.lines().filter{it.isNotBlank()}.map{line->val kind=line.substringBefore(':').trim();val value=line.substringAfter(':',"").trim();Target(kind,value,kind=="host")}.distinct()
        val schedule=Schedule(days.split(',').filter{it.isNotBlank()}.map{it.trim().toInt()}.toSet(),windows.split(';').filter{it.isNotBlank()}.map{val parts=it.trim().split('-');require(parts.size==2);TimeWindow(parts[0].trim(),parts[1].trim())},start.ifBlank{null},end.ifBlank{null})
        val rule=Blocker(ruleId,name,existing?.enabled ?: true,parsed,schedule,if(quota) groupId else null,message)
        val editedGroup=if(quota) QuotaGroup(groupId,period,Math.multiplyExact(allowance.toLong(),60000),if(cap) Math.multiplyExact(capMinutes.toLong(),60000) else null,Math.multiplyExact(breakMinutes.toLong(),60000)) else null
        val policy=state.policy.copy(blockers=state.policy.blockers.filter{it.id!=ruleId}+rule,groups=if(editedGroup!=null) state.policy.groups.filter{it.id!=groupId}+editedGroup else state.policy.groups)
        ConfigurationTransfer.validate(policy);onSave(policy)
    }.onFailure{error=it.message ?: "Check the entered values."}}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
    if(picker) AppPicker(onDismiss={picker=false},onPick={pkg->val line="app:$pkg";if(line !in targets.lines()) targets=targets.trim()+"\n"+line})
}
@Composable private fun AppPicker(onDismiss:()->Unit,onPick:(String)->Unit) {
    val context=LocalContext.current;var apps by remember{mutableStateOf<List<Pair<String,String>>>(emptyList())};var query by remember{mutableStateOf("")}
    LaunchedEffect(Unit){apps=withContext(Dispatchers.IO){val home=context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)?.activityInfo?.packageName;context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).map{it.loadLabel(context.packageManager).toString() to it.activityInfo.packageName}.distinctBy{it.second}.filter{it.second !in setOf(context.packageName,home,"com.android.dialer","com.google.android.dialer","com.android.phone","com.android.emergency")}.sortedBy{it.first.lowercase()}}}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Installed apps")},text={Column{OutlinedTextField(query,{query=it},label={Text("Search")});LazyColumn(Modifier.height(350.dp)){items(apps.filter{it.first.contains(query,true)||it.second.contains(query,true)},key={it.second}){(label,pkg)->TextButton(onClick={onPick(pkg)}){Column{Text(label);Text(pkg,style=MaterialTheme.typography.bodySmall)}}}}}},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}
