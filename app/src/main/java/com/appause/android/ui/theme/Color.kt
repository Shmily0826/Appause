package com.appause.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Appause color palette.
 *
 * We use a calming blue/indigo theme to match the "pause" concept.
 * Material 3 uses a tonal color system — we define the key colors
 * and Material 3 generates all the variations automatically.
 *
 * Color naming convention:
 * - md_xxx_YY = Material Design color "xxx" at tonal value YY (0-100).
 * - Lower values = darker, higher values = lighter.
 */

// Primary: calming blue (used for main buttons, links, FAB)
val md_primary_40 = Color(0xFF2962FF)
val md_primary_80 = Color(0xFFB8C9FF)

// Secondary: teal (used for less prominent UI elements)
val md_secondary_40 = Color(0xFF00897B)
val md_secondary_80 = Color(0xFFA7F4E6)

// Tertiary: warm amber (used for accents and highlights)
val md_tertiary_40 = Color(0xFFFF8F00)
val md_tertiary_80 = Color(0xFFFFDDB3)

// Error: standard red
val md_error_40 = Color(0xFFBA1A1A)
val md_error_80 = Color(0xFFFFB4AB)

// Neutral: for backgrounds and text
val md_neutral_10 = Color(0xFF1B1B1F)
val md_neutral_90 = Color(0xFFE4E2E6)
val md_neutral_95 = Color(0xFFF3F1F5)
val md_neutral_99 = Color(0xFFFFFBFE)
