package com.appause.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseExitPolicyTest {
    private val target = "com.example.target"
    private val own = "com.example.appause"
    private val exitedAt = 1_000L

    private fun suppress(age: Long, foreground: String?, eventAt: Long = exitedAt + age) =
        PauseExitPolicy.shouldSuppress(exitedAt, eventAt, exitedAt + age, target, foreground, own)

    @Test
    fun `queued pre-exit event cannot become a reopen after waiting for IO`() {
        assertTrue(suppress(1_200, target, eventAt = exitedAt - 1))
    }

    @Test
    fun `old app briefly exposed by overlay removal is ignored`() {
        assertTrue(suppress(100, target))
    }

    @Test
    fun `cancel echo is ignored when Home is actually foreground`() {
        assertTrue(suppress(1_500, "com.example.launcher"))
    }

    @Test
    fun `redirect echo beyond the former two second timeout is ignored`() {
        assertTrue(suppress(3_000, "com.example.learning"))
    }

    @Test
    fun `confirmed immediate reopen is intercepted during the evidence window`() {
        assertFalse(suppress(300, target))
    }

    @Test
    fun `missing foreground evidence has only a short grace`() {
        assertTrue(suppress(300, null))
        assertFalse(suppress(800, null))
        assertTrue(suppress(300, own))
        assertFalse(suppress(800, own))
    }

    @Test
    fun `stale foreground evidence cannot exempt the app forever`() {
        assertFalse(suppress(5_000, "com.example.learning"))
        assertFalse(suppress(5_001, null, eventAt = exitedAt - 1))
    }
}
