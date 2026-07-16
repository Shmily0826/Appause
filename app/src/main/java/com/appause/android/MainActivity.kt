package com.appause.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.appause.android.ui.navigation.AppNavGraph
import com.appause.android.ui.theme.AppauseTheme

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
 * What happens in onCreate:
 * 1. enableEdgeToEdge() — draw behind system bars for a modern look.
 * 2. setContent { } — define the Compose UI tree.
 * 3. AppauseTheme { } — apply Material 3 colors and typography.
 * 4. Surface { } — provide a consistent background color.
 * 5. AppNavGraph() — render the navigation host with all screens.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppauseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph()
                }
            }
        }
    }
}
