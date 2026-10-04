# FocusGate implementation plan

**TL;DR:** This is the original ten-task plan, not the current task tracker. It covers policy, storage, quotas, Accessibility, locking, browsers, optional protections, recovery, UI, and delivery. Read [the roadmap](../../roadmap.md) for completed work and the next tasks. The user already approved development; do not ask for the original approval again.

> Historical plan. Unchecked steps preserve the original test and implementation intentions. They do not mean the feature is still missing. Later user decisions and the accepted design override stale details. New AND locks are forbidden. Existing AND sessions keep their release policy. The current Room document also stores preferences; the original Room/DataStore split is not a migration instruction.

> For agentic work, use `superpowers:subagent-driven-development` or `superpowers:executing-plans` for an approved task. Use current development instructions before running any historical device command.

**Goal:** Deliver an offline Android self-control app with editable blockers, shared foreground quotas, password/timer configuration locking, and tested Xiaomi recovery.

**Architecture:** Pure Kotlin policy owns decisions and change guards. Android adapters supply observations. Local transactions store rules, sessions, and counters. Accessibility returns Home on denial. Device-specific protections do not grant device-owner powers.

**Original toolchain plan:** Kotlin, Compose, Room, DataStore, WorkManager, JUnit, instrumentation, and ADB. Start from AGP 8.11.2, Gradle 8.13, compile/target SDK 36, and min SDK 29. Check official compatibility and local caches before setup. Use Java 21 with JVM bytecode target 17. Pin compatible stable Kotlin/Compose/AndroidX versions in a catalog. Do not guess versions. Android 14 / API 34 is the first phone target. See [development context](../../development.md) for the implemented toolchain and storage.

**Spec:** [Accepted design](../specs/2026-10-03-focusgate-design.md).

## Global rules

- Use application ID `io.github.pesterevnikita.focusgate` and label FocusGate.
- Exclude `INTERNET` and `ACCESS_NETWORK_STATE` from the merged manifest. Add no VPN, remote SDK, cloud sync, remote redirect, or telemetry.
- Store presets as ordinary editable rules. Hardcode no blacklist in the evaluator.
- Default to 900,000 combined milliseconds per clock hour. No rollover. Support daily periods too.
- Website rules allow unknown URLs. Independent app/quota rules still apply.
- Restricted Mode prevents weaker settings. Starting or releasing it does not toggle blockers.
- Preserve state across ordinary restarts. Permissions, force-stop, and Android suspension can interrupt enforcement.
- Explain policy, lifecycle, and adapter behavior with plain comments.
- Commit no tokens, signing secrets, phone identifiers, private logs, or local SDK paths.
- Use optional ordinary Device Admin only. Add no device-owner, wipe, or device-password policies.
- New locks offer PASSWORD, TIMER, and PASSWORD_OR_TIMER. Keep PASSWORD_AND_TIMER only for existing sessions.
- Preserve saved credentials and lock choices on release/relock. Remove passwords only while unlocked, with current-password verification. Add no ADB credential/release/reset endpoint.

## Review focus

1. Policy edits and observations must not charge an old revision or bypass a new lock: tasks 2 and 5.
2. Hidden/stale address bars must not apply the previous URL to an unrelated page: task 6.
3. Static pages must return Home at quota exhaustion without another event: task 4.
4. An early correct password must not release a legacy AND session or authorize a later release: task 5. Reject creation of new AND sessions.
5. Settings protection must allow safe grant repair and avoid blocking emergency/system UI: task 7.

## Proposed files and contracts

The paths and interfaces below describe the original plan. Implemented file names can differ. Use development context and source for current ownership.

`:policy` uses `policy/src/main/kotlin/io/github/pesterevnikita/focusgate/policy/`. Its tests use the matching `policy/src/test/kotlin/` package. `:app` uses `app/src/main/java/io/github/pesterevnikita/focusgate/`. App tests use matching `src/test/java/` and `src/androidTest/java/` packages.

