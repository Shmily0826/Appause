# Appause — Product Requirements

> "Between the impulse and opening the app, insert a brief pause."

Appause = **App** + **Pause**. A personal Android app that helps users build mindful habits by adding a configurable cooldown before opening distracting apps.

---

## 1. Core Concept

Users create **app groups** in Appause. Each group has a list of target apps and a cooldown duration (in seconds). When the user tries to open any target app, Appause intercepts the launch and shows a full-screen "pause" screen with a countdown timer. The user can either wait and proceed, or cancel and return to the home screen.

This is NOT an app blocker. It is a **speed bump** — a moment of reflection between impulse and action.

---

## 2. MVP Features (v1)

### 2.1 App Group Management
- Create, edit, and delete app groups
- Each group has: name, cooldown duration (seconds), list of target apps
- Each app can belong to **at most one group**
- Appause itself cannot be added to any group

### 2.2 App Selection
- Display all launchable apps on the device (apps with a Launcher icon)
- Show app icon and display name
- Support search by name
- Support multi-select
- Exclude Appause itself from the list
- Exclude system components and apps without a Launcher entry by default

### 2.3 App Launch Interception
- Detect when a target app is opened (from launcher, notification, recents, search, etc.)
- Show a full-screen Pause Screen with countdown
- Same-app Activity switches do NOT re-trigger interception
- After user proceeds (cooldown complete), temporarily allow the app
- When user leaves the target app, clear the bypass — next launch triggers cooldown again

### 2.4 Pause Screen
- Full-screen Activity
- Display: Appause brand, target app name + icon, prompt message, countdown timer
- "Cancel" button: return to home screen
- "Continue" button: appears only after countdown finishes
- Prevent re-interception immediately after proceeding

### 2.5 Settings
- Master on/off toggle
- Accessibility Service status indicator
- Shortcut to system Accessibility Settings
- Default prompt message (e.g., "Take a moment.")
- Basic debug info (interception count, service status)

### 2.6 Local-Only Data
- All data stored on device (Room database + DataStore)
- No backend, no login, no cloud sync, no analytics

---

## 3. Screen Specifications

### 3.1 Home Screen
- Show whether Appause is enabled (master toggle state)
- Show Accessibility Service authorization status
- List all created groups (name, app count, cooldown seconds)
- Floating action button or card to create a new group
- Tap a group to edit it

### 3.2 Group Editor Screen
- Text field for group name
- Number input for cooldown seconds (min: 1, max: 300)
- Button to select apps (navigates to Installed Apps Screen)
- Display currently selected apps with icons
- Save button (creates or updates the group)
- Delete button (only for existing groups, with confirmation)

### 3.3 Installed Apps Screen
- RecyclerView/LazyColumn of launchable apps
- Each row: app icon, app name, package name (small), checkbox
- Search bar at top
- Multi-select support
- "Select All" / "Deselect All" optional
- Return selected apps to Group Editor

### 3.4 Pause Screen
- Full-screen overlay (no status bar hiding needed for v1)
- Centered layout: Appause logo/text, target app icon + name, prompt text, countdown number
- Cancel button: always visible
- Continue button: disabled during countdown, enabled when timer reaches 0
- No back button behavior — pressing back should act as Cancel

### 3.5 Settings Screen
- Switch: Appause master toggle
- Status card: Accessibility Service enabled/disabled
- Button: "Open Accessibility Settings" (system intent)
- Text field: default prompt message
- Debug section: total interceptions today, service uptime, database group count

---

## 4. Core Flow

```
User opens target app
        │
        ▼
AccessibilityService detects foreground app change
        │
        ▼
Is it Appause itself? ──Yes──▶ Ignore
        │ No
        ▼
Is it the launcher / system UI? ──Yes──▶ Ignore
        │ No
        ▼
Is it in any Appause group? ──No──▶ Ignore
        │ Yes
        ▼
Is it currently bypassed (cooldown already completed)? ──Yes──▶ Ignore
        │ No
        ▼
Launch PauseActivity (full-screen cooldown)
        │
        ├─ User taps Cancel ──▶ Go to Home screen
        │
        └─ Countdown finishes ──▶ User taps Continue
                │
                ▼
        Add to bypass list, finish PauseActivity
                │
                ▼
        User enters target app normally
                │
                ▼
        User leaves target app (foreground changes to another app)
                │
                ▼
        Remove from bypass list — next launch triggers cooldown again
```

---

## 5. Non-Functional Requirements

- Minimum Android version: 8.0 (API 26, Oreo)
- Target SDK: latest stable (API 35)
- All UI in Jetpack Compose with Material 3
- No memory leaks (no Context/Activity/Drawable stored in ViewModel or database)
- Database stores only package names, not app icons or binaries
- AccessibilityService must not crash when disabled or killed by system
- App must handle process restart gracefully
- No hard-coded package names for specific apps (TikTok, Instagram, etc.)

---

## 6. Out of Scope for v1

- Backend / cloud sync / account system
- AI features or smart suggestions
- Payment / subscription
- iOS support
- App usage statistics or charts
- Scheduled profiles (e.g., "work mode" during 9-5)
- Widget or notification-based controls
- Overlay/Bubble permission (use regular Activity instead)
- Preventing bypass (user can always disable Accessibility Service)
- Per-app prompt messages (only a global default)
- Multiple languages (English only for v1)
