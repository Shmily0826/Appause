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
 *
 * The UI observes filteredApps and selectedPackages to render the screen.
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

    // ── User actions ──

    /** Update the search query. Triggers automatic re-filtering via combine(). */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Toggle an app's selection state. */
    fun toggleSelection(packageName: String) {
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
