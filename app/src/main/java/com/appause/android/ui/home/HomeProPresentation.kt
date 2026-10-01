package com.appause.android.ui.home

import com.appause.android.data.pro.ProAccessStatus
import com.appause.android.data.pro.ProEntitlement
import com.appause.android.R

internal enum class HomeProKind { INACTIVE, TRIAL, LIFETIME }
internal enum class HomeProTimeUnit { DAYS, HOURS }

internal data class HomeProPresentation(
    val kind: HomeProKind,
    val title: Int,
    val remainingCount: Int? = null,
    val remainingUnit: HomeProTimeUnit? = null
)

internal fun homeProPresentation(entitlement: ProEntitlement, nowMillis: Long): HomeProPresentation {
    val status = entitlement.status
    val isTrial = status == ProAccessStatus.TRIAL_ACTIVE ||
        status == ProAccessStatus.EXPIRING_ACTIVE || status == ProAccessStatus.DEBUG
    val expiry = entitlement.expiresAt
    if (isTrial && expiry != null) {
        val remaining = (expiry - nowMillis).coerceAtLeast(0L)
        if (remaining > 0L) {
            val days = 24L * 60 * 60 * 1000
            val hours = 60L * 60 * 1000
            return if (remaining >= days) {
                HomeProPresentation(HomeProKind.TRIAL, R.string.home_pro_trial, maxOf(1, (remaining / days).toInt()), HomeProTimeUnit.DAYS)
            } else {
                HomeProPresentation(HomeProKind.TRIAL, R.string.home_pro_trial, maxOf(1, (remaining / hours).toInt()), HomeProTimeUnit.HOURS)
            }
        }
    }
    return when (status) {
        ProAccessStatus.LIFETIME ->
            HomeProPresentation(HomeProKind.LIFETIME, R.string.home_pro_lifetime)
        else -> HomeProPresentation(HomeProKind.INACTIVE, R.string.home_pro_title)
    }
}
