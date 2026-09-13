package com.appause.android.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemporaryPassPolicyTest {

    private val packageName = "com.example.bilibili"
    private val now = 1_000_000L

    @Test
    fun `supported presets create absolute expiry timestamps`() {
        assertEquals(now + 5 * 60_000L, TemporaryPassPolicy.expiresAt(now, 5))
        assertEquals(now + 15 * 60_000L, TemporaryPassPolicy.expiresAt(now, 15))
        assertEquals(now + 30 * 60_000L, TemporaryPassPolicy.expiresAt(now, 30))
        assertNull(TemporaryPassPolicy.expiresAt(now, 10))
    }

    @Test
    fun `pass is active before expiry and expired at the boundary`() {
        val expiresAt = TemporaryPassPolicy.expiresAt(now, 15)!!
        assertTrue(TemporaryPassPolicy.isActive(expiresAt, expiresAt - 1))
        assertFalse(TemporaryPassPolicy.isActive(expiresAt, expiresAt))
        assertFalse(TemporaryPassPolicy.isActive(expiresAt, expiresAt + 1))
    }

    @Test
    fun `expiry wake rechecks only an expired pass whose target is still foreground`() {
        val expiresAt = now + 15 * 60_000L

        assertFalse(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, expiresAt - 1, TemporaryPassWakePolicy.ForegroundEvidence.TARGET
            )
        )
        assertTrue(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, expiresAt, TemporaryPassWakePolicy.ForegroundEvidence.TARGET
            )
        )
        assertFalse(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, expiresAt, TemporaryPassWakePolicy.ForegroundEvidence.OTHER
            )
        )
        assertFalse(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, expiresAt, TemporaryPassWakePolicy.ForegroundEvidence.UNKNOWN
            )
        )
    }

    @Test
    fun `evidence horizon covers two full longest passes`() {
        // Worst case: user static in the target for a whole max-length pass,
        // then grants another max-length pass — the newest RESUMED at expiry
        // is ~2 × max-duration old and must still be inside the horizon.
        val maxPassMs = TemporaryPassPolicy.supportedMinutes.max() * 60_000L

        assertEquals(2 * maxPassMs, TemporaryPassWakePolicy.EXPIRY_EVIDENCE_HORIZON_MS)
        assertTrue(TemporaryPassWakePolicy.EXPIRY_EVIDENCE_HORIZON_MS >= 30 * 60_000L)
    }

    @Test
    fun `normal lookup stays authoritative when it saw the target`() {
        // Short static foreground: the default window still has evidence.
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.TARGET,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = packageName,
                longLookup = null,
                cacheLastForeground = "com.example.other",
                trustedAnchorAtGrant = null,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
    }

    @Test
    fun `long lookback rescues a target static longer than the normal horizon`() {
        // Core regression: stationary past 10min means the normal lookup is
        // null while the long replay still names the target.
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.TARGET,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = packageName,
                cacheLastForeground = null,
                trustedAnchorAtGrant = null,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.TARGET,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = packageName,
                cacheLastForeground = "com.example.other",
                trustedAnchorAtGrant = null,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
    }

    @Test
    fun `known other package is never overridden by a stale target cache`() {
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.OTHER,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = "com.example.other",
                longLookup = null,
                cacheLastForeground = packageName,
                trustedAnchorAtGrant = null,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.OTHER,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = "com.example.other",
                cacheLastForeground = packageName,
                trustedAnchorAtGrant = null,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
    }

    @Test
    fun `no evidence anywhere stays unknown even when the cache names the target`() {
        // With usage access granted, an empty 60-min window means "we could
        // see the truth and saw nothing" — the cache must not invent a TARGET.
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.UNKNOWN,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = packageName,
                trustedAnchorAtGrant = null,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
        // Without usage access the cache is the only evidence channel.
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.TARGET,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = false,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = packageName,
                trustedAnchorAtGrant = null,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.UNKNOWN,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = false,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = null,
                expiresAt = now + 60_000L,
                trustedAnchorAtGrant = null,
                targetPackage = packageName
            )
        )
    }

    @Test
    fun `valid trusted anchor resolves repeated-pass unknown to target`() {
        // Core fix: the last real foreground event is >60min old (normal and
        // long lookups both null), but THIS pass carries an anchor recorded at
        // its own grant — the target was foreground when the pass began and
        // nothing newer ever appeared. → TARGET, re-evaluate.
        // NOTE: `now` is 1e6 ms (~16.7 min since epoch), so the anchor offset
        // must stay small enough to remain positive within this test clock.
        val grantAt = now - 60_000L
        val expiresAt = now + 60_000L

        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.TARGET,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = null,
                trustedAnchorAtGrant = grantAt,
                expiresAt = expiresAt,
                targetPackage = packageName
            )
        )
        assertTrue(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, expiresAt, TemporaryPassWakePolicy.ForegroundEvidence.TARGET
            )
        )
    }

    @Test
    fun `known other evidence overrides even a valid trusted anchor`() {
        val grantAt = now - 60_000L
        val expiresAt = now + 60_000L

        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.OTHER,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = "com.example.launcher",
                longLookup = null,
                cacheLastForeground = null,
                trustedAnchorAtGrant = grantAt,
                expiresAt = expiresAt,
                targetPackage = packageName
            )
        )
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.OTHER,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = "com.example.launcher",
                cacheLastForeground = null,
                trustedAnchorAtGrant = grantAt,
                expiresAt = expiresAt,
                targetPackage = packageName
            )
        )
        assertFalse(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, now, TemporaryPassWakePolicy.ForegroundEvidence.OTHER
            )
        )
    }

    @Test
    fun `no usage access cache evidence is newer than the anchor and wins`() {
        // Precedence safety: without Usage Access, the event-stream cache is
        // the only LIVE foreground signal. A newer cache entry — launcher or
        // target alike — must outrank the older grant anchor.
        val grantAt = now - 60_000L
        val expiresAt = now + 60_000L

        // P2 (the bug this pins): cache saw the launcher after the grant.
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.OTHER,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = false,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = "com.example.launcher",
                trustedAnchorAtGrant = grantAt,
                expiresAt = expiresAt,
                targetPackage = packageName
            )
        )
        assertFalse(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, expiresAt, TemporaryPassWakePolicy.ForegroundEvidence.OTHER
            )
        )

        // P1: cache confirms the target → TARGET either way.
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.TARGET,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = false,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = packageName,
                trustedAnchorAtGrant = grantAt,
                expiresAt = expiresAt,
                targetPackage = packageName
            )
        )

        // P3: cache saw NOTHING since the grant — the anchor is the only
        // evidence left and describes the same "nothing happened" class, so
        // TARGET stands (a real leave would have produced a cache event).
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.TARGET,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = false,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = null,
                trustedAnchorAtGrant = grantAt,
                expiresAt = expiresAt,
                targetPackage = packageName
            )
        )
    }

    @Test
    fun `known long other beats anchor even with a target cache`() {
        // P4: with Usage Access available, an authoritative long lookup of
        // another app wins over BOTH the anchor and any stale cache.
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.OTHER,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = "com.example.launcher",
                cacheLastForeground = packageName,
                trustedAnchorAtGrant = now - 60_000L,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
    }

    @Test
    fun `stale or future-dated anchor does not qualify`() {
        // An anchor pointing AFTER the record's expiry cannot belong to this
        // pass — treat it as missing → UNKNOWN (fail-safe).
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.UNKNOWN,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = null,
                trustedAnchorAtGrant = now + 120_000L,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
        // Zero/negative anchors are corrupt records, never trusted.
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.UNKNOWN,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = null,
                trustedAnchorAtGrant = 0L,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
    }

    @Test
    fun `legacy pass without an anchor keeps expiry semantics unknown`() {
        // Records persisted before anchors existed parse exactly as before
        // (format unchanged) and simply have no anchor → UNKNOWN → fail safe.
        val legacy = TemporaryPassPolicy.encode(TemporaryPass(packageName, now + 60_000L))!!
        assertEquals(
            mapOf(packageName to now + 60_000L),
            TemporaryPassPolicy.parseAll(setOf(legacy))
        )
        assertEquals(
            TemporaryPassWakePolicy.ForegroundEvidence.UNKNOWN,
            TemporaryPassWakePolicy.classifyExpiryForeground(
                usageAccessGranted = true,
                normalLookup = null,
                longLookup = null,
                cacheLastForeground = null,
                trustedAnchorAtGrant = null,
                expiresAt = now + 60_000L,
                targetPackage = packageName
            )
        )
    }

    @Test
    fun `event replay keeps the newest resume and ignores stale tail pauses`() {
        val target = packageName
        val launcher = "com.example.launcher"

        // Target resumed, then a NEWER launcher resume → launcher is on top.
        assertEquals(
            launcher,
            TemporaryPassWakePolicy.resolveForeground(
                listOf(
                    TemporaryPassWakePolicy.ForegroundEvent(target, resumed = true),
                    TemporaryPassWakePolicy.ForegroundEvent(launcher, resumed = true)
                )
            )
        )
        // A stale tail pause from another app must not blank the target.
        assertEquals(
            target,
            TemporaryPassWakePolicy.resolveForeground(
                listOf(
                    TemporaryPassWakePolicy.ForegroundEvent(target, resumed = true),
                    TemporaryPassWakePolicy.ForegroundEvent(launcher, resumed = false)
                )
            )
        )
        // Target's own pause clears it (screen off / left the app).
        assertNull(
            TemporaryPassWakePolicy.resolveForeground(
                listOf(
                    TemporaryPassWakePolicy.ForegroundEvent(target, resumed = true),
                    TemporaryPassWakePolicy.ForegroundEvent(target, resumed = false)
                )
            )
        )
    }

    @Test
    fun `wake restore waits the remaining time on the original expiry`() {
        // Service reconnects 2 min into a 5 min pass: the restore must arm the
        // wake for the REMAINING 3 min, never a fresh full duration.
        val expiresAt = now + 5 * 60_000L
        val reconnectAt = now + 2 * 60_000L

        assertEquals(3 * 60_000L, TemporaryPassWakePolicy.delayMs(expiresAt, reconnectAt))
    }

    @Test
    fun `wake restore sees already-expired entries and re-evaluates immediately`() {
        // parseAll must NOT filter expired records — a pass that expired while
        // the service was down has to wake right away on reconnect.
        val expiresAt = now - 30_000L
        val encoded = TemporaryPassPolicy.encode(TemporaryPass(packageName, expiresAt))!!

        assertEquals(
            mapOf(packageName to expiresAt),
            TemporaryPassPolicy.parseAll(setOf(encoded))
        )
        assertEquals(0L, TemporaryPassWakePolicy.delayMs(expiresAt, now))
        assertTrue(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, now, TemporaryPassWakePolicy.ForegroundEvidence.TARGET
            )
        )
    }

    @Test
    fun `wake restore of an expired pass whose target is gone does nothing`() {
        val expiresAt = now - 30_000L

        assertFalse(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, now, TemporaryPassWakePolicy.ForegroundEvidence.OTHER
            )
        )
        assertFalse(
            TemporaryPassWakePolicy.shouldReevaluate(
                expiresAt, now, TemporaryPassWakePolicy.ForegroundEvidence.UNKNOWN
            )
        )
    }

    @Test
    fun `repeated wake restores stay one entry per package`() {
        // Every reconnect re-runs the restore on the same persisted set. The
        // plan must be stable and hold exactly ONE entry per package (latest
        // expiry) so the per-package scheduler replaces, never duplicates.
        val earlier = TemporaryPassPolicy.encode(TemporaryPass(packageName, now + 1_000L))!!
        val later = TemporaryPassPolicy.encode(TemporaryPass(packageName, now + 2_000L))!!
        val other = TemporaryPassPolicy.encode(TemporaryPass("com.example.other", now + 3_000L))!!
        val raw = setOf(earlier, later, other)

        val firstRestore = TemporaryPassPolicy.parseAll(raw)
        val secondRestore = TemporaryPassPolicy.parseAll(raw)

        assertEquals(firstRestore, secondRestore)
        assertEquals(mapOf(packageName to now + 2_000L, "com.example.other" to now + 3_000L), secondRestore)
    }

    @Test
    fun `malformed records are ignored and cannot create a pass`() {
        val valid = TemporaryPassPolicy.encode(TemporaryPass(packageName, now + 1_000L))!!
        val parsed = TemporaryPassPolicy.parseAll(
            setOf(valid, "malformed", "$packageName|not-a-number", "|9999", "other|0")
        )
        assertEquals(mapOf(packageName to now + 1_000L), parsed)
        assertFalse(TemporaryPassPolicy.isActive(setOf("malformed"), packageName, now))
    }

    @Test
    fun `latest expiry wins when duplicate records exist`() {
        val earlier = TemporaryPassPolicy.encode(TemporaryPass(packageName, now + 1_000L))!!
        val later = TemporaryPassPolicy.encode(TemporaryPass(packageName, now + 2_000L))!!
        assertEquals(mapOf(packageName to now + 2_000L), TemporaryPassPolicy.parseAll(setOf(earlier, later)))
    }

    @Test
    fun `grant is persisted and can be read at later wall clock times`() = runBlocking {
        val settings = SettingsDataStore(
            ApplicationProvider.getApplicationContext<Context>()
        )
        val persistedPackage = "com.example.temporarypass.persistence"
        val expiresAt = settings.grantTemporaryPass(persistedPackage, 15, now)!!

        assertEquals(expiresAt, settings.temporaryPasses.first()[persistedPackage])
        assertTrue(settings.isTemporaryPassActive(persistedPackage, expiresAt - 1))
        assertFalse(settings.isTemporaryPassActive(persistedPackage, expiresAt))
    }

    @Test
    fun `grant persists a trusted foreground anchor for the same package`() = runBlocking {
        // A: the anchor is recorded atomically with the pass, at grant time,
        // keyed to the SAME package — survives any persistence roundtrip.
        val settings = SettingsDataStore(
            ApplicationProvider.getApplicationContext<Context>()
        )
        val grantedPackage = "com.example.temporarypass.anchor"
        val expiresAt = settings.grantTemporaryPass(grantedPackage, 15, now)!!

        assertEquals(expiresAt, settings.temporaryPasses.first()[grantedPackage])
        assertEquals(now, settings.temporaryPassForegroundAnchor(grantedPackage))
        assertEquals(now, settings.temporaryPassAnchors.first()[grantedPackage])
        // Unrelated packages have no anchor at all (G, structural half).
        assertNull(settings.temporaryPassForegroundAnchor("com.example.unrelated"))
    }

    @Test
    fun `second grant replaces both expiry and anchor`() = runBlocking {
        // B/F: TP1 → TP2 replaces the pair; no stale TP1 anchor survives, and
        // both values still read back from persistence (roundtrip). Other
        // tests share the same DataStore file, so only this package's entry
        // is asserted.
        val settings = SettingsDataStore(
            ApplicationProvider.getApplicationContext<Context>()
        )
        val grantedPackage = "com.example.temporarypass.replace"
        val firstExpiry = settings.grantTemporaryPass(grantedPackage, 15, now)!!
        val secondNow = now + 60_000L
        val secondExpiry = settings.grantTemporaryPass(grantedPackage, 15, secondNow)!!

        assertEquals(secondExpiry, settings.temporaryPasses.first()[grantedPackage])
        assertEquals(secondNow, settings.temporaryPassAnchors.first()[grantedPackage])
        assertEquals(secondNow, settings.temporaryPassForegroundAnchor(grantedPackage))
        assertTrue(firstExpiry != secondExpiry)
    }
}
