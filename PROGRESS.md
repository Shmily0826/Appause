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

### 2026-07-28 (In-app feedback + doc refresh + v0.3.9)
- Added an in-app **Feedback** flow (Settings → Feedback): choose Bug report /
  Suggestion, write a message, optional contact email; device info (app version,
  Android version, model, locale) is auto-attached and **shown before sending**.
  Two channels, both system Intents only — no telemetry, no backend:
  - Email: `ACTION_SENDTO` mailto to rng2018520@gmail.com (chooser).
  - GitHub: opens the pre-filled "new issue" form with a [Bug]/[Suggestion]
    title + matching label on Shmily0826/Appause.
- New `ui/feedback/FeedbackScreen.kt`; `Routes.FEEDBACK`; `SettingsScreen` gained
  `onNavigateToFeedback` + a Feedback card (Icons.Feedback); NavGraph wired.
- Strings (EN + ZH): feedback_title/intro/bug/suggestion/message_*/contact_*/auto_info/send_email/open_issue.
- Doc refresh to match the current app (the early docs still said v0.2.1 and
  "fully local, no network", which is now partly false due to Pro activation):
  - README: version → 0.3.9, added Pro + Feedback + session-timer features,
    local-first wording, INTERNET permission note, a Feedback section, fixed
    GitHub/contact links.
  - PRIVACY: now states the only network use is a one-time Pro license redeem
    (sends a non-identifying device fingerprint, returns an on-device-verified
    token) + feedback is user-initiated; added INTERNET permission row (EN+ZH).
  - INSTALL: real GitHub Releases URL (was your-username placeholder).
  - REQUIREMENTS: added "Post-MVP additions" section (Pro/Plan B, session model,
    feedback, i18n, stats) so the v1 spec stays honest.
  - ARCHITECTURE: updated directory tree (data/pro, ui/pro, ui/feedback,
    ui/stats, ui/recommended, worker/), added §6.4 session/leave-cooldown model.
  - AGENTS: rule 8 now permits the Pro Worker as the only backend (no accounts/
    sync/analytics); notes the feedback screen uses Intents only.
  - RELEASE_NOTES: bumped to v0.3.9 with the feedback + docs notes; real GitHub URLs.
- Version bump: `app/build.gradle.kts` versionCode 27 → 28, versionName 0.3.8 → 0.3.9.
- Verified `./gradlew assembleDebug` → BUILD SUCCESSFUL.

### 2026-07-28 (Pro client gating — wire all paid features to isPro)
- Extended plan-A Pro scaffolding: all four paid gates now read `isPro` (client-only, no backend).
- `data/pro/ProState.kt`: added PRO_COOLDOWN_MAX_SECONDS=60, FREE_STATS_DAYS=7, PRO_STATS_DAYS=365.
- `GroupEditorViewModel`: exposes `isPro` + `maxCooldown` (30 free / 60 pro); `updateCooldown` now clamps to the tier cap.
- `GroupEditorScreen`: cooldown slider max + end-label follow `maxCooldown`; re-remind replaced by a locked "tap to upgrade" row for free users (deep-links to Pro). NavGraph passes `onNavigateToPro`.
- `StatsViewModel`: dailyStats/topApps/totalRatio now `flatMapLatest` over `isPro` (7-day window free, ~1-year pro). `StatsScreen` shows a free-tier history-limit notice card.
- `SettingsViewModel`: exposes `isPro`; `SettingsScreen` default-prompt editor disabled + "upgrade" card for free users.
- Strings (EN+ZH): pro_locked_hint, pro_badge, stats_free_limit_title, stats_free_limit_desc.
- Verified `./gradlew assembleDebug` → BUILD SUCCESSFUL (after a transient dexBuilder lock; retried).
- NOTE: still plan A — activation is debug-only / placeholder import; real paid unlock (plan B: CF Worker + signed JWT + N-device re-activation) deferred.

### 2026-07-28 (Release signing + sideload install guide)
- Set up signed release builds for direct (sideload) distribution — decided to use
  GitHub Releases + 蓝奏云 mirror instead of a traditional app store.
- Generated `app/release.keystore` (RSA 2048, validity 10000 days, alias `appause`).
  **Git-ignored** (`*.keystore`); NEVER commit. Back up the file + password separately.
- `app/build.gradle.kts`: load signing creds from `local.properties` (git-ignored),
  added `signingConfigs.create("release")`, and `release` buildType now uses it.
- `local.properties`: added APPause_KEYSTORE_PATH / _ALIAS / _PASSWORD / _KEY_PASSWORD
  (all git-ignored; the password is `AppauseRelease2026` — change it and back it up).
- Verified: `./gradlew assembleRelease` → BUILD SUCCESSFUL; `apksigner verify` →
  signed with v2 scheme (1 signer). APK at
  `app/build/outputs/apk/release/app-release.apk` (~12 MB).
- New `INSTALL.md`: sideload guide — enable "install unknown apps", bypass Play Protect
  / OEM "harmful app" warnings (Xiaomi/Huawei/OPPO/vivo), enable AccessibilityService,
  battery/exclude-from-kill. For the landing page / direct-download distribution.

