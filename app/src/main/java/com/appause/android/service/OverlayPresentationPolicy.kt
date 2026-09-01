package com.appause.android.service

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
        if (!isXiaomiApi36OrLater) return Path.ACCESSIBILITY_OVERLAY
        return if (canDrawOverlays) Path.APPLICATION_OVERLAY else Path.ACTIVITY
    }

    /**
     * Xiaomi Android 16 has a known 2032 interaction failure.  Do not retry
     * that surface after the interactive 2038 path fails on that target.
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
