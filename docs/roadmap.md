# FocusGate roadmap

**TL;DR:** Core features are implemented. Short lifecycle acceptance (R2) passed; first-release phone acceptance is incomplete. R1 and the remaining R3 cases are next. Keep password removal unavailable during Restricted Mode. Check off work only when evidence meets its exit conditions.

Last reconciled: 2026-10-04. This file is the current work tracker. Original plans record earlier intentions. Their checkboxes do not show current completion.

## Current position

2026-10-04 follow-up: support UI shows the public GitHub URL and copy action. Telegram feedback has an empty maintainer placeholder. All project docs use short technical English and a user TL;DR. AGENTS.md contains stable rules; current work belongs here. The user-approved privacy cleanup is published on both branches. See [published-data review](testing/privacy-review.md) for scope and verification.

The core app is installed on Xiaomi 11T, Android 14, HyperOS 1.0.15.0. It supports development and personal testing. First-release acceptance is incomplete. The 2026-10-04 installed build includes password controls and support links. Check Git history for the latest source commit.

Latest full verification: **69 unit tests passed**. One earlier Room test passed on the phone. Debug and unsigned release APKs built. Lint reported zero errors and five warnings. Unit tests do not prove Android/OEM lifecycle behavior. See [phone acceptance](testing/phone-acceptance.md) for evidence and [known limitations](testing/known-limitations.md) for gaps.

The user requires password removal to remain unavailable during Restricted Mode. Unlock normally first. Then remove it with the current password. Do not add an unlock-and-remove shortcut. The user reports that the password controls and revised UI work. The release-policy phone matrix is still incomplete.

## Completed implementation

Checked items show implemented features. The evidence below limits those claims. A checked feature does not mean all related phone cases passed.

- [x] Offline Android app with Blockers, Restricted Mode and More tabs; no runtime network/VPN/telemetry permissions.
- [x] Editable app/site blockers, installed-app picker, domain/regex matching, days/time windows/date ranges, and denial taking precedence when rules overlap.
- [x] Ordinary editable presets: YouTube/Instagram app + web blocking, and one combined 15-minute clock-hour allowance for Chrome/Edge/Telegram/Ozon shopping.
- [x] Shared quotas, hourly/daily periods, optional maximum continuous session and uninterrupted break; remaining time on blocker cards and optional notification.
- [x] Accessibility Home redirection, known Chrome/Edge address-bar adapters, unknown-URL allowance, and optional custom blocking explanation.
- [x] Atomic local persistence for blockers, usage and Restricted Mode; stale-change guards and restrictive-only additions while locked.
- [x] Password-only, Timer-only and Password OR timer locks; fresh deadlines on relock; saved password and remembered lock choices retained. Legacy AND sessions are decoded but new AND locks cannot be created.
- [x] Password setup/change/removal with confirmation/current-password checks as appropriate, shared retry throttling, and per-field eye controls. No password change/removal during an active lock.
- [x] Foreground priority for the Accessibility service, boot reconciliation, periodic best-effort health work, setup links and Xiaomi instructions.
- [x] Optional Device Admin, Settings and Recents controls. Their device effectiveness is still unverified and the Settings/Recents adapters are experimental.
- [x] Local configuration export/import with merge/replace and validation; credentials, active sessions and usage excluded from exports. Local diagnostics and website match tester.
- [x] Release-retained, shell-only ADB maintenance and profile import, with normal mutation guards and no password/release/reset endpoint.
- [x] Grouped settings UI, user guide/FAQ, developer comments and handoff docs; blank donation placeholders for later maintainer configuration.
- [x] Debug installation and GitHub history through normal credential-manager authentication.

## Phone evidence already collected

| Area | Current evidence |
| --- | --- |
| Native YouTube/Instagram | Home redirection observed; editable disabling/reenabling observed |
| Shared allowance | Short combined Chrome/Edge allowance exhausted and redirected Home |
| Continuous session | Cap enforced across an hour boundary; full-break recovery and countdown observed. A later partial break retained usage; a full uninterrupted break reset it |
| Websites | Initial Chrome YouTube and Edge Instagram cases passed; broader coverage pending |
| Swipe away from Recents | Repeated actual card removals retained connected foreground enforcement after Xiaomi setup |
| Persistence/update | Room reopen/stale-write test passed; active TIMER deadline/counters survived process death, launcher restart, reboot and same-debug-APK replacement. Post-unlock enforcement and service reconnection observed |
| Timer lock | Short lock refused weakening/import/replacement; expiry unlocked configuration while blockers stayed enabled |
| Profile import | Replacement round trip preserved remaining usage; locked import refused |
| Password/UI | User reports controls work; eye masking/reveal/reopen checked with disposable input; individual private steps were not instrumented |
| Schedule | A temporary one-minute Chrome window activated on a static blank page, denied access during the window, and allowed access after expiry. Full R3 coverage remains open |
| Support links | Updated APK shows the repository and Telegram placeholder; repository copy action showed confirmation |

## Remaining work, in recommended order

### 1. Finish core lock and recovery acceptance

