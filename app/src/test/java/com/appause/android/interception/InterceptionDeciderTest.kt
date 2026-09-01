package com.appause.android.interception

import com.appause.android.data.local.AppGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for InterceptionDecider — the pure filter chain that
 * used to live inline in AppauseAccessibilityService.handleForegroundChange().
 *
 * Every test below pins a REAL behavior from the app's debugging history
 * (the v0.5.11–v0.5.27 regressions documented in the service's comments).
 * If a future change to the filter rules breaks one of these tests, it is
 * changing documented user-visible behavior — that may be intended, but it
 * must be a conscious decision, not an accident.
 */
class InterceptionDeciderTest {

    private val target = "com.example.target"
    private val other = "com.example.other"

    /** Build pre-group input with the defaults of a "genuine open" scenario. */
    private fun preGroupInput(
        packageName: String = target,
        previousEventPackage: String? = null,
        lastForegroundPackage: String? = null,
        justCancelledPackage: String? = null,
        isEnabled: Boolean = true,
        isOwnPackage: Boolean = false,
        isSystemPackage: Boolean = false,
        isHomePackage: Boolean = false,
        isHomeForegroundConfirmed: Boolean = false,
        isBypassed: Boolean = false,
        isSessionActive: Boolean = false,
        isTemporaryPassActive: Boolean = false,
        pauseShown: Boolean = false,
        pauseTargetPackage: String? = null,
        isPauseTargetBypassed: Boolean = false
    ): InterceptionDecider.PreGroupInput = InterceptionDecider.PreGroupInput(
        packageName = packageName,
        previousEventPackage = previousEventPackage,
        lastForegroundPackage = lastForegroundPackage,
        justCancelledPackage = justCancelledPackage,
        isEnabled = isEnabled,
        isOwnPackage = isOwnPackage,
        isSystemPackage = isSystemPackage,
        isHomePackage = isHomePackage,
        isHomeForegroundConfirmed = isHomeForegroundConfirmed,
        isBypassed = isBypassed,
        isSessionActive = isSessionActive,
        isTemporaryPassActive = isTemporaryPassActive,
        pauseShown = { pauseShown },
        pauseTargetPackage = pauseTargetPackage,
        isPauseTargetBypassed = isPauseTargetBypassed
    )

    private fun postGroupInput(
        packageName: String = target,
        group: AppGroup? = AppGroup(name = "Social", cooldownSeconds = 30),
        burstSuppressed: Boolean = false,
        burstRealPackages: Set<String> = emptySet(),
        pauseShown: Boolean = false,
        isBypassed: Boolean = false,
        isTemporaryPassActive: Boolean = false
    ): InterceptionDecider.PostGroupInput = InterceptionDecider.PostGroupInput(
        packageName = packageName,
        group = group,
        burstSuppressed = burstSuppressed,
        burstRealPackages = burstRealPackages,
        pauseShown = { pauseShown },
        isBypassed = isBypassed,
        isTemporaryPassActive = isTemporaryPassActive
    )

    private fun decidePre(vararg tweaks: (InterceptionDecider.PreGroupInput) -> InterceptionDecider.PreGroupInput): PreGroupDecision {
        var input = preGroupInput()
        tweaks.forEach { input = it(input) }
        return InterceptionDecider.decidePreGroup(input)
    }

    private fun decidePost(vararg tweaks: (InterceptionDecider.PostGroupInput) -> InterceptionDecider.PostGroupInput): PostGroupDecision {
        var input = postGroupInput()
        tweaks.forEach { input = it(input) }
        return InterceptionDecider.decidePostGroup(input)
    }

    // ── Step 1: master switch ──

    @Test
    fun `app disabled skips everything`() {
        val decision = decidePre({ it.copy(isEnabled = false) })
        assertTrue(decision is PreGroupDecision.SkipDisabled)
        assertEquals("SKIP: Appause is disabled", decision.diagnosticsReason)
        assertFalse(decision.passedSystemGate)
    }

    // ── Step 2 / 2.5: self and just-cancelled suppression ──

    @Test
    fun `own package is skipped`() {
        val decision = decidePre({ it.copy(isOwnPackage = true) })
        assertTrue(decision is PreGroupDecision.SkipSelf)
        assertEquals("SKIP: Appause itself", decision.diagnosticsReason)
    }

    @Test
    fun `stale event for just-cancelled app is suppressed`() {
        // State right after Cancel, before the launcher takes over:
        // justCancelled == target AND lastForegroundPackage still == target.
        val decision = decidePre(
            { it.copy(justCancelledPackage = target, lastForegroundPackage = target) }
        )
        assertTrue(decision is PreGroupDecision.SkipStaleCancelled)
        assertEquals("SKIP: stale event for just-cancelled app ($target)", decision.diagnosticsReason)
    }

    @Test
    fun `just-cancelled suppression does not swallow a genuine quick re-open`() {
        // noteCancelled() resets lastForegroundPackage to null, so the user's
        // immediate re-open of the SAME app must NOT be treated as the stale
        // echo — it falls through toward interception ("tap Cancel, re-open
        // quickly → cooldown fires again"). v0.5.x regression guard.
        val decision = decidePre(
            { it.copy(justCancelledPackage = target, lastForegroundPackage = null) }
        )
        assertTrue(decision is PreGroupDecision.ProceedToGroupLookup)
    }

    // ── Step 2.6: poller/duplicate dedup ──

    @Test
    fun `duplicate event for current foreground is deduplicated`() {
        // In-app Activity switch (feed → note) or poller echo: the package
        // hasn't changed and the previous event was the same app.
        val decision = decidePre(
            { it.copy(lastForegroundPackage = target, previousEventPackage = target) }
        )
        assertTrue(decision is PreGroupDecision.SkipDedup)
        val dedup = decision as PreGroupDecision.SkipDedup
        assertEquals(
            "2.6 dedup skip: $target (lastFg=$target, prevEvent=$target)",
            dedup.logLine
        )
        // The original step 2.6 logged WITHOUT writing lastDecision.
        assertEquals(null, dedup.diagnosticsReason)
    }

    @Test
    fun `genuine re-open with stale lastForegroundPackage is not deduplicated`() {
        // The user left and came back: lastForegroundPackage still says target
        // (OEM swallowed the "app left" event), but the PREVIOUS event was a
        // different app → must re-evaluate, not skip. The "first open after
        // fresh state isn't intercepted" regression guard.
        val decision = decidePre(
            { it.copy(lastForegroundPackage = target, previousEventPackage = other) }
        )
        assertTrue(decision is PreGroupDecision.ProceedToGroupLookup)
    }

    @Test
    fun `pause on screen prevents the dedup from swallowing events`() {
        // Same shape as the dedup case, but a pause is showing: the 2.6 guard
        // is explicitly disabled then. Here the app is also bypassed, so the
        // decision must be RESUME — proving the event was NOT deduplicated.
        val decision = decidePre(
            { it.copy(lastForegroundPackage = target, previousEventPackage = target, pauseShown = { true }, isBypassed = true) }
        )
        assertTrue(decision is PreGroupDecision.Resume)
    }

    // ── Step 3: system packages ──

    @Test
    fun `system package is skipped`() {
        val decision = decidePre({ it.copy(isSystemPackage = true) })
        assertTrue(decision is PreGroupDecision.SkipSystem)
        assertEquals("SKIP: system package ($target)", decision.diagnosticsReason)
        assertFalse(decision.passedSystemGate)
    }

    @Test
    fun `confirmed Home during an active cooldown abandons before system filtering`() {
        val decision = decidePre({
            it.copy(
                packageName = "com.miui.home",
                isSystemPackage = true,
                isHomePackage = true,
                isHomeForegroundConfirmed = true,
                pauseShown = { true },
                pauseTargetPackage = target,
                // Countdown completion may start bypass before the user taps
                // Continue; Home must still dismiss the visible cooldown.
                isPauseTargetBypassed = true
            )
        })
        val abandon = decision as PreGroupDecision.AbandonCooldown
        assertEquals(target, abandon.targetPackage)
        assertEquals("com.miui.home", abandon.userWentTo)
    }

    @Test
    fun `unconfirmed launcher noise does not abandon a valid cooldown`() {
        val decision = decidePre({
            it.copy(
                packageName = "com.miui.home",
                isSystemPackage = true,
                isHomePackage = true,
                isHomeForegroundConfirmed = false,
                pauseShown = { true },
                pauseTargetPackage = target
            )
        })
        assertTrue(decision is PreGroupDecision.SkipSystem)
    }

    // ── Step 4: resume within leave window ──

    @Test
    fun `bypassed app returned within leave window resumes`() {
        val decision = decidePre({ it.copy(isBypassed = true) })
        assertTrue(decision is PreGroupDecision.Resume)
        assertEquals("RESUME: $target (returned within leave window)", decision.diagnosticsReason)
        // Passing the system gate means the service clears the cancel suppression.
        assertTrue(decision.passedSystemGate)
    }

    @Test
    fun `active temporary pass skips before checking bypass or group`() {
        val decision = decidePre({ it.copy(isTemporaryPassActive = true) })
        assertTrue(decision is PreGroupDecision.SkipTemporaryPass)
        assertEquals("SKIP: temporary pass active ($target)", decision.diagnosticsReason)
        assertTrue(decision.passedSystemGate)
    }

    @Test
    fun `active Continue session suppresses same-package navigation if bypass is transiently absent`() {
        val decision = decidePre({ it.copy(
            previousEventPackage = target,
            lastForegroundPackage = target,
            isBypassed = false,
            isSessionActive = true
        ) })

        assertTrue(decision is PreGroupDecision.Resume)
        assertEquals("RESUME: $target (returned within leave window)", decision.diagnosticsReason)
    }

    @Test
    fun `Temporary Pass takes precedence and expiry is not shadowed by session state`() {
        val whileActive = decidePre({ it.copy(isSessionActive = true, isTemporaryPassActive = true) })
        assertTrue(whileActive is PreGroupDecision.SkipTemporaryPass)

        val afterExpiry = decidePre({ it.copy(isSessionActive = false, isTemporaryPassActive = false) })
        assertTrue(afterExpiry is PreGroupDecision.ProceedToGroupLookup)
    }

    // ── Step 4.5: cooldown on screen ──

    @Test
    fun `different real app during cooldown abandons the cooldown`() {
        // The user swiped away from the pause screen to another app without
        // tapping Continue. On HyperOS the overlay can be hidden-but-attached,
        // so the service must dismiss it to release the guard (the
        // "completely stops popping up" bug).
        val decision = decidePre(
            { it.copy(pauseShown = { true }, pauseTargetPackage = target) },
            { it.copy(packageName = other) }
        )
        val abandon = decision as PreGroupDecision.AbandonCooldown
        assertEquals(target, abandon.targetPackage)
        assertEquals(other, abandon.userWentTo)
        assertEquals(
            "Cooldown abandoned (user left to $other before continuing) — dismissing overlay to release guard",
            abandon.warnLine
        )
        // The original abandon path logged a warning only, no lastDecision write.
        assertEquals(null, abandon.diagnosticsReason)
    }

    @Test
    fun `abandon does not fire when the pause target is bypassed`() {
        val decision = decidePre(
            { it.copy(pauseShown = { true }, pauseTargetPackage = target, isPauseTargetBypassed = true) },
            { it.copy(packageName = other) }
        )
        assertTrue(decision is PreGroupDecision.SkipPauseShown)
    }

    @Test
    fun `event for the pause target itself while shown is skipped`() {
        val decision = decidePre(
            { it.copy(pauseShown = { true }, pauseTargetPackage = target) }
        )
        assertTrue(decision is PreGroupDecision.SkipPauseShown)
        assertEquals("SKIP: cooldown overlay is showing ($target)", decision.diagnosticsReason)
    }

    @Test
    fun `pause shown with no tracked target is just skipped`() {
        val decision = decidePre({ it.copy(pauseShown = { true }) })
        assertTrue(decision is PreGroupDecision.SkipPauseShown)
    }

    // ── Step 5: second duplicate guard ──

    @Test
    fun `second duplicate guard catches the case where the guard releases mid-check`() {
        // Rare interleaving: at step 2.6 the guard reads TRUE (dedup disabled),
        // but by step 4.5 the staleness watchdog has released it. The step 5
        // guard still catches the in-app Activity switch. A varying pauseShown
        // lambda simulates exactly that sequence of reads.
        var readCount = 0
        val input = preGroupInput(
            lastForegroundPackage = target,
            previousEventPackage = target
        ).copy(pauseShown = {
            readCount++
            // 1st read (step 2.6): guard up → dedup disabled.
            // 2nd read (step 4.5): watchdog released it.
            readCount == 1
        })
        val decision = InterceptionDecider.decidePreGroup(input)
        assertTrue(decision is PreGroupDecision.SkipDuplicate)
        assertEquals("SKIP: duplicate event ($target)", decision.diagnosticsReason)
    }

    // ── Happy path pre-group ──

    @Test
    fun `genuine open proceeds to group lookup`() {
        val decision = decidePre({ it.copy(previousEventPackage = other) })
        assertTrue(decision is PreGroupDecision.ProceedToGroupLookup)
    }

    // ── Step 6: group lookup ──

    @Test
    fun `app not in any group is skipped`() {
        val decision = decidePost({ it.copy(group = null) })
        assertTrue(decision is PostGroupDecision.SkipNoGroup)
        assertEquals("SKIP: not in any group ($target)", decision.diagnosticsReason)
    }

    // ── Step 6.5: recents-replay burst ──

    @Test
    fun `recents replay burst is skipped`() {
        val decision = decidePost(
            { it.copy(burstSuppressed = true, burstRealPackages = setOf("a", "b", "c")) }
        )
        val skip = decision as PostGroupDecision.SkipBurstReplay
        assertEquals(target, skip.packageName)
        // The set renders exactly like the original inline string.
        assertEquals("SKIP: recents replay (burst=[a, b, c])", skip.diagnosticsReason)
    }

    // ── Step 6.6: state changed during group lookup ──

    @Test
    fun `pause appearing during group lookup prevents double intercept`() {
        val decision = decidePost({ it.copy(pauseShown = { true }) })
        assertTrue(decision is PostGroupDecision.SkipStateChanged)
        assertEquals("SKIP: state changed while intercepting ($target)", decision.diagnosticsReason)
    }

    @Test
    fun `bypass granted during group lookup prevents double intercept`() {
        val decision = decidePost({ it.copy(isBypassed = true) })
        assertTrue(decision is PostGroupDecision.SkipStateChanged)
    }

    @Test
    fun `temporary pass created during group lookup prevents intercept`() {
        val decision = decidePost({ it.copy(isTemporaryPassActive = true) })
        assertTrue(decision is PostGroupDecision.SkipTemporaryPass)
        assertEquals("SKIP: temporary pass active ($target)", decision.diagnosticsReason)
    }

    // ── Step 7: intercept ──

    @Test
    fun `genuine open of grouped app intercepts`() {
        val decision = decidePost()
        val intercept = decision as PostGroupDecision.Intercept
        assertEquals(target, intercept.packageName)
        // Byte-for-byte the original diagnostics string (arrow included).
        assertEquals("INTERCEPT: $target → group=Social, cooldown=30s", intercept.diagnosticsReason)
    }

    @Test
    fun `intercept reason reflects the group name and cooldown`() {
        val decision = decidePost({ it.copy(group = AppGroup(name = "游戏", cooldownSeconds = 60)) })
        assertEquals(
            "INTERCEPT: $target → group=游戏, cooldown=60s",
            decision.diagnosticsReason
        )
    }
}
