# FocusGate — Android self-control app specification

Status: design accepted by the user on 2026-10-03 with follow-up corrections incorporated below. Implementation-plan review is the next workflow stage.
Date: 2026-10-03.
Repository: https://github.com/pesterevnikita/app1

## 1. Purpose and confirmed brief

FocusGate helps a willing user stop procrastinating in selected apps and websites. When a rule blocks the current activity, the app sends the user to the launcher and optionally explains why. It is a self-control enforcement tool, not a security product against a determined attacker with physical access, ADB, root, bootloader, or factory-reset capabilities.

Confirmed by the user:

- Use a normal Android application, without device-owner provisioning.
- No VPN and no network activity by the application. All enforcement, configuration, and diagnostics are local.
- Enabled blockers enforce their rules even outside Restricted Mode.
- Restricted Mode locks configuration against weakening; it is not the switch that activates blockers.
- YouTube and Instagram should be blocked continuously, both native apps and websites.
- Edge, Chrome, Telegram, and Ozon shopping share one combined 15-minute allowance per hour.
- Primary device: Xiaomi 11T, HyperOS 1.0.15.0, Android 14.
- Simple UI, bottom navigation, permission guidance, FAQ, import/export, optional local diagnostics, and future offline donation information.

Read-only ADB inspection confirmed Android 14 / API 34 and model 21081111RG. The reported HyperOS version comes from the user. No phone settings were changed and no APK was installed during specification work.

User follow-up accepted the name FocusGate, clock-aligned quota windows (including 30 consecutive minutes spanning two hours), password OR deadline release, and restrictive-only additions. Website blocking must occur only on confidently identified matching URLs; unknown URLs remain accessible. Presets are ordinary editable/deletable blockers, never mandatory hardcoded enforcement. Optional Device Admin plus sensitive Settings protection should approximate the familiar normal uninstall friction of AppBlocker, subject to phone testing. Android 10+ minimum support and a fixed timezone during a locked session remain implementation defaults.

## 2. Scope and delivery boundaries

The first usable release must deliver both user scenarios, configuration locking, permission preflight, local persistence, usage accounting, remaining-time display, import/export, and tested lifecycle recovery on the primary phone. Device-specific protections are included only with accurate capability reporting.

Implementation should be divided into reviewable increments:

1. Pure policy engine, persistence, app blockers, basic UI, and local unit tests.
2. Chrome/Edge website adapters, shared budgets, notifications, and quota boundary tests.
3. Restricted Mode, password handling, permission preflight, restart recovery, and clock handling.
4. Xiaomi Settings/Recents protection experiments, optional Device Admin friction, health diagnostics, documentation, import/export, and phone acceptance tests.

These are delivery slices, not a substitute for the later implementation plan. Keep Settings/Recents heuristics isolated from the core policy engine.

Excluded: VPN, network inspection, remote administration, cloud sync, accounts, analytics, ads, root, device-owner enrollment, background screenshots, content recording, and copying/decompiling the dumped commercial APK. Build independently from public APIs and observed phone behavior.

## 3. Platform capabilities and honest limits

### 3.1 Enforcement

An enabled AccessibilityService receives relevant window events, identifies the foreground package, and uses GLOBAL_ACTION_HOME when a policy denies access. This redirects the UI; it does not terminate another process or prevent its background playback, downloads, notifications, or background network use. A short glimpse before redirection is possible.

Browser adapters inspect only known address-bar nodes in supported browser packages. They cannot guarantee visibility into every web page, private mode, hidden address bar, embedded WebView, or future browser version. They must never infer a URL from arbitrary page body text. Website filtering is UI observation, not a network firewall.

Source: [AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).

### 3.2 Uninstall resistance

Normal Device Admin is not equivalent to device owner. The dedicated setUninstallBlocked API is not available to an ordinary consumer app merely because it is an active legacy administrator. An optional active-admin flow may introduce an additional deactivation step on this phone; Settings interception may stop normal attempts to reach it while Accessibility is operating. Both require device verification.

