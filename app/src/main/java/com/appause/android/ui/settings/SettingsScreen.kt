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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    onLanguageChanged: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val defaultPrompt by viewModel.defaultPrompt.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshServiceStatus() }

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

            // ── Accessibility Service ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.accessibility_service), style = MaterialTheme.typography.titleMedium)
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

            // ── Battery Optimization ──
            // MIUI and other OEM ROMs aggressively kill background processes,
            // which disconnects the accessibility service. Requesting battery
            // optimization exemption tells the system to keep the process alive.
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.battery_optimization),
                        style = MaterialTheme.typography.titleMedium
                    )
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

            // ── Default Prompt ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.default_prompt_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = defaultPrompt,
                        onValueChange = viewModel::updateDefaultPrompt,
                        label = { Text(stringResource(R.string.prompt_label)) },
                        placeholder = { Text(stringResource(R.string.default_prompt)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                }
            }
        }
    }
}
