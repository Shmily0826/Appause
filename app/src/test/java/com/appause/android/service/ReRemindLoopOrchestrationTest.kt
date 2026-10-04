package com.appause.android.service

import com.appause.android.interception.InterceptionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * P1 characterization tests for the REAL re-remind coroutine loop
 * ([AppauseAccessibilityService.scheduleReRemind]) — the policy-level tests
 * (ReRemindSchedulePolicyTest) only cover "should a loop start"; these pin
 * what the loop actually DOES: initial-signal anchoring, timed pops, the
 * temporary-pass pause, and per-package cancellation.
 *
 * Loop timing contract under test (cooldown=2s, reRemind=1min):
 *   pop appearance = lastContinue + 60s − rePopSeconds(=2s) = continue + 58s,
 *   so (appearance + 2s cooldown) lands the next Continue exactly 60s after
 *   the previous one.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestAppauseApp::class)
class ReRemindLoopOrchestrationTest : ServiceOrchestrationTestBase() {

    @Test
    fun `replacing a re-remind loop cancels its old timer and reuses the initial Continue signal`() = runBlocking<Unit> {
        val groupId = seedGroup(
            "rr-replace", listOf(targetOf("rr-replace")),
            cooldownSeconds = 2, reRemindMinutes = 1
        )
        val target = targetOf("rr-replace")
        unlockPro()

        handleForeground(target)
        tapInitialContinue(target, groupId, cooldownSeconds = 2, reRemindMinutes = 1)
        drainNow()
        assertEquals(1, service.shownCooldowns.size)

        // Replace halfway through the first timer. The new loop must reuse the
        // completed initial Continue signal and restart its interval here.
        advanceServiceTimeBy(30_000)
        service.scheduleReRemind(
            targetPackage = target,
            groupId = groupId,
            cooldownSeconds = 2,
            minutes = 1,
            reRemindCooldownSeconds = 0
        )
        drainNow()

        advanceServiceTimeBy(29_000)
        assertEquals("the cancelled timer must not pop at the old +58s deadline", 1, service.shownCooldowns.size)

        advanceServiceTimeBy(29_000)
        assertEquals("the replacement timer pops at +58s from replacement", 2, service.shownCooldowns.size)
        assertTrue(service.shownCooldowns.last().isReRemind)
        assertFalse(InterceptionManager.isBypassed(target))
    }

    // ── Case 1: a real Continue starts the clock; the pop lands on schedule ──

    @Test
    fun `continue anchors the clock and a fire-once loop pops exactly once at the scheduled instant`() = runBlocking<Unit> {
        val groupId = seedGroup(
            "rr-1", listOf(targetOf("rr1")),
            cooldownSeconds = 2, reRemindMinutes = 1, reRemindRepeat = false
        )
        val target = targetOf("rr1")
        unlockPro()

        handleForeground(target)
        tapInitialContinue(target, groupId, cooldownSeconds = 2, reRemindMinutes = 1)
        drainNow() // let the loop's pro-check coroutine start and reach initialSignal

        assertEquals(1, service.shownCooldowns.size)

        // Before the scheduled instant: anchored to the Continue tap, the first
        // pop is due at continue + 60s − 2s = +58s.
        advanceServiceTimeBy(57_000)
        drainNow()
        assertEquals("no pop before continue + 58s", 1, service.shownCooldowns.size)
        assertTrue(InterceptionManager.isBypassed(target))

        advanceServiceTimeBy(1_500)
        drainNow()
        assertEquals("pop appears at continue + 58s", 2, service.shownCooldowns.size)
        val pop = service.shownCooldowns[1]
        assertTrue("the pop is a re-remind pop", pop.isReRemind)
        assertEquals(target, pop.targetPackage)
        assertEquals("the pop uses the configured re-remind cooldown", 2, pop.cooldownSeconds)
        assertFalse("clearing bypass is what lets the overlay show", InterceptionManager.isBypassed(target))

        // repeat=false: after one pop the loop is done — nothing ever pops again.
        advanceServiceTimeBy(10 * 60 * 1000L)
        drainNow()
        assertEquals("fire-once mode must not pop twice", 2, service.shownCooldowns.size)
    }

    // ── Case 2: no Continue on the initial cooldown → the loop never starts ──

    @Test
    fun `without the initial continue the loop stays parked and never pops`() = runBlocking<Unit> {
        val groupId = seedGroup(
            "rr-2", listOf(targetOf("rr2")),
            cooldownSeconds = 2, reRemindMinutes = 1
        )
        val target = targetOf("rr2")
        unlockPro()

        handleForeground(target)
        // Deliberately NO tapInitialContinue: the user is still behind the
        // pause screen and has not entered the target app.
        drainNow()

        assertEquals(1, service.shownCooldowns.size)
        advanceServiceTimeBy(10 * 60 * 1000L)
        drainNow()

        assertEquals(
            "the loop awaits initialSignal forever — no re-remind without a real Continue",
            1,
            service.shownCooldowns.size
        )
        assertFalse("no bypass was ever granted", InterceptionManager.isBypassed(target))
    }

