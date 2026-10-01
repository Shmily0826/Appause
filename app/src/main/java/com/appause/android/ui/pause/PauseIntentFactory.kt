package com.appause.android.ui.pause

import android.content.Context
import android.content.Intent

/**
 * The complete set of parameters a PauseActivity launch carries.
 *
 * Built once per interception by [com.appause.android.service.OverlayManager]
 * and applied identically to every launch path.
 */
data class PauseSpec(
    val targetPackage: String,
    val groupId: Long,
    val cooldownSeconds: Int,
    val reRemindMinutes: Int,
    val reRemindCooldownSeconds: Int,
    val reRemindRepeat: Boolean,
    val reRemindEscalate: Boolean,
    val isReRemind: Boolean
)

/**
 * Single source for the extras carried into [PauseActivity] and for the
 * Intents used by the three fallback launch paths in
 * [com.appause.android.service.OverlayManager]:
 *
 *  1. the direct `startActivity` fallback,
 *  2. the AlarmManager broadcast ([com.appause.android.service.PauseAlarmReceiver]
 *     copies these extras onto its own PauseActivity launch), and
 *  3. the Handler `postDelayed` re-launch used when the alarm is rejected
 *     (Android 12+ denies `setExactAndAllowWhileIdle` without
 *     SCHEDULE_EXACT_ALARM, which this app deliberately does not declare).
 *
 * Why a factory: path 3 was hand-rolled and FORGOT the is_re_remind extra —
 * a re-remind pop that reached it was treated as an initial cooldown, so the
 * service's re-remind loop never received its Continue signal and stalled
 * forever. Building every Intent here makes a missing extra a compile-time
 * impossibility.
 */
object PauseIntentFactory {

    // Extra keys. PauseActivity reads these literals; PauseAlarmReceiver
    // copies them verbatim via putExtras(intent). Keep values stable.
    const val EXTRA_TARGET_PACKAGE = "target_package"
    const val EXTRA_GROUP_ID = "group_id"
    const val EXTRA_COOLDOWN_SECONDS = "cooldown_seconds"
    const val EXTRA_RE_REMIND_MINUTES = "re_remind_minutes"
    const val EXTRA_RE_REMIND_COOLDOWN_SECONDS = "re_remind_cooldown_seconds"
    const val EXTRA_RE_REMIND_REPEAT = "re_remind_repeat"
    const val EXTRA_RE_REMIND_ESCALATE = "re_remind_escalate"
    const val EXTRA_IS_RE_REMIND = "is_re_remind"

    private fun fillExtras(intent: Intent, spec: PauseSpec): Intent = intent.apply {
        putExtra(EXTRA_TARGET_PACKAGE, spec.targetPackage)
        putExtra(EXTRA_GROUP_ID, spec.groupId)
        putExtra(EXTRA_COOLDOWN_SECONDS, spec.cooldownSeconds)
        putExtra(EXTRA_RE_REMIND_MINUTES, spec.reRemindMinutes)
        putExtra(EXTRA_RE_REMIND_COOLDOWN_SECONDS, spec.reRemindCooldownSeconds)
        putExtra(EXTRA_RE_REMIND_REPEAT, spec.reRemindRepeat)
        putExtra(EXTRA_RE_REMIND_ESCALATE, spec.reRemindEscalate)
        putExtra(EXTRA_IS_RE_REMIND, spec.isReRemind)
    }

    /** Launch Intent for PauseActivity itself (paths 1 and 3). */
    fun activityIntent(context: Context, spec: PauseSpec): Intent =
        fillExtras(Intent(context, PauseActivity::class.java), spec)

    /** Broadcast Intent for PauseAlarmReceiver (path 2). */
    fun alarmIntent(context: Context, spec: PauseSpec): Intent =
        fillExtras(
            Intent(context, com.appause.android.service.PauseAlarmReceiver::class.java),
            spec
        )
}