Original root setup files: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, wrapper, and `.gitignore`. Original app setup: `app/build.gradle.kts`, manifest, accessibility/admin XML, backup/data-extraction XML, and strings. Current Gradle scripts are Groovy; do not rename them because of this historical list.

Proposed `PolicyModels.kt` contracts:

- `RuleId`, `GroupId`: string value types. `PolicyRevision`: long value type.
- `Target`: `App(packageName)`, `Host(domain, includeSubdomains)`, or `UrlRegex(pattern)`.
- `Schedule`: timezone, optional date range, weekdays, and local start/end windows. Empty windows mean all day on selected weekdays.
- `Blocker(id, name, enabled, targets, schedule, quotaGroupId, message)`. No group means deny while applicable.
- `QuotaGroup(id, period: HOUR|DAY, allowanceMillis, continuousCapMillis: Long?, requiredBreakMillis: Long)`. Null cap disables session limits. Proposed editor values: 900,000 ms cap and 300,000 ms break. Key period usage by group and actual period-start instant. Save session use and last allowed-use end separately.
- `Observation(packageName, visibleUrl: String?, interactive: Boolean, observedAt: Instant, elapsedMillis: Long, revision: PolicyRevision)`. Null URL means unknown and never matches.
- `PolicySnapshot(revision, blockers, groups)`. `UsageSnapshot` maps group/bucket to consumed milliseconds.
- `ClockSnapshot(instant, elapsedMillis, bootId, zoneId)`. Inject clocks for deterministic tests.
- `Decision(denied, denyingRuleIds, billableGroupIds, nextTransitionAt: Instant?)`.

Task 5 owns mutation/session contracts. Keep them out of UI-only guards. Document interface or migration changes when extending contracts.

## Task 1: Policy engine and Android shell

**Files:** root/app setup; policy `PolicyModels.kt`, `ScheduleEvaluator.kt`, `UrlMatcher.kt`, `PolicyEngine.kt`; their tests; app `MainActivity.kt`, `ui/FocusGateApp.kt`.

**Interfaces:**

- `PolicyEngine.evaluate(policy: PolicySnapshot, usage: UsageSnapshot, observation: Observation, clock: ClockSnapshot): Decision`
- `ScheduleEvaluator.isActive(schedule: Schedule, instant: Instant, zoneId: ZoneId): Boolean`
- `UrlMatcher.matches(target: Target, visibleUrl: String?): Boolean`

- [ ] Check official toolchain compatibility and cached dependencies. Pin versions. Create only enough setup to compile tests. Set Java/Kotlin targets and wrapper checksum. Ignore `local.properties`, outputs, signing material, and diagnostics.
- [ ] Write failing tests: enabled rules deny outside Restricted Mode; disabled rules allow; denial wins over quota; unknown URLs do not match; `youtube.com` matches and `notyoutube.com` does not; Monday overnight includes Tuesday 01:00 but excludes Tuesday 02:00.
- [ ] Run `./gradlew.bat :policy:test`. Confirm behavior tests fail before evaluator implementation.
- [ ] Implement contracts and evaluation. Keep host parsing independent of Android. Pin a linear-time regex engine with full-match semantics. Compute next schedule/quota transitions as well as current denial.
- [ ] Run `./gradlew.bat :policy:test :app:assembleDebug`. Require passing tests and an APK. Launch three tabs without silently enabled presets. Commit `feat: add offline app shell and policy engine`.

## Task 2: Storage and editable presets

**Files:** app `data/FocusGateDatabase.kt`, `Entities.kt`, `ConfigurationRepository.kt`, `PresetFactory.kt`, `UiPreferences.kt`; instrumentation `data/ConfigurationRepositoryTest.kt`.

