package com.appause.android.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.appause.android.util.AppLogger
import com.appause.android.util.PersistentLog
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.appause.android.AppauseApp
import com.appause.android.R
import com.appause.android.interception.BurstTracker
import com.appause.android.interception.InterceptionDecider
import com.appause.android.interception.InterceptionManager
import com.appause.android.interception.PostGroupDecision
import com.appause.android.interception.PreGroupDecision
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
         * How long the guard may stay raised while nothing is actually on
         * screen. Covers the fallback path, where PauseActivity is launched
         * asynchronously (direct startActivity, then an AlarmManager retry
         * ~250ms later) and may need a moment to become visible.
         */
        private const val PAUSE_GUARD_GRACE_MS = 1_500L
        /**
         * Hard cap: never let the guard stick longer than this, even if a window
         * is "attached but not actually visible" (e.g. an anti-tamper app like
         * 小红书 hid our TYPE_APPLICATION_OVERLAY via setHideOverlayWindows).
         * Without this, a hidden-but-attached overlay would keep pauseShown=true
         * forever and silently swallow every later open. A genuinely visible
         * overlay returns true at the `overlayAttached` check long before this
         * cap, so the cap only ever releases a stuck/dead guard.
         */
        private const val PAUSE_GUARD_MAX_MS = 30_000L

        /**
         * Raw backing field for [pauseShown]. Do NOT read this directly —
         * always go through [pauseShown], whose getter runs the staleness
         * watchdog described below.
         *
         * @Volatile so the broadcast-thread PauseAlarmReceiver sees the latest
         * value when it reads this flag off the main thread.
         */
        @Volatile
        private var pauseGuardRaised: Boolean = false

        /** elapsedRealtime() when [pauseGuardRaised] was last set to true. */
        @Volatile
        private var pauseGuardRaisedAt: Long = 0L

        /**
         * True while PauseActivity is actually on screen (onStart..onStop).
         *
         * Deliberately tied to the *visible* window rather than to
         * onCreate/onDestroy: on HyperOS an anti-tamper app (e.g. 小红书) can
         * re-front itself and bury PauseActivity without destroying it. A
         * created-but-covered Activity is not a pause screen the user can see,
         * so it must not keep the guard raised.
         */
        @Volatile
        var pauseActivityVisible: Boolean = false

        /**
         * The package currently being intercepted by a cooldown screen.
         *
         * Lets handleForegroundChange detect "the user swiped away from the
         * cooldown without continuing" and release the guard. On HyperOS a
         * TYPE_ACCESSIBILITY_OVERLAY that the user navigated away from can be
         * hidden but NOT removed, so overlayAttached stays true and the
         * watchdog (which only releases a guard with NO window attached) can't
         * free it — pauseShown sticks true forever and every later open reads
         * "SKIP: cooldown overlay is showing" (the "completely stops popping
         * up" symptom). Tracking the target lets us dismiss the overlay the
         * moment a different real app becomes foreground.
         */
        @Volatile
        var pauseTargetPackage: String? = null

        /**
         * Guard flag: true while the cooldown screen is showing.
         * Prevents re-triggering interception while a cooldown is in progress.
         *
         * WATCHDOG (this was a real bug): the flag used to be cleared in
         * exactly two places — OverlayManager.dismiss() and
         * PauseActivity.onDestroy(). When the overlay failed to attach and the
         * fallback Activity was killed or buried before either ran, the flag
         * stayed true forever, every later interception was silently skipped
         * ("SKIP: cooldown overlay is showing"), and the app looked like it had
         * simply stopped working.
         *
         * The getter therefore checks the flag against reality: if neither the
         * overlay view nor a visible PauseActivity exists, and the grace period
         * for the fallback launch has elapsed, the guard is released.
         */
        var pauseShown: Boolean
            get() {
                if (!pauseGuardRaised) return false
                val elapsed = SystemClock.elapsedRealtime() - pauseGuardRaisedAt
                // Hard cap: release even an "attached but not visible" guard
                // (anti-tamper app hid our overlay) so it can't block interception
                // forever.
                if (elapsed > PAUSE_GUARD_MAX_MS) {
                    pauseGuardRaised = false
                    lastDecision = "WATCHDOG: released stale guard (exceeded max hold)"
                    AppLogger.w(TAG, "Pause guard watchdog: exceeded max hold — releasing guard")
                    return false
                }
                // Something is genuinely on screen → the guard is legitimate.
                if (OverlayManager.overlayAttached || pauseActivityVisible) return true
                // Nothing is on screen. Give the fallback Activity a moment to
                // come up before declaring the guard stale.
                if (elapsed < PAUSE_GUARD_GRACE_MS) return true
                pauseGuardRaised = false
                lastDecision = "WATCHDOG: released a stale cooldown guard (nothing on screen)"
                AppLogger.w(TAG, "Pause guard watchdog: no overlay and no visible PauseActivity — releasing guard")
                return false
            }
            set(value) {
                pauseGuardRaised = value
                if (value) pauseGuardRaisedAt = SystemClock.elapsedRealtime()
            }

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
         * Package seen in the PREVIOUS handleForegroundChange() call (event
         * stream OR poller). Used by the dedup guards (steps 2.6 / 5) to tell a
         * genuine re-open ("left app → came back", previous package differs)
         * apart from an in-app Activity switch ("feed → note", previous package
         * is the same app). Unlike [lastForegroundPackage] — which is only
         * updated inside certain decision branches and can go stale when an OEM
         * ROM swallows the "app left" window event — this is updated on EVERY
         * call, so it never lies about what we saw most recently.
         */
        @Volatile
        var lastEventForeground: String? = null

        /**
         * Static handle to the running service instance. Lets the Settings
         * screen toggle the monitoring notification (start/stop foreground)
         * at runtime without restarting the service.
         */
        @Volatile
        var instance: AppauseAccessibilityService? = null

        // ── Diagnostics counters ──
        // Plain in-memory fields read by the Diagnostics screen (debug builds).
        // They exist so a tester without adb can see whether the service is
        // actually alive and receiving foreground events — the single most
        // common cause of "I granted the permission but nothing is intercepted"
        // is that the ROM killed the service while the system toggle stays ON.

        /** When onServiceConnected last ran (0 = never in this process). */
        @Volatile
        var connectedAt: Long = 0L

        /** How many TYPE_WINDOW_STATE_CHANGED events we have received. */
        @Volatile
        var eventCount: Long = 0L

        /** Package of the most recent accessibility event. */
        @Volatile
        var lastEventPackage: String? = null

        /** Timestamp of the most recent accessibility event. */
        @Volatile
        var lastEventAt: Long = 0L

        /** Outcome of the last interception decision, e.g. "SKIP: not in any group". */
        @Volatile
        var lastDecision: String? = null

        /**
         * Last interception decision for a *controlled* (grouped) app. Unlike
         * [lastDecision], this is NOT overwritten by launcher/Recents/system
         * events, so the Diagnostics report always shows what happened to the
         * app the user actually cares about — even after they navigated away
         * (e.g. opened xhs, then jumped back to Appause to read the report).
         */
        @Volatile
        var lastTargetDecision: String? = null

        /**
         * How the last cooldown screen was shown — for the Diagnostics screen.
         *   "overlay_ok"            → TYPE_ACCESSIBILITY_OVERLAY added successfully
         *   "fallback_pauseactivity"→ overlay addView failed, fell back to PauseActivity
         *   null                    → nothing intercepted yet this session
         */
        @Volatile
        var lastOverlayResult: String? = null

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
     * UsageStats can lag behind a launcher window-state event on HyperOS. Keep
     * a short, event-based confirmation window so a real Home/Recents
     * transition can release an overlay even when the first UsageStats read
     * still names the app that was just left.
     */
    private val homeTransitionHandler = Handler(Looper.getMainLooper())
    private var pendingHomeTransition: Runnable? = null
    @Volatile
    private var lastObservedNavigationPackage: String? = null

    private val HOME_TRANSITION_SETTLE_MS = 350L

    /**
     * The overlay manager that shows the cooldown screen.
     * Uses TYPE_ACCESSIBILITY_OVERLAY to draw above all apps —
     * this works reliably on every OEM ROM (MIUI, ColorOS, etc.)
     * without needing the SYSTEM_ALERT_WINDOW permission.
     */
    private val overlayManager = OverlayManager()

    /** Launcher packages resolved dynamically (covers all OEM launchers). */
    private var homePackages: Set<String> = emptySet()

    /**
     * Burst fingerprint for Recents-replay detection (OEM "phantom pause
     * screen" guard). Pure logic with an injected clock — see BurstTracker
     * for why the fingerprint counts only REAL (non-system, non-launcher)
     * packages and why the threshold is 3.
     */
    private val burstTracker = BurstTracker()

    /**
     * Cache of "is this package part of the system image?" lookups.
     *
     * PackageManager.getApplicationInfo is far too slow to call on every
     * accessibility event, and the answer never changes while we are running.
     */
    private val systemImageCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

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
    /** Signalled when the user taps Continue on the INITIAL cooldown, so the
     *  first re-remind pop is anchored to that tap (not to session start). */
    private val initialContinueSignal = mutableMapOf<String, CompletableDeferred<Unit>>()

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

    /**
     * Guards against starting a session/loop twice for the same package. The
     * initial cooldown's Countdown-finish AND its Continue tap both want to
     * begin the session, but only the first must win — otherwise the loop is
     * cancelled and re-scheduled (resetting the clock to the later event).
     */
    private val sessionState = SessionState()

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

    /**
     * Log an interception decision AND remember it, so the Diagnostics screen
     * can show why the last app was (or was not) intercepted without the tester
     * needing adb.
     */
    private fun decide(message: String) {
        lastDecision = message
        AppLogger.d(TAG, message)
    }

    /**
     * Like [decide], but also records the decision against the controlled app
     * so it survives later launcher/Recents events that would otherwise scroll
     * [lastDecision] off the Diagnostics screen.
     */
    private fun decideTarget(packageName: String, message: String) {
        lastDecision = message
        lastTargetDecision = message
        AppLogger.d(TAG, message)
    }

    /**
     * Is this package background noise for burst detection?
     *
     * Enumerating OEM component names one by one turned out to be whack-a-mole:
     * HyperOS fires a DIFFERENT mix of helper packages on each launch
     * (quicksearchbox, *.systemui.plugin, personalassistant, ...), and every one
     * we failed to list made the burst detector mistake a GENUINE launch for a
     * phantom replay and skip the pause screen. That is exactly the
     * "it doesn't intercept any more" regression in v0.5.11 - v0.5.13.
     *
     * So instead of matching names, ask the system: anything shipped in the
     * system image (FLAG_SYSTEM) is a preinstalled component and counts as
     * noise. A genuine launch then contributes exactly ONE real package — the
     * app the user tapped — no matter what OEM helpers tag along.
     *
     * Deliberately NOT used by the main event filter (step 3): the user must
     * still be able to block preinstalled apps such as the OEM browser. And
     * because suppression is evaluated relative to the candidate (see
     * BurstTracker.isSuppressed), a preinstalled app can never suppress its
     * own interception either.
     */
    private fun isBurstNoisePackage(packageName: String): Boolean {
        // Launcher + the few components we know for certain.
        if (isSystemPackage(packageName)) return true
        return systemImageCache.getOrPut(packageName) {
            try {
                val flags = packageManager.getApplicationInfo(packageName, 0).flags
                (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: Exception) {
                // Unknown package — treat as a real app so we stay conservative
                // about phantom bursts rather than silently ignoring one.
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        PersistentLog.log(this, "Svc", "AccessibilityService.onCreate pid=${android.os.Process.myPid()}")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        PersistentLog.log(this, "Svc", "onServiceConnected ENTER")
        AppLogger.d(TAG, "AccessibilityService connected and running")

        try {
            // Keep a static reference so the Settings screen can toggle the
            // monitoring notification at runtime (start/stop foreground).
            instance = this
            connectedAt = System.currentTimeMillis()
            eventCount = 0L

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
                try {
                    val show = (applicationContext as AppauseApp).settingsDataStore.showNotification.first()
                    PersistentLog.log(this@AppauseAccessibilityService, "Svc", "showNotification preference=$show")
                    if (show) applyMonitoringNotification(true)
                } catch (e: Exception) {
                    PersistentLog.log(this@AppauseAccessibilityService, "Svc", "notification preference/apply error: ${e.message}")
                }
            }
            PersistentLog.log(this, "Svc", "onServiceConnected SETUP OK")
        } catch (e: Exception) {
            PersistentLog.log(this, "Svc", "onServiceConnected ERROR: ${e.javaClass.simpleName}: ${e.message}")
            AppLogger.e(TAG, "onServiceConnected error", e)
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
            try {
                val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.notification_monitoring))
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                startForeground(NOTIFICATION_ID, notification)
                PersistentLog.log(this, "Svc", "startForeground OK")
            } catch (e: Exception) {
                PersistentLog.log(this, "Svc", "startForeground FAILED: ${e.javaClass.simpleName}: ${e.message}")
                AppLogger.e(TAG, "startForeground failed", e)
            }
        } else {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                PersistentLog.log(this, "Svc", "stopForeground FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
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
        val repository = (applicationContext as AppauseApp).repository
        pollJob = serviceScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                // getForegroundPackage() returns null when usage access isn't
                // granted — in that case the poller is a no-op and the event
                // stream (with its own fallback) remains the sole detector.
                val fg = withContext(Dispatchers.IO) {
                    ForegroundChecker.getForegroundPackage(applicationContext)
                } ?: continue
                // v0.5.25: getForegroundPackage() is UNRELIABLE for grouped
                // (target) apps on HyperOS / Android 16 — it can report a
                // backgrounded target app as foreground, or report Appause
                // instead of the real app. The window-event stream is the
                // trusted source for these (v0.5.24 "trust the event"), so the
                // poller must NOT re-assert a grouped app as foreground. Doing
                // so silently poisons lastForegroundPackage / lastEventForeground
                // and swallows the next genuine re-open ("first open isn't
                // intercepted"). The poller still resets state promptly when a
                // NON-grouped app (launcher / system / other) is genuinely on top.
                val isGrouped = repository.findGroupForPackage(fg) != null
                if (isGrouped) {
                    lastPolledPackage = fg
                    continue
                }
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


        // Diagnostics: proves the service is genuinely receiving events.
        eventCount++
        lastEventPackage = packageName
        lastEventAt = System.currentTimeMillis()

        // A launcher event is the system's navigation signal. Do not dismiss
        // immediately: Recents can emit launcher/system windows while its
        // animation is settling. Wait briefly, and only proceed if no real app
        // event replaces the launcher as the observed destination.
        if (homePackages.contains(packageName)) {
            lastObservedNavigationPackage = packageName
            scheduleHomeTransitionConfirmation(packageName)
        } else if (packageName != applicationContext.packageName && !isSystemPackage(packageName)) {
            // A real app event means the pending launcher transition was not
            // the final destination. Its normal foreground handling below is
            // still responsible for dismissing an active cooldown if needed.
            lastObservedNavigationPackage = packageName
            cancelPendingHomeTransition()
        }

        AppLogger.d(TAG, "Event received: package=$packageName, class=${event.className}")

        // Record the burst fingerprint for Recents-replay detection (step 6.5
        // uses it as one of three signals confirming a genuine open). Must run
        // synchronously here (not inside the coroutine) so events are clustered
        // in arrival order within the burst window.
        burstTracker.record(System.currentTimeMillis(), packageName, applicationContext.packageName) {
            isBurstNoisePackage(it)
        }

        // Use a coroutine because we need to suspend for Repository queries.
        serviceScope.launch {
            try {
                handleForegroundChange(packageName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in handleForegroundChange for $packageName", e)
            }
        }
    }

    private fun scheduleHomeTransitionConfirmation(homePackage: String) {
        cancelPendingHomeTransition()
        val confirmation = Runnable {
            pendingHomeTransition = null
            if (lastObservedNavigationPackage != homePackage || !pauseShown || pauseTargetPackage == null) {
                return@Runnable
            }
            serviceScope.launch {
                try {
                    if (lastObservedNavigationPackage != homePackage ||
                        !pauseShown || pauseTargetPackage == null
                    ) {
                        return@launch
                    }
                    // The launcher remained the last observed user destination
                    // through the settle window. This event-based confirmation
                    // is the fallback when UsageStats is still stale.
                    handleForegroundChange(homePackage, homeEventConfirmed = true)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error confirming Home transition", e)
                }
            }
        }
        pendingHomeTransition = confirmation
        homeTransitionHandler.postDelayed(confirmation, HOME_TRANSITION_SETTLE_MS)
    }

    private fun cancelPendingHomeTransition() {
        pendingHomeTransition?.let(homeTransitionHandler::removeCallbacks)
        pendingHomeTransition = null
    }

    /**
     * Core interception logic. Called for every foreground app change.
     *
     * Since the interception testability refactor, this method is a thin
     * orchestrator: the WHAT (the filter chain, steps 1–6.6) lives in the pure
     * [InterceptionDecider], and this method executes the HOW (the side
     * effects the original inline code performed — timers, bypass changes,
     * overlay calls, diagnostics writes, and the lastForegroundPackage /
     * justCancelledPackage bookkeeping). The decision order, the exact reason
     * strings, and every effect are byte-for-byte the original behavior.
     *
     * Decision flow (unchanged):
     * 1. Is Appause disabled? → skip
     * 2. Is it Appause itself? → skip
     * 3. Is it a confirmed Home transition with a cooldown active? → abandon
     * 4. Is it a system package (launcher, system UI)? → skip
     * 5. Is it currently bypassed? → skip (user already completed cooldown)
     * 6. Is it the same as last foreground? → skip (Activity switch, not app switch)
     * 7. Does it belong to a configured group? → if yes, INTERCEPT
     * 8. Otherwise → skip (not a target app)
     */
    private suspend fun handleForegroundChange(
        packageName: String,
        homeEventConfirmed: Boolean = false
    ) {
        // Capture the package from the PREVIOUS call before overwriting, so the
        // dedup guards below can distinguish a genuine re-open from an in-app
        // Activity switch.
        val prevForeground = lastEventForeground
        lastEventForeground = packageName

        val app = applicationContext as AppauseApp
        val repository = app.repository

        // Capture the pause target once, like the original `val target = ...`
        // read inside the old step 4.5 (a volatile field must not be read twice
        // where the original read it once).
        val currentPauseTarget = pauseTargetPackage

        // Steps 1–5: pure decision (suspends only for the enabled check, as before).
        val decision = InterceptionDecider.decidePreGroup(
            InterceptionDecider.PreGroupInput(
                packageName = packageName,
                previousEventPackage = prevForeground,
                lastForegroundPackage = lastForegroundPackage,
                justCancelledPackage = justCancelledPackage,
                isEnabled = repository.isEnabled.first(),
                isOwnPackage = packageName == applicationContext.packageName,
                isSystemPackage = isSystemPackage(packageName),
                isHomePackage = homePackages.contains(packageName),
                isHomeForegroundConfirmed = homeEventConfirmed ||
                    (homePackages.contains(packageName) &&
                        ForegroundChecker.getForegroundPackage(applicationContext) == packageName),
                isBypassed = InterceptionManager.isBypassed(packageName),
                isSessionActive = sessionState.isForegroundActive(packageName),
                isTemporaryPassActive = repository.isTemporaryPassActive(
                    packageName,
                    System.currentTimeMillis()
                ),
                // Passed as a function so the guard's staleness watchdog runs
                // at exactly the same read points as the original inline code.
                pauseShown = { pauseShown },
                pauseTargetPackage = currentPauseTarget,
                isPauseTargetBypassed = currentPauseTarget != null &&
                    InterceptionManager.isBypassed(currentPauseTarget)
            )
        )

        // 3.5. A real (non-system) app came to the foreground → clear the cancel
        //      suppression (the user has moved on). Only for decisions that
        //      passed the system gate, exactly like the original step 3.5.
        if (decision.passedSystemGate && justCancelledPackage != null) {
            justCancelledPackage = null
        }

        when (decision) {
            is PreGroupDecision.SkipDisabled -> decide(decision.diagnosticsReason)

            is PreGroupDecision.SkipSelf -> {
                decide(decision.diagnosticsReason)
                lastForegroundPackage = packageName
            }

            is PreGroupDecision.SkipStaleCancelled -> decide(decision.diagnosticsReason)

            is PreGroupDecision.SkipDedup ->
                // The original step 2.6 logged WITHOUT writing lastDecision.
                AppLogger.d(TAG, decision.logLine)

            is PreGroupDecision.SkipSystem -> {
                decide(decision.diagnosticsReason)
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
            }

            is PreGroupDecision.Resume -> {
                // The user returned to this app within the leave window — cancel
                // its away cooldown and let them continue without re-interception.
                cancelLeaveTimer(packageName)
                decide(decision.diagnosticsReason)
                lastForegroundPackage = packageName
            }

            is PreGroupDecision.SkipTemporaryPass -> {
                decide(decision.diagnosticsReason)
                // Keep normal leave/re-arm behavior for any previous bypass;
                // the persisted pass itself remains independent.
                maybeStartLeaveTimerFor(lastForegroundPackage, packageName)
                lastForegroundPackage = packageName
            }

            is PreGroupDecision.AbandonCooldown -> {
                // Mid-cooldown switch-away: a DIFFERENT real app just became
                // foreground while the pause screen was up and the user never
                // tapped Continue. On HyperOS the accessibility overlay can be
                // hidden-but-not-removed, leaving overlayAttached=true and the
                // guard stuck forever (the watchdog can't release an attached
                // window). Dismiss it now so the guard is released and the next
                // genuine open re-cools instead of being silently skipped forever.
                AppLogger.w(TAG, decision.warnLine)
                overlayManager.dismiss()
                InterceptionManager.clearBypass(decision.targetPackage)
                noteCancelled(decision.targetPackage)
                pauseTargetPackage = null
                lastForegroundPackage = packageName
            }

            is PreGroupDecision.SkipPauseShown -> {
                decide(decision.diagnosticsReason)
                // A previously bypassed app is now in the background — start its
                // away cooldown (e.g. user opened app B while the cooldown for app
                // A is on screen). If the user returns within 3 min it resumes.
                maybeStartLeaveTimerFor(lastForegroundPackage, packageName)
            }

            is PreGroupDecision.SkipDuplicate -> decide(decision.diagnosticsReason)

            is PreGroupDecision.ProceedToGroupLookup -> {
                // The foreground changed to a non-bypassed, non-system app.
                // If the previous app WAS bypassed, the user left it → start its
                // 3-min away cooldown (re-arm only if they stay away past the window).
                maybeStartLeaveTimerFor(lastForegroundPackage, packageName)
                lastForegroundPackage = packageName

                // 6. Check if this app belongs to any configured group
                //    (this suspension is why the decision runs in two stages —
                //    the guard/bypass state is re-read afterwards, as before).
                val group = repository.findGroupForPackage(packageName)

                // 6.5. v0.5.24: TRUST THE ACCESSIBILITY EVENT — intercept
                //      immediately. The only suppressor is the recents-replay
                //      burst guard (see BurstTracker for the full history).
                val burstSuppressed = burstTracker.isSuppressed(System.currentTimeMillis(), packageName)

                val postDecision = InterceptionDecider.decidePostGroup(
                    InterceptionDecider.PostGroupInput(
                        packageName = packageName,
                        group = group,
                        burstSuppressed = burstSuppressed,
                        burstRealPackages = burstTracker.realPackagesSnapshot(),
                        pauseShown = { pauseShown },
                        isBypassed = InterceptionManager.isBypassed(packageName),
                        isTemporaryPassActive = repository.isTemporaryPassActive(
                            packageName,
                            System.currentTimeMillis()
                        )
                    )
                )

                if (postDecision !is PostGroupDecision.SkipNoGroup) {
                    AppLogger.d(
                        TAG,
                        "6.5 event=$packageName burstSuppress=$burstSuppressed burstReal=${burstTracker.realPackagesSnapshot()}"
                    )
                }

                when (postDecision) {
                    is PostGroupDecision.SkipNoGroup -> decide(postDecision.diagnosticsReason)

                    is PostGroupDecision.SkipTemporaryPass ->
                        decideTarget(packageName, postDecision.diagnosticsReason)

                    is PostGroupDecision.SkipBurstReplay ->
                        decideTarget(packageName, postDecision.diagnosticsReason)

                    is PostGroupDecision.SkipStateChanged ->
                        decideTarget(packageName, postDecision.diagnosticsReason)

                    is PostGroupDecision.Intercept -> {
                        // 7. Intercept! Show the cooldown overlay.
                        decideTarget(packageName, postDecision.diagnosticsReason)
                        // The decider only returns Intercept when the group
                        // lookup succeeded, so this null check never fires.
                        val targetGroup = group ?: return
                        showCooldownOverlay(
                            packageName,
                            targetGroup.id,
                            targetGroup.cooldownSeconds,
                            targetGroup.reRemindMinutes,
                            targetGroup.reRemindCooldownSeconds,
                            reRemindRepeat = targetGroup.reRemindRepeat,
                            reRemindEscalate = targetGroup.reRemindEscalate
                        )
                    }
                }
            }
        }
    }

    // v0.5.15/v0.5.16/v0.5.17/v0.5.18 interception history:
    // - v0.5.11 removed the v0.5.1 usage-event confirmation and gated
    //   interception solely on the package-burst guard (isBurstSuppressed). A
    //   genuine launch and a Recents replay emit the SAME burst of window events
    //   (incl. OTHER real apps), so the count-based guard could not tell them
    //   apart and skipped real launches → "it doesn't intercept any more".
    // - v0.5.15+ restored ForegroundChecker (the system usage-event log via
    //   queryEvents — which HyperOS does NOT spoof for replays).
    // - v0.5.17 confirmed a genuine RESUMED in the recent window, but on
    //   HyperOS a very brief open (<~200 ms) may not leave a RESUMED record, so
    //   "opened then glanced back to Appause" was still skipped (diag 2026-08-11
    //   09:05 showed exactly this: xhs opened at :24.657, back in Appause at
    //   :24.814, wasResumedRecently=false).
    // - v0.5.18 fixes it with THREE independent confirming signals in an OR
    //   (step 6.5): (a) usage log still on top after 600 ms, (b) a genuine
    //   RESUMED in the window, (c) the window-event burst is a genuine-launch
    //   SHAPE (one real app, not a Recents replay). (c) is the one that survives
    //   the quick bounce-back because it reads the accessibility burst, not the
    //   usage log. The package-burst guard is re-enabled but NON-GATING (one
    //   branch of the OR), so it can only catch clear Recents replays and can no
    //   longer single-handedly suppress a real launch.
    // - v0.5.19: the 600 ms UsageStats re-check (RETRY_FOREGROUND_MS) made the
    //   pause screen appear LATE — only after the user switched back to Appause,
    //   never while the app was open. The burst shape is conclusive the instant
    //   the app opens, so we now wait only ~200 ms (BURST_CONFIRM_DELAY_MS, just
    //   long enough for a Recents replay's 2nd real app to appear) and then
    //   intercept on the burst shape. Interception is now instant AND still
    //   catches replays AND still survives a quick switch-back.
    // - v0.5.22 (final): direct comparison with the user's WORKING release APK
    //   (base.apk, v0.5.1, byte-identical code) showed the difference is runtime
    //   permissions, not code: the release has no Usage Access → its
    //   confirmation was skipped → interception fired instantly. The debug build
    //   had Usage Access granted → the confirmation ran against HyperOS's usage
    //   log, which never records xhs's RESUMED → "real=Appause" → skip/late.
    //   So the usage-log confirmation is removed entirely; the ONLY check is the
    //   burst shape after a 60 ms settle (a replay's 2nd real app arrives in
    //   ~3 ms, so 60 ms is ample). Interception is now as fast as the release.
    // - v0.5.23: restored the v0.5.1 usage-log confirmation (immediate check +
    //   600 ms re-check) plus a wasResumedRecently tie-breaker, with full step
    //   6.5 diagnostics. Diagnostics from the user's device then PROVED the
    //   usage-event log is unreliable for the target apps on this HyperOS build:
    //   opening xhs fired event=com.xingin.xhs, yet getForegroundPackage()
    //   returned "com.appause.android.debug" and wasResumedRecently(xhs)=false
    //   → xhs was SKIPped; bilibili only intercepted via wasResumedRecently at
    //   the 600 ms mark, by which time the user was back in Appause → the pause
    //   screen landed over Appause ("intercepts only after returning to
    //   Appause"). The 600 ms delay + unreliable usage log were the cause.
    // - v0.5.24 (current): TRUST THE ACCESSIBILITY EVENT. Intercept
    //   immediately on the event package — it is the genuine foreground in every
    //   real open. The ONLY suppressor left is the recents-replay burst guard
    //   (isBurstSuppressed), with its threshold RAISED from 2 to 3 distinct real
    //   apps (BURST_MIN_DISTINCT) so a genuine open — one target app, or at most
    //   two if switching directly from another grouped app — never trips it.
    //   No usage-log gate, no 600 ms delay. The pause screen now lands WHILE the
    //   target app is on screen.

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
            packageName.endsWith(".systemui.plugin") ||   // e.g. miui.systemui.plugin / com.miui.systemui.plugin
            packageName == "com.android.settings" ||
            packageName == "com.android.quicksearchbox" ||          // AOSP quick-search box (fires on every home-screen change)
            packageName == "com.google.android.googlequicksearchbox" ||
            packageName == "com.miui.personalassistant" ||         // MIUI smart assistant/sidebar — fires on EVERY home interaction, including genuine launches
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

        // Grab the shared initial-continue signal BEFORE cancelReRemind() runs.
        // cancelReRemind() removes it from the map, and if we grabbed it after,
        // await() would hang forever (the Continue tap's completed signal would be
        // gone). Holding the reference here survives the map removal.
        val initialSignal = initialContinueSignal[targetPackage]
            ?: CompletableDeferred<Unit>().also { initialContinueSignal[targetPackage] = it }

        // Replacing a loop must not end the active foreground session; reArm()
        // is the path that deliberately clears that session marker.
        cancelReRemind(targetPackage, endSession = false)

        AppLogger.d(TAG, "Scheduling re-remind loop for $targetPackage every $minutes min (repeat=$repeat, escalate=$escalate)")

        val repository = (applicationContext as AppauseApp).repository
        val job = serviceScope.launch {
            // Counts how many re-reminds have actually popped, so escalation
            // (base × N) and the "fire once" mode know where they are.
            var remindCount = 0
            // Wait for the INITIAL cooldown's Continue so the FIRST re-pop is
            // anchored to it (user: "计时从点继续开始"). The initial cooldown is
            // shown by the intercept path, not this loop, so we await its signal
            // before computing the first interval. Every later cycle is anchored
            // to its own Continue tap below, so continue → next-continue == exactly
            // `minutes` (not minutes + cooldown, which the user reported as "等了
            // 不止 1 分钟").
            initialSignal.await()
            initialContinueSignal.remove(targetPackage)
            var lastContinueAt = System.currentTimeMillis()
            AppLogger.d(TAG, "Re-remind CLOCK START (initial continue) @ $lastContinueAt for $targetPackage")
            while (true) {
                // A temporary pass pauses the existing re-remind clock without
                // changing the saved group rule. Once it expires, the normal
                // schedule is evaluated again at this decision point.
                val temporaryPassExpiresAt = repository.temporaryPassExpiresAt(
                    targetPackage,
                    System.currentTimeMillis()
                )
                if (temporaryPassExpiresAt != null) {
                    val waitMs = (temporaryPassExpiresAt - System.currentTimeMillis())
                        .coerceAtLeast(100L)
                    delay(waitMs)
                    continue
                }
                // Cooldown the NEXT pop will use, so we can subtract it and land the
                // next "Continue" exactly `minutes` after the previous one.
                val base = reRemindCooldownSeconds.takeIf { it > 0 } ?: cooldownSeconds
                val rePopSeconds = if (escalate) base * (remindCount + 1) else base
                // Target appearance: previousContinue + minutes - rePopSeconds, so that
                // (appearance + rePopSeconds) == previousContinue + minutes.
                val targetAppearAt = lastContinueAt + minutes * 60_000L - rePopSeconds * 1000L
                val waitMs = (targetAppearAt - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(waitMs)

                // Session was re-armed (left > cooldown window, or cancelled) → stop looping.
                if (!InterceptionManager.isBypassed(targetPackage)) {
                    AppLogger.d(TAG, "Re-remind loop ends: $targetPackage no longer bypassed")
                    break
                }

                // Decide whether the user is genuinely still in the target app.
                // Prefer the system usage-event log (ForegroundChecker) — it is the
                // authoritative "what is actually on screen" signal and is immune
                // to the Recents/task-switch event bursts. The naive
                // `lastForegroundPackage` only updates on window-state *changes*,
                // so if the user stays put (no new event) it goes stale and the
                // loop would wrongly think they left and never re-pop. Fall back
                // to the naive signal only when usage access hasn't been granted.
                val stillInApp = if (ForegroundChecker.isUsageAccessGranted(applicationContext)) {
                    ForegroundChecker.getForegroundPackage(applicationContext) == targetPackage
                } else {
                    lastForegroundPackage == targetPackage
                }
                if (stillInApp && !pauseShown) {
                    AppLogger.d(TAG, "Re-remind fired: user still in $targetPackage, showing overlay")
                    // Clear bypass so the overlay shows; the loop keeps running and the
                    // user re-bypasses on Continue (countdown finish → startBypass).
                    InterceptionManager.clearBypass(targetPackage)
                    // Use the dedicated re-remind cooldown if set, otherwise fall back
                    // to the initial cooldown (keeps legacy behaviour for old groups).
                    showCooldownOverlay(targetPackage, groupId, rePopSeconds, minutes, reRemindCooldownSeconds, isReRemind = true)
                    remindCount++
                    // "Fire once" mode: after the first pop, stop the loop.
                    if (!repeat && remindCount >= 1) {
                        AppLogger.d(TAG, "Re-remind fired once (repeat off), stopping loop")
                        break
                    }
                    val signal = CompletableDeferred<Unit>()
                    reRemindContinue[targetPackage] = signal
                    signal.await()
                    // Record the Continue time so the next cycle lands exactly `minutes` later.
                    lastContinueAt = System.currentTimeMillis()
                    val nextTarget = lastContinueAt + minutes * 60_000L - rePopSeconds * 1000L
                    AppLogger.d(TAG, "Re-remind CONTINUE @ $lastContinueAt for $targetPackage -> next pop target @ $nextTarget")
                    // If the user cancelled (bypass cleared) or left, stop the loop.
                    if (!InterceptionManager.isBypassed(targetPackage)) {
                        AppLogger.d(TAG, "Re-remind loop ends: user did not continue")
                        break
                    }
                } else {
                    // User away (or a pop is mid-show) — re-check soon instead of waiting
                    // another full `minutes` (which would make the gap ~2× and feel "stuck").
                    AppLogger.d(TAG, "Re-remind tick but user away from $targetPackage, re-checking soon")
                    delay(2000L)
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
    internal fun cancelReRemind(packageName: String, endSession: Boolean = true) {
        reRemindJobs.remove(packageName)?.cancel()
        // Cancel any pending "user continued" signal so the loop's await() ends.
        reRemindContinue.remove(packageName)?.cancel()
        // Also cancel the initial-cooldown wait (e.g. user cancelled the first
        // cooldown before tapping Continue) so the loop doesn't hang forever.
        initialContinueSignal.remove(packageName)?.cancel()
        if (endSession) {
            // Session is over → allow a fresh one to start on next entry.
            sessionState.end(packageName)
        }
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
     * Called by OverlayManager when the user taps Continue on the INITIAL
     * cooldown (isReRemind == false). Unblocks the re-remind loop's first
     * interval so it starts counting from this moment.
     */
    internal fun completeInitialContinue(packageName: String) {
        // Create (if needed) and complete the SAME shared signal the loop awaits,
        // so order between the Continue tap and scheduleReRemind doesn't matter.
        val signal = initialContinueSignal[packageName]
            ?: CompletableDeferred<Unit>().also { initialContinueSignal[packageName] = it }
        signal.complete(Unit)
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
        reRemindEscalate: Boolean = false,
        preserveForegroundSession: Boolean = true
    ) {
        // Only the first trigger (Continue tap OR countdown-finish) starts the
        // session; the other must be ignored so the loop isn't re-scheduled and
        // its clock reset. Tapping Continue must be the one that wins, because
        // the user explicitly asked to proceed — that's the moment the re-remind
        // interval should be anchored to ("计时从点继续开始").
        if (!sessionState.begin(targetPackage, preserveForegroundSession)) {
            AppLogger.d(TAG, "Session start ignored (already active): $targetPackage")
            return
        }
        cancelLeaveTimer(targetPackage)
        sessionStart[targetPackage] = System.currentTimeMillis()
        InterceptionManager.startBypass(targetPackage)
        AppLogger.d(TAG, "Session start: $targetPackage")
        if (reRemindMinutes > 0) {
            // Re-remind is a Pro feature. Even if a (legacy) free user has a
            // stored reRemindMinutes > 0, only fire it when Pro is unlocked.
            serviceScope.launch {
                val proStatus = runCatching {
                    (applicationContext as AppauseApp).proState.isPro.first()
                }.fold(
                    onSuccess = { isPro ->
                        if (isPro) ReRemindProStatus.UNLOCKED else ReRemindProStatus.LOCKED
                    },
                    onFailure = { ReRemindProStatus.UNKNOWN }
                )
                ReRemindSchedulePolicy.request(
                    targetPackage = targetPackage,
                    groupId = groupId,
                    cooldownSeconds = cooldownSeconds,
                    minutes = reRemindMinutes,
                    reRemindCooldownSeconds = reRemindCooldownSeconds,
                    repeat = reRemindRepeat,
                    escalate = reRemindEscalate,
                    proStatus = proStatus
                )?.let { request ->
                    scheduleReRemind(
                        targetPackage = request.targetPackage,
                        groupId = request.groupId,
                        cooldownSeconds = request.cooldownSeconds,
                        minutes = request.minutes,
                        reRemindCooldownSeconds = request.reRemindCooldownSeconds,
                        repeat = request.repeat,
                        escalate = request.escalate
                    )
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
        PersistentLog.log(this, "Svc", "onInterrupt")
        AppLogger.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        PersistentLog.log(this, "Svc", "onDestroy")
        super.onDestroy()

        // Drop the static reference so a dead instance can't be toggled.
        // The Diagnostics screen reads this to tell "enabled in system settings"
        // apart from "actually running" (ROMs often kill the process).
        if (instance == this) {
            instance = null
            connectedAt = 0L
        }

        // Stop the foreground poller
        pollJob?.cancel()
        pollJob = null

        cancelPendingHomeTransition()
        homeTransitionHandler.removeCallbacksAndMessages(null)

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
