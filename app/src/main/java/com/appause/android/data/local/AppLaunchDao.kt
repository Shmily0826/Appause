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
}
