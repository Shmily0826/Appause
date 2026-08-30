package com.appause.android.ui.settings

import androidx.compose.foundation.lazy.LazyListScope

/** Release builds deliberately contribute no Diagnostics settings entry. */
fun LazyListScope.addDiagnosticsEntry(onClick: () -> Unit) = Unit
