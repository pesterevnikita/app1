# Development build verification - 2026-10-03

**TL;DR:** Latest full check: 69 unit tests pass, lint has zero errors/five warnings, debug and unsigned release builds pass. Main APK, Accessibility setup and native presets were tested on Xiaomi 11T. Phone acceptance is partial. User password/UI confirmation is separate from automated tests. Remaining work is in the [roadmap](../roadmap.md).

## Environment

Read-only ADB confirmed Android 14/API 34 on the target Xiaomi 11T. HyperOS 1.0.15.0 is user-reported. Chrome, Edge, Telegram, Ozon shopping, Ozon Bank, YouTube and Instagram packages were present. No device identifier is retained.

Toolchain: Java 21, Gradle 8.13, Android Gradle Plugin 8.11.2, Kotlin 2.0.21, compile/target SDK 36, min SDK 29. Wrapper checks the official distribution checksum. An installed SDK AAPT2 36.1.0 was selected through a local command-line override after Maven download timeout. Local setup stays outside tracked documentation.

## Local checks

Latest full task command:

```powershell
gradlew.bat :policy:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Result: `BUILD SUCCESSFUL`. 69 unit tests pass: 18 policy, 51 app. Lint: zero errors, five warnings. The separate Android test APK was built and exercised earlier. Local AAPT2 options were used with the task command.

Covered behavior:

- Enabled/disabled rules outside Restricted Mode; host boundaries; unknown URL allowance; overnight schedules; overlapping deny precedence.
- Shared foreground use, clock-hour resets, continuous caps and breaks across boundaries/restart, same-boot monotonic breaks under clock changes.
- Four release-policy truth tables, stronger-only edits, stale revisions, password verification and bounded diagnostics.
- Import/export validation and secret exclusions, invalid quota enums, bounded file reads on older Android.
- Static-foreground exhaustion, safe timer callbacks, pending observations during suspended checkpoint IO, preflight refusals.

Independent review found timer self-cancellation, dropped pending observations, invalid quota enums, wall-clock break billing and weak address-bar confidence checks. Each fix had a failing regression followed by a passing suite. A lint API error in bounded import reading was also fixed and tested.

Merged manifest: no INTERNET or ACCESS_NETWORK_STATE permission. Backup/cloud-transfer exclusions cover private and device-protected storage. Accessibility and Device Admin binding require platform permissions. Boot receiver is not exported. The user-approved maintenance provider remains in release, requires DUMP and exact shell UID, and exposes no release/credential command.

Configuration export contains no credentials, active sessions or counters. Source scans found no GitHub token or private phone identifier. Fake passwords appear only in tests.

Deferred lint warnings: API-31 `isAccessibilityTool=false` metadata is ignored by older systems; generic static-context warning for the application-context singleton; three optional KTX suggestions. These warnings do not prove any phone behavior.

## Swipe-away investigation

Android exit history reported HyperOS `SwipeUpClean`, with no observed AndroidRuntime crash. True Background autostart was initially off despite Other permissions setup. The user enabled the actual switch manually; its state and Battery saver / No restrictions were verified. Autostart allowed automatic reconnection with an observed gap.

The service now uses Android 14 `specialUse` foreground priority and a quiet ongoing notification. No extra polling or wake lock was added. System dumps confirmed foreground state. Repeated card removal kept the process. Feedback interruption no longer clears connection state or cancels quota timers. That fix and independent Settings/Recents eligibility have failing-then-passing regressions.

A timer test once failed under load: only one of two callbacks finished within a fixed 50 ms wait. The test now waits for actual completion under a timeout.

Do not use stock `uiautomator dump` for enforcement checks. It suppresses Accessibility temporarily and can show a disconnected UI. Use input/dumpsys, app-only screenshots or non-suppressing UiAutomation. Android instrumentation force-stops the target; here it left Accessibility malfunctioning. The user repaired it with off/on toggles, and final checks ran after reconnection. See [Android UiAutomation documentation](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).

## Phone results

| Check | Result | Evidence / next action |
| --- | --- | --- |
| ADB connection and device/package inspection | PASS | Read-only commands returned Android 14 and target packages |
| Debug/test APK installation | PASS | Main update and separate test APK installed with -r --no-streaming; user configuration preserved |
| Room reopen/transaction instrumentation | PASS | AndroidJUnitRunner: OK (1 test). Editable policy survived reopen; stale revision refused. Test-only Boolean return signature fixed after observed JUnit initialization failure |
| Accessibility grant/service connection | PASS | User granted manually; registered service and native blocking observed |
| Native blocking and latency | PARTIAL | Editable presets with Restricted Mode off returned YouTube/Instagram to com.miui.home. Exact latency not measured; no AndroidRuntime error in sampled log |
| Shared hourly budget and continuous cap | PARTIAL | Shared one-minute allowance exhausted across Chrome then Edge. Static page returned Home; both browsers then denied. Temporary one-minute cap returned static Chrome Home; full-break recovery observed. Exact latency and interrupted breaks remain unmeasured |
| Chrome/Edge versions and URL coverage | PARTIAL | Explicit Chrome Main launch of YouTube site and Edge launch of Instagram site returned Home. Chrome package-only launch first showed Xiaomi resolver. Versions, unknown URLs, hidden bars, private tabs and wider cases remain open |
| Device Admin / launcher uninstall friction | NOT RUN | Optional empty-policy admin declaration needs device confirmation |
| Sensitive/whole Settings, network exceptions, Recents | NOT RUN | Experimental class-based adapters; test actual HyperOS routes |
| Swipe-away and foreground promotion | PASS for observed scenario | Repeated FocusGate Recents-card removal kept PID, foreground service and connected Accessibility. YouTube returned Home. Final repeat used input/dumpsys without UiAutomation |
| Screen off/on, process/service death, launcher restart | PASS for short tested cases | Later recovery checks below cover manual unlock, screen-off billing, process death while idle/billing, service reconnection and launcher restart. Long idle remains open |
| Reboot and app-update recovery | PASS for tested cases | Active TIMER deadline and counters survived reboot and same-debug-APK replacement. Automatic post-unlock reconnection observed. Different-version/schema upgrade is not covered |
| Emergency UI, calls, split-screen/PiP | NOT RUN | Exclusions exist; phone behavior not measured |
| Password derivation timing and CPU/battery | NOT RUN | Default 210,000 iterations not phone-benchmarked |

Use short releasable locks for pending tests. Confirm release and Settings access before any long lock. See the roadmap for current order; old test plans are not completion evidence.

Generated debug APK: `app/build/outputs/apk/debug/app-debug.apk`, Git-ignored. It is debug-signed. No production signing key was created or committed.

## Maintenance and quota follow-up

The release-retained provider and PowerShell helper ran on the updated debug APK. Status reported connected/foreground service. A short TIMER lock refused rule disabling, allowance increases and a second lock. At expiry, settings unlocked while rules stayed enabled. Disabling the native preset allowed YouTube; reenabling returned its existing activity Home.

Android denied an app-UID `run-as` content call before the provider ran. Exact caller checks also have JVM coverage. Invalid IDs with punctuation/Unicode were refused without shell interpretation. A harmless UTF-8 printf checked Windows stdin transport.

Static Chrome crossed the clock-hour boundary under a one-minute continuous cap, then returned Home. An app-only screenshot showed 0:36 break left. After the break, without another billed foreground visit, the same card showed 1:00 and removed break text. Temporary settings were restored: 15 minutes/hour, cap off, five-minute break. Password OR/AND, reboot, Device Admin and Settings/Recents phone tests remain pending.

Debug and unsigned release builds passed. Release retained the DUMP-protected provider and had no INTERNET/ACCESS_NETWORK_STATE permission. Profile replacement passed on the phone with remaining usage unchanged. Import during a one-minute TIMER lock was refused.

Android CLI `--extra` rejected JSON containing colons. A strict JSON `--arg` envelope fixed that reproduced failure; parser regressions cover it.

Historical handoff after these tests: both presets enabled, 15 minutes/hour, cap off, five-minute break. The short TIMER lock expired normally. Later password/UI checks follow below. Never put private passwords in ADB, chat or logs.

## Password lifecycle follow-up

The user found blind replacement and unclear saved status. Code confirmed the old unlocked setter replaced a verifier without old-password verification or confirmation. Recreating the form also reset duration to one day. Fresh deadlines and verifier retention already existed.

The fix added saved/not-set status, set/change dialogs, current-password verification, matching new confirmation, shared persisted retry delays and transient buffer clearing. New sessions offer Password only, Timer only and Password OR timer. Legacy AND retains its release condition.

Last successful lock choices persist; maintenance timers leave them intact. Password-only start ignores inactive duration input. Eight lifecycle regressions brought the historical total to 65. Debug/release and lint passed. The later UI grouping build passed both variants and lint. No real credential was collected.

The debug update showed the existing password still set. Accessibility briefly disconnected during update, then reconnected automatically with foreground state. App-only screenshots verified Restricted Mode cards, More setup/status cards, explicit blocker choices and the empty three-field Change password dialog.

The user then reported everything worked as intended. This is general user confirmation. Private-entry steps and the full release-policy matrix were not instrumented.

## Password removal and eye controls

Four added lifecycle tests cover removal with current-password verification, rule/usage/preference retention, new setup without an old password, missing/wrong/stale input and shared cooldown, active-lock refusal under every release policy, and input clearing. A removed verifier blocks Password-only/OR starts, permits explicit Timer-only, and permits OR after setup.

Total: 69 JVM tests, no failures/errors/skips. Lint: zero errors/five warnings. Debug and unsigned release passed. Independent read-only review found no issues.

The update installed with `adb install -r --no-streaming`. Status reported connected/foreground Accessibility, Restricted Mode off, unchanged policy revision and both presets enabled. Allowance stayed 15 minutes/hour, cap off, break five minutes. Displayed remaining time stayed unchanged during the UI-only checks.

App-only screenshots verified Remove password and its explanation: OR needs a password and never silently changes to Timer-only. Disposable input started hidden, was revealed by the eye, then cancelled. Reopened input started hidden again; the dialog was cancelled. No real credential was entered, submitted, read or removed. Sampled AndroidRuntime output had no errors.

The user later said password behavior was fine. This is general confirmation, not instrumented removal/re-setup evidence. The user also confirmed removal stays unavailable in Restricted Mode and withdrew the unlock-and-remove suggestion. Product behavior is unchanged. Full release/relock and lifecycle acceptance remains open; see the [roadmap](../roadmap.md).

## 2026-10-04: schedule and support follow-up

A temporary Chrome app rule used a 12:47–12:48 window in the phone's Asia/Novosibirsk timezone. Chrome displayed `about:blank` before the window. At 12:47:22, `dumpsys activity` showed Home without a navigation action at the start boundary. A Chrome launch during the window also returned Home. At 12:48:28, a Chrome launch stayed in Chrome. This verifies the tested activation and expiry case. Exact redirection latency was not measured.

The original profile was restored after the test. Both presets are enabled. The shared allowance is 900,000 ms/hour, continuous cap is off, and the required break is 300,000 ms. Remaining usage was 789,215 ms before and after restoration. The test consumed normal browser usage; import did not reset it. Restricted Mode is off, and Accessibility is connected/foreground.

The support-link update passed 69 unit tests with no failures/errors/skips. Lint reported zero errors/five existing warnings. Debug and unsigned release assembly passed. The debug APK installed with `-r --no-streaming`.

An app-only screenshot verified the public repository URL, Copy repository link button, and Telegram feedback placeholder. Tapping the button showed Repository link copied. The agent did not read the prior clipboard. The Telegram placeholder stays empty until the maintainer supplies a public link. FocusGate does not open an external app or send a network request.

These observations add evidence for R3 and support UI. They do not complete the full quota/schedule matrix or all lifecycle checks.

The interrupted-break check used a temporary 120,000 ms continuous cap and 60,000 ms required break. After the first Chrome visit, effective time remaining was 86,264 ms. Returning before a full break left 85,709 ms, not a fresh 120,000 ms. After a second visit, 56,423 ms remained. A partial absence retained that value. A full uninterrupted break restored 120,000 ms.

The original profile was then restored: both presets enabled, 900,000 ms/hour allowance, cap off and 300,000 ms break. Accessibility stayed connected/foreground and Restricted Mode stayed off. This test crossed 13:00; the final hourly remaining value was 900,000 ms in the new hour. The test did not measure the exact hourly reset moment.

## 2026-10-04: recovery checks

The user selected lifecycle acceptance before the password release matrix. Initial state: Restricted Mode off, both presets enabled, 900,000 ms/hour allowance, cap off, and 300,000 ms break. No permissions were changed through ADB; the user later toggled Accessibility manually. No password was changed and no app data was cleared.

Recovery was checked before opening FocusGate or calling its maintenance provider. A provider call can start the process and reconcile timer expiry, so it is not proof of automatic recovery. System service/PID observations and native YouTube Home redirection came first. No instrumentation or UiAutomation was used.

| Case | Measured result |
| --- | --- |
| Screen off/on and manual unlock | Foreground service and the same process survived 20 seconds off. After the user unlocked, YouTube returned Home. Restricted Mode was off for this case |
| Abrupt process death, unlocked configuration | `run-as ... kill -9 <recorded PID>` terminated the debug process without package force-stop. No PID at the first 117 ms check; a new PID at 2,213 ms. Foreground service returned and YouTube returned Home before a provider call |
| Quota retention after idle process death | A safe Chrome `about:blank` visit consumed normal allowance. Remaining usage was 882,110 ms before and after death, with revision 17 and both rules enabled |
| Launcher restart during a five-minute TIMER lock | Force-stopped only `com.miui.home`, then sent Home. Launcher PID changed. FocusGate retained its process/foreground service; YouTube returned Home. Saved deadline and quota were unchanged |
| Abrupt process death during the same lock | No FocusGate PID at 2,232 ms; new PID at 4,313 ms. Service returned automatically, YouTube returned Home, lock stayed active, and deadline/quota were unchanged |
| APK replacement during the same lock | Reinstalled the existing debug APK with `adb install -r --no-streaming`, without clearing data. Service reconnected in a new process and YouTube returned Home before any app launch/provider query. Lock deadline, revision, rules and 882,110 ms remaining were retained |
| Reboot during the same lock | After manual first unlock, system dumps showed a new connected foreground service before any provider call or FocusGate launch. The active TIMER lock retained its deadline, revision, rules and 882,110 ms remaining. YouTube and Instagram subsequently returned Home |
| Explicit Accessibility off/on | User toggled the grant manually. Android showed no bound service during the off interval, then a new service record in the same process. Foreground promotion and YouTube Home redirection resumed before a provider query. This check happened after the first TIMER lock expired |
| Screen-off billing during a new three-minute TIMER lock | Chrome `about:blank` was foreground before sleep. Remaining allowance was 870,200 ms after the screen-off transition and exactly 870,200 ms twenty seconds later. After manual unlock, YouTube returned Home and the lock retained its deadline. Normal Chrome billing resumed after unlock before the test navigation |
| Process death while a quota app was active | Killed the debug process while Chrome `about:blank` was foreground. No PID at 85 ms; new process at 2,186 ms, with foreground service restored before a provider query. Remaining allowance went from the pre-kill checkpoint value of 836,269 ms to 824,152 ms after recovery and further browser use. Billing continued without replenishing usage; the active lock deadline was retained. Exact uncommitted usage loss was not measured |

The short lock retained the original deadline of 13:24:34.518 in the phone timezone. Neither process restart nor replacement started a new five-minute period. This was replacement with the same debug APK, not a version/schema migration. PID observations give polling bounds for process recovery; they do not measure exact enforcement downtime. The first two process kills occurred while Home was active, after a usage checkpoint. They do not measure loss of an uncommitted billable interval.

Reboot started at 13:20:38 with the short lock active. At 13:22:40 the phone was unlocked and Android showed a new foreground service. At 13:22:59 the original lock was still active, before its 13:24:34.518 deadline. ADB was unavailable during part of startup. No enforcement before first unlock was claimed or tested.

The first YouTube launch after reboot was still resumed at the initial two-second check, then returned Home. A later timed launch showed YouTube resumed at 4,361 ms from command start and Home at 5,813 ms. Instagram showed its activity at 2,879 ms and Home at 4,311 ms. These intervals include launch and shell/dump overhead; they are not visibility-to-redirection latency measurements. The user considered a startup delay acceptable. No manual service repair or FocusGate launch was needed. Exact startup enforcement delay and long idle behavior remain unmeasured.

After the first deadline passed, a status query reported configuration unlocked, with both blockers enabled and the quota unchanged. This confirms expiry reconciliation, not an autonomous background release instant. A new three-minute maintenance TIMER session used a fresh deadline of 13:29:14.669 and retained remembered user choices by design.

The exported configuration matched the saved baseline exactly after these checks. Both presets still use the original 15-minute hourly allowance, cap off and five-minute break. Test browser use consumed ordinary allowance; no counter reset was requested. Passwords and grants were not read or changed through ADB.

Longer screen-off/idle recovery belongs to the performance/overnight checks. Different-version upgrades, exact enforcement gaps and exact uncommitted usage loss remain unmeasured. These short phone cases are not a guarantee against OS suspension, force-stop or permission removal.

Final handoff at 13:29:40: the second short lock had expired; Restricted Mode was off. Accessibility was connected/foreground, revision remained 17, both presets were enabled, and the baseline profile matched exactly. Shared remaining allowance was 824,152 ms after normal test use. FocusGate was brought to the foreground only after recovery verification finished. No source code changed for this session; the existing installed APK was used.
