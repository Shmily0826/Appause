# Appause — Technical Architecture

---

## 1. Technology Stack

| Layer | Technology | Reason |
|-------|-----------|--------|
| Language | Kotlin | Official Android language, concise, null-safe |
| UI | Jetpack Compose + Material 3 | Modern declarative UI, less boilerplate |
| Database | Room | Compile-time SQL verification, Kotlin-friendly |
| Preferences | DataStore | Replaces SharedPreferences, coroutine-based |
| Navigation | Navigation Compose | Type-safe routing in Compose |
| Async | Coroutines + Flow | Non-blocking, lifecycle-aware |
| Detection | AccessibilityService | No overlay permission needed, works on most devices |
| App Query | PackageManager | System API to list launchable apps |
| Build | Gradle Kotlin DSL | Type-safe build scripts |
| Dependencies | Version Catalog | Centralized, shareable dependency versions |

---

## 2. Minimum SDK: API 26 (Android 8.0 Oreo)

### Why API 26?

- **Jetpack Compose**: requires minSdk 21, but we don't need to go that low
- **Material 3**: works with any Compose-supported minSdk
- **AccessibilityService**: available since API 14, no issue
- **Room / DataStore**: require API 21+, no issue
- **Device coverage**: ~93% of active Android devices run API 26+
- **API 24-25 (Nougat)**: only ~4% of devices, introduces split-screen edge cases and additional compatibility code
- **API 26+** gives us: notification channels, adaptive icons, better background execution limits (cleaner for our service)

### What we DON'T need from higher APIs:

- `UsageStatsManager` (API 22) — we use AccessibilityService instead
- `SYSTEM_ALERT_WINDOW` overlay — we use a regular Activity for the pause screen
- Bubble API (API 29) — not needed for v1

**Decision: minSdk = 26, compileSdk = 35, targetSdk = 35.**

---

## 3. Project Directory Structure

```
Appause/
├── gradle/
│   └── libs.versions.toml              # Version Catalog: all dependency versions
├── build.gradle.kts                    # Root: plugin declarations only
├── settings.gradle.kts                 # Module includes, repositories
├── gradle.properties                   # JVM args, AndroidX flags
│
├── app/
│   ├── build.gradle.kts                # App module: plugins, dependencies, config
│   ├── proguard-rules.pro              # ProGuard/R8 rules
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml     # App metadata, service, permissions
│           ├── res/
│           │   ├── values/             # Strings, colors, themes
│           │   ├── mipmap/             # App icons (adaptive)
│           │   └── xml/
│           │       └── accessibility_service_config.xml
│           └── java/com/appause/android/
│               ├── AppauseApp.kt              # Application class (optional)
│               ├── MainActivity.kt            # Single Activity host
│               │
│               ├── data/                       # ── Data Layer ──
│               │   ├── local/
│               │   │   ├── AppDatabase.kt     # Room database definition
│               │   │   ├── AppGroup.kt        # Entity: app group
│               │   │   ├── AppGroupDao.kt     # DAO: group queries
│               │   │   ├── AppLaunchRecord.kt # Entity: launch log
│               │   │   ├── AppLaunchDao.kt    # DAO: launch log queries
│               │   │   └── Converters.kt      # Room type converters
│               │   ├── repository/
│               │   │   └── AppGroupRepository.kt  # Single source of truth
│               │   └── settings/
│               │       └── SettingsDataStore.kt   # DataStore preferences
│               │
│               ├── service/                    # ── Service Layer ──
│               │   └── AppauseAccessibilityService.kt
│               │
│               ├── interception/               # ── Interception Logic ──
│               │   └── InterceptionManager.kt  # Singleton: bypass state
│               │
│               └── ui/                         # ── UI Layer ──
│                   ├── theme/
│                   │   ├── Theme.kt
│                   │   ├── Color.kt
│                   │   └── Type.kt
│                   ├── navigation/
│                   │   └── NavGraph.kt        # Navigation routes
│                   ├── home/
│                   │   ├── HomeScreen.kt
│                   │   └── HomeViewModel.kt
│                   ├── groupeditor/
│                   │   ├── GroupEditorScreen.kt
│                   │   └── GroupEditorViewModel.kt
│                   ├── appselect/
│                   │   ├── AppSelectScreen.kt
│                   │   └── AppSelectViewModel.kt
│                   ├── pause/
│                   │   ├── PauseActivity.kt   # Separate Activity!
│                   │   └── PauseViewModel.kt
│                   └── settings/
│                       ├── SettingsScreen.kt
│                       └── SettingsViewModel.kt
```

