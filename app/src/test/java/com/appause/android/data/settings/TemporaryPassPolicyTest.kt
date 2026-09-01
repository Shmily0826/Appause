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
}
