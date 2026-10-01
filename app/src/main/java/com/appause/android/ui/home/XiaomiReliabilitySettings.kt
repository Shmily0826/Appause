package com.appause.android.ui.home

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal const val XIAOMI_AUTOSTART_PACKAGE = "com.miui.securitycenter"
internal const val XIAOMI_AUTOSTART_CLASS = "com.miui.permcenter.autostart.AutoStartManagementActivity"

internal fun isXiaomiManufacturer(manufacturer: String): Boolean =
    manufacturer.equals("Xiaomi", ignoreCase = true)

internal fun autostartApplicationDetailsUri(packageName: String): String = "package:$packageName"

internal fun openXiaomiAutostartSettings(context: Context) {
    if (!isXiaomiManufacturer(Build.MANUFACTURER)) return

    val autostartIntent = Intent().apply {
        component = ComponentName(XIAOMI_AUTOSTART_PACKAGE, XIAOMI_AUTOSTART_CLASS)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(autostartIntent)
        return
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    } catch (_: RuntimeException) {
    }

    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse(autostartApplicationDetailsUri(context.packageName))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(appDetailsIntent)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    } catch (_: RuntimeException) {
    }
}
