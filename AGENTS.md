# FocusGate development instructions

Read [docs/development.md](docs/development.md) before changing code. It contains architecture, reproducible local build commands, device workflow, and recovery context. Product decisions live in the [accepted design](docs/superpowers/specs/2026-10-03-focusgate-design.md); the [implementation plan](docs/superpowers/plans/2026-10-03-focusgate.md) describes the original delivery plan. User instructions and verified device evidence take precedence over stale documentation.

## Product invariants

- This is a normal offline Android self-control app, not a device-owner or adversarial security product. Do not introduce VPN, runtime networking, telemetry, cloud backup, root, or ADB-dependent enforcement.
- Enabled blockers enforce even outside Restricted Mode. Restricted Mode prevents weakening configuration; it does not turn blockers on. Presets are ordinary editable rules, never a mandatory blacklist.
- The default allowance is **combined** across Chrome, Edge, Telegram, and Ozon shopping: 15 minutes per clock hour. Clock-hour boundary bursts are accepted. Optional continuous-session limits and breaks are separately configurable.
- Evaluate overlapping applicable rules with denial taking precedence. Bill overlapping shared groups once. Unknown browser URLs are allowed unless an independent app rule denies access.
- The release-retained ADB maintenance provider is explicitly approved. Keep it shell-only (UID 2000 plus caller DUMP permission), offline, and subject to normal transactional guards; expose no password, unlock, grant or reset endpoint. Read docs/adb-control.md before using it.
- Preserve atomic policy/session/usage persistence and restrictive-only mutation checks. New locks offer Password only, Timer only and Password OR timer. Retain AND decoding/release only for existing legacy sessions; reject creating new AND locks. Password changes require current/new/repeated-new input when a verifier already exists. Preserve saved credentials and remembered lock choices across release/relock.
- Ordinary Device Admin is optional friction. Never claim it grants device-owner uninstall prevention or guarantees service availability.

## Engineering workflow

- Investigate failures from evidence before editing. Add a meaningful regression for a reproduced defect when practical; run relevant unit tests and Android lint/build after source changes. Device acceptance is separate evidence from JVM tests.
- Explain public functions and non-obvious internal behavior with concise KDoc/comments, especially persistence, coroutine ownership, accounting clocks, and security boundaries. Explain why and invariants; avoid comments that repeat the code. The maintainer is not a Kotlin specialist.
- Keep unrelated changes out of a fix. Preserve enabled rules, credentials, quotas, and Restricted Mode across upgrades; never wipe app data to make a test pass without explicit authorization.
- Update `docs/testing/phone-acceptance.md` for measured phone results and `docs/testing/known-limitations.md` for unresolved limitations. Record commands/results and distinguish user reports from automated observations. Do not turn design intentions into verified claims.
- Keep documentation current enough for a fresh agent to resume. Record current work, reproductions, remaining checks, and significant implementation decisions in tracked development/testing docs. Ignored scratch logs are optional evidence, not the only handoff.
- Before claiming completion, inspect actual test/build outputs and the final diff. Do not broaden tests repeatedly without a change or unresolved concern.

## Phone and privacy

- Device testing and reversible debug installation are authorized. Accessibility, Device Admin, and OEM permission/battery settings are granted manually by the user. Do not silently change secure settings or grant permissions through ADB.
- Coordinate disruptive device operations such as reboot, and use short releasable Restricted Mode sessions. Never leave an unknown long-lived lock or deliberately disable the user's ability to repair the device.
- Inspect only the information needed for the test. Avoid collecting arbitrary screen content, messages, browser history, raw URLs, secrets, or device serials. Diagnostic records use bounded typed categories and timestamps.
- Do not use stock `adb shell uiautomator dump` to measure Accessibility enforcement: UiAutomation suppresses Accessibility services by default. Use `dumpsys`, app-only screenshots/manual checks, or a test automation connection with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`. Instrumentation also force-stops the target process; verify recovery afterward. Coordinate screen control rather than blindly tapping while the user may be interacting.
- Never commit tokens, credentials, phone identifiers, private logs/screenshots, APKs, signing keys, `local.properties`, or proxy credentials. Documented local build paths are setup context, not portable configuration. An earlier pasted GitHub token is exposed and must not be reused or reproduced. Use normal credential-manager authentication for publishing.

## Current issue to resume

The Xiaomi swipe-away failure was traced to a HyperOS process kill: Android exit information reports `SwipeUpClean`, with no observed AndroidRuntime crash. True Background autostart was initially off even after the separate Other permissions setup. The user manually enabled the actual Background autostart checkbox; on a subsequent swipe, Android automatically reconnected Accessibility in a new process and YouTube returned Home without a manual regrant. A short enforcement gap remains possible.

The updated build promotes the existing Accessibility service to `specialUse` foreground priority while enabled blockers or Settings/Recents protections need enforcement, without adding polling or a wake lock. Repeated verified Recents-card removal retained the same process and connected service; YouTube was redirected Home. An additional check avoided UiAutomation interference and passed. `onInterrupt` now preserves enforcement; its regression and independent-protection eligibility tests passed. Full screen-off/reboot/force-stop resilience remains unverified; see current acceptance results before expanding claims.
