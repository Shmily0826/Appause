package com.appause.android.data.settings

import android.content.Context
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
     * Default: "Take a moment."
     */
    val defaultPrompt: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_PROMPT_KEY] ?: "Take a moment."
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
}
