package com.appause.android.data.pro

import com.appause.android.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * ProState — the single source of truth for Appause Pro status.
 *
 * This is plan-A scaffolding (no backend yet):
 * - [isPro] tells the UI whether paid features are available.
 * - [unlockPro] / [importLicense] flip Pro on.
 * - [exportLicense] / [importLicense] move the license token in and out of the
 *   app, so the user can restore Pro after a factory reset or device switch
 *   without contacting a server.
 *
 * The license token is just a string for now. Plan B will replace the
 * placeholder check in [importLicense] with real signature verification
 * against a server-issued token.
 */
class ProState(private val settings: SettingsDataStore) {

    /** Free-tier limits. Paid users are not constrained by these. */
    companion object {
        /** Free users can create at most this many groups. */
        const val FREE_GROUP_LIMIT = 2

        /** Free users' cooldown is capped at this many seconds. */
        const val FREE_COOLDOWN_MAX_SECONDS = 30
    }

    /** Whether Appause Pro is currently unlocked. */
    val isPro: Flow<Boolean> = settings.isPro

    /**
     * Unlock Pro.
     * @param licenseToken optional token to store (e.g. from a real purchase).
     *   When null, no token is written — used by the debug unlock in debug builds.
     */
    suspend fun unlockPro(licenseToken: String? = null) {
        settings.setProUnlocked(true)
        if (!licenseToken.isNullOrBlank()) {
            settings.setLicenseToken(licenseToken)
        }
    }

    /**
     * Export the license token as a string the user can back up.
     * If no token is stored yet (e.g. unlocked via debug), a debug token is
     * generated so the export/import round-trip can still be tested.
     */
    suspend fun exportLicense(): String {
        val current = settings.licenseToken.first()
        if (current.isNotBlank()) return current
        val debug = "APPAUSE-DEBUG-${System.currentTimeMillis()}"
        settings.setLicenseToken(debug)
        return debug
    }

    /**
     * Import a license token.
     * @return true if the token was accepted and Pro unlocked.
     *
     * Plan A: accepts any non-blank token (placeholder).
     * Plan B: verify the token's signature locally before accepting it.
     */
    suspend fun importLicense(token: String): Boolean {
        if (token.isBlank()) return false
        settings.setLicenseToken(token)
        settings.setProUnlocked(true)
        return true
    }
}
