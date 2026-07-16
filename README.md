# Appause

**App + Pause** — Between the impulse and the app, take a moment.

Appause is a personal Android app that helps you build mindful habits. Create groups of distracting apps, set a cooldown for each group, and Appause will show a brief pause screen before those apps open. It's not a blocker — it's a speed bump for your attention.

## Example

| Group | Apps | Cooldown |
|-------|------|----------|
| Social Media | TikTok, Instagram, Reddit | 20 seconds |
| Entertainment | YouTube, Bilibili | 10 seconds |
| High Distraction | Games, short-video apps | 30 seconds |

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- Room (local database) + DataStore (preferences)
- AccessibilityService (app launch detection)
- Gradle Kotlin DSL + Version Catalog
- Min SDK 26 (Android 8.0), Target SDK 35

## Build

Open the project in Android Studio and run on an emulator or device (API 26+).

```bash
./gradlew assembleDebug
```

## Status

Phase 0 (Project Setup) — documentation and project structure created. See [PROGRESS.md](PROGRESS.md).

## Documentation

- [REQUIREMENTS.md](REQUIREMENTS.md) — Product requirements for MVP
- [ARCHITECTURE.md](ARCHITECTURE.md) — Technical design, data model, and development phases
