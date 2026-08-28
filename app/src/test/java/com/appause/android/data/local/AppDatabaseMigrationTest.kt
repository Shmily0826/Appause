package com.appause.android.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration test for [AppDatabase]: the app has shipped 6 schema versions and
 * until now every migration was verified only by hand. A broken migration loses
 * the user's interception history and statistics — data that is *not*
 * renewable, so this test locks in "v1 database -> v6, nothing lost".
 *
 * It opens a v1 database (hand-built v1 table shape, seeded with representative
 * data) and then applies the REAL migration objects
 * [AppDatabase.MIGRATION_1_2] .. [AppDatabase.MIGRATION_5_6] exactly as Room
 * would at upgrade time. Finally it asserts every row survived and the newly
 * added columns carry their documented defaults. The real migration code is
 * exercised end-to-end; no Room schema-JSON assets are needed, so the test is
 * portable across JVM unit-test runners.
 *
 * Runs on the JVM under Robolectric (no device/emulator needed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    @Test
    fun migrate1To6_keepsAllDataAndFillsNewColumnDefaults() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-test"
        context.deleteDatabase(dbName)

        // 1) Build a v1 database with the exact v1 table shape and seed it.
        val v1Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE app_groups (
                               id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                               name TEXT NOT NULL,
                               cooldownSeconds INTEGER NOT NULL,
                               createdAt INTEGER NOT NULL)"""
                        )
                        db.execSQL(
                            """CREATE TABLE group_apps (
                               packageName TEXT NOT NULL,
                               groupId INTEGER NOT NULL,
                               PRIMARY KEY(packageName),
                               FOREIGN KEY(groupId) REFERENCES app_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                        )
                        db.execSQL(
                            """CREATE TABLE app_launch_records (
                               id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                               packageName TEXT NOT NULL,
                               groupId INTEGER NOT NULL,
                               timestamp INTEGER NOT NULL,
                               action TEXT NOT NULL)"""
                        )
                        // 2 groups, 3 mapped apps, 3 launch records.
                        db.execSQL("INSERT INTO app_groups (id, name, cooldownSeconds, createdAt) VALUES (1, 'Social', 30, 1000)")
                        db.execSQL("INSERT INTO app_groups (id, name, cooldownSeconds, createdAt) VALUES (2, 'Games', 60, 2000)")
                        db.execSQL("INSERT INTO group_apps (packageName, groupId) VALUES ('com.tiktok', 1)")
                        db.execSQL("INSERT INTO group_apps (packageName, groupId) VALUES ('com.instagram', 1)")
                        db.execSQL("INSERT INTO group_apps (packageName, groupId) VALUES ('com.game', 2)")
                        db.execSQL("INSERT INTO app_launch_records (id, packageName, groupId, timestamp, action) VALUES (1, 'com.tiktok', 1, 5000, 'proceeded')")
                        db.execSQL("INSERT INTO app_launch_records (id, packageName, groupId, timestamp, action) VALUES (2, 'com.instagram', 1, 6000, 'cancelled')")
                        db.execSQL("INSERT INTO app_launch_records (id, packageName, groupId, timestamp, action) VALUES (3, 'com.game', 2, 7000, 'proceeded')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldV: Int, newV: Int) {
                        // No-op: this helper only ever creates at v1.
                    }
                })
                .build()
        )
        v1Helper.writableDatabase.close()
        v1Helper.close()

        // 2) Reopen the SAME database at v6 and apply the real migrations in order.
        val migrateHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // DB already exists at v1; onCreate is not called on upgrade.
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldV: Int, newV: Int) {
                        // Room applies every migration whose [startVersion, endVersion]
                        // is crossed. We apply them in sequence, exactly as Room does.
                        AppDatabase.MIGRATION_1_2.migrate(db)
                        AppDatabase.MIGRATION_2_3.migrate(db)
                        AppDatabase.MIGRATION_3_4.migrate(db)
                        AppDatabase.MIGRATION_4_5.migrate(db)
                        AppDatabase.MIGRATION_5_6.migrate(db)
                    }
                })
                .build()
        )
        val db = migrateHelper.writableDatabase

        // --- app_groups: original columns preserved, new columns get defaults ---
        db.query("SELECT * FROM app_groups ORDER BY id").use { cursor ->
            assertEquals("expected 2 groups after migration", 2, cursor.count)

            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            assertEquals("Social", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(30, cursor.getInt(cursor.getColumnIndexOrThrow("cooldownSeconds")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
            // Columns added by migrations 2_3 .. 5_6 get their documented defaults:
            assertEquals("pause", cursor.getString(cursor.getColumnIndexOrThrow("type")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("reRemindMinutes")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("reRemindCooldownSeconds")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("reRemindRepeat")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("reRemindEscalate")))

            cursor.moveToNext()
            assertEquals(2L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            assertEquals("Games", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(60, cursor.getInt(cursor.getColumnIndexOrThrow("cooldownSeconds")))
            assertEquals(2000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
        }

        // --- group_apps: FK mappings preserved (no data loss / no orphan rows) ---
        db.query("SELECT * FROM group_apps").use { cursor ->
            assertEquals("expected 3 group_app mappings after migration", 3, cursor.count)
        }
        db.query("SELECT * FROM group_apps WHERE packageName = 'com.tiktok'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("groupId")))
        }

        // --- app_launch_records: original columns preserved, reason defaults to '' ---
        db.query("SELECT * FROM app_launch_records ORDER BY id").use { cursor ->
            assertEquals("expected 3 launch records after migration", 3, cursor.count)
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            assertEquals("com.tiktok", cursor.getString(cursor.getColumnIndexOrThrow("packageName")))
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("groupId")))
            assertEquals(5000L, cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))
            assertEquals("proceeded", cursor.getString(cursor.getColumnIndexOrThrow("action")))
            // reason column added by migration 1_2 defaults to empty string.
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("reason")))
        }

        db.close()
        migrateHelper.close()
    }
}
