package com.appause.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Appause Material 3 Theme.
 *
 * What is a Material 3 Theme?
 * - It defines the color scheme, typography, and shape system for the entire app.
 * - Every Composable inside MaterialTheme { } automatically inherits these styles.
 * - You access them via MaterialTheme.colorScheme, MaterialTheme.typography, etc.
 *
 * Why a FIXED color scheme (no Material You / dynamic color)?
 * - Dynamic color derives the whole palette from the user's wallpaper. In
 *   practice that made every card on screen blend into the same grey-purple,
 *   destroying the visual hierarchy.
 * - We intentionally use a fixed, calm blue-purple palette so the app always
 *   has a clear hierarchy:
 *       page background  -> cool near-white
 *       content cards    -> white with a hairline border
 *       selected state   -> light blue-purple + primary border
 *       error state      -> light red background + deep red text
 *
 * Dark Mode:
 * - We support both light and dark themes.
 * - The user picks a mode in Settings: "light", "dark", or "system".
 * - "system" follows the device setting via isSystemInDarkTheme().
 * - Activities resolve the mode with appauseDarkTheme() and pass the result
 *   as the darkTheme parameter below.
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

/**
 * Light color scheme — a calm blue-purple theme with a clear hierarchy.
 *
 * Key structural tokens:
 * - background  : cool near-white page canvas
 * - surface     : white cards (read clearly against the background)
 * - surfaceVariant : light blue-grey for subtle secondary surfaces
 * - primaryContainer : light blue-purple "selected" fill
 * - errorContainer : light red "error" fill with deep red content
 */
private val LightColorScheme = lightColorScheme(
    primary = md_primary_40,
    onPrimary = Color.White,
    primaryContainer = md_primary_container_light,
    onPrimaryContainer = md_on_primary_container_light,

    secondary = md_secondary_40,
    onSecondary = Color.White,
    secondaryContainer = md_secondary_container_light,
    onSecondaryContainer = md_on_secondary_container_light,

    tertiary = md_tertiary_40,
    onTertiary = Color.White,
    tertiaryContainer = md_tertiary_container_light,
    onTertiaryContainer = md_on_tertiary_container_light,

    error = md_error_40,
    onError = Color.White,
    errorContainer = md_error_container_light,
    onErrorContainer = md_on_error_container_light,

    background = md_bg_light,
    onBackground = Color(0xFF1A1C22),
    surface = md_surface_light,
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = md_surface_variant_light,
    onSurfaceVariant = Color(0xFF44474F),

    outline = md_outline_light,
    outlineVariant = md_outline_variant_light
)

/** Dark color scheme — shown when the system is in dark mode. */
private val DarkColorScheme = darkColorScheme(
    primary = md_primary_80,
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF24379B),
    onPrimaryContainer = Color(0xFFDCE3FF),

    secondary = md_secondary_80,
    onSecondary = Color(0xFF00372E),
    secondaryContainer = Color(0xFF005044),
    onSecondaryContainer = Color(0xFFC9F2E8),

    tertiary = md_tertiary_80,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF634000),
    onTertiaryContainer = Color(0xFFFFE0C2),

    error = md_error_80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF14161C),
    onBackground = Color(0xFFE5E7EF),
    surface = Color(0xFF1A1D24),
    onSurface = Color(0xFFE5E7EF),
    surfaceVariant = Color(0xFF2A2E38),
    onSurfaceVariant = Color(0xFFB9BCC8),

    outline = Color(0xFF8A8FA0),
    outlineVariant = Color(0xFF3A3F4C)
)

@Composable
fun AppauseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Use the fixed Appause palette. We deliberately do NOT use Material You
    // (dynamic color) — see the note at the top of this file.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Resolve whether to use the dark color scheme for a stored theme mode.
 *
 * - "light"  → always light
 * - "dark"   → always dark
 * - "system" (or anything else) → follow the device setting
 *
 * This is a @Composable because the "system" case needs isSystemInDarkTheme(),
 * which can only be read inside composition.
 */
@Composable
fun appauseDarkTheme(themeMode: String): Boolean = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
