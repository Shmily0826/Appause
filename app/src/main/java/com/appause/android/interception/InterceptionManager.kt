package com.appause.android.interception

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * InterceptionManager — manages bypass state for intercepted apps.
 *
 * What is "bypass"?
 * - After the user completes the cooldown and taps "Continue", we need to let them
 *   into the target app without triggering another interception.
 * - We "bypass" the app by adding it to a bypassed set.
 * - The AccessibilityService checks this set before intercepting.
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
 * - Uses a SupervisorJob scope on the Default dispatcher for timeout management.
 * - The bypassedPackages set is accessed from multiple threads (service + activity).
 * - For v1, we use a simple HashSet. A concurrent set would be safer for production.
 */
object InterceptionManager {

    private const val TAG = "InterceptionManager"

    /** Apps that are currently allowed through without interception. */
    private val bypassedPackages = mutableSetOf<String>()

    /** Timeout jobs for auto-cleaning bypass entries (5-minute safety net). */
    private val bypassTimeoutJobs = mutableMapOf<String, Job>()

    /** Coroutine scope for managing timeout jobs. Survives individual cancellations. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Check if a package should be bypassed (no interception).
     * Called by AccessibilityService on every foreground app change.
     */
    fun isBypassed(packageName: String): Boolean {
        return packageName in bypassedPackages
    }

    /**
     * Add a package to the bypass list with an auto-expiry timeout.
     * Called when the user taps "Continue" on the Pause Screen.
     *
     * @param packageName The app to bypass.
     * @param timeoutMs Auto-remove after this duration (default 5 minutes).
     *                  Safety net in case the cleanup logic doesn't trigger.
     */
    fun startBypass(packageName: String, timeoutMs: Long = 5 * 60 * 1000L) {
        bypassedPackages.add(packageName)
        Log.d(TAG, "Bypass started: $packageName")

        // Cancel any existing timeout for this package (shouldn't happen, but safe)
        bypassTimeoutJobs[packageName]?.cancel()

        // Auto-remove after timeout — safety net for edge cases
        val job = scope.launch {
            delay(timeoutMs)
            bypassedPackages.remove(packageName)
            bypassTimeoutJobs.remove(packageName)
            Log.d(TAG, "Bypass auto-expired: $packageName")
        }
        bypassTimeoutJobs[packageName] = job
    }

    /**
     * Remove a package from the bypass list and cancel its timeout.
     * Called when:
     * - The user leaves the target app (detected by AccessibilityService).
     * - The user cancels on the Pause Screen (cleanup).
     */
    fun clearBypass(packageName: String) {
        bypassedPackages.remove(packageName)
        bypassTimeoutJobs[packageName]?.cancel()
        bypassTimeoutJobs.remove(packageName)
        Log.d(TAG, "Bypass cleared: $packageName")
    }
}
