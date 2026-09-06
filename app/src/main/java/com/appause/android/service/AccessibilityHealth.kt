package com.appause.android.service

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Evidence obtained from the system Accessibility settings. */
enum class AccessibilitySystemState {
    ENABLED,
    DISABLED,
    UNKNOWN
}

/** Evidence available from this app process about its AccessibilityService. */
enum class AccessibilityProcessState {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN
}

/** Small, fail-closed health contract shared by user-facing status screens. */
enum class AccessibilityHealthStatus {
    HEALTHY,
    ACCESSIBILITY_NOT_ENABLED,
    SERVICE_NOT_CONNECTED,
    UNKNOWN
}

enum class AccessibilityRecoveryAction {
    NONE,
    OPEN_ACCESSIBILITY_SETTINGS,
    RECHECK_ON_RESUME
}

data class AccessibilityHealthState(
    val systemState: AccessibilitySystemState,
    val processState: AccessibilityProcessState,
    val status: AccessibilityHealthStatus
) {
    /** Only this state is safe to present as ready for interception. */
    val isHealthy: Boolean
        get() = status == AccessibilityHealthStatus.HEALTHY

    /** The UI must not let a non-healthy snapshot silently pass as ready. */
    val requiresAttention: Boolean
        get() = !isHealthy

    val recoveryAction: AccessibilityRecoveryAction
        get() = when (status) {
            AccessibilityHealthStatus.HEALTHY -> AccessibilityRecoveryAction.NONE
            AccessibilityHealthStatus.ACCESSIBILITY_NOT_ENABLED,
            AccessibilityHealthStatus.SERVICE_NOT_CONNECTED ->
                AccessibilityRecoveryAction.OPEN_ACCESSIBILITY_SETTINGS
            AccessibilityHealthStatus.UNKNOWN -> AccessibilityRecoveryAction.RECHECK_ON_RESUME
        }

    companion object {
        val UNKNOWN = AccessibilityHealthState(
            systemState = AccessibilitySystemState.UNKNOWN,
            processState = AccessibilityProcessState.UNKNOWN,
            status = AccessibilityHealthStatus.UNKNOWN
        )
    }
}

/** Pure policy for combining independently observed system and process facts. */
object AccessibilityHealthPolicy {
    fun derive(
        systemState: AccessibilitySystemState,
        processState: AccessibilityProcessState
    ): AccessibilityHealthState {
        val status = when {
            systemState == AccessibilitySystemState.UNKNOWN ->
                AccessibilityHealthStatus.UNKNOWN
            systemState == AccessibilitySystemState.DISABLED ->
                AccessibilityHealthStatus.ACCESSIBILITY_NOT_ENABLED
            processState == AccessibilityProcessState.CONNECTED ->
                AccessibilityHealthStatus.HEALTHY
            processState == AccessibilityProcessState.DISCONNECTED ->
                AccessibilityHealthStatus.SERVICE_NOT_CONNECTED
            else -> AccessibilityHealthStatus.UNKNOWN
        }
        return AccessibilityHealthState(systemState, processState, status)
    }

    /**
     * Recompute health whenever process evidence changes. The system setting is
     * deliberately supplied by the caller so resume checks can refresh it.
     */
    fun observe(
        processStates: Flow<AccessibilityProcessState>,
        systemState: () -> AccessibilitySystemState
    ): Flow<AccessibilityHealthState> = processStates
        .map { processState -> derive(systemState(), processState) }
        .distinctUntilChanged()
}

/** Runtime source of truth used by all app screens that show Accessibility health. */
object AccessibilityHealthChecker {
    fun snapshot(context: Context): AccessibilityHealthState =
        AccessibilityHealthPolicy.derive(
            systemState = AccessibilityServiceChecker.systemState(context),
            processState = AppauseAccessibilityService.currentProcessState()
        )

    fun observe(context: Context): Flow<AccessibilityHealthState> =
        AccessibilityHealthPolicy.observe(
            processStates = AppauseAccessibilityService.processState,
            systemState = { AccessibilityServiceChecker.systemState(context) }
        )
}
