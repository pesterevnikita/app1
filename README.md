# FocusGate

**TL;DR:** Offline Android app that returns blocked apps and known matching websites to Home. Rules enforce even when settings are unlocked. Development APK installed; 69 unit tests pass. Phone acceptance is partial. Start with the [roadmap](docs/roadmap.md).

FocusGate helps you step away from distracting apps. Rules, shared budgets, passwords and usage stay on the phone. It is a self-control tool for normal Android actions. It is not a device-owner security product.

## Current status

Version 0.1 is a development build. Native YouTube/Instagram blocking, initial Chrome/Edge website cases and repeated swipe-away enforcement were observed on the Xiaomi 11T. An earlier Room test passed on the phone. Wider browser, restart and lock coverage still needs testing.

See the [phone results](docs/testing/phone-acceptance.md), [known limits](docs/testing/known-limitations.md), [accepted design](docs/superpowers/specs/2026-10-03-focusgate-design.md), [original plan](docs/superpowers/plans/2026-10-03-focusgate.md) and [user guide](docs/user-guide.md). The roadmap tracks current work; the original plan records implementation intentions.

## Features

- Editable presets block YouTube/Instagram apps and websites, or share 15 minutes per clock hour across Chrome, Edge, Telegram and Ozon shopping.
- Optional continuous-session limits require a break, including across hourly resets.
- Enabled blockers enforce outside Restricted Mode. Restricted Mode locks configuration against weakening changes.
- Release choices: Password only, Timer only, Password OR timer. Password OR timer is the default design.
- Passwords stay saved across release and new locks. Change or removal requires the current password while unlocked. Setup requires matching new entries. Eye buttons reveal only current input.
- Local JSON import/export excludes passwords, active sessions, usage counters and logs.
- The guarded [ADB maintenance interface](docs/adb-control.md) supports local development in every build variant.

## Build and install

Use Java 21 and an Android SDK with platform 36 and compatible build tools. Minimum Android version: Android 10/API 29. Set `JAVA_HOME` to Java 21. Create an untracked `local.properties` with your SDK location. Keep local paths and signing keys out of Git.

From PowerShell in the repository:

```powershell
.\gradlew.bat :policy:test :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Gradle wrapper checks the official distribution checksum. Build tools may download dependencies; the installed app stays offline. If Maven AAPT2 cannot download, use an installed compatible SDK copy with `-Pandroid.aapt2FromMavenOverride=<SDK>/build-tools/<version>/aapt2.exe`.

Connect an authorized device and put Android platform tools on `PATH`. Xiaomi may require **Install via USB** and a manual install confirmation. Read the Accessibility disclosure, then grant Accessibility manually. A debug APK is not a signed production release.

## Use and limits

Review targets and test a short quota while settings are editable. Then test a short Restricted Mode session and its release. Xiaomi autostart, battery settings and optional Device Admin require manual setup. Read the user guide before starting a long lock.

Website rules need a confidently identified URL from a supported Chrome/Edge address bar. Unknown URLs stay accessible unless another app rule or exhausted browser budget denies access. Home redirection may leave background audio, downloads or notifications running.

Permission loss, force-stop, OS suspension, data removal and physical-device bypasses can stop enforcement. Ordinary Device Admin adds uninstall friction; it does not provide device-owner uninstall prevention. Record phone evidence before claiming a protection works on that device.

No accounts, telemetry, VPN or runtime networking are used. Donation details and Telegram feedback remain empty maintainer placeholders. **More → About & support** shows the repository and copy-only support links. Other apps may use their own network if you paste a link there.

## Continue development

Read [AGENTS.md](AGENTS.md), [development context](docs/development.md) and the roadmap. They explain architecture, build checks and pending device tests. Keep current work in the roadmap and testing records.
