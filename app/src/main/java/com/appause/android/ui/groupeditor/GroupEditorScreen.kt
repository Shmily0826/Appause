package com.appause.android.ui.groupeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.R
import com.appause.android.data.local.AppGroup
import com.appause.android.ui.appselect.AppSelectScreen

// Inactive track uses a low-saturation blue-grey to stay consistent with the
// app's blue theme (the default M3 inactive track can look greenish/minty).
private val InactiveTrackColor = Color(0xFFD8DEE9)

/**
 * Group Editor Screen — create or edit an app group.
 *
 * Layout:
 * - Top bar with back button, title, and delete button (edit mode only)
 * - Group name text field
 * - Group type selector (Cooldown vs Recommended)
 * - Cooldown time setting (slider + number input, bidirectionally synced)
 * - Re-remind setting (switch + slider + number input)
 * - "Add apps" list item (navigates to App Select screen)
 * - "Apps in this group" section (icon + name + remove button, or empty state)
 * - Pinned Save/Cancel bottom bar (keyboard- and nav-bar-aware)
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
    val cooldownSeconds by viewModel.cooldownSeconds.collectAsStateWithLifecycle()
    val reRemindEnabled by viewModel.reRemindEnabled.collectAsStateWithLifecycle()
    val reRemindMinutes by viewModel.reRemindMinutes.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val saveCompleted by viewModel.saveCompleted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }

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

    // Display name of the first selected app — used in the delete dialog message.
    val firstAppLabel = remember(selectedPackages, name) {
        selectedPackages.firstOrNull()?.let { pkg ->
            try {
                val info = context.packageManager.getApplicationInfo(pkg, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                pkg
            }
        } ?: name
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
                        IconButton(onClick = { showDeleteDialog = true }) {
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
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
            ) {
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Group Name ──
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.label_group_name)) },
                placeholder = { Text(stringResource(R.string.placeholder_group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (name.isBlank()) {
                Text(
                    text = stringResource(R.string.group_name_required_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Cooldown + Re-remind ──
            Spacer(modifier = Modifier.height(8.dp))

                // Cooldown time — unified slider + input component
                TimeSliderInput(
                    title = stringResource(R.string.cooldown_label),
                    value = cooldownSeconds,
                    unit = stringResource(R.string.cooldown_seconds_suffix),
                    minValue = 1,
                    maxValue = 60,
                    onValueChange = viewModel::updateCooldown,
                    rangeStartLabel = stringResource(R.string.cooldown_range_start),
                    rangeEndLabel = stringResource(R.string.cooldown_range_end)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Re-remind: Switch header + description
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.re_remind_label),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = reRemindEnabled,
                            onCheckedChange = viewModel::updateReRemindEnabled
                        )
                    }
                    Text(
                        text = stringResource(R.string.re_remind_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Interval input (visible only when switch is on) — no slider,
                // just a compact number input for precise manual entry.
                if (reRemindEnabled) {
                    TimeInputRow(
                        title = stringResource(R.string.re_remind_interval_label),
                        value = reRemindMinutes,
                        unit = stringResource(R.string.re_remind_unit),
                        minValue = 1,
                        maxValue = 60,
                        onValueChange = viewModel::updateReRemind
                    )
                } else {
                    Text(
                        text = stringResource(R.string.re_remind_disabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Apps in this group ──
            Text(
                text = stringResource(R.string.apps_in_group),
                style = MaterialTheme.typography.titleMedium
            )

            // Add Apps button
            val addAppsLabel = stringResource(R.string.cd_add_apps)
            Card(
                onClick = {
                    AppSelectScreen.cachedInitialPackages = selectedPackages.toList()
                    onNavigateToAppSelect()
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = addAppsLabel }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.add_apps),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.apps_selected,
                            selectedPackages.size,
                            selectedPackages.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // App list or empty state
            if (selectedPackages.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.no_apps_selected_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.no_apps_selected_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        selectedPackages.forEachIndexed { index, pkg ->
                            SelectedAppRow(
                                packageName = pkg,
                                onRemove = { viewModel.removePackage(pkg) }
                            )
                            if (index < selectedPackages.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacing so content isn't hidden behind the bottom bar
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Delete confirmation dialog ──
    if (showDeleteDialog) {
        val quantity = selectedPackages.size.coerceAtLeast(1)
        val otherCount = (selectedPackages.size - 1).coerceAtLeast(0)
        val message = pluralStringResource(
            R.plurals.delete_message_cooldown,
            quantity,
            firstAppLabel,
            otherCount
        )
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_group_title, name)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable time setting components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A compact input-only time setting row (no slider).
 * Used for "Re-remind interval" where the user prefers manual entry.
 *
 * Layout: [title]                    [ input ] [unit]
 *
 * Same validation logic as TimeSliderInput: clamp on focus loss / IME Done.
 */
