package com.appause.android.data.repository

import com.appause.android.data.local.AppGroup
import com.appause.android.data.local.AppGroupDao
import com.appause.android.data.local.AppInterceptionCount
import com.appause.android.data.local.AppLaunchDao
import com.appause.android.data.local.AppLaunchRecord
import com.appause.android.data.local.DailyStats
import com.appause.android.data.local.GroupApp
import com.appause.android.data.local.TotalRatio
import com.appause.android.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.Flow

/**
 * Repository — the single source of truth for app group data.
 *
 * What is a Repository?
 * - It sits between the data sources (Room DAO, DataStore) and the UI (ViewModels).
 * - ViewModels NEVER call DAO directly — they always go through the Repository.
 * - This makes it easy to change data sources later (e.g., add cloud sync)
 *   without modifying any UI code.
 *
 * Why not call DAO directly from ViewModel?
 * - Separation of concerns: ViewModel handles UI logic, Repository handles data logic.
 * - Testability: you can mock the Repository in ViewModel tests.
 * - Single source of truth: all data operations go through one place.
 *
 * This repository combines three data sources:
 * 1. AppGroupDao — groups and their apps (Room)
 * 2. AppLaunchDao — interception records (Room)
 * 3. SettingsDataStore — simple settings (DataStore)
 */
class AppGroupRepository(
    private val groupDao: AppGroupDao,
    private val launchDao: AppLaunchDao,
    private val settings: SettingsDataStore
) {

    // ── Group operations ──

    /** Observe all groups. The Flow emits whenever the table changes. */
    fun observeAllGroups(): Flow<List<AppGroup>> = groupDao.observeAllGroups()

    /** Get a single group by ID. Returns null if not found. */
    suspend fun getGroupById(id: Long): AppGroup? = groupDao.getGroupById(id)

    /**
     * Save a group with its app list.
     *
     * If the group is new (id == 0): inserts the group, then inserts all apps.
     * If the group exists (id > 0): updates the group, replaces all apps.
     *
     * Why replace all apps?
     * - When editing a group, the user may add AND remove apps.
     * - It's simpler to delete all existing apps and re-insert the new list
     *   than to figure out which were added, removed, or unchanged.
     * - This is safe because we're inside a single user action (save button tap).
     */
    suspend fun saveGroupWithApps(group: AppGroup, packageNames: List<String>): Long {
        val groupId: Long = if (group.id == 0L) {
            // New group — insert and get the generated ID
            groupDao.insertGroup(group)
        } else {
            // Existing group — update
            groupDao.updateGroup(group)
            group.id
        }

        // Replace all apps for this group
        groupDao.removeAllAppsFromGroup(groupId)
        val groupApps = packageNames.map { packageName ->
            GroupApp(packageName = packageName, groupId = groupId)
        }
        groupDao.insertGroupApps(groupApps)

        return groupId
    }

    /** Delete a group and all its apps (CASCADE handles the apps automatically). */
    suspend fun deleteGroup(group: AppGroup) {
        groupDao.deleteGroup(group)
    }

    // ── App-in-group queries ──

    /** Get all package names in a specific group. */
    suspend fun getPackageNamesInGroup(groupId: Long): List<String> {
        return groupDao.getPackageNamesInGroup(groupId)
    }

    /**
     * Find which group an app belongs to, for interception purposes.
     * Returns null if the app is not in any group, OR if it belongs to a
     * "learning" group — learning apps are recommendations, never distractions,
     * so they must never trigger the cooldown screen.
     * This is called by the AccessibilityService for every app launch.
     */
    suspend fun findGroupForPackage(packageName: String): AppGroup? {
        val groupId = groupDao.findGroupIdForPackage(packageName) ?: return null
        val group = groupDao.getGroupById(groupId) ?: return null
        if (group.type == AppGroup.TYPE_LEARNING) return null
        return group
    }

    /** Get all package names that are assigned to any group (for recommended apps). */
    suspend fun getAllGroupedPackageNames(): List<String> {
        return groupDao.getAllGroupedPackageNames()
    }

    /**
     * Get package names of apps in "learning" groups.
     * These are the apps shown as suggestions on the cooldown screen.
     */
    suspend fun getLearningGroupPackageNames(): List<String> {
        return groupDao.getLearningGroupPackageNames()
    }

    // ── Launch records ──

    /**
     * Log an interception event.
     * @param reason Why the user opened the app (empty for cancellations).
     */
    suspend fun logLaunch(packageName: String, groupId: Long, action: String, reason: String = "") {
        launchDao.insertRecord(
            AppLaunchRecord(
                packageName = packageName,
                groupId = groupId,
                action = action,
                reason = reason
            )
        )
    }

    /** Count interceptions since a given timestamp (e.g., start of today). */
    suspend fun countLaunchesSince(startOfDay: Long): Int {
        return launchDao.countRecordsSince(startOfDay)
    }

    /** Observe how many times users completed the cooldown (today). */
    fun observeProceededCount(since: Long): Flow<Int> = launchDao.observeProceededCount(since)

    /** Observe how many times users cancelled (today). */
    fun observeCancelledCount(since: Long): Flow<Int> = launchDao.observeCancelledCount(since)

    // ── Settings ──

    /** Observe the master toggle state. */
    val isEnabled: Flow<Boolean> = settings.isEnabled

    /** Observe the default prompt message. */
    val defaultPrompt: Flow<String> = settings.defaultPrompt

    /** Observe the language preference. */
    val language: Flow<String> = settings.language

    /** Update the master toggle. */
    suspend fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    /** Update the default prompt message. */
    suspend fun setDefaultPrompt(prompt: String) = settings.setDefaultPrompt(prompt)

    /** Update the language preference. */
    suspend fun setLanguage(languageCode: String) = settings.setLanguage(languageCode)

    // ── Statistics ──

    /** Observe daily interception stats (proceeded vs cancelled per day). */
    fun observeDailyStats(since: Long): Flow<List<DailyStats>> =
        launchDao.observeDailyStats(since)

    /** Observe top 5 most-intercepted apps. */
    fun observeTopApps(since: Long): Flow<List<AppInterceptionCount>> =
        launchDao.observeTopApps(since)

    /** Observe total proceeded vs cancelled ratio. */
    fun observeTotalRatio(since: Long): Flow<TotalRatio> =
        launchDao.observeTotalRatio(since)
}
