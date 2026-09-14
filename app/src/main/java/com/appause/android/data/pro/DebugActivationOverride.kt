package com.appause.android.data.pro

/**
 * A debug-build-only activation override.
 *
 * It sits between the REAL entitlement (a locally verified license token) and
 * the effective entitlement every consumer reads:
 *
 *   real entitlement  →  DebugActivationOverride  →  effective entitlement
 *
 * This lets a developer exercise Pro / activation / gate UI without a real
 * activation code or a payment. Release builds can only ever produce [None]
 * (see the release variant of `DebugActivationStore`), so production behaviour
 * is untouched and the override path is unreachable there.
 */
internal sealed interface DebugActivationOverride {

    /** No override: the real entitlement decides, exactly as in production. */
    data object None : DebugActivationOverride

    /** Simulates a device that was activated until [expiresAtMillis]. */
    data class Active(val expiresAtMillis: Long) : DebugActivationOverride

    /** Simulates a device with no activation at all. */
    data object Inactive : DebugActivationOverride
}

/**
 * Pure mapping from (override, real entitlement) to the effective entitlement.
 *
 * Kept side-effect free and clock-injected so every state transition can be
 * pinned by a plain unit test — no device, no SharedPreferences, no waiting
 * seven real days for the expiry case.
 */
internal object DebugActivationPolicy {

    /** How long `Activate for 7 days` grants, measured from the tap. */
    const val ACTIVATION_DAYS = 7

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    /** Expiry stamped when the developer activates the override. */
    fun activationExpiry(nowMillis: Long, days: Int = ACTIVATION_DAYS): Long =
        nowMillis + days * MILLIS_PER_DAY

    /**
     * A set override always outranks the real entitlement:
     *
     * - [DebugActivationOverride.Active] before its expiry → Pro, reported as
     *   [ProAccessStatus.DEBUG] and carrying the debug expiry so the UI can
     *   show it.
     * - [DebugActivationOverride.Active] after its expiry → not Pro. This
     *   mirrors how a real time-limited license behaves once it lapses, which
     *   is exactly the state a developer wants to be able to reach.
     * - [DebugActivationOverride.Inactive] → not Pro even when a real license
     *   is present, so the locked experience can be tested on an activated
     *   device.
     * - [DebugActivationOverride.None] → the real entitlement, untouched.
     */
    fun resolve(
        override: DebugActivationOverride,
        real: ProEntitlement,
        nowMillis: Long
    ): ProEntitlement = when (override) {
        is DebugActivationOverride.None -> real

        is DebugActivationOverride.Inactive -> ProEntitlement(ProAccessStatus.FREE)

        is DebugActivationOverride.Active ->
            if (nowMillis <= override.expiresAtMillis) {
                ProEntitlement(ProAccessStatus.DEBUG, override.expiresAtMillis)
            } else {
                ProEntitlement(ProAccessStatus.FREE)
            }
    }
}
