# FocusGate: Android self-control app specification

**TL;DR:** FocusGate blocks selected apps and known websites on the phone. Enabled blockers work outside Restricted Mode when their schedules apply. Restricted Mode prevents weaker settings. The app stays offline. Presets are editable. This document defines requirements. Read [the roadmap](../../roadmap.md) and [phone acceptance](../../testing/phone-acceptance.md) for current status.

Status: the user accepted the design on 2026-10-03. Later corrections are included here. The user has approved development; do not repeat the original approval step.
Date: 2026-10-03.
Repository: https://github.com/pesterevnikita/app1

## 1. Purpose and confirmed requirements

FocusGate helps a willing user stop procrastinating in selected apps and websites. A blocked activity returns to the launcher. The app can show a short reason. The threat model is normal Android actions by a user who wants to procrastinate. It excludes determined attackers with physical access, ADB, root, bootloader access, or factory-reset capability.

The user confirmed these requirements:

- Use a normal Android app without device-owner setup.
- Use no VPN or app network activity. Keep enforcement, settings, and diagnostics local.
- Enforce enabled blockers even outside Restricted Mode.
- Use Restricted Mode to prevent weaker settings. It does not activate blockers.
- Offer an editable preset that always blocks native and web YouTube and Instagram.
- Offer one combined 15-minute allowance per clock hour for Edge, Chrome, Telegram, and Ozon shopping.
- Target Xiaomi 11T first: HyperOS 1.0.15.0 and Android 14.
- Provide a simple UI, bottom tabs, setup help, FAQ, import/export, optional local diagnostics, and future offline donation information.

Read-only ADB inspection during design confirmed Android 14 / API 34 and model 21081111RG. The user supplied the HyperOS version. Design work did not change phone settings or install an APK.

The user accepted the FocusGate name, clock-hour windows, Password OR timer release, and additions that only strengthen restrictions. Clock-hour windows can allow 30 consecutive minutes across two hours. Website rules deny only confidently identified matching URLs. Unknown URLs remain allowed. Presets are editable and deletable; they are never a hardcoded blacklist.

Optional Device Admin and sensitive Settings protection should provide normal uninstall friction similar to AppBlocker. Phone tests must establish the actual limits. Initial defaults are Android 10+ support and a fixed timezone during a locked session.

## 2. Scope and delivery

The first usable release must support both target scenarios. It must include configuration locking, setup checks, local storage, usage accounting, remaining-time display, import/export, and tested lifecycle recovery on the primary phone. Report device-specific protections accurately.

Use reviewable delivery steps:

1. Pure policy engine, storage, app blockers, basic UI, and unit tests.
2. Chrome/Edge website adapters, shared budgets, notifications, and quota-boundary tests.
3. Restricted Mode, passwords, setup checks, restart recovery, and clocks.
4. Xiaomi Settings/Recents experiments, optional Device Admin, health diagnostics, documentation, import/export, and phone tests.

Keep Settings/Recents detection separate from core policy. These are delivery steps, not a replacement for the implementation plan.

Exclude VPN, network inspection, remote administration, cloud sync, accounts, analytics, ads, root, device-owner enrollment, background screenshots, and content recording. Do not copy or decompile the dumped commercial APK. Build from public APIs and observed phone behavior.

## 3. Android capabilities and limits

### 3.1 Enforcement

An enabled `AccessibilityService` receives relevant window events and identifies the foreground package. On denial, it calls `GLOBAL_ACTION_HOME`. This changes the foreground UI. It does not terminate the other process or stop background playback, downloads, notifications, or network activity. The user may briefly see the blocked activity.

Browser adapters inspect known address-bar nodes in supported packages. Coverage can fail for private tabs, hidden address bars, embedded WebViews, or future browser versions. Never infer a URL from page body text. Website filtering observes the UI; it does not filter network traffic.

Source: [AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).

### 3.2 Uninstall resistance

Ordinary Device Admin does not grant device-owner powers. An active legacy administrator cannot use `setUninstallBlocked` merely because it has admin status. Admin activation may add a deactivation step on this phone. Settings protection may prevent normal navigation to that step while Accessibility runs. Test both effects.

Label the option **Uninstall resistance**. Explain its limits. Show Device Admin status separately from Settings protection status. Request ordinary Device Admin when the user enables it. On Xiaomi, test launcher long-press uninstall, Settings uninstall, and admin deactivation.

