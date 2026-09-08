package com.appause.android.ui.pause

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseActivityIntentTest {

    @Test
    fun only_a_different_target_recreates_the_pause_session() {
        assertTrue(hasDifferentPauseTarget("com.example.a", "com.example.b"))
        assertFalse(hasDifferentPauseTarget("com.example.a", "com.example.a"))
    }
}
