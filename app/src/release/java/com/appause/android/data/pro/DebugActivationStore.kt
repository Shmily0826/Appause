package com.appause.android.data.pro

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Release-build counterpart of the activation override store: the override
 * does not exist here.
 *
 * [overrideFlow] always reports [DebugActivationOverride.None] and never opens
 * any storage, so a release build keeps the fail-closed behaviour of the real
 * license path and cannot read a debug override even if one is present on the
 * device.
 *
 * The mutators are intentionally inert no-ops. Release ships no UI that calls
 * them (the release `ProDebugTools` renders nothing), so there is no entry
 * point to reach them; keeping them as no-ops means the shared call sites in
 * `ProViewModel` still compile while a release build can never enter the debug
 * path or write anything.
 */
internal object DebugActivationStore {

    fun overrideFlow(context: Context): Flow<DebugActivationOverride> =
        flowOf(DebugActivationOverride.None)

    suspend fun activateForDays(
        context: Context,
        days: Int = DebugActivationPolicy.ACTIVATION_DAYS
    ) = Unit

    suspend fun cancel(context: Context) = Unit

    suspend fun clear(context: Context) = Unit
}
