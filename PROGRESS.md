# Appause — Development Progress

## 2026-08-28 — Async signing serialization verification (TASK APPAUSE-20260828-2028)
- Confirmed the previous whole-request test queue was stronger than real DO
  handler semantics and could hide an async signing interleaving.
- Wrapped the complete per-code mutation action in `blockConcurrencyWhile`,
  including legacy KV bootstrap, JWT signing, redeem, and both unbind routes.
- Replaced the outer request queue in the local harness with a non-queued
  request model and delayed signer. The suite now passes 31/31 checks, including
  final-slot, same-device, concurrent unbind, signing-failure, and legacy
  bootstrap cases.
- No deployment or production data mutation was attempted.

## 2026-08-28 — Atomic Pro activation-code binding (TASK APPAUSE-20260828-2027)
- Replaced activation-code KV read-modify-write mutations with one SQLite-backed
  Durable Object per normalized activation code.
- Redeem, self-unbind, admin-unbind, and newly generated codes now use the same
  Durable Object coordination boundary. Existing KV-only records bootstrap
  lazily into the DO without manual production migration.
- Added concurrent new-device, final-slot, same-device, legacy bootstrap,
  signing-failure, and unbind regression coverage. Worker tests: 30 checks plus
  RS256 sign/verify, all passing locally under Node 24.
- `wrangler deploy --dry-run` was not verified because the execution safety layer
  blocked a command that may transmit bundle/config data externally. No deploy or
  production data mutation was attempted.

## 2026-08-24 — Xiaomi / HyperOS transport gate (TASK APPAUSE-20260824-1819)
- Canonical SDK adb and one `scrcpy --mouse=sdk` session were used; no ADB server restart or reboot.
- A 5-minute baseline poll stayed online, but the later setup/navigation session had a real serial disappearance, auto-recovery, and scrcpy disconnect. HyperOS OEM functional scenarios remain pending; do not call the gate release-ready.
- No source changes. Detailed evidence is recorded in `TEST_REPORT.md` §10.

## 2026-08-24 — Post-reboot transport gate (TASK APPAUSE-20260824-1839)
- User-initiated reboot completed; post-reboot Debug AccessibilityService was enabled and bound, Release remained disabled, and both packages were still installed.
- The same Xiaomi serial disappeared again during normal Debug App launch and later recovered. The post-reboot 5-minute transport gate therefore failed; HyperOS OEM functional scenarios remain untested.
- No source changes, ADB server restart, second reboot, or secure-settings modification. Evidence is recorded in `TEST_REPORT.md` §11.

## 2026-08-24 — USB cable rerun (TASK APPAUSE-20260824-1839)
- Reran the post-reboot transport operation after the user changed the USB data cable.
- scrcpy still disconnected and the first transport poll reported a missing serial before automatic recovery. OEM functional regression remains blocked; evidence is in `TEST_REPORT.md` §12.

## 2026-08-24 — USB-port isolation (TASK APPAUSE-20260824-1852)
- After moving the replacement cable to a different USB port, idle ADB, moderate read-only ADB load, and scrcpy all stayed stable with transport_id 73.
- Transport isolation is Category 5 PASS on the new port. No Appause functional testing was performed; see `TEST_REPORT.md` §13.

## 2026-08-23 (unreleased) — Pre-device verification completion (emulator, TASK APPAUSE-20260823-2249)
- Closed the remaining on-device gaps that do NOT need a Xiaomi/HyperOS device, single pass.
  **No code changes** — only emulator state (accessibility enabled via Settings UI) + UI-driven
  verification + refreshed `output/Appause-v0.5.38-debug.apk` (gitignored, not added).
- Re-confirmed emulator env is **API 37 / Android 17** (not "Android 14" as §7 wrongly said;
  corrected in TEST_REPORT.md §8). Stock Android 17, still not HyperOS.
