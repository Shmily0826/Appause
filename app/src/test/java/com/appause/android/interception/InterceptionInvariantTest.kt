package com.appause.android.interception

import com.appause.android.data.local.AppGroup
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariant ("property") tests for the pure interception decision layer.
 *
 * The existing InterceptionDeciderTest / BurstTrackerTest pin each historical
 * regression with a hand-picked scenario. This file takes the complementary
 * angle: it hammers the decision functions with MANY randomized inputs and
 * asserts GLOBAL SAFETY INVARIANTS that must hold no matter the combination —
 * the rules from AGENTS.md §4 (never self-intercept, never loop on duplicates,
 * safety gates always win). A fixed seed keeps the run reproducible.
 */
class InterceptionInvariantTest {

    private val rng = Random(20260918)

    // ── Helpers that build a fully-randomized input, then override one axis ──

    private fun randomPreGroup(
        isEnabled: Boolean = rng.nextBoolean(),
        isOwnPackage: Boolean = rng.nextBoolean(),
        isTemporaryPassActive: Boolean = rng.nextBoolean(),
        packageName: String = "com.target",
        justCancelledPackage: String? = rng.oneOf(null, "com.target", "com.other")
    ) = InterceptionDecider.PreGroupInput(
        packageName = packageName,
        previousEventPackage = rng.oneOf(null, "com.target", "com.other"),
        lastForegroundPackage = rng.oneOf(null, "com.target", "com.other"),
        justCancelledPackage = justCancelledPackage,
        isEnabled = isEnabled,
        isOwnPackage = isOwnPackage,
        isSystemPackage = rng.nextBoolean(),
        isHomePackage = rng.nextBoolean(),
        isHomeForegroundConfirmed = rng.nextBoolean(),
        isBypassed = rng.nextBoolean(),
        isSessionActive = rng.nextBoolean(),
        isTemporaryPassActive = isTemporaryPassActive,
        pauseShown = { rng.nextBoolean() },
        pauseTargetPackage = rng.oneOf(null, "com.target", "com.other"),
        isPauseTargetBypassed = rng.nextBoolean()
    )

    private fun randomPostGroup(
        group: AppGroup? = rng.oneOf(null, AppGroup(name = "G", cooldownSeconds = 30))
    ) = InterceptionDecider.PostGroupInput(
        packageName = "com.target",
        group = group,
        burstSuppressed = rng.nextBoolean(),
        burstRealPackages = setOf("com.a", "com.b"),
        pauseShown = { rng.nextBoolean() },
        isBypassed = rng.nextBoolean(),
        isTemporaryPassActive = rng.nextBoolean()
    )

    private fun <T> Random.oneOf(vararg values: T): T = values[nextInt(values.size)]

    // ── Invariant 1: the master-off switch dominates everything ──

    @Test
    fun `disabled never routes to an intercepting decision`() {
        repeat(500) {
            val decision = InterceptionDecider.decidePreGroup(randomPreGroup(isEnabled = false))
            assertEquals(PreGroupDecision.SkipDisabled, decision)
            assertFalse("disabled must not pass the system gate", decision.passedSystemGate)
        }
    }

    // ── Invariant 2: Appause never intercepts itself ──

    @Test
    fun `own package is always skipped, never proceeds to lookup`() {
        repeat(500) {
            val decision = InterceptionDecider.decidePreGroup(
                randomPreGroup(isEnabled = true, isOwnPackage = true)
            )
            assertTrue(
                "own package must resolve to SkipSelf, was $decision",
                decision is PreGroupDecision.SkipSelf
            )
        }
    }

    // ── Invariant 3: a temporary pass always wins over bypass / session ──

    @Test
    fun `active temporary pass skips regardless of other state`() {
        repeat(500) {
            // Neutralize the only check that runs BEFORE the pass gate (the
            // stale-cancelled guard, step 2.5) so we test pass precedence over
            // bypass / session / system / pause / dedup specifically.
            val decision = InterceptionDecider.decidePreGroup(
                randomPreGroup(
                    isEnabled = true,
                    isOwnPackage = false,
                    isTemporaryPassActive = true,
                    justCancelledPackage = "com.unrelated"
                )
            )
            assertTrue(
                "temporary pass must resolve to SkipTemporaryPass, was $decision",
                decision is PreGroupDecision.SkipTemporaryPass
            )
        }
    }

