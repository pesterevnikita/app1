# Local ADB maintenance controls

The user requested command-based testing and approved retaining these controls in final builds. They reuse the application's normal validation, revision checks and Restricted Mode guards. They do not activate system grants, read observed browser content, accept passwords, export password verifiers, reset locks, or clear usage.

The ContentProvider requires Android's signature-level `android.permission.DUMP` from its caller and separately accepts only the ADB shell UID (2000). Ordinary apps, the app's own UID and root UID do not pass that caller check. This is local Binder IPC, not a network server. The application remains a self-control tool; ADB itself is outside its normal-action threat model.

Authority: `content://io.github.pesterevnikita.focusgate.control`. API version: 1. The helper sends a JSON object of named string arguments using `--arg` because Android's `--extra` parser rejects colons inside values. Plain named string extras remain accepted for simple raw CLI calls; do not combine the two forms. Each response contains a `json` Bundle string with `apiVersion`, `ok`, and method-specific data or an `error`. Rejected edits do not change configuration; each call first reconciles any ordinary timer expiry. Before first device unlock, commands are refused rather than opening credential-protected storage.

## PowerShell helper

From the repository:

```powershell
$status = .\tools\focusgate-adb.ps1 status
$status | ConvertTo-Json -Depth 12
.\tools\focusgate-adb.ps1 config | ConvertTo-Json -Depth 12
```

Use the rule/group IDs returned by status/config; IDs are not hardcoded presets. The helper quotes every argument for Android's shell, including regex punctuation and single quotes, and sends UTF-8 over stdin to avoid Windows native argument rewriting. Do not construct raw shell strings from user values or put credentials in commands.

```powershell
.\tools\focusgate-adb.ps1 quota-set -Values @{groupId='GROUP_ID'; allowanceMinutes='1'; capMinutes='0'}
.\tools\focusgate-adb.ps1 quota-set -Values @{groupId='GROUP_ID'; allowanceMinutes='15'; capMinutes='1'; breakMinutes='1'}
.\tools\focusgate-adb.ps1 rule-set -Values @{ruleId='RULE_ID'; enabled='false'}
.\tools\focusgate-adb.ps1 target-add -Values @{ruleId='RULE_ID'; kind='host'; value='example.com'}
.\tools\focusgate-adb.ps1 lock-timer -Values @{minutes='5'}
```

`lock-timer` is limited to one through five minutes, requires actual connected Accessibility and an enabled blocker, and uses the ordinary TIMER release policy. It cannot replace an active lock. Password policies and password release are configured through the UI.

## Commands

| Method | String arguments | Behavior |
| --- | --- | --- |
| `status` | none | Sanitized connectivity, lock state, IDs and projected quota status; no credentials or observed URLs |
| `config` | none | Ordinary exportable configuration; no credentials, session or usage ledger |
| `config-import` | `json`; optional `merge` | Import ordinary exported JSON through the same UI/store validation; unavailable while locked; 64 KiB ADB transport limit |
| `quota-set` | `groupId`; optional `allowanceMinutes`, `capMinutes`, `breakMinutes`, `period` | Edit one existing group; cap 0 disables it; period HOUR or DAY |
| `rule-set` | `ruleId`, `enabled` | Enable/disable one existing rule; strict true/false |
| `target-add` | `ruleId`, `kind`, `value` | Add an app, host or regex target with normal validation |
| `protections-set` | optional `settingsMode`, `recents`, `networkExceptions` | Change ordinary Settings/Recents configuration; grants remain manual |
| `lock-timer` | `minutes` | Start a short timer-only configuration lock |
| `refresh` | none | Reconcile persisted state and normal timer expiry |

For raw CLI use:

```powershell
adb shell content call --uri content://io.github.pesterevnikita.focusgate.control --method status
```

## Testing discipline

Capture configuration before temporary changes; preserve the actual counters, password and lock. Restore temporary quotas/protections after a test through the same guarded commands. Never treat ADB status projection as proof that foreground accounting or redirection actually works: independently open a covered app and check the resumed activity. Coordinate reboot and manual grants with the user. Do not run instrumentation that force-stops FocusGate during a live acceptance test.

Debug and unsigned release assembly passed. On the Xiaomi, status/config access, quota edits, locked weakening refusal, duplicate-lock refusal and normal timer expiry were verified. Native redirection was checked independently. Saved-profile replacement preserved remaining usage, and import was refused during a live timer lock. Consult the phone acceptance record for remaining coverage.

On a PowerShell machine that disables script files, invoke the trusted helper with a process-local policy only: `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\focusgate-adb.ps1 status`. Do not change the machine-wide execution policy.

## Saved profiles

Export the actual configuration document, then import it during an unlocked test:

```powershell
(& .\tools\focusgate-adb.ps1 config).config | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 .\profile.json
.\tools\focusgate-adb.ps1 config-import -ProfilePath .\profile.json
.\tools\focusgate-adb.ps1 config-import -ProfilePath .\profile.json -Values @{merge='true'}
```

The default replaces configuration; merge adds it using the existing import rules. Both retain credentials and usage counters. Replacement resets optional Settings/Recents/uninstall protections for capability review, just like UI import. Configuration profiles do not contain active locks. ADB transport is bounded to 64 KiB; the UI remains available for larger supported files. Never use profile import as a lock-release mechanism.