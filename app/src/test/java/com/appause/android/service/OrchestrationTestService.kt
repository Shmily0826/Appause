package com.appause.android.service

/**
 * Test double for the cooldown PRESENTATION only.
 *
 * The real [OverlayManager.show] hosts a Compose UI whose frame loop cannot
 * survive Robolectric's paused looper (the Choreographer re-schedules frames
 * forever, hanging any looper idle). Everything else — decision chain,
 * bypass/session state, leave timers, re-remind loops — runs for real in the
 * subclassed service. The override mirrors the companion-state side effects
 * of a real successful 2032 attach (guard raised, target recorded) so the
 * decision layer observes exactly what it would in production.
 */
open class OrchestrationTestService : AppauseAccessibilityService() {

    data class ShowRecord(
        val targetPackage: String,
        val groupId: Long,
        val cooldownSeconds: Int,
        val reRemindMinutes: Int,
        val reRemindCooldownSeconds: Int,
        val isReRemind: Boolean
    )

    val shownCooldowns = mutableListOf<ShowRecord>()

    override fun showCooldownOverlay(
        packageName: String,
        groupId: Long,
        cooldownSeconds: Int,
        reRemindMinutes: Int,
        reRemindCooldownSeconds: Int,
        isReRemind: Boolean,
        reRemindRepeat: Boolean,
        reRemindEscalate: Boolean
    ) {
        // Mirror OverlayManager.show's early return when a pause is already up.
        if (AppauseAccessibilityService.pauseShown) return
        shownCooldowns += ShowRecord(
            targetPackage = packageName,
            groupId = groupId,
            cooldownSeconds = cooldownSeconds,
            reRemindMinutes = reRemindMinutes,
            reRemindCooldownSeconds = reRemindCooldownSeconds,
            isReRemind = isReRemind
        )
        AppauseAccessibilityService.lastOverlayResult = "overlay_ok"
        AppauseAccessibilityService.pauseShown = true
        AppauseAccessibilityService.pauseTargetPackage = packageName
    }
}
