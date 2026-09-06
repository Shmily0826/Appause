package com.appause.android.service

import android.view.WindowManager

/**
 * Selects the first presentation surface and whether an overlay failure may
 * try the other overlay type.  This is pure so ROM-specific policy remains
 * testable without constructing WindowManager objects.
 */
internal object OverlayPresentationPolicy {

    internal enum class Path {
        ACCESSIBILITY_OVERLAY,
        APPLICATION_OVERLAY,
        ACTIVITY
    }

    fun initialPath(isXiaomiApi36OrLater: Boolean, canDrawOverlays: Boolean): Path {
        // Xiaomi/HyperOS may hide 2038 when the target app opts out of
        // non-system overlays. The accessibility-owned 2032 window is the
        // reliable primary surface for interception and does not need the
        // separate draw-over-other-apps permission.
        return Path.ACCESSIBILITY_OVERLAY
    }

    /**
     * Xiaomi Android 16 has a known 2032 interaction limitation. Do not add a
     * second overlay attempt after 2032 fails on that target.
     */
    fun alternatePathAfterFailure(
        isXiaomiApi36OrLater: Boolean,
        attemptedPath: Path
    ): Path? {
        if (isXiaomiApi36OrLater) return null
        return when (attemptedPath) {
            Path.ACCESSIBILITY_OVERLAY -> Path.APPLICATION_OVERLAY
            Path.APPLICATION_OVERLAY -> Path.ACCESSIBILITY_OVERLAY
            Path.ACTIVITY -> null
        }
    }
}

/**
 * Keeps the blocking surface interactive without letting it claim the system
 * status/navigation areas. The system owns Back, Home, Recents, and gesture
 * insets.
 */
internal object OverlayWindowPolicy {

    /**
     * Returns the explicit content height needed to stop a 2032 input frame
     * above a visible system navigation bar.
     */
    fun heightBeforeNavigationBar(
        displayHeight: Int,
        statusBarInset: Int,
        navigationBarInset: Int
    ): Int = (displayHeight - statusBarInset - navigationBarInset).coerceAtLeast(1)

    fun flags(keepOverlayNonFocusable: Boolean = false): Int {
        // Keep the blocker interactive inside its frame, but let pointer
        // events outside that frame reach system-owned windows such as the
        // navigation bar. FLAG_NOT_FOCUSABLE implies this too, but the
        // focusable primary path must state it explicitly.
        return WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            if (keepOverlayNonFocusable) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                0
            }
    }
}
