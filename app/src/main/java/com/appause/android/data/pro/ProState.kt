package com.appause.android.data.pro

import android.content.Context
import com.appause.android.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
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
 * A single HTTP response from the redeem endpoint, decoupled from the concrete
 * [HttpURLConnection] so the [ProState.redeemCode] flow can be exercised in
 * tests without a real network. The production implementation
 * ([HttpUrlConnectionTransport]) performs the real POST; tests supply fakes.
 */
data class RedeemHttpResponse(val code: Int, val body: String)

/**
 * Performs the one-time redeem POST and returns the raw HTTP response.
 * Kept as a single-method interface so tests can stub any server behavior
 * (timeouts, status codes, malformed bodies) without touching sockets.
 */
fun interface RedeemTransport {
    fun postRedeem(requestBody: String): RedeemHttpResponse
}

/**
 * Default [RedeemTransport] backed by [HttpURLConnection]. Behaves exactly like
 * the original inline implementation of [ProState.redeemCode]: POSTs JSON to
 * `{baseUrl}/api/redeem` with 15s connect/read timeouts, and returns the status
 * code plus the response body (success stream or error stream).
 */
class HttpUrlConnectionTransport(private val baseUrl: String) : RedeemTransport {
    override fun postRedeem(requestBody: String): RedeemHttpResponse {
        val url = URL("$baseUrl/api/redeem")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        conn.outputStream.use { os ->
            os.write(requestBody.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        conn.disconnect()
        return RedeemHttpResponse(code, body)
    }
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
 *
 * ## Testability
 * The redeem flow is isolated behind small, optional seams so it can be unit
 * tested without a network or the real signing key (which is intentionally
 * absent from the repo):
 *   - [transport] — the HTTP layer (defaults to [HttpUrlConnectionTransport]).
 *   - [fingerprintProvider] — device fingerprint (defaults to [DeviceKeyStore]).
 *   - [tokenVerifier] — JWT verification (defaults to [LicenseVerifier]).
 *   - [tokenPersister] — where a verified token is stored (defaults to
 *     [SettingsDataStore.setLicenseToken]).
 *   - [dispatcher] — coroutine dispatcher for the network call.
 * Every default reproduces the original production behavior, so no caller that
 * uses the `ProState(settings, context)` constructor changes.
 */
class ProState(
    private val settings: SettingsDataStore,
    private val context: Context,
    // ── Test seams (all default to production behavior) ──
    private val transport: RedeemTransport? = null,
    private val fingerprintProvider: (() -> String)? = null,
    private val tokenVerifier: ((String, String) -> LicenseClaims?)? = null,
    private val tokenPersister: (suspend (String) -> Unit)? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val defaultVerifier: (String, String) -> LicenseClaims? = { token, fp ->
        LicenseVerifier.verify(
            token,
            LicenseVerifier.parsePublicKey(ServerKeys.SERVER_PUBLIC_KEY_PEM),
            fp
        )
    }
    private val defaultFingerprint: () -> String = {
        DeviceKeyStore.getDeviceFingerprint(context)
    }
    private val defaultPersister: suspend (String) -> Unit = {
        settings.setLicenseToken(it)
    }

    /** Free-tier limits. Paid users are not constrained by these. */
    companion object {
        /** Free users can create at most this many groups. */
        const val FREE_GROUP_LIMIT = 1

        /**
         * Cooldown cap for everyone. Longer cooldown was a Pro perk but is now
         * free for all users, so the free and pro caps are identical.
         */
        const val FREE_COOLDOWN_MAX_SECONDS = 60

        /** Pro users can set cooldowns up to this many seconds. */
        const val PRO_COOLDOWN_MAX_SECONDS = 60

        /**
         * Stats history window for everyone. Longer history was a Pro perk but
         * is now free for all users, so the free and pro windows are identical.
         */
        const val FREE_STATS_DAYS = 365

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
     * Debug-only relock — only ever called from debug builds.
     * Clears both the debug flag and any stored license token so the app
     * returns to a clean free state (useful for testing the locked experience).
     */
    suspend fun relockProDebug() {
        settings.setProUnlocked(false)
        settings.setLicenseToken("")
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
        val fp = fingerprintProvider?.invoke() ?: defaultFingerprint()
        val verifier = tokenVerifier ?: defaultVerifier
        val valid = runCatching { verifier(trimmed, fp) != null }.getOrDefault(false)
        if (valid) (tokenPersister ?: defaultPersister).invoke(trimmed)
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
        val effectiveTransport = transport ?: run {
            val base = ProConfig.WORKER_BASE_URL
            if (base.isBlank()) return RedeemResult.Error("worker_not_configured")
            HttpUrlConnectionTransport(base)
        }

        return withContext(dispatcher) {
            try {
                val fingerprint = fingerprintProvider?.invoke() ?: defaultFingerprint()
                val body = JSONObject()
                    .put("code", code.trim().uppercase())
                    .put("device", fingerprint)
                    .toString()

                val resp = effectiveTransport.postRedeem(body)

                if (resp.code !in 200..299) {
                    val reason = runCatching {
                        JSONObject(resp.body).optString("error", "http_${resp.code}")
                    }.getOrDefault("http_${resp.code}")
                    return@withContext RedeemResult.Error(reason)
                }

                val token = JSONObject(resp.body).getString("token")
                val verified = importLicense(token)
                if (verified) RedeemResult.Success
                else RedeemResult.Error("token_verify_failed")
            } catch (e: Exception) {
                RedeemResult.Error("network_error")
            }
        }
    }
}
