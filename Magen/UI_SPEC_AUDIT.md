# Magen UI specification audit — source of truth

This audit is based on the archived conversation `hershaleh/Magen-Chat-Archive/gpt` (questions 1–283) and the continuation transcript through the later decisions (questions 284 onward). It is a requirements checklist, not a claim that the current UI implements these features.

## Product-wide requirements

- [ ] Preserve the existing Android project, package IDs, signing/update compatibility, storage keys, Magen/Keeper separation, and API 23 minimum where possible.
- [ ] Hebrew and English; switching language applies immediately across the app; complete RTL/LTR layout.
- [ ] Light, dark, and follow-system themes; apply immediately.
- [ ] Global animation preference; respect Android reduced-motion/animation settings.
- [ ] Small-screen, landscape, large-font, high-contrast, and TalkBack support.
- [ ] Settings save immediately and show success/failure; never display an unsaved change as successful.
- [ ] Distinguish saved preference from capability verified from Android; show unsupported/unverifiable states honestly.

## Main navigation

- [ ] Bottom navigation, in this order: Home, Apps, Times, Protection, More.
- [ ] Navigation selection persists across activity recreation.
- [ ] Advanced settings remain a separate screen, not mixed into ordinary settings.
- [ ] Times/profiles are a distinct area.
- [ ] Notification-screen settings shortcut routes to the main Settings > Notifications screen.

## Home

- [ ] Prominent circular protection on/off control.
- [ ] Protection state is independent of app/rule-list loading; a loading failure must not change the protection control state.
- [ ] Fixed-height notifications/attention card with unread count; content refreshes automatically.
- [ ] Summary of managed/blocked apps and key protection state without duplicating a separate fault summary.
- [ ] Explicit, on-demand protection check with detailed results; only verified state is called active.
- [ ] Quick route to app management and protection diagnostics.

## Apps and per-app configuration

- [ ] Installed-app list, including non-launcher apps where Android permits; distinguish user/system apps.
- [ ] Search, useful filters, counts, multi-select, refresh, and new-app indication.
- [ ] App detail screen split into tabs: Blocking method and Additional settings.
- [ ] Per-app methods and their mutual compatibility are explained; full block and code-required modes are mutually exclusive, while time limits and allowed-time windows can compose.
- [ ] App icon/name; app-level blocking state must distinguish configured, currently not applicable, and enforcement failure.
- [ ] Reinstall recognition by package/signature and restoration of prior settings where safe.
- [ ] Blocked app screen: “This app is blocked”, original app icon and unmodified app name centered beneath it; large but proportionate icon; fallback to package name if app label is unavailable; do not invent a fallback icon.
- [ ] Block screen follows selected theme, language and RTL/LTR; “Protected by Magen” is translated.
- [ ] Block screen cannot be dismissed with Back or outside tap. One small Home icon+label button at the top logical corner returns to launcher without confirmation and without recording that action.
- [ ] Short entrance/exit animation, respecting reduced-motion; support rotation, font scaling, high contrast and TalkBack. Avoid duplicate blocking activities/loops.
- [ ] A blocked app already in foreground is blocked immediately. Detect relaunch via notifications, links, background actions and Recent Apps as far as Android permits. The persistent Magen notification can open Magen directly without lifting the previous app's block.
- [ ] Re-enforce after reboot and attempt permitted service recovery; never claim active enforcement without verification.

## Times, quotas and schedules

- [ ] Daily usage counts foreground time only.
- [ ] Personal quota per app and shared quota groups; each app belongs to at most one group, while personal and group quotas may coexist.
- [ ] Group-only, personal-only, or both are supported. Whichever active quota is exhausted blocks the app.
- [ ] Quota 0 means full-day block. A quota change applies immediately without erasing usage; lowering below consumed time blocks immediately.
- [ ] Daily reset at local midnight; other rules (e.g. allowed-time windows) continue to apply.
- [ ] Immediate blocking at exhaustion, including while the app is open; one configurable warning, default 5 minutes before exhaustion.
- [ ] Personal/group quotas can be paused independently; paused usage is not counted. A pause on one quota does not suspend another.
- [ ] Moving an app between groups applies immediately: already-consumed time remains in the old group; new usage counts in the new group.
- [ ] Weekly schedules, multiple time windows, overnight windows, breaks and date-specific exceptions; conflicts resolve conservatively (blocking wins).
- [ ] Times UI must reflect the latest approved scope. Do not silently substitute fake profile/schedule controls for previously deferred features.

## Shabbat and Jewish holidays

- [ ] Separate “Block on Shabbat and Jewish holidays” feature, not generic holiday quota rules.
- [ ] Applies to managed apps only, not the whole device; Magen remains viewable in read-only mode and settings cannot be changed during the block.
- [ ] Applies on Shabbat and Jewish holidays when work is prohibited; not Chol HaMoed.
- [ ] Hebrew calendar, Israel custom, configurable second-day-in-Israel behavior, location-based times, manual minute offsets.
- [ ] Precomputed local calendar so enforcement works offline; warn before stored calendar coverage expires.
- [ ] Shabbat/holiday block overrides ordinary quotas, pauses and schedules. Restart during the block re-applies it; changing location does not cancel a block already in progress.
- [ ] If enforcement cannot start, detect promptly, retry, notify and log; do not show a false active state.

