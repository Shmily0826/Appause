package com.appause.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.appause.android.ui.pause.PauseActivity

/**
 * PauseAlarmReceiver — launches PauseActivity when triggered by AlarmManager.
 *
 * Why a separate receiver?
 * - On some OEM ROMs (MIUI, etc.), startActivity() from AccessibilityService
 *   is silently deprioritized and the Activity doesn't come to the foreground.
 * - AlarmManager has higher system authority and can reliably bring Activities
 *   to the foreground, even on aggressive OEM ROMs.
 * - We use both direct startActivity() AND AlarmManager as a backup.
 *   Whichever succeeds first brings the cooldown screen to the user.
 */
class PauseAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PauseAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // If PauseActivity was already brought to foreground by the direct startActivity(),
        // skip the alarm backup to avoid recreating the Activity.
        if (!AppauseAccessibilityService.pauseShown) {
            Log.d(TAG, "pauseShown is false — direct launch may have failed, launching via alarm")
        } else {
            Log.d(TAG, "pauseShown is true — direct launch likely succeeded, skipping alarm")
            return
        }

        val launchIntent = Intent(context, PauseActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            // Copy the extras from the alarm intent (target_package, group_id, etc.)
            putExtras(intent)
        }
        context.startActivity(launchIntent)
    }
}