    @Test
    fun `a temporary pass can never escalate into a group lookup`() {
        // Even for fully random inputs (including the stale-cancelled case),
        // an active pass must never reach the point that could show a cooldown.
        repeat(500) {
            val decision = InterceptionDecider.decidePreGroup(
                randomPreGroup(isEnabled = true, isOwnPackage = false, isTemporaryPassActive = true)
            )
            assertTrue(
                "a pass must never escalate to group lookup, was $decision",
                decision != PreGroupDecision.ProceedToGroupLookup
            )
        }
    }

    // ── Invariant 4: post-group never intercepts an ungrouped app ──

    @Test
    fun `no group never yields an intercept`() {
        repeat(500) {
            val decision = InterceptionDecider.decidePostGroup(randomPostGroup(group = null))
            assertTrue(
                "ungrouped app must resolve to SkipNoGroup, was $decision",
                decision is PostGroupDecision.SkipNoGroup
            )
        }
    }

    // ── Invariant 5: an intercept only happens when every suppressor is off ──

    @Test
    fun `intercept is only reached when all suppressors are clear`() {
        repeat(500) {
            val group = AppGroup(name = "G", cooldownSeconds = 30)
            val burst = rng.nextBoolean()
            val bypass = rng.nextBoolean()
            val pass = rng.nextBoolean()
            val pause = rng.nextBoolean()
            val input = InterceptionDecider.PostGroupInput(
                packageName = "com.target",
                group = group,
                burstSuppressed = burst,
                burstRealPackages = setOf("com.a", "com.b"),
                pauseShown = { pause },
                isBypassed = bypass,
                isTemporaryPassActive = pass
            )
            val decision = InterceptionDecider.decidePostGroup(input)
            if (decision is PostGroupDecision.Intercept) {
                // If we DID intercept, none of the guards could have been set —
                // otherwise a suppressed launch leaked through to a cooldown.
                assertFalse("intercept while burst suppressed", burst)
                assertFalse("intercept while bypassed", bypass)
                assertFalse("intercept while temporary pass active", pass)
                assertFalse("intercept while pause shown", pause)
            }
        }
    }

    // ── Invariant 6: a genuine launch (<=2 real apps) is never burst-suppressed ──

    @Test
    fun `two or fewer real apps in any order never trigger burst suppression`() {
        val realApps = listOf("com.xhs", "com.bilibili")
        val noise = listOf("com.miui.home", "com.android.systemui")
        val ownPackage = "com.appause.android"
        val isNoise: (String) -> Boolean = { it in noise }
        val everything = realApps + noise + ownPackage

        repeat(500) {
            val tracker = BurstTracker()
            var clock = 1_000L
            // Emit a random mix of <=2 distinct real apps plus noise, all inside
            // a tight window, then assert none of them is ever suppressed.
            repeat(rng.nextInt(1, 12)) {
                val pkg = everything[rng.nextInt(everything.size)]
                tracker.record(clock, pkg, ownPackage, isNoise)
                clock += rng.nextLong(0, 100)
            }
            for (candidate in realApps) {
                assertFalse(
                    "candidate $candidate wrongly suppressed by a sub-threshold burst",
                    tracker.isSuppressed(clock, candidate)
                )
            }
        }
    }

    // ── Invariant 7: own package alone can never create a burst ──

    @Test
    fun `spamming the own package never suppresses any candidate`() {
        val tracker = BurstTracker()
        val ownPackage = "com.appause.android"
        val isNoise: (String) -> Boolean = { false }
        var clock = 0L
        repeat(50) {
            tracker.record(clock, ownPackage, ownPackage, isNoise)
            clock += 1
        }
        assertFalse(tracker.isSuppressed(clock, "com.anything"))
    }
}
