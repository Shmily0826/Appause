package com.appause.android.service

/**
 * Small in-memory lifecycle state for one service process.
 *
 * The started marker prevents duplicate re-remind loops. The foreground marker
 * is only for an explicit Continue session; Temporary Pass must not retain it
 * past its persisted expiry.
 */
internal class SessionState {

    private val startedPackages = mutableSetOf<String>()
    private val foregroundPackages = mutableSetOf<String>()

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