Label the option “Uninstall resistance,” explain the limitation, and display whether Device Admin is active separately from whether Settings protection is available. Request ordinary Device Admin when the user enables this option and test long-press launcher uninstall, uninstall from Settings, and administrator deactivation on the Xiaomi. Android may retain/recreate the launcher icon when uninstall is refused; FocusGate must not fake this by reinstalling itself or creating duplicate shortcuts. Do not call privileged uninstall-blocking APIs or request unrelated wipe/password policies.

Source: [DevicePolicyManager.setUninstallBlocked](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setUninstallBlocked(android.content.ComponentName,%20java.lang.String,%20boolean)).

### 3.3 Persistence versus availability

The locked session, rules, and quota records must survive screen off/on, activity dismissal, launcher restart, service restart, reboot, and ordinary process death. They must not reset when a service reconnects or a UI opens.

Actual enforcement depends on Android running the AccessibilityService. OS/OEM termination can create an enforcement gap. A disabled service cannot silently re-enable itself; a periodic worker cannot guarantee timely recovery. Force-stop, safe mode, cleared app data, uninstallation, and permission revocation remain limitations. Settings protection should resist normal permission-revocation/force-stop navigation where observable, but cannot promise immunity.

After reboot, normal enforcement resumes when Android reconnects the enabled service and the required storage becomes available. A minimal direct-boot record can retain locked status before first unlock; do not promise Accessibility-based enforcement before unlock without phone evidence.

