package com.appause.android.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Donut chart drawn with Compose Canvas.
 *
 * Shows the ratio between proceeded and cancelled interceptions
 * as two arcs in a ring. A white circle in the center creates
 * the "donut hole" effect.
 *
 * The total count is displayed in the center of the donut.
 *
 * @param proceeded Number of proceeded interceptions.
 * @param cancelled Number of cancelled interceptions.
 */
@Composable
fun DonutChart(
    proceeded: Int,
    cancelled: Int,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary
) {
    val total = proceeded + cancelled

    // Calculate sweep angles for each segment.
    // A full circle is 360 degrees. Each segment gets a proportion of that.
    val proceededFraction = if (total > 0) proceeded.toFloat() / total else 0f
    val cancelledFraction = if (total > 0) cancelled.toFloat() / total else 0f

    // Animate the arcs from 0 to their target sweep angle.
    var animationTriggered by remember { mutableStateOf(false) }
    val animatedProceededSweep by animateFloatAsState(
        targetValue = if (animationTriggered) proceededFraction * 360f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "donut_proceeded"
    )
    val animatedCancelledSweep by animateFloatAsState(
        targetValue = if (animationTriggered) cancelledFraction * 360f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "donut_cancelled"
    )

    LaunchedEffect(total) {
        if (total > 0) {
            animationTriggered = true
        }
    }

    Box(
        modifier = modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val strokeWidth = 20.dp.toPx()
            val arcSize = size.minDimension - strokeWidth

            // Proceeded arc (starts at top = -90 degrees)
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = animatedProceededSweep,
                useCenter = false,
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f),
                style = Stroke(width = strokeWidth)
            )

            // Cancelled arc (starts where proceeded ends)
            drawArc(
                color = tertiaryColor,
                startAngle = -90f + animatedProceededSweep,
                sweepAngle = animatedCancelledSweep,
                useCenter = false,
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f),
                style = Stroke(width = strokeWidth)
            )
        }

        // Center text showing total count
        Text(
            text = "$total",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
