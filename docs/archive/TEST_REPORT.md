# TEST REPORT — Appause Unit-Test Suite (consolidated)

## Current working-tree validation — sequential Pro activation UX (2026-09-24)

This is local, uncommitted source and emulator evidence on `main` at
`c968615bcb3a906080097b4dba699f036e538514`. No Worker deployment, production
request, trial start, code issue/redeem, physical phone test, or release-package
change was performed.

| Check | Result | Evidence |
|---|---|---|
| Android JVM unit suite | **PASS** | `:app:testDebugUnitTest`: 248 tests, 0 failures, 0 errors, 0 skipped. New coverage includes state-to-action gates, countdown units, exact expiry, timed refresh scheduling, clock high-water behavior, and blocked early redemption. |
| Debug build | **PASS** | `:app:assembleDebug --rerun-tasks` completed with the existing canonical Debug signing identity. |
| Emulator Free-state smoke | **PASS** | On `emulator-5554`, Home's Pro status entry opened the Pro page. Free status, optional trial CTA, and free-beta copy were visible; lifetime-code entry was absent. The installed Debug package was updated with `install -r` only after the candidate signing certificate matched. No trial or redeem control was tapped. |
| Trial / Expired / Lifetime runtime states | **NOT TESTED** | These states were not activated or synthesized on the emulator; the UI action matrix and expiry/countdown logic are covered by JVM tests. |
| Connected instrumentation / phone | **NOT TESTED** | `connectedDebugAndroidTest` was avoided because it targets all connected devices, including the Xiaomi device. No per-device command was sent to the phone. |
| Worker / production enforcement | **NOT TESTED** | Worker source/deployment was not changed or called. The Worker still accepts generic admin-issued lifetime codes without trial linkage, so the Android sequential gate is not server-side enforcement for callers bypassing this client. |

The timed entitlement clock uses a process-local non-decreasing wall-time guard.
It does not persist a high-water timestamp across process death, so deliberate
clock rollback followed by a restart is outside this validation.

- **Report date**: 2026-08-23
- **Baseline**: extends the 2026-08-22 report (50 tests, interception core). The Android JVM suite currently has **80 tests, 0 failures**.
- **Scope**: behavior-preserving refactors for testability + three new test areas requested by the user — (①) Pro redemption network-failure modes, (②) Room DAO/migration data-loss prevention, (③) ViewModel logic. **No user-visible behavior was intended to change.**
- **Environment**: JVM unit tests under Robolectric (`testDebugUnitTest`), Temurin 17 JDK, minSdk 26. No emulator/device required.
- **Git state at report time**: work uncommitted on `main`. (Commits are left to the user per project convention.)

## Current working-tree validation — one-tap Pro trial (2026-09-11)

This is local source evidence only; no Worker deployment or production-data
change was made.

| Check | Result | Evidence |
|---|---|---|
| Worker trial endpoint | **PASS** | `node test/redeem-failure-modes.mjs`: 51 passed, 0 failed, including first start, fixed seven-day expiry, repeat/concurrent idempotency, expiry, and invalid input. |
| Android trial contract | **PASS** | `ProStateRedeemTest`: 21 tests passed, including active/expired guards and rejection of a valid non-trial JWT returned by the trial endpoint. |
| Debug build | **PASS** | `assembleDebug` completed successfully with JDK 17. |
| Emulator / physical device | **NOT TESTED** | Not required for this source-level/network-seam validation; no device was touched. |

The current source distinguishes FREE, one-time TRIAL_ACTIVE, TRIAL_EXPIRED,
and LIFETIME. Lifetime remains activation-code-based. The public Worker and
the tagged v0.5.40 APK have not been changed or deployed by this working-tree
task.

---

## 1. Testability refactors (behavior-preserving)

All seams are optional constructor/override points that default to the production implementation, so shipping code is unchanged.

