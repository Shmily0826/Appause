package com.appause.android.util

import android.content.Context
import com.appause.android.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File-backed debug logger for service lifecycle events.
 *
 * Why we need this:
 * - LogBuffer is in-memory; if the AccessibilityService process is killed,
 *   the ring buffer is lost.
 * - PersistentLog writes to app-private files so we can see whether the
 *   service process was ever created, whether onServiceConnected ran,
 *   and whether startForeground() failed — even after a crash/kill.
 *
 * Release builds: all methods are no-ops (BuildConfig.DEBUG == false).
 */
object PersistentLog {
    private const val FILE_NAME = "appause-service.log"
    private const val MAX_BYTES = 128 * 1024L

    fun log(context: Context, tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        try {
            val file = logFile(context)
            file.parentFile?.mkdirs()
            val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            file.appendText("$ts $tag: $message\n")
            trim(file)
        } catch (_: Exception) {
            // Logging must never crash the service or app.
        }
    }

    fun read(context: Context): String {
        return try {
            logFile(context).takeIf { it.exists() }?.readText() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun clear(context: Context) {
        try {
            logFile(context).delete()
        } catch (_: Exception) {
        }
    }

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun trim(file: File) {
        if (file.length() > MAX_BYTES) {
            val text = file.readText()
            val drop = text.length / 4
            file.writeText(text.drop(drop))
        }
    }
}
