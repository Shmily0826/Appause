package com.appause.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {

    @Test
    fun `reArm ends session so the same package can start again`() {
        val state = SessionState()

        assertTrue(state.begin("com.example.bilibili", preserveForegroundSession = true))
        state.end("com.example.bilibili")

        assertTrue(state.begin("com.example.bilibili", preserveForegroundSession = true))
        assertTrue(state.isForegroundActive("com.example.bilibili"))
    }

    @Test
    fun `temporary pass session does not retain foreground marker`() {
        val state = SessionState()

        // A Temporary Pass intentionally does not claim the ordinary session
        // guard or foreground marker.
        assertTrue(state.begin("com.example.bilibili", preserveForegroundSession = false))
        assertFalse(state.isForegroundActive("com.example.bilibili"))

        // Once its persisted expiry is over, ordinary Continue can begin fresh.
        assertTrue(state.begin("com.example.bilibili", preserveForegroundSession = true))
        assertTrue(state.isForegroundActive("com.example.bilibili"))
    }
}
