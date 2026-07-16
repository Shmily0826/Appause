package com.appause.android.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * AppauseAccessibilityService — detects foreground app changes.
 *
 * This is a STUB implementation for Phase 1 (compilation only).
 * Full interception logic will be implemented in Phase 6.
 *
 * What is AccessibilityService?
 * - A system-level service that receives UI events from ALL apps on the device.
 * - Originally designed to help users with disabilities (screen readers, etc.).
 * - We use it to detect when a new app comes to the foreground.
 * - The user MUST manually enable it in Settings → Accessibility → Appause.
 *
 * Lifecycle:
 * - System creates the service when the user enables it.
 * - onServiceConnected() is called once the service is bound.
 * - onAccessibilityEvent() fires for every matching event.
 * - onInterrupt() is called if the system needs to interrupt feedback.
 * - onUnbind() / onDestroy() when the service is stopped.
 *
 * IMPORTANT: This is an accessibility feature, NOT a monitoring tool.
 * We only read the package name from events — we never read UI content.
 */
class AppauseAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppauseA11yService"

        /**
         * Whether the service is currently running.
         * Used by the UI to show service status.
         */
        var isRunning: Boolean = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d(TAG, "AccessibilityService connected and running")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Stub: will be implemented in Phase 6.
        // For now, just log the event type for debugging.
        val eventType = event?.eventType ?: return
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: "unknown"
            Log.d(TAG, "Window changed: $packageName")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "AccessibilityService destroyed")
    }
}
