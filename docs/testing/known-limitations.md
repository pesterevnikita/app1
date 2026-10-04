# Development build limitations

**TL;DR:** Phone acceptance is partial. Native blockers, repeated swipe-away and short restart/reboot recovery passed on Xiaomi 11T. Long idle, browser edge cases, Device Admin, Settings/Recents and full password release tests remain open. See the [roadmap](../roadmap.md) and [phone evidence](phone-acceptance.md). This is a development build, not a finished release.

## Enforcement and recovery

- Settings/Recents recognition uses conservative screen classes. HyperOS may return generic classes. Sensitive protection allows unknown screens. All-Settings protection can obstruct repair until normal password/timer release. Network exceptions need a recognized screen.
- Chrome/Edge need a visible recognized address bar in a stable matching window. Unknown URLs, hidden bars, unsupported browsers and embedded WebViews are allowed by website rules. Separate browser app quotas still apply. No cached URL fallback or traffic inspection is used.
- Screen-off pauses billing. Background playback continues. Multi-window, picture-in-picture and notification-shade behavior have not been tested fully on the device.
- Usage checkpoints run every five seconds while billing and at transitions. Process death may lose one uncommitted interval. OS suspension can cause larger enforcement gaps. An offline app has no trusted external clock to resolve cross-reboot clock changes.
- Short recovery checks passed for screen off/on with manual unlock, process death while idle/billing, service reconnection, launcher restart, reboot and same-debug-APK replacement. Active TIMER deadlines and counters were retained. Early post-reboot app launches took several seconds to return Home, including launch/shell overhead. Exact enforcement latency, overnight availability and different-version/schema upgrades remain unmeasured. Recovery was inspected before app/provider startup could mask it.
- Lock state persists locally. Force-stop, Accessibility removal, cleared data, safe mode, uninstall and OS suspension can stop enforcement. Ordinary Device Admin adds deactivation friction; no device-owner privilege is used.
- HyperOS initially killed FocusGate with `SwipeUpClean`; Accessibility stayed malfunctioning until manually toggled. Enabling true Background autostart allowed reconnection with a brief observed gap. This switch is separate from Other permissions / Start in background. Set Battery saver to No restrictions too. Foreground priority improves process importance; it does not guarantee survival.

## Passwords and release

- Password derivation needs device benchmarking before release. No plaintext password, cloud recovery or forgotten-password reset is provided.
- Setup uses new/repeat input and saved-password status. Change requires current/new/repeat while unlocked. Wrong current-password attempts share a persisted retry delay with removal and unlock.
- Authenticated removal and eye controls passed unit/build checks. Removal explanation and eye state were checked on-phone using disposable text. The user reports password/UI behavior is fine, including the removal/eye follow-up. This is general user confirmation, not an instrumented private-entry sequence or full release-policy matrix.
- Removal requires the current password while unlocked and preserves rules, usage and remembered choices. The user confirmed it must remain unavailable during Restricted Mode. Without a verifier, Password-only/OR cannot start. Timer-only remains available by explicit choice. Release/expiry keeps the saved password.
- New choices are Password only, Timer only and Password OR timer. Existing legacy AND locks need both conditions at release. An early password does not authorize later release. New AND sessions are rejected.
- Each Start creates fresh deadlines. Last successful mode/duration/additions choices stay local. Short maintenance timers do not overwrite them. The full phone release/relock matrix is pending.
- Release unlocks settings. Automatic temporary pause/relock is not implemented. Disable/re-enable ordinary blockers and start another session as needed.

## UI, storage and support

- Remaining time appears in blocker cards and an optional notification. A floating Accessibility countdown is not implemented. Usage Access is not requested because current billing does not need it.
- Donation placeholders show network/address and copy controls only after maintainer setup. QR rendering is deferred while the address is blank. No payment or runtime network integration is used.
- Display preferences share the atomic Room state document instead of separate DataStore storage. Mutation guards keep display changes separate from enforcement rules.
- Policy uses string IDs and typed data classes, without dedicated value-ID wrappers. Gradle scripts use Groovy after an observed Kotlin-DSL compilation stall. Product code remains Kotlin.
- Diagnostics store bounded categories and timestamps only. No URLs, page content, messages, secrets or screenshots are logged.
- Normal GitHub credential-manager authentication was repaired and verified. Never use the exposed token from the original conversation.
