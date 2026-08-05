package com.appause.android.ui.onboarding

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.service.AccessibilityServiceChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the first-launch onboarding flow.
 *
 * Responsibilities are deliberately small — onboarding only:
 * - exposes the current language and lets the user change it (which the UI
 *   applies by recreating the Activity, the same mechanism as Settings),
 * - re-checks whether the Appause accessibility service is enabled (so the
 *   "enable accessibility" step can show live status after the user returns
 *   from the system settings page),
 * - tracks the current step [page] (hoisted here so it survives navigating to
 *   the group editor and back — a remembered value in the Composable is lost
 *   when the screen leaves the composition),
 * - marks onboarding as completed (or skipped) when the user finishes.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = (application as AppauseApp).settingsDataStore

    val language: StateFlow<String> = settingsDataStore.language
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            settingsDataStore.getLanguageSync()
        )

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    /**
     * Current onboarding step (0 = language … 4 = finish).
     * Stored in the ViewModel (not in the Composable) so the position is kept
     * when the user opens the group editor and comes back — otherwise returning
     * would restart the whole guide.
     */
    var page = mutableIntStateOf(0)
        private set

    fun setPage(p: Int) { page.value = p.coerceIn(0, 4) }
    fun nextPage() { page.value = (page.value + 1).coerceAtMost(4) }
    fun prevPage() { page.value = (page.value - 1).coerceAtLeast(0) }

    /** Re-query the accessibility service status (call when the screen resumes). */
    fun refreshServiceStatus() {
        _isServiceRunning.value = AccessibilityServiceChecker.isEnabled(getApplication())
    }

    /** Persist the chosen language. The UI recreates the Activity to apply it. */
    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            settingsDataStore.setLanguage(languageCode)
        }
    }

    /**
     * Suspend version used right before Activity.recreate(): guarantees the
     * language is written to DataStore (and synced to SharedPreferences) before
     * the Activity is recreated, so attachBaseContext picks up the new locale.
     */
    suspend fun applyLanguage(languageCode: String) {
        settingsDataStore.setLanguage(languageCode)
    }

    /** Mark onboarding as done so the app starts at HOME next time. */
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setHasCompletedOnboarding(true)
        }
    }
}
