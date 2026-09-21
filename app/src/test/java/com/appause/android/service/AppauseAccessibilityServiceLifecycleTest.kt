package com.appause.android.service

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppauseAccessibilityServiceLifecycleTest {

    @Test
    fun `live instance reconciles stale process marker but absent instance stays fail closed`() {
        assertEquals(
            AccessibilityProcessState.CONNECTED,
            AppauseAccessibilityService.effectiveProcessState(
                AccessibilityProcessState.DISCONNECTED,
                hasLiveInstance = true
            )
        )
        assertEquals(
            AccessibilityProcessState.DISCONNECTED,
            AppauseAccessibilityService.effectiveProcessState(
                AccessibilityProcessState.DISCONNECTED,
                hasLiveInstance = false
            )
        )
        assertEquals(
            AccessibilityProcessState.UNKNOWN,
            AppauseAccessibilityService.effectiveProcessState(
                AccessibilityProcessState.UNKNOWN,
                hasLiveInstance = false
            )
        )
    }

    @Test
    fun `framework event reconciles stale process state and destroy remains fail closed`() {
        val controller = Robolectric
            .buildService(AppauseAccessibilityService::class.java)
            .create()
        val service = controller.get()
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)

        service.onAccessibilityEvent(event)
        event.recycle()

        assertEquals(
            AccessibilityProcessState.CONNECTED,
            AppauseAccessibilityService.currentProcessState()
        )

        controller.destroy()

        assertEquals(
            AccessibilityProcessState.DISCONNECTED,
            AppauseAccessibilityService.currentProcessState()
        )
    }
}
