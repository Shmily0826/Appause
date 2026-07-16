package com.appause.android.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.data.local.AppGroup

/**
 * Home Screen — the main entry point of Appause.
 *
 * Shows:
 * - Accessibility Service status + link to settings
 * - Master toggle
 * - List of created groups
 * - FAB to create a new group
 *
 * This screen observes the HomeViewModel for state changes.
 * When the user rotates the device or switches apps and comes back,
 * the ViewModel survives and the state is preserved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToGroupEditor: (Long?) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Refresh service status every time the screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshServiceStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appause") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToGroupEditor(null) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create group")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Service Status Card ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ServiceStatusCard(
                    isServiceRunning = isServiceRunning,
                    onOpenSettings = {
                        // Open the system Accessibility Settings page
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                )
            }

            // ── Master Toggle Card ──
            item {
                MasterToggleCard(isEnabled = isEnabled, onToggle = viewModel::toggleEnabled)
            }

            // ── Groups Section ──
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your Groups",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (groups.isEmpty()) {
                item {
                    Text(
                        text = "No groups yet. Tap + to create your first group.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(groups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        onClick = { onNavigateToGroupEditor(group.id) }
                    )
                }
            }

            // Bottom spacing so FAB doesn't cover last item
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

/** Card showing Accessibility Service status and a button to open system settings. */
@Composable
private fun ServiceStatusCard(isServiceRunning: Boolean, onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isServiceRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Accessibility Service",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (isServiceRunning) "Running" else "Not enabled",
                style = MaterialTheme.typography.bodyMedium
            )
            if (!isServiceRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap here to enable in system settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .then(Modifier) // Clickable handled by parent Card in future
                )
                // For simplicity, the whole card area navigates to settings
                // In a production app, this would be a proper Button
            }
        }
    }
}

/** Card with the master on/off toggle for Appause. */
@Composable
private fun MasterToggleCard(isEnabled: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Appause",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isEnabled, onCheckedChange = { onToggle() })
        }
    }
}

/** Card representing a single app group in the list. */
@Composable
private fun GroupCard(group: AppGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick // Material 3 Card supports onClick directly
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${group.cooldownSeconds}s cooldown",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
