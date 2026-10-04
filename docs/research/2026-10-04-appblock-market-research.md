# Android blocker research: AppBlock and alternatives

**TL;DR:** FocusGate follows a credible Android approach: observe apps and supported browser URLs, enforce ordinary rules, and lock weaker edits separately. AppBlock documents the same OEM/Accessibility problems we encountered. Our largest release gaps are safe Settings/Recents behavior, clear repair guidance, browser coverage and long idle tests. Useful future ideas are allowance warnings, temporary password-approved pauses, launch limits and better rule explanations. Keep the no-network requirement. The ideas below are recommendations, not approved implementation tasks.

Research date: **2026-10-04**. Scope: Android self-control apps and relevant open-source implementations. FocusGate baseline: commit `e75115f`, with short lifecycle acceptance recorded in the [phone results](../testing/phone-acceptance.md). Current delivery work remains in the [roadmap](../roadmap.md).

## Evidence and scope

- Vendor help pages establish documented behavior, not a guarantee on our Xiaomi 11T.
- Public source inspection establishes what the inspected files contain. It does not establish the permissions of every published APK or its behavior on a device.
- FocusGate's measured results come from our acceptance record. Proposed benefits and priorities below are our analysis.
- Android and iOS features differ. This report uses Android-specific evidence where available.
- No competitor APK was installed, no proprietary APK was decompiled, and no competitor code was copied. Public documentation cannot reveal AppBlock's exact scheduling, polling, password-storage or restart implementation.
- Some old AppBlock help-category URLs now return empty pages while search results retain older content. The report prefers current linked articles. Prices, free-tier limits and browser support can change.

## 1. What AppBlock tells us about its Android mechanism

