package com.appause.android.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.appause.android.AppauseApp
import kotlinx.coroutines.flow.first
import com.appause.android.ui.appselect.AppSelectScreen
import com.appause.android.ui.diagnostics.DiagnosticsScreen
import com.appause.android.ui.feedback.FeedbackScreen
import com.appause.android.ui.groupeditor.GroupEditorScreen
import com.appause.android.ui.home.HomeScreen
import com.appause.android.ui.onboarding.OnboardingScreen
import com.appause.android.ui.onboarding.OnboardingViewModel
import com.appause.android.ui.pro.ProScreen
import com.appause.android.ui.recommended.RecommendedAppsScreen
import com.appause.android.ui.settings.AboutSettingsScreen
import com.appause.android.ui.settings.AppearanceSettingsScreen
import com.appause.android.ui.settings.PauseSettingsScreen
import com.appause.android.ui.settings.PermissionsSettingsScreen
import com.appause.android.ui.settings.SettingsScreen
import com.appause.android.ui.stats.StatsScreen

/**
 * Navigation routes — simple string constants.
 *
 * Why string routes instead of sealed classes?
 * - Simpler for beginners to understand.
 * - Works well for a small number of screens.
 * - Can be upgraded to type-safe routes later if needed.
 */
object Routes {
    const val HOME = "home"
    const val GROUP_EDITOR = "group_editor"
    const val GROUP_EDITOR_WITH_ID = "group_editor/{groupId}"
    const val APP_SELECT = "app_select"
    const val RECOMMENDED = "recommended"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val PRO = "pro"
    const val FEEDBACK = "feedback"
    const val ONBOARDING = "onboarding"
    const val SETTINGS_APPEARANCE = "settings_appearance"
    const val SETTINGS_PERMISSIONS = "settings_permissions"
    const val SETTINGS_PAUSE = "settings_pause"
    const val SETTINGS_ABOUT = "settings_about"

    /** Debug-only troubleshooting screen (entry point is gated on BuildConfig.DEBUG). */
    const val DIAGNOSTICS = "diagnostics"

    /** Build a route string for editing an existing group. */
    fun groupEditor(groupId: Long): String = "group_editor/$groupId"
}

