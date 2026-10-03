# Development and recovery context

FocusGate is an offline Kotlin/Compose Android app for interrupting procrastination. Application ID: `io.github.pesterevnikita.focusgate`. Repository: `https://github.com/pesterevnikita/app1`. Work started on branch `feat/focusgate`; inspect `git status` and recent history rather than assuming the branch or commit is unchanged.

## Start after a handoff or context compaction

1. Read root `AGENTS.md`, the accepted design, and `docs/testing/phone-acceptance.md`.
2. Inspect `git status --short`, recent commits, and any working diff. Another agent may own a pending change; do not overwrite it.
3. Read `docs/testing/known-limitations.md` and the relevant implementation/tests. An ignored scratch progress log may exist at `.superpowers/sdd/2026-10-03-focusgate/progress.md`; tracked documentation remains the durable handoff.
4. Reconcile recent user corrections and observed phone failures with documentation. Continue the authorized task; do not request the same approval again merely because context was compacted.

## Approved behavior

Three simple tabs expose Blockers, Restricted Mode, and More. Editable presets block native/web YouTube and Instagram, and offer one shared 15-minute clock-hour budget for Chrome, Edge, Telegram, and Ozon shopping. Rules support schedules, days, date ranges, quotas, and optional maximum continuous sessions with required breaks. Default presets are not hardcoded enforcement.

Enabled rules work while configuration is unlocked. Restricted Mode locks changes, with password, timer, password OR timer, and password AND timer release choices. Provably restrictive additions may be allowed while locked. Release unlocks configuration; automatic temporary pause/relock is deferred. Website rules operate only on confidently recognized browser address bars, with host matching or bounded RE2/J full-match regex. Unknown URLs remain allowed. Denial currently returns Home rather than navigating to GitHub.

Device Admin, Settings blocking, and Recents protection provide optional normal-action friction. The app cannot promise defense against disabling Accessibility, force-stop, data removal, safe mode, root/ADB, or factory reset. Usage Access is not required by current accounting. Donations contain empty maintainer-editable constants in `donations/DonationConfig.kt`; no payment/network integration exists.

## Architecture map

Source roots are `policy/src/main/kotlin/io/github/pesterevnikita/focusgate/policy/` and `app/src/main/java/io/github/pesterevnikita/focusgate/`.

| Area | Responsibility |
| --- | --- |
| `policy/PolicyEngine`, `ScheduleEvaluator`, `UrlMatcher` | Pure JVM applicability, deny precedence, schedule transitions, domain/regex matching |
| `policy/UsageLedger`, `BucketClock` | Shared foreground accounting, bucket resets, continuous sessions and required breaks |
| `policy/RestrictedSession` | Release truth tables, deadline clocks, restrictive mutation rules |
| `data/AppStore`, `AppState`, `FocusGateDatabase` | Mutex plus Room transaction; one versioned JSON state row; durable revision checks and atomic policy/session/ledger updates |
| `accessibility/FocusGateAccessibilityService`, `BrowserAdapter` | Android observation, confident URL extraction, Home redirection, service lifecycle |
| `accessibility/EnforcementNotification` | Ongoing notification for foreground promotion of the existing Accessibility service; promotion and observed swipe-away behavior verified on Xiaomi |
| `runtime/EnforcementController` | Connects observations, policy decisions, ledger checkpoints, and platform actions |
| `runtime/ObservationDispatcher`, `TransitionScheduler` | Retains observations arriving during suspended IO; owns transition callback scheduling |
| `runtime/AndroidClock`, `BootReceiver` | UTC/monotonic/boot identity and boot/unlock reconciliation |
| `health/SetupPreflight`, `HealthWorker` | Capability checks and periodic WorkManager health checks; not a guarantee of recovery from OEM termination |
| `health/SetupScreens` | Best-effort Android/OEM setup-screen routing; permission and Background autostart grants remain manual |
| `security/PasswordVerifier` | Versioned salted PBKDF2-HMAC-SHA256 credentials; password arrays cleared after use |
| `diagnostics/LocalDiagnostics` | Bounded local category/timestamp records; no screen text or raw URLs |
| `ui/FocusGateApp`, `MainActivity` | Compose tabs, editors, setup, import/export, countdown and help |

State emission follows successful durable commits. Checkpoints during billable use occur every five seconds and at transitions; process death may lose uncommitted usage. Same-boot deadlines/breaks use monotonic time; cross-boot fallback uses UTC. Only a minimal locked flag is stored device-protected before unlock. Policy and credentials remain in credential-protected storage.

Configuration export excludes credentials, active locks, usage counters, and logs. Import validates size, enums, references, rules and numeric bounds; locked imports are refused. Imports reset optional protections for capability review. Runtime manifest explicitly removes INTERNET and ACCESS_NETWORK_STATE, including dependency contributions, and excludes backups.

The current implementation uses Groovy Gradle scripts to avoid a corporate-laptop Kotlin DSL ZIP-opening stall. Product code remains Kotlin. State/preferences share one atomic Room document rather than separate DataStore storage. These are deliberate implementation choices, not instructions to migrate them during unrelated work.