**Interfaces:** `observePolicy(): Flow<PolicySnapshot>`, `readPolicy(): PolicySnapshot`, and suspend `transact(expectedRevision: PolicyRevision, command: PolicyCommand): MutationResult` on `ConfigurationRepository`. Task 5 supplies authorization; start with unlocked changes only. `PresetFactory.create(): PresetBundle` generates new IDs. Put Room schema exports in `app/schemas/`.

- [ ] Write failing storage tests. Disabled presets stay disabled after reopen. Editing changes only saved targets. Two commands at one revision produce a conflict on the second. A failed import-shaped transaction keeps the original revision.
- [ ] Run the new instrumentation tests and confirm failures. The original command was `./gradlew.bat :app:connectedDebugAndroidTest`. For the current phone, use the safe manual-install procedure in development context; instrumentation can stop Accessibility. Do not enable admin/blockers for the original storage fixture.
- [ ] Implement atomic versioned Room state, preferences, and a preset factory. Add YouTube/Instagram continuous rules and one hourly Chrome/Edge/Telegram/Ozon shopping group. Exclude Ozon Bank. Disable private-state backup/device transfer.
- [ ] Re-run the fixture. Verify reopen and atomicity. Unsaved/disabled presets must not participate. Commit `feat: persist editable blockers and presets`.

## Task 3: Usage ledger and clocks

**Files:** policy `UsageLedger.kt`, `BucketClock.kt` and tests; app `data/UsageRepository.kt`, `runtime/AndroidClock.kt`.

**Interfaces:** `UsageLedger.transition(observation: Observation, decision: Decision, clock: ClockSnapshot): LedgerUpdate`; `checkpoint(clock: ClockSnapshot): LedgerUpdate`; `UsageRepository.apply(update: LedgerUpdate)` saves counters atomically; `BucketClock.bucket(period: QuotaPeriod, clock: ClockSnapshot): BucketId`.

- [ ] Write failing tests for shared usage: Chrome 420,000 ms plus Telegram 480,000 ms exhausts 900,000 ms for all four targets. Count overlapping matches once. Charge zero for denied use or screen-off. Reset the 10:00 bucket at 11:00. With cap off, permit the agreed 10:45–11:15 boundary burst.
- [ ] Test counter preservation after restart, backward-clock replay prevention, distinct repeated DST buckets, and stale-revision rejection.
- [ ] Test a 900,000 ms session cap starting at 10:45: deny at 11:00 despite period reset. App switching and 299,999 ms absence retain use. A 300,000 ms uninterrupted break resets it. Denied attempts do not interrupt the break. Screen-off counts as absence, not use.
- [ ] Run `./gradlew.bat :policy:test --tests '*UsageLedgerTest' --tests '*BucketClockTest'`. Confirm behavior failures.
- [ ] Implement monotonic intervals, fixed-zone buckets, checkpoints, and optional cap/break accounting. Save active usage at least every five seconds. Keep session use across period resets. Use saved last-use metadata conservatively after reboot. Do not claim a trusted clock or charge unobserved downtime. Document bounded loss.
- [ ] Re-run focused tests and all `:policy:test`. Commit `feat: track shared foreground usage budgets`.

## Task 4: Native app enforcement and first phone build

**Files:** app `accessibility/FocusGateAccessibilityService.kt`, `ForegroundObserver.kt`; runtime `EnforcementController.kt`, `TransitionScheduler.kt`, `BlockFeedback.kt` and tests; accessibility XML/manifest.

**Interfaces:** `ForegroundObserver.onEvent(event: AccessibilityEvent): Observation?`; `EnforcementController.accept(observation: Observation)`; `TransitionScheduler.schedule(at: Instant?, action: () -> Unit)`; injected `HomeAction.perform(): Boolean` wraps `AccessibilityService.performGlobalAction`.

