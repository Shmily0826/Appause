# AGENTS.md — Appause Agent Rules

This file defines rules that ALL AI agents (and human developers) MUST follow when working on the Appause project.

---

## 1. Development Workflow

1. **One task at a time.** Do not implement multiple features simultaneously.
2. **Read before modifying.** Always read existing code before making changes. Understand the current state first.
3. **No duplicate features.** Check if functionality already exists before creating new code.
4. **Compile after every change.** Run `./gradlew assembleDebug` after modifying code. If it fails, fix the error before proceeding.
5. **Never ignore compile errors.** Read the full error message, understand the root cause, and fix it properly.
6. **Do not expand scope.** Implement only what was requested. If you notice something that could be improved, note it but do not implement it unless asked.
7. **Update PROGRESS.md** after completing each phase or significant milestone.
8. **No unsolicited refactoring.** Do not rewrite or restructure existing working code unless explicitly requested.

---

## 2. Code Conventions

### For Beginners
- This project is a learning project. Code must be **readable by a beginner**.
- Prefer clear, explicit code over clever, concise code.
- Add comments to explain **why**, not **what**. (e.g., "We use applicationContext here to avoid memory leaks" instead of "Get context")
- Use meaningful variable and function names.

### Kotlin Style
- Follow official Kotlin coding conventions.
- Use `val` over `var` when the value doesn't change.
- Use data classes for data containers.
- Use sealed classes for navigation routes.
- Prefer `?.let {}` and `?: return` over nested null checks.
- Use coroutines for all async work — never block the main thread.

### Compose Style
- Keep Composable functions small and focused.
- Use `@Composable` only for UI functions.
- State should flow down (parameters), events should flow up (callbacks).
- Use `remember` for UI-local state, `ViewModel` for screen-level state.

### Architecture
- Follow the layered structure: `data/` → `repository/` → `ui/` (ViewModel → Screen).
- ViewModels never call DAO directly — always go through Repository.
- Screens (Composable functions) receive state from ViewModel, never from Room directly.
- Never store `Context`, `Activity`, or `Drawable` in ViewModel, Repository, or Database.
- Load app icons on demand from `PackageManager` in the UI layer.
- Store only `packageName` (String) in the database — never app icons or binaries.

---

## 3. AccessibilityService Rules

- The AccessibilityService is an **accessibility feature**, not a security or monitoring tool.
- Describe it honestly in all user-facing text.
- `canRetrieveWindowContent` must be `false` — we only need package names.
- Always check for null `packageName` in events.
- Always exclude: Appause itself, the launcher, system UI packages.
- Handle the service being disabled gracefully — the app must never crash.
- Do not attempt to re-enable the service programmatically — only provide a button to open system settings.

---

## 4. Interception Safety

- **Prevent infinite loops**: always check bypass list before intercepting.
- **Prevent self-interception**: always exclude Appause's own package name.
- **Prevent duplicate triggers**: track `lastForegroundPackage` and skip same-package events.
- **Cancel must go to Home**: pressing Cancel on PauseActivity must send the user to the launcher, not just finish the Activity.
- **Bypass must be temporary**: after the user proceeds, add to bypass list; when they leave the target app, remove from bypass list.
- **Process death is acceptable**: if the process is killed, the user simply sees the cooldown again on next launch. Do not over-engineer persistence for runtime state.

---

## 5. Dependency Management

- Use **Version Catalog** (`gradle/libs.versions.toml`) for all dependency versions.
- Do not hardcode version numbers in `build.gradle.kts`.
- Before adding a dependency, verify it is compatible with minSdk 26 and the current Compose BOM version.
- Do not add dependencies that are not required by the current phase.
- Do not mix incompatible library versions (e.g., different Compose compiler versions).

---

## 6. Git Conventions

- Commit messages should be concise and describe **what** and **why**.
- Example: `Add Room entities for AppGroup and GroupApp`
- Do not commit build artifacts (`build/`, `.gradle/`, `*.apk`).
- Do not commit IDE configuration files unless they contain shared project settings.
- Do not commit local.properties or files containing secrets.

---

## 7. Testing

- After each phase, verify the specific functionality described in ARCHITECTURE.md's testing checklist.
- Use logcat to verify AccessibilityService events during development.
- Test on a real device when possible — emulators may not perfectly replicate AccessibilityService behavior.
- If a phase involves UI, run the app and verify the screen visually.

---

## 8. Debug Tools (Diagnostics Screen)

The Diagnostics screen (`ui/diagnostics`) ships **only in the `debug` build flavor**
(`com.appause.android.debug`). Release builds do NOT include it. It exists purely for the
developer's manual testing and contains two test toggles:

1. **Pro on/off toggle** — forcibly sets the Pro-unlocked state locally so Pro-locked features
   (unlimited groups, re-remind, custom pause text / open reason) can be exercised without a
   real license. It does NOT issue or simulate a real license JWT; never treat it as a production
   activation path.
2. **Restart onboarding button** (v0.5.35) — calls `SettingsDataStore.clearOnboarding()` (clears
   `HAS_COMPLETED_ONBOARDING_KEY` and `HAS_SEEN_PERMISSION_INTRO_KEY`) then navigates to
   `Routes.ONBOARDING`, so the onboarding flow can be re-tested without uninstalling/reinstalling.

These are internal test aids for the developer only. Do not document them in README or expose them
to end users, and do not wire them into real product flows.

---

## 9. What NOT to Do

- Do not implement features from future phases.
- Do not add user accounts, cloud sync, or analytics SDKs. The Appause Pro
  activation Worker (`worker/`) is the **only** permitted backend — it issues
  device-bound license JWTs and must never receive, store, or sync user data.
  Do not extend it into an account/sync system.
- Do not add Flutter, React Native, or any cross-platform framework.
- Pause screen display on OEM ROMs (HyperOS/MIUI, Android 16):
  - `TYPE_ACCESSIBILITY_OVERLAY` (2032) is the PRIMARY window type. It sits above
    every app and anti-tamper apps (e.g. 小红书) CANNOT hide it via `setHideOverlayWindows`.
    This is the type the working release (base.apk / v0.5.1) uses, and it shows over 小红书.
    It requires the AccessibilityService's own window token: obtain `WindowManager` from the
    RAW `service` context (not a locale-wrapped/configuration context) or `addView()` throws
    `BadTokenException`.
  - `TYPE_APPLICATION_OVERLAY` (2038) gets HIDDEN by 小红书's `setHideOverlayWindows` even
    when `addView()` succeeds — so the pause screen is added but rendered INVISIBLE. Use it
    ONLY as a fallback when 2032 is rejected. (It does NOT require `SYSTEM_ALERT_WINDOW`.)
  - A regular foreground Activity is the LAST-resort fallback: 小红书 re-fronts itself and
    covers the Activity within ~300 ms on HyperOS, so the user may not see it. Do not treat
    Activity as the primary path.
  - Therefore: prefer 2032 → fall back to 2038 → fall back to Activity.
- Do not hardcode specific app package names (e.g., `com.zhiliaoapp.musically` for TikTok).
- Do not create empty interfaces, abstract classes, or "architecture placeholders" that have no immediate use.
- Do not add logging frameworks (use `android.util.Log` for debug logging).
- Do not add crash reporting or analytics SDKs. The in-app Feedback screen
  (`ui/feedback`) uses system Intents (email / GitHub issue) only — never
  send telemetry automatically.
- Do not assume AccessibilityService works identically on all OEM ROMs.
- Do not delete working code without a clear reason.
- Do not rewrite the entire project when encountering a single error.
