package com.appause.android.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundChangeSingleFlightTest {

    @Test
    fun `concurrent foreground handlers never overlap`() = runTest {
        val singleFlight = ForegroundChangeSingleFlight()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var activeHandlers = 0
        var maxConcurrentHandlers = 0

        launch {
            singleFlight.run {
                activeHandlers++
                maxConcurrentHandlers = maxOf(maxConcurrentHandlers, activeHandlers)
                firstStarted.complete(Unit)
                releaseFirst.await()
                activeHandlers--
            }
        }
        firstStarted.await()

        val second = launch {
            singleFlight.run {
                activeHandlers++
                maxConcurrentHandlers = maxOf(maxConcurrentHandlers, activeHandlers)
                activeHandlers--
            }
        }
        runCurrent()
        assertFalse(second.isCompleted)

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, maxConcurrentHandlers)
    }

    @Test
    fun `handler failure does not block the next foreground event`() = runTest {
        val singleFlight = ForegroundChangeSingleFlight()
        val processed = mutableListOf<String>()

        try {
            singleFlight.run<String> { error("first foreground handler failed") }
            fail("the first handler should fail")
        } catch (_: IllegalStateException) {
            // The boundary must unlock even when the handler throws.
        }

        singleFlight.run { processed += "next" }

        assertEquals(listOf("next"), processed)
    }
}
