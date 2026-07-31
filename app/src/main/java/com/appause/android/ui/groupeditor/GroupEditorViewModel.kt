package com.appause.android.ui.groupeditor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppGroup
import com.appause.android.data.pro.ProState
import com.appause.android.ui.appselect.AppSelectScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
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
    private val proState = (application as AppauseApp).proState

    /** Whether Appause Pro is unlocked (drives paid-feature gating in the UI). */
    val isPro: StateFlow<Boolean> = proState.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Maximum cooldown the current user may set.
     * Free tier is capped at [ProState.FREE_COOLDOWN_MAX_SECONDS];
     * Pro raises it to [ProState.PRO_COOLDOWN_MAX_SECONDS].
     */
    val maxCooldown: StateFlow<Int> = isPro.map { isProUnlocked ->
        if (isProUnlocked) ProState.PRO_COOLDOWN_MAX_SECONDS else ProState.FREE_COOLDOWN_MAX_SECONDS
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProState.FREE_COOLDOWN_MAX_SECONDS)

    // ── State ──

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _cooldownSeconds = MutableStateFlow(10)
    val cooldownSeconds: StateFlow<Int> = _cooldownSeconds.asStateFlow()

    /**
     * Group type: [AppGroup.TYPE_PAUSE] or [AppGroup.TYPE_LEARNING].
     * Defaults to "pause" (the classic distracting-app group).
     */
    private val _type = MutableStateFlow(AppGroup.TYPE_PAUSE)
    val type: StateFlow<String> = _type.asStateFlow()

    /**
     * Whether re-remind is enabled. Separate from the interval value so that
     * turning it off doesn't lose the user's chosen interval.
     */
    private val _reRemindEnabled = MutableStateFlow(false)
    val reRemindEnabled: StateFlow<Boolean> = _reRemindEnabled.asStateFlow()

    /**
     * Re-remind interval in minutes (1–60). Always holds a valid value even
     * when re-remind is disabled, so re-enabling restores the last setting.
     * Persisted as 0 in the DB when disabled.
     */
    private val _reRemindMinutes = MutableStateFlow(10)
    val reRemindMinutes: StateFlow<Int> = _reRemindMinutes.asStateFlow()

    /**
     * Dedicated cooldown length (seconds) for re-remind pops. 0 means "reuse
     * the first cooldown" — the default so existing groups keep old behaviour
     * until the user sets it. When > 0 the re-remind pause uses this instead
     * of [cooldownSeconds].
     */
    private val _reRemindCooldownSeconds = MutableStateFlow(0)
    val reRemindCooldownSeconds: StateFlow<Int> = _reRemindCooldownSeconds.asStateFlow()

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
                _type.value = group.type
                // DB stores 0 = disabled, 1–60 = enabled with that interval.
                _reRemindEnabled.value = group.reRemindMinutes > 0
                _reRemindMinutes.value = group.reRemindMinutes.coerceIn(1, 60)
                // 0 = reuse first cooldown (legacy default).
                _reRemindCooldownSeconds.value = group.reRemindCooldownSeconds.coerceAtLeast(0)
                _selectedPackages.value = repository.getPackageNamesInGroup(groupId)
            }
        }
    }

    // ── User input ──

    fun updateName(newName: String) {
        _name.value = newName
    }

    fun updateType(newType: String) {
        _type.value = newType
    }

    fun updateCooldown(seconds: Int) {
        // Clamp to valid range for the current tier (free 1–30, pro 1–60).
        _cooldownSeconds.value = seconds.coerceIn(1, maxCooldown.value)
    }

    fun updateReRemindEnabled(enabled: Boolean) {
        _reRemindEnabled.value = enabled
    }

    fun updateReRemind(minutes: Int) {
        // Clamp to valid range: 1–60 minutes
        _reRemindMinutes.value = minutes.coerceIn(1, 60)
    }

    /**
     * Update the re-remind cooldown length. -1 (or any negative) is treated as
     * "reuse first cooldown" and stored as 0. Otherwise clamped to 1–300s.
     */
    fun updateReRemindCooldown(seconds: Int) {
        _reRemindCooldownSeconds.value = if (seconds <= 0) 0 else seconds.coerceIn(1, 300)
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

    /**
     * Remove a single app from the current selection.
     * Backs the "remove" (×) button on each app row in the editor.
     */
    fun removePackage(packageName: String) {
        _selectedPackages.value = _selectedPackages.value.filter { it != packageName }
    }

    // ── Actions ──

    /** Save the group (create new or update existing). */
    fun save() {
        val groupName = _name.value.trim()
        if (groupName.isEmpty()) return // Don't save empty names

        viewModelScope.launch {
            // Re-remind is a Pro feature. Free users can never persist it, even
            // if a legacy value was loaded from the DB — force it off here.
            val reRemindOn = if (isPro.value) _reRemindEnabled.value else false
            val group = AppGroup(
                id = existingGroupId,
                name = groupName,
                cooldownSeconds = _cooldownSeconds.value,
                type = _type.value,
                // DB: 0 = disabled, 1–60 = enabled with that interval
                reRemindMinutes = if (reRemindOn) _reRemindMinutes.value else 0,
                // 0 = reuse first cooldown when re-remind is off too.
                reRemindCooldownSeconds = if (reRemindOn) _reRemindCooldownSeconds.value else 0
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
