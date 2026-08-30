package com.appause.android.data.pro

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * Unit tests for LicenseVerifier — the gate that decides whether a token
 * unlocks Pro. These are the scenarios a forged/expired/misbound token must
 * fail, matching the verification steps documented in LicenseVerifier.
 *
 * Each test mints its own RSA keypair and builds a real RS256 JWT, so no
 * production key material is involved.
 */
class LicenseVerifierTest {

    private lateinit var serverKeyPair: java.security.KeyPair
    private lateinit var otherKeyPair: java.security.KeyPair
    private lateinit var serverPublicKeyPem: String

    private val deviceFingerprint = "device-abc-123"

    @Before
    fun setUp() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        serverKeyPair = generator.generateKeyPair()
        otherKeyPair = generator.generateKeyPair()

        // Encode the server public key as PEM, the same shape ServerKeys uses.
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(serverKeyPair.public.encoded)
        serverPublicKeyPem = "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----\n"
    }

    /** Build and sign a JWT exactly like the activation Worker does. */
    private fun mintToken(
        payload: JSONObject,
        keyPair: java.security.KeyPair = serverKeyPair,
        alg: String = "RS256"
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = JSONObject().put("alg", alg).put("typ", "JWT")
        val headerSeg = encoder.encodeToString(header.toString().toByteArray(Charsets.UTF_8))
        val payloadSeg = encoder.encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update("$headerSeg.$payloadSeg".toByteArray(Charsets.UTF_8))
        val signatureSeg = encoder.encodeToString(signer.sign())
        return "$headerSeg.$payloadSeg.$signatureSeg"
    }

    private fun proPayload(device: String? = deviceFingerprint, exp: Long? = null): JSONObject {
        val payload = JSONObject().put("tier", "pro")
        device?.let { payload.put("device", it) }
        exp?.let { payload.put("exp", it) }
        return payload
    }

    private fun verifyToken(
        token: String,
        fingerprint: String = deviceFingerprint,
        requireDeviceBinding: Boolean = false
    ): LicenseClaims? {
        return LicenseVerifier.verify(
            token = token,
            serverPublicKey = LicenseVerifier.parsePublicKey(serverPublicKeyPem),
            deviceFingerprint = fingerprint,
            requireDeviceBinding = requireDeviceBinding
        )
    }

    // ---------- Step 1: token shape ----------

    @Test
    fun `token without three parts is rejected`() {
        assertNull(verifyToken("only.one"))
        assertNull(verifyToken(""))
        assertNull(verifyToken("a.b.c.d"))
    }

    // ---------- Step 2: algorithm ----------

    @Test
    fun `non-RS256 algorithm is rejected`() {
        val token = mintToken(proPayload(), alg = "HS256")
        assertNull(verifyToken(token))
    }

    // ---------- Step 3: signature ----------

    @Test
    fun `token signed by a different key is rejected`() {
        val forged = mintToken(proPayload(), keyPair = otherKeyPair)
        assertNull(verifyToken(forged))
    }

    @Test
    fun `tampered payload invalidates the signature`() {
        val original = mintToken(proPayload())
        val parts = original.split(".")
        // Re-encode the payload with the device claim swapped out.
        val tamperedPayload = JSONObject()
            .put("tier", "pro")
            .put("device", "attacker-device")
        val newPayloadSeg = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(tamperedPayload.toString().toByteArray(Charsets.UTF_8))
        assertNull(verifyToken("${parts[0]}.$newPayloadSeg.${parts[2]}"))
    }

    // ---------- Step 4: tier ----------

    @Test
    fun `tier other than pro is rejected`() {
        val token = mintToken(JSONObject().put("tier", "trial").put("device", deviceFingerprint))
        assertNull(verifyToken(token))
    }

    // ---------- Step 5: expiry ----------

    @Test
    fun `expired token is rejected`() {
        val expiredAt = System.currentTimeMillis() / 1000L - 60
        assertNull(verifyToken(mintToken(proPayload(exp = expiredAt))))
    }

    @Test
    fun `token expiring in the future is accepted`() {
        val expiresAt = System.currentTimeMillis() / 1000L + 3600
        val claims = verifyToken(mintToken(proPayload(exp = expiresAt)))
        assertNotNull(claims)
        assertEquals(expiresAt, claims!!.exp)
    }

    // ---------- Step 6: device binding ----------

    @Test
    fun `token bound to another device is rejected`() {
        val token = mintToken(proPayload(device = "someone-elses-device"))
        assertNull(verifyToken(token))
    }

    @Test
    fun `token without device claim is accepted on any device`() {
        // The unbound (DEV test token) shape.
        val token = mintToken(proPayload(device = null))
        val claims = verifyToken(token, fingerprint = "a-completely-different-device")
        assertNotNull(claims)
        assertEquals("pro", claims!!.tier)
    }

    @Test
    fun `production verification rejects token without device claim`() {
        val token = mintToken(proPayload(device = null))
        assertNull(verifyToken(token, requireDeviceBinding = true))
    }

    @Test
    fun `production verification accepts token with matching device claim`() {
        val token = mintToken(proPayload(device = deviceFingerprint))
        val claims = verifyToken(token, requireDeviceBinding = true)
        assertNotNull(claims)
        assertEquals(deviceFingerprint, claims!!.device)
    }

    // ---------- Happy path ----------

    @Test
    fun `valid device-bound pro token is accepted with all claims parsed`() {
        val exp = System.currentTimeMillis() / 1000L + 86400
        val payload = JSONObject()
            .put("tier", "pro")
            .put("device", deviceFingerprint)
            .put("exp", exp)
            .put("iat", exp - 60)
            .put("jti", "token-id-1")
        val claims = verifyToken(mintToken(payload))
        assertNotNull(claims)
        claims!!
        assertEquals("pro", claims.tier)
        assertEquals(deviceFingerprint, claims.device)
        assertEquals(exp, claims.exp)
        assertEquals(exp - 60, claims.iat)
        assertEquals("token-id-1", claims.jti)
    }

    // ---------- Helpers ----------

    @Test
    fun `parsePublicKey accepts the PEM shape used by ServerKeys`() {
        val key = LicenseVerifier.parsePublicKey(serverPublicKeyPem)
        assertEquals("RSA", key.algorithm)
        assertEquals(
            (serverKeyPair.public as RSAPublicKey).modulus,
            (key as RSAPublicKey).modulus
        )
    }

    @Test
    fun `base64UrlDecode handles unpadded JWT segments`() {
        // "pro" base64url-encoded without padding — the shape every JWT uses.
        val decoded = LicenseVerifier.base64UrlDecode("cHJv")
        assertEquals("pro", String(decoded, Charsets.UTF_8))
    }
}
