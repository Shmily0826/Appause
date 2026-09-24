package com.appause.android.data.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProEntitlementTest {

    @Test
    fun `trial remains active before expiry`() {
        val entitlement = classifyLicenseClaims(
            LicenseClaims(
                tier = "pro",
                device = "device",
                exp = 1_000L,
                iat = null,
                jti = null,
                trial = true
            ),
            nowSeconds = 999L
        )

        assertEquals(ProAccessStatus.TRIAL_ACTIVE, entitlement.status)
        assertTrue(entitlement.isPro)
        assertEquals(1_000_000L, entitlement.expiresAt)
    }

    @Test
    fun `trial is expired after expiry`() {
        val entitlement = classifyLicenseClaims(
            LicenseClaims(
                tier = "pro",
                device = "device",
                exp = 1_000L,
                iat = null,
                jti = null,
                trial = true
            ),
            nowSeconds = 1_001L
        )

        assertEquals(ProAccessStatus.TRIAL_EXPIRED, entitlement.status)
        assertFalse(entitlement.isPro)
    }

    @Test
    fun `trial is expired at the exact expiry second`() {
        val entitlement = classifyLicenseClaims(
            LicenseClaims("pro", "device", 1_000L, null, null, trial = true),
            nowSeconds = 1_000L
        )

        assertEquals(ProAccessStatus.TRIAL_EXPIRED, entitlement.status)
        assertFalse(entitlement.isPro)
    }

    @Test
    fun `expiring non-trial license expires at the exact boundary`() {
        val entitlement = classifyLicenseClaims(
            LicenseClaims("pro", "device", 1_000L, null, null, trial = false),
            nowSeconds = 1_000L
        )

        assertEquals(ProAccessStatus.FREE, entitlement.status)
        assertFalse(entitlement.isPro)
    }

    @Test
    fun `legacy token without expiry remains lifetime`() {
        val entitlement = classifyLicenseClaims(
            LicenseClaims(
                tier = "pro",
                device = "device",
                exp = null,
                iat = null,
                jti = null
            ),
            nowSeconds = 9_999L
        )

        assertEquals(ProAccessStatus.LIFETIME, entitlement.status)
        assertTrue(entitlement.isPro)
    }

    @Test
    fun `entitlement refresh lands on expiry then stops once expired`() {
        val active = ProEntitlement(ProAccessStatus.TRIAL_ACTIVE, expiresAt = 120_000L)
        val expired = ProEntitlement(ProAccessStatus.TRIAL_EXPIRED, expiresAt = 120_000L)

        assertEquals(20_000L, entitlementRefreshDelayMillis(active, nowMillis = 100_000L))
        assertEquals(60_000L, entitlementRefreshDelayMillis(active, nowMillis = 0L))
        assertEquals(null, entitlementRefreshDelayMillis(expired, nowMillis = 120_000L))
    }

    @Test
    fun `process clock does not move backwards`() {
        assertEquals(2_000L, nonDecreasingClockMillis(2_000L, 1_000L))
        assertEquals(3_000L, nonDecreasingClockMillis(2_000L, 3_000L))
    }
}
