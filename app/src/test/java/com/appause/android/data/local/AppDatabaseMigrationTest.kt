package com.appause.android.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration tests for [AppDatabase] using Room's OFFICIAL MigrationTestHelper.
 *
 * The app has shipped 6 schema versions and a broken migration loses the
 * user's interception history and statistics — data that is *not* renewable.
 * Two layers of protection:
 *
 * 1. [migrate1To6_validatesAgainstExportedSchema_keepsAllData] — creates a
 *    real v1 database FROM THE EXPORTED 1.json (no hand-written CREATE
 *    TABLE), runs the real MIGRATION_1_2..MIGRATION_5_6, and validates the
 *    result against the exported 6.json: a migration that forgets a column,
 *    writes a wrong default, or leaves a stale identity hash FAILS here.
 *
 * 2. [everyVersionStepHasAMigration] — the "forgot the migration" guard.
 *    Reflects over AppDatabase's MIGRATION_* constants and asserts every
 *    adjacent step from v1 up to the @Database version is covered with no
 *    gaps, so bumping `version` without adding a migration fails in CI
 *    instead of crashing the app on a real user's phone at upgrade time.
 *
 * The schema JSONs are read as unit-test assets (see the `test` sourceSet
 * in app/build.gradle.kts). Runs on the JVM under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To6_validatesAgainstExportedSchema_keepsAllData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(DB_NAME)

        // 1) Build a v1 database from the exported 1.json and seed it with
        //    representative data (v1 has no type/reason/reRemind columns yet).
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO app_groups (id, name, cooldownSeconds, createdAt) VALUES (1, 'Social', 30, 1000)")
            db.execSQL("INSERT INTO app_groups (id, name, cooldownSeconds, createdAt) VALUES (2, 'Games', 60, 2000)")
            db.execSQL("INSERT INTO group_apps (packageName, groupId) VALUES ('com.tiktok', 1)")
            db.execSQL("INSERT INTO group_apps (packageName, groupId) VALUES ('com.instagram', 1)")
            db.execSQL("INSERT INTO group_apps (packageName, groupId) VALUES ('com.game', 2)")
            db.execSQL("INSERT INTO app_launch_records (id, packageName, groupId, timestamp, action) VALUES (1, 'com.tiktok', 1, 5000, 'proceeded')")
            db.execSQL("INSERT INTO app_launch_records (id, packageName, groupId, timestamp, action) VALUES (2, 'com.instagram', 1, 6000, 'cancelled')")
            db.execSQL("INSERT INTO app_launch_records (id, packageName, groupId, timestamp, action) VALUES (3, 'com.game', 2, 7000, 'proceeded')")
        }

        // 2) Reopen and migrate to v6 — runMigrationsAndValidate checks the
        //    final schema against the exported 6.json (columns, defaults,
        //    identity hash) IN ADDITION to running our real migrations.
        val db = helper.runMigrationsAndValidate(
            DB_NAME, 6, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6
        )

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
    }

    @Test
    fun everyVersionStepHasAMigration() {
        // The schema version Room will actually create/upgrade to — read from
        // a real in-memory instance instead of hardcoding, so this test cannot
        // drift from AppDatabase's @Database(version = N).
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val currentVersion = db.openHelper.writableDatabase.version
        db.close()

        // MIGRATION_* live as Kotlin companion vals, which the compiler emits
        // as getMIGRATION_*() GETTERS (no backing fields — javap-verified), so
        // reflect over methods and read the authoritative start/end versions
        // from the Migration objects themselves.
        val companion = AppDatabase.Companion
        val steps = AppDatabase.Companion::class.java.declaredMethods
            .filter { it.name.startsWith("getMIGRATION_") && it.parameterCount == 0 }
            .map { method ->
                method.isAccessible = true
                val migration = method.invoke(companion) as androidx.room.migration.Migration
                migration.startVersion to migration.endVersion
            }
            .sortedBy { it.first }

        // Every adjacent step from v1 to the current version must have exactly
        // one migration: no gaps ("forgot MIGRATION_5_6"), no duplicates, and
        // the chain must END at the declared version (bumped `version` without
        // a new migration = runtime crash for every upgrading user).
        val expected = (1 until currentVersion).map { it to it + 1 }
        assertEquals(
            "AppDatabase v$currentVersion needs one MIGRATION_<n>_<n+1> per step: " +
                "expected $expected but found $steps",
            expected,
            steps
        )
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
