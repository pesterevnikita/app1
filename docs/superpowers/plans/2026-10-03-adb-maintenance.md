# ADB maintenance extension: 2026-10-03

**TL;DR:** Keep offline ADB controls in debug and final builds. Only the Android shell may call them. Use the same validation and locked-change guards as the app. Expose no password, release, grant, or reset command. Read [the API guide](../../adb-control.md) for current commands and [the roadmap](../../roadmap.md) for status.

The user approved this interface for future development. It allows commands instead of screenshot-based form editing. Preserve credentials, usage, and Restricted Mode. These steps are the extension plan, not claims that every test has passed.

## Implementation and tests

1. Add a main-source `ContentProvider`. Require caller `DUMP` permission and exact shell UID 2000. Return sanitized status/configuration. Allow normal quota/rule/target/protection changes, refresh, and short timer locks. Add no credential, release, grant, or reset endpoint.
2. Test strict parsing, numeric bounds, caller identity, locked weakening, and allowed additions. Route every change through the real transactional `AppStore` guard and revision checks.
3. Add a PowerShell helper that quotes arguments safely. Document the API.
4. Include the quota countdown projection fix. Run JVM tests, Android lint, and debug/release builds. Check manifest permission boundaries and absence of network permissions.
5. Update the phone without clearing data. Test helper status, quota changes, locked refusal, timer expiry, and actual Home redirection. Restore the shared quota to 15 minutes per clock hour with the continuous cap off.
6. Record measured results and remaining checks. Review the diff. Commit and push using existing credential-manager authentication.

## Approved profile-import follow-up

Add `config-import` for ordinary exported JSON. Use the existing transactional importer. Support replace and merge. Reject imports during active locks. Limit the ADB import payload to 64 KiB. Preserve credentials and counters. Explain the normal import behavior that resets optional protections for review.

On the phone, test a saved-profile import and refusal while locked. Record actual results in [phone acceptance](../../testing/phone-acceptance.md). Do not infer phone success from unit tests.
