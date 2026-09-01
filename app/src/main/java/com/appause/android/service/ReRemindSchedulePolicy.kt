package com.appause.android.service

/**
 * The result of the Pro gate used before starting a re-remind loop.
 * UNKNOWN is kept separate so a failed entitlement lookup fails closed.
 */
internal enum class ReRemindProStatus {
    UNLOCKED,
    LOCKED,
    UNKNOWN
}

internal data class ReRemindScheduleRequest(
    val targetPackage: String,
    val groupId: Long,
    val cooldownSeconds: Int,
    val minutes: Int,
    val reRemindCooldownSeconds: Int,
    val repeat: Boolean,
    val escalate: Boolean
)

internal object ReRemindSchedulePolicy {

    /**
     * Return the exact scheduler request only when the stored interval is
     * positive and Pro is confirmed unlocked. All other states fail closed.
     */
    fun request(
        targetPackage: String,
        groupId: Long,
        cooldownSeconds: Int,
        minutes: Int,
        reRemindCooldownSeconds: Int,
        repeat: Boolean,
        escalate: Boolean,
        proStatus: ReRemindProStatus
    ): ReRemindScheduleRequest? {
        if (minutes <= 0 || proStatus != ReRemindProStatus.UNLOCKED) return null

        return ReRemindScheduleRequest(
            targetPackage = targetPackage,
            groupId = groupId,
            cooldownSeconds = cooldownSeconds,
            minutes = minutes,
            reRemindCooldownSeconds = reRemindCooldownSeconds,
            repeat = repeat,
            escalate = escalate
        )
    }
}
