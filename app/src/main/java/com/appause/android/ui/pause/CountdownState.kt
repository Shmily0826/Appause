package com.appause.android.ui.pause

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Shared countdown state used by both PauseActivity and OverlayManager.
 *
 * Why extract this?
 * - The timer logic was duplicated in two places (Activity + overlay).
 * - This helper provides a smooth progress value (updated ~60fps) instead
 *   of a stepped value that only changes once per second.
 * - The smooth progress makes the progress ring animation fluid.
 */
data class CountdownState(
    /** Whole seconds remaining (decrements once per second). */
    val secondsLeft: Int,
    /** Smooth 0.0-to-1.0 progress, updated every frame (~60fps). */
    val smoothProgress: Float,
    /** Whether the countdown has reached zero. */
    val isFinished: Boolean
)

/**
 * Remember and manage a countdown timer.
 *
 * How it works:
 * - `secondsLeft` is a whole number that decrements once per second.
 * - `smoothProgress` is a continuous float (0.0 to 1.0) updated every ~16ms.
 *   This gives the progress ring a smooth animation instead of "jumping" each second.
 * - When `secondsLeft` reaches 0, `onFinished` is called once.
 *
 * @param totalSeconds The countdown duration in seconds.
 * @param onFinished Called once when the timer reaches zero.
 */
@Composable
fun rememberCountdownState(
    totalSeconds: Int,
    onFinished: () -> Unit
): CountdownState {
    var secondsLeft by remember(totalSeconds) { mutableIntStateOf(totalSeconds) }
    var smoothProgress by remember(totalSeconds) { mutableFloatStateOf(0f) }
    var finishedCalled by remember(totalSeconds) { mutableStateOf(false) }

    val isFinished = secondsLeft <= 0

    LaunchedEffect(totalSeconds) {
        val startTime = System.currentTimeMillis()
        val totalMillis = totalSeconds * 1000L

        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = totalMillis - elapsed

            if (remaining <= 0) {
                secondsLeft = 0
                smoothProgress = 1f
                if (!finishedCalled) {
                    finishedCalled = true
                    onFinished()
                }
                break
            }

            // Update whole seconds (for the displayed number)
            secondsLeft = ((remaining + 999) / 1000).toInt() // round up so "10" shows for 9.5s remaining

            // Update smooth progress (for the ring animation)
            smoothProgress = (elapsed.toFloat() / totalMillis).coerceIn(0f, 1f)

            // Sleep ~16ms for ~60fps updates
            delay(16L)
        }
    }

    return CountdownState(
        secondsLeft = secondsLeft,
        smoothProgress = smoothProgress,
        isFinished = isFinished
    )
}

/**
 * Wrapper for mutableStateOf since we use it for finishedCalled.
 * Re-using the import from the top of the file.
 */
private fun <T> mutableStateOf(value: T) = androidx.compose.runtime.mutableStateOf(value)
