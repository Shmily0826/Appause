package com.appause.android.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import java.util.Locale
import com.appause.android.util.AppLogger
import com.appause.android.util.PersistentLog
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.appause.android.AppauseApp
import com.appause.android.R
import com.appause.android.data.query.AppInfo
import com.appause.android.data.query.AppQueryService
import com.appause.android.interception.InterceptionManager
import com.appause.android.ui.pause.PauseScreenContent
import com.appause.android.ui.pause.rememberCountdownState
import com.appause.android.ui.theme.AppauseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OverlayManager — shows the cooldown screen as a system overlay.
 *
 * Why a WindowManager overlay instead of launching an Activity?
 * - On MIUI (Xiaomi) and some other OEM ROMs, startActivity() from an
 *   AccessibilityService is silently blocked or deprioritized.
 * - The Activity gets created but doesn't come to the foreground,
 *   so the user never sees the cooldown screen.
 * - TYPE_ACCESSIBILITY_OVERLAY is a special window type available to
 *   AccessibilityServices. It draws above ALL apps without needing
 *   the SYSTEM_ALERT_WINDOW permission.
 * - This works reliably on every OEM ROM (MIUI, ColorOS, OneUI, etc.)
 *   because it's an accessibility feature, not a background Activity launch.
 *
 * How it works:
 * 1. Create a ComposeView with the PauseScreenContent.
 * 2. Add it to WindowManager with TYPE_ACCESSIBILITY_OVERLAY.
 * 3. The overlay captures all touches — the user can only interact
 *    with the Cancel or Continue buttons.
 * 4. When the user dismisses (or the timer finishes), remove the overlay.
 *
 * The overlay uses a LifecycleContainer to provide Compose with a proper
 * lifecycle (needed for LaunchedEffect timers and remember{} state).
 */
@SuppressLint("ClickableViewAccessibility")
class OverlayManager {

    companion object {
        private const val TAG = "OverlayManager"
    }

    /** The overlay view — null when no overlay is showing. */
    private var overlayView: ComposeView? = null

    /** Coroutine scope for the countdown timer — cancelled on dismiss. */
    private var overlayScope: CoroutineScope? = null

