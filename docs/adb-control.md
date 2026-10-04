# Local ADB maintenance controls

**TL;DR:** Use `tools/focusgate-adb.ps1` for local status, profile import and guarded rule edits. All builds retain this user-approved API. Caller must have `android.permission.DUMP` and UID 2000. It cannot unlock, handle credentials, grant permissions or reset usage. Locked writes keep normal app guards.

The provider uses local Binder IPC, not a network server. FocusGate remains offline. The app is a self-control tool; ADB is outside its normal-action threat model.

## Access and response format

Authority: `content://io.github.pesterevnikita.focusgate.control`. API version: 1.

Android requires the signature-level `android.permission.DUMP`. The provider also requires exact ADB shell UID 2000. Ordinary apps, FocusGate's own UID and root UID fail that UID check.

The helper sends named string arguments in a JSON object through `--arg`. Android's `--extra` parser rejects colons inside values. Simple raw calls can still use named string extras; do not combine both forms.

Every response has a `json` Bundle string with `apiVersion`, `ok`, and method data or an `error`. Rejected edits leave configuration unchanged. Each call first handles ordinary timer expiry. Commands are refused before the first device unlock; they do not open credential-protected storage early.

The API uses normal validation, revision checks and Restricted Mode guards. It cannot activate grants, read observed browser content, accept passwords, export password verifiers, reset locks or clear usage.

## PowerShell helper

From the repository:

```powershell
$status = .\tools\focusgate-adb.ps1 status
$status | ConvertTo-Json -Depth 12
.\tools\focusgate-adb.ps1 config | ConvertTo-Json -Depth 12
```

Use rule/group IDs from status/config. Preset IDs are not hardcoded. The helper quotes Android shell arguments, including regex punctuation and single quotes. It sends UTF-8 through stdin to avoid Windows argument rewriting. Never build raw shell strings from user values or put credentials in commands.

```powershell
.\tools\focusgate-adb.ps1 quota-set -Values @{groupId='GROUP_ID'; allowanceMinutes='1'; capMinutes='0'}
.\tools\focusgate-adb.ps1 quota-set -Values @{groupId='GROUP_ID'; allowanceMinutes='15'; capMinutes='1'; breakMinutes='1'}
.\tools\focusgate-adb.ps1 rule-set -Values @{ruleId='RULE_ID'; enabled='false'}
.\tools\focusgate-adb.ps1 target-add -Values @{ruleId='RULE_ID'; kind='host'; value='example.com'}
.\tools\focusgate-adb.ps1 lock-timer -Values @{minutes='5'}
```

`lock-timer` accepts one through five minutes. It requires connected Accessibility and an enabled blocker. It starts an ordinary TIMER session and cannot replace an active lock. Configure password modes and release through the UI. Test timers leave remembered lock choices unchanged.

If PowerShell blocks script files, use the trusted helper with process-local policy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\focusgate-adb.ps1 status
```

Do not change machine-wide execution policy.

## Commands

| Method | String arguments | Behavior |
| --- | --- | --- |
| `status` | none | Sanitized connectivity, lock state, IDs and projected quota status; no credentials or observed URLs |
| `config` | none | Exportable configuration; no credentials, session or usage ledger |
| `config-import` | `json`; optional `merge` | Same import validation as UI/store; unlocked-only; 64 KiB ADB transport limit |
| `quota-set` | `groupId`; optional `allowanceMinutes`, `capMinutes`, `breakMinutes`, `period` | Edit an existing group; cap 0 disables it; period HOUR or DAY |
| `rule-set` | `ruleId`, `enabled` | Enable/disable an existing rule; strict true/false |
| `target-add` | `ruleId`, `kind`, `value` | Add app, host or regex target with normal validation |
| `protections-set` | optional `settingsMode`, `recents`, `networkExceptions` | Edit Settings/Recents configuration; grants remain manual |
| `lock-timer` | `minutes` | Start a short timer-only lock |
| `refresh` | none | Reload persisted state and handle ordinary timer expiry |

Raw CLI example:

```powershell
adb shell content call --uri content://io.github.pesterevnikita.focusgate.control --method status
```

## Saved profiles

Export the configuration document. Import only while unlocked:

```powershell
(& .\tools\focusgate-adb.ps1 config).config | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 .\profile.json
.\tools\focusgate-adb.ps1 config-import -ProfilePath .\profile.json
.\tools\focusgate-adb.ps1 config-import -ProfilePath .\profile.json -Values @{merge='true'}
```

Default import replaces configuration. Merge adds it under normal import rules. Both preserve credentials and usage. Replacement resets optional Settings/Recents/uninstall protections for capability review, matching UI import. Profiles contain no active locks. ADB transport is limited to 64 KiB; use the UI for larger supported files. Import cannot release a lock.

## Test rules and measured results

Save configuration before temporary edits. Preserve counters, password and active lock. Restore quotas/protections through guarded commands afterward.

Status projection alone does not prove billing or redirection. Open a covered app and check the resumed activity independently. Coordinate reboot and manual grants with the user. Instrumentation force-stops FocusGate; do not run it during a live acceptance test.

Debug and unsigned release assembly passed. On Xiaomi, status/config access, quota edits, locked weakening refusal, duplicate-lock refusal and normal TIMER expiry were observed. Native redirection was checked separately. Profile replacement preserved remaining usage; import during a live timer lock was refused. Read the [phone record](testing/phone-acceptance.md) for pending tests.
