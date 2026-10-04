package com.appause.android.service

/**
 * LeaveWindowPolicy — pure predicate for the 3-min "leave window" wall-clock
 * reconcile.
 *
 * Why this exists (deep-sleep desync): the leave timer's coroutine delay()
 * runs on the main dispatcher's uptime clock, which FREEZES while the device
 * sleeps, while the deadline recorded for the UI (Home's
 * LeaveCooldownStatusCard) keeps counting wall-clock time. After a long sleep
 * the UI shows the window as expired, but the pending coroutine has not fired
 * yet — and a user returning to the target app takes the Resume branch, which
 * CANCELS the still-pending timer. The session then never re-arms and the app
 * is entered with no cooldown at all.
 *
 * The service consults this policy (1) on every foreground change for the
 * returning package, before the Resume decision, and (2) in the foreground
 * poller after a wake, for packages whose window expired while they were
 * away — so the UI's wall clock and the actual re-arm agree.
 */
internal object LeaveWindowPolicy {

    /**
     * True when the recorded wall-clock deadline has passed while the session
     * is still alive (bypass or foreground session), i.e. the timer missed its
     * fire and the service must re-arm eagerly.
     *
     * The boundary is `now >= deadline` to match the UI: the card computes
     * secondsLeft by ceiling and disappears exactly when now reaches deadline.
     */
    fun shouldReArmOnWallClock(
        deadlineMillis: Long?,
        nowMillis: Long,
        isBypassed: Boolean,
        isSessionActive: Boolean
    ): Boolean = deadlineMillis != null &&
        nowMillis >= deadlineMillis &&
        (isBypassed || isSessionActive)
}
