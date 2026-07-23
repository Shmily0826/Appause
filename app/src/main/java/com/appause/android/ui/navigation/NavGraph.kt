package com.appause.android.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.appause.android.ui.appselect.AppSelectScreen
import com.appause.android.ui.groupeditor.GroupEditorScreen
import com.appause.android.ui.home.HomeScreen
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
    const val SETTINGS = "settings"
    const val STATS = "stats"

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

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
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
                }
            )
        }

        // ── Group Editor Screen ──
        composable(Routes.GROUP_EDITOR) {
            GroupEditorScreen(
                groupId = -1L,
                onNavigateBack = safePopBackStack,
                onNavigateToAppSelect = { navController.navigate(Routes.APP_SELECT) }
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
                onNavigateToAppSelect = { navController.navigate(Routes.APP_SELECT) }
            )
        }

        // ── App Select Screen ──
        composable(Routes.APP_SELECT) {
            AppSelectScreen(
                onNavigateBack = safePopBackStack
            )
        }

        // ── Settings Screen ──
        composable(Routes.SETTINGS) {
            val activity = LocalContext.current as? Activity
            SettingsScreen(
                onNavigateBack = safePopBackStack,
                onLanguageChanged = {
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
            )
        }

        // ── Statistics Screen ──
        composable(Routes.STATS) {
            StatsScreen(
                onNavigateBack = safePopBackStack
            )
        }
    }
}
