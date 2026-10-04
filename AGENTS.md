# FocusGate development instructions

**TL;DR:** Keep the app offline. Preserve rules, passwords, usage and active locks. Read the roadmap before work. Use measured evidence for completion claims. You may correct these instructions within the user's rules; review each change yourself.

Read [development context](docs/development.md) before changing code. Read the [accepted design](docs/superpowers/specs/2026-10-03-focusgate-design.md) for product rules. The [original plan](docs/superpowers/plans/2026-10-03-focusgate.md) records earlier intentions. User instructions and verified device evidence take precedence over stale documentation.

## Product rules

- Build a normal offline Android self-control app. Do not add device-owner management, VPN, runtime networking, telemetry, cloud backup, root, or ADB-dependent enforcement. Do not claim protection against an expert attacker.
- Enabled blockers work outside Restricted Mode. Restricted Mode prevents weaker configuration. It does not activate blockers. Presets are editable rules. They are not a mandatory blacklist.
- Chrome, Edge, Telegram and Ozon shopping share **15 minutes per clock hour** by default. Access can span a clock-hour boundary. Optional continuous-session limits and breaks have separate settings.
- Evaluate all applicable rules. A denial wins. Count an interval once within each shared group. Allow unknown browser URLs unless an independent app rule denies access.
- Keep the approved ADB maintenance provider in release builds. Require shell UID 2000 and caller DUMP permission. Keep it offline. Apply normal transactional guards. Expose no password, unlock, grant or reset endpoint. Read [the ADB guide](docs/adb-control.md) before use.
- Save policy, session and usage changes atomically. Reject weaker changes during a lock. Allow only provably restrictive additions.
- Offer Password only, Timer only and Password OR timer for new locks. Decode and honor existing legacy AND sessions. Reject new AND locks. Preserve saved credentials and remembered lock choices after release and relock.
- When a password verifier exists, a password change requires current, new and repeated-new inputs. Password removal requires the current password and an unlocked configuration. Use the same persisted retry delay for these checks. Never allow password change or removal during Restricted Mode.
- Without a verifier, setup requires only new and repeated-new inputs. Password-only and OR starts are unavailable. Timer-only remains available. Never silently convert OR to Timer-only. The eye control must never expose a saved password.
- Device Admin is optional friction. It does not provide device-owner uninstall prevention or guaranteed service availability.

## Engineering workflow

- Investigate a failure before editing. Use evidence to find its cause. Add a useful regression test when practical. After source changes, run relevant unit tests and Android lint/build checks. JVM tests do not replace device acceptance.
- Add concise KDoc for public functions. Comment on non-obvious behavior, persistence, coroutine ownership, accounting clocks and security boundaries. Explain reasons and invariants. Do not repeat code in comments. The maintainer is not a Kotlin specialist.
- Keep unrelated changes out of fixes. Preserve rules, credentials, quotas and Restricted Mode across upgrades. Do not wipe app data to pass a test without explicit authorization.
- Record measured results in [phone acceptance](docs/testing/phone-acceptance.md). Record unresolved gaps in [known limitations](docs/testing/known-limitations.md). Include commands and results. Separate user reports from automated observations. Do not present a design intention as a verified result.
- Agents may edit this file to correct verified errors or add user-endorsed workflow and product rules. No fresh permission is needed for those edits. Preserve user constraints and authorization boundaries. Do not weaken them. Self-review the final diff for accuracy, stable scope and unintended permission changes.
- Keep temporary issues and current tasks in the [roadmap](docs/roadmap.md) and development/testing records. Keep this file focused on stable instructions.
- Keep tracked handoff records current. Include reproductions, remaining checks and important implementation decisions. Ignored scratch logs can support evidence. They must not be the only handoff record.
- Use the roadmap as the current task and completion tracker. Update it after material changes or measured acceptance results. Original plan checkboxes are historical intentions.
- Before a completion claim, inspect test/build outputs and the final diff. Repeat or expand checks only after a change, failure or unresolved concern.

## Phone and privacy

- Device testing and reversible debug installation are authorized. The user grants Accessibility, Device Admin and OEM permission/battery settings manually. Do not change secure settings silently. Do not grant permissions through ADB.
- Coordinate disruptive actions such as reboot. Use short lock sessions with a known release path. Do not leave an unknown long lock or prevent device repair.
- Inspect only data needed for the test. Do not collect arbitrary screen content, messages, browser history, raw URLs, secrets or device serials. Diagnostics use bounded typed categories and timestamps.
- Do not use stock `adb shell uiautomator dump` to measure Accessibility enforcement. UiAutomation suppresses Accessibility services by default. Use `dumpsys`, app-only screenshots, manual checks, or a connection with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`.
- Instrumentation force-stops the target process. Verify recovery afterward. Coordinate screen control with the user. Do not tap blindly during shared device use.
- Never commit tokens, credentials, phone identifiers, private logs/screenshots, APKs, signing keys, `local.properties` or proxy credentials. Keep personal paths and organization-specific proxy setup in local untracked notes.
- A GitHub token pasted earlier is exposed. Do not reuse or reproduce it. Use normal credential-manager authentication for publishing.
- Use the repository's public GitHub no-reply identity for commits. Do not publish organization email addresses. Keep credentials separate from commit identity.
