package com.appause.android.service

import android.view.WindowManager

/**
 * Selects the presentation surface for the cooldown screen and whether an
 * overlay failure may try the other overlay type. Pure so ROM-specific policy
 * remains testable without constructing WindowManager objects.
 */
internal object OverlayPresentationPolicy {

    internal enum class Path {
        ACCESSIBILITY_OVERLAY,
        APPLICATION_OVERLAY,
        ACTIVITY
    }

    /**
     * The primary presentation surface: the accessibility-owned 2032 window.
     * It needs no SYSTEM_ALERT_WINDOW permission and anti-tamper target apps
     * cannot hide it (unlike TYPE_APPLICATION_OVERLAY), so it is always tried
     * first. A previous revision accepted a Xiaomi/Android-16 flag and the
     * canDrawOverlays result here, but every branch returned this value — the
     * parameters were dead and made the strategy look more configurable than
     * it actually is. If a future ROM ever needs a different initial window
     * type, reintroduce the decision here (with tests).
     */
    val initialPath: Path = Path.ACCESSIBILITY_OVERLAY

    /**
     * Xiaomi Android 16 has a known 2032 interaction limitation. Do not add a
     * second overlay attempt after 2032 fails on that target (fall straight
     * through to PauseActivity); on every other ROM the 2038 retry is allowed.
     */
    fun shouldRetryWith2038AfterFailure(isXiaomiApi36OrLater: Boolean): Boolean =
        !isXiaomiApi36OrLater
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

    /**
     * Interactive inside its frame, but pointer events outside that frame
     * reach system-owned windows such as the navigation bar. The primary
     * 2032 window is focusable, so FLAG_NOT_FOCUSABLE is deliberately absent.
     */
    fun flags(): Int = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
}
