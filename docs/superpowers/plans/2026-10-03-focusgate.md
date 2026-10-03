# FocusGate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver an offline Android self-control app with editable blockers, shared foreground quotas, password/timer configuration locking, and tested Xiaomi recovery.

**Architecture:** A pure Kotlin policy module owns decisions and mutation authorization. Android adapters supply observations; a transactional local repository owns durable rules, sessions and counters. A system-bound AccessibilityService enforces by returning Home; device-specific protections never imply privileged device-owner guarantees.

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, WorkManager, JUnit, Android instrumentation and ADB. Start with cached Android Gradle Plugin 8.11.2 / Gradle 8.13 and installed compile SDK 36, min SDK 29, target SDK 36; verify official compatibility before scaffolding. Use Java 21 available locally with Kotlin/JVM bytecode target 17. Pin compatible stable Kotlin/Compose/AndroidX versions in a version catalog after checking official release documentation and local caches; do not guess dependency versions. Android 14 / API 34 is the first phone acceptance target.

**Spec:** [Accepted design](../specs/2026-10-03-focusgate-design.md).

## Global Constraints

- Package/application ID: `io.github.pesterevnikita.focusgate`; app label: FocusGate.
- No INTERNET or ACCESS_NETWORK_STATE in the merged manifest; no VPN, remote SDKs, cloud sync, remote redirects, or telemetry.
- All presets are editable ordinary rules; no package/domain blacklist inside the evaluator.
- Clock-hour shared allowance is 900,000 milliseconds, without rollover; daily periods are also configurable.
- Unknown browser URLs are allowed by website rules; independent app/quota rules still apply.
- Restricted Mode locks configuration, never automatically activates/deactivates blockers; release leaves blockers enabled.
- Persisted state survives ordinary restarts; permission loss, force-stop and OS suspension can interrupt enforcement.
- Comments explain policy invariants and Android lifecycle/adapter quirks in plain English.
- Never embed GitHub tokens, signing secrets, phone identifiers, logs or local SDK paths in commits.
- Use only optional ordinary Device Admin, not device-owner provisioning. Never invoke wipe or device-password policies.

## Review Focus

1. Simultaneous policy edits and foreground observations must not charge an old revision or bypass a newly locked session — tasks 2 and 5.
2. Navigation with an invisible/stale address bar must not deny an unrelated page using the previous URL — task 6.
3. Quota expiry on an idle/static page must still return Home without a fresh accessibility event — task 4.
4. Correct early password entry in Password AND timer must neither release nor authorize a later release — task 5.
5. Settings protection must not trap required grant repair or mistake emergency/system UI for a blocked Settings screen — task 7.

## File map and shared contracts

`:policy` is a Kotlin/JVM module under `policy/src/main/kotlin/io/github/pesterevnikita/focusgate/policy/`; its tests live in the corresponding `policy/src/test/kotlin/` package. `:app` is Android under `app/src/main/java/io/github/pesterevnikita/focusgate/`; tests use corresponding `src/test/java/` and `src/androidTest/java/` paths.

