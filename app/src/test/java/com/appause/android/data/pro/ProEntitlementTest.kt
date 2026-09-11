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
}
