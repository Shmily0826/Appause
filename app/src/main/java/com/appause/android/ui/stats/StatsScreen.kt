package com.appause.android.ui.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.R
import com.appause.android.data.local.ReasonCount

/**
 * Statistics screen — shows historical interception data and charts.
 *
 * Layout (top to bottom):
 * 1. Weekly bar chart — 7 days of proceeded vs cancelled
 * 2. Donut chart + summary — overall ratio for 7 days
 * 3. Top apps list — top 5 most-intercepted apps
 *
 * All charts are drawn with pure Compose Canvas (no chart library).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = viewModel()
) {
    val dailyStats by viewModel.dailyStats.collectAsStateWithLifecycle()
    val topApps by viewModel.topApps.collectAsStateWithLifecycle()
    val totalRatio by viewModel.totalRatio.collectAsStateWithLifecycle()
    val reasonCounts by viewModel.reasonCounts.collectAsStateWithLifecycle()
    val reasonLabels by viewModel.reasonLabels.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Section 1: Weekly Bar Chart ──
        Card(
            modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.stats_weekly),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    WeeklyBarChart(
                        data = dailyStats,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Section 2: Donut Chart + Summary ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.stats_overview),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        DonutChart(
                            proceeded = totalRatio.proceeded,
                            cancelled = totalRatio.cancelled
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        Column {
                            // Proceeded count
                            StatRow(
                                label = stringResource(R.string.stat_waited),
                                value = totalRatio.proceeded,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Cancelled count
                            StatRow(
                                label = stringResource(R.string.stat_cancelled),
                                value = totalRatio.cancelled,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // ── Section 3: Top Apps ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.stats_top_apps),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TopAppsList(topApps = topApps)
                }
            }

            // ── Section 4: Reason Breakdown ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.stats_reason_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ReasonList(reasonCounts = reasonCounts, customLabels = reasonLabels)
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * A single row in the summary section: colored dot + label + value.
 */
@Composable
private fun StatRow(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Colored dot indicator
        androidx.compose.foundation.Canvas(
            modifier = Modifier.height(8.dp).then(Modifier.width(8.dp))
        ) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * "Reason breakdown" list — shows how many times each open-reason was picked.
 *
 * Each row shows the reason label and its count, plus a thin bar whose width
 * is proportional to that reason's share of the most-picked reason.
 */
@Composable
private fun ReasonList(
    reasonCounts: List<ReasonCount>,
    customLabels: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (reasonCounts.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            val maxCount = reasonCounts.maxOf { it.count }.coerceAtLeast(1)
            reasonCounts.forEach { item ->
                // Reason keys are stored in English (work/bored/messages/other).
                // The default display label must follow the CURRENT UI language,
                // so we resolve it with stringResource (the Activity context).
                // Do NOT take it from the ViewModel: its Application context keeps
                // the locale from process start and is never updated after an
                // in-app language switch (the "restart" only re-creates the
                // Activity, not the Application), so it would stay in the old
                // language. Pro custom labels (non-blank) always pass through.
                val label = customLabels[item.reason]?.takeIf { it.isNotBlank() }
                    ?: reasonDefaultLabel(item.reason)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${item.count}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Bar width = this reason's count relative to the top reason.
                    val fraction = item.count.toFloat() / maxCount
                    ReasonBar(fraction = fraction)
                }
            }
        }
    }
}

/**
 * A simple continuous rounded bar for the reason breakdown.
 *
 * Material3's LinearProgressIndicator draws a separate rounded cap at the
 * end of the progress track. When the track is transparent, that cap looks
 * like a floating dot under the count number for small fractions. This bar
 * draws the filled portion as one continuous rounded rectangle anchored to
 * the left edge, so there is no detached dot.
 */
@Composable
private fun ReasonBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val fillColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        val barHeight = size.height
        val radius = barHeight / 2f
        val filledWidth = (size.width * fraction.coerceIn(0f, 1f))
            .coerceAtLeast(barHeight) // keep tiny values as a small pill, not a dot

        // Full-width background track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(radius, radius)
        )

        // Filled portion anchored to the left
        drawRoundRect(
            color = fillColor,
            topLeft = Offset.Zero,
            size = Size(filledWidth, barHeight),
            cornerRadius = CornerRadius(radius, radius)
        )
    }
}

/**
 * Maps a stored reason key to its localized default label using the current
 * UI language (Activity context). Kept separate so the language switch works
 * even though the Application context is not recreated on an in-app restart.
 */
@Composable
private fun reasonDefaultLabel(reason: String): String {
    return when (reason) {
        "work" -> stringResource(R.string.intent_work)
        "bored" -> stringResource(R.string.intent_bored)
        "messages" -> stringResource(R.string.intent_messages)
        else -> stringResource(R.string.intent_other)
    }
}
