package com.appause.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure predicate tests for the leave-window wall-clock reconcile
 * (LeaveWindowPolicy.shouldReArmOnWallClock).
 */
class LeaveWindowPolicyTest {

    @Test
    fun `no deadline recorded means nothing to re-arm`() {
        assertFalse(
            LeaveWindowPolicy.shouldReArmOnWallClock(
                deadlineMillis = null,
                nowMillis = 10_000L,
                isBypassed = true,
                isSessionActive = true
            )
        )
    }

    @Test
    fun `deadline not reached keeps the session`() {
        assertFalse(
            LeaveWindowPolicy.shouldReArmOnWallClock(
                deadlineMillis = 10_000L,
                nowMillis = 9_999L,
                isBypassed = true,
                isSessionActive = false
            )
        )
    }

    @Test
    fun `deadline reached with a live bypass re-arms`() {
        // The UI card disappears exactly when now reaches the deadline, so the
        // reconcile must agree at the boundary (>=, not >).
        assertTrue(
            LeaveWindowPolicy.shouldReArmOnWallClock(
                deadlineMillis = 10_000L,
                nowMillis = 10_000L,
                isBypassed = true,
                isSessionActive = false
            )
        )
    }

    @Test
    fun `deadline reached with a live session re-arms even without bypass`() {
        assertTrue(
            LeaveWindowPolicy.shouldReArmOnWallClock(
                deadlineMillis = 10_000L,
                nowMillis = 10_001L,
                isBypassed = false,
                isSessionActive = true
            )
        )
    }

    @Test
    fun `deadline reached but session already gone does not re-arm`() {
        // The timer already fired (or the user cancelled) — nothing to clean up.
        assertFalse(
            LeaveWindowPolicy.shouldReArmOnWallClock(
                deadlineMillis = 10_000L,
                nowMillis = 10_001L,
                isBypassed = false,
                isSessionActive = false
            )
        )
    }
}
