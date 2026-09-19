package com.appause.android.ui.onboarding

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appause.android.R
import com.appause.android.data.repository.SystemStatusHolder
import com.appause.android.data.settings.SettingsDataStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device Compose UI test for the onboarding wizard's step navigation.
 *
 * The ViewModel's own logic (language persistence, page clamping, skip/complete
 * flags) is already covered by OnboardingViewModelTest; this test verifies the
 * part only a rendered tree can: which controls appear on which step and that
 * tapping Next / Back moves the visible page.
 *
 * Navigation is driven purely through the ViewModel's in-memory [page] state,
 * so NO test clicks the Skip / Create-group / Later actions — those persist
 * HAS_COMPLETED_ONBOARDING to the real DataStore and would flip the device's
 * first-launch state. We assert those buttons only render on the final step.
 *
 * The VM is built with the real SettingsDataStore / SystemStatusHolder (the
 * permission state is irrelevant to navigation, and both read live device
 * values). String assertions resolve against the app's own resources so they
 * hold under any device locale.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = composeRule.activity.getString(id)

    private fun showOnboarding(): OnboardingViewModel {
        val app = composeRule.activity.applicationContext as Application
        val viewModel = OnboardingViewModel(
            application = app,
            settingsDataStoreOverride = SettingsDataStore(app),
            systemStatusOverride = SystemStatusHolder(app)
        )
        composeRule.setContent {
            OnboardingScreen(
                onNavigateToHome = {},
                onNavigateToGroupEditor = {},
                viewModel = viewModel
            )
        }
        return viewModel
    }

    @Test
    fun first_step_shows_language_and_next_but_no_back() {
        showOnboarding()

        composeRule.onNodeWithText(str(R.string.onboarding_language_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.onboarding_skip)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.onboarding_next)).assertIsDisplayed()
        // Back is hidden on page 0 (the code only renders it when page > 0).
        composeRule.onNodeWithText(str(R.string.onboarding_back)).assertDoesNotExist()
    }

    @Test
    fun next_advances_past_language_and_back_returns_to_it() {
        showOnboarding()

        composeRule.onNodeWithText(str(R.string.onboarding_next)).performClick()
        composeRule.waitForIdle()
        // Left the language step (now the preview step).
        composeRule.onNodeWithText(str(R.string.onboarding_language_title)).assertDoesNotExist()

        composeRule.onNodeWithText(str(R.string.onboarding_back)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.onboarding_language_title)).assertIsDisplayed()
    }

    @Test
    fun walking_next_reaches_the_final_group_step() {
        showOnboarding()

        // 8 steps (page 0..7): seven Next taps walk from language to the group step.
        repeat(7) {
            composeRule.onNodeWithText(str(R.string.onboarding_next)).performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText(str(R.string.onboarding_group_title)).assertIsDisplayed()
        // Final step swaps the single Next button for Create-group + Later.
        composeRule.onNodeWithText(str(R.string.onboarding_group_add)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.onboarding_group_later)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.onboarding_next)).assertDoesNotExist()
    }
}
