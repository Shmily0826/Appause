package com.appause.android.ui.pro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.pro.DebugActivationOverride
import com.appause.android.data.pro.DebugActivationStore
import com.appause.android.data.pro.ProState
import com.appause.android.data.pro.ProEntitlement
import com.appause.android.data.pro.RedeemResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted

/**
 * ViewModel for the Appause Pro screen.
 *
 * Handles the free/paid status plus trial start and activation-code redemption.
 * The verified license token is device-bound and stays in storage; it is never
 * surfaced to the user, so there is no import/export action here.
 */
class ProViewModel(application: Application) : AndroidViewModel(application) {

    private val proState = (application as AppauseApp).proState

    val isPro: StateFlow<Boolean> = proState.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val entitlement: StateFlow<ProEntitlement> = proState.entitlement
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProEntitlement(com.appause.android.data.pro.ProAccessStatus.FREE))

    /** A one-shot message key for the UI to show (e.g. "pro_redeem_invalid"). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Structured result of the last redemption attempt, shown as a dialog. */
    private val _redeemResult = MutableStateFlow<RedeemResult?>(null)
    val redeemResult: StateFlow<RedeemResult?> = _redeemResult.asStateFlow()

    /** Debug-only unlock — the UI only calls this in debug builds. */
    fun unlockProDebug() {
        viewModelScope.launch {
            proState.unlockProDebug()
            _message.value = "pro_debug_unlocked"
        }
    }

    /** Debug-only relock — the UI only calls this in debug builds. */
    fun relockProDebug() {
        viewModelScope.launch {
            proState.relockProDebug()
            _message.value = "pro_debug_relocked"
        }
    }

    /**
     * The debug-build activation override currently in force.
     *
     * Always [DebugActivationOverride.None] on a release build, because the
     * release store has no debug implementation and never reads any storage.
     */
    internal val debugActivationOverride: StateFlow<DebugActivationOverride> =
        DebugActivationStore.overrideFlow(getApplication())
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                DebugActivationOverride.None
            )

    /** Debug-only: simulate a successful activation for the next seven days. */
    fun activateDebugForSevenDays() {
        viewModelScope.launch {
            DebugActivationStore.activateForDays(getApplication())
        }
    }

    /** Debug-only: force the not-activated state, even with a real license. */
    fun cancelDebugActivation() {
        viewModelScope.launch {
            DebugActivationStore.cancel(getApplication())
        }
    }

    /** Debug-only: drop the override and read the real activation state again. */
    fun clearDebugActivation() {
        viewModelScope.launch {
            DebugActivationStore.clear(getApplication())
        }
    }

    /** Redeem an activation code against the server (Plan B). */
    fun redeemCode(code: String) {
        viewModelScope.launch {
            _redeemResult.value = proState.redeemCode(code.trim())
        }
    }

    /** Start the one-time seven-day trial from the Pro screen. */
    fun startTrial() {
        viewModelScope.launch {
            _redeemResult.value = proState.startTrial()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearRedeemResult() {
        _redeemResult.value = null
    }
}
