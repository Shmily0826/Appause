package com.appause.android.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.appause.android.data.local.AppDatabase
import com.appause.android.data.local.AppGroup
import com.appause.android.data.local.AppLaunchRecord
import com.appause.android.data.settings.FakeSettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Branch-logic tests for [AppGroupRepository], run against a REAL in-memory
 * Room database (Robolectric, no emulator). We deliberately exercise the true
 * DAO SQL rather than fake DAOs so the queries themselves are covered.
 *
 * Only the three methods that carry real branching are pinned here:
 *  - findGroupForPackage  (learning groups must never trigger the cooldown)
 *  - saveGroupWithApps    (new-insert vs existing-update + replace-all apps)
 *  - deleteOldLaunchRecords (the 365-day retention boundary)
 * Everything else is a thin one-line delegation to the DAO and adds no value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppGroupRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AppGroupRepository

    // 365 days in ms — mirrors AppGroupRepository.RECORD_RETENTION_MS.
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppGroupRepository(
            groupDao = db.appGroupDao(),
            launchDao = db.appLaunchDao(),
            settings = FakeSettingsDataStore(context)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── findGroupForPackage ──

    @Test
    fun `findGroupForPackage returns the pause group that owns the app`() = runBlocking {
        val groupId = repository.saveGroupWithApps(
            AppGroup(name = "Social", cooldownSeconds = 30, type = AppGroup.TYPE_PAUSE),
            listOf("com.tiktok")
        )

        val found = repository.findGroupForPackage("com.tiktok")

        assertEquals(groupId, found?.id)
        assertEquals("Social", found?.name)
    }

    @Test
    fun `findGroupForPackage returns null for a learning group`() = runBlocking {
        repository.saveGroupWithApps(
            AppGroup(name = "Study", cooldownSeconds = 0, type = AppGroup.TYPE_LEARNING),
            listOf("com.duolingo")
        )

        // Learning apps are recommendations, never distractions — they must
        // not surface the cooldown screen, so the lookup returns null.
        assertNull(repository.findGroupForPackage("com.duolingo"))
    }

    @Test
    fun `findGroupForPackage returns null when the app is in no group`() = runBlocking {
        assertNull(repository.findGroupForPackage("com.not.installed"))
    }

    // ── saveGroupWithApps ──

    @Test
    fun `saveGroupWithApps inserts a brand new group and its apps`() = runBlocking {
        val generatedId = repository.saveGroupWithApps(
            AppGroup(name = "Games", cooldownSeconds = 60),
            listOf("com.game.a", "com.game.b")
        )

        assertEquals(listOf("com.game.a", "com.game.b"), repository.getPackageNamesInGroup(generatedId))
    }

    @Test
    fun `saveGroupWithApps keeps the id and replaces all apps when editing`() = runBlocking {
        val createdId = repository.saveGroupWithApps(
            AppGroup(name = "Social", cooldownSeconds = 30),
            listOf("com.old.1", "com.old.2")
        )

        // Editing: same id, a different membership (drop one, add one).
        val updatedGroup = repository.getGroupById(createdId)!!
        val returnedId = repository.saveGroupWithApps(
            updatedGroup.copy(name = "Renamed"),
            listOf("com.old.1", "com.new.3")
        )

        assertEquals(createdId, returnedId)
        assertEquals("Renamed", repository.getGroupById(createdId)?.name)
        // Old set is fully replaced: com.old.2 is gone, com.new.3 is present.
        assertEquals(listOf("com.old.1", "com.new.3").sorted(), repository.getPackageNamesInGroup(createdId).sorted())
    }

    // ── deleteOldLaunchRecords ──

    @Test
    fun `deleteOldLaunchRecords prunes only records past the 365-day window`() = runBlocking {
        val now = 1_700_000_000_000L
        db.appLaunchDao().insertRecord(AppLaunchRecord(packageName = "com.old", groupId = 1, action = "proceeded", timestamp = now - 366 * day))
        db.appLaunchDao().insertRecord(AppLaunchRecord(packageName = "com.edge", groupId = 1, action = "proceeded", timestamp = now - 364 * day))

        repository.deleteOldLaunchRecords(now)

        // The 366-day-old record is gone; the 364-day-old one is retained.
        assertEquals(1, db.appLaunchDao().countRecordsSince(0))
        assertEquals(
            0,
            db.appLaunchDao().getRecordsForPackage("com.old").size
        )
        assertEquals(
            1,
            db.appLaunchDao().getRecordsForPackage("com.edge").size
        )
    }
}
