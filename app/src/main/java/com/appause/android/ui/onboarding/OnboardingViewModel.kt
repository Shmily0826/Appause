package com.appause.android.ui.onboarding

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.appause.android.AppauseApp
import com.appause.android.data.settings.SettingsDataStore
import com.appause.android.service.AccessibilityServiceChecker
import com.appause.android.service.ForegroundChecker
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
class OnboardingViewModel(
    application: Application,
    // Test seam: lets unit tests inject a SettingsDataStore (defaults to the real one).
    settingsDataStoreOverride: SettingsDataStore? = null
) : AndroidViewModel(application) {

    private val settingsDataStore = settingsDataStoreOverride ?: (application as AppauseApp).settingsDataStore

    val language: StateFlow<String> = settingsDataStore.language
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            settingsDataStore.getLanguageSync()
        )

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    /** Whether "Display over other apps" (SYSTEM_ALERT_WINDOW) is granted. */
    private val _canDrawOverlays = MutableStateFlow(false)
    val canDrawOverlays: StateFlow<Boolean> = _canDrawOverlays

    /** Whether "Usage access" (PACKAGE_USAGE_STATS) is granted. */
    private val _isUsageAccessGranted = MutableStateFlow(false)
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted

    /** Whether the app is exempt from battery optimization ("Unrestricted"). */
    private val _isIgnoringBattery = MutableStateFlow(false)
    val isIgnoringBattery: StateFlow<Boolean> = _isIgnoringBattery

    /**
     * Current onboarding step.
     * 0 = language, 1 = pause-screen preview, 2 = privacy/value,
     * 3 = accessibility, 4 = usage access, 5 = battery,
     * 6 = display-over-other-apps, 7 = optional first group.
     * Stored in the ViewModel (not in the Composable) so the position is kept
     * when the user opens the group editor and comes back — otherwise returning
     * would restart the whole guide.
     */
    var page = mutableIntStateOf(0)
        private set

    fun nextPage() { page.value = (page.value + 1).coerceAtMost(7) }
    fun prevPage() { page.value = (page.value - 1).coerceAtLeast(0) }

    /** Re-query permission status (call when the screen resumes). */
    fun refreshServiceStatus() {
        val app = getApplication<Application>()
        _isServiceRunning.value = AccessibilityServiceChecker.isEnabled(app)
        _canDrawOverlays.value = Settings.canDrawOverlays(app)
        _isUsageAccessGranted.value = ForegroundChecker.isUsageAccessGranted(app)
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isIgnoringBattery.value = pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false
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

    /**
     * Skip the guide and jump straight to HOME. Does NOT mark the permission
     * rationale as seen — if the user skipped, they haven't seen the inline
     * explanations, so the home screen will still show the one-time rationale
     * on the first permission request.
     */
    fun skipOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setHasCompletedOnboarding(true)
        }
    }

    /** Mark onboarding as done so the app starts at HOME next time. */
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setHasCompletedOnboarding(true)
            // The guide itself explains why each permission is needed, so the
            // one-time rationale dialog on the home screen is no longer required.
            settingsDataStore.setPermissionIntroSeen()
        }
    }

    /**
     * Factory for the default production constructor.
     *
     * Compose's default `viewModel()` helper only knows how to instantiate
     * AndroidViewModels with a single-argument `(Application)` constructor. The
     * test seam adds a second optional parameter, so the production caller must
     * provide this factory to wire the real SettingsDataStore explicitly.
     */
    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
                        return OnboardingViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
