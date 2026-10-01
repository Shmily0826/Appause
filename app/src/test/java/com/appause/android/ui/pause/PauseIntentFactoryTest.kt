package com.appause.android.ui.pause

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.appause.android.service.PauseAlarmReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The OverlayManager launches PauseActivity through THREE paths (direct,
 * AlarmManager broadcast, Handler re-launch). The factory is what guarantees
 * every path carries the SAME extras — historically the Handler path dropped
 * is_re_remind, which stalled the re-remind loop. These tests pin that no
 * path can drift again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PauseIntentFactoryTest {

    private val spec = PauseSpec(
        targetPackage = "com.example.target",
        groupId = 7L,
        cooldownSeconds = 30,
        reRemindMinutes = 15,
        reRemindCooldownSeconds = 120,
        reRemindRepeat = false,
        reRemindEscalate = true,
        isReRemind = true
    )

    @Test
    fun `activity and alarm intents carry identical extras`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activity = PauseIntentFactory.activityIntent(context, spec)
        val alarm = PauseIntentFactory.alarmIntent(context, spec)

        for (intent in listOf(activity, alarm)) {
            assertEquals(spec.targetPackage, intent.getStringExtra(PauseIntentFactory.EXTRA_TARGET_PACKAGE))
            assertEquals(spec.groupId, intent.getLongExtra(PauseIntentFactory.EXTRA_GROUP_ID, -1L))
            assertEquals(spec.cooldownSeconds, intent.getIntExtra(PauseIntentFactory.EXTRA_COOLDOWN_SECONDS, -1))
            assertEquals(spec.reRemindMinutes, intent.getIntExtra(PauseIntentFactory.EXTRA_RE_REMIND_MINUTES, -1))
            assertEquals(
                spec.reRemindCooldownSeconds,
                intent.getIntExtra(PauseIntentFactory.EXTRA_RE_REMIND_COOLDOWN_SECONDS, -1)
            )
            assertEquals(
                spec.reRemindRepeat,
                intent.getBooleanExtra(PauseIntentFactory.EXTRA_RE_REMIND_REPEAT, !spec.reRemindRepeat)
            )
            assertEquals(
                spec.reRemindEscalate,
                intent.getBooleanExtra(PauseIntentFactory.EXTRA_RE_REMIND_ESCALATE, !spec.reRemindEscalate)
            )
            // The extra whose loss stalled the re-remind loop.
            assertTrue(intent.getBooleanExtra(PauseIntentFactory.EXTRA_IS_RE_REMIND, false))
        }
    }

    @Test
    fun `alarm intent targets the PauseAlarmReceiver`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarm = PauseIntentFactory.alarmIntent(context, spec)
        assertEquals(PauseAlarmReceiver::class.java.name, alarm.component?.className)
    }

    @Test
    fun `activity intent targets PauseActivity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activity = PauseIntentFactory.activityIntent(context, spec)
        assertEquals(PauseActivity::class.java.name, activity.component?.className)
    }
}
