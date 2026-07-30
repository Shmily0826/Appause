package com.appause.android.service

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * ForegroundChecker — confirms which app is genuinely on screen right now.
 *
 * Why we need this (it fixes the "notification false positive" bug):
 * - The AccessibilityService only sees the packageName carried by the
 *   TYPE_WINDOW_STATE_CHANGED event. When a media app (e.g. Bilibili) has a
 *   "now playing" notification and the user pulls down the notification shade,
 *   Android attributes that window change to the media app's package — even
 *   though the app is NOT actually in the foreground.
 * - So before we show the pause screen, we ask the system "what app is really
 *   on top?" via UsageStatsManager, and only intercept when the reported
 *   package is the real foreground app. This is the same approach Digital
 *   Wellbeing and similar apps use.
 *
 * This needs the PACKAGE_USAGE_STATS permission, which the user grants once
 * in Settings → Apps → Special app access → Usage access (mirrors how they
 * enable the accessibility service). Nothing is uploaded — the query is local.
 */
object ForegroundChecker {

    /**
     * @return the package name that is actually in the foreground right now,
     *         or null if we can't tell (usage access not granted yet, or no
     *         usage data available). A null result means "don't block" — the
     *         caller falls back to the old (less accurate) behavior.
     */
    fun getForegroundPackage(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return null

        val now = System.currentTimeMillis()
        // Query a 5-minute window so we're robust to usage-stats bucket
        // boundaries; the real foreground app's lastTimeUsed will be within
        // the last few seconds, so it will be the most recent entry.
        val stats = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 5 * 60 * 1000L,
                now
            )
        } catch (e: Exception) {
            return null
        }

        // The app with the most recent lastTimeUsed is the one on screen now.
        return stats
            .filter { it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    /**
     * @return true if the user has granted "Usage access" for Appause.
     *         When false, getForegroundPackage() returns null and the app
     *         falls back to the old behavior (so it still works, just with
     *         the occasional notification false positive until granted).
     */
    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = try {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } catch (e: Exception) {
            return false
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
