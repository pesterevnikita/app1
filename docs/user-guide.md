# FocusGate user guide

**Version 0.1 development: on-phone acceptance pending.** This guide explains the intended app and policies. Controls, browser coverage, persistence, and device protections still need acceptance testing on the primary Xiaomi 11T running Android 14/HyperOS. Do not depend on an untested long lock.

FocusGate adds a pause between an impulse and another hour of scrolling. Choose your rules, test them, then lock them for a commitment you want to keep. Configuration stays on your phone.

## Install and prepare

Build/install instructions are in the [README](../README.md). The development build requires Java 21, a local Android SDK, the Gradle wrapper, and an authorized device. Installation does not grant system permissions.

1. Open FocusGate and read the Accessibility disclosure. Manually enable its Accessibility service in Android Settings. It observes relevant foreground windows and known browser address bars to decide when to return Home. Sideloaded apps may require Android's **Allow restricted settings** action before Accessibility can be enabled; exact menus depend on the installation and OS version.
2. Review setup health. Accessibility must be connected for enforcement. Notification permission is optional for status/countdowns; Usage Access is optional for diagnostics. Grant only the permissions for features you choose.
3. On Xiaomi, find FocusGate's battery settings and select the least restrictive background option available, commonly **No restrictions**. Enable its autostart/background autostart option if present. Menu names vary across HyperOS versions; these steps and their effect are not yet phone-verified. An unavailable public status signal must be shown as **Unknown** or **User confirmed**, not automatically healthy.
4. If you choose **Uninstall resistance**, activate ordinary Device Admin manually. This may add a deactivation step before uninstall; it does not grant device-owner uninstall blocking. Review Settings and Recents protection separately, including any unavailable capabilities.
5. Test a blocked native app and a short quota with editable settings. Test browser cases and service recovery before choosing a short Restricted Mode trial. Confirm the trial releases as expected before making a longer commitment.

No system grant can be silently restored after you disable it. Check setup health after reboot or app/OS updates. Enforcement can pause while Android suspends or reconnects the service.

## Blockers and presets

The **Blockers** tab is intended for reviewing, adding, and editing rules and remaining allowance. Presets require confirmation and are ordinary editable/deletable rules while unlocked:

| Preset | Targets | Intended policy |
| --- | --- | --- |
| No YouTube or Instagram | Native YouTube/Instagram apps and their website hosts | Continuous blocking |
| Shared distraction budget | Chrome, Edge, Telegram, Ozon shopping | One combined 15 minutes per clock hour |

Ozon Bank is excluded from the shopping preset. Check actual installed app labels/package IDs; alternate profiles, clones, embedded browsers, and other browsers are not automatically covered. YouTube hosts include `youtube.com`, `youtu.be`, and `youtube-nocookie.com` with subdomains; Instagram includes `instagram.com` with subdomains. Host matching uses domain boundaries, so `notyoutube.com` does not match `youtube.com`.

Enabled blockers apply outside Restricted Mode. Applicable rules combine: any denying rule wins, even if another rule has allowance remaining. Schedules can use weekdays, time windows, and date ranges; a quota imposes no restriction outside its applicable schedule. Review the schedule preview before saving. Overnight windows belong to the weekday on which they start.

### Shared budgets and continuous sessions

Seven minutes in Chrome plus eight in Telegram uses the entire shared 15-minute budget for all four targets. Only interactive foreground use while the screen is on and unlocked counts. Switching apps within a group does not create a new allowance; matching several targets in that group counts elapsed time once. Background playback is not charged. Independent overlapping groups each count applicable use.

The allowance resets at a clock-hour boundary without rollover. Using 15 minutes before 11:00 and another 15 minutes after 11:00 can allow 30 consecutive minutes. Daily fixed windows are also part of the intended policy model. Restricted Mode captures a timezone for the session. Manual clock changes remain a limitation without a trusted external clock.

The optional **continuous-session cap** is off until selected. Example values are a 15-minute cap and a five-minute required break. Switching between group apps or crossing an hourly reset preserves session use. Time away pauses use; only one uninterrupted full break away from all group targets resets it. Screen-off/lock time can count as a break. Returning early cancels a partial break; denied attempts do not interrupt a break. A reached cap denies access even when hourly allowance remains.

The design checkpoints active usage at most every five seconds. An ordinary process death may lose up to five seconds of unrecorded use; unobserved downtime must not be billed. Recovery and multi-window/PiP behavior require phone testing.

### Website coverage

Website rules act only on confidently identified matching URLs from supported Chrome/Edge address-bar nodes. They do not scan page text or network traffic. Unknown, hidden, stale, or unobservable URLs remain accessible under website rules. A separate browser app blocker or exhausted app quota can still return Home, and unknown URLs still count against applicable browser app budgets.

