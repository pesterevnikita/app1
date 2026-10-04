# Development and recovery context

**TL;DR:** Read the roadmap and phone acceptance before work. Build with JDK 21 and the local Android SDK. Preserve app data during updates. The app works offline, but full phone acceptance is incomplete. Password removal requires an unlocked configuration.

FocusGate is an offline Kotlin/Compose Android app. Application ID: `io.github.pesterevnikita.focusgate`. Repository: `https://github.com/pesterevnikita/app1`. Work started on `feat/focusgate`. Inspect current Git state; do not assume the branch or commit is unchanged.

## Resume work

1. Read [AGENTS.md](../AGENTS.md), the [accepted design](superpowers/specs/2026-10-03-focusgate-design.md), and [phone acceptance](testing/phone-acceptance.md).
2. Read [the roadmap](roadmap.md) for current tasks and completion. Original plan checkboxes are historical.
3. Inspect `git status --short`, recent commits and the working diff. Preserve other agents' pending changes.
4. Read [known limitations](testing/known-limitations.md) and relevant code/tests. Scratch evidence may exist at `.superpowers/sdd/2026-10-03-focusgate/progress.md`. Tracked docs remain the durable handoff.
5. Apply recent user corrections and verified phone evidence. Continue authorized work. Context compaction does not require the same approval again.

## Approved behavior

Support destinations live in `support/SupportConfig.kt`. The repository URL is public. Telegram feedback stays empty until the maintainer supplies a public link. The app displays and copies links only. It never opens an external app automatically. See [published-data review](testing/privacy-review.md) for the history-cleanup status. New commits use the public GitHub no-reply identity.

The tabs are Blockers, Restricted Mode and More. Editable presets block native/web YouTube and Instagram. Chrome, Edge, Telegram and Ozon shopping share 15 minutes per clock hour. Rules support days, time windows, date ranges and quotas. Optional continuous-session caps require uninterrupted breaks. Presets are ordinary rules.

Enabled rules work when configuration is unlocked. Restricted Mode prevents weaker changes. New locks support Password only, Timer only and Password OR timer. Existing legacy AND sessions remain supported. New AND locks are refused. Provably restrictive additions can be permitted during a lock. Release unlocks configuration. It does not disable blockers. Temporary pause with automatic relock is deferred.

Website rules use confidently recognized browser address bars. Matching uses hosts or bounded RE2/J full-match regex. Unknown URLs remain allowed. Denial returns Home. It does not navigate to GitHub.

Device Admin, Settings and Recents controls add optional friction. They cannot guarantee protection against permission loss, force-stop, data removal, safe mode, root/ADB or factory reset. Accounting does not require Usage Access. Donation constants in `donations/DonationConfig.kt` remain empty. There is no payment or network integration.

## Architecture

Source roots:

- `policy/src/main/kotlin/io/github/pesterevnikita/focusgate/policy/`
- `app/src/main/java/io/github/pesterevnikita/focusgate/`

| Area | Responsibility |
| --- | --- |
| `policy/PolicyEngine`, `ScheduleEvaluator`, `UrlMatcher` | JVM rule evaluation, denial precedence, transitions and host/regex matching |
| `policy/UsageLedger`, `BucketClock` | Shared foreground accounting, bucket resets, session caps and breaks |
| `policy/RestrictedSession` | Release conditions, deadline clocks and restrictive changes |
| `data/AppStore`, `AppState`, `FocusGateDatabase` | Mutex and Room transaction; one versioned JSON row; atomic state changes and stale-write guards |
| `accessibility/FocusGateAccessibilityService`, `BrowserAdapter` | Android observations, known URLs, Home actions and service lifecycle |
| `accessibility/EnforcementNotification` | Ongoing notification for foreground priority of the existing service |
| `runtime/EnforcementController` | Observations, decisions, checkpoints and platform actions |
| `runtime/ObservationDispatcher`, `TransitionScheduler` | Retain events during suspended IO; schedule transition callbacks |
| `runtime/AndroidClock`, `BootReceiver` | UTC, monotonic time, boot identity and boot/unlock recovery |
| `health/SetupPreflight`, `HealthWorker` | Capability checks and periodic best-effort health checks |
| `health/SetupScreens` | Best-effort setup links; the user grants permissions and Background autostart |
| `security/PasswordVerifier` | Salted, versioned PBKDF2-HMAC-SHA256 verifier; clear password arrays after use |
| `diagnostics/LocalDiagnostics` | Bounded category/timestamp records; no screen text or raw URLs |
| `ui/FocusGateApp`, `MainActivity` | Tabs, editors, setup, import/export, countdown and help |

