package com.appause.android.ui.diagnostics

import android.content.Context
import android.os.PowerManager
import com.appause.android.AppauseApp
import com.appause.android.interception.InterceptionManager
import com.appause.android.service.AccessibilityServiceChecker
import com.appause.android.service.AppauseAccessibilityService
import com.appause.android.service.ForegroundChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Collects the full on-device diagnostic snapshot used by both the Diagnostics
 * screen and the Feedback screen, so the two never drift apart.
 *
 * IMPORTANT (privacy): this only reads the structured status signals the app
 * already shows on the Diagnostics screen. It does NOT read any free-text
 * production logs — [com.appause.android.util.PersistentLog] and the in-memory
 * ring buffer are debug-only and empty in release builds. So a feedback
 * submission never carries app-usage records or private content.
 */
suspend fun collectDiagnostics(context: Context): DiagnosticsState {
    val app = context.applicationContext as AppauseApp
    val repository = app.repository

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

    return DiagnosticsState(
        accessibilityEnabledInSettings = AccessibilityServiceChecker.isEnabled(context),
        serviceAlive = AppauseAccessibilityService.instance != null,
        connectedAt = AppauseAccessibilityService.connectedAt,
        masterEnabled = repository.isEnabled.first(),
        usageAccessGranted = ForegroundChecker.isUsageAccessGranted(context),
        overlayPermissionGranted = android.provider.Settings.canDrawOverlays(context),
        batteryExempted = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
        otherAppauseBuilds = AccessibilityServiceChecker.otherAppauseBuildsEnabled(context),
        eventCount = AppauseAccessibilityService.eventCount,
        lastEventPackage = AppauseAccessibilityService.lastEventPackage,
        lastEventAt = AppauseAccessibilityService.lastEventAt,
        lastDecision = AppauseAccessibilityService.lastDecision,
        lastTargetDecision = AppauseAccessibilityService.lastTargetDecision,
        overlayResult = AppauseAccessibilityService.lastOverlayResult,
        foregroundPackage = foreground,
        bypassed = InterceptionManager.bypassedSnapshot(),
        groups = groups
    )
}
