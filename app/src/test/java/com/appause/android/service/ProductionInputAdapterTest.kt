package com.appause.android.service

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import com.appause.android.data.local.AppGroup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * P2 adapter tests: the production INPUT sources, not the policies they feed.
 *
 * 1. [AccessibilityServiceChecker.systemState] must translate raw
 *    Settings.Secure rows into [AccessibilitySystemState] — the policy truth
 *    table is covered elsewhere; this pins the READ.
 * 2. [AppauseAccessibilityService.refreshHomePackages] must resolve HOME
 *    launcher(s) through PackageManager and keep treating them as system
 *    surfaces; an empty resolution must degrade safely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestAppauseApp::class)
class ProductionInputAdapterTest : ServiceOrchestrationTestBase() {

    private fun registerHomeLauncher(packageName: String) {
        val activityInfo = android.content.pm.ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.HomeActivity"
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                this.packageName = packageName
                flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM
            }
        }
        shadowOf(service.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            ResolveInfo().apply { this.activityInfo = activityInfo }
        )
    }

    // ── A. AccessibilityServiceChecker.systemState → Settings.Secure read ──

    @Test
    fun `systemState maps the raw Settings-Secure rows faithfully`() {
        val resolver = service.applicationContext.contentResolver
        val checker = AccessibilityServiceChecker

        // Nothing set at all → the getInt read throws/absent → UNKNOWN (fail-safe).
        assertEquals(AccessibilitySystemState.UNKNOWN, checker.systemState(service))

        // Master switch off → DISABLED regardless of the services list.
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.example.other/com.example.other.TheirService"
        )
        assertEquals(AccessibilitySystemState.DISABLED, checker.systemState(service))

        // Master switch on, but Appause NOT in the enabled services → DISABLED.
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        assertEquals(AccessibilitySystemState.DISABLED, checker.systemState(service))

        // Master switch on + Appause's component flattened into the list → ENABLED.
        val component = android.content.ComponentName(service, AppauseAccessibilityService::class.java)
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.example.other/com.example.other.TheirService:" + component.flattenToString()
        )
        assertEquals(AccessibilitySystemState.ENABLED, checker.systemState(service))

        // isEnabled() is the ENABLED shorthand.
        assertTrue(checker.isEnabled(service))
    }

    // ── B. refreshHomePackages — HOME resolution through PackageManager ──

    @Test
    fun `refreshHomePackages resolves installed launchers and skips them as system`() = runBlocking<Unit> {
        registerHomeLauncher("com.launcher.one")
        registerHomeLauncher("com.launcher.two")
        service.refreshHomePackages()

        // Behavior check (homePackages itself is private): both launchers must
        // now be filtered as system packages by the real decision path.
        handleForeground("com.launcher.one")
        assertEquals(
            "SKIP: system package (com.launcher.one)",
            AppauseAccessibilityService.lastDecision
        )
        handleForeground("com.launcher.two")
        assertEquals(
            "SKIP: system package (com.launcher.two)",
            AppauseAccessibilityService.lastDecision
        )
    }

    @Test
    fun `an unresolvable home set degrades safely - launcher events flow through normal handling`() = runBlocking<Unit> {
        // No HOME activity registered: queryIntentActivities returns empty and
        // resolveActivity returns null — the adapter must not crash and must
        // not classify anything as home.
        service.refreshHomePackages()

        val groupId = repository.saveGroupWithApps(
            AppGroup(name = "adapter-home", cooldownSeconds = 5),
            listOf("com.adapter.target")
        )
        createdGroupIds += groupId

        // A package that would be a launcher on a real device is, with an
        // empty resolution, treated as an ordinary app: it falls through the
        // system gate into the normal group lookup.
        handleForeground("com.some.launcher")
        assertEquals(
            "SKIP: not in any group (com.some.launcher)",
            AppauseAccessibilityService.lastDecision
        )
    }

    @Test
    fun `refresh is idempotent and picks up launcher changes like a reconnect would`() = runBlocking<Unit> {
        service.refreshHomePackages()
        registerHomeLauncher("com.launcher.late")
        // "Reconnect" (onServiceConnected calls refreshHomePackages again).
        service.refreshHomePackages()
        handleForeground("com.launcher.late")
        assertEquals(
            "SKIP: system package (com.launcher.late)",
            AppauseAccessibilityService.lastDecision
        )
    }
}
