package com.appause.android.service

import android.view.WindowManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
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
    fun `non-focusable fallback remains explicitly opt in`() {
        val flags = OverlayWindowPolicy.flags(keepOverlayNonFocusable = true)

        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }

    @Test
    fun `Xiaomi Android 16 starts with accessibility overlay even with overlay permission`() {
        assertEquals(
            OverlayPresentationPolicy.Path.ACCESSIBILITY_OVERLAY,
            OverlayPresentationPolicy.initialPath(
                isXiaomiApi36OrLater = true,
                canDrawOverlays = true
            )
        )
        assertNull(
            OverlayPresentationPolicy.alternatePathAfterFailure(
                isXiaomiApi36OrLater = true,
                attemptedPath = OverlayPresentationPolicy.Path.ACCESSIBILITY_OVERLAY
            )
        )
    }

    @Test
    fun `Xiaomi Android 16 without overlay permission still starts with accessibility overlay`() {
        assertEquals(
            OverlayPresentationPolicy.Path.ACCESSIBILITY_OVERLAY,
            OverlayPresentationPolicy.initialPath(
                isXiaomiApi36OrLater = true,
                canDrawOverlays = false
            )
        )
    }

    @Test
    fun `ordinary devices preserve accessibility overlay then application fallback`() {
        assertEquals(
            OverlayPresentationPolicy.Path.ACCESSIBILITY_OVERLAY,
            OverlayPresentationPolicy.initialPath(
                isXiaomiApi36OrLater = false,
                canDrawOverlays = false
            )
        )
        assertEquals(
            OverlayPresentationPolicy.Path.APPLICATION_OVERLAY,
            OverlayPresentationPolicy.alternatePathAfterFailure(
                isXiaomiApi36OrLater = false,
                attemptedPath = OverlayPresentationPolicy.Path.ACCESSIBILITY_OVERLAY
            )
        )
        assertEquals(
            OverlayPresentationPolicy.Path.ACCESSIBILITY_OVERLAY,
            OverlayPresentationPolicy.alternatePathAfterFailure(
                isXiaomiApi36OrLater = false,
                attemptedPath = OverlayPresentationPolicy.Path.APPLICATION_OVERLAY
            )
        )
    }
}
