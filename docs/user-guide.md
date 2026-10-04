# FocusGate user guide

**TL;DR:** Choose blockers, grant Accessibility, and test them. Blockers enforce while settings are unlocked. Restricted Mode prevents weaker settings until its password or timer release. Start with a short lock. Version 0.1 has partial phone acceptance; read the [roadmap](roadmap.md) and [limits](testing/known-limitations.md).

FocusGate adds a pause before another hour of scrolling. Choose rules that help you leave distracting apps. Test them, then lock them for a commitment you want to keep. Configuration stays on your phone.

## Install and prepare

See the [README](../README.md) for build and installation. Installation does not grant system permissions.

1. Read the Accessibility disclosure. Enable FocusGate manually in Android Accessibility settings. It observes foreground app identities and supported browser address bars, then returns Home when a rule denies access. Android may require **Allow restricted settings** for a sideloaded app. Menu names depend on the OS and installation method.
2. Check setup health. Accessibility must be connected. Notification permission is optional for status/countdowns. This build does not request Usage Access. Grant permissions only for features you choose.
3. On Xiaomi, open **More → Open Background autostart** and enable FocusGate. Open **Battery saver → No restrictions**. Background autostart is separate from **Other permissions → Start in background**. Check the switches yourself; FocusGate cannot reliably read them. Setup buttons fall back to app details when needed. If Accessibility says malfunctioning, toggle FocusGate off/on in Accessibility. Remove its Recents card, then test a blocked app.
4. If you select **Uninstall resistance**, activate Device Admin manually. It may require deactivation before uninstall. It does not provide device-owner uninstall blocking. Test Settings and Recents protections separately.
5. Test a native blocker, a short quota and browser cases. Then test a short Restricted Mode session. Confirm release and Settings access before starting a longer lock.

Disabled grants cannot be restored silently. Check health after reboot or app/OS updates. Enforcement can pause while Android suspends or reconnects the service.

When enforcement is needed, the existing Accessibility service uses foreground priority and a quiet persistent notification. This adds no polling loop or wake lock. **More** shows background protection status. If promotion fails, connected Accessibility can still enforce; reopening FocusGate retries promotion. Android force-stop and OS termination remain possible.

## Blockers and presets

Use **Blockers** to review, add or edit rules and remaining time. Presets are ordinary editable rules. You can disable or delete them while unlocked.

| Preset | Targets | Rule |
| --- | --- | --- |
| No YouTube or Instagram | Native apps and website hosts | Always block during schedule |
| Shared distraction budget | Chrome, Edge, Telegram, Ozon shopping | One combined 15 minutes per clock hour |

Ozon Bank is excluded. Check installed labels and package IDs. Alternate profiles, cloned apps, embedded browsers and other browsers are not automatically covered.

YouTube hosts: `youtube.com`, `youtu.be`, `youtube-nocookie.com`, including subdomains. Instagram host: `instagram.com`, including subdomains. Matching respects domain boundaries: `notyoutube.com` does not match `youtube.com`.

Enabled blockers apply outside Restricted Mode. Any denying applicable rule wins, even if another rule has time left. Schedules use weekdays, time windows and date ranges. Outside its schedule, a rule adds no restriction. Check the schedule before saving. An overnight window belongs to the day when it starts.

In the editor, choose **Always block during schedule** for full blocking. Choose **Allow limited usage** for a shared allowance. Both use the same schedule; the allowance covers all selected targets together.

### Shared time and continuous sessions

Seven minutes in Chrome plus eight in Telegram consumes the shared 15-minute budget for all four targets. Only interactive foreground use with the screen on and unlocked counts. Switching targets does not reset time. Multiple matching targets in one group count elapsed time once. Independent overlapping groups each count their applicable use. Background playback is not charged.

An hourly allowance resets at the clock-hour boundary. Unused time does not carry over. Fifteen minutes before 11:00 plus fifteen after 11:00 can allow 30 consecutive minutes. Daily clock periods are also supported. Restricted Mode saves its timezone. Manual clock changes remain a limit because the app has no trusted external clock.

The optional **Maximum continuous session** is off until selected. For example, set a 15-minute cap and five-minute break. Switching group apps or crossing the hour boundary keeps session use. Time away pauses use. Only one full uninterrupted break away from every group target resets it. Screen-off/lock time can count as a break. Returning early cancels a partial break; denied attempts do not interrupt it. A reached cap blocks even if hourly time remains.

Usage checkpoints run every five seconds while billing and at transitions. Ordinary process death can lose up to five seconds of unrecorded use. Unobserved downtime must not be charged. Recovery, multi-window and picture-in-picture still need phone tests.

### Websites

Website rules use confidently recognized URLs from supported Chrome/Edge address bars. They do not scan page text or network traffic. Unknown, hidden, stale or unavailable URLs stay allowed by website rules. Separate browser app rules and quotas still apply; unknown pages count toward applicable browser app budgets.

Private tabs, redirects, tab switches, hidden bars and browser updates need separate tests. Use the local match tester and coverage status. Home redirection may briefly reveal the page first. It does not stop background audio, downloads, notifications or another app's network activity.

## Restricted Mode

This tab groups status, password controls, lock choices and device protections. Starting a lock requires connected Accessibility, enabled blockers, any required password, and acknowledgement of coverage limits.