/**
 * NavHost — the navigation controller for the entire app.
 *
 * What is NavHost?
 * - It manages a stack of screens (like a browser history).
 * - navigate("route") pushes a new screen onto the stack.
 * - popBackStack() goes back to the previous screen.
 * - Each screen is defined with composable("route") { ... }.
 *
 * Back stack behavior:
 * - HomeScreen → GroupEditorScreen → AppSelectScreen
 * - Pressing Back on AppSelectScreen returns to GroupEditorScreen
 * - Pressing Back on GroupEditorScreen returns to HomeScreen
 * - Pressing Back on HomeScreen exits the app
 *
 * How groupId is passed:
 * - GroupEditor accepts an optional "groupId" argument (Long).
 * - New group: navigate to "group_editor" with default groupId = -1.
 * - Edit existing: navigate to "group_editor/123" with groupId = 123.
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as AppauseApp
    var returnHomeAfterOnboardingGroup by remember { mutableStateOf(false) }

    // The start destination depends on whether onboarding is finished.
    // DataStore is async, so we resolve it once before building the NavHost
    // (a brief blank frame before the first read is acceptable).
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val done = app.settingsDataStore.hasCompletedOnboarding.first()
        startDestination = if (done) Routes.HOME else Routes.ONBOARDING
    }

    // Guard against rapid back-button taps: only pop when the current
    // destination is fully RESUMED. During a navigation transition the
    // incoming entry is not yet RESUMED, so a second tap is ignored
    // instead of popping past the start destination (white screen bug).
    val safePopBackStack: () -> Unit = {
        val entry = navController.currentBackStackEntry
        if (entry != null && entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }

    if (startDestination != null) {
    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        // ── Home Screen ──
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToGroupEditor = { groupId ->
                    if (groupId != null && groupId > 0) {
                        navController.navigate(Routes.groupEditor(groupId))
                    } else {
                        navController.navigate(Routes.GROUP_EDITOR)
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToStats = {
                    navController.navigate(Routes.STATS)
                },
                onNavigateToRecommended = {
                    navController.navigate(Routes.RECOMMENDED)
                },
                onNavigateToPro = {
                    navController.navigate(Routes.PRO)
                }
            )
        }

        // ── Group Editor Screen ──
        composable(Routes.GROUP_EDITOR) {
            GroupEditorScreen(
                groupId = -1L,
                onNavigateBack = {
                    if (returnHomeAfterOnboardingGroup) {
                        returnHomeAfterOnboardingGroup = false
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    } else {
                        safePopBackStack()
                    }
                },
                onNavigateToAppSelect = { navController.navigate(Routes.APP_SELECT) },
                onNavigateToPro = { navController.navigate(Routes.PRO) }
            )
        }
        composable(
            route = Routes.GROUP_EDITOR_WITH_ID,
            arguments = listOf(
                navArgument("groupId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getLong("groupId") ?: -1L
            GroupEditorScreen(
                groupId = groupId,
                onNavigateBack = safePopBackStack,
                onNavigateToAppSelect = { navController.navigate(Routes.APP_SELECT) },
                onNavigateToPro = { navController.navigate(Routes.PRO) }
            )
        }

        // ── App Select Screen ──
        composable(Routes.APP_SELECT) {
            AppSelectScreen(
                onNavigateBack = safePopBackStack
            )
        }

        // ── Recommended Apps Screen ──
        composable(Routes.RECOMMENDED) {
            RecommendedAppsScreen(
                onNavigateBack = safePopBackStack,
                onNavigateToAppSelect = { navController.navigate(Routes.APP_SELECT) }
            )
        }

        // ── Settings (hub) ──
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = safePopBackStack,
                onNavigateToAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                onNavigateToPermissions = { navController.navigate(Routes.SETTINGS_PERMISSIONS) },
                onNavigateToPause = { navController.navigate(Routes.SETTINGS_PAUSE) },
                onNavigateToAbout = { navController.navigate(Routes.SETTINGS_ABOUT) },
                onNavigateToPro = { navController.navigate(Routes.PRO) },
                onNavigateToFeedback = { navController.navigate(Routes.FEEDBACK) },
                onNavigateToDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }
            )
        }

        // ── Diagnostics (debug builds only — Settings hides the entry otherwise) ──
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(
                onNavigateBack = safePopBackStack,
                onNavigateToOnboarding = { navController.navigate(Routes.ONBOARDING) }
            )
        }

        // Restart the app (used after a language switch so attachBaseContext
        // re-reads the new locale). Defined inside the route's composable
        // context where LocalContext is available.
        composable(Routes.SETTINGS_APPEARANCE) {
            val activity = LocalContext.current as? Activity
            val restartApp: () -> Unit = {
                activity?.let { act ->
                    val restartIntent = act.packageManager
                        .getLaunchIntentForPackage(act.packageName)
                    restartIntent?.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                    act.finish()
                    if (restartIntent != null) {
                        act.startActivity(restartIntent)
                    }
                }
            }
            AppearanceSettingsScreen(
                onNavigateBack = safePopBackStack,
                onLanguageChanged = restartApp
            )
        }

        // ── Settings: Permissions & Running ──
        composable(Routes.SETTINGS_PERMISSIONS) {
            PermissionsSettingsScreen(onNavigateBack = safePopBackStack)
        }

        // ── Settings: Pause behavior ──
        composable(Routes.SETTINGS_PAUSE) {
            PauseSettingsScreen(
                onNavigateBack = safePopBackStack,
                onNavigateToPro = { navController.navigate(Routes.PRO) }
            )
        }

        // ── Settings: About ──
        composable(Routes.SETTINGS_ABOUT) {
            AboutSettingsScreen(onNavigateBack = safePopBackStack)
        }

        // ── Statistics Screen ──
        composable(Routes.STATS) {
            StatsScreen(
                onNavigateBack = safePopBackStack
            )
        }

        // ── Appause Pro Screen ──
        composable(Routes.PRO) {
            ProScreen(
                onNavigateBack = safePopBackStack
            )
        }

        // ── Feedback Screen ──
        composable(Routes.FEEDBACK) {
            FeedbackScreen(
                onNavigateBack = safePopBackStack
            )
        }

        // ── Onboarding (first-launch guide) ──
        composable(Routes.ONBOARDING) {
            val onboardingViewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.Factory(LocalContext.current.applicationContext as AppauseApp)
            )
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onNavigateToGroupEditor = {
                    returnHomeAfterOnboardingGroup = true
                    navController.navigate(Routes.GROUP_EDITOR)
                }
            )
        }
    }
    }
}
