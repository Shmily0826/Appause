package com.appause.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTransitionPolicyTest {

    @Test
    fun `launcher handling is deferred while a cooldown overlay is showing`() {
        assertTrue(
            HomeTransitionPolicy.shouldDeferImmediateHandling(
                isHomePackage = true,
                pauseShown = true
            )
        )
    }

    @Test
    fun `launcher handling remains immediate when no cooldown overlay is showing`() {
        assertFalse(
            HomeTransitionPolicy.shouldDeferImmediateHandling(
                isHomePackage = true,
                pauseShown = false
            )
        )
    }

    @Test
    fun `recents launcher event does not confirm after target resume event`() {
        // Observed emulator ordering: the launcher event was newer than the
        // initial target event, but the target resumed 281 ms later.
        assertFalse(
            HomeTransitionPolicy.shouldConfirm(
                homePackage = "com.google.android.apps.nexuslauncher",
                lastObservedNavigationPackage = "com.android.chrome",
                pauseShown = true,
                pauseTargetPackage = "com.android.chrome",
                homeEventTime = 40_310_478L,
                latestRealForegroundEventTime = 40_310_759L
            )
        )
    }

    @Test
    fun `current target foreground rejects launcher confirmation before target event delivery`() {
        assertFalse(
            HomeTransitionPolicy.shouldConfirm(
                homePackage = "com.google.android.apps.nexuslauncher",
                lastObservedNavigationPackage = "com.google.android.apps.nexuslauncher",
                pauseShown = true,
                pauseTargetPackage = "com.android.chrome",
                homeEventTime = 40_310_478L,
                latestRealForegroundEventTime = 40_310_478L,
                currentForegroundPackage = "com.android.chrome"
            )
        )
    }

    @Test
    fun `stale target foreground is accepted by the bounded fail-open retry`() {
        assertTrue(
            HomeTransitionPolicy.shouldConfirm(
                homePackage = "com.google.android.apps.nexuslauncher",
                lastObservedNavigationPackage = "com.google.android.apps.nexuslauncher",
                pauseShown = true,
                pauseTargetPackage = "com.android.chrome",
                homeEventTime = 40_310_478L,
                latestRealForegroundEventTime = 40_310_478L,
                currentForegroundPackage = "com.android.chrome",
                allowStaleForegroundFallback = true
            )
        )
    }

    @Test
    fun `watchdog Home confirmation prioritizes a confirmed launcher over a late target event`() {
        assertTrue(
            HomeTransitionPolicy.shouldConfirm(
                homePackage = "com.google.android.apps.nexuslauncher",
                lastObservedNavigationPackage = "com.google.android.apps.nexuslauncher",
                pauseShown = true,
                pauseTargetPackage = "com.android.chrome",
                homeEventTime = 1_000L,
                latestRealForegroundEventTime = 1_100L,
                currentForegroundPackage = "com.google.android.apps.nexuslauncher",
                allowStaleForegroundFallback = true
            )
        )
    }

    @Test
    fun `confirmed launcher leave remains eligible after settle window`() {
        assertTrue(
            HomeTransitionPolicy.shouldConfirm(
                homePackage = "com.google.android.apps.nexuslauncher",
                lastObservedNavigationPackage = "com.google.android.apps.nexuslauncher",
                pauseShown = true,
                pauseTargetPackage = "com.android.chrome",
                homeEventTime = 40_310_478L,
                latestRealForegroundEventTime = 40_310_478L,
                currentForegroundPackage = "com.google.android.apps.nexuslauncher"
            )
        )
    }

    @Test
    fun `confirmed home dismisses an attached overlay`() {
        assertTrue(
            HomeTransitionPolicy.shouldDismissForConfirmedHome(
                homePackage = "com.miui.home",
                currentForegroundPackage = "com.miui.home",
                pausePresentationActive = true
            )
        )
    }

    @Test
    fun `system UI Home fallback requires a resolved launcher`() {
        assertTrue(
            HomeTransitionPolicy.shouldConfirmSystemUiHome(
                pausePresentationActive = true,
                pauseTargetPackage = "com.xingin.xhs",
                currentForegroundPackage = "com.miui.home",
                homePackages = setOf("com.miui.home")
            )
        )
        assertFalse(
            HomeTransitionPolicy.shouldConfirmSystemUiHome(
                pausePresentationActive = true,
                pauseTargetPackage = "com.xingin.xhs",
                currentForegroundPackage = "com.xingin.xhs",
                homePackages = setOf("com.miui.home")
            )
        )
    }

    @Test
    fun `system UI Home fallback can clear an orphaned presentation`() {
        assertTrue(
            HomeTransitionPolicy.shouldConfirmSystemUiHome(
                pausePresentationActive = true,
                pauseTargetPackage = null,
                currentForegroundPackage = "com.google.android.apps.nexuslauncher",
                homePackages = setOf("com.google.android.apps.nexuslauncher")
            )
        )
    }

    @Test
    fun `target foreground never dismisses an attached overlay for a stale home event`() {
        assertFalse(
            HomeTransitionPolicy.shouldDismissForConfirmedHome(
                homePackage = "com.miui.home",
                currentForegroundPackage = "tv.danmaku.bili",
                pausePresentationActive = true
            )
        )
    }

    @Test
    fun `bounded stale foreground fallback can dismiss after home event is accepted`() {
        assertTrue(
            HomeTransitionPolicy.shouldDismissForConfirmedHome(
                homePackage = "com.miui.home",
                currentForegroundPackage = "tv.danmaku.bili",
                pausePresentationActive = true,
                allowStaleForegroundFallback = true
            )
        )
    }

    @Test
    fun `watchdog fallback can dismiss an orphaned attached presentation`() {
        assertTrue(
            HomeTransitionPolicy.shouldDismissForConfirmedHome(
                homePackage = "com.miui.home",
                currentForegroundPackage = "com.miui.home",
                pausePresentationActive = false,
                allowStaleForegroundFallback = true
            )
        )
    }

    @Test
    fun `inactive presentation cannot be dismissed by home confirmation`() {
        assertFalse(
            HomeTransitionPolicy.shouldDismissForConfirmedHome(
                homePackage = "com.miui.home",
                currentForegroundPackage = "com.miui.home",
                pausePresentationActive = false
            )
        )
    }

    @Test
    fun `newer real app event makes a delivered home event stale`() {
        assertFalse(
            HomeTransitionPolicy.shouldConfirm(
                homePackage = "com.miui.home",
                lastObservedNavigationPackage = "com.miui.home",
                pauseShown = true,
                pauseTargetPackage = "tv.danmaku.bili",
                homeEventTime = 1_000L,
                latestRealForegroundEventTime = 1_100L
            )
        )
    }

    @Test
    fun `home event remains eligible when no newer real app event exists`() {
        assertTrue(
            HomeTransitionPolicy.shouldConfirm(
                homePackage = "com.miui.home",
                lastObservedNavigationPackage = "com.miui.home",
                pauseShown = true,
                pauseTargetPackage = "tv.danmaku.bili",
                homeEventTime = 1_000L,
                latestRealForegroundEventTime = 999L
            )
        )
    }

    @Test
    fun `zero event time keeps the bounded fallback confirmation path`() {
        assertTrue(
            HomeTransitionPolicy.shouldConfirm(
                homePackage = "com.miui.home",
                lastObservedNavigationPackage = "com.miui.home",
                pauseShown = true,
                pauseTargetPackage = "tv.danmaku.bili",
                homeEventTime = 0L,
                latestRealForegroundEventTime = 2_000L
            )
        )
    }
}
