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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Take a moment.")

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
}
