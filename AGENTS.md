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

---

## 10. AI Collaboration and Handoff Protocol

The web ChatGPT/planning agent and the local Codex coding agent have different
views of the project. The user keeps final decision-making authority.

### Responsibilities

- ChatGPT/planning agent: understand requirements, inspect GitHub state, analyze
  problems, control scope, plan tasks, review Codex handoffs, and recommend the
  next test, change, commit, push, or release decision.
- Codex/local coding agent: inspect the real local working tree, read local code,
  modify code when authorized, run builds/tests/ADB/emulator verification, check
  the diff, and report local changes that have not been pushed.

### State boundaries

Every status report must distinguish:

- **GitHub / pushed state** — commits and pushes that other agents can verify
  from GitHub.
- **Local working tree state** — local staged, unstaged, and untracked changes.
- **Manual / session verification** — emulator, ADB, logcat, real-device,
  user-confirmed results, and decisions not recorded in GitHub.

These states must not be described as if they were equivalent.

### Handoff requirements

At the end of every Codex turn, whether or not code changed, a handoff is
required without waiting for the user to request one. The handoff must begin
with a one- or two-sentence Simplified Chinese summary of what was done, the
current result, and the most important next step.

The handoff must then include:

- **Baseline** — branch, HEAD, GitHub tracking state, and working tree state as
  actually checked at the start of the turn.
- **What changed this turn** — actual work and files changed this turn,
  separately from pre-existing modifications.
- **Verification** — actual build, test, lint, diff, emulator, ADB, logcat, and
  real-device checks, each marked PASS, FAIL, or NOT TESTED. Emulator results
  must never be reported as real-device results.
- **Current implementation semantics** — for navigation, state, persistence,
  permissions, and background services, explain how the current code actually
  behaves rather than only saying that it was fixed.
- **Remaining risks / blockers** — untested paths, device or OEM risks, and
  regression concerns.
- **Final Git state** — a fresh `git status --short --branch` result, plus
  whether the turn committed, pushed, merged, tagged, or released anything.
- **Recommended next action** — one next step only. Do not begin another phase
  without the user's explicit authorization.

### Pre-existing changes and authorization

If the working tree is not clean, identify existing changes before editing.
Never overwrite, roll back, or casually rewrite them. If ownership or scope is
unclear, report the ambiguity instead of guessing. The handoff must distinguish
pre-existing modifications, changes made this turn, and untracked files.

The following permissions are independent:

- modifying code does not authorize committing;
- committing does not authorize pushing;
- pushing does not authorize merging, tagging, or releasing.

Without explicit user authorization, do not commit, push, merge, create tags, or
release.

The handoff must be self-contained for a ChatGPT agent that can read GitHub but
cannot see the local working tree or automatically know about local emulator,
ADB, logcat, or real-device results. Include current local facts without
copying the entire project history.

## 11. Task ID and Handoff Protocol

Use a stable Task ID to identify each ChatGPT ↔ Codex work item across copied
prompts and separate conversations. The format is:

```text
<PROJECT>-YYYYMMDD-HHMM
```

For Appause, use the `APPAUSE` project prefix, for example:
`APPAUSE-20260822-1705`.

### Incoming ChatGPT prompt

Every implementation, fix, test, or content task prepared for Codex should use
this envelope:

```text
===== TASK <TASK-ID> | CHATGPT → CODEX =====

Task ID: <TASK-ID>
Task: <short task title>
Direction: ChatGPT → Codex
Action: Execute this task, then return a Handoff for ChatGPT using the same Task ID.

...task instructions...

===== END TASK <TASK-ID> | CHATGPT → CODEX =====
```

The envelope is metadata and does not change the technical task. Do not invent
a replacement ID when one is supplied. A genuinely new revision or follow-up
should normally use a new Task ID.

### Codex handoff envelope

Every handoff must echo the exact same Task ID and short task title:

```text
===== TASK <TASK-ID> | CODEX → CHATGPT | HANDOFF =====

Task ID: <TASK-ID>
Task: <same short task title>
Direction: Codex → ChatGPT
Status: completed / partial / blocked

### Handoff for ChatGPT

...the repository's required handoff fields...

===== END TASK <TASK-ID> | CODEX → CHATGPT =====
```

Keep all fields required by Section 10, including baseline, pre-existing
changes, verification, implementation semantics, risks, final Git state, and
one recommended next action.

### Duplicate awareness

If the same Task ID has already been executed in the current Codex session,
explicitly report that the new prompt appears to be a duplicate instead of
silently repeating the task. The task may be rerun only after the user
explicitly confirms it. Do not create persistent repository state solely for
duplicate detection.

The Task ID must remain unchanged across the ChatGPT prompt and Codex handoff.
Modifying code, committing, pushing, merging, tagging, and releasing remain
separate permissions under Section 10.
