package com.appause.android.data.query

/**
 * Represents an installed app on the device.
 *
 * This is a lightweight data class — it only stores strings, NEVER a Drawable icon.
 * Icons are loaded on demand from PackageManager in the UI layer:
 *
 * ```kotlin
 * val icon = packageManager.getApplicationIcon(appInfo.packageName)
 * ```
 *
 * Why not store the icon here?
 * - Drawables hold references to Context and can cause memory leaks.
 * - Icons take up a lot of memory — loading all icons at once would be slow.
 * - The UI only needs icons for visible items (LazyColumn recycles them).
 */
data class AppInfo(
    /** The app's unique package name (e.g., "com.example.app"). */
    val packageName: String,

    /** The user-visible app name (e.g., "TikTok", "YouTube"). */
    val appName: String
)
