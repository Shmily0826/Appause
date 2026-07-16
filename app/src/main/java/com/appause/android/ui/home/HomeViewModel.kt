package com.appause.android.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppGroup
import com.appause.android.service.AppauseAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home Screen.
 *
 * Displays:
 * - Accessibility Service status (is it enabled by the user?)
 * - Master toggle state (is Appause enabled?)
 * - List of all created groups
 *
 * Why AndroidViewModel?
 * - We need applicationContext to check AccessibilityService status
 *   and to read settings from the repository.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AppauseApp).repository

    /**
     * List of all groups, observed from Room via Flow.
     * When groups are added/edited/deleted, this Flow emits automatically.
     *
     * stateIn() converts a cold Flow into a hot StateFlow:
     * - SharingStarted.WhileSubscribed(5000): keeps collecting for 5 seconds
     *   after the UI stops observing (handles configuration changes like rotation).
     */
    val groups: StateFlow<List<AppGroup>> = repository.observeAllGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Whether the master toggle is enabled. */
    val isEnabled: StateFlow<Boolean> = repository.isEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * Whether the Accessibility Service is currently running.
     * This is a simple snapshot — checked when the screen appears.
     *
     * Note: we read the static flag from AppauseAccessibilityService.
     * A more robust approach would use AccessibilityManager to query
     * the system, but the static flag is simpler for v1.
     */
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    init {
        refreshServiceStatus()
    }

    /** Re-check the accessibility service status. Called when the screen resumes. */
    fun refreshServiceStatus() {
        _isServiceRunning.value = AppauseAccessibilityService.isRunning
    }

    /** Toggle the master on/off switch. */
    fun toggleEnabled() {
        viewModelScope.launch {
            repository.setEnabled(!isEnabled.value)
        }
    }
}
