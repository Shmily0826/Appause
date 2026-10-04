package com.appause.android.service

import android.os.Looper
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppGroup
import com.appause.android.data.repository.AppGroupRepository
import com.appause.android.interception.InterceptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * P0-1 orchestration tests: the layer between the pure decision
 * ([InterceptionDecider]) and the real side effects — bypass membership,
 * [SessionState], leave-window deadlines, re-remind jobs and the companion
 * runtime flags.
 *
 * These tests drive the REAL service ([AppauseAccessibilityService.handleForegroundChange],
 * the same entry point the accessibility event path and the poller both use)
 * against the REAL Application graph (Room + DataStore), with the service's
 * `Dispatchers.Main` scope swapped for a virtual-time test dispatcher.
 * The overlay is shown for real (TYPE_ACCESSIBILITY_OVERLAY via Robolectric's
 * shadow WindowManager), and the Continue/Cancel taps are simulated at the
 * exact call points the OverlayManager button callbacks make.
 *
 * Virtual-time rules (these keep the tests deterministic):
 *  - [drainNow] only runs tasks scheduled at the CURRENT virtual instant, so
 *    a pending 3-minute leave timer is never fired by accident.
 *  - Future timers are advanced explicitly with `testScheduler.advanceTimeBy`.
 *  - The 800 ms cancel-suppression handler runs on Robolectric's shadow main
 *    looper, which is a separate clock advanced with `idleFor`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestAppauseApp::class)
class ForegroundChangeOrchestrationTest : ServiceOrchestrationTestBase() {

    @Test
    fun `window-state event reaches the real foreground decision and pause presentation path`() = runBlocking<Unit> {
        seedGroup("orch-event", listOf("com.orch.event.target"), cooldownSeconds = 5)
        val target = "com.orch.event.target"
        val event = android.view.accessibility.AccessibilityEvent.obtain(
            android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ).apply {
            packageName = target
        }

        service.onAccessibilityEvent(event)
        event.recycle()
        drainNow()

        assertEquals("INTERCEPT: $target → group=orch-event, cooldown=5s", AppauseAccessibilityService.lastTargetDecision)
        assertEquals(1, service.shownCooldowns.size)
        assertEquals(target, service.shownCooldowns.single().targetPackage)
    }

    // ── A. Continue → session → leave window ─────────────────────────────

    @Test
    fun `continue opens a bypassed session and leaving arms a per-package leave deadline`() = runBlocking<Unit> {
        val groupId = seedGroup("orch-A1", listOf("com.orch.a1.target"), cooldownSeconds = 5)
        val target = "com.orch.a1.target"

        handleForeground(target)
        assertEquals(
            "target should be intercepted",
            "INTERCEPT: $target → group=orch-A1, cooldown=5s",
            AppauseAccessibilityService.lastTargetDecision
        )
        assertEquals("the cooldown presentation must be shown exactly once", 1, service.shownCooldowns.size)
        assertEquals(target, service.shownCooldowns[0].targetPackage)

        tapInitialContinue(target, groupId, cooldownSeconds = 5)
        assertTrue("Continue must put the target on the bypass list", InterceptionManager.isBypassed(target))

        // Session is active: an immediate re-entry is a RESUME, not a re-cool.
        handleForeground(target)
        assertTrue(
            "re-entry inside the session must RESUME, got: ${AppauseAccessibilityService.lastDecision}",
            AppauseAccessibilityService.lastDecision.orEmpty().startsWith("RESUME")
        )

        // Leaving to an unrelated real app arms the 3-min leave window.
        handleForeground("com.orch.a1.other")
        val deadline = AppauseAccessibilityService.leaveCooldownDeadlines.value[target]
        assertNotNull("leave deadline must be recorded for the package we left", deadline)
        val expected = System.currentTimeMillis() + 3 * 60 * 1000L
        assertTrue(
            "deadline is now + the full 3-minute leave window (got $deadline, expected ~$expected)",
            deadline!! in expected - 2000..expected + 2000
        )

        // Returning within the window cancels the timer and resumes.
        handleForeground(target)
        assertNull("returning must cancel the leave deadline", AppauseAccessibilityService.leaveCooldownDeadlines.value[target])
        assertTrue(InterceptionManager.isBypassed(target))
        controller.destroy()
    }

    @Test
    fun `leave window expiring re-arms the session and the next open re-intercepts`() = runBlocking<Unit> {
        val groupId = seedGroup("orch-A2", listOf("com.orch.a2.target"), cooldownSeconds = 5)
        val target = "com.orch.a2.target"

        handleForeground(target)
        tapInitialContinue(target, groupId, cooldownSeconds = 5)
        handleForeground("com.orch.a2.other")
        assertNotNull(AppauseAccessibilityService.leaveCooldownDeadlines.value[target])

        // Advance past the 3-minute leave window: the timer must re-arm
        // (clear bypass + session) instead of waiting for a foreground event.
        advanceServiceTimeBy(3 * 60 * 1000L + 5_000)

        assertFalse("expired leave window must clear the bypass", InterceptionManager.isBypassed(target))
        assertNull(AppauseAccessibilityService.leaveCooldownDeadlines.value[target])

        handleForeground(target)
        assertTrue(
            "after re-arm the next open must get a fresh cooldown, got: ${AppauseAccessibilityService.lastTargetDecision}",
            AppauseAccessibilityService.lastTargetDecision.orEmpty().startsWith("INTERCEPT")
        )
        controller.destroy()
    }

    // ── B. Cancel ────────────────────────────────────────────────────────

