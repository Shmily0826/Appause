package com.appause.android.ui.stats

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.appause.android.data.local.DailyStats

/**
 * Weekly bar chart drawn entirely with Compose Canvas.
 *
 * Shows up to 7 days of data as paired bars:
 * - Left bar (primary color) = proceeded count
 * - Right bar (tertiary color) = cancelled count
 *
 * Bars animate from zero height to their target height when the chart
 * first appears, using animateFloatAsState with a spring-like tween.
 *
 * Why Canvas instead of a chart library?
 * - Keeps the APK small (no extra dependency).
 * - Full control over colors, animations, and Material 3 theming.
 * - Simple enough for a 7-day chart.
 */
@Composable
fun WeeklyBarChart(
    data: List<DailyStats>,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary
) {
    // Animation trigger: start at 0, animate to 1 when data appears.
    // We multiply bar heights by this fraction so they grow from the bottom.
    var animationTriggered by remember { mutableStateOf(false) }
    val animatedFraction by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "bar_chart_animation"
    )

    LaunchedEffect(data) {
        if (data.isNotEmpty()) {
            animationTriggered = true
        }
    }

    val density = LocalDensity.current
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            if (data.isEmpty()) return@Canvas

            // Find the maximum value to scale bars proportionally.
            val maxValue = data.maxOf { maxOf(it.proceeded, it.cancelled) }.coerceAtLeast(1)

            // Chart area dimensions
            val chartWidth = size.width
            val chartHeight = size.height
            val bottomPadding = with(density) { 24.dp.toPx() } // space for date labels
            val drawableHeight = chartHeight - bottomPadding

            // Each day gets an equal slice of the horizontal space.
            val dayWidth = chartWidth / data.size
            val barWidth = dayWidth * 0.3f // each bar is 30% of the day slot
            val barGap = dayWidth * 0.05f // small gap between the two bars

            data.forEachIndexed { index, stats ->
                val centerX = dayWidth * index + dayWidth / 2f

                // Proceeded bar (left, primary color)
                val proceededHeight = (stats.proceeded.toFloat() / maxValue) * drawableHeight * animatedFraction
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(
                        x = centerX - barWidth - barGap / 2f,
                        y = drawableHeight - proceededHeight
                    ),
                    size = Size(barWidth, proceededHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Cancelled bar (right, tertiary color)
                val cancelledHeight = (stats.cancelled.toFloat() / maxValue) * drawableHeight * animatedFraction
                drawRoundRect(
                    color = tertiaryColor,
                    topLeft = Offset(
                        x = centerX + barGap / 2f,
                        y = drawableHeight - cancelledHeight
                    ),
                    size = Size(barWidth, cancelledHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Date label below the bars (e.g., "18")
                // Canvas text needs nativeCanvas + Paint — Compose drawText is limited.
                val dayLabel = stats.day.substringAfterLast("-") // "2026-07-18" → "18"
                drawText(
                    text = dayLabel,
                    x = centerX,
                    y = chartHeight,
                    textColor = textColor,
                    density = density
                )
            }
        }

        // Legend row: two colored dots + labels
        BarChartLegend(primaryColor = primaryColor, tertiaryColor = tertiaryColor)
    }
}

/**
 * Draws centered text using Android's native Paint.
 * Compose's drawText doesn't support text alignment, so we use
 * the native Canvas API to center-align the date labels.
 */
private fun DrawScope.drawText(
    text: String,
    x: Float,
    y: Float,
    textColor: Int,
    density: androidx.compose.ui.unit.Density
) {
    val paint = Paint().apply {
        color = textColor
        textSize = with(density) { 11.dp.toPx() }
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(text, x, y, paint)
    }
}

/**
 * Small legend showing what each bar color means.
 * Placed below the chart for clarity.
 */
@Composable
private fun BarChartLegend(
    primaryColor: Color,
    tertiaryColor: Color,
    modifier: Modifier = Modifier
) {
    // Using Material3 components for the legend
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Proceeded dot
        Canvas(modifier = Modifier.height(8.dp).then(Modifier.fillMaxWidth(0.02f))) {
            drawCircle(color = primaryColor, radius = size.minDimension / 2f)
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.fillMaxWidth(0.02f))
        androidx.compose.material3.Text(
            text = androidx.compose.ui.res.stringResource(com.appause.android.R.string.stat_waited),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.fillMaxWidth(0.04f))
        // Cancelled dot
        Canvas(modifier = Modifier.height(8.dp).then(Modifier.fillMaxWidth(0.02f))) {
            drawCircle(color = tertiaryColor, radius = size.minDimension / 2f)
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.fillMaxWidth(0.02f))
        androidx.compose.material3.Text(
            text = androidx.compose.ui.res.stringResource(com.appause.android.R.string.stat_cancelled),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
