# Development build verification — 2026-10-03

Status: build and local behavioral checks passed. Phone acceptance is in progress: main APK installation, Accessibility setup and native preset blocking are verified. This is not a completed first-release acceptance record.

## Environment

Primary phone observed through read-only ADB: Android 14 / API 34, model 21081111RG (Xiaomi 11T). User-reported HyperOS 1.0.15.0. Chrome, Edge, Telegram, Ozon shopping, Ozon Bank, YouTube and Instagram packages were present. No device serial is retained here.

Development toolchain: Java 21, Gradle 8.13 with official distribution checksum, Android Gradle Plugin 8.11.2, Kotlin 2.0.21, compile/target SDK 36, min SDK 29. Build uses installed SDK AAPT2 36.1.0 via an untracked command-line override after the Maven AAPT2 download timed out. Corporate proxy is configured only for development commands.

## Local results

Successful command: `gradlew.bat :policy:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` with local proxy and AAPT2 override options. Result: BUILD SUCCESSFUL; 39 unit tests pass (18 policy, 21 app-side); lint reports 0 errors and 5 warnings. The same verification also assembled the separate Android test APK.

Behavior covered includes enabled/disabled rules outside Restricted Mode; domain boundaries and unknown URL allowance; overnight schedules; overlapping deny precedence; shared foreground usage; clock-hour reset; continuous-session cap and breaks across boundaries/restart; same-boot monotonic break handling under clock jumps; four release-policy truth tables; restrictive-only mutations; stale revisions; password verification; import/export validation and secret exclusions; malformed quota enums; bounded file reads compatible with older Android; static-foreground exhaustion; safe timer callbacks; retained foreground observations during suspended checkpoint IO; preflight refusals; and bounded diagnostics.

Independent whole-app review found timer self-cancellation, dropped pending observations, malformed quota enums, wall-clock break accounting and insufficient address-bar confidence gates. Each now has an observed failing regression followed by a passing suite. A separate lint API error in bounded import reading was also fixed and tested.

Merged manifest inspection: no INTERNET or ACCESS_NETWORK_STATE permission. Explicit backup/cloud-transfer exclusions cover private and device-protected storage. Framework-bound Accessibility and Device Admin components require platform bind permissions. Boot receiver is not exported; no debug bypass component is exported. No credentials/active sessions/counters are included in configuration export. Source scan found no GitHub token or private phone identifier; fake password strings occur only in tests.

Lint warnings deferred: API-31-only `isAccessibilityTool=false` metadata is ignored by older systems; application-context singleton is flagged by the generic static-context check; three optional KTX-style suggestions. No warning represents measured phone functionality.

## Phone investigation

Original swipe-away failure: Android exit history reported HyperOS `SwipeUpClean` process termination, not an observed AndroidRuntime crash. True Background autostart was initially off despite Other permissions setup. The user enabled it manually (OEM checkbox verified), and Battery saver / No restrictions was also verified. Autostart then allowed automatic reconnection but with an observed restart gap.

The updated build promotes the existing bound Accessibility service to foreground priority using Android 14 `specialUse`, a quiet ongoing notification and no extra polling or wake lock. System dumps confirm foreground status; repeated card removals retained the process. Feedback interruption no longer clears connection status or cancels quota timers; the regression was observed failing before the fix. Independent Settings/Recents protection eligibility also has a red/green regression.

An existing timer test failed on the busy laptop (one of two callbacks completed before a fixed 50 ms wait). It now awaits actual completion under a timeout.

Testing caveat: stock `uiautomator dump` suppresses Accessibility services temporarily and can capture a disconnected UI. Use non-suppressing UiAutomation connections or input/dumpsys/app-only screenshots for enforcement checks. Android instrumentation force-stops the target before/after execution; it left Accessibility malfunctioning on this phone, and the user manually toggled it off/on after testing. Final checks ran after reconnection. See [Android UiAutomation documentation](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).

## Phone results

| Check | Result | Evidence / next action |
| --- | --- | --- |
| ADB connection and device/package inspection | PASS | Read-only commands returned Android 14 and target packages |
| Debug/test APK installation | PASS | Updated main and separate instrumentation APK installed successfully with -r --no-streaming; user configuration preserved |
| Room reopen/transaction instrumentation | PASS | AndroidJUnitRunner: OK (1 test); editable policy survives database reopen and stale revision is refused. Fixed test-only Boolean return signature after observed JUnit initialization failure |
| Accessibility grant/service connection | PASS | User enabled manually; service registered and native blocking observed |
| Native blocking and latency | PARTIAL | Added editable presets with Restricted Mode off; launching YouTube and Instagram returned to com.miui.home. Exact latency not measured; no AndroidRuntime error in sampled log |
| Shared hourly budget and continuous cap on phone | PARTIAL | User observed four seconds in Telegram deducted from the shared allowance. Exhaustion/reset/cap timing still unmeasured on phone |
| Chrome/Edge browser versions and URL coverage | PARTIAL | Explicit Chrome Main launch of YouTube website and Edge launch of Instagram website returned Home. Chrome package-only launch first showed Xiaomi resolver. Versions, unknown URLs, hidden bars, private tabs and broader cases remain unverified |
| Device Admin / launcher uninstall friction | NOT RUN | Optional empty-policy admin declaration requires device confirmation |
| Sensitive/whole Settings, network exceptions, Recents | NOT RUN | Experimental class-based adapters; verify actual HyperOS routes |
| Swipe-away and foreground promotion | PASS for observed scenario | Multiple actual FocusGate Recents-card removals retained the same PID, foreground service and bound Accessibility; YouTube returned Home. Final repeat used input/dumpsys without UiAutomation |
| Screen off/on, process/service death, launcher restart | NOT RUN | Persistence is designed; remaining availability scenarios unmeasured |
| Reboot and app-update recovery | PARTIAL | Debug APK update preserved rules and Accessibility reconnected/promoted. Reboot and active Restricted Mode upgrade still unverified |
| Emergency UI, incoming calls, split-screen/PiP | NOT RUN | Exclusions exist; real device behavior not measured |
| Password derivation timing and CPU/battery | NOT RUN | 210,000-iteration default is not yet phone-benchmarked |

Next phone steps: verify wider browser coverage and timed exhaustion, then optional Device Admin, Settings protection, screen-off and reboot recovery. Start with editable blockers and a five-minute test lock. Never use a long lock before confirming release and Settings access behavior.

APK: `app/build/outputs/apk/debug/app-debug.apk` (generated, git-ignored). Build is debug-signed for development; no production signing key is created or committed.
