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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
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
                    1 -> Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        val session=state.session
                        SettingsSection("Status") {
                            Text(if(session==null) "Configuration unlocked" else "Restricted Mode active",style=MaterialTheme.typography.titleMedium)
                            Text(if(connected) "Accessibility connected" else "Enforcement unavailable: Accessibility disconnected",color=if(connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Text("Enabled blockers work whether configuration is locked or unlocked.")
                        }
                        if(session==null) {
                            SettingsSection("Password") { PasswordManagement(state.password!=null,store::updatePassword,store::removePassword) }
                            SettingsSection("Lock session") { LockControls(state,connected,{disclosure=true},::action) }
                        } else SettingsSection("Active session") {
                            Text("Release: ${releaseLabel(session.releasePolicy)}")
                            Text(session.deadlineUtcMillis?.let{"Lock time left: ${minutes(it-now)}"} ?: "No automatic deadline",style=MaterialTheme.typography.titleMedium)
                            Text("Unlocking allows editing. Your password stays saved and blockers stay enabled.")
                            if(session.releasePolicy!=ReleasePolicy.TIMER) PasswordField("Trusted-person password","Unlock configuration",{chars->action{store.release(chars)}})
                            if(session.releasePolicy==ReleasePolicy.TIMER) Text("This session releases at its deadline; there is no password override.")
                        }
                        SettingsSection("Device protections") { ProtectionControls(state,::action) }
                    }
                    2 -> Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        SettingsSection("Protection status") {
                            Text(if(connected) "Accessibility: connected" else "Accessibility: missing or disconnected",color=if(connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            val foreground by ServiceStatus.foreground.collectAsState()
                            Text(if(foreground) "Background protection: active" else "Background protection: inactive. With enabled blockers, reopen FocusGate to retry.")
                            val browser by ServiceStatus.browser.collectAsState()
                            Text(browser)
                            Text("Sensitive Settings and Recents detection is experimental; phone verification is pending.",style=MaterialTheme.typography.bodySmall)
                        }
                        SettingsSection("Phone setup") {
                            Text("On Xiaomi, enable Background autostart and choose No restrictions in Battery saver.")
                            Button(onClick={disclosure=true}){Text("Set up Accessibility")}
                            OutlinedButton(enabled=state.session==null && state.settings.settingsMode==0,onClick={SetupScreens.autostart(context)}){Text("Open Background autostart")}
                            OutlinedButton(enabled=state.session==null && state.settings.settingsMode==0,onClick={SetupScreens.battery(context)}){Text("Open Battery saver")}
                            OutlinedButton(enabled=state.session==null && state.settings.settingsMode==0,onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}){Text("Open app settings")}
                            ExpandableDetails("Setup help") {
                                Text("Background autostart is separate from Other permissions → Start in background. Check both the autostart and battery settings yourself; FocusGate cannot reliably verify them.")
                                Text("If Accessibility says malfunctioning, turn FocusGate off and on in Accessibility settings. Reopening the app alone may not reconnect it. After setup, remove FocusGate from Recents and test a blocked app again.")
                                Text("If Settings protection prevents repair, unlock configuration with your password or deadline, then turn off Settings protection.")
                            }
                        }
                        SettingsSection("Display & feedback") {
                            PreferenceRow("Show remaining time in a notification",state.countdown){enabled->if(enabled && Build.VERSION.SDK_INT>=33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS); action{store.preferences(state.diagnostics,enabled,state.popup)}}
                            PreferenceRow("Show an explanation when blocked",state.popup){action{store.preferences(state.diagnostics,state.countdown,it)}}
                            if(Build.VERSION.SDK_INT>=33) OutlinedButton(onClick={notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Allow status notifications")}
                            Text("The background service uses a persistent notification to help keep enforcement running. Android or HyperOS can still interrupt it.",style=MaterialTheme.typography.bodySmall)
                        }
                        SettingsSection("Backups") {
                            Text("Save or restore blocker configuration. Passwords, active sessions, usage counters and logs are excluded.")
                            OutlinedButton(onClick={export.launch("focusgate-config.json")}){Text("Export configuration")}
                            OutlinedButton(enabled=state.session==null,onClick={import.launch(arrayOf("application/json","text/plain"))}){Text("Import configuration")}
                            if(state.session!=null) Text("Unlock configuration before importing.",style=MaterialTheme.typography.bodySmall)
                        }
                        SettingsSection("Help & FAQ") {
                            ExpandableDetails("Read the guide and common questions") {
                                Text("Getting started",style=MaterialTheme.typography.titleSmall)
                                Text("1. Grant Accessibility after reading the disclosure.\n2. Add or edit blockers and test redirection.\n3. Ask a trusted person to set the password.\n4. Test a short Restricted Mode session before a day or week.\n5. Unlock, adjust your rules, and start a new session anytime.")
                                HorizontalDivider()
                                Text("Why does blocking continue after expiry?",style=MaterialTheme.typography.titleSmall)
                                Text("Expiry unlocks configuration. Enabled blockers keep working until you change them.")
                                Text("Do I set the password again?",style=MaterialTheme.typography.titleSmall)
                                Text("No. It stays saved across expiry and new locks. Each Start begins a fresh duration. Changing the password requires the current password and matching new entries.")
                                Text("Can I remove the password?",style=MaterialTheme.typography.titleSmall)
                                Text("Yes, while configuration is unlocked, using Remove password and your current password. Password only and Password OR timer then require a new password. You can choose Timer only, which has no early password release. Setting a new password after removal needs no old password.")
                                Text("Is the allowance shared?",style=MaterialTheme.typography.titleSmall)
                                Text("Yes, across the targets in a quota group. A clock-hour reset grants another allowance. An optional continuous-session cap limits use across that boundary.")
                                Text("What happens when a browser URL is unknown?",style=MaterialTheme.typography.titleSmall)
                                Text("Website rules allow it. App rules still apply. Private tabs and embedded browsers may not expose a URL.")
                                Text("What if I forget the password?",style=MaterialTheme.typography.titleSmall)
                                Text("Your selected timer policy still applies. Password-only requires the password. Changing or removing a saved password requires the current one; there is no forgotten-password reset.")
                                Text("Can I uninstall or stop the app?",style=MaterialTheme.typography.titleSmall)
                                Text("Device Admin may require deactivation before uninstalling. Settings protection adds friction while Accessibility runs. Force-stop can interrupt enforcement even though your configuration remains saved; reopen and check Accessibility afterward.")
                                Text("Does background playback count?",style=MaterialTheme.typography.titleSmall)
                                Text("No. FocusGate redirects foreground apps and does not stop background audio.")
                                Text("Is it offline?",style=MaterialTheme.typography.titleSmall)
                                Text("FocusGate has no networking permission. A cloud file provider can use its own network, so choose local files for exports.")
                            }
                        }
                        SettingsSection("Developer tools") {
                            ExpandableDetails("Show diagnostics and website tester") {
                                PreferenceRow("Record local diagnostics",state.diagnostics){action{store.preferences(it,state.countdown,state.popup)}}
                                Text("Optional local event records for troubleshooting; no browsing history or passwords.",style=MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick={exportLogs.launch("focusgate-diagnostics.txt")}){Text("Export diagnostics")}
                                HorizontalDivider()
                                MatchTester()
                            }
                        }
                        SettingsSection("About & support") {
                            Text("Choose rules that help you step away from distracting apps, then lock them for a commitment you want to keep.")
                            Text("0.1.0 development · phone acceptance in progress",style=MaterialTheme.typography.bodySmall)
                            if(DonationConfig.DONATION_ADDRESS.isBlank()) Text("Donation details may be added in a future release. No payments or network requests are made.")
                            else {Text(DonationConfig.DONATION_NETWORK); Text(DonationConfig.DONATION_ADDRESS); OutlinedButton(onClick={context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Donation address",DonationConfig.DONATION_ADDRESS))}){Text("Copy address")}}
                        }
                    }
                }
            }
        }
    }
    if(disclosure) AlertDialog(onDismissRequest={disclosure=false},title={Text("Accessibility disclosure")},text={Text("FocusGate observes foreground app identities and supported browser address bars, then returns Home when your rules deny access. It does not collect page bodies, messages, screenshots or browsing history, and has no network permissions. Disabling this grant interrupts enforcement. Enable it yourself in the next system screen.")},confirmButton={TextButton(onClick={disclosure=false;context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text("Continue to Android settings")}},dismissButton={TextButton(onClick={disclosure=false}){Text("Cancel")}})
    if(showEditor) BlockerEditor(editor,state,onDismiss={showEditor=false},onSave={policy->scope.launch{val error=withContext(Dispatchers.IO){store.updatePolicy(policy,state.policy.revision)}; if(error==null) {showEditor=false;notice="Saved."} else notice=error}})
    importText?.let {text-> val preview=ConfigurationTransfer.parseDocument(text); AlertDialog(onDismissRequest={importText=null},title={Text("Import preview")},text={Text("${preview.policy.blockers.size} blockers, ${preview.policy.groups.size} quota groups. No password, active session, counters or grants will be imported. Protection settings require fresh review. Choose merge or replace.")},confirmButton={TextButton(onClick={action{store.importConfiguration(text,true)};importText=null}){Text("Merge")}},dismissButton={Row{TextButton(onClick={action{store.importConfiguration(text,false)};importText=null}){Text("Replace")};TextButton(onClick={importText=null}){Text("Cancel")}}}) }
}

/** Group related controls without coupling their visibility to policy or persistence. */
@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,style=MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

/** Secondary help stays reachable without making everyday settings a wall of text. */
@Composable private fun ExpandableDetails(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()) {
        Text(title,modifier=Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(if(expanded) "Hide −" else "Show +")
    }
    if(expanded) Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}

/** The label and radio button form one accessible tap target, including disabled lock controls. */
@Composable private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean=true, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected=selected,enabled=enabled,role=Role.RadioButton,onClick=onSelect).heightIn(min=48.dp).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
        RadioButton(selected=selected,onClick=null,enabled=enabled)
        Spacer(Modifier.width(12.dp))
        Text(label,color=if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=0.38f))
    }
}

