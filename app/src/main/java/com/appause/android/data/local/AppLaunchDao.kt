package com.appause.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the app_launch_records table.
 *
 * This DAO is used to log interception events.
 * In v1, the data is only used for the debug screen (interception count).
 * Future versions may use it for usage statistics and charts.
 */
@Dao
interface AppLaunchDao {

    /** Log a new interception event. */
    @Insert
    suspend fun insertRecord(record: AppLaunchRecord)

    /** Get all records for a specific app, newest first. */
    @Query("SELECT * FROM app_launch_records WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getRecordsForPackage(packageName: String): List<AppLaunchRecord>

    /** Count how many times any app was intercepted today (since start of day). */
    @Query("SELECT COUNT(*) FROM app_launch_records WHERE timestamp >= :startOfDay")
    suspend fun countRecordsSince(startOfDay: Long): Int

    /** Count "proceeded" events since a given time (user completed the cooldown). */
    @Query("SELECT COUNT(*) FROM app_launch_records WHERE action = 'proceeded' AND timestamp >= :since")
    fun observeProceededCount(since: Long): Flow<Int>

    /** Count "cancelled" events since a given time (user backed out). */
    @Query("SELECT COUNT(*) FROM app_launch_records WHERE action = 'cancelled' AND timestamp >= :since")
    fun observeCancelledCount(since: Long): Flow<Int>

    /** Delete records older than a given timestamp (for cleanup). */
    @Query("DELETE FROM app_launch_records WHERE timestamp < :before")
    suspend fun deleteOldRecords(before: Long)

    // ── Statistics Queries ──

    /**
     * Daily interception stats grouped by date.
     * Used by the weekly bar chart to draw proceeded vs cancelled bars per day.
     *
     * strftime('%Y-%m-%d', timestamp/1000, 'unixepoch', 'localtime') converts
     * epoch milliseconds to a local date string like "2026-07-18".
     * COALESCE ensures the SUM is 0 (not null) when no rows match.
     */
    @Query("""
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(CASE WHEN action = 'proceeded' THEN 1 ELSE 0 END), 0) AS proceeded,
               COALESCE(SUM(CASE WHEN action = 'cancelled' THEN 1 ELSE 0 END), 0) AS cancelled
        FROM app_launch_records
        WHERE timestamp >= :since
        GROUP BY strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime')
        ORDER BY day ASC
    """)
    fun observeDailyStats(since: Long): Flow<List<DailyStats>>

    /**
     * Top 5 most-intercepted apps.
     * Used by the "Top Apps" list on the stats screen.
     * Ordered by count descending so the most intercepted app appears first.
     */
    @Query("""
        SELECT packageName, COUNT(*) AS interceptionCount
        FROM app_launch_records
        WHERE timestamp >= :since
        GROUP BY packageName
        ORDER BY interceptionCount DESC
        LIMIT 5
    """)
    fun observeTopApps(since: Long): Flow<List<AppInterceptionCount>>

    /**
     * Total proceeded vs cancelled ratio for a time range.
     * Used by the donut chart to show the overall split.
     * COALESCE wraps each SUM so that null becomes 0 when there are no records.
     */
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN action = 'proceeded' THEN 1 ELSE 0 END), 0) AS proceeded,
               COALESCE(SUM(CASE WHEN action = 'cancelled' THEN 1 ELSE 0 END), 0) AS cancelled
        FROM app_launch_records
        WHERE timestamp >= :since
    """)
    fun observeTotalRatio(since: Long): Flow<TotalRatio>

    /**
     * Interception count per group (last 7 days).
     * Joins launch records with group_apps to aggregate by groupId.
     * Used to sort groups by usage frequency on the Home screen.
     */
    @Query("""
        SELECT ga.groupId AS groupId, COUNT(*) AS interceptionCount
        FROM app_launch_records r
        JOIN group_apps ga ON r.packageName = ga.packageName
        WHERE r.timestamp >= :since
        GROUP BY ga.groupId
        ORDER BY interceptionCount DESC
    """)
    suspend fun getGroupInterceptionCounts(since: Long): List<GroupInterceptionCount>
}
