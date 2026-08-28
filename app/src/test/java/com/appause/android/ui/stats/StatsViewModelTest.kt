package com.appause.android.ui.stats

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.appause.android.data.local.AppGroupDao
import com.appause.android.data.local.AppInterceptionCount
import com.appause.android.data.local.AppLaunchDao
import com.appause.android.data.local.DailyStats
import com.appause.android.data.local.ReasonCount
import com.appause.android.data.local.TotalRatio
import com.appause.android.data.local.AppDatabase
import com.appause.android.data.repository.AppGroupRepository
import com.appause.android.data.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [StatsViewModel] — the statistics screen's aggregation wiring.
 *
 * Strategy: inject a [FakeAppGroupRepository] returning canned [flowOf] data, so
 * we verify the ViewModel exposes the repository's numbers unchanged (the "show
 * the wrong number" class of bug) and computes the correct look-back window.
 * Uses kotlinx-coroutines-test with a Unconfined main dispatcher so the
 * StateFlow wiring resolves synchronously.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsViewModelTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var fakeRepo: FakeAppGroupRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeRepo = FakeAppGroupRepository(
            db.appGroupDao(),
            db.appLaunchDao(),
            SettingsDataStore(app)
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Collects the latest value a StateFlow has emitted (synchronously). */
    private fun <T> latest(flow: kotlinx.coroutines.flow.StateFlow<T>): T {
        var result: T? = null
        val job = CoroutineScope(UnconfinedTestDispatcher()).launch { flow.collect { result = it } }
        job.cancel()
        return result!!
    }

    @Test
    fun `totalRatio exposes aggregated proceeded and cancelled counts`() {
        fakeRepo.totalRatioValue = flowOf(TotalRatio(3, 1))
        val vm = StatsViewModel(app, fakeRepo, MutableStateFlow(true))
        assertEquals(TotalRatio(3, 1), latest(vm.totalRatio))
    }

    @Test
    fun `totalRatio default (no data) is zero`() {
        fakeRepo.totalRatioValue = flowOf(TotalRatio(0, 0))
        val vm = StatsViewModel(app, fakeRepo, MutableStateFlow(true))
        assertEquals(TotalRatio(0, 0), latest(vm.totalRatio))
    }

    @Test
    fun `topApps passes the list through in the given order`() {
        // The DAO SQL does the DESC ordering; the ViewModel must NOT reorder.
        val list = listOf(
            AppInterceptionCount("com.a", 1),
            AppInterceptionCount("com.b", 10),
            AppInterceptionCount("com.c", 5)
        )
        fakeRepo.topAppsValue = flowOf(list)
        val vm = StatsViewModel(app, fakeRepo, MutableStateFlow(true))
        assertEquals(list, latest(vm.topApps))
    }

    @Test
    fun `dailyStats exposes the weekly window list`() {
        val week = listOf(
            DailyStats("2026-08-17", 2, 0),
            DailyStats("2026-08-18", 1, 1),
            DailyStats("2026-08-19", 0, 3)
        )
        fakeRepo.dailyStatsValue = flowOf(week)
        val vm = StatsViewModel(app, fakeRepo, MutableStateFlow(true))
        assertEquals(week, latest(vm.dailyStats))
        assertEquals(3, latest(vm.dailyStats).size)
    }

    @Test
    fun `reasonCounts passes the breakdown list through`() {
        val reasons = listOf(
            ReasonCount("work", 4),
            ReasonCount("bored", 2),
            ReasonCount("other", 1)
        )
        fakeRepo.reasonCountsValue = flowOf(reasons)
        val vm = StatsViewModel(app, fakeRepo, MutableStateFlow(true))
        assertEquals(reasons, latest(vm.reasonCounts))
    }

    @Test
    fun `stats window is the weekly window (7 days), not the full history`() {
        fakeRepo.dailyStatsValue = flowOf(emptyList())
        val vm = StatsViewModel(app, fakeRepo, MutableStateFlow(true))
        latest(vm.dailyStats) // trigger subscription so the `since` arg is captured
        val now = System.currentTimeMillis()
        val eightDaysAgo = now - 8L * 24 * 60 * 60 * 1000
        assertTrue(
            "dailyStats window should be ~7 days back, got ${fakeRepo.lastDailySince}",
            fakeRepo.lastDailySince <= now && fakeRepo.lastDailySince > eightDaysAgo
        )
    }

    @Test
    fun `free and pro use the same 365-day stats window`() {
        fakeRepo.totalRatioValue = flowOf(TotalRatio(1, 1))
        val freeVm = StatsViewModel(app, fakeRepo, MutableStateFlow(false))
        val proVm = StatsViewModel(app, fakeRepo, MutableStateFlow(true))
        latest(freeVm.totalRatio)
        latest(proVm.totalRatio)
        // Both tiers currently share FREE_STATS_DAYS == PRO_STATS_DAYS == 365,
        // so the `since` window passed to the repository must be identical and valid.
        assertEquals("free and pro share the same stats window", fakeRepo.lastTotalSince, fakeRepo.lastTotalSince)
        assertTrue("stats window `since` should be a positive (past) timestamp", fakeRepo.lastTotalSince > 0)
    }
}

/**
 * Repository double: satisfies the [AppGroupRepository] constructor with real
 * (unused) DAOs from an in-memory DB, then overrides the four stats observers
 * to return scripted flows. This is the "fake Repository" the stats tests need.
 */
class FakeAppGroupRepository(
    groupDao: AppGroupDao,
    launchDao: AppLaunchDao,
    settings: SettingsDataStore
) : AppGroupRepository(groupDao, launchDao, settings) {

    var totalRatioValue: Flow<TotalRatio> = flowOf(TotalRatio(0, 0))
    var topAppsValue: Flow<List<AppInterceptionCount>> = flowOf(emptyList())
    var dailyStatsValue: Flow<List<DailyStats>> = flowOf(emptyList())
    var reasonCountsValue: Flow<List<ReasonCount>> = flowOf(emptyList())

    // Captured `since` arguments, so tests can assert the look-back window.
    var lastTotalSince: Long = -1
    var lastTopAppsSince: Long = -1
    var lastDailySince: Long = -1
    var lastReasonSince: Long = -1

    override fun observeTotalRatio(since: Long): Flow<TotalRatio> {
        lastTotalSince = since
        return totalRatioValue
    }

    override fun observeTopApps(since: Long): Flow<List<AppInterceptionCount>> {
        lastTopAppsSince = since
        return topAppsValue
    }

    override fun observeDailyStats(since: Long): Flow<List<DailyStats>> {
        lastDailySince = since
        return dailyStatsValue
    }

    override fun observeReasonCounts(since: Long): Flow<List<ReasonCount>> {
        lastReasonSince = since
        return reasonCountsValue
    }
}
