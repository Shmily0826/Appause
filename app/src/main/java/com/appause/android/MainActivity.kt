package com.appause.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * MainActivity — the single Activity that hosts all Compose screens.
 *
 * In Jetpack Compose, we use ONE Activity and navigate between "screens"
 * (Composable functions) using Navigation Compose. This is different from
 * the old approach of having one Activity per screen.
 *
 * The only exception is PauseActivity, which MUST be a separate Activity
 * because it is launched from the AccessibilityService (which has no access
 * to this Activity's navigation controller).
 *
 * enableEdgeToEdge() makes the app draw behind the system status/navigation bars
 * for a modern, immersive look. Scaffold handles the safe insets for us.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Material 3 theme — will be extracted to ui/theme/Theme.kt in Phase 3
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Placeholder — will be replaced with NavGraph in Phase 3
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Appause",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
