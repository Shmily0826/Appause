package com.appause.android.service

import com.appause.android.service.PauseGuardPolicy.GuardAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the pause-guard watchdog's four outcomes and their exact boundaries.
 * The boundaries are load-bearing: they were tuned against real devices
 * (the "completely stops popping up" regression), so off-by-one changes
 * here are behavior changes.
 */
class PauseGuardPolicyTest {

    // ---------- Legitimate guard ----------

    @Test
    fun `attached overlay keeps the guard regardless of elapsed time`() {
        assertEquals(
            GuardAction.KEEP,
            PauseGuardPolicy.evaluate(
                elapsedMs = 10_000L,
                overlayAttached = true,
                pauseActivityVisible = false
            )
        )
    }

    @Test
    fun `visible PauseActivity keeps the guard regardless of elapsed time`() {
        assertEquals(
            GuardAction.KEEP,
            PauseGuardPolicy.evaluate(
                elapsedMs = 10_000L,
                overlayAttached = false,
                pauseActivityVisible = true
            )
        )
    }

    // ---------- Grace window (nothing on screen yet) ----------

    @Test
    fun `nothing on screen within the grace window keeps the guard`() {
        assertEquals(
            GuardAction.KEEP_WITHIN_GRACE,
            PauseGuardPolicy.evaluate(
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_GRACE_MS - 1,
                overlayAttached = false,
                pauseActivityVisible = false
            )
        )
    }

    @Test
    fun `grace boundary is exclusive - exactly the grace period is already stale`() {
        assertEquals(
            GuardAction.RELEASE_STALE,
            PauseGuardPolicy.evaluate(
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_GRACE_MS,
                overlayAttached = false,
                pauseActivityVisible = false
            )
        )
    }

    // ---------- Stale guard (nothing on screen, grace elapsed) ----------

    @Test
    fun `nothing on screen past the grace window releases the guard`() {
        assertEquals(
            GuardAction.RELEASE_STALE,
            PauseGuardPolicy.evaluate(
                elapsedMs = 5_000L,
                overlayAttached = false,
                pauseActivityVisible = false
            )
        )
    }

    // ---------- Max hold (attached-but-hidden window) ----------

    @Test
    fun `max hold releases even an attached overlay`() {
        assertEquals(
            GuardAction.RELEASE_MAX,
            PauseGuardPolicy.evaluate(
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_MAX_MS + 1,
                overlayAttached = true,
                pauseActivityVisible = false
            )
        )
    }

    @Test
    fun `max hold boundary is inclusive - exactly the cap is still held`() {
        assertEquals(
            GuardAction.KEEP,
            PauseGuardPolicy.evaluate(
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_MAX_MS,
                overlayAttached = true,
                pauseActivityVisible = false
            )
        )
    }

    @Test
    fun `max hold releases a hidden overlay even before the grace window would matter`() {
        // The anti-tamper scenario: a window is attached but was hidden.
        // The cap outranks both the grace window and window presence.
        assertEquals(
            GuardAction.RELEASE_MAX,
            PauseGuardPolicy.evaluate(
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_MAX_MS + 1,
                overlayAttached = true,
                pauseActivityVisible = true
            )
        )
    }
}
