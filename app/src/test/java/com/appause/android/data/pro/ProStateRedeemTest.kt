package com.appause.android.data.pro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.appause.android.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Unit tests for [ProState.redeemCode] — the only network call in the app and
 * the money path (one-time Pro activation). The HTTP layer, device fingerprint,
 * token verifier and token persister are all injected (see ProState's test
 * seams), so every server behavior can be simulated without a real network or
 * the real (secret) signing key.
 *
 * These cover the "external requests" failure surface: timeout, non-2xx
 * statuses, empty/malformed JSON, missing fields, device-fingerprint mismatch,
 * and token write success/failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProStateRedeemTest {

    private lateinit var context: Application
    private lateinit var settings: SettingsDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = SettingsDataStore(context)
    }

    /**
     * Deterministic [RedeemTransport] that records the request body and either
     * returns a canned response or throws (to simulate timeouts / socket errors).
     */
    private class FakeTransport(
        private val response: RedeemHttpResponse? = null,
        private val throwable: Throwable? = null,
        val capturedBodies: MutableList<String> = mutableListOf()
    ) : RedeemTransport {
        override fun postRedeem(requestBody: String): RedeemHttpResponse {
            capturedBodies.add(requestBody)
            throwable?.let { throw it }
            return response ?: RedeemHttpResponse(200, "{}")
        }
    }

    private fun proState(
        transport: RedeemTransport,
        fingerprint: String = "device-fp-123",
        verifier: ((String, String) -> LicenseClaims?)? = null,
        persister: (suspend (String) -> Unit)? = null
    ): ProState = ProState(
        settings = settings,
        context = context,
        transport = transport,
        fingerprintProvider = { fingerprint },
        tokenVerifier = verifier,
        tokenPersister = persister,
        dispatcher = Dispatchers.Unconfined
    )

    /** A verifier that accepts any token and reports the given device. */
    private fun acceptingVerifier(device: String = "device-fp-123") =
        { _: String, _: String -> LicenseClaims("pro", device, null, null, null) }

    @Test
    fun `timeout or socket error maps to network_error`() = runTest {
        val transport = FakeTransport(throwable = SocketTimeoutException("timed out"))
        val result = proState(transport).redeemCode("ABC-123")
        assertTrue(result is RedeemResult.Error)
        assertEquals("network_error", (result as RedeemResult.Error).reason)
    }

    @Test
    fun `generic IOException also maps to network_error`() = runTest {
        val transport = FakeTransport(throwable = IOException("connection reset"))
        val result = proState(transport).redeemCode("ABC-123")
        assertEquals(RedeemResult.Error("network_error"), result)
    }

    @Test
    fun `non-2xx with server error reason surfaces that reason`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(400, """{"error":"invalid_code"}"""))
        val result = proState(transport).redeemCode("ABC-123")
        assertEquals(RedeemResult.Error("invalid_code"), result)
    }

    @Test
    fun `non-2xx 500 with empty body falls back to http_500`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(500, ""))
        val result = proState(transport).redeemCode("ABC-123")
        assertEquals(RedeemResult.Error("http_500"), result)
    }

    @Test
    fun `non-2xx with malformed error body falls back to http_code`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(404, "{not valid json"))
        val result = proState(transport).redeemCode("ABC-123")
        assertEquals(RedeemResult.Error("http_404"), result)
    }

    @Test
    fun `success 200 with valid token returns Success and writes the token`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(200, """{"token":"GOOD-TOKEN"}"""))
        var written: String? = null
        val result = proState(
            transport,
            verifier = acceptingVerifier(),
            persister = { written = it }
        ).redeemCode("ABC-123")
        assertEquals(RedeemResult.Success, result)
        assertEquals("GOOD-TOKEN", written)
    }

    @Test
    fun `success 200 but empty body maps to network_error`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(200, ""))
        val result = proState(transport, verifier = acceptingVerifier()).redeemCode("ABC-123")
        assertEquals(RedeemResult.Error("network_error"), result)
    }

    @Test
    fun `success 200 with malformed JSON maps to network_error`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(200, "totally not json"))
        val result = proState(transport, verifier = acceptingVerifier()).redeemCode("ABC-123")
        assertEquals(RedeemResult.Error("network_error"), result)
    }

    @Test
    fun `success 200 with valid JSON missing token field maps to network_error`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(200, """{"foo":"bar"}"""))
        val result = proState(transport, verifier = acceptingVerifier()).redeemCode("ABC-123")
        assertEquals(RedeemResult.Error("network_error"), result)
    }

    @Test
    fun `token that fails verification maps to token_verify_failed and is not written`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(200, """{"token":"FORGED"}"""))
        var written: String? = null
        val result = proState(
            transport,
            verifier = { _: String, _: String -> null }, // rejects everything
            persister = { written = it }
        ).redeemCode("ABC-123")
        assertEquals(RedeemResult.Error("token_verify_failed"), result)
        assertEquals(null, written)
    }

    @Test
    fun `device-bound token for another device maps to token_verify_failed`() = runTest {
        // Server returns a token bound to a different device; the verifier (which
        // stands in for the real RS256 device-binding check) rejects it.
        val transport = FakeTransport(RedeemHttpResponse(200, """{"token":"JWT-device-OTHER"}"""))
        val verifier = { token: String, _: String ->
            if (token.contains("OTHER")) null else LicenseClaims("pro", "device-fp-123", null, null, null)
        }
        val result = proState(transport, verifier = verifier).redeemCode("ABC-123")
        assertEquals(RedeemResult.Error("token_verify_failed"), result)
    }

    @Test
    fun `token write failure keeps Pro locked and returns network_error`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(200, """{"token":"GOOD-TOKEN"}"""))
        var written: String? = null
        val result = proState(
            transport,
            verifier = acceptingVerifier(),
            persister = { throw IOException("disk full") }
        ).redeemCode("ABC-123")
        // Fails safe: a write failure must never unlock Pro.
        assertEquals(RedeemResult.Error("network_error"), result)
        assertEquals(null, written)
    }

    @Test
    fun `activation code is uppercased before being sent`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(200, """{"token":"GOOD"}"""))
        proState(transport, verifier = acceptingVerifier(), persister = {}).redeemCode("abc-123")
        val sent = transport.capturedBodies.last()
        assertTrue("request body should contain uppercased code", sent.contains("\"code\":\"ABC-123\""))
    }

    @Test
    fun `device fingerprint is included in the request body`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(200, """{"token":"GOOD"}"""))
        proState(transport, fingerprint = "device-fp-xyz", verifier = acceptingVerifier(), persister = {})
            .redeemCode("ABC-123")
        val sent = transport.capturedBodies.last()
        assertTrue(sent.contains("\"device\":\"device-fp-xyz\""))
    }

    @Test
    fun `blank code is still sent (uppercased to empty)`() = runTest {
        val transport = FakeTransport(RedeemHttpResponse(200, """{"token":"GOOD"}"""))
        proState(transport, verifier = acceptingVerifier(), persister = {}).redeemCode("")
        val sent = transport.capturedBodies.last()
        assertTrue(sent.contains("\"code\":\"\""))
        // (In production the server would reject the blank code; we only pin the client behavior.)
        assertFalse(sent.contains("\"code\":\" \""))
    }
}
