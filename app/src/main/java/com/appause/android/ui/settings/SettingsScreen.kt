package com.appause.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.R

/**
 * Settings hub — a category list that drills into sub-screens.
 *
 * Why a hub + sub-screens (a "secondary menu") instead of one long scroll?
 * The old single-page Settings crammed language, theme, four permission
 * toggles, Pro editors and debug info into one column, which was hard to scan.
 * Grouping into categories gives each topic its own screen with room to breathe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPause: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPro: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onNavigateToDiagnostics: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()

    // Order matters: the most-used items first.
    val categories = listOf(
        SettingsCategory(
            icon = Icons.Default.Palette,
            title = R.string.settings_category_appearance,
            subtitle = R.string.settings_category_appearance_desc,
            onClick = onNavigateToAppearance
        ),
        SettingsCategory(
            icon = Icons.Default.Shield,
            title = R.string.settings_category_permissions,
            subtitle = R.string.settings_category_permissions_desc,
            onClick = onNavigateToPermissions
        ),
        SettingsCategory(
            icon = Icons.Default.Timer,
            title = R.string.settings_category_pause,
            subtitle = R.string.settings_category_pause_desc,
            onClick = onNavigateToPause
        ),
        SettingsCategory(
            icon = Icons.Default.Feedback,
            title = R.string.settings_category_feedback,
            subtitle = null,
            onClick = onNavigateToFeedback
        ),
        SettingsCategory(
            icon = Icons.Default.Info,
            title = R.string.settings_category_about,
            subtitle = R.string.settings_category_about_desc,
            onClick = onNavigateToAbout
        )
    )

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { ProBanner(isPro = isPro, onNavigateToPro = onNavigateToPro) }
            items(categories, key = { it.title }) { category ->
                CategoryItem(category = category)
            }
            addDiagnosticsEntry(onNavigateToDiagnostics)
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}


private data class SettingsCategory(
    val icon: ImageVector,
    val title: Int,
    val subtitle: Int?,
    val badge: Int? = null,
    val onClick: () -> Unit
)

@Composable
private fun CategoryItem(category: SettingsCategory) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = category.onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.title),
                    style = MaterialTheme.typography.titleMedium
                )
                if (category.subtitle != null) {
                    Text(
                        text = stringResource(category.subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (category.badge != null) {
                Text(
                    text = stringResource(category.badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Pro promo / status bar shown at the top of Settings — kept out of the category
 * list so Pro doesn't look like just another setting. Tapping it (when free)
 * opens the Pro screen; when already Pro it shows a calm "active" note.
 */
@Composable
private fun ProBanner(
    isPro: Boolean,
    onNavigateToPro: () -> Unit
) {
    if (isPro) {
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
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.pro_active_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToPro),
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
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pro_banner_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.pro_banner_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
