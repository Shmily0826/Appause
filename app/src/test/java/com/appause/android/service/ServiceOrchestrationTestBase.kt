package com.appause.android.service

import android.os.Looper
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppGroup
import com.appause.android.data.repository.AppGroupRepository
import com.appause.android.data.settings.SettingsDataStore
import android.app.AppOpsManager
import com.appause.android.interception.InterceptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

/**
 * Shared harness for the service-level orchestration tests.
 *
 * Virtual-time model (learned the hard way — see ForegroundChangeOrchestrationTest):
 *  - The service's `Dispatchers.Main` scope is routed to a DEDICATED
 *    [TestCoroutineScheduler]; leave timers and re-remind loops only advance
 *    when the test calls [advanceServiceTimeBy].
 *  - The test body runs in `runBlocking` on the Robolectric main thread. Unlike
 *    runTest, nothing auto-advances the scheduler while the body waits on
 *    Room/DataStore IO, so a freshly armed 3-minute leave timer cannot
 *    silently fire mid-assertion.
 *  - `drainNow` runs only tasks scheduled at the CURRENT virtual instant and
 *    lets the real Room/DataStore executor threads resume them.
 *  - The 800 ms cancel suppression runs on Robolectric's shadow main looper
 *    (a separate clock), advanced with `idleFor`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ServiceOrchestrationTestBase {

    protected val serviceScheduler = TestCoroutineScheduler()
    protected val serviceMainDispatcher = StandardTestDispatcher(serviceScheduler)
    protected lateinit var controller: ServiceController<OrchestrationTestService>
    protected lateinit var service: OrchestrationTestService
    protected lateinit var repository: AppGroupRepository
    private lateinit var settings: SettingsDataStore
    protected val createdGroupIds = mutableListOf<Long>()
    protected var proUnlockedForTest = false
        private set

    @Before
    fun setUpOrchestration() {
        Dispatchers.setMain(serviceMainDispatcher)
        controller = Robolectric
            .buildService(OrchestrationTestService::class.java)
            .create()
        service = controller.get()
        val app = service.applicationContext as AppauseApp
        repository = app.repository
        settings = app.settingsDataStore
        // Production devices WITHOUT usage access take the
        // lastForegroundPackage fallback everywhere (re-remind stillInApp,
        // temporary-pass evidence). Give the tests that deterministic branch:
        // Robolectric's AppOps default would otherwise claim usage access and
        // the loop would ask an empty usage-event log ("user away" forever).
        val appOps = app.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            app.packageName,
            AppOpsManager.MODE_IGNORED
        )
    }

    @After
    fun tearDownOrchestration() {
        controller.destroy()
        // Reset every process-global the service touched so the next test
        // starts from a clean companion/singleton state.
        InterceptionManager.bypassedSnapshot().forEach(InterceptionManager::clearBypass)
        AppauseAccessibilityService.pauseShown = false
        AppauseAccessibilityService.justCancelledPackage = null
        AppauseAccessibilityService.lastForegroundPackage = null
        AppauseAccessibilityService.lastEventForeground = null
        AppauseAccessibilityService.pauseActivityVisible = false
        runBlocking {
            if (proUnlockedForTest) settings.setProUnlocked(false)
            createdGroupIds.forEach { id ->
                repository.getGroupById(id)?.let { repository.deleteGroup(it) }
            }
        }
        createdGroupIds.clear()
        Dispatchers.resetMain()
    }

    // ── helpers ──────────────────────────────────────────────────────────

    protected suspend fun seedGroup(
        name: String,
        packages: List<String>,
        cooldownSeconds: Int = 5,
        reRemindMinutes: Int = 0,
        reRemindRepeat: Boolean = true
    ): Long {
        val id = repository.saveGroupWithApps(
            AppGroup(
                name = name,
                cooldownSeconds = cooldownSeconds,
                reRemindMinutes = reRemindMinutes,
                reRemindRepeat = reRemindRepeat
            ),
            packages
        )
        createdGroupIds += id
        return id
    }

    /** Debug-only Pro unlock: only ever touches the debug DataStore flag. */
    protected fun unlockPro() {
        runBlocking { settings.setProUnlocked(true) }
        proUnlockedForTest = true
    }

    /**
     * Same three steps the OverlayManager "Continue" button performs for a
     * non-re-remind cooldown (completeInitialContinue + onSessionStart +
     * dismiss). The dismiss is simulated on the service-visible companion
     * state because the real view removal is not reachable from the test.
     */
    protected fun tapInitialContinue(
        targetPackage: String,
        groupId: Long,
        cooldownSeconds: Int,
        reRemindMinutes: Int = 0
    ) {
        service.completeInitialContinue(targetPackage)
        service.onSessionStart(targetPackage, groupId, cooldownSeconds, reRemindMinutes)
        simulateOverlayDismiss()
    }

    /**
     * Same four effects the OverlayManager "Cancel" button performs:
     * cancelReRemind + clearBypass + noteCancelled + dismiss.
     */
    protected fun tapCancel(targetPackage: String) {
        service.cancelReRemind(targetPackage)
        InterceptionManager.clearBypass(targetPackage)
        AppauseAccessibilityService.noteCancelled(targetPackage)
        simulateOverlayDismiss()
    }

    protected fun simulateOverlayDismiss() {
        AppauseAccessibilityService.pauseShown = false
    }

    /**
     * Drive one foreground change and wait for its suspension chain (Room /
     * DataStore run on real executor threads and resume back onto the virtual
     * scheduler) WITHOUT advancing virtual time.
     */
    protected suspend fun handleForeground(pkg: String) {
        service.handleForegroundChange(pkg)
        drainNow()
    }

    protected fun drainNow() {
        var stableRounds = 0
        while (stableRounds < 8) {
            val before = serviceScheduler.currentTime
            serviceScheduler.runCurrent()
            Thread.sleep(2) // let real Room/DataStore executors finish their IO
            stableRounds = if (serviceScheduler.currentTime == before) stableRounds + 1 else 0
        }
    }

    /**
     * Advance the service's virtual clock: pending leave timers / re-remind
     * delays fire. Advanced in SMALL SLICES with a drain between each: a
     * coroutine woken inside one slice may schedule its next delay further
     * out, and a single big advanceTimeBy window that already jumped past that
     * point would never execute it (advanceTimeBy does not look back).
     */
    protected fun advanceServiceTimeBy(ms: Long) {
        var remaining = ms
        while (remaining > 0) {
            val step = minOf(remaining, 10_000L)
            serviceScheduler.advanceTimeBy(step)
            serviceScheduler.runCurrent()
            drainNow()
            remaining -= step
        }
    }

    /** Advance Robolectric's wall clock (System.currentTimeMillis). */
    protected fun advanceWallClockBy(ms: Long) {
        ShadowSystemClockAdvance.advanceBy(ms)
    }

    private object ShadowSystemClockAdvance {
        fun advanceBy(ms: Long) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(ms))
        }
    }

    /** Advance the 800 ms cancel-suppression handler on the shadow main looper. */
    protected fun idleMainLooperFor(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms))
    }

    /** Group seed helper with a re-remind cooldown override. */
    protected suspend fun seedGroupWithReRemindCooldown(
        name: String,
        packages: List<String>,
        cooldownSeconds: Int,
        reRemindMinutes: Int,
        reRemindCooldownSeconds: Int
    ): Long {
        val id = repository.saveGroupWithApps(
            AppGroup(
                name = name,
                cooldownSeconds = cooldownSeconds,
                reRemindMinutes = reRemindMinutes,
                reRemindCooldownSeconds = reRemindCooldownSeconds
            ),
            packages
        )
        createdGroupIds += id
        return id
    }

    protected fun targetOf(seed: String) = "com.orch.$seed.target"
}
