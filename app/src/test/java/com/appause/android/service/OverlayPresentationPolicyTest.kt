package com.appause.android.service

import android.view.WindowManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPresentationPolicyTest {

    @Test
    fun `overlay input height stops above visible navigation bar`() {
        assertEquals(
            2140,
            OverlayWindowPolicy.heightBeforeNavigationBar(
                displayHeight = 2400,
                statusBarInset = 127,
                navigationBarInset = 133
            )
        )
    }

    @Test
    fun `overlay input height remains positive`() {
        assertEquals(
            1,
            OverlayWindowPolicy.heightBeforeNavigationBar(
                displayHeight = 100,
                statusBarInset = 80,
                navigationBarInset = 40
            )
        )
    }

    @Test
    fun `blocking overlay does not request display layout flags`() {
        val flags = OverlayWindowPolicy.flags()

        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        assertEquals(
            0,
            flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        assertEquals(
            0,
            flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        assertEquals(
            0,
            flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }

    @Test
    fun `primary presentation surface is always the accessibility overlay`() {
        assertEquals(
            OverlayPresentationPolicy.Path.ACCESSIBILITY_OVERLAY,
            OverlayPresentationPolicy.initialPath
        )
    }

    @Test
    fun `ordinary devices retry with the application overlay after 2032 failure`() {
        assertTrue(
            OverlayPresentationPolicy.shouldRetryWith2038AfterFailure(
                isXiaomiApi36OrLater = false
            )
        )
    }

    @Test
    fun `Xiaomi Android 16 skips the 2038 retry after 2032 failure`() {
        assertFalse(
            OverlayPresentationPolicy.shouldRetryWith2038AfterFailure(
                isXiaomiApi36OrLater = true
            )
        )
    }
}