- Verified live on emulator (debug build 0.5.38, versionCode 90):
  - **Group Save → real persistence**: created "TestGroup2" (name + Chrome) → Save → force-stop +
    relaunch → Home STILL shows group, no re-onboarding (disproves the earlier WAL-pull false alarm).
  - **Group Editor Cancel & top-bar Back** both → Home (no onboarding loop); both call `onNavigateBack`.
  - **Home → Group Editor → Home** round-trip clean (no onboarding pollution).
  - **H1 AbandonCooldown** (mid-cooldown switch to a different real app, no Continue): overlay
    dismissed + `Bypass cleared` + guard released; reopening the target re-intercepts (not swallowed).
  - **H2 duplicate-while-overlay**: exactly 1 INTERCEPT, 1 overlay on duplicate launch (double-intercept
    guard holds).
  - **Stock Android Recents sanity** (Scenario G substitute): PASS by equivalence — decider skips
    non-grouped/system packages, so Recents can't fabricate a phantom overlay; reopen re-intercepts.
- Build/unit re-gate: `testDebugUnitTest` **78/0**, `assembleDebug` BUILD SUCCESSFUL (after clearing
  dex `graph.bin` lock), `git diff --check` clean. `TEST_REPORT.md` §8 appended; §7 env error fixed.
- **Xiaomi/HyperOS real-device gate still PENDING** (true Scenario G, 2032-over-小红书 battle,
  电源限制 = 无限制, reboot AccessibilityService). Do NOT call this release-ready.

## 2026-08-23 (unreleased) — Android Core Integration Regression (emulator gate)
- Ran the full integration gate on the local working tree (TASK APPAUSE-20260823-2152):
  `testDebugUnitTest` → **78/0**, `assembleDebug` → BUILD SUCCESSFUL.
- **Fixed an emulator/device-only regression**: `OnboardingViewModel` had a 2-arg
  test-seam constructor that broke Compose's default `viewModel()` factory
  (`NoSuchMethodException` after `pm clear`). Added a `ViewModelProvider.Factory`
  companion + wired it in `NavGraph.kt`; removed a duplicate `ViewModelProvider`
  import. Onboarding now launches clean post-`pm clear` on a real process.
- Emulator smoke (Medium_Phone AVD, API 37): onboarding 8-page order, Later/Create/
  Back/restart/round-trip all PASS; AccessibilityService Scenarios A–F + H PASS
  (first-open INTERCEPT + `Overlay shown type=2032`, dedup SKIP, Continue bypass,
  Cancel+reopen, launcher/system SKIP, leave-window RESUME).
- **Scenario G (recents-replay burst) NOT TESTED on emulator** — OEM/HyperOS-only;
  deferred to real-device regression. BurstTracker is unit-covered (10 tests).
- `TEST_REPORT.md` §7 documents the gate + regression matrix. No git commit/tag/release.
- Working tree preserved; HEAD `eb29d1e` (== GitHub `main`). Recommended next:
  Xiaomi/HyperOS real-device regression (Scenario G + 2032-over-小红书 + 电源限制).

## 2026-08-22 (unreleased) — Interception testability refactor + unit tests
- Behavior-preserving refactor of the interception core (no functional change):
  - Extracted the `handleForegroundChange()` filter chain (steps 1–6.6) into a
    pure decision layer `interception/InterceptionDecider.kt`
    (`decidePreGroup` / `decidePostGroup`, sealed `PreGroupDecision` /
    `PostGroupDecision`). The service now only executes effects (timers,
    bypass, overlay, diagnostics); every decision order, reason string, and
    effect is byte-for-byte the original behavior.
  - Extracted the recents-replay burst fingerprint into
    `interception/BurstTracker.kt` with an injected clock (replaced
    `recordWindowEvent` / `isBurstSuppressed` and the three burst fields).
