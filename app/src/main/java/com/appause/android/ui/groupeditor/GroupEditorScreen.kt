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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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
 * - Group type selector (Cooldown vs Recommended)
 * - Cooldown header with inline value input + continuous slider (pause only)
 * - "Add apps" list item (navigates to App Select screen)
 * - "Apps in this group" section (icon + name + remove button, or empty state)
 * - Pinned Save/Cancel bottom bar (keyboard- and nav-bar-aware)
 *
 * Data flow:
 * 1. User enters name and cooldown → stored in ViewModel state
 * 2. User taps "Add apps" → navigates to AppSelectScreen
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
    val context = LocalContext.current

    // Delete confirmation dialog visibility. The group is only removed after
    // the user explicitly confirms — never on a single tap of the trash icon.
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Local text mirror of the cooldown value so the input field can be
    // edited freely (including being momentarily empty) while staying in
    // sync with the slider in both directions.
    var cooldownText by remember { mutableStateOf(cooldownSeconds.toString()) }
    LaunchedEffect(cooldownSeconds) {
        // Slider moved (or value was clamped) → update the text field,
        // unless it already shows the same number.
        if (cooldownText.toIntOrNull() != cooldownSeconds) {
            cooldownText = cooldownSeconds.toString()
        }
    }

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

    // Display name of the first selected app — used in the delete dialog
    // message ("YouTube will no longer use this cooldown rule.").
    // Falls back to the group name when the group has no apps yet.
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
            // Pinned Save/Cancel bar — always visible even when the form is long.
            // imePadding() lifts the bar above the soft keyboard so Save is never
            // covered while typing; navigationBarsPadding() keeps it clear of the
            // gesture bar / 3-button bar when the keyboard is hidden.
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
            // Explain why the Save button is greyed out until a name is entered.
            if (name.isBlank()) {
                Text(
                    text = stringResource(R.string.group_name_required_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Group Type ──
            Text(
                text = stringResource(R.string.group_type_label),
                style = MaterialTheme.typography.titleMedium
            )
            // IntrinsicSize.Max makes both cards share the same height even if
            // one description wraps to more lines than the other.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TypeOptionCard(
                    title = stringResource(R.string.group_type_pause),
                    description = stringResource(R.string.group_type_pause_desc),
                    selected = type == AppGroup.TYPE_PAUSE,
                    onClick = { viewModel.updateType(AppGroup.TYPE_PAUSE) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                TypeOptionCard(
                    title = stringResource(R.string.group_type_learning),
                    description = stringResource(R.string.group_type_learning_desc),
                    selected = type == AppGroup.TYPE_LEARNING,
                    onClick = { viewModel.updateType(AppGroup.TYPE_LEARNING) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            // ── Cooldown (pause groups only — recommended groups are never blocked) ──
            if (type == AppGroup.TYPE_PAUSE) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Header row: "Cooldown" title … [ 10 ] sec
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.cooldown_label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Compact value input, kept in sync with the slider below.
                        OutlinedTextField(
                            value = cooldownText,
                            onValueChange = { value ->
                                // Digits only, max 2 chars (valid range is 1–60).
                                val digits = value.filter { it.isDigit() }.take(2)
                                cooldownText = digits
                                digits.toIntOrNull()?.let { entered ->
                                    // Out-of-range input is clamped immediately;
                                    // the LaunchedEffect above writes the clamped
                                    // value back into the field.
                                    viewModel.updateCooldown(entered.coerceIn(1, 60))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                            modifier = Modifier.width(72.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.cooldown_seconds_suffix),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Continuous slider — no `steps` parameter, so Compose draws a
                    // smooth track with a single round thumb instead of the dense
                    // row of tick dots the old `steps = 58` produced.
                    Slider(
                        value = cooldownSeconds.toFloat(),
                        onValueChange = { viewModel.updateCooldown(it.toInt()) },
                        valueRange = 1f..60f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Range hints under the slider ends.
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.cooldown_range_start),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.cooldown_range_end),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Add Apps (standard list-item entry) ──
            // Full-width tappable row: [+ icon] Add apps …… N apps selected >
            // Resolve the label outside the semantics lambda — stringResource()
            // needs a @Composable context and semantics {} is not one.
            val addAppsLabel = stringResource(R.string.cd_add_apps)
            Card(
                onClick = {
                    // Cache current selection so AppSelectScreen can pre-check them
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

            // ── Selected Apps — independent section title + list (or empty state) ──
            Text(
                text = stringResource(R.string.apps_in_group),
                style = MaterialTheme.typography.titleMedium
            )
            if (selectedPackages.isEmpty()) {
                // Clear empty state instead of hiding the section entirely.
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
                            // Divider between rows (not after the last one).
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
        }
    }

    // ── Delete confirmation dialog ──
    if (showDeleteDialog) {
        // Quantity drives the plural form; when the group has no apps we still
        // use the singular phrasing with the group name as the subject.
        val quantity = selectedPackages.size.coerceAtLeast(1)
        val otherCount = (selectedPackages.size - 1).coerceAtLeast(0)
        val message = pluralStringResource(
            if (type == AppGroup.TYPE_LEARNING) R.plurals.delete_message_recommended
            else R.plurals.delete_message_cooldown,
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

/**
 * A selectable card for choosing the group type (Cooldown vs Recommended).
 *
 * Selected state is conveyed three ways (never by color alone):
 * a light primary-container background, a 2dp primary border, and a check
 * icon pinned to the top-right corner. Unselected cards are white with a
 * light grey outline.
 *
 * Both cards are given equal height by the parent Row (IntrinsicSize.Max +
 * fillMaxHeight), and the description uses the normal bodySmall size — no
 * extra shrinking, so text scales properly with the system font size.
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
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
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
            // Check icon pinned to the top-right corner. It always occupies its
            // space (transparent when unselected) so the layout doesn't shift.
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(18.dp)
            )
        }
    }
}

/**
 * One selected app shown as: icon + name + a remove (×) button.
 *
 * The icon and display name are resolved from PackageManager on demand and
 * cached with remember — we never store Drawables in the ViewModel.
 * The remove button's accessibility label includes the app name
 * (e.g. "Remove YouTube") so TalkBack users know exactly what they're removing.
 */
@Composable
private fun SelectedAppRow(packageName: String, onRemove: () -> Unit) {
    val context = LocalContext.current

    // Load the app icon on demand (cached per package name).
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

    // Resolve the user-visible app name; fall back to the raw package name.
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
            // Placeholder when the icon can't be loaded (e.g. app was uninstalled).
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
        // IconButton provides a ≥48dp touch target by default.
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_remove_app_named, appName),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