Private tabs, redirects, tab switches, hidden bars, and browser updates need separate coverage checks. Use the local match tester and coverage status when available. A Home redirect may briefly reveal the app/page first; it does not stop background audio, downloads, notifications, or network activity in other apps.

## Restricted Mode and release

The **Restricted Mode** tab is intended to show configuration lock state, health, release policy, and remaining time. Preflight reviews rules, permissions, browser/protection limitations, and release prerequisites. Core disconnected Accessibility or missing required credentials must prevent starting a lock. Optional capability gaps require acknowledgement.

| Release choice | How configuration becomes editable |
| --- | --- |
| Password only | Correct trusted-person password; no automatic release |
| Timer only | Deadline reached; no password override |
| Password OR timer | Either correct password or deadline; default design |
| Password AND timer | Correct password entered at or after the deadline |

A correct early password for AND does not pre-authorize later release. Expiry/release unlocks settings and **leaves enabled blockers running**. To stop a blocker, disable it after release. Early OR release lets you change rules and start a new session; it is not a timed pause with automatic re-enablement.

Set a trusted-person password while unlocked and keep it with that person. There is no locked **forgot password** bypass. Retry delays apply after failed attempts but must not postpone automatic timer release.

While locked, weakening rules, deleting/disabling blockers, raising budgets, reducing protections, changing release credentials, resetting counters, and importing configurations are prohibited. If restrictive additions were enabled before starting, allowed additions include a new enabled independent blocker, extra targets, and lower allowances. They cannot be undone during the session. Arbitrary schedule/regex edits are not accepted as provably restrictive. Harmless appearance preferences and configuration export remain available.

## Protection, repair, and uninstall

Settings protection is optional: off, sensitive screens only, or whole Settings. Sensitive-only coverage depends on reliable device-screen recognition; unknown screens remain accessible. Whole-Settings blocking must be explicitly selected. Wi-Fi/mobile exceptions and scoped permission repair require reliable recognition. If safe repair cannot be constrained, release the session using its configured condition before changing grants.

Recents protection attempts Home redirection when Recents is detected; it does not disable Recents universally. Ordinary Device Admin may make uninstall less convenient, but force-stop, permission revocation, OEM process termination, safe mode, cleared data, uninstall, ADB/root, or factory reset remain outside the self-control guarantee. FocusGate is intended for a willing user, not an adversarial security boundary.

After release, disable selected blockers/protections, turn off Device Admin through Android's administrator settings if active, disable Accessibility if desired, and uninstall normally. If protection obstructs Settings, first turn that protection off while configuration is editable. These paths still require device acceptance testing.

## Offline backup, privacy, and support

The **More** tab is intended to contain setup health, FAQ, local diagnostics, import/export, privacy, version, and donation information. Configuration export uses versioned JSON through Android's file picker. Prefer a local folder: selecting a cloud-backed document provider lets that provider use its own network service.

Exports include rules/groups/schedules and presentation preferences, excluding passwords/verifiers, active sessions, usage counters, grants, and logs. Import is allowed only while unlocked, with merge/replace preview and validation. It cannot unlock a session, restore OS grants, or transfer usage allowance. Diagnostics are optional, off by default, bounded, and exported separately; raw URLs, page/message content, passwords, and browsing history must not be recorded.

The runtime design has no Internet/network-state permissions, accounts, ads, telemetry, VPN, or cloud sync. Build tools can download dependencies. Other apps' network behavior is outside FocusGate's control.

Donation network/address placeholders are empty until supplied by the maintainer. Payment/copy/QR controls should remain hidden while no verified address exists. No address is invented and no feature is donation-gated.

## FAQ

**Why is an app still blocked after my timer ends?** The timer releases the configuration lock. Disable the ordinary blocker after release if you want access.

**Why can I use 30 minutes around an hour boundary?** Each clock hour grants its own 15 minutes. Enable a continuous-session cap if you want a break across that boundary.

**What if I forget the password?** OR and timer-only release at the deadline. Password-only has no timer release. AND still requires the password after the deadline. Import cannot provide an escape or reset an active session.

**Why did a website remain accessible?** An unknown URL is deliberately allowed by website rules. Private/embedded browsers and unobservable address bars may lack coverage. Whole-browser app rules provide a separate restriction.

**Why is YouTube audio still playing?** Home redirection does not terminate another app or stop background playback.

**Does Device Admin prevent uninstall?** Ordinary administrator activation may add a deactivation step; it is not a guaranteed uninstall block. OEM Settings protection must be tested independently.

**What if I force-stop FocusGate or lose Accessibility?** Enforcement can stop. Reopen the app, review health, and repair the grant manually using the supported scoped flow or after release. Battery/autostart changes can help availability but cannot guarantee it.

**Is my browsing sent anywhere?** Runtime networking and browsing-history collection are excluded by design. Known URL text is used transiently for local matching. Confirm the final manifest/privacy checks and phone results before treating the development build as accepted.
