package com.appause.android.ui.groupeditor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppGroup
import com.appause.android.ui.appselect.AppSelectScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Group Editor Screen.
 *
 * Handles creating and editing app groups:
 * - Group name and cooldown seconds
 * - List of selected app package names
 * - Save (insert or update) and delete operations
 *
 * Lifecycle:
 * - Created when navigating to the Group Editor screen.
 * - Destroyed when navigating away (popped from back stack).
 * - If groupId > 0, loads existing group data on init.
 */
class GroupEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AppauseApp).repository

    // ── State ──

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _cooldownSeconds = MutableStateFlow(10)
    val cooldownSeconds: StateFlow<Int> = _cooldownSeconds.asStateFlow()

    private val _selectedPackages = MutableStateFlow<List<String>>(emptyList())
    val selectedPackages: StateFlow<List<String>> = _selectedPackages.asStateFlow()

    /** Whether we're editing an existing group (true) or creating a new one (false). */
    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private var existingGroupId: Long = 0L

    /** Whether the save operation completed (used to trigger navigation back). */
    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    // ── Initialization ──

    /**
     * Load an existing group for editing.
     * Called from the Screen's LaunchedEffect when groupId > 0.
     */
    fun loadGroup(groupId: Long) {
        if (groupId <= 0 || existingGroupId == groupId) return
        existingGroupId = groupId
        _isEditing.value = true

        viewModelScope.launch {
            val group = repository.getGroupById(groupId)
            if (group != null) {
                _name.value = group.name
                _cooldownSeconds.value = group.cooldownSeconds
                _selectedPackages.value = repository.getPackageNamesInGroup(groupId)
            }
        }
    }

    // ── User input ──

    fun updateName(newName: String) {
        _name.value = newName
    }

    fun updateCooldown(seconds: Int) {
        // Clamp to valid range: 1–300 seconds
        _cooldownSeconds.value = seconds.coerceIn(1, 300)
    }

    /**
     * Update the selected packages list.
     * Called when returning from the App Select screen.
     * Reads the cached result from AppSelectScreen.
     */
    fun refreshSelectedPackages() {
        AppSelectScreen.cachedSelectedPackages?.let { packages ->
            _selectedPackages.value = packages
            AppSelectScreen.cachedSelectedPackages = null
        }
    }

    // ── Actions ──

    /** Save the group (create new or update existing). */
    fun save() {
        val groupName = _name.value.trim()
        if (groupName.isEmpty()) return // Don't save empty names

        viewModelScope.launch {
            val group = AppGroup(
                id = existingGroupId,
                name = groupName,
                cooldownSeconds = _cooldownSeconds.value
            )
            repository.saveGroupWithApps(group, _selectedPackages.value)
            _saveCompleted.value = true
        }
    }

    /** Delete the existing group. */
    fun delete() {
        if (existingGroupId <= 0) return
        viewModelScope.launch {
            val group = repository.getGroupById(existingGroupId)
            if (group != null) {
                repository.deleteGroup(group)
            }
            _saveCompleted.value = true
        }
    }
}
