package com.appause.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
class SettingsDataStore(private val context: Context) {

    // ── Keys ──
    // Keys define the "schema" of our preferences.
    // Each key has a name and a type.

    companion object {
        val IS_ENABLED_KEY = booleanPreferencesKey("is_enabled")
        val DEFAULT_PROMPT_KEY = stringPreferencesKey("default_prompt")
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

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
