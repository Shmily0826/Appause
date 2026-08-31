# Appause

**App + Pause** — put a brief moment between the impulse and the app.

Appause is a local-first Android focus tool. You choose target apps, place them
in groups, and set a cooldown. When a target app comes to the foreground,
Appause shows a short pause screen so you can breathe and decide whether to
continue.

## Latest release: v0.5.39

Download the signed APK from the canonical [GitHub Release](https://github.com/Shmily0826/Appause/releases/tag/v0.5.39):

- [Appause-v0.5.39.apk](https://github.com/Shmily0826/Appause/releases/download/v0.5.39/Appause-v0.5.39.apk)
- Package: `com.appause.android`
- SHA-256: `cc05a6b742018d907d71d1bd3f07b3044accd6ad4ab582f900ea55e1a1248a3f`

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
- **Appause Pro** — optional activation unlocks unlimited groups, re-remind, a
  custom pause prompt, and custom open reasons. A user-initiated redeem obtains
  a signed, device-bound token that Appause verifies locally. Tokens may carry
  an expiry configured for their activation code; Appause does not auto-refresh
  or perform background license checks. If a stored token expires, the user may
  need to redeem the code again or import another valid token.

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
| **Internet — only for chosen actions** | Used when you explicitly redeem Pro; importing a token does not need a network; feedback is also sent only when chosen. It is not used for automatic license checks or ordinary status checks. |

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

## v0.5.39 validation status

- Android debug unit tests: **80/80 passed**; Worker tests: **31/31 passed**.
- Release candidate gates passed: debug tests, debug and release assembly,
  release lint, release bundle, packaging script, diff checks, and release
  signing verification. The Release APK is non-debuggable, and the public
  GitHub asset matches the verified local artifact digest above.
- Physical-device post-release smoke passed data-preserving installation,
  package/version checks, installed base-APK hash comparison, configured and
  running AccessibilityService checks, overlay/usage/notification app-op checks,
  launch/process checks, and an app-specific crash/ANR scan.
- A separate post-release interception run against an already-configured target
  app was not performed because the phone was on its lock screen/AOD. Earlier
  v0.5.39 RC device evidence exercised the pause/continue/cancel and system
  navigation flow, but it should not be presented as a new post-release
  interception result.

See [PROGRESS.md](PROGRESS.md) and [TEST_REPORT.md](TEST_REPORT.md) for the
full evidence ledger and its remaining boundaries.

## Install and feedback

- [Install guide](INSTALL.md)
- [Privacy policy](PRIVACY.md)
- [GitHub Issues](https://github.com/Shmily0826/Appause/issues)
- [Latest GitHub Release](https://github.com/Shmily0826/Appause/releases/latest)

## License

[MIT](LICENSE) © 2026 Appause authors.
