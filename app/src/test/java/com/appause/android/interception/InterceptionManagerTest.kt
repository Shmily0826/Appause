package com.appause.android.interception

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for InterceptionManager — the shared bypass state that stops the
 * AccessibilityService from re-intercepting an app the user already chose to
 * enter. A stuck or wrongly-cleared entry here is exactly the kind of bug that
 * makes interception look "broken" or annoyingly repetitive on a real device.
 */
class InterceptionManagerTest {

    /** Tests share the singleton, so always start from a clean bypass set. */
    @After
    fun cleanUp() {
        InterceptionManager.bypassedSnapshot().forEach { InterceptionManager.clearBypass(it) }
    }

    @Test
    fun `package is not bypassed by default`() {
        assertFalse(InterceptionManager.isBypassed("com.example.app"))
    }

    @Test
    fun `startBypass marks the package as bypassed`() {
        InterceptionManager.startBypass("com.example.app")
        assertTrue(InterceptionManager.isBypassed("com.example.app"))
    }

    @Test
    fun `clearBypass removes the package so the next launch triggers cooldown again`() {
        InterceptionManager.startBypass("com.example.app")
        InterceptionManager.clearBypass("com.example.app")
        assertFalse(InterceptionManager.isBypassed("com.example.app"))
    }

    @Test
    fun `clearing one package does not affect other bypassed packages`() {
        InterceptionManager.startBypass("com.example.first")
        InterceptionManager.startBypass("com.example.second")
        InterceptionManager.clearBypass("com.example.first")
        assertFalse(InterceptionManager.isBypassed("com.example.first"))
        assertTrue(InterceptionManager.isBypassed("com.example.second"))
    }

    @Test
    fun `clearing a package that was never bypassed is a safe no-op`() {
        InterceptionManager.clearBypass("com.example.never-added")
        assertFalse(InterceptionManager.isBypassed("com.example.never-added"))
    }

    @Test
    fun `bypassedSnapshot returns a copy that cannot mutate the internal state`() {
        InterceptionManager.startBypass("com.example.app")
        // Mutating the returned snapshot must not leak into the real set.
        InterceptionManager.bypassedSnapshot().toMutableSet().clear()
        assertTrue(InterceptionManager.isBypassed("com.example.app"))
    }

    @Test
    fun `bypassedSnapshot reflects the current bypass list`() {
        InterceptionManager.startBypass("com.example.first")
        InterceptionManager.startBypass("com.example.second")
        assertEquals(
            setOf("com.example.first", "com.example.second"),
            InterceptionManager.bypassedSnapshot()
        )
    }
}
