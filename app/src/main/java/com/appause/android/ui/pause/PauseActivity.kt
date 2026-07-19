package com.appause.android.ui.pause

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.appause.android.AppauseApp
import com.appause.android.R
import com.appause.android.interception.InterceptionManager
import com.appause.android.service.AppauseAccessibilityService
import com.appause.android.ui.theme.AppauseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * PauseActivity — the cooldown screen shown when a user opens a target app.
 *
 * Why a separate Activity (not a Compose screen in NavGraph)?
 * - It must be launched from AccessibilityService.
 * - The Service has no access to MainActivity's NavController.
 * - It needs FLAG_ACTIVITY_NEW_TASK to start from a Service context.
 *
 * What it shows:
 * - Appause brand name
 * - Target app icon and name
 * - Prompt message (e.g., "Take a moment.")
 * - Countdown timer (large number + progress ring)
 * - Cancel button (returns to home screen)
 * - Continue button (appears when countdown reaches 0)
 *
 * Back button behavior:
 * - Pressing Back acts as Cancel → goes to home screen.
 * - This prevents the user from being stuck on this screen.
 */
class PauseActivity : ComponentActivity() {

    private val targetPackage: String get() = intent.getStringExtra("target_package") ?: ""
    private val groupId: Long get() = intent.getLongExtra("group_id", -1L)
    private val cooldownSeconds: Int get() = intent.getIntExtra("cooldown_seconds", 10)

    /** Tracks whether the user tapped Continue (vs Cancel or back press). */
    private var userProceeded = false

    /**
     * Override locale so the pause screen uses the correct language.
     * Default: system language (Chinese system → "zh", otherwise → "en").
     */
    override fun attachBaseContext(base: Context) {
        val prefs = base.getSharedPreferences("appause_locale_prefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", null)
            ?: if (Locale.getDefault().language == "zh") "zh" else "en"

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)

        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply locale to Activity resources before Compose renders.
        // See MainActivity.onCreate for detailed explanation.
        val prefs = getSharedPreferences("appause_locale_prefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", null)
            ?: if (Locale.getDefault().language == "zh") "zh" else "en"
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        super.onCreate(savedInstanceState)

        // Load target app info from PackageManager (icon + display name).
        // We do this here (not in Compose) because PackageManager needs Context.
        val pm = packageManager
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

        // Get the default prompt message from settings.
        val repository = (application as AppauseApp).repository

        setContent {
            AppauseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Load default prompt from DataStore
                    // If stored prompt is blank, use localized default
                    val defaultPromptText = stringResource(R.string.default_prompt)
                    var prompt by remember { mutableStateOf(defaultPromptText) }
                    LaunchedEffect(Unit) {
                        repository.defaultPrompt.collect { stored ->
                            prompt = if (stored.isBlank()) defaultPromptText else stored
                        }
                    }

                    // Countdown state — shared helper provides smooth progress (~60fps)
                    // instead of stepping once per second. Also handles the onFinished callback.
                    val countdown = rememberCountdownState(cooldownSeconds) {
                        // Timer finished → start bypass so the user can enter the app
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
                        onCancel = { handleCancel() },
                        onContinueWithReason = { reason -> handleContinueWithReason(reason) }
                    )

                    // Back button acts as Cancel
                    BackHandler { handleCancel() }
                }
            }
        }
    }

    /**
     * User tapped Cancel or pressed Back.
     * Send them to the home screen so they don't land on the target app.
     */
    private fun handleCancel() {
        // Log the cancellation
        val repository = (application as AppauseApp).repository
        CoroutineScope(Dispatchers.IO).launch {
            repository.logLaunch(targetPackage, groupId, "cancelled")
        }

        // Clean up any bypass state
        InterceptionManager.clearBypass(targetPackage)

        // Go to home screen — NOT just finish(), because that would reveal the target app
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
        finish()
    }

    /**
     * User selected a reason after the countdown finished.
     * The bypass is already active (set by the LaunchedEffect when timer hit 0).
     * Log the reason and finish this Activity — the target app is underneath.
     */
    private fun handleContinueWithReason(reason: String) {
        userProceeded = true

        // Log the successful proceed with the selected reason
        val repository = (application as AppauseApp).repository
        CoroutineScope(Dispatchers.IO).launch {
            repository.logLaunch(targetPackage, groupId, "proceeded", reason)
        }

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Reset the guard flag so the next target app open can trigger interception
        AppauseAccessibilityService.pauseShown = false

        // If the user didn't proceed, clean up the bypass.
        // This handles cases like: system kills the Activity, user swipes from recents, etc.
        if (!userProceeded) {
            InterceptionManager.clearBypass(targetPackage)
        }
    }
}

