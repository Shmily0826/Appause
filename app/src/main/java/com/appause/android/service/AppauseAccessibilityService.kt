package com.appause.android.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.appause.android.AppauseApp
import com.appause.android.interception.InterceptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AppauseAccessibilityService — detects foreground app changes and triggers cooldowns.
 *
 * How it works:
 * 1. System sends TYPE_WINDOW_STATE_CHANGED events when Activities appear.
 * 2. We read the event's packageName to know which app is in the foreground.
 * 3. We filter out irrelevant events (Appause, launcher, system UI, duplicates).
 * 4. We check if the app belongs to any configured group (via Repository).
 * 5. If yes, we launch PauseActivity to show the cooldown screen.
 *
 * Limitations:
 * - Events fire for EVERY Activity transition across ALL apps.
 * - Some OEM ROMs may not include packageName or may kill the service.
 * - We handle null packageName and missing groups gracefully.
 *
 * IMPORTANT: This is an accessibility feature, NOT a monitoring tool.
 * We only read package names — we never read UI content or user data.
 */
class AppauseAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppauseA11yService"

        /** Whether the service is currently running. Used by UI to show status. */
        var isRunning: Boolean = false
            private set

        /**
         * Guard flag: true while the cooldown overlay is showing.
         * Prevents re-triggering interception while a cooldown is in progress.
         */
        var pauseShown: Boolean = false
    }

    /** Coroutine scope for async work (survives individual event cancellations). */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The overlay manager that shows the cooldown screen.
     * Uses TYPE_ACCESSIBILITY_OVERLAY to draw above all apps —
     * this works reliably on every OEM ROM (MIUI, ColorOS, etc.)
     * without needing the SYSTEM_ALERT_WINDOW permission.
     */
    private val overlayManager = OverlayManager()

    /**
     * Last seen foreground package. Used to skip duplicate events.
     * When the same package appears in consecutive events (Activity switch within
     * the same app), we skip it to avoid re-triggering the cooldown.
     */
    private var lastForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d(TAG, "AccessibilityService connected and running")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        Log.d(TAG, "Event received: package=$packageName, class=${event.className}")

        // Use a coroutine because we need to suspend for Repository queries.
        serviceScope.launch {
            try {
                handleForegroundChange(packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleForegroundChange for $packageName", e)
            }
        }
    }

    /**
     * Core interception logic. Called for every foreground app change.
     *
     * Decision flow:
     * 1. Is Appause disabled? → skip
     * 2. Is it Appause itself? → skip
     * 3. Is it a system package (launcher, system UI)? → skip
     * 4. Is it currently bypassed? → skip (user already completed cooldown)
     * 5. Is it the same as last foreground? → skip (Activity switch, not app switch)
     * 6. Does it belong to a configured group? → if yes, INTERCEPT
     * 7. Otherwise → skip (not a target app)
     */
    private suspend fun handleForegroundChange(packageName: String) {
        val app = applicationContext as AppauseApp
        val repository = app.repository

        // 1. Check if Appause is enabled
        val isEnabled = repository.isEnabled.first()
        if (!isEnabled) {
            Log.d(TAG, "SKIP: Appause is disabled")
            return
        }

        // 2. Skip Appause itself
        if (packageName == applicationContext.packageName) {
            Log.d(TAG, "SKIP: Appause itself")
            lastForegroundPackage = packageName
            return
        }

        // 3. Skip common system packages (launcher, settings, etc.)
        if (isSystemPackage(packageName)) {
            Log.d(TAG, "SKIP: system package ($packageName)")
            lastForegroundPackage = packageName
            return
        }

        // 4. Check bypass — if the app is bypassed, check if we should clean up
        if (InterceptionManager.isBypassed(packageName)) {
            Log.d(TAG, "SKIP: bypassed ($packageName)")
            lastForegroundPackage = packageName
            return
        }

        // 4.5. Skip if cooldown overlay is currently showing
        if (pauseShown) {
            Log.d(TAG, "SKIP: cooldown overlay is showing ($packageName)")
            // Still clean up bypass if the user left a bypassed app
            lastForegroundPackage?.let { last ->
                if (InterceptionManager.isBypassed(last) && last != packageName) {
                    Log.d(TAG, "Cleanup: clearing bypass for $last (user left the app)")
                    InterceptionManager.clearBypass(last)
                }
            }
            return
        }

        // The foreground changed to a non-bypassed, non-system app.
        // If the previous app WAS bypassed, the user left it → clean up.
        lastForegroundPackage?.let { last ->
            if (InterceptionManager.isBypassed(last)) {
                Log.d(TAG, "Cleanup: clearing bypass for $last (user left the app)")
                InterceptionManager.clearBypass(last)
            }
        }

        // 5. Skip duplicate events (same app, different Activity)
        if (packageName == lastForegroundPackage) {
            Log.d(TAG, "SKIP: duplicate event ($packageName)")
            return
        }

        lastForegroundPackage = packageName

        // 6. Check if this app belongs to any configured group
        val group = repository.findGroupForPackage(packageName)
        if (group == null) {
            Log.d(TAG, "SKIP: not in any group ($packageName)")
            return
        }

        // 7. Intercept! Show the cooldown overlay.
        Log.d(TAG, "INTERCEPT: $packageName → group=${group.name}, cooldown=${group.cooldownSeconds}s")
        showCooldownOverlay(packageName, group.id, group.cooldownSeconds)
    }

    /**
     * Check if a package is a system UI component we should always ignore.
     * Covers the launcher, system UI, settings, recents screen, and OEM launchers.
     */
    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.systemui") ||
            packageName.startsWith("com.android.launcher") ||
            packageName == "com.android.settings" ||
            packageName == "com.google.android.googlequicksearchbox" ||
            packageName == "com.android.permissioncontroller" ||
            packageName == "com.google.android.permissioncontroller" ||
            // OEM launchers
            packageName == "com.miui.home" ||              // Xiaomi
            packageName == "com.huawei.android.launcher" || // Huawei
            packageName == "com.sec.android.app.launcher" || // Samsung
            packageName == "com.oppo.launcher" ||           // OPPO
            packageName == "com.bbk.launcher2" ||           // Vivo
            packageName == "com.oneplus.launcher" ||        // OnePlus
            packageName == "com.motorola.launcher3"         // Motorola
    }

    /**
     * Show the cooldown overlay using TYPE_ACCESSIBILITY_OVERLAY.
     *
     * Why an overlay instead of launching an Activity?
     * - On MIUI (Xiaomi) and other OEM ROMs, startActivity() from a Service
     *   is silently blocked — the Activity never appears on screen.
     * - TYPE_ACCESSIBILITY_OVERLAY draws above ALL apps without needing
     *   the SYSTEM_ALERT_WINDOW permission. It works on every OEM ROM.
     * - The overlay captures all touches, so the user can only interact
     *   with the Cancel or Continue buttons.
     */
    private fun showCooldownOverlay(packageName: String, groupId: Long, cooldownSeconds: Int) {
        overlayManager.show(this, packageName, groupId, cooldownSeconds)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        // Clean up the overlay if it's showing when the service is destroyed
        if (overlayManager.isShowing) {
            overlayManager.dismiss()
        }

        Log.d(TAG, "AccessibilityService destroyed")
    }
}
