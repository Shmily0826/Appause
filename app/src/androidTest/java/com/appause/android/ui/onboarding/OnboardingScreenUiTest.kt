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
 * Compose UI checks for the five-step onboarding flow.
 *
 * Navigation stays in ViewModel memory; Skip and Finish are not clicked so
 * the real first-launch preference is not changed.
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
            OnboardingScreen(onNavigateToHome = {}, viewModel = viewModel)
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
        composeRule.onNodeWithText(str(R.string.onboarding_service_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.onboarding_language_title)).assertDoesNotExist()

        composeRule.onNodeWithText(str(R.string.onboarding_back)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.onboarding_language_title)).assertIsDisplayed()
    }

    @Test
    fun walking_next_reaches_finish_after_three_access_setup_steps() {
        showOnboarding()

        // Five steps (page 0..4): language, three access/setup pages, then Finish.
        repeat(4) {
            composeRule.onNodeWithText(str(R.string.onboarding_next)).performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText(str(R.string.onboarding_finish_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.onboarding_finish)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.onboarding_next)).assertDoesNotExist()
    }
}
