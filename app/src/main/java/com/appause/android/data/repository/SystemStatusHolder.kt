package com.appause.android.data.repository

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.appause.android.service.AccessibilityHealthChecker
import com.appause.android.service.AccessibilityHealthState
import com.appause.android.service.ForegroundChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide holder for the permission/service status that several screens
 * show: Accessibility health, display-over-other-apps, usage access, and
 * battery-optimization exemption.
 *
 * Previously each ViewModel (Home, Onboarding, Settings) kept its own copy of
 * these four StateFlows plus identical observe/refresh code — about 60 lines
 * times three — so any change to a status check had to be made in three places
 * and the copies were starting to drift. All screens now read this holder.
 *
 * The Accessibility flow is observed continuously (service connect/disconnect
 * events recompute it), exactly like the old per-ViewModel init blocks. The
 * other three are snapshots; screens call [refresh] when they become visible,
 * because the user grants permissions in *system* settings and returns with no
 * reliable lifecycle event Appause can hook.
 *
 * Lives as a lazy singleton on [com.appause.android.AppauseApp], so the health
 * flow has exactly one process-level collector and every screen sees the same
 * values.
 */
class SystemStatusHolder(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    private val appContext = context.applicationContext

    private val _accessibilityHealth = MutableStateFlow(AccessibilityHealthState.UNKNOWN)
    val accessibilityHealth: StateFlow<AccessibilityHealthState> = _accessibilityHealth.asStateFlow()

    private val _canDrawOverlays = MutableStateFlow(false)
    val canDrawOverlays: StateFlow<Boolean> = _canDrawOverlays.asStateFlow()

    private val _isUsageAccessGranted = MutableStateFlow(false)
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted.asStateFlow()

    private val _isIgnoringBattery = MutableStateFlow(false)
    val isIgnoringBattery: StateFlow<Boolean> = _isIgnoringBattery.asStateFlow()

    init {
        scope.launch {
            AccessibilityHealthChecker.observe(appContext).collect {
                _accessibilityHealth.value = it
            }
        }
        // Initialise immediately so a screen does not flash "unverified" or
        // red before its first ON_RESUME refresh.
        refresh()
    }

    /** Re-read every snapshot status. Call when a screen becomes visible. */
    fun refresh() {
        val app = appContext
        _accessibilityHealth.value = AccessibilityHealthChecker.snapshot(app)
        _canDrawOverlays.value = Settings.canDrawOverlays(app)
        _isUsageAccessGranted.value = ForegroundChecker.isUsageAccessGranted(app)
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isIgnoringBattery.value = pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false
    }

}