## Protection and advanced protection

- [ ] Play Store and APK installation blocking with honest per-method capability/status.
- [ ] Android Settings catalog (existing 58 entries), custom title/keyword rules, search and selection; disclose that title/accessibility matching is best-effort and device/language dependent.
- [ ] VideoStop controls and exclusions; disclose detection and battery limitations.
- [ ] Regular/strong modes with clear comparison; show Device Admin and Device Owner separately and verify actual Android state.
- [ ] Advanced protection is a separate screen with permission/owner status, explanations, device limitations, integrity checks, and recovery route.
- [ ] Detect permission/service/owner changes on launch, resume, reboot and reasonable periodic intervals; attempt safe recovery, then notify/log failures.
- [ ] Do not modify ownership transfer, Keeper, release, uninstall or recovery logic as part of UI-only work.

## Notifications, diagnostics and event log

- [ ] Local persistent event log, surviving reboot; default retention forever, optional retention period, manual clear-all (not individual deletion).
- [ ] Event severity: Critical, High, Medium, Low; event state: Active, In progress, Resolved, Unresolved.
- [ ] Repeated identical faults are grouped with occurrence count, timestamps and repair history.
- [ ] Mark read/unread, mark all read and mark all unread; “needs attention” is system-derived, never manually set.
- [ ] “Mark all read” applies to all events, including filtered-out events.
- [ ] Per-severity read behavior configurable; critical unread items remain prominent by default.
- [ ] Filter by type/severity/state, sort by time, search keyword/app/error.
- [ ] Event detail shows exact time, severity/type, plain-language explanation, technical details, repair guidance and history.
- [ ] Safe one-tap repair only when reliable; warn/confirm consequential actions, verify after repair, and show failure details.
- [ ] Detect external fixes and mark resolved only after verification.
- [ ] Export a reviewable diagnostic/event report; no secrets or PIN/recovery material.
- [ ] Do not add a separate fault-summary widget to Home; show faults in notifications and diagnostics.

## More, settings, backup and recovery

- [ ] PIN change after current PIN verification; recovery-code create/renew flow; biometric option when supported; failed-attempt delay.
- [ ] Permission center with explanation, direct Android Settings link, and re-check on return.
- [ ] Guides for ordinary/strong mode, permission setup, troubleshooting, Keeper, and safe release.
- [ ] Settings grouped and navigable: Notifications, Appearance/language, privacy, animation, diagnostics, backup/restore, app info/credit.
- [ ] Backup supports manual/automatic, selectable contents, selective restore, schema/checksum validation, safety backup before restore, retention management and reminders.
- [ ] Never include PIN, hashes, salts, recovery code, live unlock/trip windows, or claim Android Device Owner/system restrictions can be restored from a backup.
- [ ] Release flow keeps “Disable strong mode (required for uninstall)” distinct from “Approve uninstall”; show verified Android result and safe recovery. Do not change this flow in the UI-only pass.
- [ ] Credit “הערשלה” and the agreed Mitmachim link.



## Settings information architecture and visual organization — added requirement

The current “More” screen is not an acceptable final settings experience. Settings must be redesigned as a proper Android-style settings hub, with category landing page and separate category screens. Do not simply add more cards to one long scrolling page.

### Settings hub

- [ ] A dedicated Settings entry/hub, visually distinct from operational screens (Home, Apps, Times, Protection).
- [ ] Category list with a clear icon, concise title and one-line current summary for each category.
- [ ] Tapping a category opens a dedicated screen with a consistent top app bar, back navigation, category title and grouped options.
- [ ] Consistent ordering, typography, row heights, dividers, icon treatment and spacing throughout.
- [ ] Compact, information-dense layout: avoid oversized cards, excessive vertical padding, repeated explanatory paragraphs and large empty gaps. Use short summaries and put longer explanations behind a detail/help action where appropriate.
- [ ] Related options stay together; advanced or destructive options are visually separated and not mixed into everyday preferences.
- [ ] Use switches only for immediate on/off preferences; use radio/segmented choices for mutually exclusive modes; use a clear secondary screen for multi-step configuration; use confirmation dialogs for consequential actions.
- [ ] Every row communicates its current value/state where applicable. Disabled/unsupported options explain why and are not styled as active.
- [ ] No duplicate settings in multiple places unless the secondary location is a deliberate shortcut to the same source of truth.
- [ ] Search settings by name/keyword, with results opening the exact category/setting.
- [ ] Responsive to small displays, landscape, font scaling, RTL/LTR, TalkBack and reduced motion.

### Required top-level settings categories

