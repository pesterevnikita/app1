# Development build verification — 2026-10-03

Status: build and local behavioral checks passed. Phone acceptance is blocked by the Xiaomi USB-install restriction. This is not a completed first-release acceptance record.

## Environment

Primary phone observed through read-only ADB: Android 14 / API 34, model 21081111RG (Xiaomi 11T). User-reported HyperOS 1.0.15.0. Chrome, Edge, Telegram, Ozon shopping, Ozon Bank, YouTube and Instagram packages were present. No device serial is retained here.

Development toolchain: Java 21, Gradle 8.13 with official distribution checksum, Android Gradle Plugin 8.11.2, Kotlin 2.0.21, compile/target SDK 36, min SDK 29. Build uses installed SDK AAPT2 36.1.0 via an untracked command-line override after the Maven AAPT2 download timed out. Corporate proxy is configured only for development commands.

## Local results

Successful command: `gradlew.bat :policy:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` with local proxy and AAPT2 override options. Result: BUILD SUCCESSFUL; 36 tests pass (18 policy, 18 app-side); lint reports 0 errors and 4 warnings.

Behavior covered includes enabled/disabled rules outside Restricted Mode; domain boundaries and unknown URL allowance; overnight schedules; overlapping deny precedence; shared foreground usage; clock-hour reset; continuous-session cap and breaks across boundaries/restart; same-boot monotonic break handling under clock jumps; four release-policy truth tables; restrictive-only mutations; stale revisions; password verification; import/export validation and secret exclusions; malformed quota enums; bounded file reads compatible with older Android; static-foreground exhaustion; safe timer callbacks; retained foreground observations during suspended checkpoint IO; preflight refusals; and bounded diagnostics.

Independent whole-app review found timer self-cancellation, dropped pending observations, malformed quota enums, wall-clock break accounting and insufficient address-bar confidence gates. Each now has an observed failing regression followed by a passing suite. A separate lint API error in bounded import reading was also fixed and tested.

Merged manifest inspection: no INTERNET or ACCESS_NETWORK_STATE permission. Explicit backup/cloud-transfer exclusions cover private and device-protected storage. Framework-bound Accessibility and Device Admin components require platform bind permissions. Boot receiver is not exported; no debug bypass component is exported. No credentials/active sessions/counters are included in configuration export. Source scan found no GitHub token or private phone identifier; fake password strings occur only in tests.

Lint warnings deferred: API-31-only `isAccessibilityTool=false` metadata is ignored by older systems; application-context singleton is flagged by the generic static-context check; two optional KTX-style suggestions. No warning represents measured phone functionality.

## Phone results

| Check | Result | Evidence / next action |
| --- | --- | --- |
| ADB connection and device/package inspection | PASS | Read-only commands returned Android 14 and target packages |
| Debug/test APK installation | BLOCKED | `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`, including non-streaming install retry |
| Room reopen/transaction instrumentation | NOT RUN | Test exists; installation blocked before execution |
| Accessibility grant/service connection | NOT RUN | User must enable manually after installation/disclosure |
| Native blocking and latency | NOT RUN | Requires connected service and reviewed enabled rules |
| Shared hourly budget and continuous cap on phone | NOT RUN | Local tests passed; phone timing not measured |
| Chrome/Edge browser versions and URL coverage | NOT RUN | Adapter fixtures passed; actual address-bar coverage not verified |
| Device Admin / launcher uninstall friction | NOT RUN | Optional empty-policy admin declaration requires device confirmation |
| Sensitive/whole Settings, network exceptions, Recents | NOT RUN | Experimental class-based adapters; verify actual HyperOS routes |
| Screen off/on, swipe-away, process/service death, launcher restart | NOT RUN | Locked data is designed to persist; availability gaps not measured |
| Reboot and app-update recovery | NOT RUN | Requires phone setup and a short releasable test session |
| Emergency UI, incoming calls, split-screen/PiP | NOT RUN | Exclusions exist; real device behavior not measured |
| Password derivation timing and CPU/battery | NOT RUN | 210,000-iteration default is not yet phone-benchmarked |

Next phone step: unlock the device, enable Install via USB if needed, accept installation, then grant Accessibility from FocusGate's disclosure flow. Start with editable blockers and a five-minute test lock. Never use a long lock before confirming release and Settings access behavior.

APK: `app/build/outputs/apk/debug/app-debug.apk` (generated, git-ignored). Build is debug-signed for development; no production signing key is created or committed.
