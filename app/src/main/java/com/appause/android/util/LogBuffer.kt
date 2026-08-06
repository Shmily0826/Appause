package com.appause.android.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer of recent log lines, so the Diagnostics screen can show
 * logs ON THE PHONE instead of requiring `adb logcat` on a computer.
 *
 * Why this exists:
 * Testers install the APK by sending it to their phone (QQ, email, ...) and have
 * no adb access. Without an in-app log view there is no way for them to tell us
 * WHY an app was not intercepted. This buffer mirrors whatever [AppLogger] writes
 * so the Diagnostics screen can display and share it.
 *
 * Privacy:
 * - Written to ONLY in debug builds (AppLogger guards every call with
 *   BuildConfig.DEBUG), exactly like logcat output.
 * - Lives in RAM only. Nothing is persisted to disk unless the user explicitly
 *   taps "share" on the Diagnostics screen.
 */
object LogBuffer {

    /** Keep the most recent N lines. Enough for a full reproduce, small in RAM. */
    private const val MAX_LINES = 600

    private val lock = Any()
    private val lines = ArrayDeque<String>()

    /**
     * SimpleDateFormat is NOT thread-safe, and log calls arrive from the service
     * thread, coroutine dispatchers and the UI thread. It is only ever touched
     * inside the [lock] block below.
     */
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())

    /** Observed by the Diagnostics screen; emits a new snapshot on every line. */
    val logLines: StateFlow<List<String>> = _lines.asStateFlow()

    /** Append one formatted line. Called by [AppLogger] only. */
    fun add(level: String, tag: String, message: String) {
        synchronized(lock) {
            val stamp = timeFormat.format(Date())
            lines.addLast("$stamp $level/$tag: $message")
            while (lines.size > MAX_LINES) lines.removeFirst()
            // Publish an immutable copy so Compose sees a genuinely new list.
            _lines.value = lines.toList()
        }
    }

    /** Wipe the buffer (the "clear" button before starting a fresh reproduce). */
    fun clear() {
        synchronized(lock) {
            lines.clear()
            _lines.value = emptyList()
        }
    }

    /** Whole buffer as one string, for copy-to-clipboard / share-as-file. */
    fun dump(): String = synchronized(lock) { lines.joinToString("\n") }
}
