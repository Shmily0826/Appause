package com.appause.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Appause Material 3 Theme.
 *
 * What is a Material 3 Theme?
 * - It defines the color scheme, typography, and shape system for the entire app.
 * - Every Composable inside MaterialTheme { } automatically inherits these styles.
 * - You access them via MaterialTheme.colorScheme, MaterialTheme.typography, etc.
 *
 * Dynamic Color (Android 12+):
 * - On Android 12 (API 31) and above, the system can generate a color scheme
 *   from the user's wallpaper. This is called "dynamic color" or "Material You".
 * - We use dynamic color when available, and fall back to our custom colors on older devices.
 *
 * Dark Mode:
 * - We support both light and dark themes.
 * - isSystemInDarkTheme() checks the user's system setting.
 * - The app automatically switches between light and dark color schemes.
 *
 * How to use in Composables:
 * ```kotlin
 * Text(
 *     text = "Hello",
 *     color = MaterialTheme.colorScheme.primary,
 *     style = MaterialTheme.typography.bodyLarge
 * )
 * ```
 */

/** Light color scheme — shown when the system is in light mode. */
private val LightColorScheme = lightColorScheme(
    primary = md_primary_40,
    secondary = md_secondary_40,
    tertiary = md_tertiary_40,
    error = md_error_40,
    background = md_neutral_99,
    surface = md_neutral_99
)

/** Dark color scheme — shown when the system is in dark mode. */
private val DarkColorScheme = darkColorScheme(
    primary = md_primary_80,
    secondary = md_secondary_80,
    tertiary = md_tertiary_80,
    error = md_error_80,
    background = md_neutral_10,
    surface = md_neutral_10
)

@Composable
fun AppauseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Choose the color scheme based on:
    // 1. Android 12+ with dynamic color? Use the system's wallpaper-derived colors.
    // 2. Otherwise, use our custom light/dark color schemes.
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