## Windows build recipe

Use a local clone and Java 21. Configure the Android SDK in untracked local.properties.

Current toolchain: Gradle 8.13, AGP 8.11.2, Kotlin 2.0.21, compile/target SDK 36, min SDK 29. Wrapper distribution checksum is checked in. Gradle properties limit workers to two and use in-process nonincremental Kotlin compilation after a Windows temporary-directory failure.

Known working command on this laptop:

```powershell
$env:JAVA_HOME = '<JDK_21>'
.\gradlew.bat :policy:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain 
```

If a development proxy is required, configure it locally. Do not publish its address or credentials.

The latest full verification passed 57 unit tests, lint with zero errors/five warnings, and assembled both debug APKs. One Room instrumentation test also passed on the Xiaomi. Reports are in `policy/build/test-results/test/`, `app/build/test-results/testDebugUnitTest/`, and `app/build/reports/`.

## Device workflow

Target: Xiaomi 11T, Android 14/API 34, user-reported HyperOS 1.0.15.0. Add Android SDK platform-tools to PATH.

```powershell
adb install -r --no-streaming app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.pesterevnikita.focusgate/.MainActivity
adb shell pidof io.github.pesterevnikita.focusgate
adb logcat -d -t 300 -s AndroidRuntime:E
```

Do not uninstall/clear data during an upgrade test. Xiaomi may require Install via USB and a manual confirmation. Both main and separate test APKs now install successfully; earlier INSTALL_FAILED_USER_RESTRICTED failures were resolved. Accessibility was granted manually. Native YouTube/Instagram redirection is observed; the user also saw four seconds of Telegram usage charged to the shared quota.

The initial user report was a serious availability regression: swiping FocusGate out of Recents stopped enforcement, reopening did not restore it, and Accessibility reported a malfunction until manually toggled off/on. Android exit information now identifies a HyperOS `SwipeUpClean` process kill, with no observed AndroidRuntime crash. The earlier Other permissions setup had not enabled the separate true Background autostart setting. The user manually enabled its actual OEM checkbox. A later swipe still killed the old process, but Android automatically reconnected Accessibility in a new process and YouTube again returned Home without a manual regrant. This is measured recovery after the setup change, with a possible short enforcement gap; it does not prove every restart scenario.

The verified lifecycle change promotes the existing Accessibility service to specialUse foreground priority while enabled blockers or Settings/Recents protections need enforcement. System dumps and an app-only screenshot confirmed foreground/connected status. Repeated actual card removals retained the same process and native blocking; the final repeat avoided UiAutomation. The onInterrupt feedback fix and independent-protection eligibility regressions passed. These observations do not prove every process-death, reboot or screen-off scenario.

Room instrumentation tests use separate database names. To avoid a Gradle device runner uninstalling the main app and losing its grants, build `:app:assembleDebugAndroidTest`, manually install only `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, then invoke:

```powershell
adb shell am instrument -w io.github.pesterevnikita.focusgate.test/androidx.test.runner.AndroidJUnitRunner
```

Run the Room fixture with Restricted Mode off: its separate database still shares the boot-session preference mirror. Keep tests narrowly scoped and understand their state changes before running. Instrumentation force-stops the target process; on this phone it left Accessibility malfunctioning until a manual off/on toggle. Do not use stock adb shell uiautomator dump for service-resilience assertions: it suppresses Accessibility by default. Prefer input/dumpsys and app-only screenshots, or UiAutomation configured with FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES. Coordinate UI taps with the user and verify the foreground screen; shared device interaction can invalidate old coordinates. Broader browser coverage, interrupted breaks, process/reboot recovery, Settings/Recents classification, Device Admin, password timing and battery cost still require measured phone acceptance. Shared exhaustion, cap across an hour boundary, break UI recovery and short TIMER locks are now measured; read the latest table. Read the acceptance table for the latest results.

## History and maintenance

GitHub publishing is authorized. The user repaired normal authentication and pushed the branches; a subsequent noninteractive remote check succeeded. Push normal local commits through credential-manager authentication; never reuse the token pasted into the initial conversation.

When changing behavior, update the relevant design/development/testing notes. Add comments explaining lifecycle ownership, clock assumptions, mutation guards and durable state boundaries. Finish with a concise account of changed behavior, actual verification, and remaining measured limitations. Avoid claiming all restart scenarios work based only on persisted data or JVM tests.

## Current extension

The user approved retaining shell-only ADB maintenance controls in final builds. Read [the API and helper guide](adb-control.md) and [extension plan](superpowers/plans/2026-10-03-adb-maintenance.md). The main-source provider requires caller DUMP permission and UID 2000; every policy write still uses transactional AppStore guards. It provides no password/release/grant/reset endpoint. Do not remove it merely because a build is release.

Current phone configuration restored after cap acceptance: shared quota 15 minutes per clock hour, continuous cap off, break five minutes; both editable presets enabled. Restricted Mode off. Next checks: password-policy release, reboot/screen-off recovery, optional admin and Settings/Recents protections. ADB profile import uses the same importer and must refuse active locks.
