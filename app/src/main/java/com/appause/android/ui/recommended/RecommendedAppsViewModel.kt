package com.appause.android.ui.recommended

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.ui.appselect.AppSelectScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Recommended Apps screen.
 *
 * Manages the global list of recommended apps shown during cooldowns.
 * Stored in DataStore as a simple set of package names.
 */
class RecommendedAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AppauseApp).repository

    private val _packages = MutableStateFlow<Set<String>>(emptySet())
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    init {
        viewModelScope.launch {
            repository.recommendedApps.collect { _packages.value = it }
        }
    }

    /** Read cached selection returning from AppSelectScreen. */
    fun refreshFromCache() {
        AppSelectScreen.cachedSelectedPackages?.let { selected ->
            viewModelScope.launch {
                repository.setRecommendedApps(selected.toSet())
            }
            AppSelectScreen.cachedSelectedPackages = null
        }
    }

    /** Remove a single app from the recommended list. */
    fun removePackage(packageName: String) {
        viewModelScope.launch {
            repository.setRecommendedApps(_packages.value - packageName)
        }
    }
}