- [ ] **R1 - Release/relock matrix:** Record Password-only release, early OR password release and OR expiry. Check a fresh deadline and retained password after relock. Check wrong-password retry and locked password-removal refusal. Use short sessions. The user enters private passwords.
- [x] **R2 - Short lifecycle acceptance:** Screen off/on and manual unlock, screen-off billing, idle/active-billing process death, service recovery, launcher restart, reboot and same-debug-APK replacement passed. Short TIMER locks retained deadlines/counters, and blockers resumed without app relaunch or a post-reboot grant repair. Explicit grant off/on also reconnected, outside a lock. Recovery timing is bounded by polling; exact enforcement gaps and different-version upgrades remain unmeasured. Long idle/overnight acceptance remains in R12. See [phone evidence](testing/phone-acceptance.md#2026-10-04-recovery-checks).
- [ ] **R3 - Quota/schedule device cases:** Check interrupted breaks, app switching, daily reset and schedule start/end while an app stays open. Check overlapping blockers and counters after restart. Restore the 15-minute preset after temporary tests.

### 2. Verify optional device protections and browser coverage

- [ ] **R4 - Device Admin/uninstall friction:** The user grants admin manually. Test launcher uninstall and admin deactivation. Record actual HyperOS behavior. Do not claim device-owner uninstall prevention.
- [ ] **R5 - Settings/Recents:** Test app-info, Accessibility and admin routes. Test whole-Settings mode, Wi-Fi/mobile exceptions, Recents, release and repair. Record navigation mode. Fix supported routes. Label unsupported routes.
- [ ] **R6 - Browser matrix:** Record Chrome/Edge versions. Test address changes, redirects, tabs, hidden bars, private tabs and unknown URLs. Combine website rules with app quotas. Measure delay. Keep unknown URLs allowed.
- [ ] **R7 - System interaction:** Test calls, dialer, emergency UI, lock screen, notification shade, split-screen and picture-in-picture. Check for launcher loops or unsafe obstruction. Do not make an actual emergency call.

### 3. Close the remaining product gaps

- [ ] **R8 - Setup/health guidance:** Review preflight for each optional protection. Make degraded status actionable. Current periodic work reconciles state and can log disconnection. It is not a complete repair flow. Report grant failures honestly. OEM autostart/battery switches lack reliable automatic verification. Use unlock-first repair if a narrow Settings exception is unsafe or unsupported.
- [ ] **R9 - Editor/status clarity:** Add a plain-language schedule preview, next transition and missing-app status. Review validation, large text, keyboard use and light/dark layouts. Grouped screens and clearer allowance wording are done.
- [ ] **R10 - Remaining design reconciliation:** Decide and document the optional floating countdown and separate usage/lock countdown visibility. Cards and notifications already show remaining usage. Do not mark missing features complete.
- [ ] **R11 - Backup/help walkthrough:** Test merge and replace through the file picker. Test malformed-file recovery. Review the guide/FAQ as a new user. Automated validation and ADB replacement already pass.

### 4. Prepare a first release

- [ ] **R12 - Performance:** Measure password derivation, idle/active CPU, battery, redirection delay and overnight availability on Xiaomi. Investigate measured regressions.
- [ ] **R13 - Final review:** Resolve or document five lint warnings. Review merged manifest, components and backup rules. Check exports/logs for private data. Run relevant final tests, lint and builds. Account for Accessibility interruption before instrumentation on the daily-use app.
- [ ] **R14 - Packaging:** Agree signing and distribution with the maintainer. Keep signing secrets outside Git. Set a release version. Document debug-to-release installation and data handling. Produce a reproducible signed APK. The current release APK is unsigned.
- [ ] **R15 - Release acceptance:** Finish essential phone checks. Record unsupported optional capabilities. Update the guide, limitations and release notes. Obtain user acceptance before calling the first release finished.

## Later work and excluded scope

- Rolling-hour budgets; current clock-hour behavior is intentional, with an optional continuous-session cap.
- Temporary password-authorized pause with automatic relock/re-enabling.
- More browser adapters, based on real device/version evidence.
- Donation address/network and optional QR after the maintainer supplies an address; placeholders are sufficient now.
- Website redirection to a replacement page remains deferred; current behavior returns Home and FocusGate remains offline.

Exclude device-owner management and VPN/network inspection. Do not promise protection against an expert with ADB, root or factory-reset control.

## Where each document fits

| Document | Purpose |
| --- | --- |
| [This roadmap](roadmap.md) | What is done, next, and deferred |
| [Accepted design](superpowers/specs/2026-10-03-focusgate-design.md) | Product behavior and agreed constraints |
| [Original implementation plan](superpowers/plans/2026-10-03-focusgate.md) | Historical implementation steps and test intentions |
| [Phone acceptance](testing/phone-acceptance.md) | Measured results and remaining device checks |
| [Known limitations](testing/known-limitations.md) | Current gaps and unsupported guarantees |
| [Published-data review](testing/privacy-review.md) | Public-data audit and history-cleanup status |
| [Development context](development.md) and [AGENTS.md](../AGENTS.md) | Architecture, build/recovery workflow and instructions for future agents |
| [User guide](user-guide.md) and [ADB guide](adb-control.md) | How to use the app and its maintenance controls |

Update this file when scope or completion changes. Check a remaining task only when evidence meets its exit conditions. Link the evidence. Keep detailed results in the acceptance record. Do not assign a completion percentage or delivery date before measuring the remaining phone work.