    @Test
    fun `cancel creates a short suppression that expires and allows re-interception`() = runBlocking<Unit> {
        val groupId = seedGroup("orch-B1", listOf("com.orch.b1.target"), cooldownSeconds = 5)
        val target = "com.orch.b1.target"

        handleForeground(target)
        assertTrue(AppauseAccessibilityService.pauseShown)
        tapCancel(target)
        assertEquals(
            "cancel must mark the package as just-cancelled",
            target,
            AppauseAccessibilityService.justCancelledPackage
        )
        assertFalse(AppauseAccessibilityService.pauseShown)

        // The stale window event the target fires right before the launcher
        // takes over (within the 800 ms suppression window). Characterization:
        // noteCancelled() resets lastForegroundPackage, so this echo no longer
        // matches the SkipStaleCancelled precondition (which requires
        // lastForegroundPackage == justCancelledPackage) and falls through to
        // the normal interception path.
        handleForeground(target)
        val echoDecision = AppauseAccessibilityService.lastTargetDecision.orEmpty()
        assertTrue(
            "stale echo within the suppression window: got '$echoDecision'",
            echoDecision.startsWith("INTERCEPT") || echoDecision.startsWith("SKIP")
        )
        simulateOverlayDismiss()

        // The 800 ms suppression timer runs on the wall-clock handler.
        idleMainLooperFor(800)
        assertNull(
            "suppression must self-clear after its grace window",
            AppauseAccessibilityService.justCancelledPackage
        )

        // Going Home, then genuinely reopening the app must re-intercept.
        handleForeground("com.android.systemui")
        assertEquals(
            "launcher-like package must be skipped as system",
            "SKIP: system package (com.android.systemui)",
            AppauseAccessibilityService.lastDecision
        )
        handleForeground(target)
        assertTrue(
            "genuine reopen after cancel must re-intercept, got: ${AppauseAccessibilityService.lastTargetDecision}",
            AppauseAccessibilityService.lastTargetDecision.orEmpty().startsWith("INTERCEPT")
        )
        controller.destroy()
    }

    // ── C. destroy / recreate state boundaries ───────────────────────────

    @Test
    fun `destroy drops service-instance state but the process-global bypass survives into the next instance`() = runBlocking<Unit> {
        val groupId = seedGroup("orch-C1", listOf("com.orch.c1.target"), cooldownSeconds = 5)
        val target = "com.orch.c1.target"

        handleForeground(target)
        tapInitialContinue(target, groupId, cooldownSeconds = 5)
        assertTrue(InterceptionManager.isBypassed(target))

        controller.destroy()

        // Service-instance state is gone.
        assertNull(
            "leave deadlines are service-instance state and must be cleared on destroy",
            AppauseAccessibilityService.leaveCooldownDeadlines.value[target]
        )
        assertEquals(
            "process state must fail closed after destroy",
            AccessibilityProcessState.DISCONNECTED,
            AppauseAccessibilityService.currentProcessState()
        )

        // Process-global state survives by design: the bypass set is owned by
        // InterceptionManager (a process singleton shared with PauseActivity).
        assertTrue(
            "bypass is process-global and must survive service destroy",
            InterceptionManager.isBypassed(target)
        )

        // A NEW service instance must not resurrect a foreground session for
        // the package: the fresh SessionState has no marker, so only the
        // surviving bypass keeps the user inside a session. Characterization:
        // the dedup markers (lastForegroundPackage / lastEventForeground) are
        // ALSO process-global, so a same-package event right after rebind is
        // swallowed by the 2.6 dedup guard — it logs but never writes
        // lastDecision and never re-cools the user.
        controller = Robolectric.buildService(OrchestrationTestService::class.java).create()
        service = controller.get()
        val decisionBeforeRebind = AppauseAccessibilityService.lastDecision
        handleForeground(target)
        assertEquals(
            "same-package event after rebind is a 2.6 dedup skip (no new decision written)",
            decisionBeforeRebind,
            AppauseAccessibilityService.lastDecision
        )

        // Once the bypass is cleared and the user is seen leaving to a system
        // surface, the new instance intercepts the next open normally.
        InterceptionManager.clearBypass(target)
        handleForeground("com.android.systemui")
        handleForeground(target)
        assertTrue(
            "with no session and no bypass the new instance must intercept, got: ${AppauseAccessibilityService.lastTargetDecision}",
            AppauseAccessibilityService.lastTargetDecision.orEmpty().startsWith("INTERCEPT")
        )
        controller.destroy()
    }

    @Test
    fun `destroy cancels pending timers so a recreated service starts without stale re-arms`() = runBlocking<Unit> {
        val groupId = seedGroup("orch-C2", listOf("com.orch.c2.target"), cooldownSeconds = 5)
        val target = "com.orch.c2.target"

        handleForeground(target)
        tapInitialContinue(target, groupId, cooldownSeconds = 5)
        handleForeground("com.orch.c2.other")
        assertNotNull(AppauseAccessibilityService.leaveCooldownDeadlines.value[target])

        controller.destroy()

        // Recreate and advance far past the old leave window: nothing may
        // re-arm behind the destroyed instance's back.
        controller = Robolectric.buildService(OrchestrationTestService::class.java).create()
        service = controller.get()
        advanceServiceTimeBy(10 * 60 * 1000L)

        assertTrue(
            "the surviving bypass keeps the user resumed in the new instance",
            InterceptionManager.isBypassed(target)
        )
        assertNull(AppauseAccessibilityService.leaveCooldownDeadlines.value[target])
        controller.destroy()
    }
}
