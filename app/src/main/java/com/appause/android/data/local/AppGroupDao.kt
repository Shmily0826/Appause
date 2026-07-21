package com.appause.android.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for app_groups and group_apps tables.
 *
 * What is a DAO?
 * - A DAO is an interface that Room uses to generate SQL queries.
 * - You write the query as an annotation, Room generates the implementation at compile time.
 * - If your SQL is wrong, the build fails — this is one of Room's biggest advantages.
 *
 * Why return Flow?
 * - Flow<List<>> automatically emits new data when the table changes.
 * - The UI observes the Flow and updates itself — no manual refresh needed.
 * - This is the "reactive" pattern: data changes → UI updates automatically.
 */
@Dao
interface AppGroupDao {

    // ── app_groups queries ──

    /** Get all groups, newest first. Returns a Flow that updates automatically. */
    @Query("SELECT * FROM app_groups ORDER BY createdAt DESC")
    fun observeAllGroups(): Flow<List<AppGroup>>

    /** Get a single group by ID. Returns null if not found. */
    @Query("SELECT * FROM app_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Long): AppGroup?

    /** Insert a new group. Returns the auto-generated row ID. */
    @Insert
    suspend fun insertGroup(group: AppGroup): Long

    /** Update an existing group (matched by primary key: id). */
    @Update
    suspend fun updateGroup(group: AppGroup)

    /**
     * Delete a group. Because GroupApp has CASCADE delete on the foreign key,
     * all group_apps rows for this group are also deleted automatically.
     */
    @Delete
    suspend fun deleteGroup(group: AppGroup)

    // ── group_apps queries ──

    /** Get all package names in a specific group. */
    @Query("SELECT packageName FROM group_apps WHERE groupId = :groupId")
    suspend fun getPackageNamesInGroup(groupId: Long): List<String>

    /** Get all apps in a group as a Flow (for UI observation). */
    @Query("SELECT * FROM group_apps WHERE groupId = :groupId")
    fun observeAppsInGroup(groupId: Long): Flow<List<GroupApp>>

    /**
     * Find which group a package belongs to.
     * Returns null if the app is not in any group.
     * This is the key query used by the AccessibilityService to decide
     * whether to intercept an app launch.
     */
    @Query("SELECT groupId FROM group_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun findGroupIdForPackage(packageName: String): Long?

    /**
     * Insert an app-to-group mapping.
     * REPLACE strategy: if the same packageName already exists (in another group),
     * it gets replaced — effectively moving the app to the new group.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupApp(groupApp: GroupApp)

    /** Insert multiple app-to-group mappings at once (for bulk operations). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupApps(groupApps: List<GroupApp>)

    /** Remove an app from its group. */
    @Query("DELETE FROM group_apps WHERE packageName = :packageName")
    suspend fun removeAppFromGroup(packageName: String)

    /** Remove all apps from a specific group (used when editing group membership). */
    @Query("DELETE FROM group_apps WHERE groupId = :groupId")
    suspend fun removeAllAppsFromGroup(groupId: Long)

    /**
     * Get all package names that are assigned to any group.
     * Used by the "recommended apps" feature — shows apps the user has
     * already added to other groups as quick-pick suggestions.
     */
    @Query("SELECT packageName FROM group_apps")
    suspend fun getAllGroupedPackageNames(): List<String>

    /**
     * Get package names of apps that belong to "learning" groups only.
     *
     * Learning groups hold apps the user WANTS to use (e.g. study apps).
     * These are shown as suggestions on the cooldown screen ("try this
     * instead") and are never intercepted themselves.
     */
    @Query(
        "SELECT packageName FROM group_apps WHERE groupId IN " +
            "(SELECT id FROM app_groups WHERE type = 'learning')"
    )
    suspend fun getLearningGroupPackageNames(): List<String>
}
