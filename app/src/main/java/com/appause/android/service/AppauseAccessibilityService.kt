package com.appause.android.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.appause.android.util.AppLogger
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.appause.android.AppauseApp
import com.appause.android.R
import com.appause.android.interception.InterceptionManager
import com.appause.android.service.ForegroundChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

/**
 * AppauseAccessibilityService — detects foreground app changes and triggers cooldowns.
 *
 * How it works:
 * 1. System sends TYPE_WINDOW_STATE_CHANGED events when Activities appear.
 * 2. We read the event's packageName to know which app is in the foreground.
 * 3. We filter out irrelevant events (Appause, launcher, system UI, duplicates).
 * 4. We check if the app belongs to any configured group (via Repository).
 * 5. If yes, we launch PauseActivity to show the cooldown screen.
 *
 * Limitations:
 * - Events fire for EVERY Activity transition across ALL apps.
 * - Some OEM ROMs may not include packageName or may kill the service.
 * - We handle null packageName and missing groups gracefully.
 *
 * IMPORTANT: This is an accessibility feature, NOT a monitoring tool.
 * We only read package names — we never read UI content or user data.
 */
class AppauseAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppauseA11yService"
        private const val NOTIFICATION_CHANNEL_ID = "appause_monitoring"
        private const val NOTIFICATION_ID = 1

        /**
         * Guard flag: true while the cooldown overlay is showing.
         * Prevents re-triggering interception while a cooldown is in progress.
         *
         * @Volatile so the broadcast-thread PauseAlarmReceiver sees the latest
         * value when it reads this flag off the main thread.
         */
        @Volatile
        var pauseShown: Boolean = false

        /**
         * The package the user just cancelled out of on the Pause screen.
         *
         * Why we need this (this was a real bug):
         * - When the user taps Cancel, we dismiss the overlay and send them home.
         * - But the target app's window fires one more TYPE_WINDOW_STATE_CHANGED
         *   event in the brief moment before the launcher takes over the screen.
         * - By then `pauseShown` is already false, and `lastForegroundPackage`
         *   has been overwritten (by the overlay's own Appause-owned window event),
         *   so the duplicate check no longer protects us.
         * - Result: the cooldown overlay popped up again on the home screen
         *   without the user opening any app.
         *
         * How it works:
         * - Set to the target package right before we dismiss on Cancel.
         * - handleForegroundChange ignores events for this package.
         * - Cleared as soon as any OTHER app (e.g. the launcher) becomes
         *   foreground, so a genuine re-open of the app later is intercepted
         *   normally.
         */
        @Volatile
        var justCancelledPackage: String? = null

        /**
         * Last seen foreground package. Used to skip duplicate events.
         * When the same package appears in consecutive events (Activity switch
         * within the same app), we skip it to avoid re-triggering the cooldown.
         *
         * Lives in the companion object (not instance-private) because
         * [noteCancelled] — called from OverlayManager on Cancel — needs to
         * reset it so an immediate re-open of the same app re-triggers the
         * cooldown instead of being swallowed as a stale echo.
         */
        @Volatile
        var lastForegroundPackage: String? = null

        /**
         * Static handle to the running service instance. Lets the Settings
         * screen toggle the monitoring notification (start/stop foreground)
         * at runtime without restarting the service.
         */
        @Volatile
        var instance: AppauseAccessibilityService? = null

        /**
         * Mark an app as "just cancelled" and schedule the suppression to clear
         * after a short grace window.
         *
         * Why a timeout (instead of only clearing on the next non-system app)?
         * After Cancel we send the user to the home screen (a system/launcher
         * package). The original code only cleared this flag when a *non-system*
         * app became foreground, so while the user sat on the launcher the flag
         * was never cleared — and from then on every foreground event for the
         * cancelled app matched the suppression, permanently exempting it from
         * interception ("tap Cancel once → can switch freely forever").
         *
         * The timeout keeps the original purpose (suppress the stale window
         * event that fires for the target app in the brief moment before the
         * launcher takes over — which can arrive within ~1.5s of Cancel), then
         * re-enables interception so the next genuine open is caught normally.
         */
        private val cancelClearHandler = Handler(Looper.getMainLooper())
        fun noteCancelled(packageName: String) {
            justCancelledPackage = packageName
            // Reset the last-foreground tracker so an immediate re-open of the
            // same app is treated as a genuine new foreground (not a stale echo).
            // Without this, lastForegroundPackage would still equal the cancelled
            // app, and a quick tap to re-open it would be mistaken for the stale
            // post-cancel event and silently skipped. After reset, the re-open
            // flows through the normal foreground check (step 6.5) and re-cools.
            lastForegroundPackage = null
            cancelClearHandler.removeCallbacksAndMessages(null)
            // Keep the suppression SHORT. Its only job is to swallow the single
            // stale window event that fires for the cancelled app in the brief
            // moment before the launcher takes over — which arrives within a few
            // hundred ms of Cancel. A long window (the old 1500ms) also swallowed
            // the user's *immediate* re-open of the same app, so Cancel then let
            // them back in with no cooldown if they were quick. 800ms is enough
            // to catch the stale event but lets a genuine quick re-open re-trigger.
            cancelClearHandler.postDelayed({ justCancelledPackage = null }, 800L)
        }
    }

    /** Coroutine scope for async work (survives individual event cancellations). */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The overlay manager that shows the cooldown screen.
     * Uses TYPE_ACCESSIBILITY_OVERLAY to draw above all apps —
     * this works reliably on every OEM ROM (MIUI, ColorOS, etc.)
     * without needing the SYSTEM_ALERT_WINDOW permission.
     */
    private val overlayManager = OverlayManager()

    /** Launcher packages resolved dynamically (covers all OEM launchers). */
    private var homePackages: Set<String> = emptySet()

    /** Packages awaiting a delayed foreground re-check (Gap #1 fix). */
    private val pendingConfirm = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Grace period for the Gap #1 re-check: long enough for UsageStats to settle. */
    private val RETRY_FOREGROUND_MS = 600L

    /**
     * Scheduled re-remind timers (in-app periodic nudge), keyed by package.
     *
     * A self-rescheduling loop started when the user enters the app; it pops
     * the cooldown again every N minutes of wall-clock time while the user is
     * still in the app, and stops only when the session is re-armed.
     */
    private val reRemindJobs = mutableMapOf<String, Job>()

    /**
     * Per-package signal completed when the user taps Continue (or Cancel) on a
     * re-remind pop. The re-remind loop awaits this before starting the next
     * interval, so the wait is measured from "user continued" rather than from
     * "overlay appeared".
     */
    private val reRemindContinue = mutableMapOf<String, CompletableDeferred<Unit>>()

    /**
     * Away cooldown timers (3-min "leave window"), keyed by package.
     *
     * When the user leaves a bypassed app, we start this timer instead of
     * clearing the bypass immediately. If they return within LEAVE_COOLDOWN_MS,
     * the timer is cancelled and the session resumes seamlessly. If it fires,
     * the session is re-armed (bypass cleared, next open re-cools).
     *
     * This replaces the old "clear bypass on any switch" logic that caused the
     * cooldown to re-pop on every in-app detour (gallery/chooser/player).
     */
    private val leaveTimers = mutableMapOf<String, Job>()

    /** Wall-clock time each session started; used for logging/debug. */
    private val sessionStart = mutableMapOf<String, Long>()

    /** How long a user can be away before the session is re-armed. */
    private val LEAVE_COOLDOWN_MS = 3 * 60 * 1000L

    /**
     * Foreground poller state.
     *
     * The accessibility event stream (TYPE_WINDOW_STATE_CHANGED) is sufficient
     * for most transitions, but it has two gaps:
     *  1. When the user OPENS an app from its icon, the event can fire BEFORE
     *     UsageStatsManager records the app as foreground, so the "confirm real
     *     foreground" guard (step 6.5) sees the launcher and wrongly skips the
     *     interception ("open doesn't restrict, only switch does").
     *  2. Some OEM ROMs drop or delay window events entirely.
     *
     * The poller asks the system "what app is really on top right now?" every
     * ~1.5s via ForegroundChecker. Because it reads the genuine foreground, it
     * never false-positives on media notifications, and it reliably catches the
     * "open" case once usage stats settle. It only runs when usage access is
     * granted (ForegroundChecker returns null otherwise → no-op).
     */
    private var pollJob: Job? = null
    private val POLL_INTERVAL_MS = 1500L
    private var lastPolledPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.d(TAG, "AccessibilityService connected and running")

        // Keep a static reference so the Settings screen can toggle the
        // monitoring notification at runtime (start/stop foreground).
        instance = this

        // Resolve the device's launcher package(s) so isSystemPackage() can
        // skip the home screen correctly on every OEM ROM.
        refreshHomePackages()

        // Start the foreground poller (catches opens the window-event stream
        // misses; see pollJob docs). Harmless if usage access isn't granted.
        startForegroundPoller()

        // Show a persistent notification to indicate the service is actively monitoring.
        // This also acts as a foreground service notification, which helps prevent
        // the system from killing the service in the background.
        // Respect the user's "show notification" preference: if disabled, the
        // service runs as a normal (non-foreground) accessibility service with no
        // persistent notification.
        createNotificationChannel()
        serviceScope.launch {
            val show = (applicationContext as AppauseApp).settingsDataStore.showNotification.first()
            if (show) applyMonitoringNotification(true)
        }
    }

    /**
     * Start or stop the persistent monitoring notification (and the foreground
     * service state that goes with it).
     * @param show true → startForeground with the monitoring notification;
     *             false → stopForeground and remove it.
     */
    private fun applyMonitoringNotification(show: Boolean) {
        if (show) {
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_monitoring))
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    /**
     * Called by the Settings screen when the user flips the notification toggle.
     * Re-reads the preference and applies it immediately (no service restart).
     */
    fun applyNotificationSetting() {
        serviceScope.launch {
            val show = (applicationContext as AppauseApp).settingsDataStore.showNotification.first()
            applyMonitoringNotification(show)
        }
    }

    /**
     * Create the notification channel for the monitoring notification.
     * Required on Android 8.0 (API 26) and above.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW // LOW = no sound, no vibration
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Periodically check the genuine foreground app and re-evaluate
     * interception. Complements the window-event stream (see pollJob docs).
     */
    private fun startForegroundPoller() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                // getForegroundPackage() returns null when usage access isn't
                // granted — in that case the poller is a no-op and the event
                // stream (with its own fallback) remains the sole detector.
                val fg = withContext(Dispatchers.IO) {
                    ForegroundChecker.getForegroundPackage(applicationContext)
                } ?: continue
                // Skip redundant evaluation when the foreground hasn't changed
                // and no pause is on screen.
                if (fg == lastPolledPackage && !pauseShown) continue
                lastPolledPackage = fg
                try {
                    handleForegroundChange(fg)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error in poller handleForegroundChange for $fg", e)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        AppLogger.d(TAG, "Event received: package=$packageName, class=${event.className}")

        // Use a coroutine because we need to suspend for Repository queries.
        serviceScope.launch {
            try {
                handleForegroundChange(packageName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in handleForegroundChange for $packageName", e)
            }
        }
    }

    /**
     * Core interception logic. Called for every foreground app change.
     *
     * Decision flow:
     * 1. Is Appause disabled? → skip
     * 2. Is it Appause itself? → skip
     * 3. Is it a system package (launcher, system UI)? → skip
     * 4. Is it currently bypassed? → skip (user already completed cooldown)
     * 5. Is it the same as last foreground? → skip (Activity switch, not app switch)
     * 6. Does it belong to a configured group? → if yes, INTERCEPT
     * 7. Otherwise → skip (not a target app)
     */
    private suspend fun handleForegroundChange(packageName: String) {
        val app = applicationContext as AppauseApp
        val repository = app.repository

        // 1. Check if Appause is enabled
        val isEnabled = repository.isEnabled.first()
        if (!isEnabled) {
            AppLogger.d(TAG, "SKIP: Appause is disabled")
            return
        }

        // 2. Skip Appause itself
        if (packageName == applicationContext.packageName) {
            AppLogger.d(TAG, "SKIP: Appause itself")
            lastForegroundPackage = packageName
            return
        }

        // 2.5. Suppress the stale event that fires for the app the user just
        //      cancelled out of (see justCancelledPackage docs). Without this,
        //      the overlay re-appears on the home screen right after Cancel.
        //      Only skip while we haven't yet confirmed the app actually left
        //      the foreground (lastForegroundPackage still == justCancelled).
        //      The moment the foreground moves to another app/launcher (step 3
        //      below), justCancelledPackage is cleared, so a genuine quick
        //      re-open re-triggers the cooldown normally.
        if (justCancelledPackage != null && packageName == justCancelledPackage
            && lastForegroundPackage == justCancelledPackage) {
            AppLogger.d(TAG, "SKIP: stale event for just-cancelled app ($packageName)")
            return
        }

        // 2.6. Cheap dedup for the foreground poller (and duplicate events):
        //      if the foreground package hasn't changed and no pause is on
        //      screen, there's nothing new to evaluate.
        if (packageName == lastForegroundPackage && !pauseShown) {
            return
        }

        // 3. Skip common system packages (launcher, settings, recents, etc.)
        if (isSystemPackage(packageName)) {
            AppLogger.d(TAG, "SKIP: system package ($packageName)")
            // The user left the previous app (via Home, Recents, etc.).
            // Start its 3-min away cooldown instead of clearing the bypass
            // immediately. This is the core fix for the in-app transient-switch
            // bug: opening a gallery/chooser/player used to clear the bypass and
            // re-pop the cooldown. Now the session survives short detours.
            maybeStartLeaveTimerFor(lastForegroundPackage, packageName)
            // If the app we just left is the one the user cancelled out of,
            // confirm it has genuinely left the foreground: clear the cancel
            // suppression so an immediate re-open re-triggers the cooldown.
            if (justCancelledPackage != null && lastForegroundPackage == justCancelledPackage) {
                justCancelledPackage = null
            }
            lastForegroundPackage = packageName
            return
        }

        // 3.5. A real (non-system) app came to the foreground.
        //      Clear the cancel suppression — the user has moved on.
        if (justCancelledPackage != null) {
            justCancelledPackage = null
        }

        // 4. RESUME: if this app still has an active session, the user returned
        //    to it (possibly after a short detour). Cancel its away cooldown and
        //    let them continue without re-interception.
        if (InterceptionManager.isBypassed(packageName)) {
            cancelLeaveTimer(packageName)
            AppLogger.d(TAG, "RESUME: $packageName (returned within leave window)")
            lastForegroundPackage = packageName
            return
        }

        // 4.5. Skip if cooldown overlay is currently showing
        if (pauseShown) {
            AppLogger.d(TAG, "SKIP: cooldown overlay is showing ($packageName)")
            // A previously bypassed app is now in the background — start its
            // away cooldown (e.g. user opened app B while the cooldown for app
            // A is on screen). If the user returns within 3 min it resumes.
            maybeStartLeaveTimerFor(lastForegroundPackage, packageName)
            return
        }

        // The foreground changed to a non-bypassed, non-system app.
        // If the previous app WAS bypassed, the user left it → start its
        // 3-min away cooldown (re-arm only if they stay away past the window).
        maybeStartLeaveTimerFor(lastForegroundPackage, packageName)

        // 5. Skip duplicate events (same app, different Activity)
        if (packageName == lastForegroundPackage) {
            AppLogger.d(TAG, "SKIP: duplicate event ($packageName)")
            return
        }

        lastForegroundPackage = packageName

        // 6. Check if this app belongs to any configured group
        val group = repository.findGroupForPackage(packageName)
        if (group == null) {
            AppLogger.d(TAG, "SKIP: not in any group ($packageName)")
            return
        }

        // 6.5. Confirm the app is ACTUALLY in the foreground.
        //      The accessibility event's packageName can be a media app whose
        //      notification is showing in the shade (e.g. Bilibili "now playing"),
        //      not the app the user is really looking at. UsageStatsManager tells
        //      us the genuine top app; if they differ, it's a false positive
        //      (notification) — skip it. If we can't determine (usage access not
        //      granted yet) we fall back to the old behavior and still intercept.
        //
        //      GAP #1: when the user OPENS an app from its icon, the window event
        //      can fire BEFORE UsageStats records the app as foreground, so the
        //      genuine top app still reads as the launcher for a few hundred ms.
        //      That used to wrongly skip the intercept ("open doesn't restrict,
        //      only switch does") and let the slow 1.5s poller catch it late —
        //      popping the cooldown on whatever the user was doing by then (e.g.
        //      tapping a comment). Fix: instead of skipping immediately, re-check
        //      once after a short grace period. If UsageStats now agrees, it was a
        //      genuine open → intercept. If it still disagrees, it's a real
        //      notification → skip.
        val actualForeground = withContext(Dispatchers.IO) {
            ForegroundChecker.getForegroundPackage(applicationContext)
        }
        if (actualForeground != null && actualForeground != packageName) {
            if (pendingConfirm.add(packageName)) {
                withContext(Dispatchers.IO) { delay(RETRY_FOREGROUND_MS) }
                pendingConfirm.remove(packageName)
                val actualRetry = withContext(Dispatchers.IO) {
                    ForegroundChecker.getForegroundPackage(applicationContext)
                }
                if (actualRetry != null && actualRetry != packageName) {
                    AppLogger.d(TAG, "SKIP: $packageName not actually foreground (real=$actualRetry) — likely notification")
                    return
                }
                // UsageStats settled to this package → genuine open, fall through.
            } else {
                // A confirmation retry is already pending for this package.
                AppLogger.d(TAG, "SKIP: confirm pending for $packageName")
                return
            }
        }

        // 7. Intercept! Show the cooldown overlay.
        AppLogger.d(TAG, "INTERCEPT: $packageName → group=${group.name}, cooldown=${group.cooldownSeconds}s")
        showCooldownOverlay(
            packageName,
            group.id,
            group.cooldownSeconds,
            group.reRemindMinutes,
            group.reRemindCooldownSeconds,
            reRemindRepeat = group.reRemindRepeat,
            reRemindEscalate = group.reRemindEscalate
        )
    }

    /**
     * Resolve the device's launcher package(s) via the HOME intent.
     *
     * Replaces a hard-coded OEM launcher list so we correctly skip the home
     * screen on every device (Xiaomi, Huawei, OPPO, vivo, realme, Meizu,
     * Honor, Nothing, etc.) without maintaining a fragile, always-incomplete list.
     */
    private fun refreshHomePackages() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        homePackages = try {
            packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Check if a package is a system UI component we should always ignore.
     *
     * Instead of maintaining a hard-coded list of OEM launchers (which misses
     * devices like realme, Meizu, Honor, Nothing, etc.), we resolve the
     * launcher package(s) dynamically via the HOME intent in refreshHomePackages()
     * and treat any of them as the home screen.
     *
     * We still hard-code a few non-launcher system components that should always
     * be skipped (system UI, settings, permission controller, and the Google
     * search box that owns the recents gesture on Pixel devices).
     */
    private fun isSystemPackage(packageName: String): Boolean {
        // Any app that can handle the HOME intent is a launcher — skip it.
        if (homePackages.contains(packageName)) return true
        // Non-launcher system UI components we always ignore.
        return packageName.startsWith("com.android.systemui") ||
            packageName == "com.android.settings" ||
            packageName == "com.google.android.googlequicksearchbox" ||
            packageName == "com.android.permissioncontroller" ||
            packageName == "com.google.android.permissioncontroller"
    }

    /**
     * Show the cooldown overlay using TYPE_ACCESSIBILITY_OVERLAY.
     *
     * Why an overlay instead of launching an Activity?
     * - On MIUI (Xiaomi) and other OEM ROMs, startActivity() from a Service
     *   is silently blocked — the Activity never appears on screen.
     * - TYPE_ACCESSIBILITY_OVERLAY draws above ALL apps without needing
     *   the SYSTEM_ALERT_WINDOW permission. It works on every OEM ROM.
     * - The overlay captures all touches, so the user can only interact
     *   with the Cancel or Continue buttons.
     */
    private fun showCooldownOverlay(
        packageName: String,
        groupId: Long,
        cooldownSeconds: Int,
        reRemindMinutes: Int = 0,
        reRemindCooldownSeconds: Int = 0,
        isReRemind: Boolean = false,
        reRemindRepeat: Boolean = true,
        reRemindEscalate: Boolean = false
    ) {
        overlayManager.show(this, packageName, groupId, cooldownSeconds, reRemindMinutes, reRemindCooldownSeconds, isReRemind, reRemindRepeat, reRemindEscalate)
    }

    /**
     * Schedule a re-remind timer for the given package.
     *
     * Called by OverlayManager after the user taps "Continue" and the group
     * has reRemindMinutes > 0. When the timer fires:
     * - If the user is still in the target app → clear bypass, show overlay again.
     * - If the user already left → do nothing (bypass was already cleaned up).
     *
     * Only one timer per package is active at a time (previous is cancelled).
     */
    /**
     * Start the self-rescheduling re-remind loop for a package.
     *
     * Called once when the user enters the app (onSessionStart). The loop pops
     * the cooldown every [minutes] of wall-clock time while the user is still
     * in the app. It does not stop on its own while the session is active — it
     * only ends when the session is re-armed (bypass cleared) or the service is
     * destroyed. Re-arming also cancels this job.
     */
    internal fun scheduleReRemind(targetPackage: String, groupId: Long, cooldownSeconds: Int, minutes: Int, reRemindCooldownSeconds: Int, repeat: Boolean = true, escalate: Boolean = false) {
        if (minutes <= 0) return

        // Cancel any existing loop for this package (e.g., from a previous session)
        cancelReRemind(targetPackage)

        AppLogger.d(TAG, "Scheduling re-remind loop for $targetPackage every $minutes min (repeat=$repeat, escalate=$escalate)")

        val job = serviceScope.launch {
            // Counts how many re-reminds have actually popped, so escalation
            // (base × N) and the "fire once" mode know where they are.
            var remindCount = 0
            while (true) {
                delay(minutes * 60 * 1000L)

                // Session was re-armed (left > cooldown window, or cancelled) → stop looping.
                if (!InterceptionManager.isBypassed(targetPackage)) {
                    AppLogger.d(TAG, "Re-remind loop ends: $targetPackage no longer bypassed")
                    break
                }

                if (lastForegroundPackage == targetPackage && !pauseShown) {
                    AppLogger.d(TAG, "Re-remind fired: user still in $targetPackage, showing overlay")
                    // Clear bypass so the overlay shows; the loop keeps running and the
                    // user re-bypasses on Continue (countdown finish → startBypass).
                    InterceptionManager.clearBypass(targetPackage)
                    // Use the dedicated re-remind cooldown if set, otherwise fall back
                    // to the initial cooldown (keeps legacy behaviour for old groups).
                    val base = reRemindCooldownSeconds.takeIf { it > 0 } ?: cooldownSeconds
                    // Escalation: the Nth pop lasts base × N (1st = base×1, 2nd = base×2, …).
                    val rePopSeconds = if (escalate) base * (remindCount + 1) else base
                    showCooldownOverlay(targetPackage, groupId, rePopSeconds, minutes, reRemindCooldownSeconds, isReRemind = true)
                    remindCount++
                    // "Fire once" mode: after the first pop, stop the loop.
                    if (!repeat && remindCount >= 1) {
                        AppLogger.d(TAG, "Re-remind fired once (repeat off), stopping loop")
                        break
                    }
                    // Anchor the next interval to "user tapped Continue" (or Cancel),
                    // not to when this overlay appeared. Otherwise the next pop fires
                    // `minutes` after the overlay showed, ignoring how long the user
                    // lingered after continuing.
                    val signal = CompletableDeferred<Unit>()
                    reRemindContinue[targetPackage] = signal
                    signal.await()
                    // If the user cancelled (bypass cleared) or left, stop the loop.
                    if (!InterceptionManager.isBypassed(targetPackage)) {
                        AppLogger.d(TAG, "Re-remind loop ends: user did not continue")
                        break
                    }
                } else {
                    AppLogger.d(TAG, "Re-remind tick but user away from $targetPackage, will re-check")
                }
            }
            reRemindJobs.remove(targetPackage)
        }

        reRemindJobs[targetPackage] = job
    }

    /**
     * Cancel a pending re-remind timer for the given package.
     * Called when the user leaves the app (bypass cleanup) or the service is destroyed.
     */
    private fun cancelReRemind(packageName: String) {
        reRemindJobs.remove(packageName)?.cancel()
        // Cancel any pending "user continued" signal so the loop's await() ends.
        reRemindContinue.remove(packageName)?.cancel()
    }

    /**
     * Called by OverlayManager when the user taps Continue or Cancel on a
     * re-remind pop. Completes the loop's wait so the next interval begins
     * from this moment.
     */
    internal fun completeReRemindContinue(packageName: String) {
        reRemindContinue.remove(packageName)?.complete(Unit)
    }

    /**
     * Called when the user enters a restricted app (cooldown finished, they
     * tapped Continue). Sets up the session: cancel any pending away cooldown,
     * record start time, mark bypassed, and start the re-remind loop if enabled.
     */
    internal fun onSessionStart(
        targetPackage: String,
        groupId: Long,
        cooldownSeconds: Int,
        reRemindMinutes: Int,
        reRemindCooldownSeconds: Int = 0,
        reRemindRepeat: Boolean = true,
        reRemindEscalate: Boolean = false
    ) {
        cancelLeaveTimer(targetPackage)
        sessionStart[targetPackage] = System.currentTimeMillis()
        InterceptionManager.startBypass(targetPackage)
        AppLogger.d(TAG, "Session start: $targetPackage")
        if (reRemindMinutes > 0) {
            // Re-remind is a Pro feature. Even if a (legacy) free user has a
            // stored reRemindMinutes > 0, only fire it when Pro is unlocked.
            serviceScope.launch {
                val isProUser = runCatching {
                    (applicationContext as AppauseApp).proState.isPro.first()
                }.getOrDefault(false)
                if (isProUser) {
                    scheduleReRemind(targetPackage, groupId, cooldownSeconds, reRemindMinutes, reRemindCooldownSeconds, reRemindRepeat, reRemindEscalate)
                }
            }
        }
    }

    /**
     * Start the 3-min away cooldown for a package the user just left.
     * Fires reArm() if the user hasn't returned within LEAVE_COOLDOWN_MS.
     */
    private fun startLeaveTimer(targetPackage: String) {
        // Idempotent: if a leave timer is already counting down for this
        // package, don't restart it. Without this, the foreground poller (and
        // repeated window events) would keep resetting the 3-min window while
        // the user sits in another app, so re-arm would never fire.
        if (leaveTimers.containsKey(targetPackage)) return
        cancelLeaveTimer(targetPackage)
        AppLogger.d(TAG, "Leave cooldown started for $targetPackage (${LEAVE_COOLDOWN_MS / 1000}s)")
        val job = serviceScope.launch {
            delay(LEAVE_COOLDOWN_MS)
            // Still bypassed means the user never came back → re-arm.
            if (InterceptionManager.isBypassed(targetPackage)) {
                AppLogger.d(TAG, "Leave cooldown fired for $targetPackage → re-arm")
                reArm(targetPackage)
            }
            leaveTimers.remove(targetPackage)
        }
        leaveTimers[targetPackage] = job
    }

    private fun cancelLeaveTimer(targetPackage: String) {
        leaveTimers.remove(targetPackage)?.cancel()
    }

    /**
     * Re-arm a session: the limit is active again. Next time the user opens the
     * app, the full initial cooldown triggers. Clears bypass + timers + state.
     */
    private fun reArm(targetPackage: String) {
        InterceptionManager.clearBypass(targetPackage)
        cancelReRemind(targetPackage)
        cancelLeaveTimer(targetPackage)
        sessionStart.remove(targetPackage)
        AppLogger.d(TAG, "Re-armed: $targetPackage")
    }

    /**
     * If [prev] is a different, currently-bypassed app than [current], start its
     * away cooldown. Centralises the "user left app X" handling so transient
     * in-app detours (gallery/chooser/player) and real exits behave the same.
     */
    private fun maybeStartLeaveTimerFor(prev: String?, current: String) {
        if (prev != null && prev != current && InterceptionManager.isBypassed(prev)) {
            startLeaveTimer(prev)
        }
    }

    override fun onInterrupt() {
        AppLogger.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Drop the static reference so a dead instance can't be toggled.
        if (instance == this) instance = null

        // Stop the foreground poller
        pollJob?.cancel()
        pollJob = null

        // Cancel all pending re-remind timers
        reRemindJobs.values.forEach { it.cancel() }
        reRemindJobs.clear()

        // Cancel all pending away cooldown timers
        leaveTimers.values.forEach { it.cancel() }
        leaveTimers.clear()

        // Cancel the service coroutine scope so any in-flight work is stopped
        // (handleForegroundChange coroutines, etc.) instead of leaking.
        serviceScope.cancel()

        // Clean up the overlay if it's showing when the service is destroyed
        if (overlayManager.isShowing) {
            overlayManager.dismiss()
        }

        AppLogger.d(TAG, "AccessibilityService destroyed")
    }
}
