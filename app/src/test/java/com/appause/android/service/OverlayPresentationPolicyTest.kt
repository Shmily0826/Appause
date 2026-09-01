package com.appause.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayPresentationPolicyTest {

    @Test
    fun `Xiaomi Android 16 with overlay permission starts with interactive application overlay`() {
        assertEquals(
            OverlayPresentationPolicy.Path.APPLICATION_OVERLAY,
            OverlayPresentationPolicy.initialPath(
                isXiaomiApi36OrLater = true,
                canDrawOverlays = true
            )
        )
        assertNull(
            OverlayPresentationPolicy.alternatePathAfterFailure(
                isXiaomiApi36OrLater = true,
                attemptedPath = OverlayPresentationPolicy.Path.APPLICATION_OVERLAY
            )
        )
    }

    @Test
    fun `Xiaomi Android 16 without overlay permission uses Activity fallback`() {
        assertEquals(
            OverlayPresentationPolicy.Path.ACTIVITY,
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
