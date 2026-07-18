package com.appause.android

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.appause.android.ui.navigation.AppNavGraph
import com.appause.android.ui.theme.AppauseTheme
import java.util.Locale
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

    /**
     * Override locale for this Activity.
     * Why here AND in AppauseApp.attachBaseContext?
     * - Application.attachBaseContext runs once when the process starts.
     * - Activity.attachBaseContext runs every time an Activity is created,
     *   including after Activity.recreate() (triggered by language switch).
     * - On MIUI and some OEM ROMs, the Activity context doesn't inherit
     *   the Application-level locale override, so we must apply it here too.
     *
     * Default behavior: if no language preference is saved, fall back to
     * the system language (Chinese system → "zh", otherwise → "en").
     */
    override fun attachBaseContext(base: Context) {
        val prefs = base.getSharedPreferences("appause_locale_prefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", null)
            ?: if (Locale.getDefault().language == "zh") "zh" else "en"

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)

        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply locale to Activity resources BEFORE super.onCreate().
        // attachBaseContext() only updates the base context's configuration.
        // On some devices/OEM ROMs, the Activity's own Resources object
        // (used by Compose for stringResource) doesn't pick up the change.
        // Updating it here ensures stringResource() returns localized strings.
        val prefs = getSharedPreferences("appause_locale_prefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", null)
            ?: if (Locale.getDefault().language == "zh") "zh" else "en"
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

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