Sources: [Direct Boot](https://developer.android.com/privacy-and-security/direct-boot), [Android stopped-package behavior](https://developer.android.com/about/versions/android-3.1#launchcontrols).

### 3.4 Settings and Recents

Selective Settings protection and Recents redirection are adapter capabilities, not universal Android APIs. Test the HyperOS Settings app, Security app, app-info screens, permission screens, launcher menus, and gesture/three-button navigation separately. Never claim “Recents disabled”; the app attempts to return Home after detecting it.

If selective Settings classification is unreliable, offer explicitly chosen whole-Settings blocking. Do not silently upgrade selective blocking to whole-Settings blocking. Unknown screens in selective mode remain accessible and are reported as a coverage limitation.

## 4. Policy model

Each blocker has an immutable ID, name, enabled flag, target set, schedule, optional quota, message, and revision. Targets are application package IDs, website host rules, or advanced URL patterns. Labels/icons are presentation data; renaming an app must not change matching.

### 4.1 Schedules

- A blocker can be always active, active on selected weekdays/time windows, active within a date range, or a combination.
- Date range AND weekday/time conditions determine applicability. Multiple time windows within a schedule are ORed.
- Intervals are start-inclusive and end-exclusive. Overnight windows belong to their starting weekday; Monday 22:00–02:00 includes Tuesday 00:00–02:00.
- “Working hours” means user-configured weekdays and times, not a inferred holiday calendar. Weekend preset means Saturday/Sunday and is editable.
- A quota blocker allows access while applicable until its quota is exhausted. Outside its active schedule it imposes no restriction.
- A “block outside allowed hours” preset compiles into explicit blocking windows; its meaning must not be confused with quota applicability.
- Show a plain-language preview and next transitions before saving a rule.

### 4.2 Combining rules

Evaluate every applicable enabled blocker for the current target. Deny if ANY blocker denies. A quota allowance never overrides another block. Disabled blockers contribute nothing.

If multiple applicable quotas cover the current activity, the same foreground elapsed time counts toward all of them. Time counts once within each shared group, even when several target patterns match. Denied time does not consume allowance.

Show all denying rules in the app; the transient message uses a deterministic primary reason: unconditional denial first, then exhausted quota, then stable rule ID. Show the next candidate availability time, or “Blocked continuously,” without falsely promising access when another rule will still deny.

### 4.3 Shared quota semantics

Default group: Edge, Chrome, Telegram, Ozon shopping. Combined 15 minutes in each clock hour, such as [10:00, 11:00). No rollover. Show the next reset time.

Support hourly and daily fixed windows plus configurable allowance length. A rolling window is out of first-release scope. UI must say “per clock hour” because 15 minutes before and 15 minutes after a boundary can produce 30 consecutive minutes of access unless the optional continuous-session cap is enabled.

The user also requested a maximum continuous session feature. Each quota group can optionally have a maximum accumulated interactive-use duration per session and a required break duration. Proposed editor values are 15 minutes maximum and a five-minute break, configurable and off until selected. Switching within a group preserves the session; an hour/day reset does not reset it. Time away pauses accumulated use, but only one uninterrupted absence from all group targets lasting the required break resets the session. Screen-off/keyguard time counts toward that break. Returning early cancels the partial break. When the cap is exhausted, deny group access until the break completes, even if hourly allowance remains; attempted denied access does not consume usage or interrupt a break because the target was not allowed. Persist session use and last allowed-use end; restore elapsed absence conservatively using available clocks after reboot and document clock limits. Restricted Mode locks cap/break settings: enabling or lowering a cap is restrictive; disabling/raising it or shortening the required break is weakening. No automatic cap is hardcoded into presets.

Count interactive foreground use while screen on and unlocked. Stop at screen-off, keyguard, foreground change, denied activity, or unobservable state. Notification shade and Recents pause counting when detected. If a browser stays foreground, browsing across tabs does not reset the budget. No background audio accounting in version one.

For split-screen, count a group when a reliably observed interactive target is in use; if several windows belong to the same group, count elapsed time once. Unsupported multi-window/PiP classification must be surfaced and tested rather than assumed correct.

Use monotonic elapsed time for running consumption. Persist counters at foreground transitions, screen transitions, exhaustion, and at most five-second intervals during billable usage. Resume from a persisted checkpoint after process death; cap unrecorded usage loss at five seconds per ordinary death. Do not charge an entire unobserved downtime. Publish this small recovery limitation.

While a billable target is active, schedule a lightweight callback at the earliest quota/schedule boundary. Re-evaluate even when no accessibility event occurs, so a static page cannot outlive its allowance. No busy-loop polling or permanent wake lock. UI countdowns may refresh once per second only while visible; background status can update once per minute and at transitions.

### 4.4 Clock and timezone

Unlocked rules follow the device timezone. Starting Restricted Mode captures a timezone for that session. Daily/hourly boundaries use that timezone until release, preventing timezone switching from trivially refreshing quotas.

Session duration uses monotonic time within a boot. Persist a UTC deadline plus boot/checkpoint information for reboot recovery. Repeated local-time windows during DST must have unique bucket identities derived from their actual instant; skipped local hours receive no extra bucket.

Persist the greatest observed quota bucket to prevent a backwards wall-clock jump from replaying old allowances. A forward jump may create a later bucket; without a trusted external clock, time manipulation across reboot cannot be solved completely. Block normal date/time Settings access when protection is selected; document remaining limitations. Do not silently turn a timed session into an indefinite lock after a clock anomaly.

## 5. Website matching and browser behavior

Default website targets:

- youtube.com and its subdomains, youtu.be and its subdomains, youtube-nocookie.com and its subdomains.
- instagram.com and its subdomains.

Use parsed URI host comparisons with domain boundaries: youtube.com must not match notyoutube.com. Normalize host case and IDN representation; ignore fragments for matching. Handle browser-displayed URLs lacking a scheme. Host matching is the preferred editor.

Advanced regex matches a normalized visible URL using explicitly documented full-match semantics. Provide validation and a local match tester. Use a linear-time regex engine compatible with offline Android execution, with length limits and explicit rejection of unsupported syntax. Never run an unbounded Java regex on the accessibility callback thread. Query strings may participate in matching in memory but must not be logged.

Ship separate adapters for installed Chrome and Edge. Each result is KnownUrl, UnknownUrl, or NotBrowser. Cache a known URL only while the same page can be tracked; invalidate on navigation uncertainty so a prior safe URL cannot authorize a new unknown page. Never treat text typed into the address bar as a confirmed navigation until supported UI signals establish it.

Unknown URL behavior is fixed to allow with a coverage warning in the first release: deny only a confidently identified URL that matches an enabled website rule. A separate app blocker can still deny the browser by package or exhausted quota. Invalidate stale URLs immediately on navigation uncertainty. Bound adapter retries within a one-second observation grace and never spin continuously. Browser foreground time still counts against applicable app quotas even when its URL is unknown. Provide a local troubleshooting status and URL match tester so the user can improve rules without collecting browsing history.

Blocked website action remains Home redirection. Redirecting to a remote replacement such as https://github.com is deferred because directing the browser to fetch it would violate the intended no-network enforcement behavior. A future offline replacement page could be designed separately; do not automate address-bar editing in this release.

First release must test normal and private tabs, redirects, hidden address bars, app links, browser startup, and switching tabs. If an adapter cannot meet the agreed cases, label it unsupported and offer whole-browser blocking. Embedded browsers, alternate profiles, cloned apps, and unselected browsers do not inherit coverage automatically; list them in preflight guidance.

## 6. Restricted Mode

### 6.1 States

Unlocked and Locked are persisted configuration states. Healthy and Degraded are independent runtime health states. Expiry releases the configuration lock; it does not disable blockers.

Starting a session atomically stores its policy snapshot/revision, release condition, deadline when applicable, password-verifier reference, protection settings, and session ID. Do not display Locked before the durable transaction completes.

Release choices:

| Choice | Release condition |
| --- | --- |
| Password only | Correct trusted-person password |
| Timer only | Deadline reached |
| Password OR timer — proposed default | Either condition |


Offer durations such as one day, one week, and custom duration. Explicitly summarize the consequences before starting, especially no automatic release for Password only. Remember the last successful mode/duration/additions locally; every Start computes a new deadline from that moment and reuses the saved password.

Release occurs only when the selected condition is satisfied and returns to editable configuration. Password AND timer is removed from new-session choices following user review. Existing persisted AND sessions retain their original release condition; never weaken an active session during an upgrade. No automatic blocker disabling. With Password OR timer, a trusted person can release early; the user can then disable selected ordinary blockers and start a new locked session anytime. A timed temporary pause with automatic re-enablement is a separate future feature, not an implied first-release password action. Timer-only controls expose no password override. No “forgot password” bypass while locked. Lost-password guidance must explain that the selected release policy still applies; app data removal is outside the protection guarantee.

### 6.2 Protected mutations

While locked, deny disabling/deleting existing blockers, removing targets, loosening schedules or quotas, changing release conditions/password, reducing protection, resetting counters, importing configurations, restoring defaults, clearing diagnostics through a path that also clears state, or enabling bypass/debug actions. Enforce this in the domain mutation layer, not merely disabled buttons.

Separate harmless presentation preferences, such as theme, from policy settings. Export remains available but includes no password verifier, active session, usage counters, or logs. Avoid enabling external intents or components that mutate rules without the same checks.

### 6.3 Adding restrictions while locked

Make this an explicit pre-session option, enabled by proposed default. Support only operations that are structurally provable to strengthen policy: add a new enabled independent blocker, add targets to an existing blocker, or lower an existing quota allowance. Adding a target to a shared group preserves accumulated usage and can never reset the group.

Do not allow arbitrary regex/schedule editing, changing quota periods, disabled replacement rules, or other changes that require proving general policy equivalence. Newly added rules stay locked until release. Warn before saving: the addition cannot be undone during the session.

### 6.4 Password storage

A trusted person sets the local password while unlocked. Never store plaintext or reveal a saved password. Each input may temporarily show its current text through an explicit eye toggle; it starts masked and returns to masked when cleared or recreated. Use a versioned, salted slow password verifier with device-benchmarked work factor; store verifier data only in private storage. Use Android Keystore-backed protection where appropriate without claiming it resists rooted/ADB attacks.

Persist failed-attempt count and exponential retry delay, capped at 15 minutes; successful verification clears it. Throttling cannot delay timer-based automatic release. No secret values in diagnostics, exports, screenshots generated by the app, or source code.

## 7. Optional device protections

Settings protection options: off; sensitive screens only; whole Settings. Sensitive screens include this app's app-info/uninstall/force-stop/data-clear routes, Accessibility management, Device Admin management, battery/autostart controls, and date/time controls. Protection applies whenever its toggle is enabled, independently of Restricted Mode; the mode locks that toggle.

Allow explicit Wi-Fi/mobile-network exceptions only where the device adapter reliably identifies their screens. On uncertainty, sensitive-only protection follows its documented limitation; whole-Settings protection denies unidentified Settings screens. SystemUI quick toggles should remain usable unless a later explicit feature says otherwise. Never block the launcher, lockscreen, dialer/emergency UI, or FocusGate itself as an ordinary target.

Permission repair must not deadlock: offer an explicit repair action that grants a short, package/screen-scoped exception only to the missing required permission's setup flow. Never create a general Settings bypass or exempt app-info/data-clear routes. If the adapter cannot safely constrain the repair flow, require the configured password release or timed release and explain that limitation.

Recents protection has its own toggle and tested capability status. A detected Recents screen invokes Home, with throttling to avoid loops. It must not mistake normal launcher use for Recents. Enabling it does not magically protect swipe-away on unrecognized OEM surfaces.

Uninstall resistance separately requests optional Device Admin and explains its extra deactivation step and limitations. Do not automatically toggle it off on session expiry; the user changes it after unlock.

## 8. Permissions, preflight, and health

Required for enabled blockers: connected AccessibilityService with necessary event/window capabilities. Request notification permission when the user chooses notifications/countdown; it is not required for core Home redirection.

Usage Access is optional for diagnostics/reconciliation, not the primary quota clock or a universal required grant. Device Admin is optional for uninstall friction. Avoid broad installed-package visibility unless demonstrably needed; prefer querying launchable apps and known browser packages. Include a manual package-ID route for supported non-launchable targets.

Explain Xiaomi autostart and battery restrictions with device-tested instructions. Read only settings that have a public or validated observable signal; otherwise show “User confirmed” or “Unknown,” not a fabricated green status. Provide restricted-settings guidance if the sideload installation flow requires it. Never change system permissions silently.

Preflight before starting Restricted Mode:

1. Summarize enabled blockers, their schedules and quota reset behavior.
2. Verify required Accessibility connection, selected browser/protection capabilities, and release prerequisites.
3. Explain known degraded protections and unknown OEM setup status.
4. Refuse lock if core Accessibility is disconnected or required release credentials are absent. Optional capability gaps require explicit acknowledgement, not a false successful check.
5. Recommend a short test session before a long commitment and show how release will work.

Check health on app open, service connect/disconnect, foreground transitions when cheap, screen unlock, package replacement, and boot recovery. Add unique WorkManager periodic work, proposed interval 30 minutes, to refresh best-effort health while the OS permits. It is not a watchdog or quota enforcement timer.

Keep degraded status local and notify when permitted. With the process/service stopped, health warnings may also be delayed. Never claim the app can always detect its own suspension immediately.

Prefer the system-bound AccessibilityService without an extra always-on foreground service. Add one only if measured phone evidence justifies it; Android 14 type/permission and background-start rules must be satisfied. Do not misuse unrelated service types.

2026-10-03 implementation refinement: measured HyperOS `SwipeUpClean` termination justified foreground promotion of the existing Accessibility service, rather than adding another service or polling watchdog. Use a quiet ongoing notification and Android 14 `specialUse` declaration while enabled blockers or Settings/Recents protections need enforcement. Handle promotion refusal without crashing Accessibility. True Background autostart and No restrictions remain manual OEM setup; foreground priority is not a survival guarantee.

Sources: [PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest), [Android 14 foreground service requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required).

## 9. User interface

Native Kotlin UI, proposed Jetpack Compose with Material components. Lightweight layouts, system typography, light/dark themes, accessible text scaling, and no decorative image pipeline.

Bottom tabs:

- **Blockers:** enabled/disabled cards, next schedule transition, shared allowance remaining, add/edit flow, app picker, website editor, local rule tester.
- **Restricted Mode:** editable/locked status, health, release policy, remaining lock duration, start/release action, protections, and permitted restrictive additions.
- **More:** setup health, usage instructions, FAQ/introduction, import/export, privacy, diagnostics, app version, and donation information.

Blocker cards clearly distinguish disabled, inactive schedule, allowed with remaining quota, and currently denying. Missing/uninstalled apps remain visible as missing targets without losing policy; reinstalling the same package restores matching. Presets are offered for review and are not silently activated at first launch.

Show remaining shared time in the Blockers tab and optional ongoing notification. Optional small Accessibility overlay countdown while a monitored app is in use must be measured on the device; avoid an additional generic draw-over-apps grant where unnecessary. Separate visibility preferences for remaining usage time and remaining lock time. Hiding a countdown does not disable enforcement.

On denial, Home redirection is the primary action. An optional short overlay/toast shows the custom blocker message and reason. Rate-limit repeated messages and Home actions, avoid focus capture and sensitive text, and ensure the message does not trap the user on the launcher.

## 10. Initial presets

Offer these on onboarding and in the preset picker, with confirmation:

| Preset | Targets | Policy |
| --- | --- | --- |
| No YouTube or Instagram | com.google.android.youtube; com.instagram.android; YouTube/Instagram host rules | Always deny |
| Shared distraction budget | com.microsoft.emmx; com.android.chrome; org.telegram.messenger; ru.ozon.app.android | Shared 15 minutes per clock hour, every day |

The installed ru.ozon.fintech.finance package is Ozon Bank and is not included by default. Package IDs observed on this phone are fixtures, not assumptions for every user/device. App-picker labels identify the actual installed packages. Selecting browser websites does not consume extra group time in addition to the browser app.

## 11. Architecture and local storage

Keep the policy engine independent of Android UI/services. Proposed units:

| Unit | Responsibility | Inputs/outputs |
| --- | --- | --- |
| PolicyEngine | Schedule, target, overlap and quota decisions | Observation + policy + clock -> decision + next transition |
| UsageLedger | Foreground intervals, checkpoints, bucket identities | Session observations -> durable counters |
| RestrictedSessionController | Release checks and mutation authorization | Commands -> authorized state transitions |
| AccessibilityObserver | Narrow event observation, adapter routing | Android events -> normalized observations |
| Browser/Settings/Recents adapters | Device-specific interpretation | Relevant nodes/windows -> typed result or unknown |
| EnforcementController | Home action, feedback and debounce | Denial -> bounded UI action |
| ConfigurationRepository | Versioned atomic policy/session persistence | Transactional reads/writes |
| HealthMonitor | Grant/capability status and warnings | Public/device-tested signals -> status |
| ImportExport | Validated offline configuration interchange | Local document -> validated proposed configuration |

Use Room for structured policy, session and ledger transactions; small UI preferences can use DataStore. Persist policy revision with observations to prevent stale evaluation after edits. Serialize ledger/policy changes through one owner; database transactions guard lock-start and authorized mutation. Do not block Android accessibility callbacks with database IO or regex work.

Store minimal direct-boot session metadata separately if needed; credential-sensitive data remains credential-protected. The full policy is reloaded after unlock, and no quota/session reset occurs during reconciliation.

Only necessary receivers/components are exported, with appropriate platform restrictions. Release builds have no exported debug bypass. Disable app backup/device-transfer of policy, verifier, active session, counters, and diagnostics; configuration transfer is explicit through import/export.

Comments should explain policy invariants, Android lifecycle surprises, clock decisions, and adapter limitations in plain English. Prefer readable Kotlin over clever abstractions for a maintainer who is learning Kotlin.

## 12. Privacy, export/import, diagnostics, donations

No INTERNET or ACCESS_NETWORK_STATE permissions in the final merged manifest. No HTTP clients, remote fonts, analytics/crash SDKs, ads, telemetry, or automatic update checks. Android/platform activity outside FocusGate is not controlled by this promise. Development tooling may download dependencies; runtime app execution is offline.

Accessibility nodes can contain private data. Inspect only relevant package/window fields and known address-bar or Settings identifiers. Never collect page text, messages, screenshots, clipboard, or browsing history. Retain URL only transiently for matching; diagnostics use rule IDs and typed failure reasons rather than raw URL/domain/path/query data. Package diagnostics are opt-in and clearly disclosed.

Export/import uses Android Storage Access Framework and versioned JSON. Export rules, groups, schedules and presentation preferences only. Explain that selecting a cloud-backed document provider uses that other app/service; FocusGate makes no network requests. Recommend a local file location.

Import while unlocked offers replace or merge with preview. Generate/remap IDs transactionally for merges. Validate size, version, regex, targets, schedule ranges, quota relationships and referential integrity; reject unsupported future schemas without partial writes. Imports never restore usage budgets, passwords, active sessions, Device Admin, or OS grants. Imported protection preferences require capability review before activation.

Developer diagnostics are off by default. Use a private bounded ring buffer, proposed cap 1 MB, with timestamp, event category, rule ID, reason, state transition, grant status and timing. No credentials or raw node content. Local export is explicit and separate from configuration export. Locked sessions may allow read-only diagnostics but no simulated clock, quota reset, policy reset, or release bypass.

Donation panel is static offline information: optional maintainer-provided cryptocurrency network/address, copy action, and locally rendered QR. Create a clearly commented DonationConfig.kt with DONATION_NETWORK and DONATION_ADDRESS empty placeholder constants that the maintainer can find and replace later. No invented address, wallet/network request, balance lookup, embedded checkout, or donation-gated feature. If no verified address has been supplied, hide the payment controls and show only a short future-support note.

## 13. Introduction and user help content

Introduction: “FocusGate adds a pause between an impulse and another hour of scrolling. Choose your rules, test them, then lock them for a commitment you want to keep. Your settings stay on this phone.”

Usage guide must cover:

1. Grant Accessibility after reading what it observes and why.
2. Review presets, installed app targets, browser coverage and shared budget.
3. Test redirection and a short quota while configuration remains editable.
4. Set a trusted-person password and/or timer, review protections, and run preflight.
5. Start a short Restricted Mode trial, verify release, then choose a longer commitment.
6. Check health after restart/updates, and export a local configuration backup if desired.

FAQ answers must explicitly cover: why blockers still work after lock expiry; shared quota and clock-hour boundary behavior; forgotten password by release policy; browser unknown-URL fallback; private/embedded browser limitations; background YouTube/audio; battery/autostart guidance on Xiaomi; why Device Admin is not an uninstall guarantee; force-stop/permission loss; offline privacy; repairing grants; how to turn protections off and uninstall after release; why import cannot unlock/reset a session.

## 14. Acceptance and verification

### Pure policy and persistence tests

- Each enabled blocker enforces outside Restricted Mode; disabling it while unlocked stops its contribution.
- Any denying rule wins over an allowing quota; overlapping quotas count an interval once per group.
- Shared group: seven minutes Chrome + eight minutes Telegram exhausts the allowance for all four packages; Ozon Bank remains outside the preset.
- Boundary cases: hourly/daily reset, midnight, overnight windows, weekdays, date-range endpoints, timezone changes, DST and backward clock jumps. Continuous-session cap persists across group switches/hour boundaries/restarts; short absence does not reset it; a full uninterrupted configured break does.
- Static foreground content is redirected at quota exhaustion and schedule start without requiring a new UI event.
- Every release policy has truth-table tests; expiry unlocks configuration but leaves blockers enabled.
- Locked mutation tests cover UI and repository/domain entry points, imports, counters, protections, password and debug operations.
- Restrictive-only additions preserve counters; attempted weakening is rejected.
- Password delay persists through process restart; timer release remains independent.
- Transaction failures never leave a UI claiming an undurable locked state; migration preserves active sessions and counters.
- Malformed/oversized imports and unsupported regex are rejected atomically. Domain boundaries prevent false substring matches.

### Primary-phone acceptance matrix

Record Android/HyperOS version, browser versions, navigation mode, grants, battery/autostart setup and observed results. Use a short test session; do not create a week-long unreleasable test lock.

- YouTube/Instagram native apps and supported browser domains return Home; unrelated sites remain accessible when identifiable and otherwise allowed.
- Chrome/Edge normal/private mode, hidden address bar, navigation, redirects and tab changes meet documented coverage or expose explicit unsupported state. Unknown/unidentified URLs are allowed by website rules, stale known URLs are invalidated, and independent app/quota denials still apply.
- App switching does not multiply quota; screen-off/lock pauses accounting; quota does not reset after service/process restart.
- Measured engineering target: native app redirection within one second of an observable foreground event; supported known URL redirection within two seconds. Report measured distribution and any failures rather than claiming an Android-wide guarantee.
- Screen off/on, activity swipe-away, launcher restart, service reconnection, process death, reboot before/after first unlock, and app update preserve locked policy. Measure reconnection gaps separately from persistence.
- Force-stop and Accessibility revocation show the documented degradation after the app can run again; never report continuous protection during unavailable service time.
- Test sensitive Settings pages, whole-Settings mode, Wi-Fi/mobile exceptions, scoped repair flow, uninstall attempts, and Recents under gesture and button navigation. Unsupported protections remain labelled unavailable.
- Split-screen/PiP, incoming calls, emergency UI and notification shade do not cause launcher loops or unsafe obstruction.
- Compare idle and active-use battery/CPU over a documented baseline and monitored session. Require no persistent wake lock, no idle high-frequency loop, no ANR/crash, bounded adapter retries, and bounded logs. Quantitative battery claims require measurements.
- Verify final merged manifest has no network permissions, backups are excluded, and release/debug component exposure matches the spec. Inspect logs/exports for sensitive fields.

### Completion criteria

The first release is usable only when both target scenarios and all core lock/recovery tests pass on the Xiaomi. Settings/Recents/uninstall friction may ship with clear tested capability limits; failures cannot silently change the agreed unknown-URL behavior or weaken configuration locking. Deliver an APK, readable source/comments, local user guide, recorded phone results, known limitations, and GitHub history without secrets.

## 15. Review decisions

The user reviewed and accepted the design with the corrections recorded in section 1. Approved decisions: clock-hour budgets, Password OR timer default, allow unknown URLs, restrictive-only additions, editable presets, ordinary Device Admin/Settings friction, and FocusGate name. The user subsequently requested an optional continuous-session cap; include it in the first release with the proposed configurable defaults in section 4.3. Review the implementation plan before starting product code.

No donation address, signing secret, GitHub token, or private phone identifier belongs in this repository.

## Password lifecycle clarification (user follow-up)

Display explicit Password set / No password set status. Initial setup uses new password plus repeated confirmation. A separate Change password action requires current password, new password, and repeated confirmation. Verify current credentials inside the durable transaction, preserve the verifier on failure, apply persisted retry delays to incorrect current-password attempts, and refuse changes during active Restricted Mode. Release/expiry never deletes the password. A new session reuses it and starts the full selected duration afresh. Password-only start must not depend on text in an inactive duration field.

Offer Remove password while unlocked, requiring the current password and the same persisted retry throttle. Successful removal clears the verifier and retry state but preserves blockers, usage, protections, and remembered lock choices. Afterwards, and on first use, setup asks only for the new password and confirmation. Removal is refused during any active session. Explain before removal and beside release choices that Password only and Password OR timer cannot start without a saved password. Never silently convert OR to Timer only: the user may explicitly choose Timer only (no early password override), or set a new password. Add an independent show/hide eye control to every password input; never expose saved credentials through it or through ADB.
