package com.appause.android.data.pro

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * The claims we care about, extracted from a verified license JWT.
 *
 * @param tier should be "pro" for a paid unlock.
 * @param device optional fingerprint the token is bound to. If present, it MUST
 *   match this device's fingerprint (see [DeviceKeyStore]). null/empty means
 *   the token is not device-bound (used by the DEV test token).
 * @param exp expiry in seconds since epoch, or null for a non-expiring (buyout) license.
 * @param iat issued-at in seconds since epoch, or null.
 * @param jti unique token id, useful for one-time redemption auditing server-side.
 */
data class LicenseClaims(
    val tier: String,
    val device: String?,
    val exp: Long?,
    val iat: Long?,
    val jti: String?
)

/**
 * LicenseVerifier — verifies an Appause Pro license token (JWT, RS256) entirely
 * on-device. No network call, no dependency on a JWT library.
 *
 * Verification steps:
 * 1. The token has three base64url parts (header.payload.signature).
 * 2. The header algorithm is "RS256".
 * 3. The signature verifies against [serverPublicKey] using SHA256withRSA.
 * 4. The payload "tier" is "pro".
 * 5. The token is not expired ("exp" claim, if present).
 * 6. If the token carries a "device" claim, it matches this device's fingerprint.
 */
object LicenseVerifier {

    /** Parse an RSA public key from a PEM string (stripping headers/whitespace). */
    fun parsePublicKey(pem: String): PublicKey {
        val clean = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(bytes)
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    /** Decode a base64url string, tolerating missing padding. */
    fun base64UrlDecode(input: String): ByteArray {
        var text = input.replace('-', '+').replace('_', '/')
        val missing = (4 - text.length % 4) % 4
        repeat(missing) { text += "=" }
        return Base64.decode(text, Base64.DEFAULT)
    }

    private fun decodeJsonSegment(segment: String): JSONObject {
        return JSONObject(String(base64UrlDecode(segment), Charsets.UTF_8))
    }

    /**
     * Verify a license token.
     * @return the parsed [LicenseClaims] if the token is valid AND bound to this
     *   device (or unbound), or null if anything fails.
     */
    fun verify(token: String, serverPublicKey: PublicKey, deviceFingerprint: String): LicenseClaims? {
        val parts = token.trim().split(".")
        if (parts.size != 3) return null

        // 1+2. Header must declare RS256.
        val header = decodeJsonSegment(parts[0])
        if (header.optString("alg") != "RS256") return null

        // 3. Signature check.
        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8)
        val signature = base64UrlDecode(parts[2])
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(serverPublicKey)
        verifier.update(signingInput)
        if (!verifier.verify(signature)) return null

        // 4-6. Payload claims.
        val payload = decodeJsonSegment(parts[1])
        val tier = payload.optString("tier", "")
        if (tier != "pro") return null

        val exp = if (payload.has("exp")) payload.getLong("exp") else null
        if (exp != null && System.currentTimeMillis() / 1000L > exp) return null

        val device = if (payload.has("device")) payload.getString("device") else null
        if (!device.isNullOrBlank() && device != deviceFingerprint) return null

        val iat = if (payload.has("iat")) payload.getLong("iat") else null
        val jti = if (payload.has("jti")) payload.getString("jti") else null

        return LicenseClaims(tier = tier, device = device, exp = exp, iat = iat, jti = jti)
    }
}