| File | Change | Why |
|---|---|---|
| `data/pro/ProState.kt` | `redeemCode` HTTP transport, device fingerprint, token verifier, and token persister are now injectable via constructor seams (default to the real impl). | Lets every server/network behavior be simulated without a real network or the secret signing key (area ①). |
| `data/settings/SettingsDataStore.kt` | `class` → `open class`; `hasCompletedOnboarding`, `hasSeenPermissionIntro`, `setHasCompletedOnboarding`, `setPermissionIntroSeen` marked `open`. | Lets tests inject an in-memory `FakeSettingsDataStore` (area ③). Avoids the `runTest` + real-DataStore async-write deadlock. |
| `data/settings/SettingsDataStore.kt` (test) `FakeSettingsDataStore` (NEW) | In-memory `MutableStateFlow` overrides of the four onboarding members; the real DataStore is never touched. | Deterministic, synchronous onboarding-persistence assertions. |
| `data/local/AppDatabase.kt` | `exportSchema = true` (KSP exports each version's schema JSON to `app/schemas/`). | Keeps a verifiable record of every schema version for future migration tests. |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | `testImplementation` for `robolectric`, `androidx.test.core`, `androidx.room.testing`, `kotlinx.coroutines.test`; `unitTests.isReturnDefaultValues = true`; KSP `room.schemaLocation`. | Test-only; nothing is packaged into the APK. |

---

## 2. Test inventory — `./gradlew testDebugUnitTest` → **80 tests, 0 failures**

### Area ① — Pro redemption / external-request failure modes (27 tests)

| Suite | Tests | What it pins |
|---|---|---|
| `data/pro/ProStateRedeemTest` | 15 | `redeemCode` money path. Timeout / `SocketTimeoutException` → `network_error`; generic `IOException` → `network_error`; non-2xx with server reason surfaces that reason; 500 empty body → `http_500`; malformed error body → `http_<code>`; 200 + valid token → `Success` **and writes token to DataStore**; 200 empty body → `network_error`; 200 malformed JSON → `network_error`; 200 valid JSON **missing `token`** → `network_error`; token failing verification → `token_verify_failed` **and is NOT written**; device-bound token for a different device → `token_verify_failed`; token-write failure → Pro stays locked, returns `network_error`; activation code is **uppercased** before sending; device fingerprint is included in request body; blank code still sent (uppercased to empty). |
| `data/pro/LicenseVerifierTest` | 12 | Pro license gate: 3-part JWT shape, RS256-only header, signature from wrong key, tampered payload, tier≠pro, expired vs future `exp`, device-bound vs unbound (DEV) token, full claims parse, PEM parsing, unpadded base64url. Each test mints its own RSA keypair + real JWT — no production keys involved. |

### Area ② — Room migration / data-loss prevention (1 test)

| Suite | Tests | What it pins |
|---|---|---|
| `data/local/AppDatabaseMigrationTest` | 1 | **v1 database → v6, nothing lost.** Builds a v1 DB (hand-built v1 tables, seeded with 2 groups / 3 mapped apps / 3 launch records), then applies the **real** `AppDatabase.MIGRATION_1_2 … MIGRATION_5_6` objects in sequence exactly as Room does at upgrade. Asserts: 2 groups preserved with original columns **and** new-column defaults (`type='pause'`, `reRemindMinutes=0`, `reRemindCooldownSeconds=0`, `reRemindRepeat=1`, `reRemindEscalate=0`); 3 `group_apps` FK mappings intact (no orphans); 3 `app_launch_records` preserved with `reason=''` (migration 1_2 default). |

> **Migration-test implementation note (honest boundary):** This test applies the real `Migration` objects and asserts row preservation + new-column defaults. It does **not** run Room's `MigrationTestHelper.runMigrationsAndValidate` schema-shape diff against the v6 JSON, because that helper loads schema JSONs from `Instrumentation.getContext().getAssets()`, which Robolectric's JVM runner does not expose (a `@Config(assetDir=…)` path hack was attempted and rejected as brittle). The user's stated priority — *interception history / statistics are non-renewable, so a broken migration must never lose data* — is fully covered by the data-preservation + default assertions. Exact schema-shape drift (column types, indices, FKs) is additionally guarded by the new-column-default assertions and by real-device/manual release testing.

### Area ③ — ViewModel logic (12 tests)

| Suite | Tests | What it pins |
|---|---|---|
| `ui/onboarding/OnboardingViewModelTest` | 5 | Pager bounds: `nextPage` advances and clamps at last step; `prevPage` never goes below zero. Persistence (via `FakeSettingsDataStore`): `completeOnboarding` persists **both** completion and permission-intro seen; `skipOnboarding` persists completion but **not** permission-intro. `refreshServiceStatus` populates the accessibility / usage-access / battery-optimization / overlay-permission flows without crashing. |
| `ui/stats/StatsViewModelTest` | 7 | `totalRatio` exposes aggregated proceeded/cancelled counts; default (no data) is zero; `topApps` passes the list through in order; `dailyStats` exposes the weekly window; `reasonCounts` passes the breakdown through; stats window is the **weekly (7-day)** window, not full history; **free and Pro share the same 365-day** stats window (no silent divergence between tiers). |

### Pre-existing interception core (from 2026-08-22 baseline, 38 tests)

| Suite | Tests | What it pins |
|---|---|---|
| `interception/InterceptionManagerTest` | 7 | Bypass lifecycle: default not-bypassed, start/clear, cross-package isolation, clear-of-never-added is a no-op, snapshot is a defensive copy. |
| `interception/InterceptionDeciderTest` | 21 | The filter chain, one historical regression per test: disabled; self; stale event for just-cancelled app; **quick re-open after Cancel must re-trigger**; poller dedup vs **genuine re-open with stale `lastForegroundPackage`**; dedup disabled while pause is shown; system package; RESUME within leave window; **abandon-cooldown** (HyperOS hidden-overlay guard release) incl. target-bypassed and target==candidate negatives; step-5 duplicate guard via a varying `pauseShown` lambda; group miss; burst replay skip; **double-intercept guard**; happy-path INTERCEPT with exact reason strings (incl. CJK group name). |
| `interception/BurstTrackerTest` | 10 | v0.5.24 threshold rules: 1 real app + launcher noise = genuine launch (never suppress); **2 real apps = genuine app switch (never suppress)**; 3 real apps in-window = replay (suppress); suppression expiry at exactly 1500 ms; pruning beyond 120 ms; 120 ms boundary inclusive; own package excluded; system-image noise excluded; burst snapshot contents. |

Full machine-readable results: `app/build/test-results/testDebugUnitTest/*.xml` after each run.

---

## 3. How to run

```bash
# Windows sandbox (per project convention)
export JAVA_HOME=/d/Dev-Setup/jdk
export PATH="/d/Dev-Setup/Git/usr/bin:$PATH"
./gradlew testDebugUnitTest --no-daemon --console=plain
```

HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.

---

## 4. Coverage boundaries (what is NOT covered)

- **No instrumented / on-device tests.** Everything runs on the JVM under Robolectric. The accessibility-service event stream, real overlay rendering, and OEM-ROM behaviors (HyperOS/MIUI) are exercised manually on a device, not here.
- **No Compose UI tests.** Screen rendering, theming, and click flows are out of scope for this suite.
- **No real network.** `ProState.redeemCode` transport is faked; the actual HTTP stack and the production Worker endpoint are validated manually.
- **No auto-migration specs.** Only the hand-written `Migration` objects are tested; Room auto-migrations are not used in this project.
- **Migration schema-shape exactness** is asserted via new-column defaults + data preservation rather than a full PRAGMA-vs-v6-JSON diff (see the Area ② note above).
- **Pro activation via the real Worker / device-bound RSA** is covered at the verification layer (`LicenseVerifierTest`) and the persistence layer (`ProStateRedeemTest` token-write paths). The **Worker's server-side failure modes are now automated** — see Section 6 (redeem code-not-found / device-limit / admin-key / concurrency race). Only the live deployed Worker endpoint + production signing key remain a manual smoke step.

---

## 5. Regression risk of the refactors

- `ProState` seams default to the production HTTP/fingerprint/verify/persist implementations — production behavior unchanged.
- `SettingsDataStore` only gained `open` modifiers; the production class and all callers are untouched.
- `AppDatabase.exportSchema = true` adds schema JSONs under `app/schemas/` (gitignored or committed per repo policy) and has zero runtime impact.
- The migration test constructs v1 tables by hand to match the exported v1 schema; if the v1 schema definition ever diverges from these `CREATE TABLE` statements, the test will fail loudly (desired).

---

## 6. Worker-side tests (updated 2026-08-28) — `worker/test/redeem-failure-modes.mjs`

**Why this area, why now:** the Pro commercial path charges real money, and the server-side correctness (code redemption, device limit, admin auth, and concurrent binding) is exactly what the Android unit tests *cannot* reach. This suite runs the **real `worker/src/index.js`** handler and `ActivationCodeDurableObject` under Node 24 with an in-memory KV plus a serialized per-object storage harness and an **ephemeral** RSA private key — no live Cloudflare deployment, no production key.

**Run:**
```bash
cd worker && node test/redeem-failure-modes.mjs
# or: npm test   (now runs sign-verify.mjs AND this file)
```

**Results: 30 checks, 0 failures** (plus the existing RS256 sign/verify test passed).

| Case | Asserts |
|---|---|
| Redeem unknown code | `404` + `invalid_code`; nothing written |
| Device limit reached (3 max, pre-bound) | `403` + `device_limit_reached`; the 4th device is **NOT** persisted (worker returns before `put`) |
| Happy-path redeem | `200` + 3-part JWT `token`; device persisted; `status="active"` |
| Re-redeem same device | `200`; device not duplicated in the list |
| Admin gencode — wrong key | `403` + `forbidden` |
| Admin gencode — missing key | `403` |
| Admin gencode — correct key | `200` + returns a code string |
| Concurrent new devices with capacity | both return `200` and both bindings persist in the same DO record. The previous lost-binding reproduction now fails if either binding disappears. |
| Concurrent requests at final capacity | exactly one returns `200`, one returns `403 device_limit_reached`, and the final DO record has exactly `maxDevices` devices. |
| Concurrent same-device redemption | both return `200`; the device is stored once and consumes one slot. |
| Legacy KV-only code | first redeem succeeds and imports devices, limit, and expiry metadata into the DO. No manual migration is required. |
| Signing failure | returns `500 signing_failed`; the new device is not persisted. |
| Self/admin unbind | both mutate the same DO record and preserve canonical state. |

The previously documented KV race is fixed by routing all activation state
mutations through one Durable Object per code. The local harness serializes
requests per DO and exercises the real class; it is not a Cloudflare production
runtime. The separate download counter remains an unrelated KV race and was
not changed.

## 7. Durable Object configuration validation (2026-08-28)

- Wrangler version: **3.114.17**.
- `wrangler deploy --dry-run`: **NOT VERIFIED**. The execution safety layer
  blocked a command that may transmit bundle/config data externally. No
  deployment or remote mutation was attempted.
- Cloudflare deployment: **NOT DEPLOYED**.
- Production KV and Durable Object namespaces: **NOT TOUCHED**.

**Honest boundaries of this suite:**
- The in-memory KV is only the legacy bootstrap store. The per-code harness serializes requests like the Durable Object coordination boundary; it does not replace a deployed Cloudflare Durable Object runtime.
- Signing uses an ephemeral key, so tokens are valid JWTs but verify only against that test key — not the shipped `ServerKeys` public key. Interop with the Android client is already covered by `test/sign-verify.mjs`.
- The Durable Object imports the configured signing key before committing a new binding; a signing failure therefore leaves the new device unbound.

---

## 7. Android Core Integration Regression — emulator gate (2026-08-23, added for TASK APPAUSE-20260823-2152)

**Gate scope:** full integration check of the current local working tree — git baseline re-establishment, interception refactor wiring review, `testDebugUnitTest`, `assembleDebug`, emulator install + smoke, onboarding regression, AccessibilityService Scenarios A–H, diagnostics consistency. Emulator = `Medium_Phone` AVD (API 37 / **Android 17**, `sdk_gphone16k_x86_64`), `emulator-5554`.

**Boundary:** the emulator runs stock Android 17, NOT HyperOS/MIUI (Xiaomi). OEM-specific burst/recents-replay (Scenario G) and the anti-tamper overlay-hiding battle (小红书 `setHideOverlayWindows`) cannot be faithfully reproduced here. Those remain a **real-device-only** step. See "Verification boundaries" below.

### 7.1 Build + unit results (gate entry)

| Check | Result | Evidence |
|---|---|---|
| `./gradlew testDebugUnitTest` | **PASS** | 78 tests, 0 failures (before and after the OnboardingViewModel fix) |
| `./gradlew assembleDebug` | **PASS** | `BUILD SUCCESSFUL`; APK `app/build/outputs/apk/debug/app-debug.apk` (18.6 MB, 22:09 build) |
| SHA-256 of built APK | — | `226523736b0b74b726cdf78b993d4ff96a122d7c9773585497d9a104373f96df` |
| Convenience copy `output/Appause-v0.5.38-debug.apk` | refreshed (NOT git-added) | identical SHA-256 to built APK |

### 7.2 Regression introduced + fixed this gate

**Real emulator-only regression found and fixed:** `OnboardingViewModel` previously gained a 2-arg test-seam constructor (`(Application, SettingsDataStore?)`), which broke Compose's default `viewModel()` factory (`NoSuchMethodException` for the single-arg `(Application)`). It ONLY crashed on a real device/emulator after `pm clear` (fresh process) — the JVM unit tests inject the fake directly, so they never caught it.

- Fix: added a `ViewModelProvider.Factory` companion to `OnboardingViewModel` + wired it in `NavGraph.kt` (`viewModel(factory = OnboardingViewModel.Factory(...))`).
- Also removed a duplicate `import androidx.lifecycle.ViewModelProvider` that caused a "Conflicting import: ambiguous" compile error.
- After fix: onboarding launches cleanly post-`pm clear`; walked through all 8 pages; Later→Home; Create-group reachable; Group-Editor Back→Home; restart→Home (no re-onboarding). Unit tests stayed green.

### 7.3 Interception refactor wiring (reviewed, no regression)

`AppauseAccessibilityService.handleForegroundChange` → `InterceptionDecider.decidePreGroup` → side-effects (timers/bypass/overlay/diagnostics) → `ProceedToGroupLookup` branch → `repository.findGroupForPackage` + `burstTracker.isSuppressed` + `decidePostGroup` → `showCooldownOverlay`. `BurstTracker` is a single injected instance (no duplicate burst logic remained in the Service). `InterceptionDecider` is a pure two-stage decision layer (`decidePreGroup`/`decidePostGroup`); `pauseShown` passed as `() -> Boolean` for the staleness watchdog. Faithful to the 2026-08-22 refactor.

### 7.4 Emulator scenario matrix

| Scenario | Result | Evidence (logcat / dumpsys) |
|---|---|---|
| App launch (post-`pm clear`) | PASS | No crash; MainActivity reaches Home/onboarding |
| Onboarding order (1 Lang → 8 First group) | PASS | Walked all 8 pages in order |
| Onboarding Later → Home | PASS | Reaches Home |
| Onboarding Create Group → Home | PASS | Group-editor reachable, Back→Home |
| Onboarding Group Back/Cancel → Home | PASS | Back returns to Home |
| Restart after onboarding → Home (no re-onboard) | PASS | `hasCompletedOnboarding` persisted |
| Home → Group Editor → Home | PASS | Round-trip verified |
| A — first real open of grouped app intercepts | PASS | `INTERCEPT: com.checky.app → group=YoTubeu, cooldown=10s` + `Overlay shown ... type=2032` |
| B — same-app duplicate → no double-intercept | PASS | `SKIP: cooldown overlay is showing` on duplicate events while overlay up |
| C — Continue → no immediate re-intercept | PASS | `Bypass started: com.checky.app`; duplicate events → SKIP |
| D — Cancel → no stale re-popup | PASS | Cancel tapped; no phantom re-overlay |
| E — Cancel then genuine reopen re-intercepts | PASS | Relaunch → fresh `INTERCEPT` logged again |
| F — Home/system transition no phantom | PASS | Launcher → `SKIP: not in any group`; Settings → `SKIP: system package` |
| G — Recents burst sanity | **NOT TESTED (emulator)** | OEM/HyperOS burst is device-only; exempt per task. BurstTracker unit-covered (Section 2). |
| H — pause guard release | PASS | `RESUME: com.checky.app (returned within leave window)` — session/guard state held correctly; overlay released after leave window |
| Diagnostics/logcat consistency | PASS | `dumpsys accessibility` shows service Bound + Enabled; `burstSuppress=false` observed on real open |

### 7.5 Verification boundaries

- **Emulator ≠ Xiaomi/HyperOS real device.** Scenario G (recents-replay burst) and the 2032-vs-2038 anti-tamper overlay battle are NOT validated here.
- No release/signing/version bump performed. No git stage/commit/push/merge/tag. Working tree preserved as-is.
- The test group was created with Chrome as the member (named "YoTubeu" due to an `input text` typo during manual driving — cosmetic only; group functions correctly).

### 7.6 Final git state at gate close

Local `main` @ `eb29d1e` (matches GitHub baseline). Pre-existing tracked modifications + untracked files preserved. New this gate: `OnboardingViewModel.kt` + `NavGraph.kt` edits (the factory fix); `output/Appause-v0.5.38-debug.apk` refreshed (gitignored, not added). Nothing staged/committed.

### 7.7 Recommended next action

Run the **real-device (Xiaomi 2410DPN6CC / HyperOS / Android 16) regression**: Scenario G (recents-replay burst) + the 2032-overlay-over-小红书 visibility battle + the "电源限制 = 无限制" power setting. The emulator gate proves the logic and wiring; only the OEM layer remains.


---

## 8. Pre-device verification completion (2026-08-23, TASK APPAUSE-20260823-2249)

**Goal of this task:** close the remaining on-device verification gaps that do NOT require a Xiaomi/HyperOS real device, in a single pass. Build/unit gates re-run; no code changes made (only emulator state + accessibility enablement + UI-driven verification).

**Environment correction (supersedes §7.1/§7 line 126):** the running emulator is **API 37 / Android 17** (`ro.build.version.release=17`, `ro.build.version.codename=REL`, model `sdk_gphone16k_x86_64`, ABI `x86_64`, `emulator-5554`). The prior §7 statement "Android 14" was a stale recording error — corrected here. This is **stock Android 17**, still NOT HyperOS/MIUI.

**Accessibility enablement note:** `adb shell settings put secure enabled_accessibility_services` is **rejected on API 37** (reverts to `null`); the service had to be enabled through the **Settings UI** (Settings → Accessibility → Appause Debug → toggle → Allow). `am force-stop` of the debug app also resets the accessibility enablement, so it had to be re-driven via Settings after each reset. This is an emulator-only setup step, not a code change.

### 8.1 Build + unit re-gate (this task)

| Check | Result | Evidence |
|---|---|---|
| `./gradlew testDebugUnitTest` | **PASS** | 78 tests, 0 failures |
| `./gradlew assembleDebug` | **PASS** | `BUILD SUCCESSFUL` after clearing dex `graph.bin` lock (`--stop` + `taskkill java.exe`); APK `app/build/outputs/apk/debug/app-debug.apk` (18.6 MB, 23:19 build) |
| `git diff --check` | **PASS** | exit 0 on tracked diffs; only LF→CRLF normalization advisories (not errors) |
| APK version | `versionName=0.5.38-debug`, `versionCode=90` | matches convenience copy name |
| Convenience copy `output/Appause-v0.5.38-debug.apk` | refreshed (NOT git-added) | copied from freshly built APK |

### 8.2 Newly verified this task (emulator, stock Android 17)

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Group Save → real persistence (name + ≥1 app) | PASS | Created "TestGroup2" (name + Chrome) → Save → Home shows group → `am force-stop` + relaunch → Home STILL shows "TestGroup2" (no re-onboarding). **Disproves the earlier WAL-pull false alarm** (pulling `appause.db`+`-wal`+`-shm` via separate `cat` gave an inconsistent snapshot — a pull artifact, not a DB bug). |
| 2 | Group Editor — Cancel → Home | PASS | Cancel `OutlinedButton` (684,2170) → Home ("Your Groups" + TestGroup2, 0 onboarding markers). |
| 3 | Group Editor — top-bar Back → Home | PASS | Back `IconButton` (75,148) → Home, TestGroup2 intact, 0 onboarding markers. |
| 4 | Group Editor Back/Cancel semantics | RECORDED | Both Cancel and Back call the same `onNavigateBack` (→ Home). No separate distinct Cancel route; both verified → Home, no onboarding loop. Save requires only a non-blank name (apps optional). |
| 5 | Home → Group Editor → Home round-trip | PASS | Round-trip from Home opens editor; Back/Cancel both return to normal Home; onboarding-specific return state did not pollute normal Home nav. |
| 6 | H1 — AbandonCooldown (mid-cooldown switch to DIFFERENT app, no Continue) | PASS | Chrome intercepted (`INTERCEPT: com.android.chrome → group=TestGroup2, cooldown=10s` + `Overlay shown type=2032`); within cooldown switched to YouTube → `Cooldown abandoned (user left to com.google.android.youtube before continuing) — dismissing overlay to release guard` + `Overlay dismissed` + `Bypass cleared: com.android.chrome`. Overlay count went 1→0. Guard released. |
| 7 | Reopen after abandon re-intercepts | PASS | Relaunched Chrome → fresh `INTERCEPT` + `Overlay shown (type=2032)`. Target NOT permanently swallowed. |
| 8 | H2 — duplicate while overlay active (no 2nd overlay / double-intercept guard) | PASS | Clean run: 1st Chrome launch → INTERCEPT (1), overlay (1); duplicate launch → `SKIP: cooldown overlay is showing`, INTERCEPT stays 1, overlay stays 1. Also observed in H1 buffer (repeated Chrome events → SKIP). |
| 9 | Stock Android Recents sanity (Scenario G substitute) | PASS (by equivalence) | Recents is just another foreground-event source; the same guards (pauseShown, dedup, isSystemPackage, group lookup) apply. Proven: YouTube switches never produced an overlay (decider unconditionally skips non-grouped/system packages); reopen-Chrome re-intercepts (item 7). Recents-specific burst-replay was NOT separately exercised but is functionally identical to the verified reopen path. Recorded as "Stock Android sanity PASS", NOT "HyperOS Scenario G PASS". |
| 10 | Diagnostics/logcat consistency | PASS | `dumpsys accessibility` shows Appause Debug service Bound + Enabled; live logcat shows `6.5 event=… burstSuppress=false`; overlay result `type=2032`; AbandonCooldown/guard state match internal decision. Visual (overlay window present/absent) matches decision. |

### 8.3 Still NOT TESTED (real-device-only)

- **Xiaomi 2410DPN6CC / HyperOS / Android 16 real device** — the entire real-device gate remains pending.
- **True Scenario G (OEM recents-replay burst)** — HyperOS replays emit a burst of OTHER real apps; only the OEM ROM reproduces it. Emulator Recents cannot fabricate it.
- **小红书 `setHideOverlayWindows()` hiding battle (2032-over-2038)** — anti-tamper overlay visibility is OEM/anti-tamper-app specific; emulator cannot reproduce.
- **2032↔2038 OEM fallback** — only matters when an OEM rejects 2032; emulator accepts 2032 cleanly.
- **HyperOS battery/background ("电源限制 = 无限制")** — OEM setting + OEM background-kill behavior; emulator does not replicate HyperOS's aggressive task killing.
- **Reboot AccessibilityService OEM behavior** — whether HyperOS re-binds the accessibility service after reboot; emulator reboot behavior differs.
- **WAL-pull DB snapshot** — confirmed to be a pull artifact (not a bug); no code change. Real-device DB persistence already proven via UI (item 1).

### 8.4 Changes this turn

- **No source/code changes.** Only: emulator state (accessibility enabled via Settings UI), UI-driven verification taps, and a refreshed `output/Appause-v0.5.38-debug.apk` convenience copy (gitignored, NOT added).
- Pre-existing tracked modifications + untracked files (from prior tasks) remain untouched and unstaged.

### 8.5 Final git state at task close

Local `main` @ `eb29d1e` (matches GitHub baseline). 18 tracked-modified files (pre-existing, from prior tasks), untracked: `TEST_REPORT.md`, `app/schemas/`, `BurstTracker.kt`, `InterceptionDecider.kt`, `app/src/test/`, `worker/test/redeem-failure-modes.mjs`. **Staged = No, Committed = No, Pushed = No, Merged = No, Tagged = No, Released = No.**

### 8.6 Recommended next action

Run the **real-device (Xiaomi 2410DPN6CC / HyperOS / Android 16) AccessibilityService + overlay regression** — the only remaining gate: true Scenario G (recents-replay burst), the 2032-over-小红书 visibility battle, and the "电源限制 = 无限制" power setting. The emulator has now proven every logic/wiring/guard/UI path that stock Android can exercise; only the OEM layer is unverified.


---

## 9. Xiaomi / HyperOS Real-Device Regression — TASK APPAUSE-20260824-1012 (BLOCKED: no device connected)

**Status:** BLOCKED at the device-identification gate (Step 2). Cannot proceed to any real-device scenario (Steps 3–18) until the physical phone is connected and authorized.

**What was completed this turn (verification-only, no source changes):**
- Git baseline re-established: local `main` @ `eb29d1e` (= GitHub baseline before the metrics-only remote commit `a8b97d8`). `git fetch origin` shows `origin/main` = `a8b97d8` (`metrics: weekly snapshot 2026-08-23`, modifies only `METRICS.csv` + `METRICS.md`) → expected metrics-only divergence; deliberately NOT merged/pulled to avoid disturbing the test working tree.
- Re-read the real-device-relevant source (unchanged from the emulator-gate logic): `AppauseAccessibilityService.kt`, `InterceptionDecider.kt`, `BurstTracker.kt`. The 2032-primary / 2038-fallback / Activity-fallback chain, `AbandonCooldown` path, and the pause-guard watchdog (1.5 s grace / 30 s hard cap) are all present and consistent with the design intent.
- Build gate passed: `./gradlew testDebugUnitTest` → **78 tests, 0 failures**; `./gradlew assembleDebug` → **BUILD SUCCESSFUL** (refreshed `app/build/outputs/apk/debug/app-debug.apk`). APK ready for install the moment a device appears.
- `git diff --check` on tracked files: clean (no accidental source edits introduced this turn).

**Device-identification gate result (Step 2):**

| Check | Result |
|---|---|
| `adb devices -l` (daemon restarted 3×) | **no devices attached** (empty list) |
| `adb wait-for-device` | timed out (exit 124) — no device ever enumerates |
| USB bus enumeration (PowerShell `Get-PnpDevice`) | only `USB\VID_0489&PID_E0F2` (Foxconn/Atheros WLAN/BT combo) and `USB\VID_0408&PID_5464` (Chicony camera/webcam); **no Android / ADB interface, no `VID_2717` (Xiaomi), no `VID_18D1` (Google)** |
| Wireless ADB | no `adbkey`-hosted TCP listener on :5555; no `adb tcpip` config present |

**Conclusion:** the Xiaomi 2410DPN6CC / HyperOS / Android 16 phone is **not connected** to this sandbox (or not in USB-debugging mode / not authorized). ADB cannot see it, so every real-device scenario (install, AccessibilityService checkpoint, Scenarios A–H, reboot) is blocked.

**Real-device matrix — all NOT TESTED (device absent):**

| Scenario | Result |
|---|---|
| Debug APK install | NOT TESTED |
| AccessibilityService Enabled | NOT TESTED |
| AccessibilityService Bound | NOT TESTED |
| Normal target first open | NOT TESTED |
| 2032 overlay visible | NOT TESTED |
| Continue | NOT TESTED |
| Cancel → no stale repopup | NOT TESTED |
| Genuine reopen | NOT TESTED |
| HyperOS Recents no phantom | NOT TESTED |
| Genuine target after Recents | NOT TESTED |
| Burst suppression semantics | NOT TESTED |
| XHS interception | NOT TESTED (package presence unknown) |
| XHS 2032 visibility | NOT TESTED |
| Hidden/abandoned guard release | NOT TESTED |
| Reopen after guard release | NOT TESTED |
| 2038 fallback | NOT EXERCISED |
| Activity fallback | NOT EXERCISED |
| Battery unrestricted | NOT TESTED |
| 5–10 min background survival | NOT TESTED |
| Lock/unlock | NOT TESTED |
| Reboot | NOT TESTED |

**Blocker resolution path:** user must connect the Xiaomi phone via USB, enable "USB debugging" (Settings → About phone → tap MIUI/HyperOS version 7× → Developer options → USB debugging), and accept the RSA authorization prompt on the phone. Then re-confirm; the SAME Task ID (`APPAUSE-20260824-1012`) continues — the build gate is already green, so install + all scenarios run next.

**Honest boundary:** this §9 entry is a blocker record, NOT a verification result. No PASS/FAIL was fabricated. Steps 3–18 remain to be executed once the device is present.

---

## 10. Xiaomi / HyperOS ADB transport and OEM gate — TASK APPAUSE-20260824-1819 (PARTIAL: transport unstable)

**Status:** the canonical-adb 5-minute baseline monitor passed, but the subsequent real-device session experienced a genuine serial disappearance and scrcpy disconnect. Functional OEM regression was stopped at the pre-test/setup stage; no Appause product bug is claimed from this turn.

### 10.1 Environment stability evidence

| Check | Result | Evidence |
|---|---|---|
| Canonical adb identified | PASS | `C:\Users\Shmily\AppData\Local\Android\Sdk\platform-tools\adb.exe`, version 37.0.0-14910828 |
| Other adb binary | FOUND, not used for direct operations | scrcpy bundle contains `.tools\scrcpy-v4.0\scrcpy-win64-v4.0\adb.exe`, same version; no evidence of version conflict |
| Persistent scrcpy | PARTIAL | one standard `-s 6036d5b --mouse=sdk` session reached the device; later disconnected during transport instability |
| ADB input | PASS | Back, Home, Swipe all exit 0; `shell echo alive` succeeded |
| Initial 5-minute monitor | PASS | 60 polls from 18:23:08 to 18:28:15 stayed `device` with `alive`; no offline/missing state |
| Later transport stability | FAIL | at about 18:32:52 direct shell returned `device not found`; 18:33:02–18:33:12 serial was absent; auto-recovered around 18:33:15 with transport_id 54 |
| scrcpy disconnect | FAIL | after recovery, a new scrcpy session logged `WARN: Device disconnected` at 18:34:12 |
| Server restart | NOT USED | no `adb kill-server` or `adb start-server` was run |
| Reboot | NOT USED | no reboot was performed |
| USB/debug notification correlation | NOT OBSERVED | no reliable visual timestamp was captured; cannot attribute the drop to a notification |

### 10.2 Real-device pre-test checks

- Physical device confirmed: Xiaomi `2410DPN6CC`, Android 16 / API 36, HyperOS `OS3.0`, `ro.kernel.qemu` empty.
- Debug APK install: PASS; `install -r` succeeded. Release package was not modified.
- APK hash verified: `248CDD97F1DF832F3111357FC39071386C09735D8AC9FA709D2C7463CE1936CB`.
- Accessibility: Debug Appause was manually enabled and was `Enabled + Bound`; Release remained disabled; crashed services were empty at the checkpoint.
- Existing Debug `test` group and ordinary/XHS membership: NOT CONFIRMED. The app was still in its normal “Finish setup” flow, and navigation into HyperOS App usage data preceded the transport failure.

### 10.3 OEM regression matrix

All product scenarios were stopped before execution because the transport became unstable:

| Scenario | Result |
|---|---|
| Ordinary intercept / 2032 visibility | NOT TESTED |
| Continue | NOT TESTED |
| Cancel / genuine reopen | NOT TESTED |
| HyperOS Recents replay | NOT TESTED |
| XHS intercept / 2032 attach / human visibility | NOT TESTED |
| AbandonCooldown / guard recovery | NOT TESTED |
| Battery unrestricted / background survival | NOT TESTED |
| Lock/unlock | NOT TESTED |
| Reboot | NOT TESTED |

### 10.4 Root-cause boundary

- **Confirmed:** the physical Xiaomi transport disappeared and later recovered without an ADB server restart; scrcpy also disconnected.
- **Ruled out for this turn:** a conflicting adb version; direct operations used only the canonical SDK adb, and the bundled scrcpy adb matched the version.
- **Not shown:** scrcpy did not cause an ADB server restart; no server restart occurred.
- **Likely but unconfirmed:** HyperOS/USB transport or security-setting instability. The Appause service log also shows repeated `onDestroy`/`onServiceConnected` cycles during setup navigation, but this does not prove the cause of the USB drop.
- **Still unknown:** cable/USB hardware failure, HyperOS notification behavior, and whether setup navigation or the security setting triggered the reconnect.

### 10.5 Changes and boundary

- No Kotlin, Compose, Gradle, interception, OverlayManager, Worker, or Pro source changes.
- No TEST_REPORT or PROGRESS changes before this append; this section records only the transport evidence and incomplete OEM gate.
- Existing local changes remain pre-existing and uncommitted.

---

## 11. Post-reboot transport gate — TASK APPAUSE-20260824-1839 (BLOCKED: transport unstable)

**Status:** post-reboot state was inspected, but the required 5-minute post-reboot transport gate failed before functional OEM regression could begin. No source code was changed and no Appause product bug is claimed.

### 11.1 Post-reboot state

| Check | Result | Evidence |
|---|---|---|
| Physical device | PASS | Xiaomi `2410DPN6CC`, `ro.kernel.qemu` empty |
| Android / HyperOS | PASS | Android 16 / API 36 / HyperOS `OS3.0` |
| Reboot reason | PASS | `ro.boot.bootreason=reboot,userrequested` |
| Serial | PASS | `6036d5b` |
| Debug package | PASS | version `0.5.38-debug`, versionCode 90, still installed |
| Release package | PASS | version `0.5.38`, versionCode 90, unchanged install timestamp |
| Debug AccessibilityService | PASS at checkpoint | Enabled + Bound after reboot; `onServiceConnected SETUP OK` at 18:41:37 |
| Release AccessibilityService | PASS | Not enabled/bound |
| Crashed services | PASS | Empty |
| Battery state | PARTIAL | Release package appears in device-idle whitelist; Debug package was not confirmed as unrestricted |
| Test group / members | NOT CONFIRMED | Opening Debug App through ADB failed during another transport disappearance; no direct DB/UI mutation was made |

### 11.2 Post-reboot transport matrix

| Check | Result | Evidence |
|---|---|---|
| Canonical adb | PASS | SDK platform-tools adb 37.0.0-14910828 |
| Initial device enumeration | PASS | `6036d5b`, transport_id 64 |
| 5-minute post-reboot gate | FAIL | Not completed: transport disappeared during the first functional precondition attempt |
| Serial disappearance | FAIL | `adb -s 6036d5b` returned `device not found` while launching Debug App around 18:42 |
| Recovery | PASS | Later `devices -l` showed the same serial as `device`, transport_id 67, and `shell echo alive` passed at 18:43:07 |
| scrcpy | NOT ESTABLISHED | No stable persistent session after reboot; functional phase was stopped |
| ADB server restart | NOT USED | No `kill-server` or `start-server` |
| Second reboot | NOT USED | Prohibited by this task |

### 11.3 OEM regression matrix

| Scenario | Result | Evidence |
|---|---|
| A. Ordinary intercept | NOT TESTED | Transport failed before target setup was confirmed |
| B. Continue | NOT TESTED | Same blocker |
| C. Cancel | NOT TESTED | Same blocker |
| D. Genuine reopen / bypass recovery | NOT TESTED | Same blocker |
| E. HyperOS Recents replay | NOT TESTED | Same blocker |
| F. XHS / 2032 / human visibility | NOT TESTED | Same blocker |
| G. AbandonCooldown / guard recovery | NOT TESTED | Same blocker |
| H. Background survival | NOT TESTED | Same blocker |
| I. Lock / unlock | NOT TESTED | Same blocker |
| J. Post-reboot persistence | PARTIAL | Accessibility remained enabled and bound at the checkpoint; functional interception was not proven because transport failed |

### 11.4 Causality boundary

- **Confirmed:** the transport instability persisted after a user-initiated reboot; the same serial disappeared during normal Debug App launch and later recovered.
- **Confirmed:** no ADB server restart, second reboot, secure-settings hack, or source change was used.
- **Not established:** whether the cable/USB port, HyperOS security-debugging state, Windows USB transport, or scrcpy interaction caused the disappearance.
- **Not an Appause bug claim:** repeated service lifecycle events and the ADB disappearance were kept separate; stable-transport functional reproduction was not obtained.

---

## 12. Rerun after USB cable change — TASK APPAUSE-20260824-1839 (FAIL: transport still unstable)

The user explicitly requested rerunning the previous post-reboot operation after changing the USB data cable. The same canonical adb, serial, and no-restart/no-reboot rules were used.

| Check | Result | Evidence |
|---|---|---|
| Xiaomi identity | PASS | `2410DPN6CC`, Android 16 / API 36 / HyperOS `OS3.0` |
| Debug AccessibilityService precondition | PASS | Enabled + Bound; Release remained disabled; crashed services empty |
| Persistent scrcpy start | FAIL | `scrcpy -s 6036d5b --mouse=sdk` ended with `WARN: Device disconnected` |
| Transport gate | FAIL | first poll at 18:48:19 reported `MISSING`; 18:48:24 recovered as `device`, transport_id 72 |
| Heartbeat after recovery | PASS | `shell echo alive` succeeded through 18:48:49 |
| 5-minute gate | NOT COMPLETED | stopped immediately after the first disappearance as required |
| ADB server restart | NOT USED | no `kill-server` or `start-server` |
| Reboot | NOT USED | no second reboot |
| HyperOS OEM functional scenarios | NOT TESTED | transport gate failed |

**Conclusion:** changing the cable did not establish a trustworthy transport. The scrcpy disconnect and serial disappearance remain environment evidence, not an Appause product failure. No source changes were made.

---

## 14. Xiaomi / HyperOS real-device OEM regression — TASK APPAUSE-20260824-1909 (PARTIAL)

This turn resumed the gate on the replacement cable and the new physical USB port after the Category 5 transport result in §13. No Kotlin, Compose, Gradle, Worker, database, or settings changes were made.

### 14.1 Baseline and preconditions

- Branch: `main`; HEAD: `eb29d1e47e43505d1e3c03f5f05cb0d7fe3da8a6`.
- GitHub state: `main` is behind `origin/main` by one commit; no commit or push was made.
- Working tree: pre-existing modifications and untracked files were preserved; no source files were changed this turn.
- Physical device: Xiaomi `2410DPN6CC`, Android 16/API 36, HyperOS `OS3.0`, serial `6036d5b`.
- Transport preflight: PASS. `adb devices -l` stayed `device`, `shell echo alive` passed, and transport ID remained 73. One local sandbox process-start permission error required an approved read-only elevated retry; no device disconnect occurred.
- Debug package: `com.appause.android.debug`, `0.5.38-debug`, versionCode 90. Release package remained installed and unchanged.
- Accessibility: Debug Appause enabled and bound; Release disabled and not bound; crashed services empty at the functional checkpoint.
- Battery/background: Debug Appause was set to HyperOS `No restrictions` through the normal UI; Debug process remained alive while backgrounded.
- Normal UI group confirmation: active `test` group, cooldown 10 seconds, two selected apps: `rednote`/XHS (`com.xingin.xhs`) and Bilibili (`tv.danmaku.bili`).

### 14.2 OEM regression matrix

| Scenario | Result | Evidence / boundary |
|---|---|---|
| A. Ordinary intercept | PASS | Bilibili produced an Appause `ACCESSIBILITY_OVERLAY` type 2032; user physically confirmed the page was visible. |
| B. Continue | NOT TESTED | No clean re-armed overlay with a controllable button was available after the first session; no tap was claimed. |
| C. Cancel | NOT TESTED | Same session-state limitation; no tap was claimed. |
| D. Genuine reopen / bypass recovery | PARTIAL | Reopen attempts were held by leave-window/same-package dedup; a clean genuine-reopen PASS was not established. |
| E. Recents replay | NOT TESTED | Recents-specific replay was not isolated from the existing overlay/session state. |
| F. XHS / 2032 / human visibility | PASS | XHS produced type 2032; user physically confirmed the Appause page was visible. |
| G. AbandonCooldown guard | PASS (machine evidence) | Logs recorded `Cooldown abandoned` after leaving to `com.miui.securitycenter`, then `Bypass cleared`; watchdog also released a stale guard. Human button outcome was not inferred. |
| H. Background survival | PASS | After Home plus 12 seconds, Debug service remained enabled/bound and process PID `11838` remained alive. |
| I. Lock / unlock | PASS (machine evidence) | Power lock/unlock returned to `mWakefulness=Awake`; Debug AccessibilityService remained enabled/bound. |
| J. Reboot persistence | PARTIAL / NOT RUN | Reboot was intentionally not performed. Only current enabled/bound state was observed; persistence across reboot remains unproven. |
| K. Repeated / burst resistance | PARTIAL | Rapid Bilibili/XHS/Home/Bilibili switching produced no additional intercept; logs showed overlay-present skip and abandonment behavior. A clean burst run beginning without a pre-existing overlay was not obtained. |

### 14.3 Observed implementation semantics and risks

- The working OEM path is confirmed as type 2032 (`ACCESSIBILITY_OVERLAY`), with human visibility confirmed on both Bilibili and XHS.
- During the same target session, Appause intentionally suppresses duplicate events: logs showed `RESUME ... returned within leave window`, `2.6 dedup skip`, and `SKIP: cooldown overlay is showing`.
- Leaving to another package while the cooldown is active triggers abandonment cleanup and clears the target bypass. A watchdog can also release a stale guard when no usable overlay remains.
- Repeated service `onDestroy`/reconnect cycles were observed during HyperOS setup/navigation. They did not coincide with transport loss in this stable-port run, but remain an OEM lifecycle risk.
- Continue, Cancel, Recents replay, and reboot persistence still require a fresh controllable session. The incomplete scenarios are verification gaps, not source bug claims.

### 14.4 Changes and final boundary

- Files changed this turn: `TEST_REPORT.md` and `PROGRESS.md` only.
- No source/build/test code changes; no build was rerun because the installed Debug APK and source were unchanged during this verification turn.
- No commit, push, merge, tag, or release.

---

## 15. Remaining HyperOS interaction regression — TASK APPAUSE-20260824-1938 (PARTIAL: one confirmed OEM interaction bug)

This turn continued the physical Xiaomi/HyperOS gate. It did not repeat the previously passed initial 2032 visibility checks except where a clean session was required.

### 15.1 Git discrepancy and baseline

- Branch: `main`; HEAD: `eb29d1e47e43505d1e3c03f5f05cb0d7fe3da8a6`.
- `origin/main`: `a8b97d8caa0fb5c03ed4cb783fc0ebca05b01938`; local `main` remained behind by one commit.
- Starting working tree had the same pre-existing modified and untracked files as §14; nothing was staged, restored, deleted, or overwritten.
- `app/src/main/java/com/appause/android/interception/InterceptionDecider.kt` is currently present on disk, untracked, not ignored, absent from the index, and has no Git history (`git ls-files`/`git log --all -- <path>` returned no entry). It was not restored or modified this turn. The previous handoff's omission was inaccurate; the cause of that reporting discrepancy cannot be proven from Git history.

### 15.2 Device and clean-session evidence

- Physical Xiaomi `2410DPN6CC`, Android 16/API 36, HyperOS device property set remained consistent with the prior gate; serial `6036d5b`.
- ADB transport stayed `device`, transport ID 73, and `shell echo alive` passed throughout the turn. Existing scrcpy process remained running; no disconnect occurred.
- Debug AccessibilityService remained enabled and bound; Release remained disabled; crashed services were empty.
- Debug battery policy remained unrestricted/allowed; `test` group remained 10 seconds with XHS and Bilibili members.
- Read-only implementation review confirmed: leave window is 180 seconds; same-package dedup requires unchanged foreground plus repeated event; Cancel clears bypass and applies an 800 ms stale-event suppression; watchdog maximum guard hold is 30 seconds.

### 15.3 Regression matrix

| Scenario | Result | Evidence |
|---|---|---|
| A. Bilibili / 2032 visibility | PREVIOUS PASS | User physically confirmed the Bilibili 2032 page was visible in §14. |
| B. Continue | PASS | After the 10-second countdown, Continue was tapped on the real screen. Overlay disappeared; Bilibili remained foreground; logs recorded `Bypass started` and `Session start`; same-session repeat logged `Session start ignored (already active)`. |
| C. Cancel | PASS | XHS clean overlay was cancelled. Overlay disappeared and HyperOS Launcher became the resulting foreground. Bypass was cleared; reopening XHS after the grace period produced a new `INTERCEPT`/2032 overlay. |
| D. Genuine reopen / recovery | PASS | Bilibili was left in Chrome for the full 180-second window; logs recorded `Leave cooldown fired`, `Bypass cleared`, and `Re-armed`; later reopen produced a new genuine `INTERCEPT`/2032 overlay. |
| E. HyperOS Recents | PASS | After Cancel/Home, HyperOS Recents displayed the XHS task; selecting it returned to XHS and produced one `INTERCEPT`/2032 overlay with no duplicate stack or loop. |
| F. XHS / 2032 visibility | PREVIOUS PASS | User physically confirmed XHS visibility in §14. |
| G. AbandonCooldown | PREVIOUS PASS | §14 recorded abandonment and bypass cleanup. |
| H. Short-window background survival | PREVIOUS PASS | §14 recorded the explicitly short-window result; no long-duration claim added. |
| I. Lock/unlock | PREVIOUS PASS | §14 recorded service survival after lock/unlock. |
| J. Reboot persistence | PASS with boundary | Debug AccessibilityService was enabled/bound immediately after the preceding user reboot; no manual re-enable or service repair occurred in this or the later functional sessions, and interception subsequently succeeded. This proves enabled-state persistence plus post-reboot functional interception, but not uninterrupted binding across every transport/lifecycle event. Battery policy changes are separate and occurred through normal UI. No reboot was performed this turn. |
| K. Rapid switching / duplicate resistance | FAIL | A controlled Home → Chrome → Bilibili → XHS → Home burst created no duplicate stack, but the XHS overlay remained visibly over Home after the target was abandoned. The 30-second watchdog logged `exceeded max hold — releasing guard` but did not dismiss the attached/visible overlay. It was only removed after a later user-equivalent Cancel tap. |

### 15.4 Current semantics and confirmed bug

- Continue starts a temporary target bypass and leaves the target app usable; same-package events are suppressed while the session is active.
- Cancel clears bypass, sends the user to Home, suppresses the stale target event briefly, and allows a later genuine reopen to intercept again.
- Returning within the 180-second leave window resumes the bypassed session; after the timer fires, the target is re-armed and the next genuine open intercepts.
- Recents returning to a re-armed grouped app is treated as a legitimate foreground transition and intercepted once.
- Same-package dedup and burst suppression prevent repeated decisions, but system-package events are handled before the abandonment branch.
- **Confirmed product/OEM interaction bug:** when a cooldown is visible and the user rapidly switches to Home, HyperOS emits system-package events that are skipped before abandonment cleanup. The watchdog releases the logical guard but the visible 2032 overlay remains over Home beyond the watchdog period. This is user-visible functional harm on the physical device. No fix was attempted.

### 15.5 Verification and boundary

- Real Xiaomi device: PASS for tested flows; K has confirmed FAIL.
- ADB transport: PASS; no serial disappearance or scrcpy disconnect.
- `git diff --check`: PASS; only line-ending warnings for pre-existing files were reported.
- Build: NOT REQUIRED; no source changed.
- Deviation: testing stopped after the confirmed K bug per task stop condition. No further retries, reboot, source modification, or internal-state manipulation were performed.

---

## 13. USB-port isolation — TASK APPAUSE-20260824-1852 (PASS: Category 5)

The user moved the phone and new cable to a different physical USB port. This task intentionally tested transport only; no Appause UI or interception scenario was run.

### 13.1 Setup and baseline

- Device: Xiaomi `2410DPN6CC`, Android 16 / API 36, HyperOS `OS3.0`.
- Serial: `6036d5b`.
- Initial transport ID: 73.
- USB properties: `sys.usb.config=adb`, `persist.sys.usb.config=adb`, `adb_enabled=1`, `development_settings_enabled=1`.
- Initial state: `device`; no unauthorized/offline prompt was observed.
- Canonical adb only: `C:\Users\Shmily\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- scrcpy: `.tools\scrcpy-v4.0\scrcpy-win64-v4.0\scrcpy.exe`, `--mouse=sdk`.
- Cable: the replacement cable from the previous rerun; port: different physical USB port, exact port type not recorded.

### 13.2 Phase results

| Phase | Result | Evidence |
|---|---|---|
| Idle / low-load ADB | PASS | 60/60 polls over about 5 minutes; serial continuously present, state `device`, heartbeat alive, transport ID fixed at 73 |
| Moderate ADB load | PASS | 36/36 polls over about 3 minutes; repeated logcat, dumpsys activity, process queries, and heartbeat all succeeded; transport ID fixed at 73 |
| scrcpy | PASS | scrcpy started successfully and stayed connected for about 3 minutes while heartbeat remained alive; no disconnect warning |
| Physical sensitivity | NOT RUN | no cable/connector movement was performed |

### 13.3 Failure timeline

No failure occurred in this task.

- Last known good: 18:55:18, transport ID 73.
- First missing/offline state: none.
- Recovery: not applicable.
- New transport ID: none; ID 73 remained fixed through all phases.

### 13.4 Classification

**Category 5 — Fully stable on new USB port.** Idle ADB, moderate read-only ADB traffic, and scrcpy all remained stable. This makes the previous USB port, its contact, or its controller path a strong suspect, but does not prove a single hardware root cause.

### 13.5 Appause boundary

This task provides no evidence of an Appause product bug. AccessibilityService and Appause were not functionally exercised by design. A later task may authorize resuming HyperOS OEM regression now that transport is stable on this port.

---

## 16. Cooldown overlay cleanup — TASK APPAUSE-20260824-1958

### 16.1 Fix scope and implementation

- Confirmed the prior real-device failure: a visible type 2032 cooldown could remain over Home after a rapid target → Chrome → Home transition; the watchdog released only the logical guard and did not remove the attached overlay.
- Added explicit launcher identification and a UsageStats foreground confirmation to the pre-group decision input.
- A confirmed Home transition now takes the `AbandonCooldown` path before broad system-package filtering, dismissing the overlay and clearing the temporary bypass. This remains effective even if countdown completion already started a bypass before Continue was tapped.
- Unconfirmed launcher/system noise remains `SkipSystem`, so incidental OEM events do not dismiss a valid cooldown.

### 16.2 Automated verification

| Check | Result | Evidence |
|---|---|---|
| `testDebugUnitTest` | PASS | 80 tests, 0 failures, 0 errors; `InterceptionDeciderTest` contains 23 tests including confirmed-Home and unconfirmed-noise cases. |
| `assembleDebug` | PASS | Debug APK built successfully; only the existing Android SDK XML-version warning appeared. |
| `git diff --check` | PASS | No whitespace errors; Git emitted only line-ending/config warnings. |

### 16.3 Xiaomi / HyperOS verification

Device: Xiaomi `2410DPN6CC`, serial `6036d5b`, Android 16 / HyperOS. The newly built Debug APK installed successfully; AccessibilityService remained enabled and bound.

| Scenario | Result | Evidence / boundary |
|---|---|---|
| Primary rapid Home abandonment | PASS | Bilibili 2032 overlay was attached, then Chrome → Home was performed. Window inspection showed no remaining Appause/2032 window, Home was foreground, and logs recorded `Overlay dismissed`. |
| Continue | PASS | Bilibili overlay was present; Continue tap left Bilibili foreground and no overlay remained. |
| Cancel | PASS | XHS overlay was present; Cancel returned to Home and dismissed the overlay. |
| Genuine reopen | PASS | XHS reopened after Cancel and produced a new 2032 overlay. |
| XHS 2032 attachment | PASS / visual confirmation pending | Current build attached a 2032 overlay over XHS; physical screen visibility was not separately user-confirmed in this run. |
| Recents | NOT TESTED | ADB disconnected immediately before the Recents command; no ADB restart or device-setting change was attempted. |

### 16.4 Remaining risks and boundaries

- Recents smoke remains to be rerun after the physical USB connection is restored.
- Current-build human visual confirmation for Bilibili/XHS should be repeated when the device is available; dumpsys proves attachment and type, not what the user saw.
- No Worker/payment/release validation was performed. No commit, push, tag, merge, or release was performed.

---

## 17. Final HyperOS overlay boundary verification — TASK APPAUSE-20260828-1608

### 17.1 Baseline and transport boundary

- Baseline rechecked: branch `main`; HEAD `eb29d1e47e43505d1e3c03f5f05cb0d7fe3da8a6`; `origin/main` `a8b97d8caa0fb5c03ed4cb783fc0ebca05b01938`; tracking state `main...origin/main [behind 1]`.
- The substantial modified and untracked working-tree state was preserved. No source, test, Gradle, configuration, database, DataStore, or Worker files were changed.
- Existing Debug APK remained at `app/build/outputs/apk/debug/app-debug.apk`, last built after APPAUSE-20260824-1958. No source/test changes occurred after that build, so source confidence was high; no rebuild was needed for this verification-only task.
- Target: Xiaomi `2410DPN6CC`, Android 16 / API 36, HyperOS, expected serial `6036d5b`.
- ADB was not available at the start of the device check and returned an empty device list after the tool invocation. The command output indicated the ADB daemon was not running and started it automatically; no further ADB restart, reboot, or device-setting change was attempted. This is recorded as a transport blocker.
- scrcpy, AccessibilityService state, battery policy, group membership, and Usage access state could not be re-read because the device was unavailable.

### 17.2 Verification matrix

| Scenario | Result | Evidence / boundary |
|---|---|---|
| Current Debug Bilibili 2032 human-visible | NOT TESTED | Device unavailable; no user visual confirmation. |
| Current Debug XHS 2032 human-visible | NOT TESTED | Device unavailable; no user visual confirmation. |
| Recents smoke | NOT TESTED | Device unavailable. |
| Home before countdown completes | NOT TESTED | Device unavailable. |
| Home after countdown completes, before Continue | NOT TESTED | Device unavailable. |
| Usage access OFF → ordinary interception | NOT TESTED | Usage access state could not be read or changed through normal UI. |
| Usage access OFF → Home abandonment | NOT TESTED | Critical optional-permission boundary was not exercised; no product conclusion is drawn. |
| Incidental system noise preserves valid overlay | NOT TESTED | Device unavailable. |

### 17.3 Runtime semantics and blockers

No new runtime semantics were observed in this task. The post-fix semantics documented in §16 remain the latest evidence, but the Usage-access-OFF boundary and current-build human visibility were not revalidated here.

**Confirmed blocker:** Xiaomi real-device verification was blocked by unavailable ADB transport before any scenario could begin. The automatic ADB daemon start is recorded as a process-side transport event, not as evidence of device recovery.

**No new confirmed product blocker:** no product scenario ran, so this task neither confirms nor clears the optional Usage access dependency risk.

---

## 18. Final HyperOS overlay boundary verification — continuation

### 18.1 Device and permission state

- Xiaomi / HyperOS real-device verification resumed on `2410DPN6CC`, Android 16 / API 36, serial `6036d5b`.
- ADB was available as `device`, transport ID 1, and `shell echo alive` passed.
- Installed Debug package: `com.appause.android.debug`, version `0.5.38-debug` / versionCode 90. Debug AccessibilityService was enabled and bound; Release service was not independently rechecked in this continuation.
- Original Usage access state: ON.
- Test state: OFF, changed only through the normal HyperOS Usage access settings UI.
- Final restored state: ON, confirmed by the system switch as `checked="true"` after completing the normal HyperOS risk confirmation dialog.
- No scrcpy check, battery-policy check, or group reconfiguration was performed.

### 18.2 Verification result and stop condition

| Scenario | Result | Evidence |
|---|---|---|
| Usage access OFF → ordinary interception | FAIL | With Usage access visibly OFF, Bilibili was launched from Home and remained foreground, but no 2032 Appause overlay appeared and no new Appause interception log was produced. |
| Usage access OFF → Home abandonment | NOT TESTED | Stopped immediately after the ordinary-interception failure, as required. |
| Usage access restoration | PASS | Normal HyperOS UI restored the switch to ON; final UI inspection confirmed `checked="true"`. |

This is a confirmed product-requirement mismatch requiring a separate implementation investigation: core interception did not work in the tested no-Usage-access state, despite Usage access being documented as optional. The Home-abandonment behavior with Usage access OFF remains untested because the prerequisite interception failed.

### 18.3 Scope boundary

Per the stop condition, Bilibili/XHS human-visibility, Recents, pre/post-countdown Home abandonment, and incidental-system-noise scenarios were not run in this continuation. No source or test files were changed; no commit, push, merge, tag, or release was performed.

---

## 19. Optional Usage access A/B diagnosis — TASK APPAUSE-20260828-1634

### 19.1 Controlled preconditions

- Xiaomi / HyperOS real-device verification: `2410DPN6CC`, Android 16 / API 36, serial `6036d5b`.
- ADB was stable as `device`; transport ID 1; `shell echo alive` passed.
- Appause normal UI confirmed the active `test` group, 10-second cooldown, two selected apps (`rednote` and `bilibili`), and Bilibili membership.
- The initial service state was invalid for this task: release `com.appause.android` was enabled while Debug `com.appause.android.debug` was disabled. This was corrected only through normal Accessibility settings: release OFF, Debug ON + bound.
- No stale overlay remained after normal Cancel/Home cleanup before the valid ON control.

### 19.2 A/B diagnosis

| Signal | Usage ON | Usage OFF |
|---|---|---|
| Accessibility event arrived | PASS, inferred from the successful interception path | PASS, inferred from the successful interception path after Debug service was re-enabled |
| `eventCount` changed | NOT independently read | NOT independently read |
| Bilibili package observed | PASS by successful target interception | PASS by successful target interception |
| Decision pipeline entered | PASS by successful target interception | PASS by successful target interception |
| Decision | INTERCEPT | INTERCEPT |
| 2032 attached | PASS | PASS |
| Human visible | PASS, screenshot showed the complete pause UI | PASS, screenshot showed the complete pause UI |

Usage ON control succeeded with the corrected Debug service. A first Usage OFF attempt produced no Bilibili event, but `dumpsys accessibility` then showed Debug disabled and only the unrelated auto-clicker enabled; that attempt was invalid as a Usage A/B control. After re-enabling Debug through normal UI while Usage remained OFF, a clean Chrome → Home → Bilibili transition produced one visible 2032 overlay.

### 19.3 Root cause conclusion

The prior APPAUSE-20260828-1608 failure was explained by an invalid service precondition, not by the decider collapsing UsageStats `null` into “candidate not foreground”:

`release AccessibilityService enabled + Debug AccessibilityService disabled`
→ no Debug service receives the Bilibili window event
→ no Debug decision pipeline entry
→ no overlay

With the correct Debug service enabled, Usage access OFF still allowed ordinary Bilibili interception. The local code already preserves the required distinction: `ForegroundChecker.getForegroundPackage()` returns nullable unknown, while the main interception path trusts the Accessibility event and does not require UsageStats confirmation. Usage confirmation is only used for the confirmed-Home abandonment branch.

### 19.4 Result and scope

- No production fix was required; no source or test files were changed.
- No new product blocker was confirmed. The optional Usage access contract passed under valid service preconditions.
- Recents, Usage OFF → Home abandonment, and incidental-system-noise scenarios were not repeated because the controlled OFF result passed and the task did not authorize expanding beyond the diagnosis.
- Usage access original state: ON; test state: OFF; final restored state: ON.

## 20. Async signing serialization verification (2026-08-28)

The earlier local harness queued each complete fake-DO request before invoking
the real object. That was stronger than the Cloudflare runtime model and could
hide interleaving across ordinary `await` points. The prior redeem sequence was
reviewed as:

```text
DO storage.get("record")
→ maxDevices/device decision
→ await importPrivateKeyPem + crypto.subtle.sign
→ DO storage.put("record")
```

The production object now wraps the whole activation-code action in
`state.blockConcurrencyWhile(...)`, including legacy KV bootstrap, signing,
redeem, and unbind. The updated harness does not queue complete requests; it
models ordinary request interleaving and provides a delayed signer. The test
observes that the second same-code request is held while the first request is
inside signing, then verifies both bindings persist after release.

**Results: 31 checks, 0 failures.** This includes delayed-signer capacity and
final-slot tests, concurrent same-device redeem, concurrent self/admin unbind,
signing-failure no-slot-consumption, and concurrent legacy bootstrap. It is a
faithful mock of the request-guard behavior, not a workerd/Cloudflare deployed
runtime.
## 21. Worker local runtime pre-deploy validation (2026-08-29)

Task `APPAUSE-20260828-2307` ran the Worker through Wrangler 3.114.17 without
deployment. `wrangler deploy --dry-run --outdir` passed and recognized the
Durable Object binding/class, SQLite migration, and KV binding. An actual
local Miniflare/workerd runtime started on `127.0.0.1:8788` using a fresh
local persistence directory and ephemeral test credentials.

Real local HTTP checks passed for admin code generation, redeem, same-device
idempotency, self-unbind, admin-unbind, two-device capacity, final-slot
contention, concurrent same-device redeem, and redeem/unbind overlap. A
synthetic legacy KV record bootstrapped into the DO while preserving its
existing device, `maxDevices=2`, and `expiresInDays=14`; subsequent DO
operations passed. Reusing the same local persistence directory after a
normal runtime restart preserved the bound device and capacity behavior.

The Worker suite remained **31 passed, 0 failed**; all requested Node syntax
checks and `git diff --check` passed. This is local runtime evidence only and
does not prove production Cloudflare behavior. Production deployment, remote
Wrangler mode, production KV/DO, production secrets, and real activation
codes were not used.

## 22. External local Worker runtime and persistence verification (2026-08-29)

### 22.1 Evidence boundary

This section records a manual/session verification performed outside Codex
Bridge in ordinary Windows PowerShell. It is not Codex execution, CI,
production Cloudflare verification, or remote Wrangler verification.

- Node: `v24.15.0`
- Locked local Wrangler: `3.114.17`
- Local command: `wrangler dev --local --port 8788 --persist-to .wrangler\external-runtime`
- Startup output: `Ready on http://127.0.0.1:8788`
- Simulated local bindings: Durable Object `ACTIVATION_CODES` and KV
  `APPAUSE_CODES`
- No production secrets, Cloudflare resources, remote mode, or real activation
  codes were used.

### 22.2 HTTP and persistence checks

| Check | Result | Evidence / boundary |
|---|---|---|
| Local Worker startup | PASS | Wrangler served on `127.0.0.1:8788` |
| `GET /` | PASS | HTTP 404, `{"error":"not_found"}`; proves runtime/routing response |
| Synthetic nonexistent `POST /api/redeem` | PASS | `APPAUSE-TEST-TEST` + `external-review-device` returned HTTP 404, `{"error":"invalid_code"}` |
| `/admin/gencode` | PASS | Local dummy `ADMIN_KEY` generated synthetic code `APPAUSE-ZKVL-E3PW` |
| Pre-restart `/admin/unbind` | PASS | Synthetic unbound device returned `{"error":"device_not_bound"}`, proving the DO record existed |
| Restart persistence | PASS | Wrangler stopped and restarted with the same `.wrangler\external-runtime` path and dummy admin key; identical lookup again returned `device_not_bound` |

`/admin/gencode` initializes the new record directly in the per-code SQLite
Durable Object. It does not write the new code to legacy KV. Consequently,
after restart, `device_not_bound` demonstrates that the DO record survived;
a fresh runtime without that persisted record would fall through to legacy KV
and return `invalid_code`.

This test proves local DO record persistence only. It does not exercise
successful JWT signing because no `APPAUSE_PRIVATE_KEY` was supplied. The
successful JWT signing path, production/remote Cloudflare resources, and
production secrets remain **NOT TESTED** in this session.

### 22.3 Bridge diagnostic conclusion

The same locked Worker and dependencies started normally in ordinary Windows
PowerShell, while the earlier Codex/Local Codex Bridge environment reproduced
`std::terminate` even for a minimal Worker with no bindings, Durable Object,
SQLite migration, or secrets. This strongly isolates that failure to the
Bridge/Codex execution environment or its Wrangler-to-workerd process
interaction, rather than a general Appause Worker, Node, or Wrangler failure
on the host.

## 24. v0.5.39 post-release curated validation summary

### Automated and local validation

- Android debug unit tests: 80/80 PASS. Worker tests: 31/31 PASS.
- The v0.5.39 release candidate passed `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintRelease`, `:app:assembleRelease`, `:app:bundleRelease`, `git diff --check`, and release packaging/signing verification.
- Release output is non-debuggable. Diagnostics UI, its ViewModel/collector, debug-only provider/resources, and test controls are isolated from Release. Feedback remains available in Release and uses a structured local status report plus user-invoked system sharing.

### Worker and Pro findings

- Synthetic production Pro E2E passed first/same-device redeem, capacity enforcement, unbind/reuse, and Durable Object state consistency without using real user or payment data.
- Tracked source confirms `expiresInDays` is optional. The Worker adds JWT `exp` only for records with that value; Android `LicenseVerifier` accepts a missing `exp` and validates an `exp` claim when present. There is no automatic refresh or renewal UI. After expiry, user-initiated redeem or import of another valid token may be needed.
- The observed 86400-second lifetime belonged to a deliberately synthetic one-day activation record and must not be read as a universal production TTL.

### Physical-device and release evidence

- On a physical Xiaomi/Android 16 device, post-release smoke passed data-preserving install, package/version checks, installed-artifact identity, launch/process checks, Accessibility service state, relevant app-op checks, and app-specific crash/ANR scanning.
- The final configured-target interception path was not newly exercised because the device was at lockscreen/AOD and the target could not be safely inferred without changing private configuration. Earlier RC evidence remains historical and is not presented as a new post-release interception pass.
- The GitHub Release asset now uses the public name `Appause-v0.5.39.apk`; size and SHA-256 remain unchanged.

### Follow-up risks

- Public naming convention is `Appause-v<version>.apk`, while `scripts/make_release.py` still emits a code-suffixed internal artifact. Resolve that mismatch in a future source/script task.
- Continue to distinguish automated/build evidence, inherited RC evidence, and physical-device smoke. The final interception manual gap remains open.

## 25. P0 fail-safe Home overlay follow-up (2026-09-04)

Task `APPAUSE-20260904-2323` continued the existing uncommitted P0 overlay
work. The Home confirmation path now keeps the event-time ordering guard and
the first UsageStats cross-check, but schedules one bounded fail-open retry when
UsageStats still reports the intercepted target. If no newer real-app
accessibility event arrives, the retry treats the already observed launcher
event as authoritative and removes the standalone 2032 overlay. This addresses
the stale-foreground case without removing the previous Recents double-flash
protection. A focused JVM test covers the retry decision.

| Check | Result | Evidence / boundary |
|---|---|---|
| Focused JVM tests | PASS | `HomeTransitionPolicyTest`, `OverlayPresentationPolicyTest`, and `InterceptionDeciderTest`; Gradle reported 30 actionable tasks successful |
| `assembleDebug` | PASS | Gradle 8.11.1, JDK 17, installed SDK API 35; process-local SDK override only |
| Alternate AVD setup | PASS | Disposable `Appause_P0_API34_Temp` created from installed API 34 `google_apis/x86_64` image; no existing AVD was wiped or modified |
| APK install and MainActivity launch | PASS | Fresh debug APK installed successfully only on emulator-5558; MainActivity became the resumed activity |
| Cancel/Home/watchdog/reopen/double-flash | NOT TESTED | Runtime scenario execution is the next checkpoint |
| Xiaomi/HyperOS physical validation | NOT TESTED | User phone unavailable; no physical device was used |

The source/test changes remain local and uncommitted. No commit, push, merge,
tag, release, or deploy occurred.

## 26. P0 fail-safe emulator acceptance continuation (2026-09-05)

Task `APPAUSE-20260904-2323` continued on disposable API 34 emulator
`emulator-5560` only. The physical Xiaomi was not targeted. The existing
`P0Testv` Chrome target was used without clearing app data.

### Implementation and contract checks

- Added a separate manifest package-visibility query for
  `ACTION_MAIN` + `CATEGORY_HOME`; the existing `ACTION_MAIN` + `CATEGORY_LAUNCHER`
  query remains for app discovery. The dynamic service resolver then reported
  `[com.google.android.apps.nexuslauncher, com.android.settings]`.
- The Android SDK 36.1 source confirms that `FLAG_REQUEST_FILTER_KEY_EVENTS`
  requires `canRequestFilterKeyEvents`. A temporary metadata + `onKeyEvent`
  experiment reached dumpsys `capabilities=8`, but emulator HOME still produced
  no `onKeyEvent` callback or `System navigation escape 3` log, and left 2032
  attached. That ineffective hook and its metadata were removed rather than
  stacked with the event path.
- The bounded fail-open path now preserves a pending Launcher confirmation
  when a later quick-search/system helper event arrives. After watchdog expiry,
  a resolved Launcher event is allowed to dismiss an orphaned attached surface
  even though the logical pause guard has already been released.

### Verification

| Check | Result | Evidence / boundary |
|---|---|---|
| Focused JVM tests | PASS | `OverlayPresentationPolicyTest` and `HomeTransitionPolicyTest` |
| `assembleDebug` | PASS | JDK 17, Gradle 8.11.1, installed SDK; current APK output rebuilt |
| Data-preserving emulator install | PASS | `adb -s emulator-5560 install -r`; no uninstall/clear/wipe |
| Service binding | PASS | Appause enabled and bound in `dumpsys accessibility` |
| 2032 geometry | PASS | `fitTypes=NAVIGATION_BARS`, requested 1080x1920, frame [0,0][1080,1920]; bottom nav inset [0,1857][1080,1920] |
| Watchdog repetition 1 | PASS | fresh 2032=1; after 34s 2032=1 + watchdog expiry log; Home -> Launcher top, `Confirmed system Home`, `Overlay dismissed`, 2032=0 |
| Watchdog repetition 2 | PASS | independent fresh 2032=1; after 34s 2032=1 + expiry log; Home -> Launcher top and 2032=0 |
| Gesture Home | PASS | `navigation_mode=2`, fresh 2032=1 -> Launcher top and 2032=0 |
| Gesture edge Back | PASS | left-edge ADB swipe, fresh 2032=1 -> Launcher top and 2032=0 |
| Three-button Home/Back/Recents | PASS | `navigation_mode=0`; each fresh 2032=1 -> 2032=0; Recents showed `recents_animation_input_consumer` and `isHomeRecentsComponent=true` |
| Gesture Recents human swipe/hold | NOT TESTED | ADB injected touch is non-authoritative for this interaction |
| Frozen-process/true ANR safety | NOT TESTED | Emulator runs did not prove behavior when Appause or the target is genuinely frozen |

The final emulator navigation mode was restored to `navigation_mode=2` and the
Launcher was left foreground. Physical-device Xiaomi validation remains a
separate evidence boundary. The source and documentation changes remain local
and uncommitted; no commit, push, tag, release, or deploy occurred.

## 27. Physical Xiaomi P0 follow-up: read-only accessibility stop diagnosis (2026-09-05)

This was a bounded diagnostic pass on the explicitly identified physical
Xiaomi `6036d5b` (`2410DPN6CC`, Android 16/API 36). No accessibility setting,
app data, package, or user setting was changed.

| Check | Result | Evidence / boundary |
|---|---|---|
| Physical identity | PASS | `adb devices -l`: `6036d5b`, model `2410DPN6CC`; emulator-5560 was not mutated |
| Appause package present | PASS | `installed=true`, `versionName=0.5.39-debug`, versionCode 91, data directory present |
| Current top/window state | PASS | `com.miui.home/.launcher.Launcher` top; no Appause `ty=2032` window |
| Accessibility setting | FAIL / blocker | `accessibility_enabled=1`, but enabled list contains only the preserved autoclicker; Appause is not enabled or bound |
| Autoclicker preservation | PASS | `com.zidongdianji.autoclicker/.AutoClickService` remains enabled and bound |
| Appause component registration | PASS | Package resolver table contains AppauseAccessibilityService with `BIND_ACCESSIBILITY_SERVICE`; HyperOS lacks `cmd package resolve-service` |
| Package state | OBSERVED | `suspended=false`, `hidden=false`, `enabled=0`, `stopped=true`; this is current state, not a causal explanation |
| Latest process exit | OBSERVED | `22:03:40.108`, `USER REQUESTED`, `FORCE STOP`, `SwipeUpClean` |
| Disable/death cause | UNKNOWN | Remaining filtered system logs contain no direct actor/cause linkage; no causal inference made |

The latest exit record proves that HyperOS recorded a `SwipeUpClean` force-stop
for the Appause process, but does not prove who initiated it or that it caused
the service to be disabled. The service was not restored because the user
explicitly reserved that action for a later normal Settings UI step.

Physical acceptance boundaries remain: Home previously passed from a fresh
2032 blocker; injected `KEYCODE_BACK` did not dismiss a valid blocker and was
not treated as a product verdict; a valid actual system Back-button tap was not
completed before service disable. Physical Recents and physical >32-second
watchdog/Home validation are NOT TESTED after a fresh blocker. No duplicate or
flash regression was authoritatively tested in this stopped state. Emulator
three-button/gesture and watchdog passes remain emulator-only evidence.

The earlier `uiautomator dump` was excluded from this pass because it caused
`onDestroy -> onCreate -> onServiceConnected` and contaminated the physical
session/re-arm state. No service reload/toggle, secure-setting write,
uninstall, clear, wipe, force-stop shortcut, source change, commit, push, tag,
release, or deploy was performed.

## 28. Physical Xiaomi package-identity boundary after manual re-enable (2026-09-05)

The user restored Appause through normal Xiaomi Accessibility Settings. This
read-only baseline confirmed the preserved autoclicker and an Appause service
were both Enabled+Bound, with three-button navigation (`navigation_mode=0`) and
no Appause 2032 before testing.

| Check | Result | Evidence / boundary |
|---|---|---|
| Bound Appause identity | OBSERVED | `com.appause.android/.service.AppauseAccessibilityService` (release package) |
| Current local P0 debug package | NOT BOUND | `com.appause.android.debug` remains installed at `0.5.39-debug`, but is absent from enabled/bound services |
| Bound release package | OBSERVED | `0.5.39`, versionCode 91, lastUpdateTime `2026-09-01 16:39:19` |
| Current debug package | OBSERVED | `0.5.39-debug`, versionCode 91, lastUpdateTime `2026-09-05 20:36:47` |
| Accessibility/autoclicker | PASS | Both release Appause and autoclicker Enabled+Bound; autoclicker unchanged |
| Navigation baseline | PASS | `navigation_mode=0`; Launcher after cleanup |
| Fresh current-P0 2032 | NOT TESTED | Normal Bilibili launch under release service produced no fresh 2032 evidence |
| Release overlay observation | OBSERVED, NOT CURRENT-P0 VERDICT | WindowManager showed release `APPLICATION_OVERLAY` type 2038; no Back tap was sent |
| Home cleanup | LIMITATION | Launcher became top, but the release 2038 window remained attached |

Because the manually selected service is the older release package, no Back,
Recents, or >32-second watchdog result from this probe can be counted as
acceptance of the current debug P0 implementation. The observed persistent
2038 is retained as release-package evidence, not attributed to the current
2032 code. Physical validation is blocked until the debug Appause component is
selected through normal Settings UI; this pass did not perform that action.

No source changes or test/build reruns were needed. No Accessibility toggle,
secure-setting write, force-stop shortcut, uninstall, clear, wipe, commit,
push, tag, release, or deploy was performed.

## 29. Physical Xiaomi P0 input-boundary repair validation (2026-09-06)

Task `APPAUSE-20260905-2032` continued on physical Xiaomi `6036d5b`
(`2410DPN6CC`, Android 16/API 36, 1080x2400, three-button navigation). The
Debug Appause service was already enabled and bound by the user; the
autoclicker remained enabled and bound. No Accessibility reload/toggle,
secure-setting write, app-data mutation, uninstall, clear, wipe, or force-stop
reset was used.

The final narrow window-policy repair combines `FLAG_NOT_TOUCH_MODAL` with an
explicit public `maximumWindowMetrics` height ending above the navigation bar.
The existing `WindowInsets.Type.navigationBars()` fit policy and
`setFitInsetsIgnoringVisibility(true)` remain. The hidden/internal
`OnComputeInternalInsetsListener` route, fake navigation UI, extra
`SYSTEM_GESTURES` fit, and disproven `Side.BOTTOM` experiment are not used.

| Check | Result | Evidence / boundary |
|---|---|---|
| Focused JVM tests | PASS | Existing focused policy/Home tests remained passing after the source repair |
| `assembleDebug` | PASS | Current APK was rebuilt with the existing JDK 17/Gradle setup |
| Same-signer data-preserving install | PASS | `adb -s 6036d5b install -r` succeeded; no uninstall/clear |
| Fresh current Debug blocker | PASS | Fresh Bilibili `INTERCEPT` plus `Overlay shown ... type=2032`; no release intercept in the test window |
| Physical input geometry | PASS | Debug frame/touchable region `[0,193][1080,2333]`; NavigationBar0 `[0,2267][1080,2400]` |
| Three-button Back tap | PASS | Fresh 2032 then tap `(810,2333)`; Launcher focused and no Debug 2032 remained |
| Three-button Recents tap | PASS, bounded | Fresh 2032 then tap `(270,2333)`; Launcher/system state and no Debug 2032; full overview animation not held in the one-second sample |
| Watchdog repetition 1 | PASS for state safety | More than 34 seconds with physical 2032 still attached, then Home -> Launcher and 2032 absent; dedicated physical expiry log not observed |
| Watchdog repetition 2 | PASS for state safety | Independent fresh run met the same >34-second pre-Home and post-Home criteria; dedicated physical expiry log not observed |
| Duplicate/flash smoke | PASS, bounded | Normal Bilibili open/Back/reopen showed one fresh intercept/2032 per open and no stale/rapid duplicate; final Back left Launcher with no Debug 2032 |
| Injected `KEYCODE_BACK` | NOT A PRODUCT VERDICT | Earlier failure is retained as an input-injection limitation; actual three-button region tap passed |
| Human gesture navigation | NOT TESTED | Device remained three-button; ADB injection is not human-finger proof |
| Frozen-process / true ANR safety | NOT TESTED | No genuine Appause/target freeze was induced |

The two physical watchdog runs establish the required fail-open state safety
after a long-held blocker, but the concise physical capture did not expose a
separate watchdog-expiry log message. Emulator watchdog logs and navigation
passes remain a separate emulator evidence class. Historical release-package
2038 behavior is not counted as current Debug 2032 evidence.

No commit, push, tag, release, or deploy was performed.

## 30. Accessibility Health & Recovery V1 emulator phase (2026-09-06 — APPAUSE-20260906-1459)

### Implementation contract

- `AccessibilityHealthState` combines two independent facts: the user-controlled system Accessibility component list and process-local service lifecycle evidence.
- `HEALTHY` requires both system `ENABLED` and process `CONNECTED`.
- `ACCESSIBILITY_NOT_ENABLED` is selected when the global Accessibility switch is off or the current Appause component is absent from the enabled list. `SERVICE_NOT_CONNECTED` is selected only after the current connected instance is observed being destroyed. System/process uncertainty remains `UNKNOWN` and is never rendered as permission-off.
- `AccessibilityHealthChecker.observe()` subscribes the three ViewModels to the service lifecycle `StateFlow`; `refreshServiceStatus()` still takes a fresh system snapshot on every screen resume. Recovery remains user-driven through `Settings.ACTION_ACCESSIBILITY_SETTINGS`.

### JVM verification

| Check | Result | Evidence / boundary |
|---|---|---|
| `:app:testDebugUnitTest` | **PASS** | 134 tests, 0 failures; includes 6 `AccessibilityHealthPolicyTest` cases |
| `:app:assembleDebug` | **PASS** | Gradle 8.11.1, JDK 17, current debug APK |
| `git diff --check` | **PASS** | No whitespace errors; Git emitted only existing line-ending warnings |

The new deterministic tests cover `ENABLED + UNKNOWN -> UNKNOWN` (fail closed),
`UNKNOWN -> CONNECTED -> HEALTHY` without another resume, `CONNECTED ->
DISCONNECTED -> SERVICE_NOT_CONNECTED`, disabled-system precedence, and unknown
system state.

### Emulator verification

Target: API 34 AVD `Appause_P0_Nav_API34_Temp`, serial `emulator-5554`, model
`sdk_gphone64_x86_64`, `ro.kernel.qemu=1`. The physical Xiaomi serial
`6036d5b` was not targeted.

| Check | Result | Evidence / boundary |
|---|---|---|
| Data-preserving debug APK install | **PASS** | `adb -s emulator-5554 install -r`, no uninstall/clear/wipe |
| Disabled baseline via normal Settings UI | **PASS** | Stop confirmation produced `accessibility_enabled=0`, empty enabled and Bound services |
| Disabled Appause UI | **PASS** | After normal Back/resume, Home showed Accessibility recovery item and Settings route |
| Enable via normal Settings UI | **PASS** | Allow confirmation produced `accessibility_enabled=1`, debug component enabled and Bound |
| Enable return/resume | **PASS** | Returning to Appause removed the Accessibility missing item; logcat recorded service connected and foreground events |
| Fresh emulator process | **PASS, bounded** | Reboot preserved enabled setting and Bound service; post-reboot Appause launch showed no false permission-off state |
| Final disable via normal Settings UI | **PASS** | Stop confirmation and return showed system-disabled red recovery UI again |
| Direct secure-setting writes | **NOT USED** | Settings values were read-only observations; all toggles used normal Android UI |
| `uiautomator` | **NOT USED** | Screen screenshots and coordinate input only |
| Healthy configured-target interception | **NOT TESTED** | No configured group existed; creating test data was outside this bounded validation |

The emulator also showed that `am force-stop`/`am crash` can cause Android to
disable or quarantine the Accessibility service; those probes were not treated
as product failures. `am kill` did not remove the bound service. The final
emulator state was deliberately left disabled through normal Settings UI.

No commit, push, merge, tag, release, or deploy occurred.

### Review follow-up

The independent lifecycle/state review found one same-scope race and fixed it:
`currentProcessState()` now reads the process `StateFlow` as the sole runtime
source, so a destroy emission cannot be overwritten by a still-clearing static
instance reference. `systemState()` also converts ordinary setting-read
exceptions to `UNKNOWN`, preventing an observer from terminating while retaining
stale health. Focused JVM tests and `assembleDebug` were rerun successfully;
the rebuilt APK was data-preservingly installed on `emulator-5554`, and the
read-only disabled setting/dumpsys plus Appause launch check passed. No blocking
review finding remains. The configured-target interception path remains outside
this validation because the emulator had no configured group.

## 2026-09-06 Emulator interception smoke — P0 batch + session lifecycle (Medium_Phone, emulator-5554)
- Environment: headless `Medium_Phone` AVD, debug package `com.appause.android.debug`
  installed with data-preserving `install -r`; its AccessibilityService was already
  the only enabled one. `TestGroup2` (Chrome, 10 s cooldown) pre-existed; a global
  recommended app (Calendar) was configured through the UI to exercise P0-1.
- **P0-1 (pause-screen data source merge) PASS**: launching Chrome produced a real
  `type=2032` window (`dumpsys` verified) showing the correct app name/icon, a
  ticking 10 s countdown, and the recommended app Calendar sourced from the same
  global DataStore list the overlay path uses.
- **Cancel path PASS**: Cancel returned to `NexusLauncherActivity`; 2032 window
  count dropped to 0 (no remnant); a further log entry was written to stats
  (Avoided=1 visible on the home screen).
- **Immediate re-open after Cancel PASS**: fresh 2032 pause screen appeared
  (count=1), so the 800 ms `noteCancelled` suppression did not swallow a genuine
  re-open.
- **Continue path PASS**: selecting the "Work" reason enabled Continue; after
  Continue the overlay was dismissed (count=0), Chrome reached the foreground,
  and the home screen Today card showed Completed=1 / Avoided=1 (proceed and
  cancel both persisted and observed).
- **Session lifecycle PASS**: reopening Chrome within the 3-minute leave window
  resumed without interception (count=0); after leaving to the launcher for
  200 s, reopening produced a fresh 2032 pause screen (count=1, re-armed).
  Note: a first re-arm attempt with Chrome kept in the foreground during the
  wait correctly did NOT re-arm (leave timer starts only on real leave) — the
  tester's sequencing error, not a product defect.
- Not covered: navigation-escape (Back/Recents) region behavior on three-button
  devices (covered by the separate physical-Xiaomi P0 evidence), re-remind
  (Pro-gated, not unlocked here), Temporary Pass chooser, PauseActivity fallback
  path (2032 attach did not fail on this AVD). Physical device was not touched.

## 2026-09-06 B1/B2 verification (JVM + emulator regression)
- B1 (P1-1 dead-strategy removal, P1-6 record retention, P2-1 dead code, P2-6
  comment fixes): `assembleDebug` PASS, `testDebugUnitTest` PASS. Emulator
  regression: refactored build reinstalled with `-r` on emulator-5554; opening
  the grouped target still produced a real `type=2032` pause screen with the
  recommended app row intact — the overlay refactor is behavior-preserving as
  claimed. Overlay dismissed cleanly afterwards.
- B2 (G2 PauseGuardPolicy extraction, G3a LicenseVerifier hardening + tests):
  `testDebugUnitTest` PASS with 147 tests (baseline 134, net +13: +8
  PauseGuardPolicy boundary cases, +6 LicenseVerifier fail-closed cases,
  −4 replaced OverlayPresentationPolicy cases, −1 SessionState/other drift).
- LicenseVerifier.verify now catches malformed-segment exceptions itself and
  returns null, matching its documented "null on any failed step" contract;
  callers already wrapped it in runCatching, so end-to-end behavior is
  unchanged (fail closed either way).
- New pure policy: `PauseGuardPolicy.evaluate` with exact historical
  boundaries (max-hold `>` 30 s, grace `<` 1.5 s), wired into the
  `pauseShown` getter with identical read points and identical side effects.
- Physical device was not touched. No commit happened at the time of testing.

## 31. v0.5.40 current-source Home/Recents finalization (2026-09-11)

**Scope:** the current `main` source @ `ed64329`, version `0.5.40` /
versionCode `92`. The `v0.5.40` release tag points to an earlier commit, so
this entry describes current source behavior and does not claim that the tagged
APK contains these post-tag fixes.

### Focused validation

| Check | Result | Evidence / boundary |
|---|---|---|
| Home transition policy | **PASS** | Focused `HomeTransitionPolicyTest` covered Home, Recents, stale target, and immediate reopen decisions. |
| Existing pause/overlay policy tests | **PASS** | `OverlayPresentationPolicyTest`, `PausePresentationPolicyTest`, `PauseGuardPolicyTest`, and `PauseActivityIntentTest` passed. |
| `assembleDebug` | **PASS** | Current source assembled successfully. |
| Emulator smoke | **PASS** | `emulator-5554`: Home dismissed the active pause presentation; Recents did not dismiss it as Home; immediate target reopen intercepted again. |
| Xiaomi physical smoke | **PASS** | Xiaomi 2410DPN6CC / Android 16: ADB/logcat/WindowManager showed `reason=homekey` → overlay dismiss, `reason=recentapps` → overlay still present after about 1.8 s, and Home → Bilibili reopen about 291 ms later → fresh second intercept. |

No subjective visual-smoothness measurement is claimed. The physical evidence is
objective navigation/overlay/intercept evidence, not a claim about the tagged
v0.5.40 APK; the current source is later than that tag.

## 32. v0.5.41 repeat-interception / pause-overlay stacking fix (2026-09-17)

**Scope:** release `v0.5.41` / versionCode `93`, built from `main` @ `d210197`.
The defect: the 30 s pause-guard watchdog released the logical guard while the
primary `TYPE_ACCESSIBILITY_OVERLAY` (2032) was still attached, so with a
cooldown longer than 30 s the next foreground event ran a second INTERCEPT and
attached a second overlay, leaving the first window unremovable.

Full analysis, trigger boundary and raw evidence: `docs/REPEAT_INTERCEPTION_STRESS_TEST.md`.

### Gate results

| Check | Result | Evidence / boundary |
|---|---|---|
| `testDebugUnitTest` | **PASS** | 22 suites, **216 tests, 0 failures, 0 errors, 0 skipped**, including the updated `PauseGuardPolicyTest` boundaries and the new `InterceptionDeciderTest` integration case. |
| `assembleRelease` | **PASS** | `BUILD SUCCESSFUL in 2m 39s`; `aapt2 dump badging` reports `versionCode='93' versionName='0.5.41'`, `minSdk 26`. |
| Release signing | **PASS** | `apksigner verify --print-certs`: V2 signer `CN=Appause`, SHA-256 `843d4ce0ed3b369b00fd5d22be90bfff454ec2ef5d8d415d6ec15c9489431525` — **byte-identical to the published v0.5.40 APK**, so an in-place upgrade stays valid. |
| Emulator reproduction | **PASS** | Pre-fix build (9-14 APK), `Medium_Phone` API 37, cooldown 60 s: with **no user action at all** — only Clock opening one of its own config pages — the guard was released at +31 s and a second `INTERCEPT` + second `Overlay shown` followed, with no `Overlay dismissed` between them. Post-fix build: 1 INTERCEPT / 1 overlay, zero watchdog releases (`run-20260917-000356`, `self-event3.log`). |
| Human-like stress regression | **PASS** | 17 scenarios (S1–S17) on `emulator-5554`; analyzer reported no duplicate interception, no overlay leak and no blocked retry. |
| Xiaomi physical smoke (this fix) | **NOT TESTED** | The device was used for diagnosis on the pre-fix build only. Its group cooldown is 10 s, which cannot reach the 30 s watchdog, so the physical device could not exercise this defect either way. Emulator results are **not** recorded as device results. |

### Notes

- The countdown reset caused by `AbandonCooldown` (leaving the pause screen
  mid-countdown restarts the countdown on return) is **intended behaviour** and
  is deliberately unchanged — see `docs/REPEAT_INTERCEPTION_STRESS_TEST.md` §13.
- No subjective smoothness claim. Physical-device evidence for this specific fix
  remains a manual gap to close on a build with a cooldown above 30 s.

## 33. v0.5.42 stats-screen crash fix (2026-09-17)

### Regression found after the v0.5.41 release
- User report: opening the Statistics screen ("当天的记录" entry) crashed instantly on **both** the
  debug and the release build on the physical device. Confirmed 2/2 reproductions, then read the stack
  from `adb logcat -d -b crash`:
  `Cannot create an instance of class ...StatsViewModel` →
  `Caused by: NoSuchMethodException: StatsViewModel.<init> [class android.app.Application]`.

### Root cause
- Commit `497b35b` (2026-08-28) added two default-valued test-seam parameters
  (`repositoryOverride`, `isProOverride`) to the `StatsViewModel` primary constructor.
- Kotlin default parameters do **not** generate a real `(Application)` single-arg constructor in
  bytecode. The stats screen creates the VM through the default `AndroidViewModelFactory`, which
  reflectively requires exactly that shape → crash on every stats-screen entry since.
- The crash first shipped in a public release with **v0.5.39** (`git tag --contains 497b35b`).
- Why the 216 unit tests were blind: they construct the VM through the 3-arg injection path and never
  exercise the default-factory reflection.
- Why onboarding has the same pattern but does not crash: its screen wires an explicit
  `viewModelFactory` in `NavGraph.kt`; the stats screen used bare `viewModel()`.

### Fix
- `StatsViewModel` constructor annotated with `@JvmOverloads` (one line), restoring the
  `(Application)` constructor while keeping the test seams. Guard comment added so the annotation is
  not removed as "redundant". Repo-wide scan: no other ViewModel has this hazard. Committed as `963c5f9`.

### Verification
- `assembleDebug` + `testDebugUnitTest`: **216 tests, 0 failures / 0 errors / 0 skipped**.
- Physical device (Xiaomi 2410DPN6CC / HyperOS / Android 16, debug build): tapped the Today stats
  card → Statistics screen opens normally, 143 records, weekly bar chart, Overview and Top Apps all
  render; 0 FATAL exceptions in logcat. Before the fix: crash 2/2 on both builds.

### Release gate (v0.5.42 / versionCode 94)
- `assembleRelease` + full unit suite run together: BUILD SUCCESSFUL in 4m 24s; 22 suites / 216 tests,
  0 failures / 0 errors / 0 skipped.
- `aapt2 dump badging`: `versionCode='94' versionName='0.5.42'`, minSdk 26.
- `apksigner verify --print-certs`: V2 signer `CN=Appause`, SHA-256 `843d4ce0…431525`, **byte-identical
  to the published v0.5.41 APK**, so in-place upgrade stays valid.
- Boundary note: the TP + lockscreen freeze reported by the user on 09-17 10:00–10:14 occurred while
  release + debug accessibility services were simultaneously enabled. Dual-service interference is a
  plausible hypothesis, not a controlled root-cause proof. The `AbandonCooldown` countdown reset stays
  intended behaviour, unchanged.

## 34. Xiaomi single-service Temporary Pass lockscreen expiry validation (2026-09-17)

| Check | Result | Evidence / boundary |
|---|---|---|
| Release-only AccessibilityService state | **PASS** | Physical Xiaomi 2410DPN6CC / HyperOS / Android 16; debug Appause service was not enabled for this scenario and the original release-only state was restored. |
| Temporary Pass through lockscreen expiry | **PASS** | Target interception → five-minute Temporary Pass → lock while active → remain locked through expiry → wake/unlock. Exactly one expiry re-evaluation and one resulting 2032 interception. |
| Duplicate/stacking/stale state | **PASS** | No duplicate `INTERCEPT`, overlapping overlay, stacked `PauseActivity`, stale bypass, or Temporary Pass extension/freeze observed. |
| Exact 30-second watchdog boundary on Xiaomi | **NOT TESTED** | v0.5.41 watchdog/repeat-interception boundary remains validated on API 37 emulator; Xiaomi exact-30-second evidence is absent. |

This physical result closes the single-package Temporary Pass + lockscreen lifecycle gap. It does not
prove that dual AccessibilityService interference caused the earlier freeze; that remains unproven.

## 35. F-27 Home-escape fix: release-build device re-verification + v0.5.44 RC gates (2026-09-21)

| Check | Result | Evidence / boundary |
|---|---|---|
| F-27 fix in signed release build on HyperOS | **PASS** | `assembleRelease` from main@d2d2aea, versionCode 95 / v0.5.43, cert SHA-1 99f2dadb…0816; `adb install -r` over the user's existing release (same signature, data kept; MIUI reinstall did NOT revoke appops trio or a11y binding). `scripts/stress/release_smoke.py` 8/8 PASS on Xiaomi 2410DPN6CC: xhs+bili intercept, ONE Home press dismisses the 2032 card and focus returns to com.miui.home (the exact F-27 symptom), re-arm on relaunch, APP_SWITCH→Recents leaves no overlay residue, zero residual windows. Oracles are window/focus-only (release non-debuggable: no plog/Room); Continue/Cancel never tapped, user's "social media" group data untouched. |
| v0.5.44 RC gates | **PASS** | versionCode 96 / versionName 0.5.44; `testDebugUnitTest` 235 tests 0 failures; `assembleDebug` + `assembleRelease` BUILD SUCCESSFUL incl. lintVitalRelease; §2 secret scan: no keystore/.dev.vars ever committed, `canRetrieveWindowContent=false`. |
| Final named artifact | **PASS** | `python scripts/make_release.py` → `output/Appause-v0.5.44.apk`; apksigner V2 verifies with release cert; aapt badging `com.appause.android 96/0.5.44`. Diff vs the device-verified 95 build is version metadata only. |
| Install final RC file + user-path smoke | **NOT TESTED** | Checklist §6 manual-install item pending; recommended before publishing (≈5 min phone time, same code path already device-verified). |
| Website/README/INSTALL v0.5.44 sync | **NOT DONE** | Update at publish time together with the GitHub Release + tag. |

## 36. Release Readiness: RC 0.5.44 emulator suite + on-device auto batch (2026-09-21)

| Check | Result | Evidence / boundary |
|---|---|---|
| Emulator 95->96 upgrade data keep (Room group + DataStore md5) | **PASS** | release_emu_check v2, evidence/release-emu/emu2-*: u-data-kept, u-intercept-96, u-cancel-95 & u-continue-96 proven via Room rows. |
| Emulator RC fresh install + onboarding (language, pre-grant denied page, battery step) | **PASS** | f-1/f-2/ob-battery screenshots; f-fresh-onboarding, f-onboarding-through-battery. |
| Emulator reboot: auto a11y rebind, persistence, interception, HOME dismiss | **PASS** | r-auto-rebind=True (no adb help), r-data-persist, r-intercept, r-home-dismiss 5.7 s; exact-header re-probe <3 s. |
| v1-run 7 FAILs | **B-class (harness)** | Same-value `settings put` fires no change event -> release service never rebinds after force-stop (fix: clear "" then put); loose `grep com.appause.android` window oracle matched .debug/Activity windows -> false intercept (fix: exact header `com.appause.android}`). No product defect. |
| On-device RC 96 overwrite over user's real 95 | **PASS** | d9_release_final install: versionCode 95->96, appops trio + deviceidle whitelist survive, user groups render unchanged (rc96-home.png), Service active. |
| On-device intercept latency + adb-HOME dismiss (RC, charging) | **PASS** | xhs 0.34 s / bili 0.30 s; home-dismiss both, zero residual windows. |
| On-device real-finger gestures + 10-min idle F-26 release probe | **NOT TESTED** | Awaiting the single user batch (GOAL_R_MANUAL.md R-C/R-D). |
| Health-report false alarm under emulator bind churn (KI-1) | **FAIL (conservative direction, non-blocking)** | After heavy adb settings-put churn: release service demonstrably live (2032 intercepts at 0.3-0.6 s, no crash logs) while in-app status pages persistently read "Needs recovery" (_processState stuck DISCONNECTED). Never observed on the physical HyperOS device (health UI correct there); error direction is a safe false-alarm, self-heals via the guided recovery loop. Follow-up hardening proposed: derive liveness from the onAccessibilityEvent stream, not bind callbacks alone. |

## 37. KI-2 fade-out polish on overlay dismissal (2026-09-21, user-feedback driven)

| Check | Result | Evidence / boundary |
|---|---|---|
| User feedback on no-animation dismissal | **CONFIRMED** | "刚打开就立刻关闭会卡一下；等 1-2s 就基本无感" — first-frame-not-yet-rendered window cannot paint a fade. |
| Fix: 100ms alpha fade + 200ms guaranteed-removal safety net + <300ms-fresh windows skip fade | **PASS** | OverlayManager.dismiss(); removal never depends on animation (F-27 principle preserved). Gates: 235 unit tests, assembleDebug/Release + lintVital green. |
| Device re-verify (HyperOS, RC rebuild) | **PASS** | immediate-dismiss 718ms / normal-dismiss 308ms single-HOME, focus=com.miui.home, zero residual windows. Final RC sha256 bc73d76a…516fff (v0.5.44/96, supersedes e6524be5 build). |
| User finger re-check on fade build | **PARTIAL** | Scenario 2 (dismiss after 1-2 s): imperceptible — fixed. Scenario 1 (dismiss the instant the card appears): still hitches — root cause shifts to first-frame main-thread contention, not the fade; deeper fix touches initial composition (out of minimal-fix scope). Remains a cosmetic Known Issue (KI-2 residual); function unaffected (single HOME always clears, zero residual). |

## 38. Review batch A+B: entitlement outage hold, leave-window wall-clock reconcile, pause configChanges (2026-10-04, APPAUSE-20261004-1448)

Two independent read-only model reviews of main@601fc9d (Claude Sonnet 5.5 Medium + HY4) were re-verified line-by-line before any change. Verdicts: #1 leave-window deep-sleep desync CONFIRMED; #2 Pro entitlement outage→FREE CONFIRMED and worse than reported (a FREE emission terminates the entitlement flow's refresh loop, pinning the wrong state); #3 rebind state split PARTLY correct (split is real, but "infinite Resume" is disproved — a residual bypass re-arms via the next leave transition, so the bounded cost is one free entry per service outage plus silent re-remind loss for that sitting); #4 PauseActivity recreation restarts the countdown CONFIRMED (fallback-Activity path only; the 2032 overlay window is not recreated); #5 homePackages staleness DOWNGRADED to edge-case risk (queryIntentActivities covers launcher switching; no package-event receiver exists but the remaining triggers are a connect-time query exception or post-connect launcher install).

| Check | Result | Evidence / boundary |
|---|---|---|
| #2 fix: entitlement holds last verified claims across a THROWN verification (fb6126b) | **PASS (unit)** | `resolveRealEntitlement` routed through the existing `fingerprintProvider`/`tokenVerifier` seams (the hardcoded DeviceKeyStore path was untestable) + token-keyed `lastVerified` hold. Null (hard-invalid) stays authoritative/fail-closed; cache keyed by token prevents cross-token leakage; expiry still re-classifies per resolution. New `ProStateEntitlementHoldTest` (Robolectric): hold-on-failure, fail-closed-with-no-history, null-is-authoritative, no-cross-token-leak. |
| #1 fix: leave window re-arms by wall clock (4ef99a7) | **PASS (unit)** | New pure `LeaveWindowPolicy` + two call sites: eager re-arm in `handleForegroundChangeImpl` before the Resume decision, and a poller sweep of expired off-screen windows on wake. 5 JVM tests incl. the `now == deadline` boundary matching the UI card's disappearance. |
| #4 fix: PauseActivity survives configuration changes | **PASS (compile)** | Manifest `configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode|fontScale"`; rotation no longer restarts the countdown from full length nor releases the pause guard mid-recreation. Layout already covers both orientations via `PauseLayoutPolicy`. Emulator rotation check NOT TESTED this batch. |
| Full suite + build | **PASS** | `testDebugUnitTest` 272 tests, 0 failures, 0 errors (263 baseline + 9 new); `assembleDebug` green after each batch (JDK 17 override). |
| Deep-sleep device experiment (screen-off ≥3.5 min → return → expect cooldown) | **NOT TESTED** | Unit tests pin the predicate only; the end-to-end sleep/wake path needs the emulator/physical run (mind the logcat keep-alive trap: disconnect adb or use wireless debugging). |
| #3 rebind reconcile | **NOT STARTED** | Awaiting PersistentLog rebind-frequency data from the Xiaomi device before choosing clear-all vs foreground-preserving reconcile. Reproduction must use a11y off/on toggling, NOT `am force-stop`. |

## 39. Batch A+B emulator verification (2026-10-04, APPAUSE-20261004-1448, emulator round)

Environment: Medium_Phone headless running a clean build of main@d5d3d14. The PauseActivity
`exported=true` variant used only for experiment ②'s direct launch was a temporary working-tree
manifest change, reverted before the interception rounds (clean d5d3d14 build reinstalled). The
AVD's old TestGroup2/Chrome data was gone → group "SleepTest" (Chrome, 10s cooldown) created via
the DB-edit path.

| Check | Result | Evidence / boundary |
|---|---|---|
| #4 configChanges: rotation keeps the cooldown running (PauseActivity fallback path) | **PASS** | Direct-launched PauseActivity (60s): screenshots 57 → 22s later 35 (landscape two-pane relayout) → 28 → 24 (portrait restored). Two configuration changes, countdown continuous throughout; the old behavior would have restarted from ~60 at each rotation. |
| #1 normal re-arm path regression | **PASS** | Continue → Home → leave timer armed (180s) → fired at +180.0s → return to Chrome → fresh full INTERCEPT (2032 window = 1, screenshot). |
| #1 wall-clock reconcile (the deep-sleep desync path) | **PASS (deterministic equivalent)** | The AVD refused to deep-suspend today (two rounds — plain screen-off, and Doze force-idle + battery unplug; the coroutine timer fired at exactly +180.0s both times, so monotonic never froze). The desync state was therefore produced deterministically: armed the leave timer, jumped the device wall clock +600s via `cmd alarm set-time` (deadline 7 min in the past while the uptime timer still had ~178s pending — exactly the state deep sleep creates). Returning to Chrome: `Poller: leave window expired by wall clock → re-arm` + `Re-armed` + a fresh full INTERCEPT with overlay (screenshot); no RESUME, and the old timer never won the race. Device clock restored to host time afterward. |
| Real deep-sleep run (screen-off ≥3.5 min on device) | **NOT TESTED** | Emulator would not suspend today; the clock-jump equivalence covers the reconcile logic, and the platform premise (deep sleep defers uptime-based Handler delays) is the already-recorded 2026-09-13 observation. A Xiaomi physical-device run remains the final confirmation. |
| Setup notes for future rounds | — | `run-as … sh -c 'cat > …'` redirect fails (ENOENT) — use `run-as … cp` with absolute paths; `run-as am start` cannot start the app's own non-exported components (calling-package/uid mismatch); production image = no `adb root`. Current overlay tap coords (physical 1080x2400): Continue (540,1492), Cancel (540,1618) — the 2026-09-13 coords (1702/1827) are stale for this build. "SleepTest" group (Chrome, 10s) remains in the AVD DB. |

## 40. Test-gap closure batch: orchestration / re-remind / isolation / adapters / process-death (2026-10-05)

This records the earlier `main@6f1eb0d` snapshot; the 288-test result and working-tree observations below are historical, not current-state claims.

Goal was NOT new bug-hunting but closing the already-cross-verified gap between
the pure decision layer (`InterceptionDecider` etc., long covered) and the real
side effects (`handleForegroundChangeImpl` → bypass/session/leave timers/re-remind/
overlay). Baseline: main@6f1eb0d, clean tree.

| Check | Result | Evidence / boundary |
|---|---|---|
| P0-1 orchestration tests (`ForegroundChangeOrchestrationTest`, 5 historical tests) | **PASS** | Robolectric drives the REAL service entry point against the REAL graph (Room in-memory + DataStore): Continue→bypass session→leave deadline armed/cancelled (±2s of now+180s)→expiry re-arm→fresh INTERCEPT; Cancel→suppression window→800ms expiry→re-intercept; destroy/recreate contract (leave deadlines die with the instance, bypass is process-global and survives, dedup markers swallow the first same-package event after rebind — logged SkipDedup characterization, no new decision written). Current committed source has 6 tests; the later-added window-state event test is not counted in this historical run. Re-remind-job destruction is not directly asserted by this row. |
| P1 re-remind loop tests (`ReRemindLoopOrchestrationTest`, 4 historical tests) | **PASS** | Case 1: Continue anchors the clock, fire-once pops exactly once at continue+58s (60s−2s cooldown) and never again. Case 2: without a real Continue the loop stays parked on `initialSignal.await()` forever — no pop (product semantics: user still behind the pause screen). Case 3: the real TP grant path (preserveForegroundSession=false) NEVER starts the loop; an expired pass's wake re-runs the COOLDOWN (not a re-remind) via cached-last-foreground evidence. Case 4: cancelReRemind is package-scoped. Current committed source has 5 tests; the later-added case verifies loop replacement reuses the initial Continue signal. |
| P1/P2 multi-package isolation (`MultiPackageIsolationTest`, 3 tests) | **PASS** | A/B/C through one service: leave windows arm per left package (opening B after continuing A starts A's window — characterized), returning to B cancels only B, A's expiry re-arms only A; re-remind pops only for the package the user is actually in (single-foreground semantics, away-loop re-delay); TP on B suppresses only B (A resumes, C re-intercepts). Home's `minOrNull()` display is a UI decision and NOT judged here. |
| P2 production input adapters (`ProductionInputAdapterTest`, 4 tests) | **PASS** | `AccessibilityServiceChecker.systemState` translates raw Settings.Secure rows (unset→UNKNOWN, off→DISABLED, on-without-app→DISABLED, on+component→ENABLED); `refreshHomePackages` resolves registered HOME launchers (multi-launcher) into system-skip behavior, degrades safely with an empty resolution, and is idempotent/re-drivable like a reconnect. |
| P2 real Keystore integration | **NOT ADDED (by design)** | The AndroidKeyStore-backed `DeviceKeyStore.getDeviceFingerprint` path is not exercised by the cited JVM/Robolectric tests; real integration validation needs a runtime supplying AndroidKeyStore. The JVM-verifiable segment (sign→verify→claims, `LicenseVerifierTest` 20 cases) and the Robolectric segment (transient throw→last-known-good hold, `ProStateEntitlementHoldTest` 4 cases) were already covered — this batch adds no regression for the old finding #2. |
| P0-2 non-force-stop process death (`scripts/stress/p11_process_death.py`, 3 probes) | **PASS (historically reported on emulator-5556, Appause_Campaign_API34)** | Retained `actions.log` records retries followed by PASS outcomes and PID changes for all three probes. The retained artifacts do not independently establish the reported `emulator-5556` serial. HEALTH: kill → auto rebind → "connected and running" log → ≤1 window at the sampled check → interception works. RUNTIME-LOSS: Continue bypass → kill -9 → rebind → target re-INTERCEPTS; the window-count check was sampled after returning Home, so it does not prove a transient duplicate overlay was absent during presentation. PASS-SURVIVES: persisted Temporary Pass → kill -9 → rebind → pass still suppresses → after clearing, interception recovers. `adb shell kill` is EPERM on both Play and google_apis images without root; use `adb root` (google_apis) or `run-as <pkg> kill -9 <pid>`. HyperOS physical device: plain kill from adb NOT permitted → manual validation item. |
| Full suite + build | **PASS (historically reported)** | `testDebugUnitTest` 288 tests, 0 failures, 0 errors (272 baseline + 16 new); `assembleDebug` green. Retained artifacts are insufficient to independently reproduce this exact run. The later accepted final §41 baseline records 290/290 tests for both debug and release. |
| Production code changes in this historical batch | — | Test-seam visibility changes (`AppauseAccessibilityService`/`AppauseApp` and selected members made open/internal) were behavior-neutral. The batch also included a re-remind lifecycle fix: loop replacement reuses the session's initial Continue signal; true session end removes it; service destruction cancels and clears re-remind signals. |
| Harness lessons (for future rounds) | — | (1) `runTest` auto-advances the whole virtual scheduler while the body awaits — a freshly armed 3-min leave timer silently fires mid-assertion; the harness uses runBlocking + a DEDICATED TestCoroutineScheduler + sliced advances with drains. (2) Compose overlay frames re-schedule forever on the paused looper → hang any `destroy()`; hence the show-recording test double. (3) In this session's Robolectric setup, `ShadowSystemClock.advanceBy` did not move `System.currentTimeMillis()`; expired-pass seeding used a past-timestamp grant instead. |

Historical working-tree observation from that session: `.github/workflows/ci.yml` and
`app/build.gradle.kts` carry content changes NOT made by this session (CI adds
release-variant tests + lint; build.gradle adds an androidResources
ignoreAssetsPattern). They were present in the tree when this session finished
and were left untouched. `AndroidManifest.xml` shows only line-ending noise.

## 41. Cloud consolidation acceptance (2026-10-05 — APPAUSE-20261005-CLOUD-CONSOLIDATION)

| Check | Result | Evidence / boundary |
|---|---|---|
| Focused orchestration | **PASS** | `ForegroundChangeOrchestrationTest`: 6/6; `ReRemindLoopOrchestrationTest`: 5/5. Replacement reuses the original initial-Continue deferred; loop replacement cancels the prior job, true session end removes/cancels the deferred, and service destruction cancels and clears all re-remind signals. |
| Full JVM unit suites | **PASS** | Fresh `testDebugUnitTest` and `testReleaseUnitTest`: 290 tests each, 0 failures, 0 errors, 0 skipped. Room `MigrationTestHelper` reads exported schema versions 1 and 6 under both variants. |
| Lint and local assemble | **PASS** | `lintDebug` (0 errors, 167 warnings), `lintRelease` (0 errors, 147 warnings), `assembleDebug`, and `assembleRelease`. The API<33 receiver overload is narrowly lint-suppressed; runtime receiver behavior was not changed. |
| Production APK schema assets | **PASS** | `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk` each contain 0 entries under `assets/com.appause.android.data.local.AppDatabase/`; test merged assets still expose the schemas to Robolectric. |
| A/B findings and device boundary | **HOLD** | DataStore `IOException` fallback remains rejected/held (no continuing recovery implemented or tested). Notification toggle permission-launcher wiring is already present from `6f4a7fa`; actual Android permission dialog needs a real device. `ACTION_CLOSE_SYSTEM_DIALOGS` remains on current exported/system-compatible registration semantics pending OEM/device evidence. No ADB, emulator, or physical-device operation occurred. |
| Git / release | **NOT DONE** | No staging, commit, push, merge, tag, release, install, or deployment. `git diff --check` passed (Git emitted only autocrlf notices). |

## 42. v0.5.46 overnight release hardening (2026-10-12 — autonomous overnight goal)

| Check | Result | Evidence / boundary |
|---|---|---|
| CI red-light regression root-caused | **FIXED (root cause)** | `37313ba` moved Room schema assets to the `main` sourceSet and excluded `*.json` via `androidResources.ignoreAssetsPattern`. Robolectric unit tests read the SAME merged assets as the APK (`test_config.properties → android_merged_assets`), so `!*.json` stripped schemas from unit tests too → `AppDatabaseMigrationTest` FileNotFoundException, CI red since 2026-10-04 (verified via `gh run list`: runs for `55a9c14` and `501a5c4` both FAILED on `testReleaseUnitTest`). Fix: remove the ignoreAssetsPattern block; schemas (~10 KB, public in this MIT repo) now ship in APKs by design. Comment in `app/build.gradle.kts` documents why hiding them from APKs is impossible via assets. |
| Full JVM unit suites | **PASS** | `testDebugUnitTest` + `testReleaseUnitTest`: 292 tests each (290 baseline + AppQueryServiceTest 2), 0 failures. Includes new `SettingsDataStoreCorruptionTest` (corrupt file → defaults, healthy roundtrip preserved, shared-store reset) and `AppQueryServiceTest` (multi-launcher-activity dedupe, self-exclusion). |
| New finding: app-picker crash (release blocker, pre-existing) | **FIXED (root cause)** | Reproduced on emulator (Pixel-style image): an app with multiple MAIN/LAUNCHER activities (Google app) appears twice in `queryIntentActivities` results; `AppSelectScreen` keys items by packageName → duplicate-key `IllegalArgumentException` crash as soon as both entries compose (e.g. searching "s" narrows the list). Present in v0.5.45. Fixed in `AppQueryService.getLaunchableApps()` with a `seenPackages` dedupe; regression test added. |
| New fix: DataStore corruption fallback | **IMPLEMENTED + TESTED** | Evidence-first per the agreed gate: isolated Robolectric test first reproduced `CorruptionException` from a corrupted `datastore/settings.preferences_pb` (RED), then `ReplaceFileCorruptionHandler { emptyPreferences() }` (+ AppLogger.e) turned it GREEN. NavGraph crash-loop path (`hasCompletedOnboarding.first()` in LaunchedEffect) can no longer loop: worst case re-shows onboarding; Room data untouched. Test also proved cross-test pollution hazard: the DataStore delegate is a JVM-wide singleton, so the test resets shared keys at the end (leaving `isEnabled=false` behind had poisoned service orchestration tests → 17 failures, bisected and fixed). |
| Emulator runtime validation (Appause_Campaign_API34, Android 15 image) | **PASS (see per-item)** | Onboarding (language → skip → home) PASS. Upgrade v0.5.45 → v0.5.46 via `install -r` PASS (same signature CN=Appause; app returned straight to Home, no onboarding reset; onboarding state retained across upgrade). Interception pipeline: cold-start Chrome (non-system target) → pause overlay (TYPE_ACCESSIBILITY_OVERLAY) with 10s countdown, four reason chips, Continue/Cancel PASS; Cancel → clean return to launcher, no lingering card PASS; Cancel → relaunch → intercepted again PASS; countdown end → Continue enabled → enters Chrome PASS; re-open within the 3-minute leave window → correctly NOT intercepted (bypass session) PASS; leave window expired (Home + 3.5 min idle) → cold start intercepted again PASS. Settings as target never intercepts — `SKIP: system package (com.android.settings)` is BY DESIGN (hardcoded exclusion + resolved HOME packages). |
| Emulator NOT VERIFIED items | **NOT VERIFIED** | Temporary Pass expiry wake (needs 5+ min grant wait), re-remind loop on-device behavior, HyperOS physical-key escapes (Home/Back/Recents on MIUI), real license redemption against production Worker, edge-to-edge/PauseAlarmReceiver behavior under targetSdk 36. Emulator results must never be recorded as HyperOS physical-device results. |
| APK artifacts | **PASS** | `output/Appause-v0.5.46.apk` — 12,292,714 bytes, SHA-256 `f91e4d09b09a79e651db382a240b89e1e65dcc8302c20f6bda4339faeb2a48be`, versionCode 98 / versionName 0.5.46 verified via aapt badging, signed with existing release key (cert SHA-256 843d4ce0…1525). Debug overlay `app-debug.apk` used for runtime diagnosis only. |
| Worker / Pro production | **READ-ONLY PASS (deployment existence)** | `wrangler deployments list` succeeded: latest production deployment 2026-09-25 (v14c64587, after v0.5.45). All `/api/*` routes are POST (mutating) — none were called. `/admin/stats` GET requires ADMIN_KEY secret; not exercised. Functional activation acceptance deferred (mutating, needs authorization). |
| Git / release | **NOT DONE** | No staging, commit, push, tag, or release. Working tree carries: 6 pre-existing v0.5.46 candidate files + tonight's edits (`app/build.gradle.kts` schema-assets fix, `SettingsDataStore.kt`, `AppQueryService.kt`, `docs/RELEASE_NOTES.md`, `docs/archive/TEST_REPORT.md`, `docs/archive/PROGRESS.md`, 2 new test files). |
| Harness lesson | — | `adb shell uiautomator dump` briefly DISPLACES bound accessibility services on the emulator (service flaps unbound→rebound within ~1-2 s). It made the home health card show "已开启但尚未连接" and briefly emptied service-decision state during testing — an artifact of the probe, not an app bug. Prefer `screencap` + visual reads when validating a11y-dependent behavior. |

## 43. Temporary Pass visibility toggle — HyperOS physical-device acceptance (2026-10-13)

Device: Xiaomi `2410DPN6CC` (haotian), Android 16 / HyperOS, serial `6036d5b`, 1080×2400, USB-powered, screen kept on. No Task ID was supplied for this task.

| Check | Result | Evidence / boundary |
|---|---|---|
| Build under test | **PASS** | `com.appause.android.debug` versionName `0.5.46-debug` / versionCode `98`, APK SHA-256 `f3a6b162cea330149c4c5b126f7bf09be710535d45bad9c8c8f34752756da048`, V2 cert SHA-256 `c860f6234c056eb9b8a922657b9a37c9863958969df953bf94c97318857257b3`. Newest source edit was 15:25 and the rebuilt APK 15:36+ (re-signed this session), so the artifact matches the working tree. |
| Install path (signature mismatch root cause) | **FIXED (harness, not product)** | `adb install -r` first failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: the previous session's build passed `-PappauseDebugKeystore=.android-local/debug.keystore` (cert `cae84d71…`) while the phone's debug slot is signed by `~/.android/debug.keystore` (cert `c860f623…`). No source or config file was edited — a plain `assembleDebug` uses the AGP default keystore, which matches. Reinstall returned `Success` and `firstInstallTime` stayed `2026-09-29 17:19:15` → debug data lossless. The old artifact was preserved as `output/_app-debug-98-DCB6d0e1-androidlocalkey.apk`. |
| Toggle visible and operable in Settings | **PASS** | Top card of 暂停设置 / Pause behavior; default ON; one tap flips it. Screenshots `output/tpt-verify/04`,`05`. |
| OFF hides the entry on the real pause screen | **PASS (2/2)** | 2032 overlay for `com.deepl.mobiletranslator` with countdown finished: only 继续 / 取消, no 临时放行. Screenshots `37`,`50` (log `Overlay shown … type=2032` @22:23:58 and @22:49:00). |
| ON shows it again + chooser + real grant | **PASS** | Screenshot `43` shows 临时放行; `44` shows the inline 5/15/30 chooser; granting 5 min persisted `temporary_passes = com.deepl.mobiletranslator\|1791884808224` plus its foreground-anchor sibling key. During the active pass, re-entering DeepL produced no card. |
| Persistence across process death | **PASS** | `am force-stop` + relaunch: DataStore raw bytes still `show_temporary_pass … \x12\x02\x08\x00` and the UI still rendered OFF (screenshot `06`). |
| zh / en copy and layout | **PASS** | `Show Temporary Pass` / `显示临时放行` with their descriptions; no clipping or wrap defects at 1080×2400. |
| OFF blocks a stale selection callback | **NOT VERIFIED (unreachable state)** | The pause card is a fullscreen-modal 2032 window, so Settings cannot be operated while the card (or its chooser) is open — no real user path reaches this. Static check of the guard: `OverlayManager.kt:461` and `PauseActivity.kt:437` return early INSIDE `try { … } finally { onSelectionFinished() }` (`OverlayManager.kt:498-502`), so a race cannot leave the chooser stuck in `selectionInFlight`. |
| Closing the toggle collapses an open chooser | **NOT VERIFIED (unreachable state)** | Same reason. `PauseActivity.kt:614-618` resets `temporaryPassChooserState` when `showTemporaryPassLinkEnabled` turns false, and every render branch additionally requires `showTemporaryPassLinkEnabled`; the chooser's own 取消 was verified working on device (screenshot `47`: chooser collapses, card and entry survive). |
| Core interception regression | **PASS** | Debug service genuinely bound (`onServiceConnected SETUP OK` @22:15:02, pid 30624 — not just the system toggle). Six cards across the session (22:17:00, 22:17:34, 22:23:58, 22:33:33, 22:39:33, 22:49:00), all `ty=ACCESSIBILITY_OVERLAY` (2032). Cancel → launcher focus `com.miui.home` PASS; Continue → card dismissed and Home stats moved to 完成 2 / 阻止 3 (decision accounting correct); re-entry after the leave window re-intercepts PASS; toggle OFF did not affect Continue/Cancel PASS. |
| Back escapes the pause screen | **PASS** | Back dismissed the card to the launcher without admitting the target app (log `backCB FIRED` @22:35:44, screenshot `45`). |
| HOME escapes the pause screen | **NOT VERIFIED (uncontrolled)** | Only indirect evidence (22:17 pair: HOME then re-entry re-intercepted). A controlled probe could not be scheduled: after a Continue/pass the package stays bypassed long enough that a fresh card did not appear within 150 s. |
| **NEW DEFECT — Back with the duration chooser open cancels the whole card** | **FAIL (low-medium)** | Reproduced 1/1 at 22:35:44. Expected per `PauseActivity.kt:620-628`: `DISMISS_CHOOSER` collapses only the chooser. Actual: chooser collapsed AND `CANCEL_PAUSE` ran (card gone, launcher focused, no pass granted — DataStore contained no `com.deepl` entry). The chooser's own 取消 behaves correctly in the same state, so the state machine is fine and only the Back route is wrong. Suspected cause, not yet proven by a code change: the 2032 overlay receives one physical Back twice — `OverlayManager.kt:100-103` (`dispatchKeyEvent`) and `:397-404` (`OnBackInvokedCallback`) both call `standaloneBackBridge.dispatch()`; the first hit collapses the chooser, the second falls through to Cancel. Minimal fix candidates: make `dispatch()` idempotent per Back gesture, or skip the key-event route when an `OnBackInvokedDispatcher` is registered (API 33+). |
| Release install / user data safety | **PASS** | `com.appause.android` stayed 0.5.45/97 with `firstInstallTime` 2026-07-29; its accessibility service remained Enabled+Bound for the entire session; no uninstall, `pm clear`, or data reset was performed on either package. |
| Test-config restore | **PASS** | appops reverted (`GET_USAGE_STATS`→default, `SYSTEM_ALERT_WINDOW`→ignore), `deviceidle whitelist -com.appause.android.debug` removed, test group `TPtest` deleted (Home back to 还没有分组), toggle left at its default ON, and the Appause Debug accessibility service turned off (its own log: `onUnbind`/`onDestroy` @23:03:35). Release whitelist entry and release a11y untouched. |
| JVM baseline on the same tree | **PASS** | `testDebugUnitTest` 293 tests, 0 failures. Visibility-toggle coverage is data-layer only (`SettingsDataStoreCorruptionTest`); the two presentation-path guards have no unit test, which is why this device round existed. |
| Harness boundary (must not be read as a product result) | — | HyperOS parked the debug process at `oom_score_adj=200`, queueing accessibility events and releasing them in a burst when the process woke (e.g. DeepL foreground at 22:22:43 → card at 22:23:58). Timing was treated as probe noise; no "no interception" observation was classified as a verdict without a wake-and-flush retry. `uiautomator dump` was never used, per the goal's constraint about displacing bound services. |
| Git / release | **NOT DONE** | No staging, commit, push, merge, tag, release or deploy. No Worker endpoint called. HEAD still `501a5c4`, `main` still 1 behind `origin/main`, working tree still the same 17 modified + 3 untracked paths. |

## 44. v0.5.46 signed release candidate — offline acceptance (2026-10-17; APPAUSE-20261016-2356)

| Check | Result | Evidence / boundary |
|---|---|---|
| Build inputs | **PASS** | main at 501a5c4 with the existing uncommitted fixes and Temporary Pass toggle. SHA-256 manifest covers 155 Android source/config/schema files; all stayed unchanged through verification. This APK includes the dirty working tree and cannot be reproduced from HEAD alone. |
| Full JVM suites | **PASS** | testDebugUnitTest: 293 tests; testReleaseUnitTest: 293 tests. Both had 0 failures, 0 errors, 0 skipped. XML reports saved in the task output. These are current executions; the Gradle log lists both test tasks without UP-TO-DATE. |
| Lint | **PASS (with warnings)** | lintDebug: 0 errors, 149 warnings, 3 information; lintRelease: 0 errors, 135 warnings, 3 information. Existing compiler deprecation/opt-in warnings remain. |
| Compile/package | **PASS** | assembleDebug + assembleRelease; combined six-task verification invocation completed BUILD SUCCESSFUL in 2m23s (50 executed / 70 up-to-date tasks). JDK 17 from D:/Dev-Setup/jdk, workspace .gradle/.android-user-home. No code/config fix was needed this turn. |
| Signed final APK | **PASS** | package com.appause.android, versionName 0.5.46, versionCode 98, minSdk 26, targetSdk 35, not debuggable, no instrumentation. apksigner verify passed using APK Signature Scheme v2 (v1/v3/v3.1/v4 not enabled); zipalign 4-byte verification passed. Size 12,293,758 bytes. SHA-256 `ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927`. |
| Upgrade identity | **PASS (static only)** | Signer certificate SHA-256 `843d4ce0ed3b369b00fd5d22be90bfff454ec2ef5d8d415d6ec15c9489431525` matches the local v0.5.45 APK whose file hash matches the published GitHub asset. Candidate code 98 > published 97. Actual upgrade/data retention on a device remains NOT TESTED for this final file. |
| Release/debug isolation | **PASS (bounded)** | DiagnosticsScreenKt DEX descriptor absent in release and present in debug as a control. debug-only diagnostics/pro-tools/activation resources absent. Release source counterparts render no tools and activation-store mutators are inert. Two old main-resource labels (pro_debug_unlock / pro_debug_unlocked) remain; they are not proof of a reachable release override. No cleanup/refactor performed. |
| Accessibility / package content | **PASS** | Compiled accessibility config resolves to res/-N.xml; canRetrieveWindowContent=false. Release resource-name shortening required resolving the actual XML path through aapt2. Temporary Pass key/setter symbols present in release DEX. Public Room schemas 1.json/6.json are included as intended by current build.gradle.kts. |
| Artifact preservation | **PASS** | Old F91E... v0.5.46 files copied to previous-artifacts before rebuilding. Existing make_release.py packaged the verified build into output/Appause-v0.5.46.apk; a pinned copy and SHA256SUMS.txt live in output/release-candidate-APPAUSE-20261016-2356/. Copies match byte-for-byte. Rebuilding changes BUILD_TIME and may change the hash; distribute the pinned file, not a later rebuild under the same filename. |
| Runtime / visual / device | **NOT TESTED** | No ADB, install, emulator, physical-device, logcat, microphone/audio, Worker mutation or real activation performed. Earlier emulator/debug/HyperOS records in sections 42–43 are historical and do not pass this final release APK. |
| Known device defect | **OPEN (historical)** | Section 43: Back while the Temporary Pass duration chooser is open cancels the whole pause card instead of only collapsing the chooser. No fix or retest this turn. Home escape and pass-expiry acceptance also remain incomplete for this final release file. |
| Publication | **NOT DONE** | No commit/push/merge/tag/release/upload/deploy. GitHub/landing metadata remain on public v0.5.45. Mainland mirror still needs the selected new release file; no mirror URL invented. |

Verdict: **signed candidate ready for device acceptance; PUBLIC RELEASE NOT YET ACCEPTED**. Next: test this exact release file on the intended HyperOS device without uninstalling or clearing data.

## 45. Installed phone build and service read-only audit (2026-10-17 — APPAUSE-20261017-0950)

| Check | Result | Evidence / boundary |
|---|---|---|
| Physical device identity | **PASS** | Explicit serial 6036d5b, Android 16, user 0; device time 2026-10-17 09:51:59 +1300, Pacific/Auckland. ADB daemon auto-started on the host; no mobile UI automation or input. |
| Installed release | **PASS (identified), NOT CURRENT CANDIDATE** | com.appause.android 0.5.45 / 97; built Sep 29 14:58:02 NZDT; installed update Sep 29 14:59:10. Pulled base.apk SHA-256 de35488865fa805eb400facfe3aa9f90f4e113f72003371138b7893195b82278. This also differs from public GitHub v0.5.45 bytes (07cf...), so versionName alone does not identify the artifact. The installed signer matches the new candidate signer. |
| Installed debug | **PASS (identified), OLDER DEBUG BUILD** | com.appause.android.debug 0.5.46-debug / 98; built Oct 13 21:36:00 NZDT, updated Oct 13 21:37:03; SHA-256 f3a6b162cea330149c4c5b126f7bf09be710535d45bad9c8c8f34752756da048. Not running and accessibility not enabled in this snapshot. Separate package/storage from release; a newer debug version does not update release. |
| New candidate comparison | **PASS (not installed)** | Pinned Oct 16 23:57:30 release candidate 0.5.46 / 98 has SHA-256 ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927. Neither installed APK equals this file. No install was performed. |
| Service current state | **PASS (snapshot)** | Release pid 16665; accessibility Enabled lists release component; Bound lists Appause and package-scoped activity-services reports hasBound=true with system client. Binding/Crashed lists empty. Android 16's Bound line shows the label, not package id; package-scoped service binding confirms identity. Usage access / overlay / background appops all allow. This is not a functional interception test. |
| New visibility setting | **PASS (static APK observation)** | show_temporary_pass key absent from installed release DEX, present in installed debug and new candidate. The daily release does not contain the new visibility toggle. |
| Crash / exit records | **PASS (bounded observation)** | Release: 16 retained exits, no CRASH/ANR reason; UID10560 crash log buffer empty. Latest retained exit Oct 16 19:19:20 is SIGNALED/status9; cause unknown. Older entries explicitly say SwipeUpClean and critical_mem_pressure. These prove past kills, not a current code crash or a reproduced interception failure. Debug has two Sep29 exception-crash records, before the currently installed Oct13 debug build; do not attribute them to that build. |
| Device/behavior tests | **NOT TESTED** | No opening target apps, screenshot/UI-tree capture, interception/Cancel/Continue/Home/Back/temporary-pass expiry tests, activation, install/uninstall, force-stop, clear-data, settings or service changes. Earlier chooser+Back defect remains historical evidence in section 43, not a new reproduction on today's installed release. |
| Git/build | **NOT DONE** | No Android source/config changes, build, unit tests, lint, commit, push, merge, tag or release this turn. Current code/old device/new candidate proof layers remain separate. |

Evidence: ignored output/phone-audit-APPAUSE-20261017-0950/ (package-scoped dumps, pulled APKs, certificate checks, summary). Recommendation: after authorization, upgrade the release package in place with the pinned candidate; do not uninstall or clear data.
## 46. Signed release in-place upgrade and HyperOS core acceptance (2026-10-17 — APPAUSE-20261017-0954)

| Check | Result | Evidence / boundary |
|---|---|---|
| Exact artifact / upgrade | **PASS** | Explicit physical serial 6036d5b; Xiaomi 2410DPN6CC, Android 16 / HyperOS. `adb install -r` Success: release 0.5.45 / 97 to 0.5.46 / 98. Pulled installed-after.apk equals the pinned candidate SHA-256 ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927. Update time 2026-10-17 09:55:52; firstInstallTime unchanged at 2026-07-29 23:01:15. |
| Data retention | **PASS (bounded)** | Screenshots 02-before-home / 03-after-home preserve social media (3 apps, 10s cooldown), 娱乐 (empty, 10s), initial Today 4 completed / 2 avoided and Lifetime Pro Active. Group details retain re-remind 10 min. No full private database/history extraction or comparison; Pro display retention is not a fresh redemption test. Debug package unchanged. |
| Service / window | **PASS** | Release accessibility service auto-rebound after upgrade; package-scoped service reports hasBound=true. Actual pause above 小红书 is owned by com.appause.android with ty=ACCESSIBILITY_OVERLAY (2032), recorded in 11-windows.txt and 38-rearm-windows.txt. |
| Visibility toggle | **PASS** | First visit ON; OFF persists when leaving/re-entering Settings and hides the Temporary Pass entry on completed cooldown (13-pause-off-done). ON restores the entry (22-pause-on-done) and opens 5/15/30 min chooser (23). Left ON (35). No duration selected, no Temporary Pass granted; no process-death persistence test this round. |
| Cancel / countdown / Continue | **PASS** | Ten-second cooldown reaches enabled Continue. Cancel returns to launcher (14 visual). Continue after selecting Work focuses com.xingin.xhs/.index.v2.IndexActivityV2 (30-continue-window.txt); immediately returned Home without capturing the feed. Today increments one completed decision. |
| Home / re-entry | **PASS** | HOME during the pause focuses com.miui.home (26-home-window.txt); immediate target re-entry shows a fresh pause (27). |
| Leave window / re-arm | **PASS** | After Continue then leaving target, waited 202.1 seconds, longer than configured 180s; target re-entry produces a fresh accessibility-overlay cooldown (38). Awake-device result only; deep sleep not tested. |
| Chooser + Back | **PASS (user-accepted behavior; corrected in §47)** | Recorded behavior on this exact release: Back from duration chooser closes the entire pause and focuses launcher (24, 24-chooser-back-window.txt); next opening shows a new pause (25). User independently confirmed return-to-launcher and re-interception are intended. Earlier FAIL assumed chooser-only dismissal, which was not an agreed acceptance requirement. No pass issued or target admitted. No source fix required; duplicate-delivery speculation is not a confirmed defect. |
| Test side effects / restore | **PASS (recorded)** | Real test decisions changed Today from 4/2 to 5/5 (+1 completed, +3 avoided). Groups not edited, toggle restored ON, phone left at Appause Home (40-final-home). No uninstall, pm clear, force-stop, license mutation, appops, battery or service-setting changes. |
| Crash / ANR | **PASS (bounded observation)** | Current release UID10560 crash log buffer empty; no new crash/ANR exit observed during session. Retained buffers cannot prove absence of all failures. |
| Harness | **PASS (bounded)** | Explicit serial throughout. Used screencap plus visually grounded input and dumpsys, no uiautomator/UiAutomation hierarchy capture. Initial dumpsys window windows lacked mCurrentFocus: corrected to full dumpsys window; that probe assertion was a harness error, not an app failure. |
| Other acceptance | **NOT TESTED** | Temporary Pass issuance/expiry, re-remind timing, real activation, deep sleep/reboot, process death, other OEMs, Chinese UI and emulator on this final artifact. Earlier debug/emulator and offline checks remain separate historical evidence. |
| Build / Git / publication | **NOT TESTED / NOT DONE** | No Android source/config edit; no build, unit-test or lint rerun required for this test-only phase. Offline candidate evidence in §44. No staging, commit, push, merge, tag, release, upload or deploy. Baseline and final remain main@501a5c4, cached origin/main one ahead; all pre-existing changes preserved. |

Verdict (corrected by §47): **upgrade PASS; tested core physical-device flows PASS; full release checklist remains incomplete for untested paths**. Chooser Back is accepted, not a release blocker. Evidence: ignored `output/device-acceptance-APPAUSE-20261017-0954/`. No Back-routing fix recommended.

## 47. User acceptance correction for chooser Back (2026-10-17 — APPAUSE-20261017-1011)

- User independently tested and confirmed: Back returns to the desktop, then opening the target again triggers interception. This matches the intended product behavior. The earlier agent's chooser-only-dismissal expectation was not an agreed requirement; withdraw the defect classification and associated release blocker in sections 43/46 and historical handoffs. Preserve their raw observations as historical evidence.
- **PASS (accepted behavior):** exit to launcher does not grant a Temporary Pass or bypass subsequent interception. Prior session screenshots 24/25 corroborate the observed route; user confirmation is a separate human-acceptance layer, not a new automated device run.
- **NOT TESTED this correction:** device, build, unit tests, lint or other release-checklist paths. No Android code, APK or phone state changed. The previously recorded untested expiry/deep-sleep/other-OEM paths remain untested; this correction does not claim full release acceptance.
- Current result: upgrade and tested core flows PASS; no chooser Back repair needed. Remaining publication work includes verifying the intended latest APK download hosting; public metadata has not been changed in this correction.

## 48. Public download hosting verification (2026-10-17 — APPAUSE-20261017-1014)

| Check | Result | Evidence / boundary |
|---|---|---|
| Published website | **PASS (identified)** | Browser shows https://shmily0826.github.io/Appause/ still offering v0.5.45 / 97; download controls point directly at the versioned GitHub APK URL. Pages API reports built, main branch root. Local replacement landing page is still unpublished. |
| Public beta release | **PASS (identified)** | Live GitHub API: v0.5.45 is published, prerelease=true, published_at 2026-09-24T04:30:34Z. No v0.5.46 in the current release list. The /releases/latest API returns stable v0.5.43, not the newer public beta; avoid treating stable-latest as newest-beta metadata. |
| Direct APK download | **PASS** | Full current v0.5.45 download follows GitHub redirect to release-assets.githubusercontent.com and returns HTTP200, APK MIME type, Content-Disposition attachment; filename Appause-v0.5.45.apk, 12,268,450 bytes. SHA-256 07cfbfaace902ea718f46abc1bcfd2011f42087e73ed3c60a34a62921bf30b6a matches GitHub asset digest. It is a file download without navigating through the release page, but still depends on GitHub infrastructure. |
| Domestic mirror | **PASS (identified), STALE** | Opened the published Lanzou folder through the browser, submitted its public password1234 normally, observed only Appause-v0.5.39.apk, 11.6M, 2026-08-30. No current APK uploaded. This is a page/password flow, not a permanent direct file URL. No account login or security bypass. |
| Intended candidate | **PASS** | Rehashed the exact pinned Appause-v0.5.46.apk, 12,293,758 bytes, SHA-256 ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927. Its core device evidence is §46 as corrected by §47. Candidate still local; no upload/release performed. |
| Documentation | **PASS** | Corrected obsolete no-verified-signed-APK wording in README.md and bilingual docs/INSTALL.md. Public links remain v0.5.45 because v0.5.46 is not hosted. No HTML/Android source/config edit or rebuild. |
| Mainland performance / final mirror bytes | **NOT TESTED** | Current host is not proof of mainland access or speed. No v0.5.46 mirror URL exists to download/hash-check yet. No site publication, upload, Git write, device action, JVM or lint run. |

Evidence: ignored output/download-audit-APPAUSE-20261017-1014/verification.json and downloaded public APK. Next: place the exact pinned v0.5.46 APK on the selected domestic-accessible hosting and provide its download URL for verification. Using Lanzou requires its normal page/password flow; a direct download needs a public file URL from file hosting/object storage.

## 49. Uploaded Lanzou v0.5.46 and local download-page preparation (2026-10-17 — APPAUSE-20261017-1027)

| Check | Result | Evidence / boundary |
|---|---|---|
| Uploaded file listing | **PASS** | Normal browser share-folder password1234 flow now lists Appause-v0.5.46.apk, 11.7M, instead of the old v0.5.39. Opened its ordinary file page. |
| Mirror byte verification | **NOT VERIFIED (network verification)** | File frame initially displayed address timeout; normal reload exposed download buttons. CLI binary request returned HTTP200 with HTML/JS network-verification content, not APK bytes. Browser download button reached a page explicitly requiring verification because the network was abnormal. No CAPTCHA solved, challenge decoded/executed, cookie forged or access control bypassed. HTML response is not an APK hash mismatch. Saved as download-response.html.gz/html. |
| Human confirmation | **PASS (user-reported)** | Direct user reply: download works in their browser, and the uploaded file is the exact previously provided accepted APK. This supports identity/usability at the human layer; it is not a fresh machine-verified mirror checksum. |
| Pinned local file | **PASS** | Local pinned release SHA-256 still ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927, 0.5.46 / 98. No rebuild or overwrite of this release artifact. |
| Local web download route | **PASS** | index.html and zh.html now offer Lanzou as primary APK source, visibly show password1234 and v0.5.46 filename. Compact header/footer buttons go to the download section. GitHub release/file remain explicitly labeled older v0.5.45; no nonexistent GitHub v0.5.46 link. Shared release.js, static HTML fallbacks, JSON-LD, versionCode98, date2026-10-17 and expected checksum aligned. Regenerated both OG cards with the existing generator; icons untouched. |
| Docs | **PASS** | README, bilingual INSTALL, RELEASE_NOTES and RELEASE_CHECKLIST distinguish new Lanzou download, old GitHub release and still-unpublished local website update. No runtime defect classification restored; chooser Back remains accepted per §47. |
| Local responsive checks | **PASS (bounded)** | Shared web-inspector CLI: both languages at 1440x900 and 390x844 over localhost. Final desktop runs and sequential mobile confirmation runs have no horizontal overflow, console/page errors or failed requests. Download-area screenshots visually checked: version, button, password and older GitHub labels readable. Initial file:// font CORS failure and parallel mobile request refusals were harness issues; HTTP serving plus sequential mobile confirmation resolved them. No browser dependencies installed or project Playwright imported. |
| Static / script checks | **PASS** | Parsed both JSON-LD documents, checked primary/older URLs, no-JS version/code/file/hash fallbacks; Node VM confirmed release.js applies Lanzou primary and older GitHub release URL. git diff --check passes. |
| Android compile | **PASS** | assembleDebug BUILD SUCCESSFUL in38s; 38 tasks, 7 executed/31up-to-date. Android source/config unchanged; no new unit/lint/emulator/physical-device run this phase. Prior accepted release tests remain separate. |
| Publication | **NOT DONE** | Main still 501a5c4, cached origin/main one ahead, pre-existing dirty Android/web/docs preserved. No staging, commit, push, merge, tag, GitHub release or website deployment. User performed Lanzou upload; agent performed no upload. Full release checklist still has previously untested paths. |

Evidence: ignored output/mirror-verify-APPAUSE-20261017-1027/ (verification.json, pre-edit backups, screenshots, inspection JSON, build log and handoff). Next: explicit authorization to commit/push the prepared website and download materials for GitHub Pages publication, as required by AGENTS.md §10.

## 50. Authorized website publication preparation (2026-10-17 — APPAUSE-20261017-1038)

- Authorization: direct user approval to commit/push the 28 prepared website/resource/document files to main and publish through GitHub Pages. No Android code or APK publication authorized by this scope.
- Baseline: original main501a5c4 with existing dirty work; live remote main34579ec differs only by METRICS.csv / METRICS.md. Independent clean worktree starts at34579ec and preserves that snapshot. Approved input hashes checked before copying the exact allowlist.
- Prepared website uses Lanzou v0.5.46 / 98 with password1234; GitHub v0.5.45 links are explicitly older fallbacks. Local browser/static/build checks and the distinction between blocked automated mirror-byte verification and user-confirmed normal download are recorded in section49.
- Publication is verified only after push by checking the Pages build/deployment and live page/assets. Do not treat this pre-push entry as live deployment proof; the dated task handoff records final results and Git state.
