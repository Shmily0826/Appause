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
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_MAX_MS + 1,
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

    // ---------- Max hold (2038 fallback can be attached-but-hidden) ----------

    @Test
    fun `attached overlay stays guarded past the old max hold`() {
        assertEquals(
            GuardAction.KEEP,
            PauseGuardPolicy.evaluate(
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_MAX_MS + 1,
                overlayAttached = true,
                pauseActivityVisible = false,
                overlayCanBeHiddenByTarget = false
            )
        )
    }

    @Test
    fun `hideable overlay is still held exactly at the max boundary`() {
        assertEquals(
            GuardAction.KEEP,
            PauseGuardPolicy.evaluate(
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_MAX_MS,
                overlayAttached = true,
                pauseActivityVisible = false,
                overlayCanBeHiddenByTarget = true
            )
        )
    }

    @Test
    fun `max hold releases a hideable 2038 overlay`() {
        assertEquals(
            GuardAction.RELEASE_MAX,
            PauseGuardPolicy.evaluate(
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_MAX_MS + 1,
                overlayAttached = true,
                pauseActivityVisible = false,
                overlayCanBeHiddenByTarget = true
            )
        )
    }

    @Test
    fun `visible PauseActivity outranks the 2038 max hold`() {
        assertEquals(
            GuardAction.KEEP,
            PauseGuardPolicy.evaluate(
                elapsedMs = PauseGuardPolicy.PAUSE_GUARD_MAX_MS + 1,
                overlayAttached = true,
                pauseActivityVisible = true,
                overlayCanBeHiddenByTarget = true
            )
        )
    }
}
