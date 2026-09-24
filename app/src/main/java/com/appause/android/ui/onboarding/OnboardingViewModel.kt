package com.appause.android.ui.onboarding

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.appause.android.AppauseApp
import com.appause.android.data.repository.SystemStatusHolder
import com.appause.android.data.settings.SettingsDataStore
import com.appause.android.service.AccessibilityHealthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the short first-launch onboarding flow.
 *
 * It stores the current page across system-settings visits, refreshes access
 * status on return, and records completion or skip.
 */
class OnboardingViewModel @JvmOverloads constructor(
    application: Application,
    // @JvmOverloads is REQUIRED here. OnboardingScreen's `viewModel` parameter
    // defaults to the bare `viewModel()` helper, which goes through the default
    // AndroidViewModelFactory; that factory reflectively looks up an
    // (Application) single-arg constructor, and Kotlin default parameters do
    // NOT generate one. Without this annotation the screen dies with
    // NoSuchMethodException on every entry — the same defect that crashed
    // StatsViewModel from v0.5.39 to v0.5.42. NavGraph.kt currently passes an
    // explicit factory, which is the only reason this stayed hidden; do not
    // rely on that call site. Guarded by ViewModelFactoryContractTest.
    //
    // Test seam: lets unit tests inject a SettingsDataStore (defaults to the real one).
    settingsDataStoreOverride: SettingsDataStore? = null,
    // Test seam: lets unit tests inject a SystemStatusHolder (defaults to the
    // process-wide one) so permission refreshes run against the test context.
    systemStatusOverride: SystemStatusHolder? = null
) : AndroidViewModel(application) {

    private val settingsDataStore = settingsDataStoreOverride ?: (application as AppauseApp).settingsDataStore
    private val systemStatus = systemStatusOverride ?: (application as AppauseApp).systemStatus

    val language: StateFlow<String> = settingsDataStore.language
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            settingsDataStore.getLanguageSync()
        )

    // Permission/service status shared with Home and Settings (SystemStatusHolder).
    val accessibilityHealth: StateFlow<AccessibilityHealthState> get() = systemStatus.accessibilityHealth
    val canDrawOverlays: StateFlow<Boolean> get() = systemStatus.canDrawOverlays
    val isUsageAccessGranted: StateFlow<Boolean> get() = systemStatus.isUsageAccessGranted
    val isIgnoringBattery: StateFlow<Boolean> get() = systemStatus.isIgnoringBattery

    init {
        refreshServiceStatus()
    }

    /**
     * Current step: language, accessibility, usage access, overlay fallback,
     * and finish. Keeping it in the ViewModel preserves position across system
     * settings while this Activity remains alive.
     */
    var page = mutableIntStateOf(0)
        private set

    fun nextPage() { page.value = (page.value + 1).coerceAtMost(4) }
    fun prevPage() { page.value = (page.value - 1).coerceAtLeast(0) }

    /** Re-query permission status (call when the screen resumes). */
    fun refreshServiceStatus() {
        systemStatus.refresh()
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
