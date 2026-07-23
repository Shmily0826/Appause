package com.appause.android.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppGroup
import com.appause.android.service.AccessibilityServiceChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

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

    // ── Today's Statistics ──
    // Calculate midnight of today to filter records.
    private val startOfToday: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** How many times the user completed the cooldown today. */
    val proceededToday: StateFlow<Int> = repository.observeProceededCount(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** How many times the user cancelled today. */
    val cancelledToday: StateFlow<Int> = repository.observeCancelledCount(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Whether the Accessibility Service is enabled by the user.
     * This is a simple snapshot — checked when the screen appears.
     *
     * We query the SYSTEM setting (Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
     * via AccessibilityServiceChecker, NOT an in-process flag. The system setting
     * survives process death, so reopening the app no longer falsely reports the
     * service as disabled (which used to nag the user to re-grant permission).
     */
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    /**
     * Number of apps in each group (groupId -> count).
     * Backs the "N apps" row on each group card.
     * Groups with no apps are absent from the map (the UI treats that as 0).
     */
    private val _appCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val appCounts: StateFlow<Map<Long, Int>> = _appCounts.asStateFlow()

    init {
        refreshServiceStatus()
        loadAppCounts()
    }

    /** Re-check the accessibility service status. Called when the screen resumes. */
    fun refreshServiceStatus() {
        _isServiceRunning.value = AccessibilityServiceChecker.isEnabled(getApplication())
    }

    /**
     * Load the number of apps in each group.
     * Called on init and again whenever the screen resumes (so the counts
     * refresh after the user edits a group and navigates back).
     */
    fun loadAppCounts() {
        viewModelScope.launch {
            _appCounts.value = repository.getAppCounts()
        }
    }

    /** Toggle the master on/off switch. */
    fun toggleEnabled() {
        viewModelScope.launch {
            repository.setEnabled(!isEnabled.value)
        }
    }
}
