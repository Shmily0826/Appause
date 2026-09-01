package com.appause.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
