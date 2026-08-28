package com.appause.android.interception

/**
 * BurstTracker — detects the OEM "app-switch replay" burst.
 *
 * This is a behavior-preserving extraction of the burst fingerprint that used
 * to live inline in AppauseAccessibilityService (recordWindowEvent /
 * isBurstSuppressed). It is pure logic with an injected clock, so the exact
 * historical regressions around it (v0.5.11–v0.5.24 threshold changes) can be
 * pinned by unit tests.
 *
 * WHY THIS EXISTS: the HyperOS "phantom pause screen" bug. On MIUI/HyperOS,
 * opening Recents or swiping the gesture bar replays a TYPE_WINDOW_STATE_CHANGED
 * event for every cached task inside the same ~3 ms:
 *
 *     12:52:52.705  com.xingin.xhs
 *     12:52:52.708  com.miui.home
 *     12:52:52.708  com.miui.personalassistant
 *
 * A genuine launch also fires several packages in the same few ms, but on
 * HyperOS those are the target app + the launcher + always-on system
 * components — i.e. only ONE real app. So we count only NON-system,
 * NON-launcher packages: ">= BURST_MIN_DISTINCT real apps within
 * BURST_WINDOW_MS" is a reliable, OEM-agnostic fingerprint of the phantom
 * burst — and it does NOT require Usage access.
 *
 * Threading: methods are synchronized to preserve the mutual exclusion the
 * inline code had (synchronized list / synchronized set / AtomicLong).
 */
class BurstTracker(
    private val burstWindowMs: Long = DEFAULT_BURST_WINDOW_MS,
    private val minDistinctRealPackages: Int = DEFAULT_BURST_MIN_DISTINCT,
    private val suppressDurationMs: Long = DEFAULT_BURST_SUPPRESS_MS
) {
    companion object {
        /** A window-state event cluster within this many ms counts as one burst. */
        const val DEFAULT_BURST_WINDOW_MS = 120L

        /**
         * Distinct REAL (user-installed, non-launcher) packages seen inside
         * BURST_WINDOW_MS that trigger burst mode (recents-replay suppression).
         *
         * v0.5.24: raised from 2 to 3. A GENUINE single-app open fires exactly ONE
         * real app (the target); if the user switches directly from another grouped
         * app, at most TWO real apps appear in the window. A true HyperOS "recents
         * replay" fires a BURST of window events for EVERY cached task — many real
         * apps in the same 2–3 ms — so >=3 cleanly separates the two. With the
         * threshold at 2, opening xhs while bilibili is ALSO in a group made the
         * buffer show 2 real apps and wrongly SKIPped a genuine launch (this was
         * the v0.5.11–v0.5.22 regression). The burst guard is now the ONLY gate
         * (v0.5.24 trusts the accessibility event directly and does not consult
         * the unreliable usage-event log), so it must be permissive enough to
         * never suppress a real open.
         */
        const val DEFAULT_BURST_MIN_DISTINCT = 3

        /** How long to keep suppressing interception after a burst is detected. */
        const val DEFAULT_BURST_SUPPRESS_MS = 1500L
    }

    /** Recent window-state events (event time, package), pruned to the burst window. */
    private val recentWindowPackages = mutableListOf<Pair<Long, String>>()

    /** While now < suppressUntilMs, the most recent cluster looked like an OEM replay. */
    private var suppressUntilMs = 0L

    /**
     * The REAL (non-noise) packages that made up the burst recorded above.
     *
     * We keep the actual packages — not just a flag — so the suppression can be
     * evaluated RELATIVE to the app we are about to intercept. A cluster only
     * proves a phantom app-switch if it contains a real app OTHER than the
     * candidate; a cluster consisting solely of the candidate is just a normal
     * launch. Without this, a target app that happens to ship in the system
     * image (OEM bloatware) could suppress its own interception.
     */
    private var burstRealPackages = setOf<String>()

    /**
     * Record a window-state event and update the burst-suppression flag.
     *
     * @param nowMs current time (injected so tests are deterministic).
     * @param isNoisePackage classifies a package as launcher / system-image
     *   noise. The service passes its PackageManager-backed classifier; tests
     *   pass a fake.
     */
    @Synchronized
    fun record(nowMs: Long, packageName: String, ownPackage: String, isNoisePackage: (String) -> Boolean) {
        recentWindowPackages.add(nowMs to packageName)
        // Drop entries older than the burst window so only a tight cluster counts.
        recentWindowPackages.removeAll { nowMs - it.first > burstWindowMs }
        // Only REAL apps count toward the burst fingerprint — a genuine launch
        // on HyperOS fires the target + launcher + assorted system components
        // in the same few ms; those must not inflate the count.
        val realDistinct = recentWindowPackages
            .map { it.second }
            .filter { !isNoisePackage(it) && it != ownPackage }
            .toSet()
        if (realDistinct.size >= minDistinctRealPackages) {
            suppressUntilMs = nowMs + suppressDurationMs
            burstRealPackages = realDistinct
        }
    }

    /**
     * True if [candidate] is being reported as foreground only because of a
     * recent OEM app-switch replay. The cluster must contain a real app OTHER
     * than the candidate — a burst made up solely of the candidate is an
     * ordinary launch, not a replay.
     */
    @Synchronized
    fun isSuppressed(nowMs: Long, candidate: String): Boolean {
        if (nowMs >= suppressUntilMs) return false
        return burstRealPackages.any { it != candidate }
    }

    /** Snapshot of the burst's real packages, for diagnostics logging. */
    @Synchronized
    fun realPackagesSnapshot(): Set<String> = burstRealPackages.toSet()
}