Emit state only after a durable commit. Save billable usage every five seconds and at transitions. Process death can lose uncommitted usage. Use monotonic time for same-boot deadlines and breaks. Use UTC after reboot. Before first unlock, device-protected storage holds only a minimal locked flag. Full policy and credentials use credential-protected storage.

Exports exclude credentials, active locks, usage and logs. Imports validate size, enums, references, rules and numeric bounds. Refuse imports while locked. Reset imported optional protections for capability review. The merged runtime manifest removes INTERNET and ACCESS_NETWORK_STATE, including dependency contributions. Backup is excluded.

Gradle scripts use Groovy after an observed Kotlin DSL ZIP-opening stall. Product code uses Kotlin. State and preferences share one atomic Room document. Do not migrate these choices during unrelated work.

## Windows build

Run PowerShell from the repository root. Use JDK 21 and a local Android SDK. Keep `local.properties` and machine-specific notes untracked.

Toolchain: Gradle 8.13, AGP 8.11.2, Kotlin 2.0.21, compile/target SDK 36, min SDK 29. The wrapper checksum is tracked. Gradle limits workers to two. Kotlin compilation runs in-process without incremental compilation after a Windows temporary-directory failure.

```powershell
# Replace the placeholders with local installation directories.
$env:JAVA_HOME = '<JDK_21_DIRECTORY>'
$env:ANDROID_HOME = '<ANDROID_SDK_DIRECTORY>'
.\gradlew.bat :policy:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

Keep proxy hosts, ports and credentials in local untracked configuration. If needed, use `-Pandroid.aapt2FromMavenOverride=<ANDROID_SDK_DIRECTORY>/build-tools/<VERSION>/aapt2.exe`. Omit unnecessary overrides. Cache writes and dependency downloads may need sandbox escalation.

The latest full verification passed 69 unit tests. Lint reported zero errors and five warnings. Debug and unsigned release APKs built. The separate instrumentation APK built earlier. One Room test passed on the Xiaomi. Reports are under `policy/build/test-results/test/`, `app/build/test-results/testDebugUnitTest/` and `app/build/reports/`. Check acceptance records before treating this history as current evidence.

## Device workflow

Target: Xiaomi 11T, Android 14/API 34. The user reported HyperOS 1.0.15.0. Add the SDK `platform-tools` directory to PATH.

```powershell
adb install -r --no-streaming app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.pesterevnikita.focusgate/.MainActivity
adb shell pidof io.github.pesterevnikita.focusgate
adb logcat -d -t 300 -s AndroidRuntime:E
```

Do not uninstall or clear data during upgrade tests. Xiaomi can require Install via USB and manual confirmation. Main and test APK installations succeeded after earlier INSTALL_FAILED_USER_RESTRICTED failures. The user granted Accessibility manually. Native YouTube/Instagram redirection was observed. The user also reported four seconds of Telegram charged to the shared budget.

### Swipe-away history

The user first reported that Recents removal stopped enforcement. Reopening did not repair Accessibility. A manual off/on toggle was needed. Android exit information showed HyperOS `SwipeUpClean`, with no observed AndroidRuntime crash.

Other permissions had not enabled the separate Background autostart setting. The user enabled that checkbox manually. A later swipe killed the old process, but Android reconnected Accessibility in a new process. YouTube returned Home without a manual regrant. A short enforcement gap remained possible.

The existing Accessibility service now uses `specialUse` foreground priority when blockers or Settings/Recents protections need enforcement. Dumps and an app-only screenshot confirmed foreground/connected status. Repeated card removals retained the process and native blocking. The final repeat avoided UiAutomation. The `onInterrupt` fix and independent-protection eligibility regressions passed. These results do not prove all screen-off, process-death or reboot cases.

### Instrumentation precautions

Room fixtures use separate database names. They still share the boot-session preference mirror. Run them with Restricted Mode off. Understand state changes before running each fixture.

A Gradle device runner can uninstall the main app and lose its grants. Instead, build `:app:assembleDebugAndroidTest`. Install only `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. Then run:

