package com.appause.android.data.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the debug activation override: the 7-day window, cancel semantics,
 * expiry behaviour, and the fallback to the real entitlement. Pure logic, so
 * every state is reachable without a device or waiting seven real days.
 */
class DebugActivationPolicyTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `activate for seven days stamps an expiry seven days ahead`() {
        val now = 1_000_000_000_000L
        assertEquals(now + 7 * day, DebugActivationPolicy.activationExpiry(now))
        assertEquals(7, DebugActivationPolicy.ACTIVATION_DAYS)
    }

    @Test
    fun `active override unlocks pro and reports the debug expiry`() {
        val now = 5_000L
        val effective = DebugActivationPolicy.resolve(
            override = DebugActivationOverride.Active(now + 7 * day),
            real = ProEntitlement(ProAccessStatus.FREE),
            nowMillis = now
        )
        assertTrue(effective.isPro)
        assertEquals(ProAccessStatus.DEBUG, effective.status)
        assertEquals(now + 7 * day, effective.expiresAt)
    }

    @Test
    fun `active override still holds on the exact expiry instant`() {
        val now = 9_000L
        val effective = DebugActivationPolicy.resolve(
            override = DebugActivationOverride.Active(now),
            real = ProEntitlement(ProAccessStatus.FREE),
            nowMillis = now
        )
        assertTrue(effective.isPro)
    }

    @Test
    fun `expired override reads as not activated`() {
        val now = 10_000L
        val effective = DebugActivationPolicy.resolve(
            override = DebugActivationOverride.Active(now - 1),
            real = ProEntitlement(ProAccessStatus.FREE),
            nowMillis = now
        )
        assertFalse(effective.isPro)
        assertEquals(ProAccessStatus.FREE, effective.status)
    }

    @Test
    fun `expired override does not silently restore a real license`() {
        // A lapsed override must read as "not activated" so the locked UI can be
        // exercised, instead of quietly falling back to a real license.
        val now = 10_000L
        val effective = DebugActivationPolicy.resolve(
            override = DebugActivationOverride.Active(now - 1),
            real = ProEntitlement(ProAccessStatus.LIFETIME),
            nowMillis = now
        )
        assertFalse(effective.isPro)
    }

    @Test
    fun `cancel forces not activated even with a lifetime license`() {
        val effective = DebugActivationPolicy.resolve(
            override = DebugActivationOverride.Inactive,
            real = ProEntitlement(ProAccessStatus.LIFETIME),
            nowMillis = 42L
        )
        assertFalse(effective.isPro)
        assertEquals(ProAccessStatus.FREE, effective.status)
    }

    @Test
    fun `no override returns the real entitlement untouched`() {
        val real = ProEntitlement(ProAccessStatus.LIFETIME)
        assertEquals(
            real,
            DebugActivationPolicy.resolve(
                override = DebugActivationOverride.None,
                real = real,
                nowMillis = 42L
            )
        )
    }

    @Test
    fun `no override keeps an unlicensed device inactive`() {
        val effective = DebugActivationPolicy.resolve(
            override = DebugActivationOverride.None,
            real = ProEntitlement(ProAccessStatus.FREE),
            nowMillis = 42L
        )
        assertFalse(effective.isPro)
        assertEquals(ProAccessStatus.FREE, effective.status)
    }

    @Test
    fun `no override preserves an expiring real license`() {
        val real = ProEntitlement(ProAccessStatus.EXPIRING_ACTIVE, expiresAt = 123_456L)
        val effective = DebugActivationPolicy.resolve(
            override = DebugActivationOverride.None,
            real = real,
            nowMillis = 42L
        )
        assertTrue(effective.isPro)
        assertEquals(123_456L, effective.expiresAt)
    }
}
