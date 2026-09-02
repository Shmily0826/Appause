package com.appause.android.ui.pause

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appause.android.data.query.AppInfo
import com.appause.android.ui.theme.AppauseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemporaryPassChooserUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inline_chooser_maps_thirty_minutes_and_blocks_reopen_until_completion() {
        val selectedMinutes = mutableListOf<Int>()
        var finishSelection: (() -> Unit)? = null

        composeRule.setContent {
            AppauseTheme {
                PauseScreenContent(
                    appName = "Chrome",
                    appIcon = null,
                    prompt = "Take a few seconds before deciding whether to continue.",
                    secondsLeft = 0,
                    smoothProgress = 1f,
                    totalSeconds = 10,
                    isFinished = true,
                    onCancel = {},
                    onContinueWithReason = {},
                    onTemporaryPassSelected = { minutes, onFinished ->
                        selectedMinutes += minutes
                        finishSelection = onFinished
                    },
                    useBackHandler = false,
                    useInlineTemporaryPassChooser = true
                )
            }
        }

        composeRule.onNodeWithText("Temporary pass").performClick()
        composeRule.onNodeWithText("Use for 30 min").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(30), selectedMinutes)
            assertTrue(finishSelection != null)
        }
        composeRule.onAllNodesWithText("Use for 30 min").assertCountEquals(0)
        composeRule.onAllNodesWithText("Temporary pass").assertCountEquals(0)

        composeRule.runOnIdle { finishSelection!!.invoke() }
        composeRule.onNodeWithText("Temporary pass").assertIsDisplayed()
    }

    @Test
    fun selection_in_flight_blocks_standalone_back_and_outer_actions_until_completion() {
        val bridge = StandaloneBackBridge()
        var cancelled = false
        var continued = false
        var openedRecommended = false
        var finishSelection: (() -> Unit)? = null

        composeRule.setContent {
            AppauseTheme {
                PauseScreenContent(
                    appName = "Chrome",
                    appIcon = null,
                    prompt = "Prompt",
                    secondsLeft = 0,
                    smoothProgress = 1f,
                    totalSeconds = 10,
                    isFinished = true,
                    onCancel = { cancelled = true },
                    onContinueWithReason = { continued = true },
                    onTemporaryPassSelected = { _, onFinished -> finishSelection = onFinished },
                    useBackHandler = false,
                    standaloneBackBridge = bridge,
                    useInlineTemporaryPassChooser = true,
                    recommendedApps = listOf(AppInfo("com.example.learning", "Learning")),
                    onOpenRecommendedApp = { openedRecommended = true }
                )
            }
        }

        composeRule.onNodeWithText("Temporary pass").performClick()
        composeRule.onNodeWithText("Use for 5 min").performClick()

        composeRule.runOnIdle {
            assertTrue(finishSelection != null)
            bridge.dispatch()
            assertTrue(!cancelled)
            assertTrue(!continued)
            assertTrue(!openedRecommended)
        }
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        composeRule.onNodeWithText("Learning").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Temporary pass").assertCountEquals(0)

        composeRule.runOnIdle { finishSelection!!.invoke() }
        composeRule.onNodeWithText("Continue").assertIsEnabled()
        composeRule.onNodeWithText("Cancel").assertIsEnabled()
        composeRule.onNodeWithText("Learning").assertIsEnabled()
        composeRule.onNodeWithText("Temporary pass").assertIsDisplayed()

        composeRule.runOnIdle { bridge.dispatch() }
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun standalone_back_bridge_dismisses_chooser_then_invokes_cancel() {
        val bridge = StandaloneBackBridge()
        var cancelled = false

        composeRule.setContent {
            AppauseTheme {
                PauseScreenContent(
                    appName = "Chrome",
                    appIcon = null,
                    prompt = "Prompt",
                    secondsLeft = 0,
                    smoothProgress = 1f,
                    totalSeconds = 10,
                    isFinished = true,
                    onCancel = { cancelled = true },
                    onContinueWithReason = {},
                    useBackHandler = false,
                    standaloneBackBridge = bridge,
                    useInlineTemporaryPassChooser = true
                )
            }
        }

        composeRule.onNodeWithText("Temporary pass").performClick()
        composeRule.runOnIdle { bridge.dispatch() }
        composeRule.onNodeWithText("Temporary pass").assertIsDisplayed()

        composeRule.runOnIdle { bridge.dispatch() }
        composeRule.runOnIdle { assertTrue(cancelled) }
    }
}