- Added the project's first unit tests (`app/src/test`, JUnit 4, JVM-only,
  never packaged into the APK) — 50 tests total, all passing:
  - `LicenseVerifierTest` (12): forged/tampered/expired/misbound tokens, PEM
    parsing, unpadded base64url.
  - `InterceptionManagerTest` (7): bypass lifecycle.
  - `InterceptionDeciderTest` (21): every step of the filter chain, pinning
    the v0.5.11–v0.5.27 regression behaviors (stale cancel event, quick
    re-open after cancel, poller dedup vs genuine re-open, abandon-cooldown,
    burst suppression, double-intercept guard) and the exact diagnostics
    strings.
  - `BurstTrackerTest` (10): the v0.5.24 threshold rules (2 real apps =
    genuine switch, 3 = replay), window pruning/boundary, suppression expiry,
    own-package and noise exclusion.
- `LicenseVerifier` switched `android.util.Base64` → `java.util.Base64`
  (identical decoding, available since API 26 = minSdk) so it runs in JVM tests.
- Gradle: added `junit` + `org.json` as `testImplementation` deps via the
  Version Catalog, and `unitTests.isReturnDefaultValues = true`.
- Verification: `./gradlew testDebugUnitTest` 50/50 PASS;
  `./gradlew assembleDebug` PASS. No on-device regression testing done yet —
  real-device smoke test of the interception flow is still recommended before
  release.

## v0.5.38 (released)
- Release documentation and packaging audit:
  - Version bumped to 0.5.38 / 90.
  - Corrected INSTALL.md: accessibility is required; overlay permission is an optional fallback; upgrades must install over the existing app rather than uninstalling first.
  - Corrected privacy disclosures for user-initiated diagnostic feedback, including group/package and recent foreground/interception fields shown before sending.
  - Corrected the manifest overlay comment to match the actual 2032 → 2038 → Activity fallback order.
  - Updated README, hosted privacy policy, Coolapk materials, and domestic/overseas payment-route notes.
- Onboarding flow refinement:
  - Reordered the guide so users see the pause-screen preview before configuring permissions.
  - Moved the privacy/value explanation ahead of AccessibilityService setup.
  - Reworded Chinese copy to use more natural terms such as “等待时间” and “提醒页面”.
  - Added a clear explanation that Appause identifies the foreground app only and does not read screen content, messages, or account information.
  - English copy updated to match the new value-first flow.
  - Debug build verified with `gradlew assembleDebug` → BUILD SUCCESSFUL.
- Follow-up onboarding and home setup refinement:
  - Kept the live pause-screen preview early in onboarding, but restored normal
    Next navigation so no later permission step can be skipped.
  - Moved optional first-group creation to the final step, after all setup
    explanations; users can also enter Home without creating a group.
  - Replaced the stacked red permission and battery warnings on Home with one
    calm setup checklist that opens the next unfinished setting.
- Onboarding follow-up (pending real-device verification):
  - Removed the obsolete page-index setter and added a dedicated return path
    from the final onboarding Group Editor step to Home.
  - Simplified the first-group Chinese and English copy and the preview caption;
    no other onboarding page copy was changed.
