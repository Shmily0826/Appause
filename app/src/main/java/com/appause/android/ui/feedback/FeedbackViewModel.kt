package com.appause.android.ui.feedback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.ui.diagnostics.DiagnosticsState
import com.appause.android.ui.diagnostics.collectDiagnostics
import com.appause.android.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Supplies the on-device diagnostic snapshot attached to every feedback
 * submission, so the developer can see WHY interception may be failing for a
 * user without asking them to open the Diagnostics screen and copy logs.
 *
 * Only structured status is attached — the same signals the Diagnostics screen
 * already shows. No usage history, and no free-text production logs (those are
 * debug-only and empty in release builds, so [LogBuffer.dump] returns "" there).
 */
class FeedbackViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<DiagnosticsState?>(null)
    val state: StateFlow<DiagnosticsState?> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.value = collectDiagnostics(context)
        }
    }

    /** In-memory log tail — debug builds only; returns "" in release. */
    fun logTail(): String = LogBuffer.dump()
}
