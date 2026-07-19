package com.appause.android.ui.appselect

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.R

/**
 * App Select Screen — pick apps to include in a group.
 *
 * Shows all launchable apps on the device with checkboxes.
 * Supports search and multi-select.
 *
 * "Recommended" section:
 * - Shows apps the user has already added to other groups.
 * - These are quick-pick suggestions — tapping a chip toggles selection.
 * - Helps users quickly re-add apps they've previously categorised
 *   (e.g., learning apps) to new groups.
 *
 * Data passing:
 * - When user confirms selection, results are cached in [cachedSelectedPackages].
 * - The GroupEditorScreen reads this cache when this screen pops.
 * - This is a simple pattern that avoids complex navigation argument passing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(
    onNavigateBack: () -> Unit,
    initialPackages: List<String> = emptyList(),
    viewModel: AppSelectViewModel = viewModel()
) {
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val recommendedApps by viewModel.recommendedApps.collectAsStateWithLifecycle()

    // Pre-select apps that are already in the group being edited
    // Reads from both the parameter AND the companion cache
    LaunchedEffect(initialPackages) {
        val packages = if (initialPackages.isNotEmpty()) {
            initialPackages.toSet()
        } else {
            AppSelectScreen.cachedInitialPackages?.toSet().also {
                AppSelectScreen.cachedInitialPackages = null
            } ?: emptySet()
        }
        if (packages.isNotEmpty()) {
            viewModel.preSelectPackages(packages)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_select_apps)) },
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
        },
        bottomBar = {
            // Confirm button at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.selected_count, selectedPackages.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        // Cache the selection and go back
                        AppSelectScreen.cachedSelectedPackages = selectedPackages.toList()
                        onNavigateBack()
                    }
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_confirm))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Search Bar ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Recommended Apps (quick-pick from existing groups) ──
            // Only show when there are recommended apps and no active search
            if (recommendedApps.isNotEmpty() && searchQuery.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.recommended_apps),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Horizontal scrollable row of chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(recommendedApps, key = { it.packageName }) { app ->
                            val isSelected = selectedPackages.contains(app.packageName)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleSelection(app.packageName) },
                                label = { Text(app.appName) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ── App List ──
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        // Load icon from PackageManager — cached by remember per item
                        val context = LocalContext.current
                        val iconBitmap = remember(app.packageName) {
                            try {
                                context.packageManager
                                    .getApplicationIcon(app.packageName)
                                    .toBitmap(width = 96, height = 96)
                                    .asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    viewModel.toggleSelection(app.packageName)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // App icon
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = app.appName,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Empty state
                    if (filteredApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank())
                                        stringResource(R.string.no_apps_match, searchQuery)
                                    else
                                        stringResource(R.string.no_launchable_apps),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Bottom spacing for bottom bar
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }
}

/**
 * Companion object for passing data between GroupEditorScreen and AppSelectScreen.
 * - cachedSelectedPackages: set when user taps Confirm, read by GroupEditorScreen on return.
 * - cachedInitialPackages: set by GroupEditorScreen before navigating here, read on init.
 */
object AppSelectScreen {
    var cachedSelectedPackages: List<String>? = null
    var cachedInitialPackages: List<String>? = null
}
