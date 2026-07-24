# Appause

**App + Pause** — Between the impulse and the app, take a moment.

Appause is a personal Android app that helps you build mindful habits. Create
groups of distracting apps, set a cooldown for each group, and Appause shows a
brief pause screen before those apps open. It's not a blocker — it's a *speed
bump* for your attention.

> 🔒 **Privacy-first:** Appause is fully local. No account, no network, no
> analytics, no ads. Your data never leaves your device. See
> [PRIVACY.md](PRIVACY.md).

---

## Features

- **App groups** — bundle distracting apps (e.g. Social, Entertainment) and
  manage them together.
- **Per-group cooldown** — a configurable pause (e.g. 10–30s) before a grouped
  app opens.
- **Pause screen** — a calm, dismissible screen instead of an instant block.
  Cancel always returns you to the home screen.
- **Re-remind** — optionally get nudged again if you reopen the app during the
  cooldown window.
- **Dark mode** — follows the system theme.
- **Recommended apps** — suggestions to help you build your first groups.
- **Usage stats** — see how your groups are performing over time.
- **OEM guidance** — built-in explanations for why detection may stop on some
  devices (battery optimization, auto-start) and how to fix it.

---

## Screenshots

> 📷 Add 3–5 screenshots here (group list, pause screen, settings) before
> publishing. A short GIF / video of the pause-screen countdown is the single
> most effective promo asset.

---

## How it works

Appause uses Android's **AccessibilityService** to detect which app is in the
foreground, by its **package name only** (`canRetrieveWindowContent = false` —
it never reads your screen). When the foreground app belongs to a group you
configured, Appause shows the pause screen for that group's cooldown.

Everything is stored **locally** (Room database + DataStore). There is no
server and no cloud sync.

---

## Permissions

| Permission | Why |
|------------|-----|
| AccessibilityService | Detect the foreground app by package name (see above). |
| Foreground Service | Keep detection alive while the device is in use. |
| POST_NOTIFICATIONS (Android 13+) | Show the "detection active" notification. |

Appause does **not** request the `SYSTEM_ALERT_WINDOW` (overlay) permission;
the pause screen uses an accessibility overlay instead.

---

## Requirements

- **Android 8.0+** (API 26)
- **Target SDK 35**
- To build: **JDK 17** and the Android SDK (see below)

---

## Build from source

### Prerequisites

- **JDK 17** (set `JAVA_HOME` to a JDK 17 install; the build fails on older
  JDKs such as Java 7/8).
- Android SDK with a platform for API 35.

### Using Android Studio

Open this folder in Android Studio, then **Run** or **Build → Build Bundle(s)
/ APK(s)**.

### Using the command line

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires your own signing key — see note below)
./gradlew assembleRelease
```

> ℹ️ **Signing:** `release` builds need a signing key. Store it (and any
> passwords) in a **local, git-ignored** file (e.g. `signing.properties`) — never
> commit keys. `local.properties`, `*.keystore`, `*.jks`, and `signing.properties`
> are already excluded by [.gitignore](.gitignore).

---

## Install

- **GitHub Releases** — download the latest signed APK.
- **CoolApk (酷安)** — available on the app's CoolApk page.

> Appause is distributed directly as an APK. It is **not** on Google Play,
> because Google Play restricts AccessibilityService to accessibility-use cases
> for users with disabilities; Appause is a habit-forming tool for everyone.

---

## Privacy

Read the full [Privacy Policy](PRIVACY.md). In short: no account, no network,
no analytics, no ads — all data stays on your device.

---

## Status

Current version: **0.2.1**. All planned MVP phases are complete (project
setup, data layer, interception, groups, pause UI, stats, re-remind, dark mode,
OEM guidance).

Known pre-release work:

- Real-device testing across OEM ROMs (Xiaomi HyperOS, Huawei, OPPO, vivo) to
  verify AccessibilityService survival after reboot / battery optimization.
- On-boarding flow that walks users through auto-start and battery whitelisting
  per device.

See [PROGRESS.md](PROGRESS.md) for the full development log.

---

## Contributing

Issues and pull requests are welcome. Please read [AGENTS.md](AGENTS.md) — it
defines the project's coding conventions and the rules every contributor (and
AI agent) must follow. Keep changes focused on one task at a time, and run
`./gradlew assembleDebug` after modifying code.

---

## Contact

- GitHub: [Shmily0826](https://github.com/Shmily0826)
- Email: rng2018520@gmail.com

---

## License

[MIT](LICENSE) © 2026 Appause authors.

---

## Disclaimer

Appause is an accessibility / habit-forming aid, not a medical, therapeutic, or
security tool. It cannot and does not guarantee behavior change.
