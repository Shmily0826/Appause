package com.appause.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PausePresentationPolicyTest {

    @Test
    fun `target is included only when requested`() {
        val targetOnly = { PausePresentationPolicy.isActive(true, { true }, { false }, { false }, { false }, { false }) }
        val targetExcluded = { PausePresentationPolicy.isActive(false, { true }, { false }, { false }, { false }, { false }) }

        assertTrue(targetOnly())
        assertFalse(targetExcluded())
    }

    @Test
    fun `members are evaluated in order and short circuit`() {
        val evaluations = mutableListOf<String>()
        val active = PausePresentationPolicy.isActive(
            includeTarget = true,
            targetPresent = { evaluations += "target"; false },
            overlayShowing = { evaluations += "showing"; false },
            overlayAttached = { evaluations += "attached"; true },
            activityVisible = { evaluations += "activity"; true },
            pauseShown = { evaluations += "getter"; true }
        )

        assertTrue(active)
        assertTrue(evaluations == listOf("target", "showing", "attached"))
    }

    @Test
    fun `excluding target does not evaluate target`() {
        var targetEvaluated = false

        assertFalse(
            PausePresentationPolicy.isActive(
                includeTarget = false,
                targetPresent = { targetEvaluated = true; true },
                overlayShowing = { false },
                overlayAttached = { false },
                activityVisible = { false },
                pauseShown = { false }
            )
        )
        assertFalse(targetEvaluated)
    }

    @Test
    fun `pauseShown is evaluated last when earlier members are false`() {
        val evaluations = mutableListOf<String>()

        assertTrue(
            PausePresentationPolicy.isActive(
                includeTarget = true,
                targetPresent = { evaluations += "target"; false },
                overlayShowing = { evaluations += "showing"; false },
                overlayAttached = { evaluations += "attached"; false },
                activityVisible = { evaluations += "activity"; false },
                pauseShown = { evaluations += "getter"; true }
            )
        )
        assertTrue(evaluations == listOf("target", "showing", "attached", "activity", "getter"))
    }
}
