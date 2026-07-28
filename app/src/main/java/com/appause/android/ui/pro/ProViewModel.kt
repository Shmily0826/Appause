package com.appause.android.ui.pro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.pro.ProState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted

/**
 * ViewModel for the Appause Pro screen.
 *
 * Handles the free/paid status and the (plan-A) unlock + license import/export
 * actions. Plan B will add real server verification behind [importLicense].
 */
class ProViewModel(application: Application) : AndroidViewModel(application) {

    private val proState = (application as AppauseApp).proState

    val isPro: StateFlow<Boolean> = proState.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** A one-shot message key for the UI to show (e.g. "pro_imported"). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** The most recently exported token, shown so the user can copy it. */
    private val _exportedToken = MutableStateFlow<String?>(null)
    val exportedToken: StateFlow<String?> = _exportedToken.asStateFlow()

    /** Debug-only unlock — the UI only calls this in debug builds. */
    fun unlockProDebug() {
        viewModelScope.launch {
            proState.unlockProDebug()
            _message.value = "pro_debug_unlocked"
        }
    }

    /** Import a pasted license token. */
    fun importLicense(token: String) {
        viewModelScope.launch {
            val ok = proState.importLicense(token.trim())
            _message.value = if (ok) "pro_imported" else "pro_import_failed"
        }
    }

    /** Export the license token for offline backup. */
    fun exportLicense() {
        viewModelScope.launch {
            _exportedToken.value = proState.exportLicense()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearExportedToken() {
        _exportedToken.value = null
    }
}
