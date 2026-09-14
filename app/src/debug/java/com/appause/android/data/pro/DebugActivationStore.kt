package com.appause.android.data.pro

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * Debug-build implementation of the activation override store.
 *
 * Persistence is a PRIVATE SharedPreferences file that only this object opens
 * — deliberately NOT the app's settings DataStore, so the override can never
 * land in a production-compatible store. The release variant of this class
 * never opens the file at all, so release builds cannot read it even when a
 * developer's device happens to carry one.
 *
 * Surviving a restart is intentional: it keeps the override active across the
 * app relaunches a developer does while testing, and it lives in its own
 * sandbox file so no real user data is touched.
 */
internal object DebugActivationStore {

    private const val PREFS_NAME = "appause_debug_activation"
    private const val KEY_MODE = "mode"
    private const val KEY_EXPIRES_AT = "expires_at"

    private const val MODE_NONE = "none"
    private const val MODE_ACTIVE = "active"
    private const val MODE_INACTIVE = "inactive"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun read(context: Context): DebugActivationOverride {
        val stored = prefs(context)
        return when (stored.getString(KEY_MODE, MODE_NONE)) {
            MODE_ACTIVE -> DebugActivationOverride.Active(stored.getLong(KEY_EXPIRES_AT, 0L))
            MODE_INACTIVE -> DebugActivationOverride.Inactive
            else -> DebugActivationOverride.None
        }
    }

    /**
     * Emits the current override immediately, then again on every change, so a
     * tap in the debug UI refreshes every Pro consumer through the single
     * entitlement flow.
     */
    fun overrideFlow(context: Context): Flow<DebugActivationOverride> = callbackFlow {
        trySend(read(context))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read(context))
        }
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs(context).unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** `Activate for 7 days`. */
    suspend fun activateForDays(
        context: Context,
        days: Int = DebugActivationPolicy.ACTIVATION_DAYS
    ) {
        val expiresAt = DebugActivationPolicy.activationExpiry(System.currentTimeMillis(), days)
        withContext(Dispatchers.IO) {
            prefs(context).edit()
                .putString(KEY_MODE, MODE_ACTIVE)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .apply()
        }
    }

    /** `Cancel activation` — forces the not-activated state. */
    suspend fun cancel(context: Context) {
        withContext(Dispatchers.IO) {
            prefs(context).edit()
                .putString(KEY_MODE, MODE_INACTIVE)
                .remove(KEY_EXPIRES_AT)
                .apply()
        }
    }

    /** `Use real activation state` — drops the override entirely. */
    suspend fun clear(context: Context) {
        withContext(Dispatchers.IO) {
            prefs(context).edit().clear().apply()
        }
    }
}
