package com.appause.android.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Proves and pins the startup-recovery behavior when the DataStore file is
 * corrupted on disk.
 *
 * Why this file exists: [NavGraph][com.appause.android.ui.navigation.NavGraph]
 * calls `hasCompletedOnboarding.first()` inside a `LaunchedEffect` BEFORE any
 * destination exists. If the underlying `dataStore.data` flow terminates with
 * an exception, that coroutine dies with the app — every subsequent launch
 * crashes again, so the app is UNREACHABLE until the user clears storage or
 * reinstalls (losing Room data like groups and stats for nothing).
 *
 * The single test method covers the whole lifecycle on purpose: the
 * `Context.dataStore` delegate is a JVM-wide singleton, so a second test
 * method would see a DataStore bound to a previous test's filesDir.
 *
 * Runs on the JVM under Robolectric; the corrupted file is real bytes in a
 * real temp directory, not a mock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsDataStoreCorruptionTest {

    @Test
    fun corruptedSettingsFile_recoversWithDefaults_andKeepsHealthyRoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // preferencesDataStore(name = "settings") persists at this exact path.
        val corruptFile = File(context.filesDir, "datastore/settings.preferences_pb")
        corruptFile.parentFile.mkdirs()
        // Leading 0x00 is an invalid protobuf field tag → read fails with
        // CorruptionException (an IOException), the realistic on-disk damage.
        corruptFile.writeBytes(byteArrayOf(0x00, 0x2A, 0x7F, 0x42))

        val settings = SettingsDataStore(context)

        // Startup-critical reads must fall back to defaults instead of
        // throwing. NavGraph then shows onboarding again (acceptable: Room
        // groups/stats are untouched) instead of crash-looping forever.
        assertEquals(false, settings.hasCompletedOnboarding.first())
        assertEquals(true, settings.isEnabled.first())
        assertEquals(true, settings.showTemporaryPass.first())

        // Recovery must not leave the store broken: normal writes/reads work.
        settings.setEnabled(false)
        settings.setHasCompletedOnboarding(true)
        assertEquals(false, settings.isEnabled.first())
        assertEquals(true, settings.hasCompletedOnboarding.first())
        assertEquals(true, settings.showTemporaryPass.first())

        // The visibility choice is explicit and survives a fresh wrapper over
        // the same persistent DataStore, just like an app restart.
        settings.setShowTemporaryPass(false)
        assertEquals(false, SettingsDataStore(context).showTemporaryPass.first())
        settings.setShowTemporaryPass(true)
        assertEquals(true, SettingsDataStore(context).showTemporaryPass.first())

        // The DataStore delegate is a JVM-wide singleton shared by every test
        // in this Robolectric sandbox, bound to whichever test touches it
        // first. The service orchestration tests read isEnabled through the
        // SAME instance, so this test must leave the shared store in default
        // state (enabled, onboarding pending) — the values written above
        // would otherwise leak into whichever test class binds it next.
        settings.setEnabled(true)
        settings.setShowTemporaryPass(true)
        settings.setHasCompletedOnboarding(false)
        assertEquals(true, settings.isEnabled.first())
        assertEquals(true, settings.showTemporaryPass.first())
        assertEquals(false, settings.hasCompletedOnboarding.first())
    }
}