```powershell
adb shell am instrument -w io.github.pesterevnikita.focusgate.test/androidx.test.runner.AndroidJUnitRunner
```

Instrumentation force-stops the target. On this phone, Accessibility then needed a manual off/on toggle. Verify recovery afterward. Stock `adb shell uiautomator dump` suppresses Accessibility. Do not use it to prove service resilience. Use input/dumpsys, app-only screenshots or UiAutomation with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`.

Coordinate taps with the user. Verify the current screen before input. Shared use can invalidate coordinates. Read [phone acceptance](testing/phone-acceptance.md) for measured shared exhaustion, hour-boundary caps, break recovery and short Timer locks. Browser coverage, interrupted breaks, recovery, protections, password timing and battery cost still need broader checks.

## Maintenance and current evidence

[The roadmap](roadmap.md) tracks current work. [Phone acceptance](testing/phone-acceptance.md) records results. [Known limitations](testing/known-limitations.md) records gaps. Use these records to select work. Do not turn the history below into completion claims.

GitHub publishing is authorized. Normal authentication was repaired, and a noninteractive remote check succeeded. Use credential-manager authentication. Never reuse the exposed token from the earlier conversation.

Update relevant design, development and testing docs after behavior changes. Explain lifecycle ownership, clocks, mutation guards and durable state in comments. Report changed behavior, actual checks and remaining limits. Persisted state or JVM tests alone do not prove Android recovery.

### ADB maintenance

The user approved shell-only maintenance controls in final builds. Read [the ADB guide](adb-control.md) and [extension plan](superpowers/plans/2026-10-03-adb-maintenance.md). The provider requires caller DUMP permission and UID 2000. All writes use transactional AppStore guards. It exposes no password, release, grant or reset endpoint. Release builds retain it. Profile import uses the same importer and refuses active locks.

After cap tests, the phone configuration was restored: 15 minutes per clock hour, continuous cap off, five-minute break, both editable presets enabled, Restricted Mode off. Do not assume live state is unchanged. Next checks are in the roadmap.

### Password and UI follow-up

Initial password setup requires matching new/repeated inputs. Change requires the current password and matching new/repeated inputs while unlocked. Persist failed-attempt delays. Store only the verifier. Keep credentials across upgrades. Remember the last successful lock mode, duration and additions. Each lock starts a fresh deadline. Maintenance test timers must not replace remembered choices.

Restricted Mode uses status, password, session and protection cards. More groups setup, display, backups, help, developer tools and about/support. Secondary help is collapsed. The blocker editor offers Always block during schedule or Allow limited usage. Protection writes include the original snapshot to reject stale changes.

`PasswordChanges.remove` checks the latest credential and uses the shared retry delay. `AppStore.removePassword` runs the change transactionally. It refuses active sessions, clears caller buffers and preserves policy, usage and preferences. No ADB credential endpoint exists.

`ui/PasswordInput` has independent eye controls for setup, change, removal and unlock. Inputs start masked. Dialog recreation does not save input or visibility. Password-only and OR starts require a saved password. Timer-only requires explicit selection and has no early password override. Removal never converts remembered OR to Timer. After removal, setup needs no old password. Remembered choices change only after another successful lock.

Four added regression tests cover removal, fresh setup, lock prerequisites, incorrect/stale attempts, cooldown, locked refusal and input clearing. The update passed 69 tests, lint and both APK builds. Read-only review found no issues. Debug installation preserved presets/usage and reconnected Accessibility. Disposable input confirmed the removal explanation and hidden/revealed/reopened-hidden behavior. The agent cancelled the dialog and did not read or remove the real password.

The user reported that password controls and the revised UI work. This is general user confirmation. It is not a measured release-policy matrix. Password removal remains unavailable during Restricted Mode. Unlock normally, then remove with the current password. The user withdrew the proposed unlock-and-remove shortcut. Its exploratory test edits were reverted. Product code and the installed build keep the confirmed password behavior. No further password redesign is pending.
