package com.appause.android.data.query

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins the dedupe of launcher entries: one app can expose SEVERAL
 * MAIN/LAUNCHER activities and [android.content.pm.PackageManager]
 * .queryIntentActivities() returns one ResolveInfo per activity. The app
 * select screen keys its list items by packageName, so a duplicated package
 * in this list crashes recomposition with "Key ... was already used"
 * (reproduced on a Pixel-style emulator via the Google app).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppQueryServiceTest {

    private fun launcherResolveInfo(packageName: String): ResolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.MainActivity"
        }
    }

    private fun installLauncherActivity(packageName: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, launcherResolveInfo(packageName))
    }

    @Test
    fun appWithMultipleLauncherActivities_appearsOnce() {
        installLauncherActivity("com.example.multi")
        installLauncherActivity("com.example.multi") // second launcher activity, same app
        installLauncherActivity("com.example.other")

        val apps = AppQueryService(ApplicationProvider.getApplicationContext()).getLaunchableApps()

        assertEquals(listOf("com.example.multi", "com.example.other"), apps.map { it.packageName })
    }

    @Test
    fun appauseItself_isExcluded() {
        val myPackage = ApplicationProvider.getApplicationContext<android.content.Context>().packageName
        installLauncherActivity(myPackage)
        installLauncherActivity("com.example.other")

        val apps = AppQueryService(ApplicationProvider.getApplicationContext()).getLaunchableApps()

        assertEquals(listOf("com.example.other"), apps.map { it.packageName })
    }
}
