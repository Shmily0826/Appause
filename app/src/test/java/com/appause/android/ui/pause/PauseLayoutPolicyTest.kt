package com.appause.android.ui.pause

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pause screen's adaptive layout branch decisions:
 * portrait keeps the single column, landscape switches to the two-pane
 * layout, and overflow scroll only appears when content genuinely exceeds
 * the viewport (font scale / tiny height fallback).
 */
class PauseLayoutPolicyTest {

    @Test
    fun `portrait viewport keeps the single column`() {
        // Typical phone portrait: 1080x2400.
        assertFalse(PauseLayoutPolicy.useLandscapeTwoPane(1080, 2400))
        // Tablet portrait.
        assertFalse(PauseLayoutPolicy.useLandscapeTwoPane(1600, 2560))
    }

    @Test
    fun `landscape viewport switches to two-pane`() {
        // Typical phone landscape: 2400x1080 (the Xiaomi acceptance device).
        assertTrue(PauseLayoutPolicy.useLandscapeTwoPane(2400, 1080))
        // Landscape usable viewport after system bars: still wider than tall.
        assertTrue(PauseLayoutPolicy.useLandscapeTwoPane(2400, 953))
        // Square-ish stays portrait (default, not a landscape trigger).
        assertFalse(PauseLayoutPolicy.useLandscapeTwoPane(1080, 1080))
    }

    @Test
    fun `unknown dimensions stay portrait`() {
        assertFalse(PauseLayoutPolicy.useLandscapeTwoPane(0, 0))
        assertFalse(PauseLayoutPolicy.useLandscapeTwoPane(0, 2400))
        assertFalse(PauseLayoutPolicy.useLandscapeTwoPane(1080, 0))
    }

    @Test
    fun `overflow scroll only when content exceeds viewport`() {
        // Normal landscape usable height (~953px on the Xiaomi) with a
        // layout that fits: no scrolling.
        assertFalse(PauseLayoutPolicy.useOverflowScroll(900, 953))
        // Extreme font scale pushes the content past the viewport: scroll.
        assertTrue(PauseLayoutPolicy.useOverflowScroll(1200, 953))
        // Normal portrait fits.
        assertFalse(PauseLayoutPolicy.useOverflowScroll(1400, 2400))
        // Extreme portrait content overflows.
        assertTrue(PauseLayoutPolicy.useOverflowScroll(2600, 2400))
    }

    @Test
    fun `unknown measures never enable overflow scroll`() {
        assertFalse(PauseLayoutPolicy.useOverflowScroll(0, 953))
        assertFalse(PauseLayoutPolicy.useOverflowScroll(1200, 0))
    }

    @Test
    fun `portrait keeps the original countdown ring size`() {
        assertEquals(140, PauseLayoutPolicy.countdownRingSizeDp(landscape = false))
        assertEquals(140, PauseLayoutPolicy.PORTRAIT_COUNTDOWN_RING_DP)
    }

    @Test
    fun `landscape uses the compact countdown scale`() {
        // Roughly 0.84x of the portrait ring, and the number shrinks with it
        // so it never fills the smaller circle.
        assertEquals(118, PauseLayoutPolicy.countdownRingSizeDp(landscape = true))
        assertTrue(
            PauseLayoutPolicy.Compact.COUNTDOWN_RING_DP <
                PauseLayoutPolicy.PORTRAIT_COUNTDOWN_RING_DP
        )
        assertEquals(48, PauseLayoutPolicy.Compact.COUNTDOWN_NUMBER_SP)
    }

    @Test
    fun `landscape content cap is narrower than a phone landscape viewport`() {
        // A phone landscape is ~2400x1080 px; at a typical 2.75 density that
        // is ~873dp wide. The cap must actually clamp at that width — the
        // previous 900dp cap did not, which is why the panes stretched.
        assertTrue(PauseLayoutPolicy.Compact.CONTENT_MAX_WIDTH_DP < 873)
        // ...but it must still leave the two panes a usable share each.
        assertTrue(PauseLayoutPolicy.Compact.CONTENT_MAX_WIDTH_DP > 600)
    }
}
