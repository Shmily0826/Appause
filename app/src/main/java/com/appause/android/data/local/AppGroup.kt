package com.appause.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity: represents a user-created app group.
 *
 * A group has a name (e.g., "Social Media"), a cooldown duration in seconds,
 * and a list of target apps (stored in the separate GroupApp table).
 *
 * Table name: "app_groups"
 *
 * Why separate table for apps?
 * - One group can have many apps (1:N relationship).
 * - Storing apps in a separate table lets us enforce "one app = one group"
 *   using packageName as the primary key in GroupApp.
 * - It's cleaner than storing a comma-separated list of package names.
 */
@Entity(tableName = "app_groups")
data class AppGroup(
    /**
     * Auto-generated unique ID.
     * Room generates a new Long value for each inserted row.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Display name of the group (e.g., "Social Media", "Entertainment").
     */
    val name: String,

    /**
     * Cooldown duration in seconds. Range: 1–300.
     * When a target app is opened, the user must wait this many seconds
     * before they can proceed.
     */
    val cooldownSeconds: Int,

    /**
     * Timestamp when this group was created (epoch milliseconds).
     * Used for sorting groups by creation order.
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Group type: [TYPE_PAUSE] or [TYPE_LEARNING].
     *
     * - "pause": distracting apps. Opening one triggers the cooldown screen.
     * - "learning": recommended apps. They are NEVER intercepted; instead they
     *   are shown as suggestions on the cooldown screen ("try this instead").
     *
     * Stored as a plain String (not an enum) because Room persists it directly
     * and a String keeps the SQL migration simple.
     */
    val type: String = TYPE_PAUSE
) {
    companion object {
        /** Distracting apps that get intercepted with a cooldown. */
        const val TYPE_PAUSE = "pause"

        /** Learning apps that are recommended during cooldowns, never intercepted. */
        const val TYPE_LEARNING = "learning"
    }
}
