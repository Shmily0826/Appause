package com.appause.android.service

/**
 * Small in-memory lifecycle state for one service process.
 *
 * The started marker prevents duplicate re-remind loops. The foreground marker
 * is only for an explicit Continue session; Temporary Pass must not retain it
 * past its persisted expiry.
 *
 * Concurrent sets: these are touched from the service main thread, overlay
 * callbacks on Dispatchers.IO, and PauseActivity, so plain HashSets would be
 * unsafe under the same cross-thread access as InterceptionManager.
 */
internal class SessionState {

    private val startedPackages: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val foregroundPackages: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun begin(packageName: String, preserveForegroundSession: Boolean): Boolean {
        if (!preserveForegroundSession) return true
        if (!startedPackages.add(packageName)) return false
        foregroundPackages.add(packageName)
        return true
    }

    fun isForegroundActive(packageName: String): Boolean = packageName in foregroundPackages

    fun end(packageName: String) {
        startedPackages.remove(packageName)
        foregroundPackages.remove(packageName)
    }
}
