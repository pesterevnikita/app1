# ADB maintenance extension — 2026-10-03

Approved: retain an offline shell-only interface in debug and final builds, allowing future agent development without screenshot-based form editing. It must preserve normal validation, revision checks, credentials, usage and Restricted Mode.

1. Implement a main-source ContentProvider protected by caller DUMP permission and exact shell UID 2000. Expose sanitized status/config, ordinary quota/rule/target/protection changes, refresh and short timer locks; no password, release, grant or reset commands.
2. Test strict parsing, numeric bounds, shell identity and locked weakening/additions. Use the real transactional AppStore guard for every mutation.
3. Provide an argument-quoted PowerShell helper and API documentation.
4. Integrate the quota countdown projection fix; run JVM tests, lint and debug/release assembly. Inspect manifests for permission boundaries and absence of network permission.
5. Install without clearing data. Verify helper status, quota changes, locked rejection, timer expiry and actual Home redirection on the Xiaomi. Restore temporary 15-minute quota settings with continuous cap off.
6. Record measured acceptance and remaining checks, review the diff, commit and push through existing credential-manager authentication.

Follow-up approved during implementation: add config-import using ordinary exported JSON and the existing transactional importer. Support replace/merge, reject active locks, cap ADB payload at 64 KiB, preserve credentials/counters, and document ordinary protection-reset semantics. Verify actual saved-profile import and locked refusal on phone.
