package com.appause.android.util

import android.content.Context
import com.appause.android.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only, on-device crash capture.
 *
 * Installs a [Thread.getDefaultUncaughtExceptionHandler] that writes the last
 * uncaught exception's stack trace to an app-private file. The Diagnostics
 * screen reads and shows it, so a crash on a real device can be reported
 * without USB/adb.
 *
 * Privacy: the log never leaves the device and is only written in DEBUG
 * builds (BuildConfig.DEBUG == true), so it cannot reach a release APK.
 */
object CrashLog {
    private const val FILE_NAME = "appause-crash.log"
    private lateinit var appContext: Context

    /** Call once from Application.onCreate. No-op in release builds. */
    fun install(context: Context) {
        if (!BuildConfig.DEBUG) return
        appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(thread, throwable)
            } catch (_: Exception) {
                // Never let logging cause a second crash.
            }
            // Re-throw to the system handler so the normal crash dialog still shows.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val file = File(appContext.filesDir, FILE_NAME)
        file.parentFile?.mkdirs()
        val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("$ts CRASH on thread: ${thread.name}\n")
        appendThrowable(sb, throwable, 0)
        file.writeText(sb.toString())
    }

    private fun appendThrowable(sb: StringBuilder, t: Throwable, depth: Int) {
        if (depth > 4) return
        sb.append("${t.javaClass.name}: ${t.message}\n")
        t.stackTrace.take(50).forEach { sb.append("    at $it\n") }
        t.cause?.let { appendThrowable(sb, it, depth + 1) }
    }

    /** Returns the last crash stack (empty string if none). */
    fun read(): String = try {
        File(appContext.filesDir, FILE_NAME).takeIf { it.exists() }?.readText() ?: ""
    } catch (_: Exception) {
        ""
    }

    /** Deletes the captured crash (e.g. after it has been shared). */
    fun clear() {
        try {
            File(appContext.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
        }
    }
}
