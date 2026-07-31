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
    val type: String = TYPE_PAUSE,

    /**
     * Re-remind interval in minutes. Range: 0–60.
     *
     * - 0 = disabled (no re-remind).
     * - 1–60 = after the user completes the cooldown and enters the app,
     *   the cooldown screen pops up again after this many minutes — but only
     *   if the user is still inside the target app.
     *
     * Only meaningful for [TYPE_PAUSE] groups. Learning groups are never
     * intercepted, so re-remind does not apply to them.
     */
    val reRemindMinutes: Int = 0,

    /**
     * Cooldown duration (in seconds) used for *re-remind* pops specifically.
     * Range: 0–300. A value of 0 means "use [cooldownSeconds]" (so existing
     * groups keep the old behaviour — re-remind pause length === first pause
     * length — until the user sets it explicitly).
     *
     * Only meaningful when [reRemindMinutes] > 0. Lets the user make the
     * repeat reminders shorter/longer than the initial cooldown.
     */
    val reRemindCooldownSeconds: Int = 0,

    /**
     * Whether the re-remind loop keeps popping the cooldown every [reRemindMinutes]
     * indefinitely (true) or only fires ONCE after the first interval (false).
     *
     * Default true keeps the original behaviour (re-peats forever while the user
     * stays in the app). Users who find the repeat annoying can turn it off so
     * they get a single nudge.
     */
    val reRemindRepeat: Boolean = true,

    /**
     * Whether each successive re-remind pop uses a LONGER cooldown. When true,
     * the Nth re-remind duration = base × N (1st = base×1, 2nd = base×2, 3rd =
     * base×3, …), where base is [reRemindCooldownSeconds] (or [cooldownSeconds]
     * if that is 0). Lets users escalate the friction the longer they linger.
     */
    val reRemindEscalate: Boolean = false
) {
    companion object {
        /** Distracting apps that get intercepted with a cooldown. */
        const val TYPE_PAUSE = "pause"

        /** Learning apps that are recommended during cooldowns, never intercepted. */
        const val TYPE_LEARNING = "learning"
    }
}
