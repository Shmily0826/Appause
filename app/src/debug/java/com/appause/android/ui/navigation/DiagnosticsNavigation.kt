package com.appause.android.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.appause.android.ui.diagnostics.DiagnosticsScreen

/** Debug builds expose the developer-only Diagnostics destination. */
fun NavGraphBuilder.addDiagnosticsDestination(
    onNavigateBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    composable(Routes.DIAGNOSTICS) {
        DiagnosticsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToOnboarding = onNavigateToOnboarding
        )
    }
}
