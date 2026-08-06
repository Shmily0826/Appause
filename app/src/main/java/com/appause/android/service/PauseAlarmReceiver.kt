package com.appause.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appause.android.ui.pause.PauseActivity
import com.appause.android.util.AppLogger
import com.appause.android.util.PersistentLog

/**
 * PauseAlarmReceiver — launches PauseActivity when triggered by AlarmManager.
 *
 * Why a separate receiver?
 * - On some OEM ROMs (MIUI/HyperOS), startActivity() from an AccessibilityService
 *   is silently deprioritized and the Activity never reaches the foreground.
 * - AlarmManager has higher system authority and reliably brings the Activity
 *   to the foreground, even on aggressive OEM ROMs.
 * - The OverlayManager schedules this receiver as a backup whenever the
 *   WindowManager overlay can't be added (e.g. TYPE_ACCESSIBILITY_OVERLAY is
 *   rejected on Android 16 / HyperOS).
 *
 * NOTE: we deliberately ALWAYS launch here. A background startActivity on MIUI is
 * dropped without error, so `pauseShown == true` is NOT a reliable signal that the
 * direct launch succeeded — trusting it would silently skip the only launch that
 * actually works. PauseActivity is `singleInstance` + CLEAR_TOP, so a duplicate
 * launch merely re-fronts the existing instance (no second screen, no reset).
 */
class PauseAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PauseAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        PersistentLog.log(context, "Alarm", "PauseAlarmReceiver fired for ${intent.getStringExtra("target_package")}")
        val launchIntent = Intent(context, PauseActivity::class.java).apply {
            // NEW_TASK: required to start from a BroadcastReceiver (non-Activity ctx).
            // CLEAR_TOP + SINGLE_TOP: if PauseActivity already exists (e.g. the
            // direct-launch fast path succeeded), just re-front it via onNewIntent
            // — do NOT use CLEAR_TASK, which destroys & recreates the Activity and
            // resets the countdown (the "second pause window at 5s" bug).
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            // Copy the extras from the alarm intent (target_package, group_id, etc.)
            putExtras(intent)
        }
        try {
            context.startActivity(launchIntent)
            AppLogger.d(TAG, "PauseActivity launched via AlarmManager for ${intent.getStringExtra("target_package")}")
            PersistentLog.log(context, "Alarm", "startActivity issued for PauseActivity")
        } catch (e: Exception) {
            AppLogger.e(TAG, "AlarmManager launch of PauseActivity failed", e)
            PersistentLog.log(context, "Alarm", "startActivity FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