@Composable private fun PreferenceRow(label: String,checked: Boolean,enabled: Boolean=true,onChange: (Boolean)->Unit) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Spacer(Modifier.width(12.dp));Switch(checked=checked,onCheckedChange=onChange,enabled=enabled)}}
@Composable private fun PasswordField(label: String,button: String,onSubmit:(CharArray)->Unit,enabled: Boolean=true) {
    var value by remember{mutableStateOf("")}
    PasswordInput(label,value,enabled){value=it}
    Button(enabled=enabled && value.isNotEmpty(),onClick={val chars=value.toCharArray();value="";onSubmit(chars)}){Text(button)}
}
/** Collect release conditions before starting a lock; the store repeats the safety checks. */
@Composable private fun LockControls(state: AppState,connected: Boolean,setup:()->Unit,action: (suspend()->String?)->Unit) {
    val store=Graph.store
    val saved=state.lockPreferences ?: LockPreferences()
    val modes=listOf(ReleasePolicy.PASSWORD_OR_TIMER,ReleasePolicy.PASSWORD,ReleasePolicy.TIMER)
    var mode by remember{mutableStateOf(saved.releasePolicy.takeIf{it in modes} ?: ReleasePolicy.PASSWORD_OR_TIMER)}
    var duration by remember{mutableStateOf((saved.durationMillis/60000).toString())}
    var additions by remember{mutableStateOf(saved.allowRestrictiveAdditions)}
    var acknowledged by remember{mutableStateOf(false)}
    Text("Release condition")
    modes.forEach{entry->ChoiceRow(releaseLabel(entry),mode==entry,enabled=entry==ReleasePolicy.TIMER || state.password!=null){mode=entry}}
    if(state.password==null) Text("No password set. Choose Timer only, or set a password above to use either password option.")
    Text(when(mode) {
        ReleasePolicy.PASSWORD_OR_TIMER -> if(state.password==null) "Password OR timer cannot start without a password. It does not automatically become Timer only." else "The saved password unlocks early, or the timer unlocks automatically."
        ReleasePolicy.PASSWORD -> if(state.password==null) "Password only cannot start without a password." else "The saved password is required to unlock. There is no automatic expiry."
        else -> "The timer unlocks automatically. A password cannot end this session early."
    })
    if(mode!=ReleasePolicy.PASSWORD) {
        Row {TextButton(onClick={duration="5"}){Text("5-min test")};TextButton(onClick={duration="1440"}){Text("Day")};TextButton(onClick={duration="10080"}){Text("Week")}}
        OutlinedTextField(duration,{duration=it},label={Text("Duration in minutes")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        Text("Every Start begins the full duration again. Your last successful choices are remembered.")
    }
    PreferenceRow("Allow stronger additions while locked",additions){additions=it}
    Text("${state.policy.blockers.count{it.enabled}} enabled blockers will keep enforcing. Unlocking allows editing; it keeps your password and leaves blockers enabled.")
    Row {Checkbox(acknowledged,{acknowledged=it});Text("I tested my rules and accept the listed browser/OEM coverage limits.",Modifier.padding(top=10.dp).weight(1f))}
    if(!connected) OutlinedButton(onClick=setup){Text("Set up Accessibility")}
    val needsPassword=mode!=ReleasePolicy.TIMER
    if(needsPassword && state.password==null) Text("Set a password before starting this mode.",color=MaterialTheme.colorScheme.error)
    Button(enabled=connected && acknowledged && (!needsPassword || state.password!=null),onClick={action{
        // Password-only has no deadline and must not parse an inactive duration field.
        val ms=if(mode==ReleasePolicy.PASSWORD) saved.durationMillis else {
            val value=duration.toLongOrNull()?.takeIf{it in 1L..525600L} ?: return@action "Choose 1 to 525600 minutes."
            value*60000L
        }
        store.start(mode,ms,additions,ServiceStatus.connected.value)
    }}){Text("Start Restricted Mode")}
}
/** AND is displayed only when reading a session created by an older build. */
private fun releaseLabel(mode: ReleasePolicy)=when(mode) {
    ReleasePolicy.PASSWORD -> "Password only"
    ReleasePolicy.TIMER -> "Timer only"
    ReleasePolicy.PASSWORD_OR_TIMER -> "Password or timer"
    ReleasePolicy.PASSWORD_AND_TIMER -> "Password and timer (existing session)"
}
/** Optional normal-Android friction; these controls never claim device-owner privileges. */
@Composable private fun ProtectionControls(state: AppState,action:(suspend()->String?)->Unit) {
    val context=LocalContext.current; val store=Graph.store
    val manager=context.getSystemService(DevicePolicyManager::class.java)
    val component=ComponentName(context,FocusGateAdminReceiver::class.java)
    Text("Device Admin: ${if(manager.isAdminActive(component)) "active" else "not active"}.")
    Text("Device Admin may add a deactivation step before uninstalling. Android can still interrupt protection.",style=MaterialTheme.typography.bodySmall)
    PreferenceRow("Uninstall resistance",state.settings.uninstallResistance,state.session==null){enabled->action{
        val error=store.updateSettings(state.settings.copy(uninstallResistance=enabled),state.settings)
        if(error==null) withContext(Dispatchers.Main) {
            if(enabled) context.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,component).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Adds an administrator deactivation step before uninstalling FocusGate. No wipe or device-password policies are requested."))
            else if(manager.isAdminActive(component)) manager.removeActiveAdmin(component)
        }
        error
    }}
    HorizontalDivider()
    Text("Block access to Android Settings",style=MaterialTheme.typography.titleMedium)
    listOf("Off","Sensitive settings only (experimental)","All Settings screens").forEachIndexed{index,label->ChoiceRow(label,state.settings.settingsMode==index,state.session==null){action{store.updateSettings(state.settings.copy(settingsMode=index),state.settings)}}}
    PreferenceRow("Allow recognized Wi-Fi and mobile settings",state.settings.networkExceptions,state.session==null){action{store.updateSettings(state.settings.copy(networkExceptions=it),state.settings)}}
    HorizontalDivider()
    PreferenceRow("Return Home when Recents is detected",state.settings.recents,state.session==null){action{store.updateSettings(state.settings.copy(recents=it),state.settings)}}
    if(state.session!=null) Text("Unlock configuration to change device protections.",style=MaterialTheme.typography.bodySmall)
    Text("Blocking all Settings can prevent permission repair. Unlock configuration and turn this protection off before repairing grants. Detection of sensitive screens and Recents varies by HyperOS version.",style=MaterialTheme.typography.bodySmall)
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
        if(locked) Text("While locked, only changes that add restrictions can be saved.")
        OutlinedTextField(name,{name=it},label={Text("Name")},enabled=!locked || existing==null)
        HorizontalDivider()
        Text("Apps & websites",style=MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick={picker=true}){Text("Choose installed apps")}
        OutlinedTextField(targets,{targets=it},label={Text("Apps and websites")},supportingText={Text("One per line:\napp:package.id\nhost:youtube.com (includes subdomains)\nregex:https://example\\.com/.* (full match)")},minLines=3)
        HorizontalDivider()
        Text("Schedule",style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(days,{days=it},label={Text("Weekdays: Mon=1 … Sun=7")},enabled=!locked || existing==null)
        Row {TextButton(enabled=!locked || existing==null,onClick={days="1,2,3,4,5"}){Text("Weekdays")};TextButton(enabled=!locked || existing==null,onClick={days="6,7"}){Text("Weekend")}}
        OutlinedTextField(windows,{windows=it},label={Text("Active windows; blank = all day")},supportingText={Text("09:00-17:00;22:00-02:00. Outside these windows this rule does nothing.")},enabled=!locked || existing==null)
        OutlinedTextField(start,{start=it},label={Text("Start date YYYY-MM-DD (optional)")},enabled=!locked || existing==null)
        OutlinedTextField(end,{end=it},label={Text("End date exclusive (optional)")},enabled=!locked || existing==null)
        HorizontalDivider()
        Text("Blocking rule",style=MaterialTheme.typography.titleMedium)
        ChoiceRow("Always block during schedule",!quota,!locked || existing==null){quota=false}
        ChoiceRow("Allow limited usage",quota,!locked || existing==null){quota=true}
        Text(if(quota) "Allow access during the schedule until the shared time allowance runs out. Outside the schedule, this rule does not restrict access."
            else "Block the selected apps and websites whenever the schedule is active. Outside the schedule, this rule does not restrict access.",style=MaterialTheme.typography.bodySmall)
        if(quota) {
            OutlinedTextField(allowance,{allowance=it},label={Text("Shared allowance in minutes")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            Row {QuotaPeriod.entries.forEach{entry->FilterChip(selected=period==entry,onClick={period=entry},enabled=!locked || existing==null,label={Text("Per clock ${entry.name.lowercase()}")})}}
            PreferenceRow("Maximum continuous session",cap){cap=it}
            if(cap) {OutlinedTextField(capMinutes,{capMinutes=it},label={Text("Maximum session in minutes")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));OutlinedTextField(breakMinutes,{breakMinutes=it},label={Text("Uninterrupted break in minutes")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))}
            Text("Allowance is shared across every app/site selected here. Switching targets does not reset it; there is no rollover.",style=MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Text("Block message",style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(message,{message=it},label={Text("Explanation shown when blocked")},enabled=!locked || existing==null)
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
