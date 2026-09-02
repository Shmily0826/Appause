package com.appause.android.ui.pause

import com.appause.android.data.settings.TemporaryPassPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryPassChooserStateTest {

    @Test
    fun back_dismisses_open_chooser_before_outer_cancel() {
        val state = TemporaryPassChooserState().open()

        assertEquals(TemporaryPassBackAction.DISMISS_CHOOSER, state.backAction())
        assertFalse(state.dismiss().isOpen)
    }

    @Test
    fun back_from_closed_chooser_keeps_outer_cancel_flow() {
        assertEquals(
            TemporaryPassBackAction.CANCEL_PAUSE,
            TemporaryPassChooserState().backAction()
        )
    }

    @Test
    fun back_during_selection_is_ignored_until_completion_then_outer_cancel_recovers() {
        val inFlight = TemporaryPassChooserState().open().tryStartSelection()!!

        assertEquals(
            TemporaryPassBackAction.IGNORE_SELECTION_IN_FLIGHT,
            inFlight.backAction()
        )

        val recovered = inFlight.completeSelection()
        assertFalse(recovered.selectionInFlight)
        assertEquals(TemporaryPassBackAction.CANCEL_PAUSE, recovered.backAction())
    }

    @Test
    fun chooser_cannot_open_before_cooldown_finishes() {
        val initial = TemporaryPassChooserState()

        assertEquals(initial, temporaryPassChooserStateAfterOpen(initial, isFinished = false))
        assertTrue(temporaryPassChooserStateAfterOpen(initial, isFinished = true).isOpen)

        val inFlight = initial.open().tryStartSelection()!!
        assertEquals(
            inFlight,
            temporaryPassChooserStateAfterOpen(inFlight, isFinished = true)
        )
    }

    @Test
    fun selection_is_one_shot_until_async_completion() {
        val openState = TemporaryPassChooserState().open()
        val selectedState = openState.tryStartSelection()

        assertNotNull(selectedState)
        assertTrue(selectedState!!.selectionInFlight)
        assertFalse(selectedState.isOpen)
        assertNull(selectedState.tryStartSelection())

        val reopenedState = selectedState.completeSelection().open()
        assertNotNull(reopenedState.tryStartSelection())
    }

    @Test
    fun chooser_uses_supported_five_fifteen_thirty_minute_options() {
        assertEquals(listOf(5, 15, 30), TemporaryPassPolicy.supportedMinutes)
    }

    @Test
    fun duration_options_have_at_least_material_touch_target() {
        assertTrue(TEMPORARY_PASS_MIN_TOUCH_TARGET_DP >= 48)
    }
}