| Release choice | Unlock condition |
| --- | --- |
| Password only | Correct saved password; no timer |
| Timer only | Deadline; no password override |
| Password OR timer | Correct saved password or deadline; default design |

Existing legacy **Password AND timer** sessions keep both conditions. Enter the correct password at or after the deadline. An early password does not authorize later release. New sessions cannot select AND.

Release unlocks configuration and **leaves enabled blockers running**. Disable a blocker after release if you want access. Early OR release lets you edit and start a new session. There is no automatic temporary-pause/relock feature.

Each Start begins a fresh duration. The last successful mode, duration and restrictive-additions choice are remembered locally. Short maintenance timers do not replace those choices.

### Password setup, change and removal

While unlocked, **Set password** asks for **New password** and **Repeat new password**. There is no current-password field when none is saved. Ask a trusted person to set and keep it. Passwords remain saved after release and are reused for later locks.

**Change password** requires the correct **Current password**, plus matching **New password** and **Repeat new password**. Wrong current-password attempts trigger a saved retry delay. Unlocked settings do not let you replace the password without knowing it.

**Remove password** also requires unlocked settings and the correct current password. Rules, usage and remembered lock choices stay unchanged. Status becomes **No password set**. Later setup asks only for new and repeated input.

Without a password, Password only and Password OR timer cannot start. **OR does not silently become Timer only.** Choose Timer only explicitly, or set a password. Timer-only sessions have no early password release. You cannot remove a password during an active lock; release the lock normally first.

Change and removal require the current password even after timer expiry. There is no forgotten-password reset. Retry delays do not delay automatic timer release.

Eye buttons show or hide current input. Each field starts hidden. Clearing a field or reopening a dialog hides it again. The saved password is never shown.

### Changes while locked

You cannot disable/delete rules, raise budgets, reduce protections, change credentials, reset counters or import configuration. If stronger additions were allowed at Start, you can add an enabled independent blocker, add targets or lower allowances. You cannot undo those changes until release. Arbitrary schedule/regex edits are not accepted as proven stronger changes. Display preferences and configuration export remain available.

## Protection and repair

Settings protection has three choices: off, sensitive screens, or all Settings. Sensitive protection needs recognized screen classes and allows unknown screens. All-Settings blocking is broader and can obstruct repair. Wi-Fi/mobile exceptions also need recognition. Release the session normally before changing grants when safe repair cannot be limited.

Recents protection returns Home when Recents is detected. Coverage varies by OS; it does not disable Recents universally. Device Admin adds possible uninstall friction. Force-stop, grant removal, OS termination, safe mode, cleared data, uninstall, ADB/root and factory reset can bypass enforcement. FocusGate supports self-control, not a determined attacker.

To uninstall after release, disable chosen blockers/protections, deactivate Device Admin in Android if active, and disable Accessibility if wanted. Then uninstall normally. Turn off Settings protection first if it blocks the route. These routes still need phone acceptance.

## Backup, privacy and support

**More** groups health, phone setup, display/feedback, backups, FAQ, developer tools and support. Expand secondary help when needed.

Export writes versioned JSON through Android's file picker. Choose a local folder; a cloud-backed file provider may use its own network. Exports include rules, groups, schedules and display preferences. They exclude passwords/verifiers, active sessions, usage, grants and logs.

Import is unlocked-only, with merge/replace preview and validation. It cannot unlock a session, restore grants or transfer usage time. Diagnostics are optional, off by default, bounded and exported separately. They do not record raw URLs, page content, messages, passwords or browsing history.

FocusGate has no Internet/network-state permissions, accounts, ads, telemetry, VPN or cloud sync. Build tools may download dependencies. Other apps control their own network activity.

Donation network/address placeholders are empty until the maintainer supplies verified details. Copy/payment/QR controls stay hidden without an address. QR rendering is deferred. No payment address is invented; no feature requires a donation.

**More → About & support** shows the [GitHub repository](https://github.com/pesterevnikita/app1) and **Copy repository link**. Telegram feedback says **coming soon** until the maintainer sets a verified public link in `support/SupportConfig.kt`. A configured link gets a copy button. Copying is local. Paste into another app yourself to visit; it may use its own network and account. FocusGate sends no feedback automatically.

## FAQ

**Why is an app still blocked after expiry?** Expiry unlocks settings. Disable its blocker if you want access.

**Why can I use 30 minutes around an hour boundary?** Each hour has a fresh allowance. Set a continuous cap if you want a break across that boundary.

**What if I forget the password?** OR and Timer only release at the deadline. Password only has no timer. Legacy AND still needs the password after the deadline. Change/removal require the current password. Import cannot reset a lock.

**Do I set a password for each lock?** No. It stays saved. Each timed Start creates a new deadline.

**Why was a website allowed?** Its URL may be unknown or unavailable. Website rules allow unknown URLs. A browser app rule can add a broader restriction.

**Why does background audio continue?** Home redirection does not terminate the other app or stop its audio.

**Does Device Admin prevent uninstall?** It may add deactivation before uninstall. It is not a guaranteed uninstall block.

**What if Accessibility stops?** Reopen FocusGate, check health and repair the grant manually. Release first if Settings protection obstructs repair. Autostart/battery setup helps availability but cannot guarantee it.

**Is browsing sent anywhere?** FocusGate has no runtime networking or browsing-history collection. Known URL text is used briefly for local matching. Read current manifest checks and phone evidence before treating the development build as accepted.