1. **התראות (Notifications)** — a first-class category, not buried under another page:
   - [ ] Notification permission/status and shortcut to Android's app notification settings.
   - [ ] Persistent protection-status notification preference and its truthful capability/status.
   - [ ] Event/diagnostic notification preferences, including severity/read behavior already specified in the event-log section.
   - [ ] Quota warning preferences (default 5-minute warning; user-configurable where supported).
   - [ ] Sound/vibration and other notification presentation options only where Android supports them.
   - [ ] Explain which notifications are essential to protection and cannot safely be disabled, if any; do not imply a disabled Android permission can be bypassed.
   - [ ] A preview/example and a clear route to the in-app notification/event center.

2. **מראה ושפה (Appearance & language)**:
   - [ ] Light / dark / follow system.
   - [ ] Hebrew / English, immediate application and correct RTL/LTR.
   - [ ] App-wide animation preference, respecting Android reduced-motion settings.
   - [ ] Any other appearance options only if confirmed in the archived specification; do not invent cosmetic toggles.

3. **אבטחה וכניסה (Security & sign-in)**:
   - [ ] Change PIN (verify current PIN first).
   - [ ] Recovery code creation/renewal and clear one-time display/copy guidance.
   - [ ] Biometric unlock only when available and supported.
   - [ ] Failed-attempt delay / lockout status.
   - [ ] Keep release/uninstall controls out of routine security preferences; they remain in their dedicated protected flow.

4. **הרשאות והגנת מערכת (Permissions & system protection)**:
   - [ ] Permission center with current status, why each permission is needed, Android Settings deep link and re-check on return.
   - [ ] Device Admin and Device Owner shown separately, based on verified Android state.
   - [ ] Accessibility, usage access, notification access, battery/background operation and other actually-required capabilities shown only if relevant to the build/device.
   - [ ] Advanced protection diagnostics and safe recovery entry point, without changing ownership/release/Keeper logic.

5. **פרטיות (Privacy)**:
   - [ ] Group only privacy options that are actually specified and implemented.
   - [ ] Explain diagnostic data handling and what is included in exports/backups.
   - [ ] Never expose PIN, recovery material, hashes/salts or sensitive internal state.
   - [ ] Do not add telemetry, cloud sync or data collection preferences unless the product actually contains those features and the archived decisions authorize them.

6. **אבחון ויומן אירועים (Diagnostics & event log)**:
   - [ ] Event center entry, filters/search, read/unread actions, severity and state.
   - [ ] Retention policy and export options from the event-log specification.
   - [ ] Explicit diagnostic/protection check and understandable results.
   - [ ] Technical details and safe repair actions remain in dedicated detail screens.

7. **גיבוי ושחזור (Backup & restore)**:
   - [ ] Manual/automatic backup, selectable contents, restore preview and selective restore.
   - [ ] Schema/checksum validation and safety backup before restore.
   - [ ] Retention management/reminders.
   - [ ] Clearly list excluded secrets and non-restorable Android system ownership/restrictions.

8. **מידע על האפליקציה (About Magen)**:
   - [ ] Version/build information, app identity, credits (“הערשלה”) and agreed Mitmachim profile link.
   - [ ] Help/guides and support/contact details only when verified in the project/spec.
   - [ ] No fake update checker or unsupported links.

### Settings completeness and quality gate

- [ ] Inventory every existing preference, setting, control and route in the current code and archived specification before moving or removing anything.
- [ ] Map each setting to exactly one category and one canonical state owner; preserve existing storage keys and data formats.
- [ ] Identify missing settings explicitly; do not silently omit an existing capability because the current UI does not expose it.
- [ ] Identify duplicate, obsolete, contradictory or placeholder controls and resolve them against the archived decisions.
- [ ] Verify every row and action: opens the right screen, saves the intended value, survives app restart, reflects the actual current value, and has correct disabled/error states.
- [ ] Verify category navigation/back stack, settings search, language switch, RTL/LTR, light/dark/system theme, compact spacing and large-font layout.
- [ ] Do not claim the settings redesign complete until the inventory is reconciled and every specified setting has a real destination and truthful behavior.


## Explicitly deferred / optional unless the archived decisions say otherwise

Intruder photo, alternate launcher identity/icon, remote approval, Wi-Fi/location rules, home-screen widget, website/DNS filtering, and other ideas initially listed as future/P2/P3 must not be presented as implemented merely because they appeared in brainstorming.

## Current PR #5 — initial audit

The current branch is a partial UI scaffold, not a full implementation:
- Home is still a status/summary layout; it does not match the agreed circular protection control plus fixed-height notifications card.
- Several new screens use local-only demo state. `SpecToggle`, `SpecField`, and multiple buttons are not connected to a real model or persistent preview state.
- Security log and backup screens explicitly contain “not connected” placeholders and no event list/detail flows.
- Quota, schedule and Shabbat screens are skeletal examples, not complete management workflows.
- The app detail flow and several required per-app controls still need comparison against the original archived decisions.
- Global theme/animation behavior and complete English/RTL parity remain incomplete.
- Unit-test failure around `hasAnyRule` remains separate from APK compilation and must be fixed before calling validation complete.

This document must be updated as each item is implemented and tested. A screen title or inert switch does not count as implementation.