    // ── Case 3: a Temporary Pass — the real grant path never starts the loop ──

    @Test
    fun `temporary pass never starts the loop and an expired pass wake re-runs the cooldown`() = runBlocking<Unit> {
        val groupId = seedGroup(
            "rr-3", listOf(targetOf("rr3")),
            cooldownSeconds = 2, reRemindMinutes = 1
        )
        val target = targetOf("rr3")
        unlockPro()

        handleForeground(target)
        assertEquals(1, service.shownCooldowns.size)

        // Mirror OverlayManager.temporaryPassAction exactly: the pass path runs
        // completeInitialContinue + onSessionStart(preserveForegroundSession=false)
        // + clearBypass + scheduleTemporaryPassExpiry. preserveForegroundSession=false
        // is what KEEPS the re-remind loop from starting (onSessionStart only
        // schedules it for an explicit foreground session).
        val expiresAt = runBlocking { repository.grantTemporaryPass(target, 5, System.currentTimeMillis()) }
        org.junit.Assert.assertNotNull(expiresAt)
        service.completeInitialContinue(target)
        service.onSessionStart(
            target, groupId, cooldownSeconds = 2, reRemindMinutes = 1,
            preserveForegroundSession = false
        )
        InterceptionManager.clearBypass(target)
        service.scheduleTemporaryPassExpiry(target, expiresAt!!)
        simulateOverlayDismiss()
        drainNow()

        assertFalse(
            "the pass path grants a bypass-free bounded session",
            InterceptionManager.isBypassed(target)
        )

        // Advance past the would-be re-remind instant (continue + 58s), staying
        // inside the active pass's 5-minute wake delay: with no loop in
        // existence, no re-remind pop can ever fire under the pass.
        advanceServiceTimeBy(58_000)
        drainNow()
        assertEquals(
            "no re-remind pop under a temporary pass (the loop was never started)",
            1,
            service.shownCooldowns.size
        )

        // Now REPLACE the pass with an already-expired one (the same thing the
        // reconnect-restore path sees). Its wake delay collapses to 0, the
        // cached-last-foreground evidence classifies the target, and the wake
        // re-runs the COOLDOWN — the pass is over, the rule is active again.
        val expiredAt = runBlocking {
            repository.grantTemporaryPass(target, 5, System.currentTimeMillis() - 6 * 60 * 1000L)
        }
        org.junit.Assert.assertNotNull(expiredAt)
        service.scheduleTemporaryPassExpiry(target, expiredAt!!)
        advanceServiceTimeBy(1_000)
        drainNow()
        assertEquals(
            "an expired pass's wake re-intercepts with a fresh cooldown",
            2,
            service.shownCooldowns.size
        )
        assertFalse(
            "the post-pass pop is a fresh cooldown, not a re-remind",
            service.shownCooldowns[1].isReRemind
        )
    }

    // ── Case 4: cancelReRemind is package-scoped ─────────────────────────

    @Test
    fun `cancelReRemind stops only that package and leaves other loops running`() = runBlocking<Unit> {
        val groupA = seedGroup(
            "rr-4a", listOf(targetOf("rr4a")),
            cooldownSeconds = 2, reRemindMinutes = 1
        )
        val groupB = seedGroup(
            "rr-4b", listOf(targetOf("rr4b")),
            cooldownSeconds = 2, reRemindMinutes = 1
        )
        val a = targetOf("rr4a")
        val b = targetOf("rr4b")
        unlockPro()

        handleForeground(a)
        tapInitialContinue(a, groupA, cooldownSeconds = 2, reRemindMinutes = 1)
        handleForeground(b)
        tapInitialContinue(b, groupB, cooldownSeconds = 2, reRemindMinutes = 1)
        drainNow()
        assertEquals(2, service.shownCooldowns.size)

        advanceServiceTimeBy(57_000)
        assertEquals("neither loop pops early", 2, service.shownCooldowns.size)

        service.cancelReRemind(a)
        drainNow()

        advanceServiceTimeBy(2_000)
        drainNow()
        assertEquals(
            "B still pops at its scheduled instant",
            3,
            service.shownCooldowns.size
        )
        assertEquals(b, service.shownCooldowns[2].targetPackage)
        assertTrue(
            "A must keep its bypass — its loop was cancelled, not re-armed",
            InterceptionManager.isBypassed(a)
        )
        assertFalse("B's pop cleared B's bypass", InterceptionManager.isBypassed(b))
    }
}
