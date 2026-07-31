package com.appause.android.ui.settings

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.service.AccessibilityServiceChecker
import com.appause.android.service.AppauseAccessibilityService
import com.appause.android.service.ForegroundChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings Screen.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AppauseApp).repository

    val isEnabled: StateFlow<Boolean> = repository.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val defaultPrompt: StateFlow<String> = repository.defaultPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Whether Appause Pro is unlocked (gates the custom prompt editor). */
    val isPro: StateFlow<Boolean> = (application as AppauseApp).proState.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val language: StateFlow<String> = repository.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            (application as AppauseApp).settingsDataStore.getLanguageSync())

    val themeMode: StateFlow<String> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            (application as AppauseApp).settingsDataStore.getThemeModeSync())

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    private val _isUsageAccessGranted = MutableStateFlow(false)
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted

    private val _isIgnoringBattery = MutableStateFlow(false)
    val isIgnoringBattery: StateFlow<Boolean> = _isIgnoringBattery

    /** Whether the persistent monitoring notification is shown. Default true. */
    val showNotification: StateFlow<Boolean> = (getApplication<Application>() as AppauseApp).settingsDataStore.showNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * Re-read every permission/status that the Settings screen shows.
     *
     * Why we expose this as a manual refresh (instead of a lifecycle observer):
     * the user grants a permission in a *system* settings page and then navigates
     * BACK to Appause. On that return there is no reliable lifecycle event the
     * Composable can hook, so we re-query when the screen becomes visible. The
     * SettingsScreen calls this from a DisposableEffect on resume.
     */
    fun refreshServiceStatus() {
        val app = getApplication<Application>()
        _isServiceRunning.value = AccessibilityServiceChecker.isEnabled(app)
        _isUsageAccessGranted.value = ForegroundChecker.isUsageAccessGranted(app)
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isIgnoringBattery.value = powerManager?.isIgnoringBatteryOptimizations(app.packageName) ?: false
    }

    fun updateDefaultPrompt(prompt: String) {
        viewModelScope.launch {
            repository.setDefaultPrompt(prompt)
        }
    }

    fun setLanguage(languageCode: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.setLanguage(languageCode)
            // Language is now saved to both DataStore and SharedPreferences.
            // Run the callback (typically an app restart) so the new locale
            // is guaranteed to be read by attachBaseContext on the new Activity.
            onComplete()
        }
    }

    /**
     * Persist the chosen theme mode ("system", "light", or "dark").
     * No restart needed — the Activities observe themeMode and recompose
     * with the new color scheme immediately.
     */
    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    /**
     * Toggle the persistent monitoring notification. Persists the choice and
     * asks the running service to add/remove the notification immediately so the
     * user sees the change without restarting Appause.
     */
    fun setShowNotification(show: Boolean) {
        viewModelScope.launch {
            (getApplication<Application>() as AppauseApp).settingsDataStore.setShowNotification(show)
            AppauseAccessibilityService.instance?.applyNotificationSetting()
        }
    }
}
