package com.appause.android.data.settings

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake of [SettingsDataStore] for unit tests.
 *
 * It overrides only the four onboarding-backed members so the onboarding
 * persistence tests can assert synchronously without touching the real
 * DataStore — whose async IO writes deadlock under `runTest`. The real
 * DataStore (created lazily from the `context` passed to the super
 * constructor) is never accessed by this fake, so any `Context` (e.g. a
 * Robolectric `Application`) is safe to pass.
 *
 * All other settings (language, master toggle, Pro token, …) are inherited
 * but are not exercised by the onboarding code path.
 */
class FakeSettingsDataStore(context: Context) : SettingsDataStore(context) {

    private val _hasCompletedOnboarding = MutableStateFlow(false)
    private val _hasSeenPermissionIntro = MutableStateFlow(false)

    override val hasCompletedOnboarding: Flow<Boolean> = _hasCompletedOnboarding.asStateFlow()
    override val hasSeenPermissionIntro: Flow<Boolean> = _hasSeenPermissionIntro.asStateFlow()

    override suspend fun setHasCompletedOnboarding(done: Boolean) {
        _hasCompletedOnboarding.value = done
    }

    override suspend fun setPermissionIntroSeen() {
        _hasSeenPermissionIntro.value = true
    }
}
