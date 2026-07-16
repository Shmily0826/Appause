package com.appause.android.ui.pause

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * PauseActivity — full-screen cooldown overlay.
 *
 * This is a STUB for Phase 1. Full implementation in Phase 7.
 *
 * Why a separate Activity (not a Compose screen in NavGraph)?
 * - It must be launched from AccessibilityService.
 * - The Service has no access to MainActivity's NavController.
 * - It needs FLAG_ACTIVITY_NEW_TASK to start from a Service context.
 *
 * Manifest settings:
 * - excludeFromRecents="true": don't show in the recent apps list.
 * - taskAffinity="": runs in its own task, separate from MainActivity.
 * - launchMode="singleInstance": only one instance at a time.
 */
class PauseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // In Phase 7, we'll read the target app info from the Intent extras.
        val targetPackage = intent.getStringExtra("target_package") ?: "unknown"

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pause Screen\n(Pausing: $targetPackage)\n\nPhase 7 stub",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}