### Key Design Decisions

1. **PauseActivity is a SEPARATE Activity**, not a Compose screen in the nav graph.
   Reason: it must be launched from the AccessibilityService, which has no access to the main Activity's navigation controller. It gets its own Compose content.

2. **InterceptionManager is a singleton object** that holds runtime interception state (bypass list, last foreground package).
   Reason: the AccessibilityService and PauseActivity run in the same process but are different components. They need a shared state holder. A singleton `object` is the simplest approach for v1.

3. **Repository wraps DAO**, ViewModels never call DAO directly.
   Reason: single source of truth, easier to test and modify later.

---

## 4. Data Model (Room)

### 4.1 Entity: `app_groups`

| Column | Type | Constraint | Description |
|--------|------|------------|-------------|
| id | Long | PRIMARY KEY, autoGenerate | Unique group ID |
| name | String | NOT NULL | Display name (e.g., "Social Media") |
| cooldownSeconds | Int | NOT NULL | Wait time (1–300) |
| createdAt | Long | NOT NULL | Creation timestamp (epoch millis) |

### 4.2 Entity: `group_apps`

| Column | Type | Constraint | Description |
|--------|------|------------|-------------|
| packageName | String | PRIMARY KEY | Target app's package name |
| groupId | Long | NOT NULL, FK → app_groups.id | Parent group |

Why `packageName` as primary key? This enforces "one app belongs to one group" at the database level.

### 4.3 Entity: `app_launch_records`

| Column | Type | Constraint | Description |
|--------|------|------------|-------------|
| id | Long | PRIMARY KEY, autoGenerate | Unique record ID |
| packageName | String | NOT NULL | Which app was intercepted |
| groupId | Long | NOT NULL, FK → app_groups.id | Which group triggered it |
| timestamp | Long | NOT NULL | When it happened (epoch millis) |
| action | String | NOT NULL | "cancelled" or "proceeded" |

### 4.4 DataStore Keys

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `is_enabled` | Boolean | true | Master toggle |
| `default_prompt` | String | "Take a moment." | Pause screen message |

### 4.5 Type Converters

Room stores simple types by default. We need a `Converters` class for:
- `List<String>` ↔ `String` (if we ever store lists in a column — v1 may not need this since `group_apps` is a separate table)

---

## 5. AccessibilityService: How It Works

### 5.1 What is AccessibilityService?

AccessibilityService is a system-level service designed to help users with disabilities. It receives callbacks when UI events happen across ALL apps on the device. We use it to detect when a new app comes to the foreground.

**Important: this is an accessibility feature, NOT a security or monitoring tool.**

### 5.2 Setup Steps

1. **Create the service class** extending `AccessibilityService`
2. **Register in AndroidManifest.xml** with `<service>` tag, `BIND_ACCESSIBILITY_SERVICE` permission, and metadata pointing to the config XML
3. **Create config XML** (`res/xml/accessibility_service_config.xml`) specifying which events to listen for
4. **User manually enables** the service in: Settings → Accessibility → Appause → Enable

The user MUST enable it manually. There is no way to enable it programmatically (this is a security restriction by Android).

### 5.3 Event Detection Flow

```
System sends AccessibilityEvent
        │
        ▼
onAccessibilityEvent(event)
        │
        ▼
Filter: event.eventType == TYPE_WINDOW_STATE_CHANGED?
        │ (This event fires when a new Activity window becomes active)
        │
        ▼
Get: event.packageName (CharSequence?)
        │
        ▼
Validate: is it non-null, non-system, non-launcher, non-Appause?
        │
        ▼
Check: is this packageName in any configured group?
        │
        ▼
Check: is it currently bypassed?
        │
        ▼
If interception needed → launch PauseActivity with FLAG_ACTIVITY_NEW_TASK
```

