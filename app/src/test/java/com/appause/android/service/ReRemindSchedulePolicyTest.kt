package com.appause.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReRemindSchedulePolicyTest {

    private val targetPackage = "com.example.bilibili"

    @Test
    fun `non-positive interval does not schedule`() {
        assertNull(
            request(minutes = 0, proStatus = ReRemindProStatus.UNLOCKED)
        )
        assertNull(
            request(minutes = -1, proStatus = ReRemindProStatus.UNLOCKED)
        )
    }

    @Test
    fun `locked Pro status does not schedule`() {
        assertNull(
            request(minutes = 1, proStatus = ReRemindProStatus.LOCKED)
        )
    }

    @Test
    fun `failed Pro lookup fails closed without scheduling`() {
        assertNull(
            request(minutes = 1, proStatus = ReRemindProStatus.UNKNOWN)
        )
    }

    @Test
    fun `unlocked Pro status creates request with all scheduler arguments`() {
        val scheduled = request(
            minutes = 7,
            reRemindCooldownSeconds = 12,
            repeat = false,
            escalate = true,
            proStatus = ReRemindProStatus.UNLOCKED
        )

        assertEquals(
            ReRemindScheduleRequest(
                targetPackage = targetPackage,
                groupId = 42L,
                cooldownSeconds = 10,
                minutes = 7,
                reRemindCooldownSeconds = 12,
                repeat = false,
                escalate = true
            ),
            scheduled
        )
    }

    @Test
    fun `repeat keeps later reminders eligible after each Continue while session is alive`() {
        assertTrue(
            ReRemindLifecyclePolicy.shouldContinueAfterPop(
                sessionActive = true,
                repeat = true,
                remindCount = 1
            )
        )
        assertTrue(
            ReRemindLifecyclePolicy.shouldContinueAfterPop(
                sessionActive = true,
                repeat = true,
                remindCount = 2
            )
        )
    }

    @Test
    fun `repeat off stops after the first re-remind and ended session stops any mode`() {
        assertFalse(
            ReRemindLifecyclePolicy.shouldContinueAfterPop(
                sessionActive = true,
                repeat = false,
                remindCount = 1
            )
        )
        assertFalse(
            ReRemindLifecyclePolicy.shouldContinueAfterPop(
                sessionActive = false,
                repeat = true,
                remindCount = 1
            )
        )
    }

    @Test
    fun `session liveness survives bypass clearing until explicit session end`() {
        val state = SessionState()
        assertTrue(state.begin(targetPackage, preserveForegroundSession = true))

        // Temporary Pass clears the runtime bypass, but does not end this session.
        assertTrue(
            ReRemindLifecyclePolicy.shouldContinueAfterPop(
                sessionActive = state.isForegroundActive(targetPackage),
                repeat = true,
                remindCount = 1
            )
        )

        state.end(targetPackage)
        assertFalse(state.isForegroundActive(targetPackage))
    }

    private fun request(
        minutes: Int,
        reRemindCooldownSeconds: Int = 0,
        repeat: Boolean = true,
        escalate: Boolean = false,
        proStatus: ReRemindProStatus
    ): ReRemindScheduleRequest? = ReRemindSchedulePolicy.request(
        targetPackage = targetPackage,
        groupId = 42L,
        cooldownSeconds = 10,
        minutes = minutes,
        reRemindCooldownSeconds = reRemindCooldownSeconds,
        repeat = repeat,
        escalate = escalate,
        proStatus = proStatus
    )
}
