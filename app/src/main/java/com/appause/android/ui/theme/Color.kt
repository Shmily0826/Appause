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

// ─────────────────────────────────────────────────────────────
// Refined light palette — a clear, calm visual hierarchy.
//
// Why a fixed palette instead of Material You (dynamic color)?
// Dynamic color derives everything from the user's wallpaper, which made
// every card blend into the same grey-purple. A fixed palette guarantees
// the intended light blue-purple look and a consistent hierarchy:
//   page background  -> cool near-white
//   content cards    -> white
//   selected state   -> light blue-purple + primary border
//   error state      -> light red background + deep red text
// ─────────────────────────────────────────────────────────────

// Page background: a cool, near-white with a hint of blue.
val md_bg_light = Color(0xFFF5F7FC)

// Card / content surface: pure white so cards read clearly on the background.
val md_surface_light = Color(0xFFFFFFFF)

// Secondary surface: a very light blue-grey for subtle contrast and dividers.
val md_surface_variant_light = Color(0xFFE9EDF6)

// Selected state: light blue-purple fill with readable deep-indigo content.
val md_primary_container_light = Color(0xFFDCE3FF)
val md_on_primary_container_light = Color(0xFF14308F)

// "Recommended" accent: soft teal.
val md_secondary_container_light = Color(0xFFC9F2E8)
val md_on_secondary_container_light = Color(0xFF00382E)

// "Cooldown" accent: soft amber.
val md_tertiary_container_light = Color(0xFFFFE0C2)
val md_on_tertiary_container_light = Color(0xFF5C2D00)

// Error state: light red background with deep red content.
val md_error_container_light = Color(0xFFFDECEA)
val md_on_error_container_light = Color(0xFF8C1D18)

// Borders: light grey-blue, used for hairline card outlines.
val md_outline_light = Color(0xFF7A7E8C)
val md_outline_variant_light = Color(0xFFD9DEEA)
