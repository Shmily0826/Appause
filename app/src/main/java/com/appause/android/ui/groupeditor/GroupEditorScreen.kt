package com.appause.android.ui.groupeditor

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.R
import com.appause.android.data.local.AppGroup
import com.appause.android.ui.appselect.AppSelectScreen

/**
 * Group Editor Screen — create or edit an app group.
 *
 * Layout:
 * - Top bar with back button, title, and delete button (edit mode only)
 * - Group name text field
 * - Cooldown seconds slider + text field
 * - "Select Apps" button (navigates to App Select screen)
 * - List of currently selected apps
 * - Save button
 *
 * Data flow:
 * 1. User enters name and cooldown → stored in ViewModel state
 * 2. User taps "Select Apps" → navigates to AppSelectScreen
 * 3. User picks apps → confirmed → cached in AppSelectScreen.cachedSelectedPackages
 * 4. User returns here → LaunchedEffect reads cache → updates ViewModel
 * 5. User taps Save → ViewModel writes to Repository → navigates back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditorScreen(
    groupId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAppSelect: () -> Unit,
    viewModel: GroupEditorViewModel = viewModel()
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val cooldownSeconds by viewModel.cooldownSeconds.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val saveCompleted by viewModel.saveCompleted.collectAsStateWithLifecycle()

    // Load existing group data when editing
    LaunchedEffect(groupId) {
        if (groupId > 0) viewModel.loadGroup(groupId)
    }

    // Read cached app selection when returning from AppSelectScreen
    LaunchedEffect(Unit) {
        viewModel.refreshSelectedPackages()
    }

    // Navigate back after successful save/delete
    LaunchedEffect(saveCompleted) {
        if (saveCompleted) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) stringResource(R.string.title_edit_group)
                        else stringResource(R.string.title_new_group)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_group))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            // Pinned Save/Cancel bar — always visible even when the form is long.
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = viewModel::save,
                        enabled = name.isNotBlank()
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
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
            // ── Group Name ──
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.label_group_name)) },
                placeholder = { Text(stringResource(R.string.placeholder_group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Group Type ──
            Text(
                text = stringResource(R.string.group_type_label),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TypeOptionCard(
                    title = stringResource(R.string.group_type_pause),
                    description = stringResource(R.string.group_type_pause_desc),
                    selected = type == AppGroup.TYPE_PAUSE,
                    onClick = { viewModel.updateType(AppGroup.TYPE_PAUSE) },
                    modifier = Modifier.weight(1f)
                )
                TypeOptionCard(
                    title = stringResource(R.string.group_type_learning),
                    description = stringResource(R.string.group_type_learning_desc),
                    selected = type == AppGroup.TYPE_LEARNING,
                    onClick = { viewModel.updateType(AppGroup.TYPE_LEARNING) },
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Cooldown Seconds (pause groups only — learning groups are never blocked) ──
            if (type == AppGroup.TYPE_PAUSE) {
                Text(
                    text = stringResource(R.string.cooldown_label, cooldownSeconds),
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = cooldownSeconds.toFloat(),
                    onValueChange = { viewModel.updateCooldown(it.toInt()) },
                    valueRange = 1f..60f,
                    steps = 58,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cooldownSeconds.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { viewModel.updateCooldown(it) }
                    },
                    label = { Text(stringResource(R.string.label_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Select Apps Button ──
            OutlinedButton(
                onClick = {
                    // Cache current selection so AppSelectScreen can pre-check them
                    AppSelectScreen.cachedInitialPackages = selectedPackages.toList()
                    onNavigateToAppSelect()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.select_apps_button, selectedPackages.size))
            }

            // ── Selected Apps Preview ──
            if (selectedPackages.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.apps_in_group),
                            style = MaterialTheme.typography.labelLarge
                        )
                        selectedPackages.take(5).forEach { pkg ->
                            Text(
                                text = pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selectedPackages.size > 5) {
                            Text(
                                text = stringResource(R.string.and_more_apps, selectedPackages.size - 5),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

        }
    }
}

/**
 * A selectable card for choosing the group type (Pause vs Learning).
 *
 * When [selected] is true the card is highlighted with the primary container
 * color and a thicker primary border, so the current choice is obvious.
 */
@Composable
private fun TypeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
