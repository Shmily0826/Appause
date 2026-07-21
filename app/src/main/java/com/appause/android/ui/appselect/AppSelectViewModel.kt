package com.appause.android.ui.appselect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.query.AppInfo
import com.appause.android.data.query.AppQueryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the App Selection screen.
 *
 * This screen shows all launchable apps on the device and lets the user
 * pick which ones to include in a group.
 *
 * Why AndroidViewModel instead of ViewModel?
 * - We need Context to access PackageManager (for querying installed apps).
 * - AndroidViewModel provides `application` context safely.
 * - Regular ViewModel must NEVER store Context — it can cause memory leaks.
 *
 * State management:
 * - allApps: the full list of launchable apps (loaded once from PackageManager)
 * - searchQuery: the current search text (typed by the user)
 * - selectedPackages: which apps the user has checked (multi-select)
 * - filteredApps: derived from allApps + searchQuery (shown in the UI)
 * - isLoading: whether the app list is still being loaded
 * - takenPackages: apps already in OTHER groups (shown but not selectable,
 *   because an app can only belong to one group)
 *
 * The UI observes filteredApps, selectedPackages, and takenPackages to render the screen.
 */
class AppSelectViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AppauseApp).repository
    private val appQueryService = AppQueryService(application)

    // ── State ──

    /** All launchable apps (loaded once, then cached). */
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    /** Current search query. */
    private val _searchQuery = MutableStateFlow("")

    /** Set of currently selected package names. */
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())

    /** Whether the app list is still loading. */
    private val _isLoading = MutableStateFlow(true)

    /**
     * Package names already assigned to OTHER groups.
     *
     * An app can only belong to ONE group (group_apps uses packageName as its
     * primary key). If we let the user check an app that's already in another
     * group, saving would silently MOVE it here (Room's REPLACE strategy) and
     * the other group would lose it — that's the "groups interfere with each
     * other" bug. So these apps are shown but NOT selectable.
     */
    private val _takenPackages = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Filtered list: allApps filtered by searchQuery.
     * Uses combine() so it automatically updates when either allApps or searchQuery changes.
     */
    val filteredApps: StateFlow<List<AppInfo>> =
        combine(_allApps, _searchQuery) { apps, query ->
            if (query.isBlank()) {
                apps
            } else {
                val lowerQuery = query.lowercase()
                apps.filter { app ->
                    app.appName.lowercase().contains(lowerQuery) ||
                        app.packageName.lowercase().contains(lowerQuery)
                }
            }
        }.let { flow ->
            // Convert Flow → StateFlow with an initial value
            MutableStateFlow<List<AppInfo>>(emptyList()).also { stateFlow ->
                viewModelScope.launch {
                    flow.collect { stateFlow.value = it }
                }
            }
        }

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val takenPackages: StateFlow<Set<String>> = _takenPackages.asStateFlow()

    // ── Initialization ──

    init {
        loadApps()
    }

    /**
     * Load all launchable apps from PackageManager.
     *
     * Why withContext(Dispatchers.IO)?
     * - PackageManager.queryIntentActivities() can take 100ms+ on devices with many apps.
     * - We MUST NOT block the main thread (it causes jank or ANR).
     * - Dispatchers.IO is a thread pool optimized for disk/network operations.
     */
    private fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                appQueryService.getLaunchableApps()
            }
            _allApps.value = apps
            _isLoading.value = false
        }
    }

    /**
     * Compute which apps are already taken by OTHER groups.
     *
     * @param currentGroupPackages apps that already belong to the group being
     * edited — these stay selectable (they're this group's own apps).
     *
     * Everything else that appears in group_apps belongs to a different group
     * and is marked "taken" so the user can't accidentally steal it.
     */
    fun loadTakenPackages(currentGroupPackages: Set<String>) {
        viewModelScope.launch {
            val taken = withContext(Dispatchers.IO) {
                repository.getAllGroupedPackageNames().toSet() - currentGroupPackages
            }
            _takenPackages.value = taken
        }
    }

    // ── User actions ──

    /** Update the search query. Triggers automatic re-filtering via combine(). */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Toggle an app's selection state. Apps taken by other groups are ignored. */
    fun toggleSelection(packageName: String) {
        // Apps that already belong to another group can't be selected — see takenPackages.
        if (packageName in _takenPackages.value) return
        val current = _selectedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _selectedPackages.value = current
    }

    /** Select all currently visible (filtered) apps. */
    fun selectAll() {
        _selectedPackages.value = filteredApps.value.map { it.packageName }.toSet()
    }

    /** Deselect all apps. */
    fun deselectAll() {
        _selectedPackages.value = emptySet()
    }

    /**
     * Pre-select apps that are already in the group being edited.
     * Called when the screen opens with an existing group.
     */
    fun preSelectPackages(packageNames: Set<String>) {
        _selectedPackages.value = packageNames
    }

    /** Get the final list of selected package names (to pass back to the group editor). */
    fun getSelectedPackageNames(): List<String> {
        return _selectedPackages.value.toList()
    }
}