### 5.4 Config XML

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="false"
    android:notificationTimeout="100"
    android:packageNames="@null"
    android:description="@string/accessibility_service_description" />
```

Key settings:
- `typeWindowStateChanged`: fires when a new Activity window is shown
- `feedbackGeneric`: we don't provide actual accessibility feedback
- `canRetrieveWindowContent="false"`: we only need the package name, not window content (privacy-friendly)
- `notificationTimeout="100"`: minimum 100ms between events (reduces spam)

### 5.5 Known Limitations

1. **Event frequency**: TYPE_WINDOW_STATE_CHANGED fires for EVERY Activity transition across ALL apps. Dialogs, permission screens, and in-app navigation all trigger events. We must filter aggressively.

2. **Duplicate events**: the same package name may appear in consecutive events (e.g., Activity A → Activity B within the same app). We track `lastForegroundPackage` and skip if unchanged.

3. **Device fragmentation**: some OEM ROMs (MIUI, ColorOS, EMUI) may not always include `packageName` in events, or may kill background services aggressively. There is no universal fix — we handle null packageName gracefully.

4. **No "app closed" event**: there is no reliable callback for when a user leaves an app. We detect "leaving" by observing that the foreground package has changed to something other than the target app.

5. **Service can be killed**: Android may stop the service to free memory. When restarted, in-memory state (bypass list, last foreground package) is lost. This is acceptable for v1 — the user will simply see the cooldown again on next launch.

6. **Cannot self-enable**: the user must go to system settings to enable the service. We can only provide a button that opens the settings page.

7. **Recent tasks**: restoring an app from recents triggers TYPE_WINDOW_STATE_CHANGED, so our interception works. However, some launchers handle recents differently — edge cases are possible.

---

## 6. State Management: Avoiding Interception Loops

### 6.1 The Core Problem

When Appause intercepts an app launch and shows PauseActivity, the system reports that the foreground app has changed to Appause. When the user taps "Continue" and PauseActivity finishes, the foreground returns to the target app — which would trigger ANOTHER interception. Infinite loop.

### 6.2 Solution: Bypass List + Package Tracking

`InterceptionManager` (singleton object) maintains:

```
bypassedPackages: MutableSet<String>     // Apps currently allowed through
lastForegroundPackage: String?           // Last seen foreground package
```

**Interception rules:**

| Condition | Action |
|-----------|--------|
| Package == Appause itself | Always ignore |
| Package == launcher | Always ignore |
| Package is null or system UI | Always ignore |
| Package == lastForegroundPackage | Skip (same app, Activity switch) |
| Package is in bypassedPackages | Skip (cooldown already completed) |
| Package is in a configured group | **INTERCEPT** → show PauseActivity |
| Package is NOT in any group | Ignore |

**PauseActivity actions:**

| User action | What happens |
|-------------|-------------|
| Cancel | Finish PauseActivity → send user to Home screen (launcher Intent) |
| Continue (after countdown) | Add package to bypassedPackages → finish PauseActivity |

**Bypass cleanup:**

When AccessibilityService detects that the foreground package is NO LONGER the bypassed target (and is not PauseActivity), remove the target from bypassedPackages.

```
Foreground changes: targetApp → otherApp (not Appause, not targetApp)
    → Remove targetApp from bypassedPackages
    → Next launch of targetApp will trigger cooldown again
