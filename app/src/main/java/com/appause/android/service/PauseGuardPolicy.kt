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
 *  2. An anti-tamper app could hide a TYPE_APPLICATION_OVERLAY (2038) via
 *     setHideOverlayWindows, so a window WAS attached but never visible.
 *     The hard cap remains only for that fallback overlay type.
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
     * Hard cap for the legacy 2038 fallback, which target apps may hide while
     * WindowManager still reports it attached. The primary 2032 overlay and a
     * visible PauseActivity are real presentation signals and must outlive it.
     */
    const val PAUSE_GUARD_MAX_MS = 30_000L

    enum class GuardAction {
        /** A window is genuinely on screen — the guard is legitimate. */
        KEEP,

        /** Nothing on screen yet, but the fallback launch may still land. */
        KEEP_WITHIN_GRACE,

        /** Nothing on screen and the grace window has elapsed — release. */
        RELEASE_STALE,

        /** A hideable 2038 fallback has exceeded its max hold — release. */
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
        overlayCanBeHiddenByTarget: Boolean = false,
        graceMs: Long = PAUSE_GUARD_GRACE_MS,
        maxMs: Long = PAUSE_GUARD_MAX_MS
    ): GuardAction = when {
        pauseActivityVisible -> GuardAction.KEEP
        overlayAttached && !overlayCanBeHiddenByTarget -> GuardAction.KEEP
        elapsedMs > maxMs -> GuardAction.RELEASE_MAX
        overlayAttached -> GuardAction.KEEP
        elapsedMs < graceMs -> GuardAction.KEEP_WITHIN_GRACE
        else -> GuardAction.RELEASE_STALE
    }
}
