package com.appause.android.ui.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppGroup
import com.appause.android.interception.InterceptionManager
import com.appause.android.service.AccessibilityServiceChecker
import com.appause.android.service.AppauseAccessibilityService
import com.appause.android.service.ForegroundChecker
import com.appause.android.util.PersistentLog
import android.content.ComponentName
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One configured group, flattened for display on the Diagnostics screen. */
data class GroupDiag(
    val name: String,
    val type: String,
    val cooldownSeconds: Int,
    val packages: List<String>
) {
    /** Learning groups are recommendations — they never trigger a cooldown. */
    val intercepts: Boolean get() = type != AppGroup.TYPE_LEARNING
}

/** Everything the Diagnostics screen needs, refreshed on a timer. */
data class DiagnosticsState(
    val accessibilityEnabledInSettings: Boolean = false,
    val serviceAlive: Boolean = false,
    val connectedAt: Long = 0L,
    val masterEnabled: Boolean = true,
    val usageAccessGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val eventCount: Long = 0L,
    val lastEventPackage: String? = null,
    val lastEventAt: Long = 0L,
    val lastDecision: String? = null,
    val overlayResult: String? = null,
    val foregroundPackage: String? = null,
    val bypassed: Set<String> = emptySet(),
    val groups: List<GroupDiag> = emptyList(),
    val persistentLog: String = "",
    val forceStartResult: String? = null
) {
    /** Groups that can actually intercept something (pause type, at least 1 app). */
    val activeGroups: List<GroupDiag> get() = groups.filter { it.intercepts && it.packages.isNotEmpty() }

    /**
     * The single most likely reason interception is not happening, evaluated in
     * the same order the service itself checks them. Null = everything looks OK.
     */
    val verdict: Verdict
        get() = when {
            !accessibilityEnabledInSettings -> Verdict.NO_PERMISSION
            !serviceAlive -> Verdict.SERVICE_DEAD
            !masterEnabled -> Verdict.MASTER_OFF
            activeGroups.isEmpty() -> Verdict.NO_GROUPS
            eventCount == 0L -> Verdict.NO_EVENTS
            else -> Verdict.OK
        }

    enum class Verdict { NO_PERMISSION, SERVICE_DEAD, MASTER_OFF, NO_GROUPS, NO_EVENTS, OK }
}

/**
 * ViewModel for the debug-only Diagnostics screen.
 *
 * It answers, on the phone itself, the question "why was my app not
 * intercepted?" by surfacing every gate the AccessibilityService checks:
 * permission → service alive → master switch → group membership → events.
 */
class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AppauseApp
    private val repository = app.repository

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-read every signal. Called on entry and by the 1-second UI ticker. */
    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()

            val groups = repository.observeAllGroups().first().map { group ->
                GroupDiag(
                    name = group.name,
                    type = group.type,
                    cooldownSeconds = group.cooldownSeconds,
                    packages = repository.getPackageNamesInGroup(group.id)
                )
            }

            // UsageStats queries hit the system service — keep them off the main thread.
            val foreground = withContext(Dispatchers.IO) {
                ForegroundChecker.getForegroundPackage(context)
            }

            val persistentLog = withContext(Dispatchers.IO) {
                PersistentLog.read(context)
            }

            _state.value = _state.value.copy(
                accessibilityEnabledInSettings = AccessibilityServiceChecker.isEnabled(context),
                serviceAlive = AppauseAccessibilityService.instance != null,
                connectedAt = AppauseAccessibilityService.connectedAt,
                masterEnabled = repository.isEnabled.first(),
                usageAccessGranted = ForegroundChecker.isUsageAccessGranted(context),
                overlayPermissionGranted = android.provider.Settings.canDrawOverlays(context),
                eventCount = AppauseAccessibilityService.eventCount,
                lastEventPackage = AppauseAccessibilityService.lastEventPackage,
                lastEventAt = AppauseAccessibilityService.lastEventAt,
                lastDecision = AppauseAccessibilityService.lastDecision,
                overlayResult = AppauseAccessibilityService.lastOverlayResult,
                foregroundPackage = foreground,
                bypassed = InterceptionManager.bypassedSnapshot(),
                groups = groups,
                persistentLog = persistentLog
            )
        }
    }

    /**
     * Attempt to start the AccessibilityService explicitly.
     *
     * Normally the system binds the service automatically when the user toggles
     * it on in Accessibility settings. On some OEM ROMs (notably MIUI) the
     * binding is silently skipped, so this button lets us test whether an
     * explicit start helps and records the result in the persistent log.
     */
    fun forceStartService() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PersistentLog.log(context, "Diag", "forceStartService clicked")
                val component = ComponentName(context, AppauseAccessibilityService::class.java)

                // Strategy 1: startService (creates the service process).
                val startIntent = Intent().apply {
                    setComponent(component)
                    action = "android.accessibilityservice.AccessibilityService"
                }
                val startResult = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
                PersistentLog.log(context, "Diag", "startForegroundService result=$startResult")

                // Strategy 2: request the system bind it by toggling the component.
                // This is a no-op visibility change, but it can prompt the system
                // to re-evaluate the enabled service list on some ROMs.
                context.packageManager.setComponentEnabledSetting(
                    component,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                PersistentLog.log(context, "Diag", "setComponentEnabledSetting DONE")

                _state.value = _state.value.copy(
                    forceStartResult = "已发送启动请求，请等 3–5 秒后点刷新。",
                    persistentLog = PersistentLog.read(context)
                )
            } catch (e: Exception) {
                PersistentLog.log(context, "Diag", "forceStartService ERROR: ${e.javaClass.simpleName}: ${e.message}")
                _state.value = _state.value.copy(
                    forceStartResult = "启动失败: ${e.javaClass.simpleName}: ${e.message}",
                    persistentLog = PersistentLog.read(context)
                )
            }
        }
    }

    fun clearForceStartResult() {
        _state.value = _state.value.copy(forceStartResult = null)
    }
}
