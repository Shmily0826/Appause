# Appause

**App + Pause** — Between the impulse and the app, take a moment.

Appause is a personal Android app that helps you build mindful habits. Create
groups of distracting apps, set a cooldown for each group, and Appause shows a
brief pause screen before those apps open. It's not a blocker — it's a *speed
bump* for your attention.

> 🔒 **Privacy-first:** Appause is **local-first**. No account, no analytics,
> no ads, and your groups/stats never leave your device. The only network use
> is a **one-time** license check when you activate Appause Pro — daily use is
> fully offline. See [PRIVACY.md](PRIVACY.md).

---

## Features

- **App groups** — bundle distracting apps (e.g. Social, Entertainment) and
  manage them together.
- **Per-group cooldown** — a configurable pause (e.g. 10–30s free / up to 60s
  Pro) before a grouped app opens.
- **Pause screen** — a calm, dismissible screen instead of an instant block.
  Cancel always returns you to the home screen.
- **Session timer + re-remind** — optionally get nudged again while you're still
  inside a distracting app. A session keeps counting even if you briefly switch
  apps; a real leave (home screen, or 3+ minutes away) re-arms the cooldown.
- **Dark mode** — follows the system theme.
- **Recommended apps** — suggestions to help you build your first groups.
- **Usage stats** — see how your groups are performing over time.
- **OEM guidance** — built-in explanations for why detection may stop on some
  devices (battery optimization, auto-start) and how to fix it.
- **In-app feedback** — report a bug or suggest a feature straight from
  Settings. Device info is attached automatically and shown to you before
  sending (email or GitHub issue).
- **Appause Pro (optional)** — unlock unlimited groups, longer cooldowns,
  custom pause text and full history. Activation is a one-time, offline-verified
  license — see [worker/README.md](worker/README.md).

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
account and no cloud sync. The only network request is a **one-time** license
redeem when you activate Appause Pro; after that, Pro works fully offline.

---

## Permissions

| Permission | Why |
|------------|-----|
| AccessibilityService | Detect the foreground app by package name (see above). |
| Foreground Service | Keep detection alive while the device is in use. |
| POST_NOTIFICATIONS (Android 13+) | Show the "detection active" notification. |
| INTERNET | Only for the one-time Appause Pro license redeem. Not used during normal use. |

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

Read the full [Privacy Policy](PRIVACY.md). In short: no account, no analytics,
no ads — your groups and stats stay on your device. The only network use is a
one-time Pro license check, and feedback is sent only when *you* choose to.

---

## Status

Current version: **0.3.9**. All MVP phases are complete (project setup, data
layer, interception, groups, pause UI, stats, re-remind, dark mode, OEM
guidance, in-app feedback). Appause Pro (server-activated license) is in
preview.

Known work:

- Real-device testing across OEM ROMs (Xiaomi HyperOS, Huawei, OPPO, vivo) to
  verify AccessibilityService survival after reboot / battery optimization.
- On-boarding flow that walks users through auto-start and battery whitelisting
  per device.
- Plan B step 4: payment / code distribution for Appause Pro.

See [PROGRESS.md](PROGRESS.md) for the full development log.

---

## Contributing

Issues and pull requests are welcome. Please read [AGENTS.md](AGENTS.md) — it
defines the project's coding conventions and the rules every contributor (and
AI agent) must follow. Keep changes focused on one task at a time, and run
`./gradlew assembleDebug` after modifying code.

---

## Feedback

Found a bug or have an idea? You can report it without leaving the app:

- **In-app:** Settings → **Feedback** — pick "Bug report" or "Suggestion",
  write your message, and send via email or open a GitHub issue. Your app
  version, Android version, device model and locale are attached automatically
  (and shown to you before sending).

- **Direct:** open an issue on
  [GitHub](https://github.com/Shmily0826/Appause/issues) or email
  [rng2018520@gmail.com](mailto:rng2018520@gmail.com).

Appause does not collect telemetry, so please include steps to reproduce for
bugs — the auto-attached device info helps a lot.

---

## 📊 Project metrics

Curious how the project is doing? Star/fork counts, cumulative Release download
totals, and 14-day traffic are collected automatically (GitHub Actions, weekly)
and logged in [METRICS.md](METRICS.md). **No user or device data is tracked** —
only public, aggregate repository stats, consistent with Appause's
privacy-first design.

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
