package com.appause.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity: records each interception event for debugging and future stats.
 *
 * Table name: "app_launch_records"
 *
 * Each row represents one time the user tried to open a target app:
 * - Which app (packageName)
 * - Which group triggered the interception (groupId)
 * - When it happened (timestamp)
 * - What the user did (action: "cancelled" or "proceeded")
 *
 * This data is useful for:
 * - Debug screen showing interception count
 * - Future features like usage statistics (out of scope for v1)
 */
@Entity(tableName = "app_launch_records")
data class AppLaunchRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Package name of the intercepted app. */
    val packageName: String,

    /** ID of the group that triggered the interception. */
    val groupId: Long,

    /** When the interception happened (epoch milliseconds). */
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * What the user did:
     * - "cancelled": user tapped Cancel and went back to home screen
     * - "proceeded": user waited for the countdown and tapped Continue
     */
    val action: String
)
