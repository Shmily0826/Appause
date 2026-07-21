package com.appause.android.data.local

/**
 * One row of the "how many apps are in each group" aggregate query.
 *
 * This is NOT a Room entity — it's a lightweight result container (POJO).
 * Room maps the query columns `groupId` and `appCount` onto these properties
 * by name, so the names must match the SELECT aliases exactly.
 *
 * Used by the Home screen to show "N apps" on each group card.
 */
data class GroupAppCount(
    /** The group's row ID (matches app_groups.id). */
    val groupId: Long,

    /** How many apps belong to that group. */
    val appCount: Int
)
