package com.appause.android.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity: maps an app (by packageName) to a group.
 *
 * Table name: "group_apps"
 *
 * Why is packageName the primary key?
 * - This enforces "one app can only belong to one group" at the database level.
 * - If we tried to insert the same packageName with a different groupId,
 *   Room would throw a conflict (which we handle with onConflict = REPLACE).
 *
 * Foreign key: groupId references app_groups.id.
 * - When a group is deleted, all its group_apps rows are also deleted (CASCADE).
 * - This prevents orphaned entries.
 *
 * Index on groupId:
 * - Speeds up queries like "get all apps in group X".
 * - Room requires an index on foreign key columns.
 *
 * IMPORTANT: We store only the packageName, NEVER the app icon or binary data.
 * App icons are loaded on-demand from PackageManager in the UI layer.
 */
@Entity(
    tableName = "group_apps",
    foreignKeys = [
        ForeignKey(
            entity = AppGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class GroupApp(
    /**
     * The target app's package name (e.g., "com.example.app").
     * This is the primary key — one app, one group.
     */
    @PrimaryKey
    val packageName: String,

    /**
     * ID of the group this app belongs to.
     * Foreign key to app_groups.id with CASCADE delete.
     */
    val groupId: Long
)
