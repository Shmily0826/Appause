package com.appause.android.data.local

/**
 * One day's worth of interception statistics.
 * Used by the weekly bar chart to show proceeded vs cancelled per day.
 *
 * @param day Date in "YYYY-MM-DD" format (from SQLite strftime).
 * @param proceeded Number of times the user completed the cooldown.
 * @param cancelled Number of times the user backed out.
 */
data class DailyStats(
    val day: String,
    val proceeded: Int,
    val cancelled: Int
)

/**
 * Overall proceeded vs cancelled counts for a time range.
 * Used by the donut chart to show the total ratio.
 *
 * COALESCE in the SQL query ensures these are never null,
 * even when there are no records.
 */
data class TotalRatio(
    val proceeded: Int,
    val cancelled: Int
)

/**
 * A single app's interception count.
 * Used by the "Top Apps" list to show which apps get intercepted most.
 *
 * @param packageName The app's package name (icon/name loaded from PackageManager in UI).
 * @param interceptionCount Total number of times this app was intercepted.
 */
data class AppInterceptionCount(
    val packageName: String,
    val interceptionCount: Int
)

/**
 * Count of "proceeded" events grouped by the reason the user selected.
 * Used by the stats screen to show why distracting apps were opened.
 *
 * @param reason The stable reason key chosen by the user
 *               ("work"/"bored"/"messages"/"other"). Display labels are resolved
 *               at UI time (localized default, or the Pro custom text).
 * @param count  How many times this reason was picked.
 */
data class ReasonCount(
    val reason: String,
    val count: Int
)

/** Interception count per group (for sorting groups by usage frequency). */
data class GroupInterceptionCount(
    val groupId: Long,
    val interceptionCount: Int
)
