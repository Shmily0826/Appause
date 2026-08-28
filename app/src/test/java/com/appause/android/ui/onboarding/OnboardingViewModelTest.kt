package com.appause.android.ui.onboarding

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.appause.android.data.settings.FakeSettingsDataStore
import com.appause.android.data.settings.SettingsDataStore
import com.appause.android.service.AccessibilityServiceChecker
import com.appause.android.service.ForegroundChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [OnboardingViewModel] — page navigation, completion/skip
 * persistence, and the permission-status refresh.
 *
 * The ViewModel's settings seam lets us inject [FakeSettingsDataStore], an
 * in-memory fake, so the persistence assertions are synchronous (the real
 * DataStore's async IO writes deadlock under `runTest`). `Dispatchers.setMain`
 * is set to `Unconfined` so `viewModelScope.launch { ... }` runs eagerly and
 * the fake flows update before the test reads them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingViewModelTest {

    private lateinit var app: Application
    private lateinit var settings: FakeSettingsDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        // In-memory fake — no real DataStore, so persistence is observable
        // synchronously without a `withTimeout`/`first { it }` race.
        settings = FakeSettingsDataStore(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(): OnboardingViewModel = OnboardingViewModel(app, settings)

    @Test
    fun `nextPage advances and clamps at the last step`() {
        val viewModel = vm()
        assertEquals(0, viewModel.page.value)
        viewModel.nextPage()
        assertEquals(1, viewModel.page.value)
        repeat(10) { viewModel.nextPage() }
        assertEquals(7, viewModel.page.value) // max step
    }

    @Test
    fun `prevPage never goes below zero`() {
        val viewModel = vm()
        viewModel.prevPage()
        assertEquals(0, viewModel.page.value)
        viewModel.nextPage()
        viewModel.prevPage()
        assertEquals(0, viewModel.page.value)
    }

    @Test
    fun `completeOnboarding persists completion and permission intro`() = runTest {
        val viewModel = vm()
        viewModel.completeOnboarding()
        // The fake flows update synchronously, so first() returns immediately.
        assertTrue("onboarding should be marked completed", settings.hasCompletedOnboarding.first())
        assertTrue("permission intro should be marked seen", settings.hasSeenPermissionIntro.first())
    }

    @Test
    fun `skipOnboarding persists completion but not permission intro`() = runTest {
        val viewModel = vm()
        viewModel.skipOnboarding()
        assertTrue("onboarding should be marked completed", settings.hasCompletedOnboarding.first())
        // Skipping means the user never saw the inline explanations.
        assertFalse("permission intro should NOT be marked seen on skip", settings.hasSeenPermissionIntro.first())
    }

    @Test
    fun `refreshServiceStatus populates permission flows without crashing`() {
        val viewModel = vm()
        viewModel.refreshServiceStatus()
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        assertEquals(
            "isServiceRunning must mirror AccessibilityServiceChecker",
            AccessibilityServiceChecker.isEnabled(app),
            viewModel.isServiceRunning.value
        )
        assertEquals(
            "canDrawOverlays must mirror Settings.canDrawOverlays",
            Settings.canDrawOverlays(app),
            viewModel.canDrawOverlays.value
        )
        assertEquals(
            "isUsageAccessGranted must mirror ForegroundChecker",
            ForegroundChecker.isUsageAccessGranted(app),
            viewModel.isUsageAccessGranted.value
        )
        assertEquals(
            "isIgnoringBattery must mirror PowerManager",
            pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false,
            viewModel.isIgnoringBattery.value
        )
    }
}