### 2026-07-28 (Plan B step 2 — client-side JWT license verification)
- Implemented local, offline license verification so "open source but paid" holds:
  a fork gets the verify key, not the signing key.
- New `data/pro/DeviceKeyStore.kt`: Android Keystore RSA-2048 keypair (non-extractable
  private key), exposes a stable SHA-256 device fingerprint used for token device-binding.
- New `data/pro/LicenseVerifier.kt`: manual JWT (RS256) verification — base64url parse,
  SHA256withRSA signature check against the server public key, plus tier/exp/device
  claim validation. No new dependency (uses android.util.Base64 + org.json).
- New `data/pro/ServerKeys.kt`: embeds the server RSA PUBLIC key (verify-only). Currently
  a DEV key so the client is testable before the Worker exists; swap for the production
  public key (private half lives only in Cloudflare) before any public release.
- `ProState`: `isPro` now derived from a locally-verified token OR a debug flag
  (debug builds only). `importLicense()` verifies before storing (bad paste never flips
  Pro). `unlockPro()` renamed to `unlockProDebug()`.
- `SettingsDataStore`: `isPro` flow renamed to `isProDebug` (debug-only flag); real Pro
  is gated by the verified token in ProState.
- `AppauseApp`: ProState now receives the Application context (needed for Keystore).
- `ProScreen`/`ProViewModel`: input relabeled "License token"; added offline-verify hint.
  Strings (EN+ZH): pro_enter_code, pro_code_hint, pro_import_failed, pro_token_note updated.
