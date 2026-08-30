package com.appause.android.ui.navigation

import androidx.navigation.NavGraphBuilder

/** Release builds deliberately contribute no Diagnostics destination. */
fun NavGraphBuilder.addDiagnosticsDestination(
    onNavigateBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) = Unit
