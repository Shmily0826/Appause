package com.appause.android.service

/**
 * Pure decision core of the pause-guard watchdog.
 *
 * `AppauseAccessibilityService.pauseShown` is not a plain boolean: while the
 * guard is raised, every read checks it against reality, because two real
 * bugs left the guard stuck true forever and silently swallowed every later
 * interception ("SKIP: cooldown overlay is showing"):
 *  1. The fallback PauseActivity never became visible (or was buried), so
 *     neither the overlay nor the Activity could clear the flag. A short
 *     grace window covers the async Activity launch; past it, a guard with
 *     nothing on screen is stale.
 *  2. An anti-tamper app (e.g. 小红书) hid the attached overlay via
 *     setHideOverlayWindows, so a window WAS attached but never visible.
 *     The hard cap releases even an attached guard.
 *
 * Extracted as a pure function so the four outcomes (and their exact
 * boundaries) can be unit-tested without a clock or a WindowManager.
 */
internal object PauseGuardPolicy {

    /**
     * How long the guard may stay raised while nothing is actually on screen.
     * Covers the fallback path, where PauseActivity is launched asynchronously
     * (direct startActivity, then an AlarmManager retry ~250ms later) and may
     * need a moment to become visible.
     */
    const val PAUSE_GUARD_GRACE_MS = 1_500L

    /**
     * Hard cap: never let the guard stick longer than this, even if a window
     * is "attached but not actually visible". A genuinely visible overlay
     * returns [GuardAction.KEEP] long before this cap, so the cap only ever
     * releases a stuck/dead guard.
     */
    const val PAUSE_GUARD_MAX_MS = 30_000L

    enum class GuardAction {
        /** A window is genuinely on screen — the guard is legitimate. */
        KEEP,

        /** Nothing on screen yet, but the fallback launch may still land. */
        KEEP_WITHIN_GRACE,

        /** Nothing on screen and the grace window has elapsed — release. */
        RELEASE_STALE,

        /** The guard has exceeded its max hold — release unconditionally. */
        RELEASE_MAX
    }

    /**
     * Decide what a guard read should do, given [elapsedMs] since the guard
     * was raised and the two window-presence signals. Boundaries match the
     * historical inline logic exactly: the max-hold check is `>` (so exactly
     * 30_000 ms is still held) and the grace check is `<` (so exactly
     * 1_500 ms with nothing on screen is already stale).
     */
    fun evaluate(
        elapsedMs: Long,
        overlayAttached: Boolean,
        pauseActivityVisible: Boolean,
        graceMs: Long = PAUSE_GUARD_GRACE_MS,
        maxMs: Long = PAUSE_GUARD_MAX_MS
    ): GuardAction = when {
        elapsedMs > maxMs -> GuardAction.RELEASE_MAX
        overlayAttached || pauseActivityVisible -> GuardAction.KEEP
        elapsedMs < graceMs -> GuardAction.KEEP_WITHIN_GRACE
        else -> GuardAction.RELEASE_STALE
    }
}
