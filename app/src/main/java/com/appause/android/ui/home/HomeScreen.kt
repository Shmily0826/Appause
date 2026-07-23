package com.appause.android.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.R
import com.appause.android.data.local.AppGroup

/**
 * Home Screen — the main entry point of Appause.
 *
 * Shows:
 * - A unified status header (service status dot + master toggle)
 * - Today's interception statistics
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
    onNavigateToStats: () -> Unit,
    onNavigateToRecommended: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val proceededToday by viewModel.proceededToday.collectAsStateWithLifecycle()
    val cancelledToday by viewModel.cancelledToday.collectAsStateWithLifecycle()
    val appCounts by viewModel.appCounts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Refresh service status + app counts every time the screen becomes visible.
    // Unlike LaunchedEffect(Unit) which fires only once on first composition,
    // this lifecycle observer fires on every ON_RESUME — so the status updates
    // automatically when the user returns from accessibility settings or the
    // group editor.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshServiceStatus()
                viewModel.loadAppCounts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appause") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            // Extended FAB: icon + label makes the action explicit.
            // The visible "New group" text doubles as the accessibility label.
            ExtendedFloatingActionButton(
                onClick = { onNavigateToGroupEditor(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_group)) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Unified Status Header (service status + master toggle) ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                StatusHeaderCard(
                    isServiceRunning = isServiceRunning,
                    isEnabled = isEnabled,
                    onToggle = viewModel::toggleEnabled,
                    onOpenSettings = {
                        // Open the system Accessibility Settings page
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                )
            }

            // ── Today's Statistics ──
            item {
                TodayStatsCard(
                    proceeded = proceededToday,
                    cancelled = cancelledToday,
                    onClick = onNavigateToStats
                )
            }

            // ── Recommended Apps entry ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToRecommended,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.recommended_apps_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.recommended_apps_home_desc),
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
            }

            // ── Groups Section ──
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.your_groups),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (groups.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_groups_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(groups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        appCount = appCounts[group.id] ?: 0,
                        onClick = { onNavigateToGroupEditor(group.id) }
                    )
                }
            }

            // Bottom spacing so FAB doesn't cover last item
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

/**
 * Unified status control header.
 *
 * Two clearly distinct states — the old design showed a red "Not enabled"
 * label next to a switch that looked ON, which was contradictory. Now:
 *
 * 1. Service OFF (permission missing): a light-red warning card with a title,
 *    an explanation and an explicit "Open settings" action. The master switch
 *    is hidden entirely — toggling is meaningless while Appause cannot even
 *    observe the foreground app. Tapping anywhere on the card also opens the
 *    system accessibility settings.
 *
 * 2. Service ON: a normal card titled "Service active". Only in this state is
 *    the master on/off switch shown and enabled.
 */
@Composable
private fun StatusHeaderCard(
    isServiceRunning: Boolean,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit
) {
    // Controls the OEM guidance dialog shown from the warning card.
    var showServiceHelp by remember { mutableStateOf(false) }

    if (!isServiceRunning) {
        // ── Warning state: accessibility permission is missing ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenSettings,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.service_not_enabled_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.service_not_enabled_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                // Explicit action — don't rely on card color alone to convey
                // what the user should do.
                TextButton(onClick = onOpenSettings) {
                    Text(
                        text = stringResource(R.string.open_settings),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // Secondary action: explain why Xiaomi/Huawei keep turning
                // the service off and what the user can do about it.
                TextButton(onClick = { showServiceHelp = true }) {
                    Text(
                        text = stringResource(R.string.service_off_help),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    } else {
        // ── Normal state: service is active, master toggle is usable ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status dot — primary color signals "everything is fine".
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.service_active),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(
                            if (isEnabled) R.string.appause_enabled
                            else R.string.appause_disabled
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Resolve the label outside the semantics lambda —
                // stringResource() needs a @Composable context and
                // semantics {} is not one.
                val masterToggleLabel = stringResource(R.string.cd_master_toggle)
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics {
                        contentDescription = masterToggleLabel
                    }
                )
            }
        }
    }

    // OEM guidance dialog — explains why Xiaomi/Huawei turn the service off
    // and lists the manual steps the user can take to keep it alive.
    if (showServiceHelp) {
        AlertDialog(
            onDismissRequest = { showServiceHelp = false },
            title = { Text(stringResource(R.string.service_help_title)) },
            text = { Text(stringResource(R.string.service_help_body)) },
            confirmButton = {
                TextButton(onClick = { showServiceHelp = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }
}

/** Card showing today's interception statistics: waited vs cancelled. Tappable to open full stats. */
@Composable
private fun TodayStatsCard(proceeded: Int, cancelled: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row with a "details" chevron so users know it's tappable
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.today),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.view_details),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // "Waited" — user completed the cooldown and proceeded
                StatItem(
                    value = proceeded,
                    label = stringResource(R.string.stat_waited),
                    icon = Icons.Default.HourglassEmpty,
                    color = MaterialTheme.colorScheme.primary
                )
                // "Cancelled" — user backed out during cooldown
                StatItem(
                    value = cancelled,
                    label = stringResource(R.string.stat_cancelled),
                    icon = Icons.Default.Block,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

/**
 * A single statistic: a small icon on top, then a large bold number in the
 * accent color, then a caption label.
 */
@Composable
private fun StatItem(value: Int, label: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$value",
            // headlineLarge is bold + large in the app's type scale.
            style = MaterialTheme.typography.headlineLarge,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Card representing a single app group, using a consistent three-row layout:
 * 1. Group name + type badge (Cooldown / Recommended)
 * 2. Number of apps in the group (pluralized: "1 app" / "N apps")
 * 3. Rule summary — cooldown time or "never blocked" hint
 * A trailing chevron signals that the whole card is tappable.
 */
@Composable
private fun GroupCard(group: AppGroup, appCount: Int, onClick: () -> Unit) {
    val isLearning = group.type == AppGroup.TYPE_LEARNING
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick // Material 3 Card provides ripple + pressed state
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: group name + type badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TypeBadge(isLearning = isLearning)
                }

                // Row 2: how many apps are in this group (correct plural form)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (appCount > 0) {
                            pluralStringResource(R.plurals.group_app_count, appCount, appCount)
                        } else {
                            stringResource(R.string.group_no_apps)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Row 3: rule summary — cooldown time or "never blocked" hint
                Text(
                    text = if (isLearning) {
                        stringResource(R.string.group_type_learning_desc)
                    } else {
                        stringResource(R.string.cooldown_format, group.cooldownSeconds)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Trailing chevron — makes tappability obvious without color.
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Small pill badge marking a group's type.
 * Pause groups get a warm (tertiary) tone; learning groups get a teal
 * (secondary) tone, so the two are easy to tell apart at a glance.
 */
@Composable
private fun TypeBadge(isLearning: Boolean) {
    Text(
        text = stringResource(if (isLearning) R.string.badge_learning else R.string.badge_pause),
        style = MaterialTheme.typography.labelSmall,
        color = if (isLearning) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
        modifier = Modifier
            .background(
                color = if (isLearning) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
