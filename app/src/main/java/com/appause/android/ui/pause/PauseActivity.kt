package com.appause.android.ui.pause

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    /** Timer coroutine — cancelled if the Activity is destroyed early. */
    private var timerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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

                    // Countdown state
                    var secondsLeft by remember { mutableIntStateOf(cooldownSeconds) }
                    val isPaused = secondsLeft > 0

                    // Countdown timer — ticks every second
                    LaunchedEffect(cooldownSeconds) {
                        timerJob = coroutineContext[Job]
                        while (secondsLeft > 0) {
                            delay(1000L)
                            secondsLeft--
                        }
                        // Timer finished → start bypass so the user can enter the app
                        InterceptionManager.startBypass(targetPackage)
                    }

                    PauseScreenContent(
                        appName = appName,
                        appIcon = appIcon,
                        prompt = prompt,
                        secondsLeft = secondsLeft,
                        totalSeconds = cooldownSeconds,
                        isPaused = isPaused,
                        onCancel = { handleCancel() },
                        onContinue = { handleContinue() }
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
     * User tapped Continue after the countdown finished.
     * The bypass is already active (set by the LaunchedEffect when timer hit 0).
     * Just finish this Activity — the target app is underneath.
     */
    private fun handleContinue() {
        userProceeded = true

        // Log the successful proceed
        val repository = (application as AppauseApp).repository
        CoroutineScope(Dispatchers.IO).launch {
            repository.logLaunch(targetPackage, groupId, "proceeded")
        }

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()

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
 * Clean, minimal layout focused on the countdown experience.
 */
@Composable
private fun PauseScreenContent(
    appName: String,
    appIcon: Drawable?,
    prompt: String,
    secondsLeft: Int,
    totalSeconds: Int,
    isPaused: Boolean,
    onCancel: () -> Unit,
    onContinue: () -> Unit
) {
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

            Spacer(modifier = Modifier.height(48.dp))

            // ── Countdown Ring + Number ──
            Box(contentAlignment = Alignment.Center) {
                // Animated progress ring
                val progress by animateFloatAsState(
                    targetValue = if (totalSeconds > 0)
                        (totalSeconds - secondsLeft).toFloat() / totalSeconds
                    else 1f,
                    label = "countdown"
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(140.dp),
                    strokeWidth = 6.dp,
                    color = if (isPaused)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.tertiary
                )

                // Large countdown number
                Text(
                    text = if (isPaused) "$secondsLeft" else "✓",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaused)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Action Button ──
            if (isPaused) {
                TextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.pause_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pause_continue),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
