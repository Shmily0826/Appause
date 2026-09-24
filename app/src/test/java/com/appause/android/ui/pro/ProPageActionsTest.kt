package com.appause.android.ui.pro

import com.appause.android.data.pro.ProAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ProPageActionsTest {

    @Test
    fun `page actions move trial and lifetime activation into sequential stages`() {
        assertEquals(ProPageActions(true, false, false, false), proPageActions(ProAccessStatus.FREE))
        assertEquals(ProPageActions(false, true, false, false), proPageActions(ProAccessStatus.TRIAL_ACTIVE))
        assertEquals(ProPageActions(false, false, true, false), proPageActions(ProAccessStatus.TRIAL_EXPIRED))
        assertEquals(ProPageActions(false, false, false, true), proPageActions(ProAccessStatus.LIFETIME))
    }

    @Test
    fun `countdown switches units and reaches zero at expiry`() {
        val now = 1_000_000L

        assertEquals(
            TrialCountdownParts(TrialCountdownUnit.DAYS_HOURS, 1, 2),
            trialCountdownParts(now + 26 * 60 * 60 * 1_000L, now)
        )
        assertEquals(
            TrialCountdownParts(TrialCountdownUnit.HOURS_MINUTES, 2, 5),
            trialCountdownParts(now + (2 * 60 + 5) * 60 * 1_000L, now)
        )
        assertEquals(
            TrialCountdownParts(TrialCountdownUnit.MINUTES, 3),
            trialCountdownParts(now + 3 * 60 * 1_000L, now)
        )
        assertEquals(
            TrialCountdownParts(TrialCountdownUnit.SECONDS, 0),
            trialCountdownParts(now, now)
        )
    }
}
