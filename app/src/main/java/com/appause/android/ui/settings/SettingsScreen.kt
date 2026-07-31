package com.appause.android.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.R

/**
 * Settings Screen — configure Appause behavior and view debug info.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPro: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onLanguageChanged: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val defaultPrompt by viewModel.defaultPrompt.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val isUsageAccessGranted by viewModel.isUsageAccessGranted.collectAsStateWithLifecycle()
    val isIgnoringBattery by viewModel.isIgnoringBattery.collectAsStateWithLifecycle()
    val showNotification by viewModel.showNotification.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-query permission status every time the screen becomes visible (e.g.
    // after the user grants a permission in a system page and navigates back).
    // A plain LaunchedEffect(Unit) only fires on first composition, which is
    // why the red/green dot used to lag until re-entering the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshServiceStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Language ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = language == "en",
                            onClick = {
                                if (language != "en") {
                                    // Save language first, then restart the app
                                    viewModel.setLanguage("en", onLanguageChanged)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.language_english))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = language == "zh",
                            onClick = {
                                if (language != "zh") {
                                    // Save language first, then restart the app
                                    viewModel.setLanguage("zh", onLanguageChanged)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.language_chinese))
                    }
                }
            }

            // ── Theme ──
            // Light / Dark / Follow system. The choice is applied reactively
            // (no restart) because the Activities observe the theme mode.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = themeMode == "light",
                            onClick = { if (themeMode != "light") viewModel.setThemeMode("light") }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.theme_light))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = themeMode == "dark",
                            onClick = { if (themeMode != "dark") viewModel.setThemeMode("dark") }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.theme_dark))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = themeMode == "system",
                            onClick = { if (themeMode != "system") viewModel.setThemeMode("system") }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.theme_system))
                    }
                }
            }

            // ── Accessibility Service ──
            // Required: without it Appause cannot detect foreground app changes.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.accessibility_service), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.required_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                if (isServiceRunning) R.string.service_running
                                else R.string.service_not_enabled
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isServiceRunning)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(R.string.open_accessibility_settings))
                    }
                }
            }

            // ── Usage Access (optional) ──
            // Confirms which app is genuinely on screen, so a media app's
            // notification in the shade (e.g. Bilibili "now playing") doesn't
            // trigger the pause by mistake. This is a *recommended* enhancement,
            // NOT required — Appause still intercepts normally without it.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.usage_access), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.optional_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (isUsageAccessGranted) R.string.usage_access_granted
                            else R.string.usage_access_not_granted
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUsageAccessGranted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isUsageAccessGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.usage_access_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(R.string.open_usage_access_settings))
                    }
                }
            }

            // ── Battery Optimization (required) ──
            // MIUI and other OEM ROMs aggressively kill background processes,
            // which disconnects the accessibility service. Requesting battery
            // optimization exemption tells the system to keep the process alive.
            // Without it the "running" service is silently killed and Appause
            // stops intercepting — so we mark this as required too.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.battery_optimization),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.required_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (isIgnoringBattery) R.string.battery_exempted
                            else R.string.battery_not_exempted
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isIgnoringBattery)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    if (!isIgnoringBattery) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                // Request battery optimization exemption.
                                // This opens a system dialog asking the user to confirm.
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.request_battery_exempt))
                        }
                    }
                }
            }

            // ── Monitoring notification ──
            // The persistent "Appause is monitoring" notification. Turning it
            // off stops the always-on notification; the accessibility service
            // still runs (just not as a foreground service). Trade-off: some
            // aggressive OEM ROMs may be more likely to kill it in the background.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.show_notification_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = showNotification,
                            onCheckedChange = viewModel::setShowNotification
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.show_notification_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Default Prompt ──
            // Custom prompt is a Pro feature. Free users see the field disabled
            // with a hint; tapping the card opens the Pro screen.
            // NOTE: bind the TextField to LOCAL state, not directly to the async
            // StateFlow. updateDefaultPrompt writes to DataStore asynchronously,
            // so a direct binding makes `value` briefly revert to the previous
            // text on every keystroke, clamping the cursor to position 0 (left).
            var prompt by remember { mutableStateOf(defaultPrompt) }
            LaunchedEffect(defaultPrompt) {
                if (defaultPrompt != prompt) prompt = defaultPrompt
            }
            if (isPro) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.default_prompt_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = {
                                prompt = it
                                viewModel.updateDefaultPrompt(it)
                            },
                            label = { Text(stringResource(R.string.prompt_label)) },
                            placeholder = { Text(stringResource(R.string.default_prompt)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToPro
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.default_prompt_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.pro_locked_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(R.string.pro_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Appause Pro ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToPro
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pro_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.pro_settings_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Feedback ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToFeedback
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Feedback,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.feedback_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.feedback_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Debug Info ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.debug_info), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.debug_android, Build.VERSION.SDK_INT, Build.VERSION.RELEASE),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.debug_enabled, isEnabled.toString()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.debug_service, isServiceRunning.toString()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.debug_usage_access, isUsageAccessGranted.toString()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