/**
 * The visual content of the Pause Screen.
 *
 * Layout (top to bottom):
 * 1. App icon + name
 * 2. Prompt message (e.g., "Take a moment.")
 * 3. Countdown ring with animated number (or checkmark when finished)
 * 4. 2x2 reason selection grid (disabled until countdown finishes)
 * 5. Cancel button
 *
 * The reason buttons are disabled during the countdown — the user must
 * complete the full cooldown before choosing a reason and proceeding.
 *
 * @param smoothProgress 0.0 to 1.0 continuous progress (updated ~60fps by CountdownState).
 * @param isFinished true when countdown reaches zero.
 * @param onContinueWithReason Called with the selected reason string when user picks one.
 */
@Composable
internal fun PauseScreenContent(
    appName: String,
    appIcon: Drawable?,
    prompt: String,
    secondsLeft: Int,
    smoothProgress: Float,
    totalSeconds: Int,
    isFinished: Boolean,
    onCancel: () -> Unit,
    onContinueWithReason: (String) -> Unit
) {
    // Track whether the user has selected a reason (disables all buttons)
    var reasonSelected by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── App Icon ──
            if (appIcon != null) {
                val bitmap = remember(appIcon) {
                    appIcon.toBitmap(width = 128, height = 128).asImageBitmap()
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = appName,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── App Name ──
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Prompt Message ──
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Countdown Ring + Number ──
            Box(contentAlignment = Alignment.Center) {
                CountdownRing(
                    progress = smoothProgress,
                    isFinished = isFinished
                )

                AnimatedContent(
                    targetState = if (isFinished) -1 else secondsLeft,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith
                            (slideOutVertically { -it } + fadeOut())
                    },
                    label = "countdown_number"
                ) { number ->
                    Text(
                        text = if (number >= 0) "$number" else "\u2713",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (number >= 0)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Reason selection grid — enabled only after countdown finishes ──
            // The user must complete the cooldown before proceeding.
            // Once the timer hits 0, they pick a reason and enter the app.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReasonButton(
                        text = stringResource(R.string.intent_work),
                        enabled = isFinished && !reasonSelected,
                        onClick = {
                            reasonSelected = true
                            onContinueWithReason("work")
                        }
                    )
                    ReasonButton(
                        text = stringResource(R.string.intent_bored),
                        enabled = isFinished && !reasonSelected,
                        onClick = {
                            reasonSelected = true
                            onContinueWithReason("bored")
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReasonButton(
                        text = stringResource(R.string.intent_messages),
                        enabled = isFinished && !reasonSelected,
                        onClick = {
                            reasonSelected = true
                            onContinueWithReason("messages")
                        }
                    )
                    ReasonButton(
                        text = stringResource(R.string.intent_other),
                        enabled = isFinished && !reasonSelected,
                        onClick = {
                            reasonSelected = true
                            onContinueWithReason("other")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Cancel button — always visible below the reason grid ──
            TextButton(
                onClick = onCancel,
                enabled = !reasonSelected
            ) {
                Text(
                    text = stringResource(R.string.pause_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * A single reason button in the intent selection grid.
 * Styled as an outlined button to feel lighter than a primary action —
 * the user is making a choice, not confirming a dangerous operation.
 */
@Composable
private fun ReasonButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(140.dp)
            .height(44.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
