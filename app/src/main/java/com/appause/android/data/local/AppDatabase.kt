package com.appause.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
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
         * Migration from version 1 to 2: add "reason" column for intent tracking.
         *
         * Why ALTER TABLE instead of recreating?
         * - We cannot drop and recreate the table because we would lose all
         *   existing launch records (the user's history).
         * - NOT NULL DEFAULT '' ensures old rows get an empty string,
         *   matching the Kotlin default value.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_launch_records ADD COLUMN reason TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * Migration from version 2 to 3: add "type" column to app_groups.
         *
         * Existing groups all become "pause" groups (the classic behavior —
         * they get intercepted). The new "learning" type is opt-in: users
         * create learning groups to collect apps that are recommended
         * during cooldowns instead of being intercepted.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_groups ADD COLUMN type TEXT NOT NULL DEFAULT 'pause'"
                )
            }
        }
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