- [ ] Write failing fake-clock/Home tests. Denial returns Home. Launcher/self/emergency observations do not loop. Static content reaches quota exhaustion without another event. Schedule activation re-evaluates. Throttle repeated actions/messages. A failed Home action records a typed reason without recursive retries.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest` and confirm failures. Implement narrow package/window routing with serialized IO/evaluation outside callbacks. Reconnect loads saved policy/counters without reset.
- [ ] Run policy/app tests and assemble debug. Update with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Show the disclosure and ask the user to grant Accessibility in the UI. Do not grant it through ADB.
- [ ] Test native YouTube/Instagram and a short quota using user-reviewed presets. Restore 15 minutes afterward. Record measured results in phone acceptance. Do not create a long lock. Commit `feat: enforce app blockers through accessibility`.

## Task 5: Restricted Mode, passwords, and change guards

**Files:** policy `RestrictedSession.kt`, `MutationGuard.kt` and tests; app `security/PasswordVerifier.kt`, `data/SessionRepository.kt`, `runtime/RestrictedSessionController.kt`; instrumentation `security/SessionPersistenceTest.kt`.

**Interfaces:** `ReleasePolicy` contains PASSWORD, TIMER, PASSWORD_OR_TIMER, and legacy PASSWORD_AND_TIMER. New starts reject AND. `LockedSession(id, releasePolicy, deadlineUtc, zoneId, allowRestrictiveAdditions, lockedRevision)` also stores clock checkpoint data. `PolicyCommand` covers rule creation/edit/disable/delete, target additions, lower allowances, protections, and import. Other contracts: `MutationGuard.authorize(command, session, policy): MutationResult`; `RestrictedSessionController.start(request: LockRequest): StartResult`; `release(password: CharArray?): ReleaseResult`; `SessionRepository.read(): LockedSession?`.

- [ ] Write failing release truth tables for supported choices and legacy AND. An early correct legacy-AND password leaves the lock active and requires a fresh password at the deadline. Reject new AND starts. Timer expiry keeps blockers enabled. Correct OR password releases early. Missing required verifier rejects start.
- [ ] Write failing locked-change tests. Reject disable/delete/import/password changes/weaker protections. Allow enabled new rules, targets, or lower allowance only when selected. Enabling/lowering a cap is restrictive; disabling/raising it or shortening a break is weakening. Adding a group target keeps counters. A stale change racing lock start cannot commit. A new locked rule cannot be removed.
- [ ] Run focused policy tests and confirm failures. Implement transactional guards, durable start, and release. Use versioned salted PBKDF2-HMAC-SHA256 with a 16-byte random salt and 32-byte output. Benchmark roughly 250–500 ms on the phone. Store/log no plaintext. Persist exponential retry delay capped at 15 minutes. Keep timer release independent.
- [ ] Run policy/app tests and targeted instrumentation. Check lock/retry persistence after reopen and failed transactions. Test short Password OR timer on the phone. Commit `feat: lock configuration with password and timer release`.

Later password follow-ups require current/new/confirmation for changes, unlocked-only authenticated removal, no-old-password setup after removal, retained credentials/choices, and fresh deadlines on relock. See the accepted design and password-removal plan.

## Task 6: Chrome/Edge website adapters

**Files:** app `accessibility/browser/BrowserAdapter.kt`, `ChromeAdapter.kt`, `EdgeAdapter.kt`, `BrowserObservationCache.kt` and fixture tests; extend policy matching tests.

**Interfaces:** `BrowserAdapter.inspect(snapshot: AddressBarSnapshot): BrowserResult` returns `KnownUrl(url)`, `UnknownUrl`, or `NotBrowser`. Snapshot includes only relevant IDs/text/window/navigation signals, not full node dumps. `BrowserObservationCache.observe(result, navigationToken): String?` clears uncertain navigation.

- [ ] Write failing fixtures. Known YouTube denies. Unknown URLs allow without an independent rule. Exhausted browser quota still denies unknown URLs. New navigation clears a stale URL. Address-bar typing alone is not committed navigation. Tab changes clear stale cache.
- [ ] Run app and matching tests and confirm failures. Build narrow adapters from phone observations. Cap retries at one second. Save/log no raw URL or node text. Add typed adapter status and a local match tester.
- [ ] Test installed Chrome/Edge normal/private tabs, hidden bars, redirects, and tab changes. Record versions and limits. Automated tooling must not open arbitrary remote pages. Re-run unit tests and record phone evidence. Commit `feat: block confidently identified browser websites`.

## Task 7: Optional admin, Settings, and Recents

**Files:** app `admin/FocusGateAdminReceiver.kt`, `accessibility/system/SettingsAdapter.kt`, `RecentsAdapter.kt`, `RepairGrantScope.kt`, `ui/ProtectionSettings.kt`; `SystemProtectionTest.kt`; admin XML/manifest.

**Interfaces:** `SettingsAdapter.classify(snapshot: SystemScreenSnapshot): SettingsScreen` returns `Sensitive(kind)`, `Wifi`, `MobileNetwork`, `Other`, or `Unknown`. `RecentsAdapter.classify(snapshot): RecentsState`. `RepairGrantScope.allows(screen, now): Boolean` permits only a named missing-grant route for at most 120 seconds.

- [ ] Write failing tests. Sensitive mode denies known app/admin/Accessibility/date-time routes and allows unknown. Whole-Settings mode denies unknown but permits a selected validated Wi-Fi exception. Repair cannot exempt app-info/data-clear/uninstall. Expired repair denies. Do not classify emergency/dialer/launcher as blocked. Recents Home actions must not loop on the launcher.
- [ ] Run tests. Implement capability reporting, bounded detection, optional admin request, and scoped repair. Use an empty admin policy list if Android accepts it. Request no wipe/device-lock/password policy just to obtain admin status. Verify activation and report unsupported behavior.
- [ ] Let the user approve the Android admin grant UI. Test a refused launcher uninstall without confirming successful removal. Test Settings, admin deactivation, quick network controls, gestures, and button Recents where feasible. Ask before changing navigation mode. Update acceptance. Commit `feat: add optional uninstall and system settings friction`.

## Task 8: Health, boot, and recovery

**Files:** app `health/HealthMonitor.kt`, `HealthWorker.kt`, `SetupPreflight.kt`, `runtime/BootReceiver.kt`, `data/DirectBootSessionStore.kt`; health/recovery tests; receiver/storage manifest changes.

**Interfaces:** `HealthMonitor.inspect(): HealthSnapshot` uses `Connected`, `Missing`, `Unknown`, and `UserConfirmed`. `SetupPreflight.check(policy, sessionRequest, health): PreflightResult`. `RecoveryCoordinator.reconcile(clock: ClockSnapshot): RecoveryResult` loads saved state without reset.

- [ ] Write failing tests. Disconnected Accessibility rejects start. Optional gaps require acknowledgement. Unknown autostart is not verified. Reconnect/boot retains groups/session. Expired locks release on the next reconciliation. Missing service marks degradation without clearing the lock. Clock anomalies cannot create an indefinite timer lock.
- [ ] Run tests. Add event-triggered checks and unique 30-minute WorkManager work. Handle `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, and `PACKAGE_REPLACED` with minimal exports/direct-boot metadata. Add no unnecessary foreground service. Later measured Xiaomi evidence approved foreground promotion of the existing service; follow current design.
- [ ] Test instrumentation and phone screen off/on, swipe-away, launcher restart, ordinary process death/service restart, and reboot. Coordinate reboot timing with the user. Record connection gaps separately from persistence. Commit `feat: recover locked sessions and report setup health`.

