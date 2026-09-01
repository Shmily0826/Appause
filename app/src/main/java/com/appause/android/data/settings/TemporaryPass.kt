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
