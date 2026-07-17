package com.appause.android.interception

import android.util.Log

/**
 * InterceptionManager — manages bypass state for intercepted apps.
 *
 * What is "bypass"?
 * - After the user completes the cooldown and taps "Continue", we need to let them
 *   into the target app without triggering another interception.
 * - We "bypass" the app by adding it to a bypassed set.
 * - The AccessibilityService checks this set before intercepting.
 *
 * When does bypass get cleared?
 * - When the user LEAVES the target app (detected by AccessibilityService).
 *   Next time they open the app, the cooldown triggers again.
 * - When the user taps Cancel on the Pause Screen.
 *
 * Why a singleton (object)?
 * - The AccessibilityService and PauseActivity are separate Android components.
 * - They need to share the bypass state, but can't pass data directly.
 * - A singleton object is the simplest way to share state across components
 *   in the same process.
 *
 * Lifecycle:
 * - Created once when the process starts (first access).
 * - Lost when the process is killed (acceptable — user just sees cooldown again).
 *
 * Thread safety:
 * - The bypassedPackages set is accessed from multiple threads (service + activity).
 * - For v1, we use a simple HashSet. A concurrent set would be safer for production.
 */
object InterceptionManager {

    private const val TAG = "InterceptionManager"

    /** Apps that are currently bypassed (user completed cooldown and is using the app). */
    private val bypassedPackages = mutableSetOf<String>()

    /**
     * Check if a package should be bypassed (no interception).
     * Called by AccessibilityService on every foreground app change.
     */
    fun isBypassed(packageName: String): Boolean {
        return packageName in bypassedPackages
    }

    /**
     * Add a package to the bypass list.
     * Called when the user taps "Continue" on the Pause Screen.
     *
     * The bypass lasts until the user leaves the target app.
     * The AccessibilityService detects this and calls clearBypass().
     */
    fun startBypass(packageName: String) {
        bypassedPackages.add(packageName)
        Log.d(TAG, "Bypass started: $packageName")
    }

    /**
     * Remove a package from the bypass list.
     * Called when:
     * - The user leaves the target app (detected by AccessibilityService).
     * - The user cancels on the Pause Screen (cleanup).
     *
     * After clearing, next time the user opens this app, the cooldown triggers again.
     */
    fun clearBypass(packageName: String) {
        bypassedPackages.remove(packageName)
        Log.d(TAG, "Bypass cleared: $packageName")
    }
}
