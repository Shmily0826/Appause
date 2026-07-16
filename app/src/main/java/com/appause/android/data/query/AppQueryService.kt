package com.appause.android.data.query

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * AppQueryService — queries the device for launchable (user-visible) apps.
 *
 * How it works:
 * - Uses PackageManager.queryIntentActivities() with MAIN/LAUNCHER intent.
 * - This returns only apps that have a Launcher icon — exactly what users see
 *   on their home screen or app drawer.
 * - System components, background services, and hidden apps are automatically excluded.
 *
 * Why not use getInstalledPackages()?
 * - getInstalledPackages() returns ALL installed packages, including system services,
 *   background agents, and apps without any UI. We would need to filter them manually.
 * - queryIntentActivities() with LAUNCHER intent does the filtering for us.
 *
 * Thread safety:
 * - PackageManager queries can be slow on devices with many apps (100ms+).
 * - Always call these methods from a coroutine on Dispatchers.IO.
 *
 * Context:
 * - We use applicationContext (passed from AppauseApp) to avoid memory leaks.
 * - Never pass an Activity context here — the service may outlive the Activity.
 */
class AppQueryService(private val context: Context) {

    companion object {
        private const val TAG = "AppQueryService"
    }

    /**
     * Get all launchable apps on the device, excluding Appause itself.
     *
     * The returned list is sorted alphabetically by app name.
     *
     * @return List of AppInfo objects. Icons are NOT included — load them
     *         on demand in the UI via packageManager.getApplicationIcon().
     */
    fun getLaunchableApps(): List<AppInfo> {
        val packageManager = context.packageManager

        // Create an intent that matches all apps with a Launcher icon.
        // This is the standard way to find "user-visible" apps.
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Query all activities that match this intent.
        // Each result represents one launcher entry (one app icon on the home screen).
        val resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0)

        val appList = mutableListOf<AppInfo>()
        val myPackageName = context.packageName

        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo.packageName

            // Skip Appause itself — users should not be able to add it to a group
            if (packageName == myPackageName) continue

            // Get the user-visible app name from the system.
            // loadLabel() returns the android:label attribute of the app.
            val appName = resolveInfo.loadLabel(packageManager).toString()

            appList.add(AppInfo(packageName = packageName, appName = appName))
        }

        // Sort alphabetically by app name (case-insensitive)
        return appList.sortedBy { it.appName.lowercase() }
    }

    /**
     * Search apps by name (case-insensitive substring match).
     *
     * @param query The search text. If empty, returns all launchable apps.
     */
    fun searchApps(query: String): List<AppInfo> {
        val allApps = getLaunchableApps()
        if (query.isBlank()) return allApps

        val lowerQuery = query.lowercase()
        return allApps.filter { app ->
            app.appName.lowercase().contains(lowerQuery) ||
                app.packageName.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Check if a specific package name is a launchable app.
     * Useful for validating that a stored packageName still exists on the device.
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            Log.w(TAG, "Error checking package $packageName: ${e.message}")
            false
        }
    }

    /**
     * Get the display name for a single package.
     * Returns null if the app is not installed or has no launcher entry.
     */
    fun getAppName(packageName: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
