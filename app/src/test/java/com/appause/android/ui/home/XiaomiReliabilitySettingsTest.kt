package com.appause.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiReliabilitySettingsTest {
    @Test
    fun `Xiaomi manufacturer match is case insensitive and scoped`() {
        assertTrue(isXiaomiManufacturer("Xiaomi"))
        assertTrue(isXiaomiManufacturer("xiaomi"))
        assertTrue(isXiaomiManufacturer("XIAOMI"))
        assertFalse(isXiaomiManufacturer("Samsung"))
        assertFalse(isXiaomiManufacturer("Google"))
    }

    @Test
    fun `autostart component and app details fallback are stable`() {
        assertEquals("com.miui.securitycenter", XIAOMI_AUTOSTART_PACKAGE)
        assertEquals(
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
            XIAOMI_AUTOSTART_CLASS
        )
        assertEquals("package:com.appause.android", autostartApplicationDetailsUri("com.appause.android"))
    }
}
