# Appause — Development Progress

## Phase Overview

| Phase | Name | Status | Notes |
|-------|------|--------|-------|
| 0 | Project Setup | ✅ Done | Docs + project structure + Gradle config |
| 1 | Data Layer | ✅ Done | Room entities, DAOs, Database, DataStore, Repository. Build passes. |
| 2 | App Query | ✅ Done | PackageManager wrapper, AppInfo, AppQueryService, AppSelectViewModel |
| 3 | Navigation + Theme | ✅ Done | Material 3 theme (Color/Type/Theme), NavGraph, routes |
| 4 | Home Screen | ✅ Done | Service status card, master toggle, group list, FAB |
| 5 | Group Editor + App Select | ✅ Done | Group form, app picker with search + multi-select |
| 6 | AccessibilityService | ✅ Done | Real event detection, InterceptionManager, bypass logic |
| 7 | Pause Screen | ✅ Done | Countdown UI with progress ring, cancel/continue, logging |
| 8 | Settings + Polish | ✅ Done | Settings icon in Home, back handler, onDestroy cleanup |

## All Phases Complete — MVP Ready

### Phases 6–8 Completed (implemented together)
- InterceptionManager: singleton bypass state with startBypass/clearBypass/isBypassed, 5-min auto-expiry timeout
- AccessibilityService: real event detection with 7-step filter chain (enabled? → self? → system? → bypassed? → duplicate? → in group? → INTERCEPT)
- PauseActivity: full Compose UI with app icon, name, prompt, animated countdown ring + number, Cancel (→ Home) and Continue buttons
- BackHandler: back press acts as Cancel
- onDestroy: cleans up bypass state if user didn't proceed
- Launch logging: cancelled/proceeded actions logged to Room
- Settings gear icon added to HomeScreen TopAppBar
- Build verified: `gradlew assembleDebug` → BUILD SUCCESSFUL (6s incremental)

### What's next
- Test on real device: create groups, enable AccessibilityService, open target apps, verify cooldown flow.
- Future: app icon display in group editor, usage statistics, scheduled profiles.

## Log

### 2026-07-23 (Accessibility service persistence fix)
- Root cause: the static `isRunning` flag in AppauseAccessibilityService reset
  to false whenever the OS killed the process, causing a false "Service not
  enabled" report even though the system permission was still granted.
- Fix: removed the static flag entirely. Added `AccessibilityServiceChecker`
  object that reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` directly —
  the system-level record that survives process death and reboot.
  HomeViewModel and SettingsViewModel now call this checker instead of the flag.
- OEM guidance: on Xiaomi (HyperOS/MIUI) and Huawei (HarmonyOS/EMUI) the system
  genuinely removes the service from the enabled list after a force-stop or
  reboot. This cannot be re-enabled programmatically (AGENTS.md rule 3).
  Added a "Why it keeps turning off" button on the warning card that opens an
  AlertDialog explaining the cause and listing the manual steps: allow autostart,
  set battery to no restrictions, lock in recents, then re-enable in settings.
- New strings (EN + ZH): service_off_help, service_help_title, service_help_body,
  got_it.
- BUILD SUCCESSFUL via `gradlew assembleDebug`.

### 2026-07-22 (In-app dark mode)
- Added a user-selectable theme mode in Settings: Light / Dark / Follow system.
- Persisted in DataStore (`theme_mode`), mirrored to SharedPreferences for a
  flicker-free synchronous read on cold start.
- MainActivity and PauseActivity now observe the mode and resolve the dark
  flag via a new `appauseDarkTheme()` helper; switching applies reactively
  with no restart. The existing dark palette is used as-is.
- New strings (EN + ZH): theme, theme_light, theme_dark, theme_system.
- Version bumped to 0.2.1 (versionCode 17).
- BUILD SUCCESSFUL via `gradlew assembleDebug`.

### 2026-07-21 (Bugfix + UI/UX overhaul)
- Fixed: pause screen re-appearing on the launcher after Cancel (stale window
  event race) via `justCancelledPackage` suppression guard in
  AppauseAccessibilityService / OverlayManager / PauseActivity. Committed be349fa.
- Theme: disabled Material You dynamic color (root cause of the uniform
  grey-purple look); added a fixed refined light palette to Color.kt and mapped
  all structural tokens (background/surface/containers/outline) in Theme.kt.
- Home: status header split into two states — service OFF shows a light-red
  warning card with "Open settings" and NO switch (fixes the contradictory
  "Not enabled" + enabled-looking switch); service ON shows "Service active"
  with the master toggle. Stats relabeled Completed/Avoided. Group cards got a
  chevron + pluralized app counts. FAB is now extended ("+ New group").
- Terminology (UI only, data values unchanged): Pause → Cooldown,
  Learning → Recommended; stats Waited/Cancelled → Completed/Avoided.
- Group Editor: type cards equal-height with top-right check + light borders;
  slider is now a continuous track (removed steps=58 tick dots) with an inline
  "Cooldown [ 10 ] sec" input, bidirectionally synced and clamped to 1–60;
  Add-apps row with light border + plural counts; selected apps as an
  independent section with dividers, per-app "Remove %s" a11y labels and an
  empty state; delete now requires a confirmation dialog with a
  count/type-aware message; bottom bar is keyboard- (imePadding) and
  nav-bar-aware.
- Strings: added plurals (group_app_count, apps_selected, selected_count,
  delete messages) in EN + ZH; new status/empty-state/dialog copy in both
  languages.
- BUILD SUCCESSFUL — app-debug.apk (~18MB) via `gradlew assembleDebug`.
- Version bumped to 0.2.0 (versionCode 16) — UI overhaul marks the start of
  the 0.2.x series.

### 2026-07-16 (Phases 6–8)
- Phases 6–8 completed together: full interception + pause screen + polish.
- Created InterceptionManager.kt (bypass singleton with timeout).
- Updated AppauseAccessibilityService.kt (stub → real 7-step filter chain).
- Rewrote PauseActivity.kt (stub → full countdown UI with progress ring).
- Added Settings gear icon to HomeScreen TopAppBar.
- One build error: missing `kotlinx.coroutines.launch` import in PauseActivity. Fixed.
- BUILD SUCCESSFUL.

### 2026-07-16 (Phases 3–5)
- Phases 3–5 completed together: Navigation + Theme + all UI screens.
- Created 13 new files: 3 theme files, NavGraph, 4 screens, 4 ViewModels, updated MainActivity.
- Screen scaffolds include full functional UI (not just placeholders).
- Added lifecycle-viewmodel-compose and compose-material-icons-extended deps.
- No build errors — compiled on first attempt.

### 2026-07-16 (Phase 2)
- Phase 2 completed: App Query layer.
- Created 3 new files: AppInfo.kt, AppQueryService.kt, AppSelectViewModel.kt.
- Added lifecycle-viewmodel-compose dependency to Version Catalog.
- No build errors — compiled successfully on first attempt.

### 2026-07-16 (Phase 1)
- Phase 1 completed: Data Layer implementation.
- Created 18 source files across Gradle config, Android resources, and Kotlin.
- Two build errors encountered and fixed:
  1. `dependencyResolution` → `dependencyResolutionManagement` in settings.gradle.kts
  2. Missing mipmap icons → created adaptive icon with vector drawable
- BUILD SUCCESSFUL — app-debug.apk (11MB) generated.

### 2026-07-16 (Phase 0)
- Phase 0 completed: documentation and project planning.
- All foundational documents created.
- Ready to begin Phase 1: Data Layer implementation.
