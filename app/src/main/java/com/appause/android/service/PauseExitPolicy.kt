package com.appause.android.service

/** Only consult foreground evidence for the app the user explicitly left. */
internal object PauseExitPolicy {
    const val EVIDENCE_WINDOW_MS = 5_000L
    private const val NAVIGATION_SETTLE_MS = 250L
    private const val UNKNOWN_FOREGROUND_GRACE_MS = 800L

    fun shouldSuppress(
        exitedAtUptime: Long,
        eventUptime: Long,
        nowUptime: Long,
        targetPackage: String,
        currentForegroundPackage: String?,
        ownPackage: String
    ): Boolean {
        val age = nowUptime - exitedAtUptime
        if (age !in 0 until EVIDENCE_WINDOW_MS) return false
        // A queued event from before the tap cannot represent a new open.
        if (eventUptime <= exitedAtUptime) return true
        // Removing the overlay can expose the old app before Home/the new app resumes.
        if (age < NAVIGATION_SETTLE_MS) return true
        // The overlay itself can be reported as foreground during dismissal.
        if (currentForegroundPackage == null || currentForegroundPackage == ownPackage) {
            return age < UNKNOWN_FOREGROUND_GRACE_MS
        }
        return currentForegroundPackage != targetPackage
    }
}
