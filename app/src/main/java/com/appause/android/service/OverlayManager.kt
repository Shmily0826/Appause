package com.appause.android.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Gravity
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
import com.appause.android.interception.InterceptionManager
import com.appause.android.ui.pause.PauseScreenContent
import com.appause.android.ui.pause.rememberCountdownState
import com.appause.android.ui.theme.AppauseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
     */
    fun show(
        service: AppauseAccessibilityService,
        targetPackage: String,
        groupId: Long,
        cooldownSeconds: Int
    ) {
        // Prevent duplicate overlays
        if (overlayView != null) {
            Log.d(TAG, "Overlay already showing, skipping")
            return
        }

        val context = service.applicationContext

        // Load target app info from PackageManager (icon + display name).
        // We do this outside Compose because PackageManager needs Context.
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
        val repository = (context as AppauseApp).repository
        val defaultPromptText = context.resources.getString(R.string.default_prompt)

        // Create WindowManager
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Create the Compose view that will host our UI
        val composeView = ComposeView(context)

        // Set up window layout parameters:
        // - TYPE_ACCESSIBILITY_OVERLAY: draws above all apps, no special permission needed
        // - MATCH_PARENT: covers the entire screen
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Focusable so we capture all input; not touch-modal is default for this type
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        // Set up lifecycle for Compose (needed for LaunchedEffect timers).
        // Without this, Compose doesn't know when to start/stop effects.
        val lifecycleContainer = LifecycleContainer()
        composeView.setViewTreeLifecycleOwner(lifecycleContainer)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleContainer)

        // Start the lifecycle — this enables LaunchedEffect to run
        lifecycleContainer.create()
        lifecycleContainer.start()
        lifecycleContainer.resume()

        // Build the Compose UI
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

                    // Countdown state — shared helper provides smooth progress (~60fps)
                    val countdown = rememberCountdownState(cooldownSeconds) {
                        // Timer finished → start bypass so user can enter the app
                        InterceptionManager.startBypass(targetPackage)
                    }

                    PauseScreenContent(
                        appName = appName,
                        appIcon = appIcon,
                        prompt = prompt,
                        secondsLeft = countdown.secondsLeft,
                        smoothProgress = countdown.smoothProgress,
                        totalSeconds = cooldownSeconds,
                        isFinished = countdown.isFinished,
                        onCancel = {
                            // Log the cancellation
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.logLaunch(targetPackage, groupId, "cancelled")
                            }
                            InterceptionManager.clearBypass(targetPackage)
                            dismiss()
                        },
                        onContinueWithReason = { reason ->
                            // Log the successful proceed with the selected reason
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.logLaunch(targetPackage, groupId, "proceeded", reason)
                            }
                            dismiss()
                        }
                    )
                }
            }
        }

        // Add the overlay to the screen
        windowManager.addView(composeView, params)
        overlayView = composeView

        // Create a coroutine scope for this overlay's lifetime
        overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // Mark that the pause screen is showing (prevents re-triggering)
        AppauseAccessibilityService.pauseShown = true

        Log.d(TAG, "Overlay shown for $targetPackage, cooldown=${cooldownSeconds}s")
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
            Log.d(TAG, "Overlay dismissed")
        } catch (e: Exception) {
            Log.w(TAG, "Error removing overlay", e)
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