## Task 9: UI, transfer, diagnostics, and help

**Files:** app `ui/blockers/BlockersScreen.kt`, `BlockerEditor.kt`, `AppPicker.kt`, `ui/restricted/RestrictedModeScreen.kt`, and `ui/more/MoreScreen.kt`; `io/ConfigurationTransfer.kt`, `diagnostics/LocalDiagnostics.kt`, `donations/DonationConfig.kt`, `DonationPanel.kt`, and `runtime/RemainingTimeNotification.kt`; `ConfigurationTransferTest.kt`, `LocalDiagnosticsTest.kt`, and `ui/LockedControlsTest.kt`; `docs/user-guide.md`.

**Interfaces:** `ConfigurationTransfer.preview(json: String, mode: ImportMode): ImportPreview`; `apply(preview, expectedRevision): MutationResult`; `export(policy, preferences): String`. `LocalDiagnostics.record(event: DiagnosticEvent)` accepts typed non-content fields only. Donation network/address constants start empty with maintainer comments.

- [ ] Write failing tests. Replace/merge validates references and remaps IDs. The original proposed oversized-import boundary was >1 MB; check current importer limits before using this historical value. Reject future schemas/invalid regex atomically. Reject locked import. Exclude verifier/session/counters/logs from export. The proposed diagnostic cap was 1 MB, without URL/password fields. Empty donation address hides payment/copy/QR controls.
- [ ] Run tests. Build three tabs, schedule/quota editors, optional cap/break controls, installed-app picker, regex tester, lock preflight, shared countdown, optional notification, and small Accessibility overlay. Show the smaller period/session allowance and the remaining required break. Save display preferences without allowing weaker locked policy.
- [ ] Test UI guards. Reject locked disabling and allow permitted additions. Password release permits disabling a preset and relocking. Expiry leaves blockers enabled. Check text scaling, themes, and launcher feedback.
- [ ] Add local SAF JSON transfer, opt-in read-only diagnostics, static donation settings, and local QR only for a nonempty address. Write introduction, FAQ, setup, and uninstall help. Commit `feat: complete blocker management and offline user tools`.

