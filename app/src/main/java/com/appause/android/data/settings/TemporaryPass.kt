package com.appause.android.data.settings

/** A persisted, package-scoped exception that ends at an absolute wall-clock time. */
data class TemporaryPass(
    val packageName: String,
    val expiresAt: Long
)

/** Pure rules for validating and evaluating persisted temporary passes. */
object TemporaryPassPolicy {

    const val FIVE_MINUTES = 5
    const val FIFTEEN_MINUTES = 15
    const val THIRTY_MINUTES = 30

    val supportedMinutes = listOf(FIVE_MINUTES, FIFTEEN_MINUTES, THIRTY_MINUTES)

    private const val SEPARATOR = "|"
    private const val MINUTE_MS = 60_000L

    /** Return an expiry timestamp for a supported preset, or null for invalid input. */
    fun expiresAt(now: Long, minutes: Int): Long? {
        if (minutes !in supportedMinutes) return null
        val durationMs = minutes * MINUTE_MS
        if (now > Long.MAX_VALUE - durationMs) return null
        return now + durationMs
    }

    /** Equality at the boundary is expired; only time strictly before expiry is active. */
    fun isActive(expiresAt: Long, now: Long): Boolean = expiresAt > now

    /** Encode one record for the DataStore string set. */
    fun encode(pass: TemporaryPass): String? {
        if (pass.packageName.isBlank() || pass.packageName.contains(SEPARATOR)) return null
        if (pass.expiresAt <= 0L) return null
        return "${pass.packageName}$SEPARATOR${pass.expiresAt}"
    }

    /** Parse a record defensively; malformed data never becomes an indefinite bypass. */
    fun parse(raw: String): TemporaryPass? {
        val parts = raw.split(SEPARATOR)
        if (parts.size != 2) return null
        val packageName = parts[0]
        val expiresAt = parts[1].toLongOrNull() ?: return null
        if (packageName.isBlank() || packageName.contains(SEPARATOR) || expiresAt <= 0L) return null
        return TemporaryPass(packageName, expiresAt)
    }

    /** Parse all valid records, keeping the latest expiry when duplicates exist. */
    fun parseAll(raw: Set<String>): Map<String, Long> = raw.mapNotNull(::parse)
        .groupBy { it.packageName }
        .mapValues { (_, passes) -> passes.maxOf { it.expiresAt } }

    fun isActive(raw: Set<String>, packageName: String, now: Long): Boolean {
        val expiry = parseAll(raw)[packageName] ?: return false
        return isActive(expiry, now)
    }
}

/** Deterministic decisions for the one-shot expiry wake owned by the service. */
internal object TemporaryPassWakePolicy {

    /**
     * Evidence horizon for the expiry wake's fallback lookup, derived from the
     * product itself: 2 × the longest supported pass. Worst realistic case is
     * a user who sits in the target for a whole pass duration BEFORE granting
     * a max-length pass — at expiry the newest ACTIVITY_RESUMED can then be
     * ~2 × max-duration old, so the default 10-min UsageEvents horizon (see
     * ForegroundChecker) finds nothing and the wake used to silently bail.
     * Growing supportedMinutes grows this horizon automatically.
     */
    val EXPIRY_EVIDENCE_HORIZON_MS: Long =
        2L * (TemporaryPassPolicy.supportedMinutes.maxOrNull() ?: 0) * 60_000L

    /** What the wake can conclude about the target being on screen. */
    enum class ForegroundEvidence { TARGET, OTHER, UNKNOWN }

    /**
     * One sampled activity-lifecycle event, in chronological list order
     * (mirrors the order UsageEvents delivers them). [resumed] maps
     * ACTIVITY_RESUMED; a PAUSED event maps resumed = false.
     */
    data class ForegroundEvent(val packageName: String, val resumed: Boolean)

    /**
     * Pure replay of an ordered usage-event sample: the most recent RESUMED
     * is the top app; a PAUSED clears it only when it names the same package
     * (stale tail-end pauses from other apps must not blank the answer).
     * Same semantics ForegroundChecker applies to its short windows.
     */
    fun resolveForeground(events: List<ForegroundEvent>): String? {
        var currentTop: String? = null
        for (event in events) {
            if (event.resumed) {
                currentTop = event.packageName
            } else if (currentTop == event.packageName) {
                currentTop = null
            }
        }
        return currentTop
    }

    /**
     * Classify the expiry wake's foreground evidence, in strict precedence:
     *  1. normal lookup (60s → 10min window) is authoritative when non-null;
     *  2. long-lookback replay is authoritative when non-null;
     *  3. without usage access, the event-stream cache is the ONLY live
     *     foreground signal and is NEWER than the grant anchor, so a known
     *     cache entry (launcher/other included) wins — it must never be
     *     overridden by the older anchor into a phantom TARGET;
     *  4. the pass's OWN trusted anchor decides when nothing newer exists:
     *     it is recorded at grant time while the interception UI was over the
     *     target, never overrides a known OTHER, and expires with its record
     *     ([trustedAnchorAtGrant] must be ≤ [expiresAt] to count). This also
     *     covers no-usage-access runs where the cache saw nothing since the
     *     grant — the same "nothing happened" evidence class as the anchor;
     *  5. with usage access granted but NO events in either window and NO
     *     anchor the answer is UNKNOWN — the cache is deliberately NOT
     *     consulted there, because a stale cache entry must never override
     *     "we could see the truth and saw nothing" into a phantom intercept.
     */
    fun classifyExpiryForeground(
        usageAccessGranted: Boolean,
        normalLookup: String?,
        longLookup: String?,
        cacheLastForeground: String?,
        trustedAnchorAtGrant: Long?,
        expiresAt: Long,
        targetPackage: String
    ): ForegroundEvidence = when {
        normalLookup != null -> evidenceOf(normalLookup, targetPackage)
        longLookup != null -> evidenceOf(longLookup, targetPackage)
        !usageAccessGranted && cacheLastForeground != null ->
            evidenceOf(cacheLastForeground, targetPackage)
        isTrustedAnchorValid(trustedAnchorAtGrant, expiresAt) -> ForegroundEvidence.TARGET
        else -> ForegroundEvidence.UNKNOWN
    }

    /**
     * The anchor belongs to exactly one pass record: it must exist and point
     * at (or before) that record's expiry. A replaced or legacy record without
     * an anchor — or a corrupted future-dated one — never qualifies.
     */
    private fun isTrustedAnchorValid(anchorAtGrant: Long?, expiresAt: Long): Boolean =
        anchorAtGrant != null && anchorAtGrant in 1..expiresAt

    private fun evidenceOf(packageName: String?, targetPackage: String): ForegroundEvidence =
        when (packageName) {
            targetPackage -> ForegroundEvidence.TARGET
            null -> ForegroundEvidence.UNKNOWN
            else -> ForegroundEvidence.OTHER
        }

    fun delayMs(expiresAt: Long, now: Long): Long = (expiresAt - now).coerceAtLeast(0L)

    fun shouldReevaluate(
        expiresAt: Long,
        now: Long,
        foregroundEvidence: ForegroundEvidence
    ): Boolean = foregroundEvidence == ForegroundEvidence.TARGET && now >= expiresAt
}
