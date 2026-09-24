# Appause

**App + Pause** — put a brief moment between the impulse and the app.

Appause is a local-first Android focus tool. You choose target apps, place them
in groups, and set a cooldown. When a target app comes to the foreground,
Appause shows a short pause screen so you can breathe and decide whether to
continue.

## Latest release: v0.5.44 Public Beta

Download the signed APK from the canonical [GitHub Release](https://github.com/Shmily0826/Appause/releases/tag/v0.5.44):

- [Appause-v0.5.44.apk](https://github.com/Shmily0826/Appause/releases/download/v0.5.44/Appause-v0.5.44.apk)
- Package: `com.appause.android`
- versionCode: `96`

Future public release APKs use `Appause-v<version>.apk`. The Android
`versionCode` is internal metadata and is not part of the public filename.
See [INSTALL.md](INSTALL.md) before the first sideload.

## What it does

- **App groups** — organize target apps and manage a shared cooldown.
- **Pause screen** — a visible countdown before a target app opens. Continue is
  available after the countdown; explicit Cancel returns to the home screen.
- **Session and re-remind** — optionally receive another nudge while staying in
  a target app. Leaving for the home screen, or for a longer period, re-arms the
  cooldown.
- **Usage statistics** — review activity and reasons you chose to continue.
- **Recommended apps and dark mode** — useful setup shortcuts and a system-aware
  theme.
- **OEM guidance** — setup explains battery, auto-start, and background-service
  settings that matter on Xiaomi/HyperOS and similar devices.
- **Feedback** — Settings → Feedback lets you review a structured diagnostic
  snapshot before choosing to send a bug report or suggestion.
- **Appause Pro** — optional access to unlimited groups, re-remind, a custom
  pause prompt, and custom open reasons. Appause's core remains permanently
  free, with no subscription or paid version. The Pro page shows an optional,
  once-per-device 7-day trial while Free; after successful activation it shows
  a live countdown and hides lifetime-code entry. At expiry, the user can
  request a free, manually issued lifetime code through Settings → Feedback.
  Lifetime access shows status only. Android verifies signed, device-bound
  tokens locally and refreshes timed entitlement state at expiry without
  background Worker checks.

> **Public beta position (2026-09-22):** Appause remains free during the public
> beta; no payments, subscriptions, or paid tier are planned. Pro remains a
> clearly labelled, gated set of experimental features. The one-tap, once-per-
> device 7-day trial is opt-in and helps us learn about real engagement and
> invite feedback; without appropriate consented telemetry, it is not a
> precise D7-retention metric. After the trial, lifetime Pro access remains
> free and manually issued on request through the in-app feedback path (email or
> GitHub issue). This is staged access, not a promise that every Pro feature is
> enabled or bug-free.

## Screenshots

| Home | Pause screen | Groups |
|---|---|---|
| ![Home](images/screenshots/en/home.png) | ![Pause](images/screenshots/en/pause.png) | ![Groups](images/screenshots/en/group.png) |

| Statistics | Settings | Feedback |
|---|---|---|
| ![Statistics](images/screenshots/en/statistics.png) | ![Settings](images/screenshots/en/settings.png) | ![Feedback](images/screenshots/en/feedback.png) |

## Privacy and permissions

Appause stores groups, cooldowns, and statistics on the device using Room and
DataStore. It has no account, ads, analytics SDK, or cloud sync. The
AccessibilityService reads only the foreground package name;
`canRetrieveWindowContent` is `false`, so Appause does not read screen content,
messages, screenshots, or keystrokes. See [PRIVACY.md](PRIVACY.md) for the
complete policy.

| Setup item | Why it is used |
|---|---|
| **AccessibilityService — required** | Detects which app is in the foreground so configured target apps can be paused. |
| **Battery unrestricted — required for reliable background operation on Xiaomi/HyperOS and similar ROMs** | Prevents the system from killing the detection service. Also allow auto-start and, where available, lock Appause in recents. |
| **Usage Access — optional, recommended** | Confirms the genuinely foreground app locally and reduces false triggers from notifications. |
| **Display over other apps — optional fallback** | The normal pause screen uses an accessibility overlay. Grant this only if a device does not display the pause screen, so Appause can try its compatibility fallback. |
| **Notifications — optional for the ongoing status notification** | Android 13+ may ask for notification permission; it does not provide foreground-app detection. |
| **Internet — only for chosen actions** | Used when you explicitly start the Pro trial or redeem a lifetime activation code; the server-issued token is then verified locally, including expiry and device binding, and persisted for offline entitlement checks. Feedback is also sent only when chosen. It is not used for automatic license checks or ordinary status checks. |

Appause is an accessibility / habit-forming aid, not a monitoring, security, or
medical tool. You can always disable the service or change your groups.

## Requirements

- Android 8.0 or newer (API 26+)
- Release package: `com.appause.android`
- Direct APK distribution through GitHub Releases; Appause is not distributed
  through Google Play

## Build from source

The project uses Kotlin, Jetpack Compose, Room, DataStore, and Gradle Kotlin
DSL. Use JDK 17 and an Android SDK with API 35 available.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

Release signing uses a local, git-ignored signing configuration. Never commit a
keystore, password, private key, activation code, or Worker secret.

Diagnostics and Pro test controls are isolated to the `debug` build
(`com.appause.android.debug`). They are not included in the production Release
APK and are not a production activation path.

## v0.5.44 Public Beta validation status

- The public GitHub Release is `v0.5.44`, package `com.appause.android`,
  versionCode `96`. The accepted APK SHA-256 is
  `B2073E9138EEDF0FD3E31343304C04CFD6DB0D5CC199FBF69E961F34D68FA369`.
- The Pro trial retry compatibility fix accepts a freshly signed retry token
  while retaining signature, device-binding, tier, expiry, and exact seven-day
  window validation.
- Focused JVM policy tests and `assembleDebug` passed for the current
  Home/Recents and pause-overlay implementation.
- Emulator-only smoke verified Home dismisses the active pause presentation,
  Recents is not treated as Home, and an immediate target reopen is intercepted
  again.
- Xiaomi 2410DPN6CC / Android 16 physical validation also passed with objective
  ADB, logcat, and WindowManager evidence: `reason=homekey` dismissed the
  overlay; `reason=recentapps` left it present after about 1.8 seconds; and a
  Home-to-Bilibili reopen about 291 ms later produced a fresh second intercept.
  No subjective visual-smoothness measurement is claimed.
- The v0.5.44 Public Beta also passed a bounded Xiaomi Android 16 smoke:
  Accessibility health showed Enabled/Running before and after one Bilibili
  type-2032 interception and Home cleanup. This is not full OEM/device QA.

See [PROGRESS.md](PROGRESS.md) and [TEST_REPORT.md](TEST_REPORT.md) for the
full evidence ledger and its remaining boundaries.

## Install and feedback

- [Install guide](INSTALL.md)
- [Privacy policy](PRIVACY.md)
- [GitHub Issues](https://github.com/Shmily0826/Appause/issues)
- [v0.5.44 Public Beta Release](https://github.com/Shmily0826/Appause/releases/tag/v0.5.44)

## License

[MIT](LICENSE) © 2026 Appause authors.
