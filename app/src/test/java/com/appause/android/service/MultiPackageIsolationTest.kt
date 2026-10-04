package com.appause.android.service

import com.appause.android.interception.InterceptionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1/P2 characterization: three packages (A/B/C) through the same service —
 * bypass, foreground sessions, leave-window deadlines, re-remind loops and
 * temporary passes must all be keyed per package with no cross-talk.
 *
 * Characterization note (per the user's instruction): the runtime deadline map
 * IS per-package (`leaveCooldownDeadlines: StateFlow<Map<String, Long>>`). The
 * Home card's `values.minOrNull()` display is a UI decision on top of it and
 * is NOT judged here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestAppauseApp::class)
class MultiPackageIsolationTest : ServiceOrchestrationTestBase() {

    private lateinit var a: String
    private lateinit var b: String
    private lateinit var c: String
    private lateinit var other: String
    private var groupA: Long = 0
    private var groupB: Long = 0
    private var groupC: Long = 0

    private fun seedThreePackages(reRemindMinutes: Int = 0) = runBlocking<Unit> {
        groupA = seedGroup("iso-A", listOf(targetOf("isoA")), reRemindMinutes = reRemindMinutes)
        groupB = seedGroup("iso-B", listOf(targetOf("isoB")), reRemindMinutes = reRemindMinutes)
        groupC = seedGroup("iso-C", listOf(targetOf("isoC")), reRemindMinutes = reRemindMinutes)
        a = targetOf("isoA")
        b = targetOf("isoB")
        c = targetOf("isoC")
        other = "com.orch.iso.other"
    }

    @Test
    fun `bypass sessions and leave deadlines are isolated across three packages`() = runBlocking<Unit> {
        seedThreePackages()

        // Open A and B with Continue sessions; C is intercepted but not continued.
        handleForeground(a)
        tapInitialContinue(a, groupA, cooldownSeconds = 5)
        handleForeground(b)
        tapInitialContinue(b, groupB, cooldownSeconds = 5)
        handleForeground(c)
        assertEquals("each intercept shows its own cooldown", 3, service.shownCooldowns.size)
        simulateOverlayDismiss() // C stays on its pause screen — dismiss it to keep driving

        assertTrue(InterceptionManager.isBypassed(a))
        assertTrue(InterceptionManager.isBypassed(b))
        assertFalse("C was never continued", InterceptionManager.isBypassed(c))

        // Moving on to an unrelated app starts a leave window for EVERY
        // bypassed package the user has left (A when B opened, B when C
        // opened, both still counting) — C has neither bypass nor session.
        handleForeground(other)
        assertNotNull("A's leave window started when the user opened B", AppauseAccessibilityService.leaveCooldownDeadlines.value[a])
        assertNotNull("B's leave window started when the user opened C", AppauseAccessibilityService.leaveCooldownDeadlines.value[b])
        assertNull("C has nothing to re-arm", AppauseAccessibilityService.leaveCooldownDeadlines.value[c])

        // Returning to B cancels B's window and resumes B — A's window keeps
        // running independently.
        handleForeground(b)
        assertNull("returning to B cancels only B's window", AppauseAccessibilityService.leaveCooldownDeadlines.value[b])
        assertNotNull("A's window is untouched by B's return", AppauseAccessibilityService.leaveCooldownDeadlines.value[a])
        assertTrue(InterceptionManager.isBypassed(b))

        // When A's window expires, ONLY A re-arms. B (whose window was
        // cancelled and whose session is live) stays bypassed.
        advanceServiceTimeBy(3 * 60 * 1000L + 5_000)
        assertFalse("A's expired window clears A's bypass", InterceptionManager.isBypassed(a))
        assertTrue("B's bypass survives A's re-arm", InterceptionManager.isBypassed(b))
        assertNull(AppauseAccessibilityService.leaveCooldownDeadlines.value[a])

        handleForeground(a)
        assertTrue(
            "A gets a fresh cooldown after its own re-arm, got: ${AppauseAccessibilityService.lastTargetDecision}",
            AppauseAccessibilityService.lastTargetDecision.orEmpty().startsWith("INTERCEPT")
        )
        simulateOverlayDismiss()
        handleForeground(b)
        assertTrue(
            "B still resumes inside its own session, got: ${AppauseAccessibilityService.lastDecision}",
            AppauseAccessibilityService.lastDecision.orEmpty().startsWith("RESUME")
        )
    }

