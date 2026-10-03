# FocusGate

FocusGate is a native Android self-control app designed to redirect selected apps and known matching websites to Home. Rules, shared usage budgets, and configuration stay on the phone.

**Status: 0.1 development APK installed; 39 unit tests and one on-device Room test pass. Phone acceptance is partial.** Native blocking, initial Chrome/Edge website cases, and swipe-away enforcement were observed on the Xiaomi 11T; full browser/restart/lock coverage remains unverified. See [verification results](docs/testing/phone-acceptance.md), the [accepted design](docs/superpowers/specs/2026-10-03-focusgate-design.md), [implementation plan](docs/superpowers/plans/2026-10-03-focusgate.md), and [user guide](docs/user-guide.md).

The editable presets offer continuous YouTube/Instagram blocking and a combined 15-minute allowance per clock hour for Chrome, Edge, Telegram, and Ozon shopping. An optional continuous-session cap adds a required break. Restricted Mode locks configuration; enabled blockers work even when that mode is off. Password OR timer release is the default design, with password-only, timer-only, and password AND timer choices.

## Build and install

Use Java 21 and a local Android SDK with platform 36 and compatible build tools. Android 10/API 29 is the minimum target. Set `JAVA_HOME` to your Java 21 installation and create an untracked `local.properties` with your SDK location, for example `sdk.dir=C:/Android/Sdk`. Do not commit machine-specific paths or signing keys.

From PowerShell in the repository:

```powershell
.\gradlew.bat :policy:test :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The checked-in Gradle wrapper verifies its official distribution checksum. Dependencies may require downloads during development; runtime execution is offline. On a corporate connection, pass local proxy host/port JVM options to Gradle; do not commit proxy credentials. If downloading Maven AAPT2 times out, an installed compatible SDK copy can be selected with `-Pandroid.aapt2FromMavenOverride=<SDK>/build-tools/<version>/aapt2.exe`. Installation requires an authorized connected device and Android SDK platform tools on `PATH`. Xiaomi may require **Install via USB** and an installation prompt confirmation. Grant Accessibility manually after reading its disclosure. A debug build is for development, not a signed production release.

## Use and limits

Review targets, test a short quota while settings are editable, then test a short Restricted Mode session and its release. Xiaomi battery/autostart setup and optional ordinary Device Admin need manual review; instructions and caveats are in the [user guide](docs/user-guide.md).

Website rules deny only confidently identified matching URLs in supported Chrome/Edge address bars. Unknown URLs remain accessible unless an independent app rule or exhausted budget denies the browser. This is UI redirection, not a network firewall: background audio, downloads, and notifications may continue. Android permission loss, force-stop, OEM suspension, data removal, and physical-device bypasses can defeat enforcement.

Configuration import/export is intended to use local versioned JSON, excluding passwords, active sessions, usage counters, and logs. No accounts, telemetry, VPN, or runtime networking are part of the design. Donation information uses empty maintainer-supplied placeholders; no payment address is provided.

Phone results must be recorded before describing the app as accepted or protections as working on a specific device.

For further development or an agent handoff, start with [AGENTS.md](AGENTS.md) and [development context](docs/development.md). They include architecture, build commands, test practices, and the current device investigation.