Root files: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, wrapper files, `.gitignore`. App setup: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/accessibility_service.xml`, `app/src/main/res/xml/device_admin.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/values/strings.xml`.

Policy contracts in `PolicyModels.kt`:

- `RuleId`, `GroupId`: string value types; `PolicyRevision`: long value type.
- `Target`: `App(packageName)` / `Host(domain, includeSubdomains)` / `UrlRegex(pattern)`.
- `Schedule`: zone-aware optional date range, weekday set and local start/end windows; empty time-window list means all day on selected days.
- `Blocker(id, name, enabled, targets, schedule, quotaGroupId, message)`; nullable group means deny while applicable.
- `QuotaGroup(id, period: HOUR|DAY, allowanceMillis, continuousCapMillis: Long?, requiredBreakMillis: Long)`; null cap disables continuous-session limiting, proposed editor values are 900,000 ms cap and 300,000 ms break. Shared period usage is keyed by group and actual period-start instant; session usage and last allowed-use end are persisted separately.
- `Observation(packageName, visibleUrl: String?, interactive: Boolean, observedAt: Instant, elapsedMillis: Long, revision: PolicyRevision)`; null URL is unknown, never a match.
- `PolicySnapshot(revision, blockers, groups)`; `UsageSnapshot` maps group/bucket to consumed milliseconds.
- `ClockSnapshot(instant, elapsedMillis, bootId, zoneId)`; injected clocks enable deterministic tests.
- `Decision(denied, denyingRuleIds, billableGroupIds, nextTransitionAt: Instant?)`.

Mutation/session contracts are defined in task 5 rather than scattered into UI components. All components consume these contracts; implementers may extend them only with documented migration/interface updates.

## Task 1: Buildable policy engine and Android shell

**Files:** Root/app setup above; policy `PolicyModels.kt`, `ScheduleEvaluator.kt`, `UrlMatcher.kt`, `PolicyEngine.kt`; tests `PolicyEngineTest.kt`, `ScheduleEvaluatorTest.kt`, `UrlMatcherTest.kt`; app `MainActivity.kt`, `ui/FocusGateApp.kt`.

**Interfaces:** `PolicyEngine.evaluate(policy: PolicySnapshot, usage: UsageSnapshot, observation: Observation, clock: ClockSnapshot): Decision`; `ScheduleEvaluator.isActive(schedule: Schedule, instant: Instant, zoneId: ZoneId): Boolean`; `UrlMatcher.matches(target: Target, visibleUrl: String?): Boolean`.

- [ ] Verify official toolchain compatibility, inspect cached dependencies and pin the version catalog. Create only the scaffolding needed to compile the tests; configure Java/Kotlin toolchains and wrapper with official distribution checksum. Ignore local.properties, build outputs, signing material and local diagnostics.
- [ ] Write failing tests: `enabledRuleDeniesOutsideRestrictedMode` asserts deny; `disabledRuleDoesNotDeny` asserts allow; `denyWinsOverQuotaAllowance` asserts deny; `unknownUrlDoesNotMatch` asserts false; `hostBoundary` asserts youtube.com true and notyoutube.com false; `overnightMondayWindow` asserts Tuesday 01:00 active and Tuesday 02:00 inactive.
- [ ] Run `./gradlew.bat :policy:test`; confirm the new behavior tests fail before evaluator implementation.
- [ ] Implement the contracts and evaluator. Keep URL host parsing independent of Android; advanced patterns use a pinned linear-time engine and explicit full-match semantics. Calculate next schedule/quota transitions, not only current denial.
- [ ] Run `./gradlew.bat :policy:test :app:assembleDebug`; expect zero test failures and a generated debug APK. Launch a minimal three-tab shell with no presets silently enabled. Commit `feat: add offline app shell and policy engine`.

## Task 2: Durable configuration and editable presets

**Files:** App `data/FocusGateDatabase.kt`, `data/Entities.kt`, `data/ConfigurationRepository.kt`, `data/PresetFactory.kt`, `data/UiPreferences.kt`; instrumentation `data/ConfigurationRepositoryTest.kt`.

**Interfaces:** `ConfigurationRepository.observePolicy(): Flow<PolicySnapshot>`; `readPolicy(): PolicySnapshot`; `transact(expectedRevision: PolicyRevision, command: PolicyCommand): MutationResult` (suspend; authorization delegated to task 5, initially unlocked only); `PresetFactory.create(): PresetBundle` creates new IDs each time. Room schema exports belong in `app/schemas/`.

- [ ] Write failing reopen/transaction tests: disabled preset remains disabled after database reopen; preset editing changes only its stored targets; two commands against the same revision cause the second to return a conflict; failed import-shaped transaction leaves the original revision unchanged.
- [ ] Run `./gradlew.bat :app:connectedDebugAndroidTest` on the connected phone; expect new assertions to fail. Do not enable blockers/admin yet.
- [ ] Implement atomic versioned Room storage, preferences, and ordinary preset factory. Create YouTube/Instagram continuous rules and one shared hourly group for Chrome/Edge/Telegram/Ozon shopping. Do not include Ozon Bank. Disable automatic backup/device-transfer of private state.
- [ ] Repeat instrumentation; expect reopen/atomicity assertions to pass. Verify presets are absent from evaluation unless actually saved/enabled. Commit `feat: persist editable blockers and presets`.

## Task 3: Usage ledger and deterministic clock accounting

**Files:** Policy `UsageLedger.kt`, `BucketClock.kt`; tests `UsageLedgerTest.kt`, `BucketClockTest.kt`; app `data/UsageRepository.kt`, `runtime/AndroidClock.kt`.

**Interfaces:** `UsageLedger.transition(observation: Observation, decision: Decision, clock: ClockSnapshot): LedgerUpdate`; `checkpoint(clock: ClockSnapshot): LedgerUpdate`; `UsageRepository.apply(update: LedgerUpdate)` atomically persists group counters; `BucketClock.bucket(period: QuotaPeriod, clock: ClockSnapshot): BucketId`.

- [ ] Write failing tests: Chrome 420,000 ms plus Telegram 480,000 ms exhausts 900,000 ms for all four group targets; overlapping matches count once; denied use/screen-off counts zero; 11:00 resets the 10:00 bucket; crossing 10:45–11:15 permits the agreed 30 minutes when cap is off; restarting preserves counters; backwards clock does not replay an old bucket; repeated DST hour buckets are distinct; stale revision updates are rejected. With a 900,000 ms continuous cap, 10:45 usage denies at 11:00 despite the hourly reset; switching apps and a 299,999 ms absence preserve session use; a 300,000 ms uninterrupted break resets it; denied attempts do not interrupt the break; screen-off counts as absence and does not add usage.
- [ ] Run `./gradlew.bat :policy:test --tests '*UsageLedgerTest' --tests '*BucketClockTest'`; expect behavioral failures.
- [ ] Implement monotonic interval accumulation, fixed-zone buckets, checkpoint/reopen state, optional continuous-session cap/break accounting, and max five-second active-use checkpoints. Session use survives quota resets; after reboot use persisted last-use metadata conservatively without claiming trusted-clock guarantees. Never charge unobserved process downtime; document its bounded-loss limitation.
- [ ] Repeat focused tests, then all `:policy:test`. Commit `feat: track shared foreground usage budgets`.

## Task 4: Native app enforcement and first phone build

**Files:** App `accessibility/FocusGateAccessibilityService.kt`, `accessibility/ForegroundObserver.kt`, `runtime/EnforcementController.kt`, `runtime/TransitionScheduler.kt`, `runtime/BlockFeedback.kt`; tests `runtime/EnforcementControllerTest.kt`, `runtime/TransitionSchedulerTest.kt`; update accessibility XML/manifest.

**Interfaces:** `ForegroundObserver.onEvent(event: AccessibilityEvent): Observation?`; `EnforcementController.accept(observation: Observation)`; `TransitionScheduler.schedule(at: Instant?, action: () -> Unit)`; `HomeAction.perform(): Boolean` is injected around AccessibilityService.performGlobalAction.

- [ ] Write failing tests with a fake clock/HomeAction: denial calls Home; launcher/self/emergency observations do not loop; static foreground reaches quota exhaustion without events and calls Home; schedule activation triggers reevaluation; repeated same-event messages/actions are throttled; failed Home action records a typed failure without recursive retries.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest`; confirm behavior failures, then implement narrow package/window event routing and serialized IO/evaluation off accessibility callbacks. Service connection restores policy and counters rather than resetting them.
- [ ] Run policy/app unit tests and assembleDebug. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`; ask the user to grant Accessibility in the phone UI after showing disclosure. Never grant silently through ADB.
- [ ] With user-reviewed enabled presets, verify native YouTube/Instagram Home redirection and a short temporary quota. Restore the intended 15-minute rule after testing. Record results in `docs/testing/phone-acceptance.md`; no long locked session yet. Commit `feat: enforce app blockers through accessibility`.

## Task 5: Restricted Mode, password release and safe mutation

**Files:** Policy `RestrictedSession.kt`, `MutationGuard.kt`; tests `RestrictedSessionTest.kt`, `MutationGuardTest.kt`; app `security/PasswordVerifier.kt`, `data/SessionRepository.kt`, `runtime/RestrictedSessionController.kt`; instrumentation `security/SessionPersistenceTest.kt`.

**Interfaces:** `ReleasePolicy`: PASSWORD, TIMER, PASSWORD_OR_TIMER, PASSWORD_AND_TIMER; `LockedSession(id, releasePolicy, deadlineUtc, zoneId, allowRestrictiveAdditions, lockedRevision)` plus durable clock checkpoint. `PolicyCommand` defines create/edit/disable/delete rule, add targets, reduce allowance, protection edits and import. `MutationGuard.authorize(command, session, policy): MutationResult`; `RestrictedSessionController.start(request: LockRequest): StartResult`; `release(password: CharArray?): ReleaseResult`; `SessionRepository.read(): LockedSession?`.

- [ ] Write failing truth-table tests for all four release modes; early correct AND password leaves locked and requires a new password at deadline; timer expiry leaves blocker enabled; correct OR password releases early; missing required verifier rejects start.
- [ ] Write failing mutation tests: locked disable/delete/import/password change/protection weakening is denied; add new enabled rule/target or lower allowance is accepted only if allowed; enabling/lowering a continuous cap is restrictive, disabling/raising it or reducing the required break is denied; group target addition keeps counters; stale mutation racing lock-start cannot commit; a newly added rule cannot be removed while locked.
- [ ] Run focused policy tests; expect failures. Implement guards in repository transactions and durable lock-start/release. Implement salted PBKDF2-HMAC-SHA256 verifier with 16-byte random salt and 32-byte output, versioned work factor benchmarked for roughly 250–500 ms on the phone; no plaintext storage/logging. Persist capped 15-minute exponential retry delay independently from timer expiry.
- [ ] Run policy/unit tests and instrumentation verifying locked state/verifier delay after reopen and failed transaction behavior. Enable a short releasable session on the phone and verify password OR timer. Commit `feat: lock configuration with password and timer release`.

## Task 6: Chrome/Edge website adapters and safe unknown handling

**Files:** App `accessibility/browser/BrowserAdapter.kt`, `ChromeAdapter.kt`, `EdgeAdapter.kt`, `BrowserObservationCache.kt`; tests `accessibility/browser/BrowserAdapterTest.kt`; policy match tests extend task 1.

**Interfaces:** `BrowserAdapter.inspect(snapshot: AddressBarSnapshot): BrowserResult` returns KnownUrl(url), UnknownUrl, or NotBrowser; `AddressBarSnapshot` contains only relevant IDs/text/window/navigation signals, never full node dumps; `BrowserObservationCache.observe(result, navigationToken): String?` invalidates on uncertainty.

- [ ] Write failing fixture tests: known YouTube host denies, unknown URL remains allowed absent independent rule, browser app quota still denies unknown URL when exhausted, stale YouTube URL is cleared on new navigation, address-bar editing alone is not committed navigation, tab switching invalidates stale cache.
- [ ] Run app unit tests and match tests; confirm failures, then implement adapters from narrow on-phone observation with capped one-second retry budget. Never persist/log raw URL/node text. Provide typed adapter status and local match tester.
- [ ] Verify normal/private tabs, hidden address bar, redirects and tab changes on installed Chrome/Edge. Record browser versions and limitations; no arbitrary remote pages opened by automated tooling. Repeat unit tests and record phone results. Commit `feat: block confidently identified browser websites`.

## Task 7: Optional Device Admin, Settings and Recents protections

**Files:** App `admin/FocusGateAdminReceiver.kt`, `accessibility/system/SettingsAdapter.kt`, `RecentsAdapter.kt`, `RepairGrantScope.kt`, `ui/ProtectionSettings.kt`; tests `accessibility/system/SystemProtectionTest.kt`; device_admin XML and manifest.

**Interfaces:** `SettingsAdapter.classify(snapshot: SystemScreenSnapshot): SettingsScreen` returns Sensitive(kind), Wifi, MobileNetwork, Other, Unknown; `RecentsAdapter.classify(snapshot): RecentsState`; `RepairGrantScope.allows(screen, now): Boolean` authorizes only a named missing grant route for at most 120 seconds.

- [ ] Write failing tests: sensitive-only denies known app/admin/Accessibility/date-time routes and allows unknown; whole-Settings denies unknown but permits validated chosen Wi-Fi exception; repair scope cannot allow app-info/data-clear/uninstall; expired repair scope denies; emergency/dialer/launcher are never classified as protected targets; Recents action does not loop on launcher.
- [ ] Run tests, then implement device-specific capability reporting, bounded classification, optional ordinary Device Admin request, and scoped repair action. Declare an empty admin policy list if accepted by the platform; do not request wipe/device-lock/password policies just to acquire administrator status. Verify activation on the phone and report unsupported behavior honestly.
- [ ] Ask user to approve the Android grant UI when enabling admin. Test refused launcher uninstall without actually confirming a successful removal; test Settings routes, deactivation interception, quick network settings, gestures and three-button Recents where feasible. Do not change phone navigation mode without user agreement. Update acceptance document. Commit `feat: add optional uninstall and system settings friction`.

## Task 8: Health, boot and lifecycle recovery

**Files:** App `health/HealthMonitor.kt`, `HealthWorker.kt`, `SetupPreflight.kt`, `runtime/BootReceiver.kt`, `data/DirectBootSessionStore.kt`; tests `health/SetupPreflightTest.kt`, `runtime/RecoveryTest.kt`; manifest receiver/storage updates.

**Interfaces:** `HealthMonitor.inspect(): HealthSnapshot` uses Connected, Missing, Unknown and UserConfirmed statuses; `SetupPreflight.check(policy, sessionRequest, health): PreflightResult`; `RecoveryCoordinator.reconcile(clock: ClockSnapshot): RecoveryResult` reloads state without reset.

- [ ] Write failing tests: disconnected Accessibility refuses start; optional capability gap requires acknowledgement; unknown autostart never reports verified; service reconnect/boot preserves groups/session; timed expiry during process absence releases configuration on next reconciliation; unavailable service marks health degraded without clearing lock; clock anomaly does not create an indefinite timer lock.
- [ ] Run tests, then implement event-triggered health and unique 30-minute WorkManager work. Configure BOOT_COMPLETED/LOCKED_BOOT_COMPLETED/PACKAGE_REPLACED handling with only necessary exports and minimal device-protected session metadata. Do not start an unnecessary foreground service.
- [ ] Run instrumentation and phone screen-off/on, activity swipe-away, launcher restart, ordinary process death/service restart and reboot recovery tests. Obtain user timing agreement before rebooting their phone; document connection gaps separately from persisted-state results. Commit `feat: recover locked sessions and report setup health`.

## Task 9: Complete UI, import/export, diagnostics and help

**Files:** App `ui/blockers/BlockersScreen.kt`, `BlockerEditor.kt`, `AppPicker.kt`, `ui/restricted/RestrictedModeScreen.kt`, `ui/more/MoreScreen.kt`, `io/ConfigurationTransfer.kt`, `diagnostics/LocalDiagnostics.kt`, `donations/DonationConfig.kt`, `donations/DonationPanel.kt`, `runtime/RemainingTimeNotification.kt`; tests `io/ConfigurationTransferTest.kt`, `diagnostics/LocalDiagnosticsTest.kt`; instrumentation `ui/LockedControlsTest.kt`; `docs/user-guide.md`.

**Interfaces:** `ConfigurationTransfer.preview(json: String, mode: ImportMode): ImportPreview`; `apply(preview, expectedRevision): MutationResult`; `export(policy, preferences): String`; `LocalDiagnostics.record(event: DiagnosticEvent)` accepts only typed non-content fields. `DonationConfig.DONATION_NETWORK` and `DONATION_ADDRESS` are empty strings with maintainer comments.

- [ ] Write failing tests: replace/merge import validates references and remaps IDs; oversized input (>1 MB)/future schema/invalid regex rejects atomically; locked import rejects; export excludes verifier/session/counters/logs; diagnostics cap is 1 MB with no URL/password fields; empty donation address hides payment/copy/QR controls.
- [ ] Run tests, then build three-tab UI, editable schedule/quota and optional continuous-cap/break controls, installed app picker, local regex tester, start/release preflight, shared remaining-time display, optional notification and small accessibility overlay. Show the smaller of period/session allowance and remaining required break when capped. Safely persist preferences independently of locked policy.
- [ ] Run UI instrumentation: locked disabling is unavailable; allowed restrictive additions work; password release permits disabling preset then relocking; expiry leaves blockers active. Verify text scaling/light/dark themes and no trapped launcher feedback.
- [ ] Add local SAF JSON import/export, opt-in read-only diagnostics, static donation configuration and local QR rendering only for nonempty configuration. Write introduction/FAQ/setup/uninstall guidance from accepted spec. Commit `feat: complete blocker management and offline user tools`.

## Task 10: Acceptance, packaging and GitHub history

**Files:** `docs/testing/phone-acceptance.md`, `docs/testing/known-limitations.md`, `README.md`; build configuration only if verification finds defects.

- [ ] Run `./gradlew.bat :policy:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest`; inspect actual failures and fix their cause before claiming success. Repeat checks only after relevant changes.
- [ ] Inspect merged manifest, backup rules, component exports, dependency tree, logs and exports for network permissions and sensitive data. Compare idle/active CPU and battery over documented baseline sessions; no unmeasured battery guarantee.
- [ ] Finish the spec's phone matrix including native app latency, browser latency, group exhaustion, all release policies with short sessions, admin friction and lifecycle availability gaps. Record PASS/FAIL/UNSUPPORTED/NOT RUN accurately; missing core cases prevent first-release completion.
- [ ] Deliver debug APK path and readable build/setup/user guide; release signing is a separate decision, never invent or commit private keys. Record app/browser/OS versions and known limitations. Keep every preset configurable and donation address blank.
- [ ] Commit final documentation. Push authorized commits when credential-manager authentication is usable; if still rejected, preserve local commits and request reauthentication without reusing the exposed token.

## Plan self-review and execution handoff

Coverage: policy/schedules/overlap/clock tasks 1–3; enforcement/feedback task 4; lock/password/mutation task 5; browser observation task 6; Settings/Recents/admin task 7; permissions/health/recovery task 8; UI/help/privacy/import/donations/logging task 9; measurable phone verification and delivery task 10. The five review-focus cases each have an explicit owning test.

Deferred by accepted design: rolling-hour quotas, automatic temporary-pause/relock, remote replacement website redirects, broader browser adapters and stronger device-owner management. The user requested continuous-session caps while planning: they are included in tasks 3, 5 and 9, optional and configurable. Deferral never changes the first-release clock-hour allowance or configurable presets.

Next gate: user reviews this concrete plan and chooses native implementation or subagent-driven execution. Recommendation: native implementation with an independent final review; these tasks share tightly coupled Android state and phone observations, so direct execution keeps iteration practical. No implementation, dependency installation, long locked session, or permission change begins before that review.