- GroupEditorScreen re-remind refinement (task #50 follow-up):
  - Re-remind block already in default-collapsed CollapsibleCard (everyone).
  - "Same as first" cooldown now explicit: added a switch; slider min is 1s (no implicit 0). Data model keeps `0 = same as first`.
  - Renamed 重复提醒 → 循环提醒 (Loop reminders).
  - Simplified 逐次延长冷却 desc with concrete example (30 → 60 → 90 s).
  - Collapsed re-remind card shows a summary ("已开启 · 每 X 分钟" / Off / Pro only).
  - Slider: continuous track (no steps), default circular thumb; number input shrunk to 56dp so all three time controls share one layout.
  - Debug build green → output/Appause-debug-v0.5.2.apk.

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

### 2026-08-18 (English landing page design directions)
- Added an isolated comparison hub at `taste-variants/skills-en/index.html`.
- Added four complete English landing page directions without replacing the
  current site:
  - Precision: technical Workbench layout with a cobalt instrument aesthetic.
  - Quiet: calm evergreen Split Studio layout with a clear screenshot-led
    product story.
  - Still: low-stimulation dark Feature Stack built around notice, pause, and
    choose moments.
  - Breathe: clear-water Map / Diagram layout that visualizes the short path
    from selecting an app to making a deliberate choice.
- Removed the high-contrast Poster direction after design review.
- Added three follow-up English directions after a second design review:
  - Moment: a fixed-light product campaign with the pause, setup, and outcome
    visible in a short page.
  - Daylight: a bright consumer direction centered on a close crop of the real
    countdown rather than a simulated device frame.
  - Direct: a conversion-led product proof page that shows what appears before
    YouTube opens and keeps supporting screenshots compact.
- Updated the comparison hub to place the three follow-up directions first and
  retain the previous four for reference.
- Reused real English app screenshots and current product/privacy facts; added
  dedicated token files plus Hallmark preflight and rotation records.
- Verified at 320, 375, 414, 768, 1280, and 1920 px with no horizontal overflow;
  all screenshots loaded, clickable labels stayed on one line, and the Precision
  Ctrl/Cmd K dialog worked.
- Fixed the pre-existing debug build error by adding the missing
  `com.appause.android.R` import to `DiagnosticsScreen.kt`.
- BUILD SUCCESSFUL via `assembleDebug` using Java 17.

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
  (all git-ignored; keep the keystore password changed from any default and backed up separately).
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

### 2026-08-24 (Xiaomi / HyperOS real-device gate — APPAUSE-20260824-1909)
- Resumed on the replacement cable and the new physical USB port after the Category 5 transport gate.
- Confirmed Xiaomi 2410DPN6CC / Android 16 / HyperOS OS3.0, Debug AccessibilityService enabled + bound, Release disabled, Debug battery set to HyperOS `No restrictions`, and the active `test` group through normal UI: 10-second cooldown, XHS and Bilibili members.
- Real-device results: Bilibili and XHS both attached as 2032 accessibility overlays and were physically confirmed visible by the user. Background survival and lock/unlock retained the bound service. AbandonCooldown logs showed guard dismissal and bypass cleanup.
- Continue, Cancel, Recents replay, and reboot persistence remain untested; genuine reopen/burst were only partial because the same-session leave-window/dedup state and an existing overlay prevented a clean re-armed run.
- Only `TEST_REPORT.md` and `PROGRESS.md` were updated. No source changes, commit, push, or release.

### 2026-08-24 (Remaining HyperOS interaction regression — APPAUSE-20260824-1938)
- Read-only baseline confirmed unchanged. `InterceptionDecider.kt` is present as an untracked, non-ignored file with no Git history; it was not restored or modified.
- Continue PASS: countdown completed, overlay dismissed, Bilibili usable, temporary bypass/session started.
- Cancel PASS: overlay dismissed, Launcher/Home became foreground, bypass cleared, and later XHS reopen intercepted again.
- Genuine reopen PASS: after the real 180-second leave window, Bilibili re-armed and intercepted again. HyperOS Recents replay PASS: selecting XHS from Recents produced one expected interception.
- Rapid-switch K FAIL: Home/Chrome/Bilibili/XHS/Home did not stack duplicate overlays, but the visible XHS 2032 overlay remained over Home after the target was abandoned; the 30-second watchdog released logical guard without dismissing the visible overlay. This is a confirmed real-device user-visible bug and was not fixed.
- Reboot persistence classified conservatively as PASS with boundary: enabled state persisted from the preceding user reboot and later functional interception succeeded without manual re-enable; uninterrupted binding continuity remains unproven.
- Only `TEST_REPORT.md` and `PROGRESS.md` changed this turn. No source changes, staging, commit, push, or release.

### 2026-08-24 (Cooldown overlay cleanup — APPAUSE-20260824-1958)
- Implemented the minimal Home-abandonment fix in `InterceptionDecider.kt` and `AppauseAccessibilityService.kt`: confirmed launcher foreground is handled before broad system filtering, and visible cooldown cleanup is not blocked by an automatically started bypass.
- Added two focused decision tests: confirmed Home abandons an active cooldown; unconfirmed launcher noise remains skipped.
- Automated verification: `testDebugUnitTest` PASS (80 tests, 0 failures), `assembleDebug` PASS, `git diff --check` PASS.
- Installed the new Debug APK on Xiaomi `6036d5b`. Real-device primary rapid Home abandonment PASS; Continue, Cancel, and genuine reopen PASS; XHS 2032 attachment PASS by window inspection. Recents became NOT TESTED because ADB disconnected immediately before that step.
- Current-build human visual confirmation and Recents need one follow-up when the USB connection is restored. No commit, push, merge, tag, or release.

### 2026-08-28 (Final HyperOS Overlay Boundary Verification — APPAUSE-20260828-1608)
- Verification-only task; no source or test files changed.
- Rechecked Git baseline and preserved the existing modified/untracked working tree. The post-fix Debug APK from APPAUSE-20260824-1958 remained the trusted local artifact.
- Xiaomi `2410DPN6CC` was not available in ADB before any scenario began. ADB output indicated its daemon was not running and started it automatically; no further restart, reboot, or settings change was attempted.
- All requested device scenarios were NOT TESTED: Bilibili/XHS human visibility, Recents, Home abandonment before/after countdown, Usage access OFF behavior, and incidental system noise.
- No new product blocker was confirmed because no product scenario ran. The Usage access OFF boundary remains unresolved and must not be considered passed.

### 2026-08-28 (Final HyperOS Overlay Boundary Verification continuation — APPAUSE-20260828-1608)
- Xiaomi `6036d5b` reconnected. Confirmed Debug package `0.5.38-debug` / versionCode 90 and ADB heartbeat.
- Recorded Usage access original state ON, changed it to OFF through normal HyperOS settings, then restored it to ON through the normal risk-confirmation UI; final switch was confirmed ON.
- Usage access OFF → ordinary Bilibili interception FAIL: Bilibili remained foreground but no 2032 overlay or new Appause interception log appeared. Per stop condition, no other product scenarios were run.
- Usage access OFF → Home abandonment remains NOT TESTED. This is now a confirmed product-requirement mismatch requiring a separate implementation investigation because Usage access is documented as optional.
- No source/test changes, staging, commit, push, merge, tag, or release.

### 2026-08-28 (Optional Usage access interception diagnosis — APPAUSE-20260828-1634)
- Rechecked the baseline and confirmed `test` group configuration through normal Appause UI: 10-second cooldown, Bilibili and rednote members.
- Found the earlier Usage OFF result had invalid service preconditions: release AccessibilityService was enabled while Debug was disabled. Corrected via normal Accessibility settings only: release OFF, Debug enabled + bound.
- Usage ON control PASS: Bilibili produced one visible 2032 pause overlay; screenshot confirmed the complete UI.
- With Usage access OFF and Debug AccessibilityService still enabled + bound, clean Chrome → Home → Bilibili interception PASS; one visible 2032 overlay appeared. No source change was required.
- Root cause of the earlier failure is invalid Debug/release service selection and missing Accessibility event, not a UsageStats-null decider regression. Usage access original/final state restored to ON.
- Recents, Usage OFF → Home abandonment, and incidental-system-noise scenarios were not expanded after the valid OFF control passed. No source/test changes, staging, commit, push, merge, tag, or release.
### 2026-08-29 (Worker pre-deploy runtime validation — APPAUSE-20260828-2307)
- Wrangler 3.114.17 `deploy --dry-run --outdir` PASS; bundle, `ACTIVATION_CODES` DO class, `new_sqlite_classes` migration, and local KV binding parsed successfully.
- Actual local Miniflare/workerd runtime PASS on `127.0.0.1:8788` with isolated simulated KV/DO persistence and ephemeral RSA/admin/download values; no remote mode or production resource was used.
- Real local HTTP smoke and concurrency PASS: gencode/redeem/idempotent repeat/self-unbind/admin-unbind, capacity, final-slot contention, same-device race, and redeem/unbind overlap.
- Legacy KV bootstrap PASS with preserved existing device, `maxDevices=2`, and `expiresInDays=14`; post-bootstrap DO behavior PASS. Same persistence directory restart smoke PASS.
- Worker suite PASS (31/31), all three Node syntax checks PASS, and `git diff --check` PASS. No production source/config changes; only this progress record and `TEST_REPORT.md` were updated, with no stage/commit/push/deploy.

### 2026-08-29 (External Worker runtime persistence verification — TASK APPAUSE-20260829-1358)
- Manual/session verification in ordinary Windows PowerShell: locked local Wrangler 3.114.17 on Node v24.15.0 started `wrangler dev --local --port 8788 --persist-to .wrangler\external-runtime` and reported `Ready on http://127.0.0.1:8788`.
- Local Miniflare bindings were simulated: `ACTIVATION_CODES` Durable Object and `APPAUSE_CODES` KV. `GET /` returned 404 `{"error":"not_found"}`; synthetic nonexistent redeem returned 404 `{"error":"invalid_code"}`.
- With a local dummy admin key, `/admin/gencode` created synthetic code `APPAUSE-ZKVL-E3PW`. `/admin/unbind` for an unbound synthetic device returned `{"error":"device_not_bound"}` before and after stopping/restarting Wrangler with the identical persistence path, proving local SQLite-backed DO record persistence.
- This was external manual/session evidence, not Bridge, CI, production, or remote Cloudflare verification. No production secrets/resources or real activation codes were used; JWT signing was NOT TESTED because no private key was supplied.
- The same locked Worker/dependencies succeeding outside Bridge isolates the earlier Bridge-only `std::terminate` to the Bridge/Codex execution environment or its Wrangler→workerd process interaction. No source/config change, stage, commit, push, or deploy occurred.

## 2026-08-31 — v0.5.39 post-release public status
- v0.5.39 is released on GitHub as `Appause v0.5.39`. The public Android artifact is `Appause-v0.5.39.apk`, package `com.appause.android`, versionCode 91; its verified SHA-256 is `cc05a6b742018d907d71d1bd3f07b3044accd6ad4ab582f900ea55e1a1248a3f`.
- Worker validation passed the 31-test baseline and synthetic production Pro lifecycle checks, including device binding, capacity, unbind/reuse, and state consistency. No user, payment, or existing entitlement data was involved.
- Pro semantics were checked against tracked Android and Worker code: activation records may specify `expiresInDays`; the Worker adds `exp` only when that value is present, and Android accepts a signed token without `exp` while checking it when present. There is no automatic refresh; an expired token may require user-initiated redeem or import of another valid token. The observed one-day token was specific to a synthetic one-day record, not a universal policy.
- Accessibility and overlay RC iterations were curated from automated and physical-device evidence. The final design keeps the non-focusable accessibility overlay, explicit Cancel cleanup/return-to-Home behavior, launcher-settle handling, and synchronized overlay creation. Intermediate Back-as-Cancel experiments were superseded; system Back/Recents are not the explicit Cancel action.
- v0.5.39 release-candidate validation passed the Android unit/build/lint/package/bundle gates, release signing checks, and debug-only diagnostics isolation. Feedback remains a release feature using a structured local status report; the Diagnostics screen and test controls remain debug-only.
- Post-release smoke on a physical Xiaomi/Android 16 device passed data-preserving installation, package/version and artifact identity, launch/process checks, Accessibility service state, relevant app-op checks, and app-specific crash/ANR scanning. The final interception path for an already-configured blocked target was not newly exercised because the device remained at lockscreen/AOD.
- Public APK naming is `Appause-v<version>.apk`. `scripts/make_release.py` still emits a code-suffixed internal convenience artifact; this docs-only task leaves that mismatch for follow-up.
