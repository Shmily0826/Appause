package com.appause.android.ui.stats

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.appause.android.R
import com.appause.android.data.local.AppInterceptionCount

/**
 * List of the top 5 most-intercepted apps.
 *
 * Each row shows:
 * - App icon (loaded from PackageManager via Context, not stored in DB)
 * - App name (resolved from PackageManager)
 * - Interception count (right-aligned)
 *
 * Why load icons here (in Composable) instead of in ViewModel?
 * - PackageManager needs a Context, which ViewModels should never hold.
 * - Loading icons is a UI concern — it's fine to do it in the Composable.
 * - We use remember() so the icon is only loaded once per composition.
 */
@Composable
fun TopAppsList(
    topApps: List<AppInterceptionCount>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = context.packageManager

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (topApps.isEmpty()) {
            // Show a friendly message when there's no data yet
            Text(
                text = stringResource(R.string.stats_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            topApps.forEachIndexed { index, item ->
                // Resolve app name and icon from PackageManager.
                // remember() ensures we don't re-query on every recomposition.
                val appName = remember(item.packageName) {
                    try {
                        pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0)).toString()
                    } catch (e: Exception) {
                        item.packageName
                    }
                }
                val appIcon: Drawable? = remember(item.packageName) {
                    try {
                        pm.getApplicationIcon(item.packageName)
                    } catch (e: Exception) {
                        null
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rank number
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(20.dp)
                    )

                    // App icon
                    if (appIcon != null) {
                        val bitmap = remember(appIcon) {
                            appIcon.toBitmap(width = 64, height = 64).asImageBitmap()
                        }
                        Image(
                            bitmap = bitmap,
                            contentDescription = appName,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // App name (takes remaining space)
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )

                    // Interception count
                    Text(
                        text = "${item.interceptionCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
