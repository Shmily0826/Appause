package com.appause.android.ui.pause

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom countdown ring drawn with Compose Canvas.
 *
 * Why a custom ring instead of CircularProgressIndicator?
 * - We can smoothly blend colors as progress advances (primary → tertiary).
 * - We can add a pulsing glow effect behind the ring.
 * - The progress is a continuous float, so the ring moves smoothly
 *   instead of "jumping" once per second.
 *
 * Drawing layers (bottom to top):
 * 1. Pulsing glow — a soft, breathing circle behind the ring.
 * 2. Track — a faint full circle showing the full path.
 * 3. Progress arc — the colored arc showing elapsed time.
 *
 * @param progress 0.0 to 1.0, how much of the countdown has elapsed.
 * @param isFinished Whether the countdown has reached zero.
 * @param size The diameter of the ring.
 * @param strokeWidth The thickness of the ring line.
 */
@Composable
fun CountdownRing(
    progress: Float,
    isFinished: Boolean,
    size: Dp = 140.dp,
    strokeWidth: Dp = 6.dp
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    // Pulsing glow — oscillates between small and large using an infinite transition.
    // Why? It gives a subtle "breathing" feeling while the user waits,
    // making the countdown feel alive rather than static.
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    // Blend the ring color from primary (start) to tertiary (almost done).
    // This gives a visual cue that time is running out.
    val ringColor = if (isFinished) tertiary else lerp(primary, tertiary, progress)

    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }

    Canvas(modifier = Modifier.size(size)) {
        val canvasSize = this.size.minDimension
        val radius = (canvasSize - strokePx) / 2f
        val center = Offset(canvasSize / 2f, canvasSize / 2f)
        val arcSize = Size(radius * 2, radius * 2)
        val topLeft = Offset((canvasSize - radius * 2) / 2f, (canvasSize - radius * 2) / 2f)

        // Layer 1: Pulsing glow (only while countdown is active)
        if (!isFinished) {
            drawCircle(
                color = primary.copy(alpha = glowAlpha),
                radius = radius * glowScale + strokePx,
                center = center
            )
        }

        // Layer 2: Track — a faint full circle as background
        drawArc(
            color = surfaceVariant.copy(alpha = 0.3f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Layer 3: Progress arc — shows elapsed time with blended color
        drawArc(
            color = ringColor,
            startAngle = -90f,  // Start from top (12 o'clock)
            sweepAngle = progress * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}
