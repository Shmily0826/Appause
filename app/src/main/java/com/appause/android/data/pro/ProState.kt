package com.appause.android.data.pro

import android.content.Context
import com.appause.android.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Result of a server-side activation attempt ([ProState.redeemCode]).
 * The UI maps [Error.reason] to a user-facing string.
 */
sealed interface RedeemResult {
    data object Success : RedeemResult
    data class Error(val reason: String) : RedeemResult
}

/**
 * ProState — the single source of truth for Appause Pro status.
 *
 * Plan B (this file): Pro is unlocked only when a LICENSE TOKEN is present AND
 * verifies locally:
 *   - the token's RS256 signature matches the embedded server public key
 *     ([ServerKeys]),
 *   - it has not expired,
 *   - if it is device-bound, the "device" claim matches this device's
 *     fingerprint ([DeviceKeyStore]).
 *
 * The token is stored in DataStore so Pro survives process death and can be
 * re-imported after a factory reset / device switch (offline, no server call).
 *
 * A separate debug flag (debug builds only) can force Pro on for development.
 * The signing private key stays server-side, so a fork of this open-source repo
 * cannot mint valid tokens — it can only verify them.
 */
class ProState(
    private val settings: SettingsDataStore,
    private val context: Context
) {

    /** Free-tier limits. Paid users are not constrained by these. */
    companion object {
        /** Free users can create at most this many groups. */
        const val FREE_GROUP_LIMIT = 2

        /** Free users' cooldown is capped at this many seconds. */
        const val FREE_COOLDOWN_MAX_SECONDS = 30

        /** Pro users can set cooldowns up to this many seconds. */
        const val PRO_COOLDOWN_MAX_SECONDS = 60

        /** Free users see at most this many days of history in Stats. */
        const val FREE_STATS_DAYS = 7

        /** Pro users see up to this many days of history (effectively all). */
        const val PRO_STATS_DAYS = 365
    }

    /**
     * Whether Appause Pro is currently unlocked.
     * True if the debug flag is on (debug builds) OR a stored license token
     * verifies locally (signature + expiry + device binding).
     */
    val isPro: Flow<Boolean> = combine(settings.licenseToken, settings.isProDebug) { token, debug ->
        if (debug) return@combine true
        if (token.isBlank()) return@combine false
        runCatching {
            val fingerprint = DeviceKeyStore.getDeviceFingerprint(context)
            val publicKey = LicenseVerifier.parsePublicKey(ServerKeys.SERVER_PUBLIC_KEY_PEM)
            LicenseVerifier.verify(token, publicKey, fingerprint) != null
        }.getOrDefault(false)
    }

    /** Debug-only unlock — only ever called from debug builds. */
    suspend fun unlockProDebug() {
        settings.setProUnlocked(true)
    }

    /**
     * Import and verify a license token.
     * @return true if the token is valid (and device-bound to this device, if
     *   claimed). The token is stored only when valid, so a mistyped or forged
     *   token never flips Pro on.
     */
    suspend fun importLicense(token: String): Boolean {
        val trimmed = token.trim()
        if (trimmed.isBlank()) return false
        val valid = runCatching {
            val fingerprint = DeviceKeyStore.getDeviceFingerprint(context)
            val publicKey = LicenseVerifier.parsePublicKey(ServerKeys.SERVER_PUBLIC_KEY_PEM)
            LicenseVerifier.verify(trimmed, publicKey, fingerprint) != null
        }.getOrDefault(false)
        if (valid) {
            settings.setLicenseToken(trimmed)
        }
        return valid
    }

    /**
     * Export the license token as a string the user can back up.
     * If no real token is stored yet (e.g. unlocked via debug), a debug
     * placeholder is returned so the screen still has something to show. It is
     * NOT stored, because it would not pass verification elsewhere.
     */
    suspend fun exportLicense(): String {
        val current = settings.licenseToken.first()
        if (current.isNotBlank()) return current
        return "APPAUSE-DEBUG-${System.currentTimeMillis()}"
    }

    /**
     * Redeem an activation code against the Plan B server (Cloudflare Worker).
     *
     * Flow:
     *  1. Compute this device's fingerprint (Android Keystore public key).
     *  2. POST { code, device } to {WORKER_BASE_URL}/api/redeem.
     *  3. On success the server returns a signed, device-bound JWT which we
     *     verify locally ([importLicense]) before storing — so a tampered or
     *     forged response never flips Pro on.
     *
     * This is the only network call in the app, and it is one-time (activation).
     * Returns [RedeemResult.Success] only when the returned token verifies.
     */
    suspend fun redeemCode(code: String): RedeemResult {
        val base = ProConfig.WORKER_BASE_URL
        if (base.isBlank()) return RedeemResult.Error("worker_not_configured")

        return withContext(Dispatchers.IO) {
            try {
                val fingerprint = DeviceKeyStore.getDeviceFingerprint(context)
                val body = JSONObject()
                    .put("code", code.trim().uppercase())
                    .put("device", fingerprint)
                    .toString()

                val url = URL("$base/api/redeem")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                conn.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val respText = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                conn.disconnect()

                if (responseCode !in 200..299) {
                    val reason = runCatching {
                        JSONObject(respText).optString("error", "http_$responseCode")
                    }.getOrDefault("http_$responseCode")
                    return@withContext RedeemResult.Error(reason)
                }

                val token = JSONObject(respText).getString("token")
                val verified = importLicense(token)
                if (verified) RedeemResult.Success
                else RedeemResult.Error("token_verify_failed")
            } catch (e: Exception) {
                RedeemResult.Error("network_error")
            }
        }
    }
}