Android may keep or recreate the launcher icon after a refused uninstall. Do not simulate this by reinstalling FocusGate or adding duplicate shortcuts. Do not call privileged uninstall APIs or request unrelated wipe/password policies.

Source: [DevicePolicyManager.setUninstallBlocked](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setUninstallBlocked(android.content.ComponentName,%20java.lang.String,%20boolean)).

### 3.3 Stored state and service availability

Preserve locked sessions, rules, and quota records through screen off/on, activity dismissal, launcher restart, service restart, reboot, and ordinary process death. Opening the UI or reconnecting the service must not reset them.

Enforcement requires a running Accessibility service. Android or the manufacturer can stop it and cause a gap. A disabled service cannot enable itself. Periodic work cannot guarantee quick recovery. Force-stop, safe mode, data removal, uninstall, and permission revocation remain limits. Settings protection should resist observable normal routes to force-stop or revoke permissions. It cannot guarantee prevention.

After reboot, enforcement resumes when Android connects the enabled service and storage becomes available. A minimal direct-boot record can retain locked status before first unlock. Do not claim pre-unlock enforcement without phone evidence.

Sources: [Direct Boot](https://developer.android.com/privacy-and-security/direct-boot), [Android stopped-package behavior](https://developer.android.com/about/versions/android-3.1#launchcontrols).

### 3.4 Settings and Recents

Settings and Recents detection depends on device adapters. There is no universal API for these protections. Test HyperOS Settings, Security, app-info, permissions, launcher menus, gestures, and three-button navigation separately.

Describe Recents protection as returning Home after detection. Do not claim that it disables Recents.

If selective Settings detection is unreliable, offer whole-Settings blocking as an explicit choice. Never switch to it silently. Allow unknown screens in selective mode and report the coverage limit.

## 4. Policy model

Each blocker has an immutable ID, name, enabled flag, targets, schedule, optional quota, message, and revision. Targets use package IDs, website hosts, or advanced URL patterns. Labels and icons are display data. An app rename must not change matching.

### 4.1 Schedules

- Support always active, selected weekdays/time windows, date ranges, and combinations.
- Apply the date range AND the weekday/time conditions. Combine multiple time windows with OR.
- Include the start and exclude the end. An overnight window belongs to its starting weekday. Monday 22:00–02:00 includes Tuesday 00:00–02:00.
- Working hours use configured weekdays and times. Do not infer holidays. The editable weekend preset means Saturday/Sunday.
- A quota blocker allows access within its schedule until the allowance runs out. It imposes no restriction outside that schedule.
- Compile a “block outside allowed hours” preset into explicit blocking windows. Keep this distinct from a quota's active schedule.
- Show a plain-language preview and next transitions before saving.

### 4.2 Overlapping rules

Evaluate every applicable enabled blocker for the target. Deny access if any blocker denies it. A quota allowance never overrides another denial. Disabled blockers have no effect.

Charge foreground time to every applicable quota group. Charge once within each group, even if several targets match. Do not charge denied time.

Show all denying rules in the app. Choose the transient reason deterministically: unconditional denial first, then exhausted quota, then stable rule ID. Show the next possible availability time or **Blocked continuously**. Do not promise access if another rule will still deny it.

### 4.3 Shared quotas and continuous sessions

The default group contains Edge, Chrome, Telegram, and Ozon shopping. It has 15 combined minutes per clock hour, for example `[10:00, 11:00)`. Unused time does not carry over. Show the next reset time.

Support hourly and daily fixed windows with configurable allowances. Rolling windows are outside the first release. Label hourly quotas **per clock hour**. Without a continuous-session cap, 15 minutes before and 15 minutes after a boundary can allow 30 consecutive minutes.

Each group can have an optional maximum continuous session and a required break. Proposed editor values are a 15-minute cap and a five-minute break. Both are configurable. The cap stays off until selected.

- Accumulate interactive use across apps in the group.
- Keep session use across hour/day resets.
- Pause accumulation while away from the group.
- Reset the session only after one uninterrupted absence from all group targets for the full required break.
- Count screen-off and keyguard time toward the break.
- Cancel a partial break when allowed group use resumes early.
- After the cap is exhausted, deny access until the break completes, even if period allowance remains.
- Do not charge denied attempts or interrupt the break for them. The target was not allowed.
- Persist session use and the end of last allowed use. After reboot, restore absence conservatively from available clocks and explain clock limits.

Restricted Mode locks cap and break settings. Enabling or lowering a cap strengthens restrictions. Disabling or raising a cap, or shortening the break, weakens them. Do not hardcode a cap into presets.

Count interactive foreground use only when the screen is on and unlocked. Stop at screen-off, keyguard, foreground changes, denial, or unknown activity. Pause for notification shade and Recents when detected. Browser tab changes do not reset its budget. Version one does not count background audio.

In split-screen, count reliably observed interactive use. Charge a group once if several windows belong to it. Report and test unsupported split-screen/PiP cases.

Use monotonic elapsed time for consumption. Save counters on foreground/screen transitions, exhaustion, and at intervals no longer than five seconds during billable use. Resume from the checkpoint after process death. Limit unsaved usage loss to five seconds per ordinary death. Do not charge unobserved downtime. Document this recovery limit.

While a billable target stays active, schedule a lightweight callback at the earliest quota or schedule boundary. Re-evaluate even without a new Accessibility event. A static page must not exceed its allowance. Use no busy loop or permanent wake lock. Visible UI countdowns may update once per second. Background status may update once per minute and at transitions.

### 4.4 Clocks and timezones

Unlocked rules follow the device timezone. Capture the timezone when Restricted Mode starts. Use it for hourly/daily boundaries until release. A timezone change must not refresh the session's allowances.

Use monotonic time for a session duration within one boot. Save a UTC deadline and boot/checkpoint metadata for reboot recovery. Repeated DST windows need separate bucket IDs based on their actual instants. Skipped local hours give no extra bucket.

Save the greatest observed quota bucket. A backward clock change must not replay an old allowance. A forward change can reach a later bucket. Without a trusted external clock, the app cannot fully prevent time manipulation across reboot. Protect normal date/time Settings routes when selected. Explain remaining limits. A clock anomaly must not silently turn a timed lock into an indefinite lock.

## 5. Website matching and browsers

Default hosts, including subdomains:

- `youtube.com`, `youtu.be`, `youtube-nocookie.com`.
- `instagram.com`.

Parse URI hosts and enforce domain boundaries. `youtube.com` must not match `notyoutube.com`. Normalize host case and IDN representation. Ignore fragments. Accept displayed URLs without a scheme. Prefer host rules in the editor.

Advanced regex uses full-match semantics on a normalized visible URL. Document that behavior. Add validation and a local match tester. Use a linear-time regex engine that runs offline on Android. Limit pattern length and reject unsupported syntax. Do not run unbounded Java regex on the Accessibility callback thread. Query strings can be matched in memory but must not be logged.

Provide separate Chrome and Edge adapters. Results are `KnownUrl`, `UnknownUrl`, or `NotBrowser`. Cache a known URL only while the same page remains traceable. Clear it on navigation uncertainty. A previous safe URL must not authorize a new unknown page. Address-bar typing alone is not confirmed navigation; require supported UI signals.

Website rules allow unknown URLs in the first release. Report the coverage warning. Deny only a confidently identified URL that matches an enabled rule. Independent package/quota rules can still deny the browser. Browser foreground time still counts toward app quotas when the URL is unknown.

Clear stale URLs immediately. Limit adapter retries to a one-second observation grace. Do not spin continuously. Provide local adapter status and a match tester without collecting history.

Blocked websites return Home. Remote redirects, including `https://github.com`, are deferred because the browser would fetch network content. A future offline replacement page needs a separate design. Do not automate address-bar editing in this release.

Test normal/private tabs, redirects, hidden address bars, app links, startup, and tab switches. If an adapter cannot meet the agreed cases, label it unsupported and offer whole-browser blocking. Explain that embedded browsers, other profiles, cloned apps, and unselected browsers do not automatically receive coverage.

## 6. Restricted Mode

### 6.1 States and release

Persist `Unlocked` and `Locked` configuration states. Track `Healthy` and `Degraded` runtime health separately. Expiry unlocks settings; blockers stay enabled.

Starting a session must atomically store its policy snapshot/revision, release condition, deadline if needed, verifier reference, protections, and session ID. Show Locked only after the durable transaction succeeds.

| Choice | Release condition |
| --- | --- |
| Password only | Correct trusted-person password |
| Timer only | Deadline reached |
| Password OR timer: default | Either condition |

Offer one day, one week, and custom durations. Explain the consequences before start. Password only has no automatic release. Remember the last successful mode, duration, and additions choice locally. Each Start uses the saved password and computes a fresh full duration.

Release requires the selected condition. It makes settings editable. It does not disable blockers. New sessions cannot use Password AND timer. Honor the original release condition of existing AND sessions; an upgrade must not weaken them.

With OR, a trusted person can release early. The user can then disable ordinary blockers and start a new session. Automatic temporary pause/re-enable/relock is a future feature. Timer only has no password override. Do not provide a forgotten-password bypass while locked. Explain that the selected release policy still applies. App data removal remains outside the protection guarantee.

### 6.2 Protected changes

While locked, reject these changes in the domain transaction layer, not only in the UI:

- Disable/delete blockers or remove targets.
- Loosen schedules or quotas.
- Change release conditions or the password.
- Reduce protections or reset counters.
- Import configurations or restore defaults.
- Clear diagnostics through an action that also clears state.
- Enable debug/bypass actions.

Keep harmless display preferences, such as theme, separate from policy. Allow export without verifier, active session, counters, or logs. Apply the same guards to external intents and components that can change rules.

### 6.3 Add restrictions while locked

Offer an explicit option before start. The proposed default is on. Allow only structurally proven stronger changes: add a new enabled independent blocker, add targets to an existing blocker, or lower a quota allowance. Adding a shared-group target must preserve its usage.

Do not allow arbitrary regex/schedule changes, quota-period changes, disabled replacement rules, or changes that require proving general policy equivalence. Keep new rules locked until release. Warn that the user cannot undo an addition during the session.

### 6.4 Password storage

A trusted person sets the password while unlocked. Store no plaintext and never reveal the saved password. An eye control may show only the text currently entered into that field. Start masked. Return to masked when cleared or recreated.

Use a versioned, salted, slow verifier. Benchmark its work factor on the phone. Keep verifier data in private storage. Use Android Keystore protection where appropriate, without claiming protection against root/ADB attacks.

Persist failed attempts and an exponential retry delay capped at 15 minutes. Successful verification clears them. Retry throttling must not delay timer release. Do not put secrets in diagnostics, exports, app-generated screenshots, or source code.

## 7. Optional device protections

Settings choices are off, sensitive screens only, or whole Settings. Sensitive routes include FocusGate app-info/uninstall/force-stop/data-clear, Accessibility, Device Admin, battery/autostart, and date/time. Enforce a selected protection outside Restricted Mode too. Restricted Mode locks its toggle.

Allow explicit Wi-Fi/mobile-network exceptions only when the adapter reliably identifies them. Sensitive-only mode allows unknown screens. Whole-Settings mode denies unidentified Settings screens. Keep SystemUI quick toggles usable unless a later explicit feature changes this. Never use the launcher, lockscreen, dialer/emergency UI, or FocusGate itself as ordinary blocked targets.

Permission repair must not trap the user. Offer an explicit short exception scoped to the package and screen of a missing required grant. Do not provide a general Settings bypass or exempt app-info/data-clear. If the adapter cannot safely limit the route, require normal password/timer release and explain why.

Recents has its own toggle and tested capability status. On detection, return Home with throttling. Do not mistake normal launcher use for Recents. This cannot protect unknown manufacturer surfaces.

Uninstall resistance separately requests optional Device Admin. Explain the added deactivation step and its limits. Expiry must not switch it off automatically. The user changes it after release.

## 8. Permissions, setup checks, and health

Enabled blockers need a connected Accessibility service with the required event/window access. Request notification permission for chosen notifications/countdowns. Core Home redirection does not require it.

Usage Access is optional for diagnostics/reconciliation. It is not the primary usage clock or a universal required grant. Device Admin is optional. Query launchable apps and known browsers before requesting broad package visibility. Add manual package-ID entry for supported targets without launcher entries.

Explain Xiaomi autostart and battery settings using tested instructions. Read settings only through public or validated signals. Otherwise show **User confirmed** or **Unknown**. Do not show a false verified status. Explain restricted-settings steps when sideloading requires them. Never change system grants silently.

Before Restricted Mode starts:

1. Summarize enabled rules, schedules, and quota resets.
2. Check Accessibility, selected browser/protection capabilities, and release prerequisites.
3. Explain degraded protections and unknown manufacturer setup.
4. Refuse start if Accessibility is disconnected or required credentials are missing. Require explicit acknowledgement of optional capability gaps.
5. Recommend a short trial and explain release before a long lock.

Check health on app open, service connection changes, cheap foreground transitions, screen unlock, package replacement, and boot recovery. Use unique periodic WorkManager work, with a proposed 30-minute interval. It updates health when Android permits. It is not a watchdog or enforcement timer.

Keep degraded status local and notify when allowed. Health warnings can also be delayed while the process/service is stopped. Do not promise immediate detection of suspension.

Prefer the system-bound Accessibility service. Add foreground service priority only when measured phone evidence justifies it. Follow Android 14 service type, permission, and background-start rules. Do not misuse an unrelated type.

**2026-10-03 implementation refinement:** observed HyperOS `SwipeUpClean` termination justified promoting the existing Accessibility service. Use no extra service or polling watchdog. Use a quiet ongoing notification and Android 14 `specialUse` declaration while blockers or Settings/Recents protections need enforcement. A refused promotion must not crash Accessibility. Background autostart and No restrictions remain manual setup. Foreground priority is not a survival guarantee.

Sources: [PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest), [Android 14 foreground service requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required).

## 9. User interface

Use lightweight native Kotlin UI, initially proposed as Compose with Material components. Use system typography, light/dark themes, and accessible text scaling. Avoid a decorative image pipeline.

| Bottom tab | Content |
| --- | --- |
| Blockers | Rule cards, next transition, shared time remaining, add/edit, app picker, websites, local tester |
| Restricted Mode | Lock status, health, release choice, time remaining, start/release, protections, allowed additions |
| More | Setup, guide, FAQ/introduction, import/export, privacy, diagnostics, version, donation information |

Rule cards distinguish disabled, inactive schedule, allowed with quota, and denying. Keep missing app targets visible. Reinstalling the same package restores matching. Offer presets for review; do not silently enable them on first launch.

Show shared time remaining in Blockers and an optional notification. Measure any optional small Accessibility countdown overlay on the phone. Avoid an extra generic draw-over-apps grant when unnecessary. Keep usage-countdown and lock-countdown visibility separate. Hiding a countdown must not affect enforcement.

Return Home on denial. An optional short overlay/toast can show the custom message and reason. Limit repeated feedback and Home actions. Avoid focus capture and private text. Feedback must not trap the user on the launcher.

## 10. Initial presets

Offer presets during onboarding and in a picker. Require confirmation.

| Preset | Targets | Policy |
| --- | --- | --- |
| No YouTube or Instagram | `com.google.android.youtube`; `com.instagram.android`; YouTube/Instagram hosts | Always deny |
| Shared distraction budget | `com.microsoft.emmx`; `com.android.chrome`; `org.telegram.messenger`; `ru.ozon.app.android` | Shared 15 minutes per clock hour, every day |

Do not include Ozon Bank, `ru.ozon.fintech.finance`. Observed package IDs are fixtures, not universal assumptions. Show actual installed packages in the app picker. Matching browser websites must not charge extra time beyond the browser's group use.

## 11. Architecture and local storage

Keep the policy engine separate from Android UI/services. Proposed units:

| Unit | Responsibility | Inputs/outputs |
| --- | --- | --- |
| PolicyEngine | Schedules, targets, overlapping rules, quotas | Observation + policy + clock -> decision + next transition |
| UsageLedger | Foreground time, checkpoints, buckets | Observations -> stored counters |
| RestrictedSessionController | Release and change guards | Commands -> allowed state changes |
| AccessibilityObserver | Narrow Android observations | Events -> normalized observations |
| Browser/Settings/Recents adapters | Device-specific detection | Relevant nodes/windows -> result or unknown |
| EnforcementController | Home, feedback, debounce | Denial -> bounded UI action |
| ConfigurationRepository | Atomic versioned storage | Transactional reads/writes |
| HealthMonitor | Grants, capabilities, warnings | Public/tested signals -> status |
| ImportExport | Validated local configuration transfer | Local file -> proposed configuration |

Use Room for atomic policy, session, and ledger storage. The original design allowed DataStore for small UI preferences. Current implementation stores preferences in the same Room document; see development context. This is not an instruction to split storage.

Associate policy revisions with observations to avoid stale decisions. Serialize ledger/policy changes through one owner. Use transactions for lock start and guarded changes. Keep database IO and regex work off Accessibility callbacks.

Keep only minimal direct-boot metadata outside credential-protected storage. Reload full policy after unlock without resetting quota or session.

Export only required components and apply platform restrictions. Release builds must have no exported bypass. Disable backup/device-transfer of policy, verifier, session, counters, and diagnostics. Configuration transfer must be explicit.

Comments should explain policy rules, lifecycle surprises, clocks, and adapter limits in plain English. Favor readable Kotlin over clever abstractions for the maintainer.

## 12. Privacy, transfer, diagnostics, and donations

The merged manifest must contain no `INTERNET` or `ACCESS_NETWORK_STATE`. Use no HTTP clients, remote fonts, analytics/crash SDKs, ads, telemetry, or update checks. Other Android apps can still use networks. Development tools may download dependencies; the running app stays offline.

Accessibility nodes can contain private data. Inspect only needed package/window fields and known address-bar/Settings identifiers. Collect no page text, messages, screenshots, clipboard, or history. Keep URLs only transiently for matching. Diagnostics use rule IDs and typed reasons, never raw URL/domain/path/query data. Package diagnostics are opt-in with clear disclosure.

Use Android Storage Access Framework and versioned JSON for transfer. Export rules, groups, schedules, and display preferences only. Explain that choosing a cloud document provider may use that other app's network. Recommend a local file.

While unlocked, offer replace or merge with preview. Generate/remap merge IDs in a transaction. Validate size, version, regex, targets, schedules, quotas, and references. Reject future schemas and invalid input without partial writes. Never import usage budgets, passwords, sessions, Device Admin grants, or OS grants. Review imported protection preferences before activation.

Diagnostics start off. Use a private bounded ring buffer with a proposed 1 MB cap. Allowed fields are timestamp, category, rule ID, reason, state transition, grant status, and timing. Include no credentials or raw node content. Export diagnostics explicitly, separately from configuration. Locked sessions may allow read-only diagnostics. They must not allow simulated clocks, quota/policy reset, or release bypass.

Keep donations static and offline. A supplied cryptocurrency network/address may support copying and a locally generated QR. Put clearly commented empty `DONATION_NETWORK` and `DONATION_ADDRESS` constants in `DonationConfig.kt`. Invent no address. Add no wallet/network request, balance check, checkout, or donation-gated feature. Without a verified address, hide payment controls and show a future-support note.

Show the public GitHub repository link in More with **Copy repository link**. Add a clearly named empty Telegram feedback-link placeholder for the maintainer. Show **Coming soon** while it is empty. If configured later, display the link and allow copying it. Do not invent a contact address. These actions copy only; FocusGate must not fetch links or open another app. The user can paste a copied link elsewhere. That other app may use the network.

## 13. Introduction and help

Suggested introduction: “FocusGate adds a pause before more scrolling. Choose your rules, test them, then lock them for the time you choose. Your settings stay on this phone.”

The guide must cover:

1. Read the disclosure and grant Accessibility.
2. Review presets, apps, browser coverage, and the shared budget.
3. Test redirection and a short quota while settings stay editable.
4. Set a trusted-person password and/or timer, review protections, and run setup checks.
5. Try a short lock and its release before a long commitment.
6. Check health after restarts/updates and optionally export a local backup.

FAQ must explain blockers after expiry, shared quotas, clock-hour boundaries, forgotten passwords by release policy, unknown URLs, private/embedded browsers, background YouTube/audio, Xiaomi battery/autostart, Device Admin limits, force-stop/permission loss, offline privacy, grant repair, release before uninstall, and why import cannot unlock/reset a session.

## 14. Acceptance and verification

These items define required test coverage. They do not claim that tests passed. Record actual results in testing documents.

### Policy and storage tests

- Enabled rules enforce outside Restricted Mode. Disabling an unlocked rule removes its effect.
- Denial wins over quota allowance. Charge overlapping matches once per group.
- Seven minutes of Chrome plus eight minutes of Telegram exhaust all four group targets. Exclude Ozon Bank.
- Test hourly/daily reset, midnight, overnight windows, weekdays, date-range endpoints, timezone changes, DST, and backward clock changes.
- Keep continuous-session use across app switches, hour boundaries, and restarts. Short absence does not reset it. A full uninterrupted break does.
- Redirect static content at quota exhaustion and schedule start without a new UI event.
- Test release truth tables, including legacy AND. Reject new AND sessions. Expiry keeps blockers enabled.
- Test locked changes through UI, repository/domain, import, counters, protections, password, and debug paths.
- Additions preserve counters. Reject weaker settings.
- Keep password delays across restart. Timer release stays independent.
- Failed transactions must not show an unsaved Locked state. Migrations keep sessions and counters.
- Reject malformed/oversized imports and unsupported regex atomically. Test host boundaries.

### Primary-phone matrix

Record Android/HyperOS, browser versions, navigation mode, grants, battery/autostart setup, and results. Use short test sessions. Do not create an unreleasable week-long test lock.

- Native YouTube/Instagram and supported matching websites return Home. Identified unrelated sites remain allowed; unknown sites also remain allowed by website rules.
- Test Chrome/Edge normal/private tabs, hidden address bars, navigation, redirects, startup, and tab changes. Report unsupported cases. Clear stale URLs. Keep independent app/quota denials.
- Switching apps must not multiply usage. Screen-off/lock pauses it. Service/process restart must not reset quotas.
- Measure native redirection against a one-second target after an observable event. Measure known-URL redirection against a two-second target. Report distributions and failures; these are not Android-wide guarantees.
- Test screen off/on, swipe-away, launcher restart, service reconnection, process death, reboot before/after first unlock, and app update. Preserve locked policy. Measure connection gaps separately.
- After force-stop or Accessibility revocation, report degradation once the app can run. Do not claim protection while the service was absent.
- Test sensitive/whole Settings, Wi-Fi/mobile exceptions, scoped repair, uninstall attempts, and gesture/button Recents. Label unsupported protection unavailable.
- Test split-screen/PiP, incoming calls, emergency UI, and notification shade for unsafe blocking or launcher loops.
- Compare idle/active CPU and battery with a documented baseline. Require no permanent wake lock, idle high-frequency loop, ANR/crash, unbounded retries, or unbounded logs. Measure before making battery claims.
- Check the merged manifest, backup exclusions, release/debug exports, logs, and exported files for privacy compliance.

### Completion criteria

The first release is usable when both target scenarios and core lock/recovery phone tests pass on Xiaomi. Optional Settings/Recents/uninstall friction may ship with tested limits. Failures must not change unknown-URL policy or weaken locking.

Deliver an APK, readable source/comments, local guide, phone results, known limits, and GitHub history without secrets.

## 15. Approved decisions and follow-ups

The user accepted clock-hour quotas, OR by default, unknown URLs allowed, restrictive additions, editable presets, ordinary Device Admin/Settings friction, and the FocusGate name. The optional configurable continuous-session cap is in first-release scope. Keep the proposed values in section 4.3.

The user already approved implementation. Follow the current roadmap; do not repeat the original design/plan approval step. Keep donation addresses, signing secrets, GitHub tokens, and private phone identifiers out of the repository.

### Password lifecycle clarification

Show **Password set** or **No password set**. First setup asks for a new password and confirmation. Change password asks for current, new, and confirmation. Verify the current password inside the durable transaction. On failure, keep the verifier and persist retry delays. Refuse changes during Restricted Mode.

Release/expiry keeps the password. Each new session reuses it and starts a fresh full duration. Password-only start must not depend on an inactive duration input.

Offer **Remove password** only while unlocked. Require the current password and shared retry delay. Successful removal clears verifier and retry state. Preserve blockers, usage, protections, and remembered lock choices. First setup and setup after removal ask no old password.

Reject removal during every active session. Explain before removal and beside release choices that Password only and Password OR timer need a saved password. Never silently convert OR to Timer only. The user can explicitly choose Timer only, which has no early password override, or set a new password.

Every password input has an independent eye control. It can reveal current input only. ADB must not expose saved credentials.
