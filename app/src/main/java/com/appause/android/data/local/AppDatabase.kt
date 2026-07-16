package com.appause.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room Database — the central database class for Appause.
 *
 * What is RoomDatabase?
 * - It's an abstraction over SQLite that provides compile-time SQL verification.
 * - You define entities (tables), DAOs (queries), and Room generates the SQLite code.
 * - The @Database annotation lists all entities and sets the schema version.
 *
 * Why singleton pattern?
 * - Creating a database instance is expensive (opens a file, creates connections).
 * - We only want ONE instance shared across the entire app.
 * - The companion object's getInstance() creates it once and reuses it.
 *
 * @TypeConverters:
 * - Tells Room about custom type converters (see Converters.kt).
 * - Currently our entities only use simple types, but we register the converter
 *   for future use.
 */
@Database(
    entities = [
        AppGroup::class,
        GroupApp::class,
        AppLaunchRecord::class
    ],
    version = 1,
    exportSchema = false  // Simplified for v1; enable for production migration tracking
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /** DAO for app_groups and group_apps tables. */
    abstract fun appGroupDao(): AppGroupDao

    /** DAO for app_launch_records table. */
    abstract fun appLaunchDao(): AppLaunchDao

    companion object {
        /**
         * Singleton instance.
         *
         * @Volatile ensures that changes to this variable are immediately visible
         * to all threads (prevents stale reads in multi-threaded access).
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance.
         * Creates it on first call, returns the cached instance on subsequent calls.
         *
         * synchronized(this) prevents two threads from creating two instances
         * at the same time (double-checked locking pattern).
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "appause.db"
                )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
