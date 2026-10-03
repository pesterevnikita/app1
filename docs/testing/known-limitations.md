# Development build limitations

- This build has not yet run its UI/enforcement acceptance tests on the primary phone because HyperOS rejected USB installation. It is a development build, not a certified first release.
- Settings and Recents classification is deliberately conservative and class-based. HyperOS may expose generic screen classes. Sensitive-only protection allows unknown screens; whole-Settings protection is broader and can obstruct repair until password/timer release. Network exceptions require a recognized screen.
- Chrome/Edge adapters require a visible recognized address bar in a stable matching window. Unknown URLs, hidden address bars, unsupported browsers and embedded WebViews are allowed by website rules. Independent browser app quotas still apply. There is no cached URL fallback or traffic inspection.
- Screen-off pauses billing; background playback continues. Multi-window/PiP and notification-shade observation have not been device-characterized.
- Checkpoints are scheduled every five seconds during billable use and at transitions. Ordinary death can lose up to one uncommitted checkpoint interval; OS suspension can introduce larger availability gaps. Clock changes across reboot cannot be resolved using a trusted external clock because the app is offline.
- Locked state persists locally, but force-stop, disabling Accessibility, data removal, safe mode, uninstallation, or OEM suspension can stop enforcement. Device Admin adds only normal deactivation friction; no device-owner privilege is used.
- Remaining time is shown in blocker cards and an optional notification. A floating Accessibility countdown overlay is not implemented in this development build. Usage Access is not requested because current accounting does not need it.
- Donation placeholders support static network/address display and copy after maintainer configuration. A QR renderer is deferred while the address is blank. No payments or network activity are implemented.
- Presentation preferences are stored in the same atomic Room state document, rather than separate DataStore storage. Mutation guards keep them separate from enforcement policy.
- Policy uses string IDs and typed data classes rather than dedicated Kotlin value-ID wrappers. Gradle scripts use Groovy to avoid an observed Kotlin-DSL compilation stall on this laptop; product code remains Kotlin.
- Password derivation has a conservative default work factor but requires device benchmarking before release. No plaintext password, cloud recovery, or “forgot password” bypass exists.
- Release mode unlocks configuration; there is no automatic temporary-pause/relock feature. Disable/re-enable ordinary blockers and start another locked session as needed.
- Advanced diagnostics record only bounded typed categories and timestamps. They do not record URLs, page content, messages, secrets or screenshots.
- GitHub publishing requires repaired credential-manager authentication. Local history is preserved without using the exposed token.
