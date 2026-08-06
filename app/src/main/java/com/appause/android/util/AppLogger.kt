package com.appause.android.util

import android.util.Log
import com.appause.android.BuildConfig

/**
 * Debug-only logger.
 *
 * All Appause logging goes through this wrapper so that NOTHING is written to
 * logcat in release builds — including the user's installed package names,
 * which would be a privacy leak for an app that markets itself as local-only.
 *
 * - Debug builds (BuildConfig.DEBUG == true): forwards to android.util.Log AND
 *   mirrors the line into [LogBuffer], so the in-app Diagnostics screen can show
 *   the same information to a tester who has no adb access.
 * - Release builds (BuildConfig.DEBUG == false): the calls are skipped entirely,
 *   so the log strings are not even constructed.
 */
object AppLogger {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg)
            LogBuffer.add("D", tag, msg)
        }
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, msg)
            LogBuffer.add("I", tag, msg)
        }
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, msg)
            LogBuffer.add("W", tag, msg)
        }
    }

    fun e(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg)
            LogBuffer.add("E", tag, msg)
        }
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, msg, tr)
            LogBuffer.add("W", tag, "$msg | ${tr.javaClass.simpleName}: ${tr.message}")
        }
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg, tr)
            LogBuffer.add("E", tag, "$msg | ${tr.javaClass.simpleName}: ${tr.message}")
        }
    }
}
