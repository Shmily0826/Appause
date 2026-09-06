package com.appause.android.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityHealthPolicyTest {

    @Test
    fun `enabled and connected is healthy`() {
        val state = AccessibilityHealthPolicy.derive(
            AccessibilitySystemState.ENABLED,
            AccessibilityProcessState.CONNECTED
        )

        assertEquals(AccessibilityHealthStatus.HEALTHY, state.status)
        assertTrue(state.isHealthy)
        assertEquals(AccessibilityRecoveryAction.NONE, state.recoveryAction)
    }

    @Test
    fun `enabled but unknown process is unverified and fail closed`() {
        val state = AccessibilityHealthPolicy.derive(
            AccessibilitySystemState.ENABLED,
            AccessibilityProcessState.UNKNOWN
        )

        assertEquals(AccessibilityHealthStatus.UNKNOWN, state.status)
        assertFalse(state.isHealthy)
        assertEquals(AccessibilityRecoveryAction.RECHECK_ON_RESUME, state.recoveryAction)
    }

    @Test
    fun `connected event upgrades enabled unknown without another resume`() = runTest {
        val processStates = MutableStateFlow(AccessibilityProcessState.UNKNOWN)
        val health = AccessibilityHealthPolicy.observe(
            processStates = processStates,
            systemState = { AccessibilitySystemState.ENABLED }
        )
        val first = health.first()
        assertEquals(AccessibilityHealthStatus.UNKNOWN, first.status)

        processStates.value = AccessibilityProcessState.CONNECTED

        assertEquals(AccessibilityHealthStatus.HEALTHY, health.first().status)
    }

    @Test
    fun `observed destroy moves healthy to service not connected`() = runTest {
        val processStates = MutableStateFlow(AccessibilityProcessState.CONNECTED)
        val health = AccessibilityHealthPolicy.observe(
            processStates = processStates,
            systemState = { AccessibilitySystemState.ENABLED }
        )
        assertEquals(AccessibilityHealthStatus.HEALTHY, health.first().status)

        processStates.value = AccessibilityProcessState.DISCONNECTED

        val recovered = health.first { it.status == AccessibilityHealthStatus.SERVICE_NOT_CONNECTED }
        assertEquals(AccessibilityRecoveryAction.OPEN_ACCESSIBILITY_SETTINGS, recovered.recoveryAction)
    }

    @Test
    fun `system disabled wins over connected process`() {
        val state = AccessibilityHealthPolicy.derive(
            AccessibilitySystemState.DISABLED,
            AccessibilityProcessState.CONNECTED
        )

        assertEquals(AccessibilityHealthStatus.ACCESSIBILITY_NOT_ENABLED, state.status)
        assertFalse(state.isHealthy)
    }

    @Test
    fun `unknown system state never becomes healthy`() {
        val state = AccessibilityHealthPolicy.derive(
            AccessibilitySystemState.UNKNOWN,
            AccessibilityProcessState.CONNECTED
        )

        assertEquals(AccessibilityHealthStatus.UNKNOWN, state.status)
        assertFalse(state.isHealthy)
    }
}
