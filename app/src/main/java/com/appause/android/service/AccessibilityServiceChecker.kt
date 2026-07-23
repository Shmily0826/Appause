package com.appause.android.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * Checks whether the Appause accessibility service is enabled, by asking the
 * SYSTEM rather than relying on an in-process flag.
 *
 * Why this matters (this fixed a real, very annoying bug):
 * - The old approach kept a static `isRunning` boolean inside the service.
 *   That variable lives in the app's process memory. When the OS kills the
 *   process (extremely common on Xiaomi HyperOS / Huawei HarmonyOS), the
 *   variable resets to false — even though the user's accessibility permission
 *   is still perfectly valid.
 * - Result: every time the app reopened it showed "Service not enabled" and
 *   nagged the user to grant the permission again, even though nothing was
 *   actually wrong.
 *
 * The truth lives in Settings.Secure:
 * - When a user enables an accessibility service, the system records its
 *   component name in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
 * - This setting survives process death and even device reboot. It is the
 *   authoritative answer to "has the user enabled our service?".
 *
 * So we read that setting directly. Now the UI only prompts the user when the
 * service is genuinely disabled — not merely because our process restarted.
 */
object AccessibilityServiceChecker {

    /**
     * @return true if the user has enabled the Appause accessibility service
     *         in system settings (and accessibility is turned on at all).
     */
    fun isEnabled(context: Context): Boolean {
        // 1. Is accessibility switched on for the device at all?
        //    If the user turned off accessibility entirely, no service runs,
        //    so we should treat ours as not enabled.
        val accessibilityOn = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            ) == 1
        } catch (e: Settings.SettingNotFoundException) {
            false
        }
        if (!accessibilityOn) return false

        // 2. Is OUR service listed among the enabled accessibility services?
        //    The value is a colon-separated list of flattened component names,
        //    e.g. "com.foo/.MyService:com.appause.android/.AppauseAccessibilityService".
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // The component we expect to find in that list.
        val expected = ComponentName(context, AppauseAccessibilityService::class.java)

        // Split on ':' and compare each entry. unflattenFromString() safely
        // parses both full ("pkg/full.Class") and short ("pkg/.Class") forms.
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (entry in splitter) {
            val component = ComponentName.unflattenFromString(entry)
            if (component != null && component == expected) {
                return true
            }
        }
        return false
    }
}