    /**
     * Show the cooldown overlay for the given target app.
     *
     * @param service The AccessibilityService context (needed for WindowManager).
     * @param targetPackage Package name of the app the user tried to open.
     * @param groupId ID of the group this app belongs to (for logging).
     * @param cooldownSeconds How long the countdown should last.
     * @param reRemindMinutes If > 0, the initial entry starts a re-remind loop.
     * @param reRemindCooldownSeconds Dedicated cooldown length for re-remind pops.
     *                   If 0, the re-remind pause reuses [cooldownSeconds].
     * @param isReRemind True when this overlay is a re-remind pop (re-bypass only,
     *                   no new session/loop).
     */
    fun show(
        service: AppauseAccessibilityService,
        targetPackage: String,
        groupId: Long,
        cooldownSeconds: Int,
        reRemindMinutes: Int = 0,
        reRemindCooldownSeconds: Int = 0,
        isReRemind: Boolean = false,
        reRemindRepeat: Boolean = true,
        reRemindEscalate: Boolean = false
    ) {
        // Prevent duplicate overlays
        if (overlayView != null) {
            AppLogger.d(TAG, "Overlay already showing, skipping")
            return
        }

        // Use service context (not applicationContext) for proper theme attributes.
        // AccessibilityService extends Service, which is a valid ContextWrapper
        // with the app's theme applied. applicationContext may lack theme attrs
        // that Compose/Material3 needs for rendering.
        // Apply the in-app locale override (same logic as MainActivity /
        // PauseActivity) here: the AccessibilityService does NOT override locale
        // itself, and a language switch restarts only the Activities — not this
        // service — so without wrapping, the overlay would keep rendering the
        // language that was active when the service was first created. Wrapping
        // makes every overlay appearance read the current language from
        // SharedPreferences, so the interception screen stays consistent with
        // the rest of the app.
        val context = applyAppLocaleOverride(service)

        // Load target app info from PackageManager (icon + display name).
        val pm = context.packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(targetPackage, 0)).toString()
        } catch (e: Exception) {
            targetPackage
        }
        val appIcon: Drawable? = try {
            pm.getApplicationIcon(targetPackage)
        } catch (e: Exception) {
            null
        }

        // Get repository and default prompt
        val repository = (context.applicationContext as AppauseApp).repository
        val defaultPromptText = context.resources.getString(R.string.default_prompt)

        // Create WindowManager from service context
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Create the Compose view that will host our UI.
        // fitsSystemWindows = false prevents the view from adding padding
        // for system bars (status bar, nav bar). Without this, Compose's
        // Surface adds top padding that leaves a gap behind the status bar.
        val composeView = ComposeView(context).apply {
            fitsSystemWindows = false
        }

        // Set up window layout parameters.
        // Window type selection is critical and ROM-dependent:
        // - TYPE_APPLICATION_OVERLAY: needs SYSTEM_ALERT_WINDOW ("显示悬浮窗")
        //   but draws ABOVE all app tasks — apps like 小红书 that re-front
        //   themselves cannot cover it. This is the reliable type on OEM ROMs
        //   (HyperOS/MIUI) where TYPE_ACCESSIBILITY_OVERLAY is rejected.
        // - TYPE_ACCESSIBILITY_OVERLAY: needs no extra permission and works on
        //   stock ROMs, but throws BadTokenException on HyperOS/Android 16.
        // We prefer TYPE_APPLICATION_OVERLAY when the user has granted the
        // permission, and fall back to TYPE_ACCESSIBILITY_OVERLAY otherwise.
        // If the chosen type fails at addView, the catch below tries the
        // alternate type before giving up to a full-screen Activity (which some
        // apps can cover by re-fronting).
        val canDrawOverlays = android.provider.Settings.canDrawOverlays(context)
        val preferredType = if (canDrawOverlays)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        // - MATCH_PARENT: covers the entire screen
        // - FLAG_LAYOUT_IN_SCREEN: positions the window across the full screen
        // - FLAG_LAYOUT_NO_LIMITS: extends the window behind status bar and
        //   navigation bar, eliminating the white gap at the top of the screen
        // - FLAG_NOT_FOCUSABLE: allows the window to receive touch events
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            preferredType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        // Set up lifecycle for Compose (needed for LaunchedEffect timers).
        val lifecycleContainer = LifecycleContainer()
        composeView.setViewTreeLifecycleOwner(lifecycleContainer)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleContainer)

        // Start the lifecycle — this enables LaunchedEffect to run
        lifecycleContainer.create()
        lifecycleContainer.start()
        lifecycleContainer.resume()

        // Build the Compose UI
        // Query service for resolving recommended app names.
        val appQueryService = AppQueryService(context.applicationContext)

        composeView.setContent {
            AppauseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Load default prompt from DataStore settings
                    var prompt by remember { mutableStateOf(defaultPromptText) }
                    LaunchedEffect(Unit) {
                        repository.defaultPrompt.collect { stored ->
                            prompt = if (stored.isBlank()) defaultPromptText else stored
                        }
                    }

                    // Custom open-reason labels (Pro). Blank entries fall back to
                    // their localized default string resource.
                    val reasonKeys = listOf("work", "bored", "messages", "other")
                    val reasonDefs = listOf(
                        R.string.intent_work, R.string.intent_bored,
                        R.string.intent_messages, R.string.intent_other
                    )
                    var reasons by remember {
                        mutableStateOf(
                            reasonKeys.mapIndexed { i, k -> k to context.resources.getString(reasonDefs[i]) }
                        )
                    }
                    LaunchedEffect(Unit) {
                        repository.reasons.collect { custom ->
                            reasons = reasonKeys.mapIndexed { i, k ->
                                k to (if (custom[i].isBlank()) context.resources.getString(reasonDefs[i]) else custom[i])
                            }
                        }
                    }

                    // Recommended apps — the global list the user configured,
                    // shown as "try one of these instead" suggestions.
                    // Excludes the target app itself.
                    var recommendedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
                    LaunchedEffect(Unit) {
                        recommendedApps = withContext(Dispatchers.IO) {
                            repository.recommendedApps.first()
                                .filter { it != targetPackage }
                                .mapNotNull { pkg ->
                                    appQueryService.getAppName(pkg)?.let { name ->
                                        AppInfo(packageName = pkg, appName = name)
                                    }
                                }
                        }
                    }

                    // Countdown state — shared helper provides smooth progress (~60fps)
                    val countdown = rememberCountdownState(cooldownSeconds) {
                        // Timer finished → enter the app.
                        // Initial entry records the session + starts the re-remind
                        // loop; a re-remind pop just re-bypasses (the loop is already
                        // running and will re-fire on its own).
                        if (isReRemind) {
                            InterceptionManager.startBypass(targetPackage)
                        } else {
                            service.onSessionStart(targetPackage, groupId, cooldownSeconds, reRemindMinutes, reRemindCooldownSeconds, reRemindRepeat, reRemindEscalate)
                        }
                    }

                    PauseScreenContent(
                        appName = appName,
                        appIcon = appIcon,
                        prompt = prompt,
                        secondsLeft = countdown.secondsLeft,
                        smoothProgress = countdown.smoothProgress,
                        totalSeconds = cooldownSeconds,
                        isFinished = countdown.isFinished,
                        reasons = reasons,
                        onCancel = {
                            // Log the cancellation
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.logLaunch(targetPackage, groupId, "cancelled")
                            }
                            // Let the re-remind loop end its wait (next interval won't start).
                            if (isReRemind) service.completeReRemindContinue(targetPackage)
                            InterceptionManager.clearBypass(targetPackage)
                            // Suppress the stale window event that fires for the target
                            // app right before the launcher takes over — otherwise the
                            // overlay re-appears on the home screen after Cancel.
                            // Must be set BEFORE dismiss() (which resets pauseShown).
                            // noteCancelled() also auto-clears after a short grace
                            // window so the app is intercepted again on the next open.
                            AppauseAccessibilityService.noteCancelled(targetPackage)
                            dismiss()
                            // Send user to home screen so they don't land on the target app.
                            // Same behavior as PauseActivity.handleCancel().
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(homeIntent)
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "Failed to send to home", e)
                            }
                        },
                        onContinueWithReason = { reason ->
                            // Log the successful proceed with the selected reason
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.logLaunch(targetPackage, groupId, "proceeded", reason)
                            }
                            // Anchor the next re-remind interval to this Continue tap.
                            if (isReRemind) service.completeReRemindContinue(targetPackage)
                            dismiss()
                            // Re-remind is handled by the self-rescheduling loop started
                            // in onSessionStart (initial entry). For a re-remind pop the
                            // loop is already running, so nothing extra to schedule here.
                        },
                        recommendedApps = recommendedApps,
                        onOpenRecommendedApp = { pkg ->
                            // Open the recommended (learning) app instead of the target.
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                            if (launchIntent != null) {
                                InterceptionManager.clearBypass(targetPackage)
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    context.startActivity(launchIntent)
                                } catch (e: Exception) {
                                    AppLogger.w(TAG, "Failed to open recommended app", e)
                                }
                            }
                            dismiss()
                        }
                    )
                }
            }
        }

        // Add the overlay to the screen. Try the preferred window type first;
        // if it fails, try the alternate overlay type before falling back to a
        // full-screen Activity (which some apps can cover by re-fronting).
        var overlayAdded = false
        var usedType = preferredType
        try {
            windowManager.addView(composeView, params)
            overlayAdded = true
        } catch (e: Exception) {
            AppLogger.w(TAG, "addView failed with type $usedType: ${e.javaClass.simpleName}: ${e.message}")
            PersistentLog.log(context, "Overlay", "addView FAILED ($usedType): ${e.javaClass.simpleName}: ${e.message}")
            // Try the alternate overlay type before giving up to an Activity.
            val altType = if (preferredType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            try {
                params.type = altType
                usedType = altType
                windowManager.addView(composeView, params)
                overlayAdded = true
                AppLogger.d(TAG, "Overlay added with alternate type $altType")
                PersistentLog.log(context, "Overlay", "addView OK with alternate type $altType")
            } catch (e2: Exception) {
                AppLogger.w(TAG, "addView also failed with alternate type $altType: ${e2.javaClass.simpleName}: ${e2.message}")
                PersistentLog.log(context, "Overlay", "addView FAILED ($altType): ${e2.javaClass.simpleName}: ${e2.message}")
            }
        }

        if (overlayAdded) {
            overlayView = composeView
            // Create a coroutine scope for this overlay's lifetime
            overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            // Mark that the pause screen is showing (prevents re-triggering)
            AppauseAccessibilityService.pauseShown = true
            AppauseAccessibilityService.lastOverlayResult = "overlay_ok"
            AppLogger.d(TAG, "Overlay shown for $targetPackage (type=$usedType), cooldown=${cooldownSeconds}s")
            PersistentLog.log(context, "Overlay", "overlay shown type=$usedType for $targetPackage")
        } else {
            // Both overlay types failed → fall back to launching PauseActivity.
            // NOTE: an Activity can be covered by apps that re-front themselves
            // (e.g. 小红书); the overlay above is the preferred path.
            AppauseAccessibilityService.lastOverlayResult = "fallback_pauseactivity"
            // Clean up the failed overlay's lifecycle
            lifecycleContainer.destroy()
            // Optimistically mark the pause as showing so we don't re-trigger the
            // cooldown while PauseActivity is coming up (or even if it never does).
            AppauseAccessibilityService.pauseShown = true

            // Build the launch intent once, reused by both the fast path and the
            // AlarmManager backup below.
            val intent = Intent(context, com.appause.android.ui.pause.PauseActivity::class.java).apply {
                putExtra("target_package", targetPackage)
                putExtra("group_id", groupId)
                putExtra("cooldown_seconds", cooldownSeconds)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Fast path: direct startActivity — works on most stock ROMs.
            try {
                context.startActivity(intent)
                AppLogger.d(TAG, "Fallback: PauseActivity direct launch attempted for $targetPackage")
            } catch (e2: Exception) {
                AppLogger.w(TAG, "Direct PauseActivity launch failed (will rely on AlarmManager): ${e2.message}")
            }

            // Reliable path: AlarmManager has higher system authority than a
            // background startActivity on MIUI/HyperOS, where the direct launch is
            // silently dropped. The receiver re-launches PauseActivity ~250ms later;
            // if the direct launch already won, singleInstance + CLEAR_TOP just
            // re-fronts the existing instance (no duplicate screen, no reset).
            schedulePauseViaAlarm(context, targetPackage, groupId, cooldownSeconds)
        }
    }

    /**
     * Launch [com.appause.android.ui.pause.PauseActivity] via [AlarmManager].
     *
     * On MIUI/HyperOS a background `startActivity()` from the AccessibilityService
     * is silently deprioritized and the Activity never reaches the foreground.
     * AlarmManager (a higher-authority system component) reliably brings it to
     * the front. This is the real fallback used when the WindowManager overlay
     * can't be added (e.g. TYPE_ACCESSIBILITY_OVERLAY rejected on Android 16).
     */
    private fun schedulePauseViaAlarm(
        context: Context,
        targetPackage: String,
        groupId: Long,
        cooldownSeconds: Int
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alarmIntent = Intent(context, com.appause.android.service.PauseAlarmReceiver::class.java).apply {
                putExtra("target_package", targetPackage)
                putExtra("group_id", groupId)
                putExtra("cooldown_seconds", cooldownSeconds)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + 250L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            AppLogger.d(TAG, "Scheduled PauseActivity via AlarmManager (MIUI/HyperOS fallback)")
            PersistentLog.log(context, "Overlay", "AlarmManager backup scheduled for $targetPackage")
        } catch (e: Exception) {
            AppLogger.e(TAG, "AlarmManager fallback failed for $targetPackage", e)
            PersistentLog.log(context, "Overlay", "AlarmManager FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Remove the overlay from the screen and clean up resources.
     * Called when the user taps Cancel, Continue, or when the service
     * detects the user has left the target app.
     */
    fun dismiss() {
        val view = overlayView ?: return

        try {
            val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(view)
            AppLogger.d(TAG, "Overlay dismissed")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error removing overlay", e)
        }

        // Clean up the lifecycle so Compose effects stop running
        // (The LifecycleContainer will be garbage collected along with the view)
        overlayView = null

        // Cancel any running coroutines (countdown timer, etc.)
        overlayScope?.coroutineContext?.get(Job)?.cancel()
        overlayScope = null

        // Reset the guard flag so the next target app open can trigger interception
        AppauseAccessibilityService.pauseShown = false
    }

    /** Whether the overlay is currently showing. */
    val isShowing: Boolean get() = overlayView != null
}

/**
 * Wrap [base] with the user's chosen locale (zh / en), matching the override
 * applied in AppauseApp / MainActivity / PauseActivity. Returns a context whose
 * resources resolve strings in the selected language. The AccessibilityService
 * itself does not override locale, so the overlay must do it explicitly.
 */
private fun applyAppLocaleOverride(base: Context): Context {
    val prefs = base.getSharedPreferences("appause_locale_prefs", Context.MODE_PRIVATE)
    val languageCode = prefs.getString("language", null)
        ?: if (Locale.getDefault().language == "zh") "zh" else "en"
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

/**
 * Minimal lifecycle + saved-state container for a ComposeView in a WindowManager overlay.
 *
 * Compose needs a lifecycle to run LaunchedEffect and other side-effects.
 * In a normal Activity, this is provided automatically. For a standalone view
 * added via WindowManager, we must provide it manually.
 *
 * This container implements both LifecycleOwner and SavedStateRegistryOwner,
 * which are the two "view tree owners" that Compose requires.
 */
private class LifecycleContainer : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun create() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun resume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
