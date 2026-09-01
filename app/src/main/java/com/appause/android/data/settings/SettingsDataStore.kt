package com.appause.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore extension property — creates a DataStore instance.
 *
 * What is DataStore?
 * - DataStore is the modern replacement for SharedPreferences.
 * - It's built on Kotlin coroutines and Flow (instead of callbacks).
 * - Data is persisted asynchronously — no risk of blocking the main thread.
 * - It survives process death (stored in a file under the app's data directory).
 *
 * Why `by preferencesDataStore()`?
 * - This is a Kotlin delegate that creates the DataStore lazily on first access.
 * - The string "settings" is the file name (stored as /data/data/.../preferences/settings).
 * - Using an extension property on Context ensures we always use the same Context.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * SettingsDataStore — wrapper for simple key-value settings.
 *
 * We use DataStore for settings that don't need relational structure:
 * - Master toggle (is Appause enabled?)
 * - Default prompt message
 * - Language preference
 *
 * For structured data (groups, apps, records), we use Room instead.
 */
open class SettingsDataStore(private val context: Context) {

    // ── Keys ──
    // Keys define the "schema" of our preferences.
    // Each key has a name and a type.

    companion object {
        val IS_ENABLED_KEY = booleanPreferencesKey("is_enabled")
        val DEFAULT_PROMPT_KEY = stringPreferencesKey("default_prompt")
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val RECOMMENDED_APPS_KEY = stringSetPreferencesKey("recommended_apps")
        val SHOW_NOTIFICATION_KEY = booleanPreferencesKey("show_notification")
        val TEMPORARY_PASSES_KEY = stringSetPreferencesKey("temporary_passes")

        // ── Custom open-reason labels (Pro) ──
        // The 4 "why are you opening this app?" options on the Pause Screen.
        // Each key stores the user's custom label; blank means "use the
        // localized default string resource". Order: work, bored, messages, other.
        val REASON_WORK_KEY = stringPreferencesKey("reason_work")
        val REASON_BORED_KEY = stringPreferencesKey("reason_bored")
        val REASON_MESSAGES_KEY = stringPreferencesKey("reason_messages")
        val REASON_OTHER_KEY = stringPreferencesKey("reason_other")

        // ── Pro / license (monetization, plan B) ──
        // pro_unlocked: DEBUG-ONLY flag flipped by debug builds to force Pro on.
        //   Real Pro is gated by a verified license token (see ProState), not by
        //   this flag. Never set this in release builds.
        // license_token: the (signed, server-issued) JWT used to unlock Pro and
        //   to re-activate the app after a factory reset or device switch.
        val PRO_UNLOCKED_KEY = booleanPreferencesKey("pro_unlocked")
        val LICENSE_TOKEN_KEY = stringPreferencesKey("license_token")

        // ── First-launch onboarding ──
        // Whether the user has finished (or skipped) the onboarding flow.
        // Drives the NavGraph start destination (ONBOARDING vs HOME).
        val HAS_COMPLETED_ONBOARDING_KEY = booleanPreferencesKey("onboarding_done")

        // ── Permission-rationale intro ──
        // Whether the user has already seen the in-app explanation of WHY
        // Appause needs its permissions (accessibility / usage / battery /
        // overlay). Once true, tapping any "open settings" action goes straight
        // to the system page; before that, the app shows a one-time rationale
        // dialog first. Onboarding completion also sets this, since the guide
        // itself explains the permissions.
        val HAS_SEEN_PERMISSION_INTRO_KEY = booleanPreferencesKey("permission_intro_seen")

        // SharedPreferences key for sync locale override (used in attachBaseContext)
        private const val PREFS_NAME = "appause_locale_prefs"
        private const val PREF_LANGUAGE_KEY = "language"
        private const val PREF_THEME_MODE_KEY = "theme_mode"
    }

    // ── Read operations (return Flow for reactive observation) ──

    /**
     * Whether Appause is enabled (master toggle).
     * Default: true — Appause starts enabled.
     */
    val isEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_ENABLED_KEY] ?: true
    }

    /**
     * The default prompt message shown on the Pause Screen.
     * Empty string means "use localized default" — the UI falls back to stringResource().
     * This avoids storing a language-specific string that becomes stale after language switch.
     */
    val defaultPrompt: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_PROMPT_KEY] ?: ""
    }

    /**
     * The selected language code ("en" or "zh").
     * Falls back to SharedPreferences (which has system-detected value from attachBaseContext).
     */
    val language: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: getLanguageSync()
    }

    /**
     * The selected theme mode: "system" (follow the device), "light", or "dark".
     * Default: "system" — the app honors the device's light/dark setting until
     * the user explicitly picks one.
     */
    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: "system"
    }

    /**
     * Whether to show the persistent "monitoring" notification (and run the
     * accessibility service as a foreground service). Default: true.
     * Users who find the always-on notification annoying can turn it off; the
     * service still runs as a normal (non-foreground) accessibility service.
     */
    val showNotification: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_NOTIFICATION_KEY] ?: true
    }

    /**
     * The global set of recommended app package names.
     * These are shown as suggestions on the cooldown screen ("try one of these instead").
     * Replaces the old per-group "learning" type — now a single global list.
     */
    val recommendedApps: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[RECOMMENDED_APPS_KEY] ?: emptySet()
    }

    /** Persisted package-scoped temporary passes, represented by absolute expiry timestamps. */
    val temporaryPasses: Flow<Map<String, Long>> = context.dataStore.data.map { preferences ->
        TemporaryPassPolicy.parseAll(preferences[TEMPORARY_PASSES_KEY] ?: emptySet())
    }

    /** Custom label for the "work" open-reason option (blank = use default). */
    val reasonWork: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[REASON_WORK_KEY] ?: ""
    }

    /** Custom label for the "bored" open-reason option (blank = use default). */
    val reasonBored: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[REASON_BORED_KEY] ?: ""
    }

    /** Custom label for the "messages" open-reason option (blank = use default). */
    val reasonMessages: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[REASON_MESSAGES_KEY] ?: ""
    }

    /** Custom label for the "other" open-reason option (blank = use default). */
    val reasonOther: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[REASON_OTHER_KEY] ?: ""
    }

    /**
     * Debug-only Pro flag (flipped by debug builds). Real Pro is derived from a
     * verified license token in [com.appause.android.data.pro.ProState], not
     * from this flag. Defaults to false.
     */
    val isProDebug: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PRO_UNLOCKED_KEY] ?: false
    }

    /**
     * The stored license token (empty until a license is imported or unlocked).
     * Used by export/import to let the user restore Pro offline.
     */
    val licenseToken: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LICENSE_TOKEN_KEY] ?: ""
    }

    /**
     * Whether the user has completed (or skipped) the first-launch onboarding.
     * Default: false — the onboarding screen shows on first launch.
     */
    open val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_COMPLETED_ONBOARDING_KEY] ?: false
    }

    /**
     * Whether the user has seen the one-time permission-rationale explanation.
     * Default: false — the first permission request will show the rationale
     * dialog before jumping to system settings. Set true once shown (or when
     * onboarding is completed, since the guide explains the same thing).
     */
    open val hasSeenPermissionIntro: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_SEEN_PERMISSION_INTRO_KEY] ?: false
    }

    // ── Write operations (suspend functions — must be called from a coroutine) ──

    /** Update the master toggle. */
    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_ENABLED_KEY] = enabled
        }
    }

    /** Update the default prompt message. */
    suspend fun setDefaultPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_PROMPT_KEY] = prompt
        }
    }

    /**
     * Update the language preference.
     * Also syncs to SharedPreferences for immediate locale override.
     */
    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = languageCode
        }
        // Sync to SharedPreferences for attachBaseContext
        syncLanguageToPrefs(languageCode)
    }

    /**
     * Update the theme mode preference ("system", "light", or "dark").
     * The UI observes themeMode and recomposes immediately — no restart needed.
     * Also mirrored to SharedPreferences so the next cold start can read it
     * synchronously and apply the right theme on the first frame.
     */
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
        syncThemeModeToPrefs(mode)
    }

    /** Update the global recommended apps list. */
    suspend fun setRecommendedApps(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[RECOMMENDED_APPS_KEY] = packages
        }
    }

    /**
     * Grant one supported temporary pass. The caller supplies wall-clock time so
     * expiry behavior stays explicit and deterministic in unit tests.
     */
    suspend fun grantTemporaryPass(packageName: String, minutes: Int, now: Long): Long? {
        val expiresAt = TemporaryPassPolicy.expiresAt(now, minutes) ?: return null
        val encoded = TemporaryPassPolicy.encode(TemporaryPass(packageName, expiresAt)) ?: return null
        context.dataStore.edit { preferences ->
            val current = preferences[TEMPORARY_PASSES_KEY] ?: emptySet()
            val updated = current.mapNotNull { TemporaryPassPolicy.parse(it) }
                .filterNot { it.packageName == packageName }
                .mapNotNull { TemporaryPassPolicy.encode(it) }
                .toMutableSet()
            updated.add(encoded)
            preferences[TEMPORARY_PASSES_KEY] = updated
        }
        return expiresAt
    }

    /** Return an unexpired pass expiry, or null for missing, malformed, or expired data. */
    suspend fun temporaryPassExpiresAt(packageName: String, now: Long): Long? =
        temporaryPasses.first()[packageName]?.takeIf { TemporaryPassPolicy.isActive(it, now) }

    suspend fun isTemporaryPassActive(packageName: String, now: Long): Boolean =
        temporaryPassExpiresAt(packageName, now) != null

    /** Update the custom label for the "work" open-reason option. */
    suspend fun setReasonWork(value: String) {
        context.dataStore.edit { preferences -> preferences[REASON_WORK_KEY] = value }
    }

    /** Update the custom label for the "bored" open-reason option. */
    suspend fun setReasonBored(value: String) {
        context.dataStore.edit { preferences -> preferences[REASON_BORED_KEY] = value }
    }

    /** Update the custom label for the "messages" open-reason option. */
    suspend fun setReasonMessages(value: String) {
        context.dataStore.edit { preferences -> preferences[REASON_MESSAGES_KEY] = value }
    }

    /** Update the custom label for the "other" open-reason option. */
    suspend fun setReasonOther(value: String) {
        context.dataStore.edit { preferences -> preferences[REASON_OTHER_KEY] = value }
    }

    /** Mark Appause Pro as unlocked (or locked). */
    suspend fun setProUnlocked(unlocked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PRO_UNLOCKED_KEY] = unlocked
        }
    }

    /** Store the license token (used for offline re-activation). */
    suspend fun setLicenseToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[LICENSE_TOKEN_KEY] = token
        }
    }

    /**
     * Mark the first-launch onboarding as completed (or skipped).
     * Once true, the NavGraph starts at HOME instead of ONBOARDING.
     */
    open suspend fun setHasCompletedOnboarding(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING_KEY] = done
        }
    }

    /**
     * Mark the permission-rationale explanation as seen, so subsequent
     * permission requests skip the one-time dialog and go straight to settings.
     */
    open suspend fun setPermissionIntroSeen() {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEN_PERMISSION_INTRO_KEY] = true
        }
    }

    /**
     * Debug-only: reset the first-launch state so the onboarding guide can be
     * re-shown for testing. Clears both the completed flag and the
     * permission-intro-seen flag, so a re-run shows the full guide AND the
     * one-time permission rationale again. Mirrors the Pro debug tools in
     * ProScreen, which flip a similar test-only flag.
     */
    suspend fun clearOnboarding() {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING_KEY] = false
            preferences[HAS_SEEN_PERMISSION_INTRO_KEY] = false
        }
    }

    /** Update whether the persistent monitoring notification is shown. */
    suspend fun setShowNotification(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_NOTIFICATION_KEY] = show
        }
    }

    /**
     * Read the theme mode from SharedPreferences synchronously.
     * Used as the initial value when the Activity starts observing themeMode,
     * so dark-mode users don't see a light flash before the Flow emits.
     * Defaults to "system" when nothing has been saved yet.
     */
    fun getThemeModeSync(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_THEME_MODE_KEY, "system") ?: "system"
    }

    /** Mirror the theme mode into SharedPreferences for synchronous startup reads. */
    private fun syncThemeModeToPrefs(mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_THEME_MODE_KEY, mode).apply()
    }

    // ── SharedPreferences sync for locale override ──

    /**
     * Read language from SharedPreferences synchronously.
     * Used in attachBaseContext and DataStore Flow fallback.
     * On first launch, detects from system locale.
     */
    fun getLanguageSync(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(PREF_LANGUAGE_KEY)) {
            prefs.getString(PREF_LANGUAGE_KEY, "en") ?: "en"
        } else {
            val detected = if (java.util.Locale.getDefault().language == "zh") "zh" else "en"
            prefs.edit().putString(PREF_LANGUAGE_KEY, detected).apply()
            detected
        }
    }

    /**
     * Sync language to SharedPreferences for immediate locale override.
     * Called when language changes, before Activity recreation.
     */
    private fun syncLanguageToPrefs(languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANGUAGE_KEY, languageCode).apply()
    }
}
