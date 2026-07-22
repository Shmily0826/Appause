package com.appause.android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.service.AppauseAccessibilityService
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

    val language: StateFlow<String> = repository.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            (application as AppauseApp).settingsDataStore.getLanguageSync())

    val themeMode: StateFlow<String> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            (application as AppauseApp).settingsDataStore.getThemeModeSync())

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    fun refreshServiceStatus() {
        _isServiceRunning.value = AppauseAccessibilityService.isRunning
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
}
