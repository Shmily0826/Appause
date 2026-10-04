package com.appause.android.data.pro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.appause.android.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Entitlement resolution failure behavior.
 *
 * A transient Keystore/verifier outage must never present as a hard FREE for
 * a token that previously verified: that lie reaches every isPro gate, and
 * GroupEditorViewModel.save() persists a destructive re-remind wipe whenever
 * it observes isPro == false. The last-known-good hold (ProState.lastVerified)
 * is what keeps these cases stable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProStateEntitlementHoldTest {

    private lateinit var context: Application
    private lateinit var settings: SettingsDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = SettingsDataStore(context)
    }

    /** Lifetime claims: no "exp", so classification never touches the clock. */
    private fun lifetimeClaims() = LicenseClaims(
        tier = "pro",
        device = null,
        exp = null,
        iat = null,
        jti = null
    )

    private fun proState(verifier: (String, String) -> LicenseClaims?): ProState =
        ProState(
            settings = settings,
            context = context,
            fingerprintProvider = { "fp-1" },
            tokenVerifier = verifier,
            dispatcher = Dispatchers.Unconfined
        )

    @Test
    fun `transient verifier failure holds the last verified entitlement`() = runTest {
        var shouldFail = false
        val state = proState { _, _ ->
            if (shouldFail) throw IllegalStateException("keystore hiccup")
            lifetimeClaims()
        }
        settings.setLicenseToken("token-a")

        // First collection verifies and establishes the last-known-good state.
        assertEquals(ProAccessStatus.LIFETIME, state.entitlement.first().status)

        // Keystore goes down; the same token must NOT collapse to FREE.
        shouldFail = true
        val held = state.entitlement.first()
        assertEquals(ProAccessStatus.LIFETIME, held.status)
        assertTrue(held.isPro)
    }

    @Test
    fun `failure with nothing ever verified fails closed to FREE`() = runTest {
        val state = proState { _, _ -> throw IllegalStateException("keystore down") }
        settings.setLicenseToken("token-a")

        val held = state.entitlement.first()
        assertEquals(ProAccessStatus.FREE, held.status)
        assertFalse(held.isPro)
    }

    @Test
    fun `hard invalid answer is authoritative even after a prior success`() = runTest {
        var accept = true
        val state = proState { _, _ -> if (accept) lifetimeClaims() else null }
        settings.setLicenseToken("token-a")

        assertEquals(ProAccessStatus.LIFETIME, state.entitlement.first().status)

        // null means "signature/binding invalid" — a definitive answer, not an
        // outage — so it must resolve FREE instead of riding the cached claims.
        accept = false
        val held = state.entitlement.first()
        assertEquals(ProAccessStatus.FREE, held.status)
        assertFalse(held.isPro)
    }

    @Test
    fun `failure fallback does not leak across a token change`() = runTest {
        var shouldFail = false
        val state = proState { _, _ ->
            if (shouldFail) throw IllegalStateException("keystore hiccup")
            lifetimeClaims()
        }
        settings.setLicenseToken("token-a")
        assertEquals(ProAccessStatus.LIFETIME, state.entitlement.first().status)

        // A DIFFERENT token that hits the outage must not ride token-a's
        // cached claims — the hold is keyed by token.
        settings.setLicenseToken("token-b")
        shouldFail = true
        val held = state.entitlement.first()
        assertEquals(ProAccessStatus.FREE, held.status)
        assertFalse(held.isPro)
    }
}
