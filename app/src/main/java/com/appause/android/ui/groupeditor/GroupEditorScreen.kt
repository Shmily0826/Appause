package com.appause.android.ui.groupeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
 * Layout (top level keeps only the essentials):
 * - Top bar with back button, title, and delete button (edit mode only)
 * - Group name text field
 * - Cooldown time setting (slider + number input, bidirectionally synced)
 * - Re-remind — collapsed Card by default (everyone). Expanding it reveals the
 *   enable switch, interval slider, re-remind cooldown, and a "More options"
 *   sub-card holding Repeat + Escalate.
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
    onNavigateToPro: () -> Unit,
    viewModel: GroupEditorViewModel = viewModel()
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    // Local mirror so the cursor stays put while typing. updateName persists to
    // the ViewModel state asynchronously-ish; binding `value = name` directly
    // would briefly revert the text each keystroke and jump the cursor to the
    // start (same bug as the Settings search/name fields elsewhere).
    var nameLocal by remember { mutableStateOf(name) }
    LaunchedEffect(name) {
        if (name != nameLocal) nameLocal = name
    }
    // Name error is shown only after the user interacts (types then clears) or
    // taps Save with an empty name — not on first open (Section 5).
    var showNameError by remember { mutableStateOf(false) }
    var nameHadTyped by remember { mutableStateOf(false) }
    val cooldownSeconds by viewModel.cooldownSeconds.collectAsStateWithLifecycle()
    val reRemindEnabled by viewModel.reRemindEnabled.collectAsStateWithLifecycle()
    val reRemindMinutes by viewModel.reRemindMinutes.collectAsStateWithLifecycle()
    val reRemindCooldownSeconds by viewModel.reRemindCooldownSeconds.collectAsStateWithLifecycle()
    val reRemindRepeat by viewModel.reRemindRepeat.collectAsStateWithLifecycle()
    val reRemindEscalate by viewModel.reRemindEscalate.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val saveCompleted by viewModel.saveCompleted.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val maxCooldown by viewModel.maxCooldown.collectAsStateWithLifecycle()
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
                        onClick = {
                            // Validate on click so an empty name shows the inline
                            // error (Section 5) instead of silently doing nothing.
                            // The ViewModel still refuses to persist an empty name.
                            if (nameLocal.isBlank()) {
                                showNameError = true
                            } else {
                                viewModel.save()
                            }
                        },
                        enabled = true
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
                value = nameLocal,
                onValueChange = {
                    nameLocal = it
                    viewModel.updateName(it)
                    if (it.isNotBlank()) nameHadTyped = true
                    // Error only after the user has typed something and then
                    // cleared it — not on the initial empty state.
                    showNameError = nameHadTyped && it.isBlank()
                },
                label = { Text(stringResource(R.string.label_group_name)) },
                placeholder = { Text(stringResource(R.string.placeholder_group_name)) },
                singleLine = true,
                isError = showNameError,
                supportingText = if (showNameError) {
                    { Text(stringResource(R.string.group_name_required_hint)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Cooldown (top-level, always visible) ──
            Spacer(modifier = Modifier.height(8.dp))

            // Cooldown time — unified slider + input component.
            // The maximum is gated by Pro: free users top out at 30s,
            // Pro users can go up to 60s.
            val cooldownUnit = stringResource(R.string.cooldown_seconds_suffix)
            TimeSliderInput(
                title = stringResource(R.string.cooldown_label),
                value = cooldownSeconds.coerceAtMost(maxCooldown),
                unit = cooldownUnit,
                minValue = 1,
                maxValue = maxCooldown,
                onValueChange = viewModel::updateCooldown,
                rangeStartLabel = "1$cooldownUnit",
                rangeEndLabel = "$maxCooldown$cooldownUnit"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Re-remind (collapsed Card by default, for everyone) ──
            // Free users see a Pro badge in the header; expanding reveals the
            // upsell. Pro users get the full controls inside.
            // Collapsed-header summary so the state is visible without expanding.
            val reRemindSummary = when {
                !isPro -> stringResource(R.string.re_remind_summary_locked)
                !reRemindEnabled -> stringResource(R.string.re_remind_summary_off)
                else -> stringResource(R.string.re_remind_summary_on, reRemindMinutes)
            }
            CollapsibleCard(
                title = stringResource(R.string.re_remind_label),
                description = stringResource(R.string.re_remind_desc),
                badge = if (!isPro) stringResource(R.string.pro_badge) else null,
                collapsedSummary = reRemindSummary
            ) {
                if (isPro) {
                    // Enable switch — the card title already says "Re-remind".
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.re_remind_enable),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = reRemindEnabled,
                            onCheckedChange = viewModel::updateReRemindEnabled
                        )
                    }

                    if (reRemindEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Interval — slider (replaces the old number-only input).
                        val reUnit = stringResource(R.string.re_remind_unit)
                        TimeSliderInput(
                            title = stringResource(R.string.re_remind_interval_label),
                            value = reRemindMinutes,
                            unit = reUnit,
                            minValue = 1,
                            maxValue = 60,
                            onValueChange = viewModel::updateReRemind,
                            rangeStartLabel = stringResource(R.string.re_remind_range_start),
                            rangeEndLabel = stringResource(R.string.re_remind_range_end)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Re-remind cooldown length. The data model stores 0 to
                        // mean "same as first cooldown", but we never surface that
                        // 0 in the UI. Instead we expose an explicit switch:
                        //  ON  -> stored value is 0; slider is disabled and shows
                        //         the first cooldown as a reference.
                        //  OFF -> slider edits a real 1..max seconds value.
                        val reCooldownUnit = stringResource(R.string.label_seconds)
                        val sameAsFirst = reRemindCooldownSeconds <= 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.re_remind_cooldown_reuse),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = sameAsFirst,
                                onCheckedChange = { on ->
                                    if (on) viewModel.updateReRemindCooldown(0)
                                    else viewModel.updateReRemindCooldown(
                                        if (reRemindCooldownSeconds > 0) reRemindCooldownSeconds
                                        else cooldownSeconds.coerceIn(1, 300)
                                    )
                                }
                            )
                        }
                        if (sameAsFirst) {
                            Text(
                                text = stringResource(R.string.re_remind_sameasfirst_caption, cooldownSeconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TimeSliderInput(
                            title = stringResource(R.string.re_remind_cooldown_label),
                            value = if (sameAsFirst) cooldownSeconds else reRemindCooldownSeconds,
                            unit = reCooldownUnit,
                            minValue = 1,
                            maxValue = maxCooldown.coerceAtLeast(60),
                            onValueChange = viewModel::updateReRemindCooldown,
                            enabled = !sameAsFirst,
                            rangeStartLabel = "1$reCooldownUnit",
                            rangeEndLabel = "${maxCooldown.coerceAtLeast(60)}$reCooldownUnit"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Repeat + Escalate live inside "More options".
                        CollapsibleCard(
                            title = stringResource(R.string.re_remind_more_options),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            border = null
                        ) {
                            // "Repeat" switch — fire every interval (on) or only once (off).
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.re_remind_repeat_label),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = reRemindRepeat,
                                        onCheckedChange = viewModel::updateReRemindRepeat
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.re_remind_repeat_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // "Escalate" switch — each successive pop lasts longer (base × N).
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.re_remind_escalate_label),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = reRemindEscalate,
                                        onCheckedChange = viewModel::updateReRemindEscalate
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.re_remind_escalate_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.re_remind_disabled_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } else {
                    // Locked upsell — a single, clearly-clickable upgrade action.
                    // The PRO badge and the header expand/collapse stay intact.
                    Button(
                        onClick = onNavigateToPro,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.re_remind_pro_unlock))
                    }
                }
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
                    Spacer(modifier = Modifier.width(12.dp))
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
 * A Card whose header row toggles an expandable body.
 *
 * Only the header is clickable, so interactive controls inside the body
 * (switches, sliders) never accidentally collapse the card. The body is
 * collapsed by default unless [defaultExpanded] is set.
 */
@Composable
private fun CollapsibleCard(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    badge: String? = null,
    collapsedSummary: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    // When collapsed, prefer the dynamic summary (if provided);
                    // otherwise fall back to the static description.
                    val subtitle = if (!expanded && collapsedSummary != null) collapsedSummary else description
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.cd_collapse_section else R.string.cd_expand_section
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
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
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    unit: String,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    rangeStartLabel: String,
    rangeEndLabel: String,
    enabled: Boolean = true
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Row 1: title (left) … [input] unit (right)
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

        // Row 2 + 3: Slider and range labels, inset from the screen edge so the
        // track (and the min/max thumb positions) clear the Android edge-gesture
        // zones. The labels get an extra inset equal to the slider thumb radius
        // so they line up with the visual track ends. The standard M3 Slider
        // already draws one continuous track (active → thumb → inactive) with a
        // filled circular thumb — no custom boxes/canvas needed.
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "$title: $value $unit"
                    }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = rangeStartLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = rangeEndLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
