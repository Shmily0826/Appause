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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.appause.android.AppauseApp
import com.appause.android.R
import com.appause.android.data.query.AppInfo
import com.appause.android.data.query.AppQueryService
import com.appause.android.interception.InterceptionManager
import com.appause.android.service.AppauseAccessibilityService
import com.appause.android.ui.theme.AppauseTheme
import com.appause.android.ui.theme.appauseDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * - Prompt message (e.g., "Take a few seconds before deciding whether to continue.")
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

        // Query service for resolving recommended app names (needs Context).
        val appQueryService = AppQueryService(application)

        setContent {
            // Match the user's chosen theme (light / dark / system), using the
            // synchronous value as initial state to avoid a theme flash.
            val settingsDataStore = (application as AppauseApp).settingsDataStore
            val themeMode by settingsDataStore.themeMode
                .collectAsState(initial = settingsDataStore.getThemeModeSync())

            AppauseTheme(darkTheme = appauseDarkTheme(themeMode)) {
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

                    // Custom open-reason labels (Pro). Each blank entry falls back
                    // to its localized default string resource.
                    val reasonKeys = listOf("work", "bored", "messages", "other")
                    val reasonDefs = listOf(
                        R.string.intent_work, R.string.intent_bored,
                        R.string.intent_messages, R.string.intent_other
                    )
                    val defaultLabels = reasonDefs.map { stringResource(it) }
                    var reasons by remember {
                        mutableStateOf(reasonKeys.zip(defaultLabels).map { (k, l) -> k to l })
                    }
                    LaunchedEffect(Unit) {
                        repository.reasons.collect { custom ->
                            reasons = reasonKeys.mapIndexed { i, k ->
                                k to (if (custom[i].isBlank()) defaultLabels[i] else custom[i])
                            }
                        }
                    }

                    // Recommended learning apps — apps the user has added to their
                    // "learning" groups. Shown during the cooldown as "try one of
                    // these instead" suggestions. Excludes the target app itself.
                    var recommendedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
                    LaunchedEffect(Unit) {
                        recommendedApps = withContext(Dispatchers.IO) {
                            repository.getLearningGroupPackageNames()
                                .filter { it != targetPackage }
                                .mapNotNull { pkg ->
                                    appQueryService.getAppName(pkg)?.let { name ->
                                        AppInfo(packageName = pkg, appName = name)
                                    }
                                }
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
                        onContinueWithReason = { reason -> handleContinueWithReason(reason) },
                        reasons = reasons,
                        recommendedApps = recommendedApps,
                        onOpenRecommendedApp = { pkg -> openRecommendedApp(pkg) }
                    )

                    // Back button acts as Cancel
                    BackHandler { handleCancel() }
                }
            }
        }
    }

    /**
     * User tapped a recommended (learning) app during the cooldown.
     * Open that app instead of the distracting target.
     *
     * We clear the bypass for the target so that the next time the user
     * opens the target, the cooldown will trigger again.
     */
    private fun openRecommendedApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return

        // The user chose an alternative app — they are NOT proceeding to the target.
        // Clear any bypass so the target is intercepted again next time.
        InterceptionManager.clearBypass(targetPackage)

        startActivity(launchIntent)
        finish()
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

        // Suppress the stale window event that fires for the target app right
        // before the launcher takes over — otherwise the cooldown re-triggers
        // on the home screen. (Same guard as the overlay path in OverlayManager.)
        // noteCancelled() also auto-clears after a short grace window so the app
        // is intercepted again on the next genuine open.
        AppauseAccessibilityService.noteCancelled(targetPackage)

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

    override fun onStart() {
        super.onStart()
        // Liveness signal for the service's pause-guard watchdog: the pause
        // screen is only "showing" while its window is actually visible.
        AppauseAccessibilityService.pauseActivityVisible = true
    }

    override fun onStop() {
        super.onStop()
        // Covered or backgrounded (e.g. an anti-tamper app re-fronted itself),
        // so the user can no longer see the pause screen. Dropping this lets
        // the watchdog release the guard instead of blocking every later
        // interception.
        AppauseAccessibilityService.pauseActivityVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()

        // Reset the guard flag so the next target app open can trigger interception
        AppauseAccessibilityService.pauseShown = false
        AppauseAccessibilityService.pauseActivityVisible = false
        AppauseAccessibilityService.pauseTargetPackage = null

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
 * 2. Prompt message (e.g., "Take a few seconds before deciding whether to continue.")
 * 3. Countdown ring with animated number (or checkmark when finished)
 * 4. 2x2 reason selection grid (always enabled, single-select)
 * 5. Continue button (enabled only after countdown finishes)
 * 6. Cancel button
 *
 * The reason buttons can be tapped at any time during the countdown — the
 * user answers "why are you opening this app?" while the cooldown runs.
 * Selecting a reason only records the choice; it does NOT dismiss the screen.
 * The user must still wait for the countdown, then tap Continue to enter.
 *
 * @param smoothProgress 0.0 to 1.0 continuous progress (updated ~60fps by CountdownState).
 * @param isFinished true when countdown reaches zero.
 * @param onContinueWithReason Called with the selected reason when the user taps Continue.
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
    onContinueWithReason: (String) -> Unit,
    reasons: List<Pair<String, String>> = emptyList(),
    recommendedApps: List<AppInfo> = emptyList(),
    onOpenRecommendedApp: ((String) -> Unit)? = null
) {
    // The reason the user has selected (null = not selected yet).
    // Selecting a reason does NOT dismiss the screen — the user must still
    // wait for the countdown to finish. This lets them answer early while
    // the cooldown runs, without being able to skip the cooldown.
    var selectedReason by remember { mutableStateOf<String?>(null) }

    // The pause screen must work in any orientation. A full-screen overlay
    // follows the device rotation, and in landscape the screen is short — a
    // fixed vertical stack would overflow and clip the Continue/Cancel
    // buttons. So we make the whole thing scrollable and cap its width so it
    // never stretches awkwardly on wide screens. TopCenter keeps the top
    // reachable when content is taller than the screen.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 24.dp),
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

            Spacer(modifier = Modifier.height(20.dp))

            // ── Recommended learning apps ──
            // Apps the user has already categorised into other groups (e.g. learning
            // apps). Shown during the cooldown as "try one of these instead" — a
            // nudge toward a productive app instead of the distracting one.
            if (recommendedApps.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.pause_recommended_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(recommendedApps, key = { it.packageName }) { app ->
                        RecommendedAppChip(
                            app = app,
                            onClick = { onOpenRecommendedApp?.invoke(app.packageName) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Reason selection grid — always enabled, single-select ──
            // The user can pick a reason at any time during the countdown.
            // Selecting one only records the choice — it does NOT let them
            // enter the app early. They must still wait for the timer.
            // Labels come from `reasons` (Pro-customizable, else localized
            // defaults); laid out 2 per row so long labels are never truncated.
            if (reasons.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reasons.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (key, label) ->
                                ReasonButton(
                                    text = label,
                                    selected = selectedReason == key,
                                    onClick = { selectedReason = key },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Continue button — the ONLY way to enter the app ──
            // Disabled until the countdown finishes. When tapped, logs the
            // selected reason (empty if none) and proceeds to the target app.
            Button(
                onClick = { onContinueWithReason(selectedReason ?: "") },
                enabled = isFinished,
                modifier = Modifier
                    .width(200.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.pause_continue),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Cancel button — always available ──
            TextButton(onClick = onCancel) {
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
 * Always tappable — selecting a reason only records the choice, it does
 * not dismiss the screen. The selected button is highlighted with a filled
 * style so the user can see their current choice.
 */
@Composable
private fun ReasonButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        // Flexible width (caller applies Row weight); keep a comfortable min height.
        modifier = modifier.heightIn(min = 44.dp),
        colors = if (selected) {
            // Highlight the selected reason with the primary container color
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A compact chip for a recommended (learning) app shown during the cooldown.
 * Displays the app icon and name; tapping it opens that app as an alternative
 * to the distracting target.
 */
@Composable
private fun RecommendedAppChip(
    app: AppInfo,
    onClick: () -> Unit
) {
    // Load the app icon from PackageManager — cached per package name.
    val context = LocalContext.current
    val iconBitmap = remember(app.packageName) {
        try {
            context.packageManager
                .getApplicationIcon(app.packageName)
                .toBitmap(width = 64, height = 64)
                .asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = app.appName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
