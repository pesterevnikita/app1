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
| Screen off/on, process/service death, launcher restart | PARTIAL | One-minute TIMER lock and connected foreground service survived 15-second screen off/on. Manual unlock then needed; post-unlock blocking and other restart cases remain open |
| Reboot and app-update recovery | PARTIAL | Debug update preserved rules and reconnected/promoted Accessibility. Reboot and active-lock upgrade remain unverified |
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
