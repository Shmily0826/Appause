package com.appause.android.ui.pause

/**
 * Pure layout decisions for the pause screen's adaptive portrait/landscape
 * structure. Kept side-effect free so the branch rules can be pinned by
 * plain unit tests without a Compose or device harness.
 */
internal object PauseLayoutPolicy {

    /**
     * Two-pane landscape layout when the viewport is wider than it is tall.
     * Portrait (the long-standing baseline) keeps the single column.
     * Zero/negative dimensions (before the first real measure) stay portrait
     * — the proven default.
     */
    fun useLandscapeTwoPane(viewportWidthPx: Int, viewportHeightPx: Int): Boolean =
        viewportWidthPx > 0 && viewportHeightPx > 0 && viewportWidthPx > viewportHeightPx

    /**
     * Overflow scroll only when the measured content is taller than the
     * viewport. Normal portrait AND normal landscape fit without scrolling;
     * extreme font scale or tiny viewports keep scrolling so every action
     * stays reachable.
     */
    fun useOverflowScroll(contentHeightPx: Int, viewportHeightPx: Int): Boolean =
        contentHeightPx > 0 && viewportHeightPx > 0 && contentHeightPx > viewportHeightPx

    /**
     * Compact sizing used ONLY when the two-pane landscape layout is active.
     * Values are dp (sp for the countdown number).
     *
     * Why a separate set: a phone held sideways has roughly half the usable
     * HEIGHT of portrait (about 390dp versus about 800dp), so the portrait
     * rhythm that reads as calm upright becomes loose, makes the countdown
     * the single heaviest element on screen, and pushes the recommended-app
     * row past the viewport. Landscape therefore tightens the vertical gaps
     * and shrinks the countdown instead of stretching anything horizontally.
     */
    object Compact {
        /** Keeps the two panes centred with breathing room on a ~873dp-wide
         *  phone landscape instead of filling the whole display. */
        const val CONTENT_MAX_WIDTH_DP = 720

        const val PANE_SPACING_DP = 24

        const val APP_ICON_SIZE_DP = 52
        const val GAP_ICON_TO_NAME_DP = 10
        const val GAP_NAME_TO_PROMPT_DP = 6
        const val GAP_PROMPT_TO_COUNTDOWN_DP = 18
        const val GAP_COUNTDOWN_TO_RECOMMENDED_DP = 12
        const val GAP_RECOMMENDED_HEAD_TO_ROW_DP = 6

        const val COUNTDOWN_RING_DP = 118
        const val COUNTDOWN_NUMBER_SP = 48

        /** Stops the intent pills and the primary CTA from stretching across a
         *  very wide pane; both keep their normal touch targets. */
        const val REASON_PILL_MAX_WIDTH_DP = 160
        const val CONTINUE_BUTTON_WIDTH_DP = 176

        /** Continue / Cancel / Temporary Pass read as ONE decision group. */
        const val GAP_REASONS_TO_ACTIONS_DP = 20
        const val GAP_BETWEEN_ACTIONS_DP = 2
    }

    /**
     * Portrait keeps the long-standing 140dp ring (CountdownRing's own
     * default); landscape uses ~0.84x so the countdown stops dominating.
     */
    fun countdownRingSizeDp(landscape: Boolean): Int =
        if (landscape) Compact.COUNTDOWN_RING_DP else PORTRAIT_COUNTDOWN_RING_DP

    const val PORTRAIT_COUNTDOWN_RING_DP = 140
}