    @Test
    fun `re-remind loops are keyed per package - only the package the user is in pops`() = runBlocking<Unit> {
        seedThreePackages(reRemindMinutes = 1)
        unlockPro()

        // A and B continue (both loops start). C is never opened.
        handleForeground(a)
        tapInitialContinue(a, groupA, cooldownSeconds = 2, reRemindMinutes = 1)
        handleForeground(b)
        tapInitialContinue(b, groupB, cooldownSeconds = 2, reRemindMinutes = 1)
        drainNow()
        assertEquals(2, service.shownCooldowns.size)

        // The user ends up back in A: at the shared pop instant ONLY A's loop
        // pops; B's loop sees a foreign foreground and re-checks instead.
        handleForeground(a)
        advanceServiceTimeBy(58_000)
        drainNow()
        assertEquals(3, service.shownCooldowns.size)
        assertEquals(a, service.shownCooldowns[2].targetPackage)
        assertFalse("A's pop cleared A's bypass", InterceptionManager.isBypassed(a))
        assertTrue("B's bypass is untouched by A's pop", InterceptionManager.isBypassed(b))

        // Tapping Continue on A's pop re-arms A and starts A's next interval
        // (mirroring the re-remind overlay's Continue button).
        InterceptionManager.startBypass(a)
        service.completeReRemindContinue(a)
        simulateOverlayDismiss()
        drainNow()

        // Now the user moves to B. B's loop woke during the first advance,
        // saw a foreign foreground, and re-delayed; once the user is really in
        // B again the loop's next cycle pops for B — and ONLY for B (A's loop
        // is parked awaiting its own Continue).
        handleForeground(b)
        advanceServiceTimeBy(75_000)
        drainNow()
        assertEquals("B pops (on its own schedule), A does not", 4, service.shownCooldowns.size)
        assertEquals(b, service.shownCooldowns[3].targetPackage)
        assertFalse("B's pop cleared B's bypass", InterceptionManager.isBypassed(b))
        assertTrue("A's bypass survives B's pop", InterceptionManager.isBypassed(a))

        // Cancelling A's loop stops only A. B's loop is parked awaiting ITS
        // Continue, so nothing further pops for either package.
        service.cancelReRemind(a)
        drainNow()
        advanceServiceTimeBy(60_000)
        drainNow()
        assertEquals(
            "A's cancelled loop never pops again; B is parked awaiting its Continue",
            4,
            service.shownCooldowns.size
        )
        assertTrue("A keeps its bypass after cancel", InterceptionManager.isBypassed(a))
    }

    @Test
    fun `a temporary pass on B does not leak into A or C decisions`() = runBlocking<Unit> {
        seedThreePackages()

        handleForeground(a)
        tapInitialContinue(a, groupA, cooldownSeconds = 5)
        handleForeground(b)
        // Grant B a pass the way the overlay's pass button does.
        val expiresAt = runBlocking { repository.grantTemporaryPass(b, 5, System.currentTimeMillis()) }
        org.junit.Assert.assertNotNull(expiresAt)
        service.completeInitialContinue(b)
        service.onSessionStart(b, groupB, cooldownSeconds = 5, reRemindMinutes = 0, preserveForegroundSession = false)
        InterceptionManager.clearBypass(b)
        simulateOverlayDismiss()
        handleForeground(c)
        simulateOverlayDismiss()

        // B's pass suppresses only B.
        handleForeground(b)
        assertEquals(
            "B is held by its pass, got: ${AppauseAccessibilityService.lastDecision}",
            "SKIP: temporary pass active ($b)",
            AppauseAccessibilityService.lastDecision
        )
        handleForeground(a)
        assertTrue(
            "A is unaffected by B's pass — still in session, got: ${AppauseAccessibilityService.lastDecision}",
            AppauseAccessibilityService.lastDecision.orEmpty().startsWith("RESUME")
        )
        handleForeground(c)
        assertTrue(
            "C is unaffected by B's pass — re-intercepted, got: ${AppauseAccessibilityService.lastTargetDecision}",
            AppauseAccessibilityService.lastTargetDecision.orEmpty().startsWith("INTERCEPT")
        )
        assertTrue(InterceptionManager.isBypassed(a))
    }
}