- Dev keypair + a sample unbound dev token generated to `C:\Users\Shmily\Appause_Keys\`
  (private key kept OUT of the repo). The dev token verifies on any device for local testing.
- Verified `./gradlew assembleDebug` → BUILD SUCCESSFUL (after a stuck Gradle/Java process
  held the KSP cache; fixed by `--stop` + kill java + `rm -rf app/build`).
- SECURITY NOTE: do NOT ship a release APK with the DEV public key in ServerKeys — anyone
  could use the dev token. Replace with the production key before any public release.
- Next (Plan B step 3): deploy the Cloudflare Worker (code redeem -> sign JWT with
  device-bound "device" claim + exp + jti). Step 4: payment/code distribution.
- NOTE: `JAVA_HOME` in the shell still points at a Java 7 dir; build requires
  `JAVA_HOME=/d/Dev-Setup/jdk` (Temurin 17). Fix the env var to avoid friction.

### 2026-07-28 (Plan B step 3 — Cloudflare Worker + client redeem bridge)
- Built the server half of "open source but paid": a Cloudflare Worker that issues
  device-bound RS256 Pro license JWTs. Forking the repo still can't mint tokens
  because the signing private key lives only in a Cloudflare secret.
- New `worker/` project (separate from app): `src/jwt.mjs` (shared RS256 sign +
  pkcs8 import), `src/index.js` (Worker endpoints), `scripts/genkeys.mjs`
  (Node-generated PKCS#8 private + SPKI public), `test/sign-verify.mjs`
  (proves tokens verify against the Android client's SHA256withRSA verifier),
  `wrangler.toml`, `package.json`, `README.md` (deploy + key-setup + red lines).
- Worker endpoints: `POST /api/redeem {code,device}` -> `{token}` (enforces
  maxDevices, default 3; re-issues idempotently for an already-bound device),
  `POST /api/unbind` (self-service), `POST /admin/gencode` (x-admin-key),
  `POST /admin/unbind` (lost-device admin). Codes + device bindings in KV
  namespace `APPAUSE_CODES`; secrets `APPAUSE_PRIVATE_KEY` + `ADMIN_KEY`.
- Token claims match the client contract exactly: `tier:"pro"`, `device` (SHA-256
  keystore fingerprint), optional `exp` (null = buyout), `iat`, `jti`.
- `test/sign-verify.mjs` executed with managed Node 22 -> OK (RS256 token signed
  by the real jwt.mjs and verified with Node RSA-SHA256 == Android SHA256withRSA).
- Client bridge (so the Worker is actually usable): new `data/pro/ProConfig.kt`
  (WORKER_BASE_URL, empty by default), `ProState.redeemCode(code)` posts
  {code, device fingerprint} to /api/redeem, verifies the returned token locally
  via importLicense before storing; `ProViewModel.redeemCode`; `ProScreen` adds a
  "Redeem online" primary button + "Or paste a license token" secondary; added
  `<uses-permission android.permission.INTERNET/>` (one-time activation only,
  daily use stays offline). Strings (EN+ZH) for redeem/errors.
- `.gitignore`: worker/node_modules, .wrangler, .dev.vars added.
- Verified `./gradlew assembleDebug` -> BUILD SUCCESSFUL (transient dexBuilder
  file-lock again; fixed by PowerShell force-delete of app/build + rebuild).
- SECURITY RED LINES (same as before, now actionable): generate a FRESH
  production key pair; set ServerKeys.SERVER_PUBLIC_KEY_PEM to it +
  IS_PRODUCTION_KEY=true; set the private key as the Cloudflare secret; NEVER
  commit the private key/ADMIN_KEY/.dev.vars; never ship DEV key in a release.
- Next (Plan B step 4): payment/storefront that calls /admin/gencode on purchase.

### 2026-07-27 (ProState — monetization scaffolding, plan A)
- Added offline buy-once monetization scaffolding (plan A), no backend.
- New `data/pro/ProState.kt`: wraps SettingsDataStore; exposes `isPro` Flow,
  `unlockPro()`, `exportLicense()`, `importLicense()`. Free limits as constants:
  FREE_GROUP_LIMIT = 2, FREE_COOLDOWN_MAX_SECONDS = 30.
- SettingsDataStore: added `pro_unlocked` + `license_token` keys (read/write).
- AppauseApp: exposed `proState` lazy singleton.
- New `ui/pro/ProScreen.kt` + `ProViewModel.kt`: free/Pro comparison, activate
  (code input + debug unlock, BuildConfig.DEBUG only), export/import license
  (copies token to clipboard). Reachable from Settings and from Home when the
  free group limit is hit.
- HomeViewModel: exposes `isPro`; HomeScreen FAB gates new-group creation on
  the free group limit (routes to Pro screen when exceeded).
- Strings (EN + ZH): pro_* (title, status, comparison rows, activate, debug
  unlock, license export/import, messages) + pro_settings_desc.
- Plan B (server verification, signed token, N-device re-activation) deferred.
- BUILD SUCCESSFUL via `gradlew assembleDebug` (transient dex lock on retry).

### 2026-07-23 (Re-remind feature + wording fix)
- New feature: per-group "Re-remind" — after the user completes the cooldown
  and enters the app, the cooldown screen pops up again after N minutes if
  they're still inside. Configurable per group via a 0–60 min slider in the
  group editor (0 = off). Only applies to Cooldown (pause) groups.
- Implementation: AppGroup.reRemindMinutes field + MIGRATION_3_4 (DB v4);
  AccessibilityService.scheduleReRemind() uses a coroutine delay + checks
  lastForegroundPackage before re-showing the overlay; timer is cancelled
  when the user leaves the app.
- Wording: stat_cancelled ZH "避开" → "阻止"; OEM guidance text made generic
  ("some Android devices" instead of naming only Xiaomi/Huawei).
- New strings (EN + ZH): re_remind_label, re_remind_desc, re_remind_off,
  re_remind_value, re_remind_range_end.
- BUILD SUCCESSFUL via `gradlew assembleDebug`.

### 2026-07-24 (P0/P1 technical hardening for distribution)
- P0-2: replaced all `android.util.Log` with a debug-only `AppLogger`
  (app/src/main/java/com/appause/android/util/AppLogger.kt); release builds
  no longer write the user's installed package names to logcat (privacy fix
  for an app that markets itself as local-only). Enabled `buildConfig = true`
  in app/build.gradle.kts so `BuildConfig.DEBUG` is available.
- P1-3: `isSystemPackage()` now resolves launcher packages dynamically via the
  HOME intent (`refreshHomePackages()`) instead of a hard-coded OEM launcher
  list — correctly skips the home screen on realme/Meizu/Honor/Nothing, etc.
- P1-4: `AppauseAccessibilityService.onDestroy()` now calls `serviceScope.cancel()`
  so in-flight coroutines are stopped instead of leaking.
- P1-5: `pauseShown` and `justCancelledPackage` marked `@Volatile` for
  cross-thread visibility (read by PauseAlarmReceiver on the broadcast thread).
- BUILD SUCCESSFUL via `./gradlew assembleDebug` (note: the shell env's
  JAVA_HOME pointed at a Java 7 dir and broke Gradle; override with
  JAVA_HOME=/d/Dev-Setup/jdk = Temurin 17).

### 2026-07-24 (Open-source / distribution prep)
- Added `LICENSE` (MIT) for GitHub open-source release.
- Added `PRIVACY.md` — bilingual (EN + ZH) privacy policy stating the app is
  fully local: no account, no network, no analytics, no ads; AccessibilityService
  reads package name only (`canRetrieveWindowContent = false`); permissions table.
  Suitable as the privacy-policy URL for 酷安 / Play Data Safety.
- Rewrote `README.md` into a publish-ready framework: features, screenshots
  placeholder, how-it-works, permissions table, requirements, build-from-source
  (with JDK 17 / JAVA_HOME note + signing guidance), install (GitHub Releases +
  酷安), privacy link, status (0.2.1, all phases done), contributing, license,
  disclaimer. Notes clearly that Appause is NOT on Google Play due to the
  AccessibilityService policy.
- Enhanced `.gitignore`: added signing-file patterns (`signing.properties`,
  `key.properties`, `*.p12`, `*.pfx`, `*.keystore.*`), `*.hprof`, and
  `.workbuddy/`. `local.properties` / `*.keystore` / `*.jks` were already ignored.
- Confirmed no hard-coded signing config or secrets in build.gradle.kts or
  gradle.properties.

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
