package com.appause.android.interception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for BurstTracker — the OEM "app-switch replay"
 * fingerprint. The thresholds pinned here are the direct product of the
 * v0.5.11–v0.5.24 debugging history:
 *  - threshold 2 suppressed GENUINE launches ("it doesn't intercept any more"),
 *  - threshold 3 cleanly separates a real open (1–2 real apps) from a HyperOS
 *    recents replay (3+ real apps in the same ~3 ms).
 */
class BurstTrackerTest {

    private val own = "com.appause.android"
    private val allReal: (String) -> Boolean = { false }

    private fun tracker() = BurstTracker()

    @Test
    fun `fresh tracker never suppresses`() {
        assertFalse(tracker().isSuppressed(nowMs = 1000, candidate = "com.example.app"))
    }

    @Test
    fun `single real app with launcher noise never suppresses`() {
        // A genuine launch on HyperOS: the target + the launcher + always-on
        // system components — only ONE real app.
        val t = tracker()
        t.record(1000, "com.example.target", own, allReal)
        t.record(1000, "com.miui.home", own) { it == "com.miui.home" }
        t.record(1001, "com.android.quicksearchbox", own) { true }
        assertFalse(t.isSuppressed(1005, "com.example.target"))
    }

    @Test
    fun `two real apps do not trigger suppression`() {
        // v0.5.24 rule: switching directly from one grouped app to another
        // shows TWO real apps — that is a genuine switch, never a replay.
        val t = tracker()
        t.record(1000, "com.example.first", own, allReal)
        t.record(1000, "com.example.second", own, allReal)
        assertFalse(t.isSuppressed(1005, "com.example.first"))
        assertFalse(t.isSuppressed(1005, "com.example.second"))
    }

    @Test
    fun `three real apps within the window trigger suppression`() {
        // A HyperOS recents replay fires window events for every cached task.
        val t = tracker()
        t.record(1000, "com.example.a", own, allReal)
        t.record(1002, "com.example.b", own, allReal)
        t.record(1003, "com.example.c", own, allReal)
        assertTrue(t.isSuppressed(1005, "com.example.a"))
        assertTrue(t.isSuppressed(1005, "com.example.unrelated"))
    }

    @Test
    fun `suppression expires after the duration`() {
        val t = tracker()
        t.record(1000, "com.example.a", own, allReal)
        t.record(1000, "com.example.b", own, allReal)
        t.record(1000, "com.example.c", own, allReal)
        // Suppress-until is 1000 + 1500; the boundary itself is expired.
        assertTrue(t.isSuppressed(2499, "com.example.x"))
        assertFalse(t.isSuppressed(2500, "com.example.x"))
    }

    @Test
    fun `events spread beyond the window do not accumulate`() {
        // The burst window is 120 ms; entries older than that are pruned, so
        // slow back-to-back launches never look like one tight cluster.
        val t = tracker()
        t.record(1000, "com.example.a", own, allReal)
        t.record(1121, "com.example.b", own, allReal)
        t.record(1122, "com.example.c", own, allReal)
        assertFalse(t.isSuppressed(1125, "com.example.x"))
    }

    @Test
    fun `events exactly at the window boundary still count as one cluster`() {
        // Pruning drops entries older than 120 ms — a gap of exactly 120 ms
        // is still "in" (1122 - 1002 = 120, not > 120).
        val t = tracker()
        t.record(1002, "com.example.a", own, allReal)
        t.record(1122, "com.example.b", own, allReal)
        t.record(1122, "com.example.c", own, allReal)
        assertTrue(t.isSuppressed(1125, "com.example.x"))
    }

    @Test
    fun `own package never counts toward the burst`() {
        // Appause's own window events must not inflate the real-app count.
        val t = tracker()
        t.record(1000, own, own, allReal)
        t.record(1000, "com.example.a", own, allReal)
        t.record(1000, "com.example.b", own, allReal)
        assertFalse(t.isSuppressed(1005, "com.example.a"))
    }

    @Test
    fun `noise classifier excludes system image packages`() {
        // Even 5 OEM helper packages are all noise → not a burst.
        val t = tracker()
        for (i in 1..5) {
            t.record(1000, "com.oem.helper$i", own) { true }
        }
        assertFalse(t.isSuppressed(1005, "com.example.x"))
    }

    @Test
    fun `snapshot returns the detected burst packages`() {
        // NOTE: the fingerprint re-classifies every buffered event on every
        // record() call using THAT call's classifier. The classifier must be
        // stable per package (as the PackageManager-backed one in the service
        // is) — so the test uses one shared classifier for all records.
        val t = tracker()
        val launcherIsNoise = { pkg: String -> pkg == "com.miui.home" }
        t.record(1000, "com.example.a", own, launcherIsNoise)
        t.record(1000, "com.miui.home", own, launcherIsNoise)
        t.record(1001, "com.example.b", own, launcherIsNoise)
        t.record(1001, "com.example.c", own, launcherIsNoise)
        assertEquals(
            setOf("com.example.a", "com.example.b", "com.example.c"),
            t.realPackagesSnapshot()
        )
    }
}
