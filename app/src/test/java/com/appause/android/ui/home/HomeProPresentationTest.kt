package com.appause.android.ui.home

import com.appause.android.R
import com.appause.android.data.pro.ProAccessStatus
import com.appause.android.data.pro.ProEntitlement
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeProPresentationTest {
    @Test fun `inactive presentation`() {
        assertEquals(HomeProPresentation(HomeProKind.INACTIVE, R.string.home_pro_title), homeProPresentation(ProEntitlement(ProAccessStatus.FREE), 0L))
    }

    @Test fun `trial rounds remaining days down`() {
        assertEquals(HomeProPresentation(HomeProKind.TRIAL, R.string.home_pro_trial, 6, HomeProTimeUnit.DAYS), homeProPresentation(ProEntitlement(ProAccessStatus.TRIAL_ACTIVE, 6L * DAY + 23L * HOUR), 0L))
    }

    @Test fun `trial under a day rounds remaining hours down`() {
        assertEquals(HomeProPresentation(HomeProKind.TRIAL, R.string.home_pro_trial, 23, HomeProTimeUnit.HOURS), homeProPresentation(ProEntitlement(ProAccessStatus.TRIAL_ACTIVE, 23L * HOUR + 59L * 60 * 1000), 0L))
    }

    @Test fun `lifetime presentation`() {
        assertEquals(HomeProPresentation(HomeProKind.LIFETIME, R.string.home_pro_lifetime), homeProPresentation(ProEntitlement(ProAccessStatus.LIFETIME), 0L))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val HOUR = 60L * 60 * 1000
    }
}