AppBlock documents Usage Access for app detection, Accessibility for website/advanced blocking, overlay permission for its block screen, and OEM battery/autostart setup. That supports an observation-and-intervention model; it does not reveal the exact internal detection loop. [Android troubleshooting](https://appblock.app/appblock-is-not-working-on-android/).

Its website rules use Accessibility and configured URLs or keywords. Keywords can apply to the host or the entire URL. AppBlock publishes a supported-browser list and offers an option to block unsupported browsers as whole apps. These are browser-specific capabilities, not evidence of universal traffic interception. [Website rules](https://appblock.app/how-to-block-websites-on-android/), [supported browsers](https://appblock.app/which-browsers-are-supported-by-appblock/).

AppBlock's customization guide describes using Device Admin for uninstall resistance. Its Strict Mode also offers Settings and Recents restrictions. These public claims do not establish device-owner powers or resistance to ADB/factory reset. FocusGate's ordinary-admin threat model remains appropriate. [Customization](https://appblock.app/how-to-customize-my-appblock-2/), [Strict Mode](https://appblock.app/how-to-use-strict-mode-2/).

**Our inference:** the basic product direction matches. FocusGate makes a deliberate implementation choice: Accessibility observations and Home redirection, with no Usage Access requirement for its own foreground accounting. AppBlock's overlay and usage-history features explain some of its additional permissions. More permissions are not automatically a reliability improvement.

AppBlock's permission guide maps its optional capabilities as follows. These are the vendor's stated purposes, not a manifest audit. [Permission guide](https://appblock.app/why-do-i-have-to-grant-so-many-permissions/).

| Permission / capability | Vendor-stated purpose |
| --- | --- |
| Usage Access | Foreground app detection and usage history |
| Accessibility | Websites, Settings, split-screen and selective content |
| Display over other apps | Blocking screen |
| Alarms/reminders | Schedule and reminder timing |
| Post notifications | Status and reminders |
| Notification Access | Suppress other apps' notifications |
| Location | Location and Wi-Fi conditions |
| Device Admin | Uninstall friction in Strict Mode |

FocusGate needs neither traffic interception nor another app's exact permission set to implement its present rules. Additional capabilities should request permissions only when the user chooses those capabilities.

## 2. AppBlock features compared with FocusGate

“Implemented” below refers to our code and roadmap. Device coverage can still be partial.

| Capability | AppBlock's documented behavior | FocusGate position |
| --- | --- | --- |
| Ordinary schedules | Time/day rules and multiple schedules. Overlapping schedules keep a target blocked if any applicable schedule denies it. [Conditions](https://appblock.app/can-i-combine-blocking-conditions-2/) | Implemented; remaining overlap/device cases are R3 |
| Hourly/daily budgets | Usage caps, optional time windows and configurable daily reset time. [Usage limits](https://appblock.app/how-usage-limits-work-on-android/) | Shared clock-hour/day groups implemented; custom daily reset time absent |
| App-open limits | Daily/hourly launch-count schedules. [Launch count](https://appblock.app/how-to-create-a-launch-count-schedule/) | Not implemented; useful for brief repeated checking |
| Combined group budget | Reviewed help establishes hourly/daily caps, but not the exact combined multi-app semantics of our preset | Our Chrome/Edge/Telegram/Ozon group shares one 15-minute allowance; do not claim AppBlock lacks this without direct confirmation |
| Maximum continuous session | Exact continuous-cap-plus-uninterrupted-break semantics were not established by reviewed AppBlock docs | Implemented and short phone cases passed; kept separate from hourly reset |
| Quick Block / Pomodoro | One-off timed blocks, repeated focus/break intervals and Quick Settings tile. [Quick Block](https://appblock.app/how-to-use-quick-block-2/) | Recurring/editable rules exist; no separate Quick Block, Pomodoro or tile |
| Configuration commitment | Timer, PIN, charger, cooldown, schedule-following and partner approval; docs also list combined methods. [Strict Mode](https://appblock.app/how-to-use-strict-mode-2/) | Password, Timer and Password OR timer; stronger-only additions. New AND locks deliberately excluded |
| Website matching | URLs and host/full-URL keywords, with many browser adapters. [Website rules](https://appblock.app/how-to-block-websites-on-android/) | Host rules and bounded regex; Chrome/Edge adapters; unknown URLs allowed |
| Selective content | Shorts, Reels/Stories and selected Snapchat/WhatsApp feeds using Accessibility. [Content blocking](https://appblock.app/how-to-block-instagram-reels-youtube-shorts-or-snapchat-stories-on-android/) | Whole app/site blocking; no in-app feed detector |
| Notification blocking | Can suppress selected apps' notifications while still allowing the apps. [Notification rules](https://appblock.app/how-can-i-block-only-notifications-from-selected-apps/) | Absent; Home redirection leaves notifications and background media running |
| Allowlists / location | Advertises essential-app allowlists and location-based schedules. [Android product](https://appblock.app/android/) | Blocklists only; no location permission or location rules |
| New-app coverage | Can automatically include newly installed apps. [How-to overview](https://appblock.app/help/android/how-to-use/) | Saved package targets; no automatic new-app policy |
| Useful block feedback | Reason, custom message, attempts counter, optional delay and different visual styles. [Customization](https://appblock.app/how-to-customize-my-appblock-2/) | Custom message and quota cards; no complete “which rules denied me / available again when” view |
| Limit reminders | Supports pre-limit and schedule notifications. [How-to overview](https://appblock.app/help/android/how-to-use/) | Optional remaining-time notification; no configurable pre-limit warning |
| Business model | Separate free and premium capabilities, including restrictions on schedules and Strict Mode. [Free/premium comparison](https://appblock.app/free-vs-premium-appblock-features-on-android/) | No purchase/account requirement; blank donation placeholders |

**Important distinction:** a one-off block timer decides when blocking ends. Restricted Mode's timer decides when configuration can be edited. A usage budget decides how much foreground use remains. FocusGate should keep those concepts separate even if Quick Block is added later.

## 3. AppBlock's own limitations are useful test cases

| Vendor-reported issue | What we should learn |
| --- | --- |
| Accessibility can stop on Xiaomi/Huawei/Oppo; vendor recommends autostart, battery exclusions and Recents pinning. [Accessibility repair](https://appblock.app/my-accessibility-keeps-turning-off-what-can-i-do-2/) | Our Xiaomi setup is justified. Report connection and enforcement health, not only a configured grant. Finish unplugged long-idle acceptance |
| Settings blocking can interfere with authenticators. [Authenticator issue](https://appblock.app/my-authenticator-is-blocked-but-i-didnt-block-it-2/) | Test authentication handoffs and essential apps before long locks. This is an AppBlock report, not an observed FocusGate failure |
| Recents protection can disrupt Home navigation. [Home/Recents issue](https://appblock.app/why-is-my-ui-home-and-recent-apps-blocked-2/) | Keep conservative recognition. Test both normal navigation and repair; do not classify the whole launcher as Recents |
| Time-zone changes can extend a lock unexpectedly. [Time-zone issue](https://appblock.app/time-zone-changes-affecting-blocking-2/) | Keep lock durations independent of civil-time schedules. Add time-zone/DST/reboot tests and make the schedule-zone policy explicit |
| Vendor acknowledges a bug where apps remain blocked after pause/end. [Troubleshooting](https://appblock.app/help/android/troubleshooting-and-permissions/) | Verify expiry on a static foreground app, wake and restart. A displayed expired timer alone is insufficient |
| Floating-window behavior is not reliable across all devices in reviewed FAQ. [Android FAQ](https://appblock.app/help/android/faqs/) | Test actual HyperOS floating windows, PiP and split-screen. A competitor feature claim is not a universal platform guarantee |

**Our inference:** the biggest missing work is acceptance and repair UX, not a hidden permission that makes every blocker unkillable. Today's reboot/process tests strengthen confidence, but do not replace overnight, permission-loss or system-UI testing.

## 4. AppBlock privacy differs from our requirements

AppBlock's current Google Play privacy policy says core rules, screen processing and detailed statistics are mainly local. It also describes account/cloud/support features, analytics and other SDKs, server-side installed-app categorization, package names in some product events, and narrow crash-report cases containing a blocked page address. These are policy disclosures, not observations of traffic from the user's installed copy. [App privacy policy](https://appblock.app/privacy-policy-google-play/).

Partner approval requires internet access, and its help says emergency codes are server-validated. [Unblocking help](https://appblock.app/help/android/strict-mode-and-unblocking/).

**Decision for FocusGate:** keep local JSON backups and trusted-person passwords. Avoid cloud approval, analytics, automatic app categorization services and server recovery. An offline trusted person can still authorize a release by entering the saved password. We should not add AppBlock's emergency bypass flow to our confirmed password behavior.

## 5. Other commercial products

These comparisons describe documented Android features. None was tested on our phone in this research session.

| Product | Useful ideas / documented behavior | Fit and limits |
| --- | --- | --- |
| Stay Focused | Profiles, time limits, timed Strict Mode, planned breaks, selective short-content blocking. Its Android FAQ acknowledges OEM restart and multi-window limits. [Product/FAQ](https://www.stayfocused.me/) | Broad UX comparison. Its policy includes analytics/advertising and usage/device data processing, so it differs from no-network FocusGate. [Privacy](https://www.stayfocused.me/privacy-policy-play-store) |
| Lock Me Out | App/website blocklists or allowlists; time, app-open and device-unlock triggers; several break models; password release and stronger-only locked edits. [Detailed guide](https://www.teqtic.com/lock-me-out) | Especially relevant to our policy model. Vendor says usage/screen processing stays local; this does not establish absence of network permission. [Privacy](https://www.teqtic.com/lockmeout-privacy-policy). Browser/OEM fixes in its [changelog](https://www.teqtic.com/lockmeout-changelog) show ongoing maintenance |
| ScreenZen | Opening delays, increasing waits, short sessions, daily/time rules and customizable reflection prompts. [Android developer listing](https://play.google.com/store/apps/details?id=com.screenzen) | Good friction UX ideas. Current policy allows self-hosted analytics and optional cloud features. Exact Android strict-release/browser coverage was not established. [Privacy](https://screenzen.co/policies/privacy-policy) |
| one sec | Breathing/intent delay. Android blocking tutorial describes manual timed blocks, opt-in recurring blocks and strict blocks; labels blocking beta. [Android block tutorial](https://tutorials.one-sec.app/en/articles/3311298), [intervention customization](https://tutorials.one-sec.app/en/articles/3310978) | June 2026 table marks general Android website blocking unavailable; August Adult Content Detox is a separate scoped beta. Do not confuse it with full site-rule parity. [Feature table](https://tutorials.one-sec.app/en/articles/3036418), [detox beta](https://tutorials.one-sec.app/en/articles/3035394). “Offline by default” permits optional online features. [Privacy](https://one-sec.app/privacy/) |
| Freedom | Cross-device/account workflow, app/site Accessibility blocking, Locked Mode and optional limited breaks. Ordinary admin adds launcher-uninstall friction; Settings uninstall remains possible. [Android setup](https://support.freedom.to/en/articles/4523583-how-to-install-freedom-for-android), [options](https://support.freedom.to/en/articles/6350161-freedom-for-android-app-options-explained) | Account workflow differs from our offline model. Use Android evidence: do not transfer its iOS VPN mechanism to Android. OEM problems are acknowledged. [Troubleshooting](https://support.freedom.to/en/articles/4529927-freedom-for-android-troubleshooting) |

**Our inference:** products cluster around hard blocking, configuration commitment and habit friction. FocusGate already covers the first two. A breathing screen or launch delay is an optional additional tool; it should not dilute hard blockers or become mandatory UI.

## 6. Open-source projects and source lessons

### TimeLimit.io

The official upstream is on **Codeberg**, with a separate older Open TimeLimit project. Its website documents local and connected modes, schedules, extra time and OEM troubleshooting. F-Droid build metadata identifies the current upstream and GPL-3.0-only license. Current Codeberg source retrieval failed, so manifest/service internals were not inspected. [Official site](https://timelimit.io/en/), [build metadata](https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/io.timelimit.android.aosp.direct.yml).

**Our inference:** a useful established quota/setup comparison. Optional device-owner and connected operation are outside our scope. Separate health/status from policy; do not infer that its local mode implies our exact permission model.

### Mindful

Repository: `akaMrNagar/Mindful`; LICENSE contains GPL version 2. Its inspected source manifest declares internet and VPN capabilities alongside tracking/overlay services. Thus a README “offline” description is not equivalent to a no-network manifest. [Repository](https://github.com/akaMrNagar/Mindful), [license](https://github.com/akaMrNagar/Mindful/blob/main/LICENSE), [manifest](https://github.com/akaMrNagar/Mindful/blob/main/android/app/src/main/AndroidManifest.xml).

Its Accessibility service separates browser, content and device-feature managers. The inspected dispatch guard and issue #265 show a protection-combination concern when content blocking is disabled. [Service](https://github.com/akaMrNagar/Mindful/blob/main/android/app/src/main/java/com/mindful/android/services/accessibility/MindfulAccessibilityService.kt), [issue #265](https://github.com/akaMrNagar/Mindful/issues/265).

**Our inference:** keep independent Settings/Recents enforcement independent of app/site blockers. FocusGate already has a regression for independent protection eligibility; retain it and add phone acceptance. Separate adapters help maintenance. An `onDestroy()` fallback alone cannot prove abrupt-death recovery.

### Nudge

Repository: `astraedus/nudge`; GPL version 3. The inspected source manifest declares no INTERNET permission and disables backup. It also declares optional camera and secure-settings permissions, so it is broader than FocusGate and some README permission wording is stale. This was source inspection, not a merged release-manifest or APK audit. [Manifest](https://github.com/astraedus/nudge/blob/main/app/src/main/AndroidManifest.xml), [license](https://github.com/astraedus/nudge/blob/main/LICENSE).

The inspected Accessibility service leaves feedback interruption empty and guards cleanup after partial initialization. Issue #57 reports a fixed teardown crash with maintainer-described tests, and separately identifies a foreground-service startup refusal. The teardown fix does not establish that the separate refusal was resolved. Those are upstream reports, not our device results. [Service](https://github.com/astraedus/nudge/blob/main/app/src/main/java/com/astraedus/nudge/service/NudgeAccessibilityService.kt), [issue #57](https://github.com/astraedus/nudge/issues/57).

**Our inference:** add partial-startup/rapid-toggle teardown checks and visible foreground-promotion failure guidance. Reuse ideas, not assumptions or source code. License review would be needed before incorporating third-party code; none was incorporated here.

## 7. Recommended priorities for FocusGate

These are candidate ideas. They do not change the accepted spec or add commitments automatically.

| Priority | Candidate | Why / constraints |
| --- | --- | --- |
| First-release work | Actionable setup health | Distinguish grant enabled, service connected, foreground promotion and last observed activity. Explain the repair action. Do not claim automatic verification of every OEM switch |
| First-release work | Explain the effective block | Show denying rule(s), the next allowance/schedule transition and break time. Explain shared time across apps and overlapping denials. Build on R9; keep screen text/URLs out of diagnostics |
| First-release tests | Essential-app / system-UI safety | Expand R7 to authentication handoffs, network setup, calls, navigation, keyboard and permission dialogs. Test narrow Settings and Recents before whole-Settings mode |
| First-release tests | Clock and idle behavior | Extend R3/R12 with midnight, time-zone/DST changes, wake after deadline, low-battery state and unplugged overnight use. Keep monotonic lock duration separate from civil schedules |
| Small future feature | Configurable allowance warning | Warn once at a chosen remaining time. Optional notification, no new Usage Access permission. Avoid repeated warnings on every Accessibility event |
| Small future feature | Related website suggestions | AppBlock prompts about related websites when selecting apps. [How-to overview](https://appblock.app/help/android/how-to-use/). We could offer a small local, editable mapping for custom blockers, with no online lookup. Existing YouTube/Instagram presets already cover app plus web |
| High-value future feature | Password-authorized temporary pause | A trusted person permits a bounded pause, with automatic return to enforcement. Persist the pause and original lock; process death must not turn a short pause into permanent weakening. Already deferred in the roadmap |
| Future feature | Hour/day app-open limit | Useful for repeated two-second checks that barely consume time. Define a real foreground visit and debounce window events. A denied attempt must not count as successful use |
| Future feature | Quick Block and optional tile | One-off sessions without editing long-term rules. Preserve the separate configuration lock and never let a tile weaken an active lock |
| Future feature | Custom daily reset / duplicate blocker | Helpful for night shifts and repeated setups. Reset changes need transactional guards; duplication must not accidentally bypass an existing denial |
| Advanced optional feature | Allowlist / new-app policy | Can close normal install-and-switch workarounds. First handle dialer, launcher, FocusGate, authentication and system UI safely. Do not auto-block every new package by default |
| Advanced optional feature | Opening delay / selective feeds | Delay can break automatic checking; selective feeds can preserve useful messaging. Both need separate designs. Feed adapters are app-version/language dependent; unknown screens should remain allowed under our current policy |

For the user's current presets, the best near-term payoff is reliability, clear remaining-time/reason UI and an allowance warning. Selective Shorts/Reels blocking is optional: the requested baseline already blocks YouTube/Instagram completely.

Avoid adding location, Wi-Fi inspection, notifications access, exact alarms or a second tracking service solely because another app uses them. Each extra capability needs a concrete benefit and measured need. Cloud approval, VPN, network categorization and device-owner management conflict with current requirements.

## 8. Tests suggested by the research

| Test | Expected FocusGate behavior / open question |
| --- | --- |
| Settings/Recents enabled with ordinary rules disabled | Independent protections still work; only recognized supported routes are blocked |
| Authenticator or banking authentication handoff | Essential authentication remains usable according to the selected protection mode; record actual HyperOS behavior |
| Window/tab/keyboard churn in a quota app | One foreground interval billed once per group; no duplicate launch counting if that feature is later added |
| Blocked page with hidden URL bar, preview or WebView | Unknown URL allowed unless an independent app rule denies it; no invented URL confidence |
| App uninstall/reinstall or new browser installation | Existing package rules still apply when the package returns. Refresh app-picker/missing-app state; distinguish this from an unimplemented automatic new-app policy |
| Overnight, deadline expiry, hour/day transition | Sleep does not consume foreground time; expired configuration lock releases on reconciliation; enabled rules remain enabled |
| Time-zone/DST change around a lock and schedule | Lock duration does not extend accidentally. Verify/document which zone schedules and quota buckets use during and outside a lock |
| Partial startup, rapid Accessibility toggles, foreground promotion refusal | No teardown crash; truthful degraded state and a usable manual repair path |

## 9. What remains unknown

- AppBlock's exact shared-budget/session semantics, internal clocks, password hashing, persistence/restart implementation and installed-copy network behavior.
- Whether all AppBlock browser adapters and advertised protections work on this exact HyperOS version. Its public lists are not our acceptance evidence.
- Exact Android strict-release semantics for some alternative products, and features that differ between beta/release builds.
- TimeLimit's current source-level service implementation, because its upstream fetch was unavailable.
- The user's detailed AppBlock frustrations. A follow-up can change priorities.

Do not call FocusGate stronger than a competitor based on this research. We can substantiate its no-network build checks, ordinary shared budget and our measured phone cases. Competitor reliability requires comparable device tests.
