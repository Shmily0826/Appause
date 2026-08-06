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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.R

/**
 * Shared scaffold for every Settings sub-screen: a top bar with a back button
 * and a single scrollable column of cards. Keeps the four sub-screens visually
 * consistent with the old single-page Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScaffold(
    titleRes: Int,
    onNavigateBack: () -> Unit,
    content: @Composable (innerPadding: androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
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
            content(innerPadding)
        }
    }
}

/**
 * Appearance sub-screen: language + theme.
 * Language changes are applied by restarting the app (onLanguageChanged),
 * exactly like the old Settings page — theme changes apply reactively.
 */
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    onLanguageChanged: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    SettingsSubScaffold(R.string.settings_category_appearance, onNavigateBack) {
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
                        onClick = { if (language != "en") viewModel.setLanguage("en", onLanguageChanged) }
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
                        onClick = { if (language != "zh") viewModel.setLanguage("zh", onLanguageChanged) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.language_chinese))
                }
            }
        }

        // ── Theme ──
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
    }
}

/**
 * Permissions & Running sub-screen: accessibility service (required),
 * battery optimization (required), usage access (optional) and the
 * persistent monitoring notification.
 */
@Composable
fun PermissionsSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val isUsageAccessGranted by viewModel.isUsageAccessGranted.collectAsStateWithLifecycle()
    val isIgnoringBattery by viewModel.isIgnoringBattery.collectAsStateWithLifecycle()
    val canDrawOverlays by viewModel.canDrawOverlays.collectAsStateWithLifecycle()
    val showNotification by viewModel.showNotification.collectAsStateWithLifecycle()

    // "Enabled" green — legible on both light and dark surfaces.
    val enabledGreen = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshServiceStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSubScaffold(R.string.settings_category_permissions, onNavigateBack) {
        // ── Why these permissions? ──
        // Put this first so users see the privacy explanation before they judge
        // each individual toggle. Also defends against "this app is malicious"
        // anxiety on OEM ROMs that aggressively warn about accessibility access.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.permissions_why_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.permissions_why_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Accessibility Service (required) ──
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.accessibility_service), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(if (isServiceRunning) R.string.status_enabled else R.string.status_disabled),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isServiceRunning) enabledGreen else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (isServiceRunning) R.string.service_running else R.string.service_not_enabled
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isServiceRunning) enabledGreen else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.open_accessibility_settings))
                }
            }
        }

        // ── Display over other apps (required on OEM ROMs like HyperOS) ──
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.overlay_permission), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(if (canDrawOverlays) R.string.status_enabled else R.string.status_disabled),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (canDrawOverlays) enabledGreen else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(if (canDrawOverlays) R.string.overlay_granted else R.string.overlay_not_granted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (canDrawOverlays) enabledGreen else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!canDrawOverlays) {
                    Text(
                        text = stringResource(R.string.overlay_permission_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.open_overlay_settings))
                }
            }
        }

        // ── Battery Optimization (required) ──
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.battery_optimization), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(if (isIgnoringBattery) R.string.status_enabled else R.string.status_disabled),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isIgnoringBattery) enabledGreen else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (isIgnoringBattery) R.string.battery_exempted else R.string.battery_not_exempted
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isIgnoringBattery) enabledGreen else MaterialTheme.colorScheme.error
                )
                if (!isIgnoringBattery) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.request_battery_exempt))
                    }
                }
            }
        }

        // ── Usage Access (optional) ──
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.usage_access), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(if (isUsageAccessGranted) R.string.status_enabled else R.string.status_disabled),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUsageAccessGranted) enabledGreen else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (isUsageAccessGranted) R.string.usage_access_granted else R.string.usage_access_not_granted
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUsageAccessGranted) enabledGreen else MaterialTheme.colorScheme.error
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
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.open_usage_access_settings))
                }
            }
        }

        // ── Monitoring notification ──
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.show_notification_title), style = MaterialTheme.typography.titleMedium)
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

        // ── Open-source / privacy reassurance ──
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.open_source_footer_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.open_source_footer_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                val repoUrl = stringResource(R.string.github_repo_url)
                Button(
                    onClick = { uriHandler.openUri(repoUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.github_repo_button))
                }
            }
        }
    }
}

/**
 * Pause behavior sub-screen: default prompt + custom open reasons.
 *
 * Both cards are always visible so free users can see what the page offers,
 * but Pro-only fields are disabled and show a lock hint until they upgrade.
 */
@Composable
fun PauseSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPro: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val defaultPrompt by viewModel.defaultPrompt.collectAsStateWithLifecycle()
    val reasonCustom by viewModel.reasons.collectAsStateWithLifecycle()
    val reasonDefs = listOf(
        R.string.intent_work, R.string.intent_bored,
        R.string.intent_messages, R.string.intent_other
    )

    var prompt by remember { mutableStateOf(defaultPrompt) }
    LaunchedEffect(defaultPrompt) {
        if (defaultPrompt != prompt) prompt = defaultPrompt
    }

    SettingsSubScaffold(R.string.settings_category_pause, onNavigateBack) {
        // ── Default prompt (Pro-only edit) ──
        ProLockedCard(isPro = isPro, onNavigateToPro = onNavigateToPro) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.default_prompt_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    if (!isPro) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.pro_badge),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = stringResource(R.string.pro_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!isPro) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pro_locked_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = {
                        prompt = it
                        viewModel.updateDefaultPrompt(it)
                    },
                    enabled = isPro,
                    label = { Text(stringResource(R.string.prompt_label)) },
                    placeholder = { Text(stringResource(R.string.prompt_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Custom open reasons (Pro-only edit) ──
        ProLockedCard(isPro = isPro, onNavigateToPro = onNavigateToPro) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.custom_reasons_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    if (!isPro) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.pro_badge),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = stringResource(R.string.pro_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.custom_reasons_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                reasonDefs.forEachIndexed { index, defRes ->
                    CustomReasonField(
                        value = reasonCustom.getOrNull(index) ?: "",
                        defaultRes = defRes,
                        enabled = isPro,
                        onValueChange = { viewModel.updateReason(index, it) }
                    )
                    if (index < reasonDefs.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * A Card that becomes clickable only when the feature is locked.
 * Keeps the same look for Pro and free users; free users tap to go Pro.
 */
@Composable
private fun ProLockedCard(
    isPro: Boolean,
    onNavigateToPro: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    if (isPro) {
        Card(modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            onClick = onNavigateToPro,
            content = content
        )
    }
}

/**
 * About sub-screen: version + debug info, in case the user (or support) needs
 * to report what's running.
 */
@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val isUsageAccessGranted by viewModel.isUsageAccessGranted.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshServiceStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSubScaffold(R.string.settings_category_about, onNavigateBack) {
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

/**
 * One editable field for a custom open-reason label.
 * Binds to LOCAL state (not directly to the async StateFlow) so keystrokes
 * don't briefly revert `value` and clamp the cursor to position 0. External
 * changes (e.g. Pro restore) are synced via LaunchedEffect.
 */
@Composable
private fun CustomReasonField(
    value: String,
    defaultRes: Int,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != text) text = value
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        enabled = enabled,
        label = { Text(stringResource(defaultRes)) },
        placeholder = { Text(stringResource(defaultRes)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
