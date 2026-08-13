package com.appause.android.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * ForegroundChecker — confirms which app is genuinely on screen right now.
 *
 * Why we need this (it fixes two real bugs):
 *
 * 1. "Notification false positive": when a media app (e.g. Bilibili) has a
 *    "now playing" notification and the user pulls down the shade, Android
 *    attributes the window change to the media app's package — even though the
 *    app is NOT in the foreground.
 *
 * 2. "Recents / task-switch false positive" (the HyperOS bug reported
 *    2026-08-09): on MIUI/HyperOS, opening Recents or swiping the gesture bar
 *    to switch apps makes the system emit a BURST of TYPE_WINDOW_STATE_CHANGED
 *    events — one for every cached task — within the same 2-3 ms:
 *
 *        12:52:52.705  package=com.xingin.xhs
 *        12:52:52.708  package=com.miui.home
 *        12:52:52.708  package=com.miui.personalassistant
 *
 *    The accessibility event stream alone CANNOT distinguish that burst from a
 *    genuine app launch — the event sequence is byte-for-byte the same shape.
 *    Only the system's own usage-event log knows which app actually ended up
 *    resumed. That is what this class reads.
 *
 * Implementation note — why queryEvents() and not queryUsageStats():
 * - The old implementation used queryUsageStats() and picked the entry with the
 *   largest `lastTimeUsed`. That is an AGGREGATE per package, so an app that was
 *   re-fronted for 3 ms during a task-switch animation gets the same fresh
 *   timestamp as the app the user is actually looking at. With 3 ms between
 *   them, "who is on top" became a coin flip — which is exactly why the pause
 *   screen kept popping on the launcher.
 * - queryEvents() returns the ordered event log (RESUMED / PAUSED per activity).
 *   The last RESUMED that has not been followed by its own PAUSED is the real
 *   foreground app. This is how Digital Wellbeing-style apps do it.
 *
 * This needs the PACKAGE_USAGE_STATS permission, which the user grants once in
 * Settings → Apps → Special app access → Usage access. Nothing is uploaded —
 * the query is local and read-only.
 */
object ForegroundChecker {

    /**
     * UsageEvents.Event.ACTIVITY_RESUMED (API 29+) and its pre-29 alias
     * MOVE_TO_FOREGROUND share the same numeric value (1). Same for
     * ACTIVITY_PAUSED / MOVE_TO_BACKGROUND (2). We use the literals so the
     * code compiles cleanly on minSdk 26 without deprecation suppressions.
     */
    private const val EVENT_ACTIVITY_RESUMED = 1
    private const val EVENT_ACTIVITY_PAUSED = 2

    /**
     * How far back to read the usage-event log. Long enough to always contain
     * the most recent resume (even if the user stared at one app for a while),
     * short enough to stay cheap.
     */
    private const val LOOKBACK_MS = 60_000L

    /** Wider second pass, used only when the 60 s window contained no events. */
    private const val LOOKBACK_WIDE_MS = 10 * 60_000L

    /**
     * @return the package name that is actually in the foreground right now,
     *         or null if we can't tell (usage access not granted, or the system
     *         has no usage data yet). A null result means "don't block" — the
     *         caller falls back to the less accurate event-only behavior.
     */
    fun getForegroundPackage(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return null

        return resolveFromEvents(usageStatsManager, LOOKBACK_MS)
            ?: resolveFromEvents(usageStatsManager, LOOKBACK_WIDE_MS)
    }

    /**
     * @return true if [pkg] had a genuine ACTIVITY_RESUMED event in the last
     *         [windowMs] milliseconds.
     *
     * Unlike [getForegroundPackage] (which reports the app CURRENTLY on top),
     * this confirms the app was actually opened even if the user has since
     * switched away. That is exactly what we want for interception: opening
     * 小红书 and glancing back at Appause should still trigger the pause screen.
     * Notifications and Recents glances never produce a real RESUMED for the
     * target, so they remain correctly ignored.
     */
    fun wasResumedRecently(context: Context, pkg: String, windowMs: Long): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        val events = try {
            usageStatsManager.queryEvents(now - windowMs, now)
        } catch (e: Exception) {
            return false
        } ?: return false
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == EVENT_ACTIVITY_RESUMED && event.packageName == pkg) {
                return true
            }
        }
        return false
    }

    /**
     * Replay the system's usage-event log over the last [lookbackMs] and return
     * the package that is currently resumed.
     *
     * We walk the events in order and keep the most recent RESUMED package. A
     * PAUSED event for that same package clears it, which is what makes the
     * transient task-switch case resolve correctly: xhs RESUMED → xhs PAUSED →
     * launcher RESUMED leaves us with the launcher, not xhs.
     */
    private fun resolveFromEvents(
        usageStatsManager: UsageStatsManager,
        lookbackMs: Long
    ): String? {
        val now = System.currentTimeMillis()
        val events = try {
            usageStatsManager.queryEvents(now - lookbackMs, now)
        } catch (e: Exception) {
            return null
        } ?: return null

        val event = UsageEvents.Event()
        var currentTop: String? = null
        var sawAnyEvent = false

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                EVENT_ACTIVITY_RESUMED -> {
                    sawAnyEvent = true
                    currentTop = event.packageName
                }
                EVENT_ACTIVITY_PAUSED -> {
                    sawAnyEvent = true
                    // Only clear if the paused activity belongs to the app we
                    // currently believe is on top. Pauses from other packages
                    // are stale tail-end events and must not blank our answer.
                    if (currentTop == event.packageName) {
                        currentTop = null
                    }
                }
            }
        }

        // No events at all in this window → tell the caller to widen the search
        // (or, on the wide pass, that we genuinely cannot determine anything).
        if (!sawAnyEvent) return null

        // We saw events but everything is paused (e.g. screen off). Treat that
        // as "unknown" rather than guessing.
        return currentTop
    }

    /**
     * @return true if the user has granted "Usage access" for Appause.
     *         When false, getForegroundPackage() returns null and interception
     *         runs unverified — which is when the false positives above happen.
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