```

### 6.3 Timeout Safety Net

If the user never leaves the target app (e.g., force-closes Appause), the bypass remains forever. To prevent this, each bypass has a timeout (e.g., 5 minutes). After timeout, the bypass is automatically removed.

For v1, we can use a simple coroutine delay in InterceptionManager. If the process is killed, the bypass is lost anyway (which is fine — user just sees the cooldown again).

---

## 7. Common Pitfalls and Mitigations

### 7.1 Infinite Interception Loop
**Problem**: PauseActivity finishes → target app visible → service detects target app → intercepts again.
**Solution**: Add target to bypass list before finishing PauseActivity. Service checks bypass list before intercepting.

### 7.2 Same-App Activity Switches
**Problem**: User opens YouTube → watches video → clicks channel → new Activity fires event → intercepted again.
**Solution**: Track `lastForegroundPackage`. If current package equals last seen package, skip. Only intercept on package CHANGE.

### 7.3 Context/Activity Leaks
**Problem**: Storing Context or Activity references in ViewModel, singleton, or database.
**Solution**: Use `applicationContext` in ViewModels (via `AndroidViewModel`). Never store Drawable or Activity references. Load icons on-demand from PackageManager.

### 7.4 Service Killed by System
**Problem**: AccessibilityService process killed → in-memory bypass state lost → user sees cooldown again on next launch.
**Solution**: Acceptable for v1. Room database persists group configuration across restarts. Only runtime bypass state is lost.

### 7.5 PackageManager Blocking Main Thread
**Problem**: `getInstalledPackages()` and `getApplicationInfo()` can be slow on devices with many apps.
**Solution**: Always call PackageManager methods on `Dispatchers.IO` in a coroutine. Cache results in the ViewModel.

### 7.6 Appause Intercepting Itself
**Problem**: User opens Appause settings → service detects Appause package → tries to intercept.
**Solution**: Always check if `packageName == context.packageName` and skip. Hard-code this exclusion.

### 7.7 Cancel Behavior
**Problem**: User taps Cancel on PauseActivity, but finishing the Activity just reveals the target app underneath.
**Solution**: Before finishing, launch the Home screen via `Intent(ACTION_MAIN, CATEGORY_HOME)`. This moves the target app to the background.

### 7.8 Process Death and State
**Problem**: Android kills the process while cooldown is active → user reopens app → state is lost.
**Solution**: For v1, the cooldown is only relevant while the process is alive. If killed, the user simply sees the cooldown again on next app launch (which is the intended behavior anyway).

---

## 8. Development Phases

### Phase 0: Project Setup ← **Current**
- Create Android Studio project structure
- Configure Gradle, Version Catalog, package name
- Create documentation files
- Verify project compiles with empty Compose Activity

### Phase 1: Data Layer
- Room entities (AppGroup, GroupApp, AppLaunchRecord)
- DAOs with queries
- AppDatabase with type converters
- DataStore for settings
- Repository wrapping DAOs

### Phase 2: App Query
- PackageManager wrapper to list launchable apps
- AppInfo data class (packageName, appName, icon loaded on demand)
- Filtering logic (exclude system apps, exclude Appause)

### Phase 3: Navigation + Theme
- Material 3 theme (Color, Type, Theme)
- Navigation Compose routes
- Screen scaffolds (empty screens with titles)

### Phase 4: Home Screen
- Display service status
- List groups from Room
- Navigate to group editor

### Phase 5: Group Editor + App Selection
- Create/edit group form
- App selection screen with search + multi-select
- Save/delete group via repository

### Phase 6: AccessibilityService
- Service implementation
- Manifest registration
- Event filtering logic
- InterceptionManager singleton

### Phase 7: Pause Screen
- PauseActivity with Compose UI
- Countdown timer (coroutine-based)
- Cancel and Continue actions
- Integration with InterceptionManager

### Phase 8: Settings + Polish
- Settings screen with DataStore
- Debug info
- Edge case handling
- Testing on device

---

## 9. Build and Run

```bash
# From Android Studio:
# 1. Open the Appause/ folder
# 2. Wait for Gradle sync
# 3. Run on emulator or connected device (API 26+)

# From command line:
./gradlew assembleDebug
./gradlew installDebug
```

---

## 10. Testing Checklist (per phase)

- [ ] Phase 0: Project opens in Android Studio, Gradle sync succeeds, empty Activity runs
- [ ] Phase 1: Room database creates, DAOs insert/query work (verified via logcat or unit test)
- [ ] Phase 2: Installed apps list shows correctly, search works
- [ ] Phase 3: Navigation between screens works, theme applies
- [ ] Phase 4: Home screen shows groups, service status accurate
- [ ] Phase 5: Can create/edit/delete groups with apps
- [ ] Phase 6: AccessibilityService detects app launches (verified via logcat)
- [ ] Phase 7: Pause screen shows countdown, cancel/continue work
- [ ] Phase 8: Settings persist, debug info accurate
