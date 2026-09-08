package com.appause.android.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppGroup
import com.appause.android.data.pro.ProState
import com.appause.android.service.AccessibilityHealthState
import com.appause.android.service.AppauseAccessibilityService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    private val settingsDataStore = (application as AppauseApp).settingsDataStore

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

    /** Whether Appause Pro is unlocked (gates paid features like extra groups). */
    val isPro: StateFlow<Boolean> = (application as AppauseApp).proState.isPro
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // ── Today's Statistics ──
    // "Today" must be re-evaluated on resume, not fixed at construction: the
    // AccessibilityService keeps this process alive for days, so a constructor-
    // era midnight constant would keep filtering by YESTERDAY's window after
    // the date rolls over (the "today counter is stale" bug). The window lives
    // in a StateFlow; the counters re-derive whenever it changes, and
    // refreshServiceStatus() (called on every ON_RESUME) refreshes it. Setting
    // it to the same value is a no-op (StateFlow conflation), so nothing
    // re-queries while the day has not changed.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _startOfToday = MutableStateFlow(computeStartOfToday())

    /** How many times the user completed the cooldown today. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val proceededToday: StateFlow<Int> = _startOfToday
        .flatMapLatest { repository.observeProceededCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** How many times the user cancelled today. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cancelledToday: StateFlow<Int> = _startOfToday
        .flatMapLatest { repository.observeCancelledCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun computeStartOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // Permission/service status is shared process-wide (the Onboarding and
    // Settings screens show the same four values); see SystemStatusHolder for
    // what each status means and why some are snapshots rather than flows.
    private val systemStatus = (application as AppauseApp).systemStatus
    val accessibilityHealth: StateFlow<AccessibilityHealthState> get() = systemStatus.accessibilityHealth
    val canDrawOverlays: StateFlow<Boolean> get() = systemStatus.canDrawOverlays
    val isUsageAccessGranted: StateFlow<Boolean> get() = systemStatus.isUsageAccessGranted
    val isIgnoringBattery: StateFlow<Boolean> get() = systemStatus.isIgnoringBattery
    val leaveCooldownDeadlines: StateFlow<Map<String, Long>> =
        AppauseAccessibilityService.leaveCooldownDeadlines

    /**
     * Number of apps in each group (groupId -> count).
     * Backs the "N apps" row on each group card.
     * Groups with no apps are absent from the map (the UI treats that as 0).
     */
    private val _appCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val appCounts: StateFlow<Map<Long, Int>> = _appCounts.asStateFlow()

    /**
     * 7-day interception frequency per group (groupId -> count).
     * Loaded on init and refreshed on every ON_RESUME via loadAppCounts().
     */
    private val _interceptionCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /**
     * Groups sorted by interception frequency (most active first).
     * Falls back to creation order for groups with no recent interceptions.
     *
     * This is DERIVED from the groups flow rather than a manual sorted copy:
     * a copy taken from groups.value during the WhileSubscribed(5000) gap can
     * read the empty initial value and would never be recomputed — the user
     * would see "no groups" after returning to the app even though the data
     * exists. combine() keeps a single source of truth and re-sorts on every
     * groups emission.
     */
    val sortedGroups: StateFlow<List<AppGroup>> =
        combine(groups, _interceptionCounts) { groups, counts ->
            groups.sortedByDescending { counts[it.id] ?: 0 }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Whether the full group list is expanded (shows all groups, not just top 4). */
    private val _groupsExpanded = MutableStateFlow(false)
    val groupsExpanded: StateFlow<Boolean> = _groupsExpanded.asStateFlow()

    init {
        refreshServiceStatus()
        loadAppCounts()
    }

    /** Re-check the accessibility service status. Called when the screen resumes. */
    fun refreshServiceStatus() {
        // Also roll the "today" window over if the date changed while we were
        // away (see the Today's Statistics block above).
        _startOfToday.value = computeStartOfToday()
        systemStatus.refresh()
    }

    /**
     * Load app counts and group interception frequency.
     * Called on init and again whenever the screen resumes.
     * sortedGroups re-derives itself from these counts (see its declaration).
     */
    fun loadAppCounts() {
        viewModelScope.launch {
            _appCounts.value = repository.getAppCounts()
            // 7-day interception frequency (most active first)
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            _interceptionCounts.value = repository.getGroupInterceptionCounts(sevenDaysAgo)
        }
    }

    /** Toggle expand/collapse of the group list. */
    fun toggleGroupsExpanded() {
        _groupsExpanded.value = !_groupsExpanded.value
    }

    /** Toggle the master on/off switch. */
    fun toggleEnabled() {
        viewModelScope.launch {
            repository.setEnabled(!isEnabled.value)
        }
    }

    /**
     * Whether the user has seen the one-time permission-rationale explanation.
     * Drives the "explain before first request" gate on the home permission
     * banners: the first tap of any "open settings" action shows the rationale
     * dialog; afterwards taps go straight to system settings.
     */
    val hasSeenPermissionIntro: StateFlow<Boolean> = settingsDataStore.hasSeenPermissionIntro
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** Record that the permission rationale has been shown. */
    fun markPermissionIntroSeen() {
        viewModelScope.launch {
            settingsDataStore.setPermissionIntroSeen()
        }
    }
}