## Task 10: Acceptance, packaging, and publishing

**Files:** phone acceptance, known limitations, and README. Change build settings only for proven defects.

- [ ] The original full command was `./gradlew.bat :policy:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest`. Use current development instructions to install/run a narrow test APK safely instead of blindly running its device-test task. Inspect failures and fix their causes. Repeat checks only after relevant changes.
- [ ] Inspect merged manifest, backups, exports, dependencies, logs, and transfer files for network permissions and private data. Compare idle/active CPU and battery against a documented baseline. Make no unmeasured battery promise.
- [ ] Complete the spec's phone matrix: native/browser latency, shared exhaustion, supported release choices, admin friction, and recovery gaps. Test legacy AND separately in policy/storage fixtures. Record PASS, FAIL, UNSUPPORTED, or NOT RUN accurately. Missing core cases prevent release completion.
- [ ] Deliver the debug APK path and readable build/setup/usage guides. Decide release signing separately; invent/commit no private key. Record app/browser/OS versions and limits. Keep presets configurable and donation address blank.
- [ ] Commit final documentation. Push authorized commits through normal credential-manager authentication. If rejected, retain local commits and request reauthentication. Never reuse the token exposed in chat.

## Self-review and handoff

Tasks 1–3 cover policy, schedules, overlapping rules, and clocks. Task 4 covers enforcement/feedback. Task 5 covers lock/password guards. Task 6 covers browser observation. Task 7 covers optional protections. Task 8 covers setup/recovery. Task 9 covers UI/help/transfer/privacy/donations/logging. Task 10 covers measured acceptance and delivery. Each review-focus case has an owning test task.

Deferred: rolling-hour quotas, automatic temporary pause/relock, remote replacement redirects, broader browser adapters, and stronger device-owner management. Optional continuous-session limits are included in tasks 3, 5, and 9. These deferrals do not change clock-hour quotas or editable presets.

The user approved execution and the core app has been built and installed. Continue from [the roadmap](../../roadmap.md) and [development context](../../development.md). Do not restart this plan or ask for its original approval again.
