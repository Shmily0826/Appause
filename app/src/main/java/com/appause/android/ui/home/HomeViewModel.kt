package com.appause.android.ui.home

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppGroup
import com.appause.android.data.pro.ProState
import com.appause.android.service.AccessibilityServiceChecker
import com.appause.android.service.ForegroundChecker
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
     * Whether "Display over other apps" (SYSTEM_ALERT_WINDOW) is granted.
     *
     * NOTE: this is now OPTIONAL. The pause screen uses a TYPE_ACCESSIBILITY_OVERLAY
     * (2032) window, which does NOT require SYSTEM_ALERT_WINDOW and which anti-tamper
     * apps (e.g. 小红书's setHideOverlayWindows) cannot hide. SYSTEM_ALERT_WINDOW is
     * only consulted as a fallback if 2032 is rejected on a rare ROM. So a missing
     * overlay permission must NOT block or alarm the user — interception still works.
     */
    private val _canDrawOverlays = MutableStateFlow(false)
    val canDrawOverlays: StateFlow<Boolean> = _canDrawOverlays.asStateFlow()

    /**
     * Whether "Usage access" (PACKAGE_USAGE_STATS) is granted.
     *
     * This used to be treated as an optional nicety, which turned out to be
     * wrong: without it Appause cannot tell a real app launch from the burst of
     * window events that MIUI/HyperOS replays for every cached task when the
     * user opens Recents or swipes to switch apps. The result is a pause screen
     * appearing on the home screen out of nowhere. It belongs in the required
     * set, next to accessibility and overlay.
     */
    private val _isUsageAccessGranted = MutableStateFlow(false)
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted.asStateFlow()

    /**
     * Whether the app is exempt from battery optimization ("无限制" / no
     * restrictions). On HyperOS/MIUI this is the #1 cause of "first open isn't
     * intercepted, only works after switching to Appause": when false, the
     * system kills the AccessibilityService in the background and does NOT
     * auto-restart it, so foreground events stop arriving until the Appause app
     * itself is opened again. This must be ON for interception to be reliable.
     */
    private val _isIgnoringBattery = MutableStateFlow(false)
    val isIgnoringBattery: StateFlow<Boolean> = _isIgnoringBattery.asStateFlow()

    /**
     * Number of apps in each group (groupId -> count).
     * Backs the "N apps" row on each group card.
     * Groups with no apps are absent from the map (the UI treats that as 0).
     */
    private val _appCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val appCounts: StateFlow<Map<Long, Int>> = _appCounts.asStateFlow()

    /**
     * Groups sorted by interception frequency (most active first).
     * Falls back to creation order for groups with no recent interceptions.
     */
    private val _sortedGroups = MutableStateFlow<List<AppGroup>>(emptyList())
    val sortedGroups: StateFlow<List<AppGroup>> = _sortedGroups.asStateFlow()

    /** Whether the full group list is expanded (shows all groups, not just top 4). */
    private val _groupsExpanded = MutableStateFlow(false)
    val groupsExpanded: StateFlow<Boolean> = _groupsExpanded.asStateFlow()

    init {
        refreshServiceStatus()
        loadAppCounts()
    }

    /** Re-check the accessibility service status. Called when the screen resumes. */
    fun refreshServiceStatus() {
        _isServiceRunning.value = AccessibilityServiceChecker.isEnabled(getApplication())
        _canDrawOverlays.value = Settings.canDrawOverlays(getApplication())
        _isUsageAccessGranted.value = ForegroundChecker.isUsageAccessGranted(getApplication())
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isIgnoringBattery.value =
            pm?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) ?: false
    }

    /**
     * Load app counts and group interception frequency, then sort groups.
     * Called on init and again whenever the screen resumes.
     */
    fun loadAppCounts() {
        viewModelScope.launch {
            _appCounts.value = repository.getAppCounts()
            // Sort groups by 7-day interception frequency (most active first)
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            val counts = repository.getGroupInterceptionCounts(sevenDaysAgo)
            _sortedGroups.value = groups.value.sortedByDescending { group ->
                counts[group.id] ?: 0
            }
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