@Composable
private fun TimeInputRow(
    title: String,
    value: Int,
    unit: String,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var textValue by remember { mutableStateOf(value.toString()) }
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isEditing) {
            textValue = value.toString()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = textValue,
            onValueChange = { newText ->
                val digits = newText.filter { it.isDigit() }.take(2)
                textValue = digits
                digits.toIntOrNull()?.let { parsed ->
                    onValueChange(parsed.coerceIn(minValue, maxValue))
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .width(64.dp)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        isEditing = true
                    } else if (isEditing) {
                        isEditing = false
                        val validated = textValue.toIntOrNull()
                            ?.coerceIn(minValue, maxValue) ?: value
                        textValue = validated.toString()
                        onValueChange(validated)
                    }
                }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A unified time-setting control used by both "Cooldown" and "Re-remind".
 *
 * Layout:
 *   Row 1: [title]                    [ input ] [unit]
 *   Row 2: [slider with circular thumb, continuous track]
 *   Row 3: [rangeStart]              [rangeEnd]
 *
 * Bidirectional sync:
 * - Dragging the slider updates the input text in real time.
 * - Typing in the input updates the slider position (clamped to range).
 * - Final validation (clamp + correct text) happens on focus loss or IME Done,
 *   NOT on every keystroke — so the user can type "10" without being
 *   interrupted after typing "1".
 */
@Composable
private fun TimeSliderInput(
    title: String,
    value: Int,
    unit: String,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    rangeStartLabel: String,
    rangeEndLabel: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    // Local text mirror — allows the user to type freely (including being
    // momentarily empty) without the slider fighting back on every keystroke.
    var textValue by remember { mutableStateOf(value.toString()) }
    var isEditing by remember { mutableStateOf(false) }

    // When the slider moves (or the value is loaded/clamped externally),
    // sync the text field — but only when the user isn't actively typing.
    LaunchedEffect(value) {
        if (!isEditing) {
            textValue = value.toString()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: title … [input] unit
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            // Compact number input — 64dp wide, centered digits.
            OutlinedTextField(
                value = textValue,
                onValueChange = { newText ->
                    // Digits only, max 2 characters (range is at most 1–60).
                    val digits = newText.filter { it.isDigit() }.take(2)
                    textValue = digits
                    // Update the slider immediately with the clamped value,
                    // but don't correct the text yet (user may be mid-input).
                    digits.toIntOrNull()?.let { parsed ->
                        onValueChange(parsed.coerceIn(minValue, maxValue))
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true,
                enabled = enabled,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .width(64.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            isEditing = true
                        } else if (isEditing) {
                            // Focus lost → final validation: clamp and correct text.
                            isEditing = false
                            val validated = textValue.toIntOrNull()
                                ?.coerceIn(minValue, maxValue) ?: value
                            textValue = validated.toString()
                            onValueChange(validated)
                        }
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Row 2: Slider — standard circular thumb, continuous track.
        // No `steps` parameter → smooth track, no dense tick dots.
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = minValue.toFloat()..maxValue.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = InactiveTrackColor,
                disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                disabledInactiveTrackColor = InactiveTrackColor.copy(alpha = 0.38f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Row 3: range endpoint labels
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = rangeStartLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = rangeEndLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Selected app row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One selected app: icon + name + remove (×) button.
 * The remove button's touch target is the full IconButton (≥48dp).
 */
@Composable
private fun SelectedAppRow(packageName: String, onRemove: () -> Unit) {
    val context = LocalContext.current

    val iconBitmap = remember(packageName) {
        try {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    val appName = remember(packageName) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = appName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = appName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = appName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_remove_app_named, appName),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
