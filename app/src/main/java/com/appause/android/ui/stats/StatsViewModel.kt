package com.appause.android.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.R
import com.appause.android.data.local.AppInterceptionCount
import com.appause.android.data.local.DailyStats
import com.appause.android.data.local.TotalRatio
import com.appause.android.data.local.ReasonCount
import com.appause.android.data.pro.ProState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/**
 * ViewModel for the Statistics screen.
 *
 * Exposes three StateFlows:
 * - dailyStats: per-day proceeded vs cancelled (for the bar chart)
 * - topApps: top 5 most-intercepted apps (for the list)
 * - totalRatio: overall proceeded/cancelled split (for the donut chart)
 *
 * The look-back window depends on Pro:
 * - Free users see the last [ProState.FREE_STATS_DAYS] days.
 * - Pro users see up to [ProState.PRO_STATS_DAYS] days (effectively all history).
 *
 * Why AndroidViewModel?
 * - We need application context to access the repository singleton.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AppauseApp).repository

    /** Whether Appause Pro is unlocked (drives the stats window). */
    val isPro: StateFlow<Boolean> = (application as AppauseApp).proState.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Start-of-day timestamp [days] ago (inclusive of today).
     * days=7 → -6; days=365 → -364.
     */
    private fun windowStart(days: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -(days - 1))
    }.timeInMillis

    /** Daily stats for the current window (for bar chart). */
    val dailyStats: StateFlow<List<DailyStats>> = isPro.flatMapLatest { pro ->
        repository.observeDailyStats(windowStart(if (pro) ProState.PRO_STATS_DAYS else ProState.FREE_STATS_DAYS))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** Top intercepted apps in the current window. */
    val topApps: StateFlow<List<AppInterceptionCount>> = isPro.flatMapLatest { pro ->
        repository.observeTopApps(windowStart(if (pro) ProState.PRO_STATS_DAYS else ProState.FREE_STATS_DAYS))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** Overall proceeded vs cancelled ratio in the current window. */
    val totalRatio: StateFlow<TotalRatio> = isPro.flatMapLatest { pro ->
        repository.observeTotalRatio(windowStart(if (pro) ProState.PRO_STATS_DAYS else ProState.FREE_STATS_DAYS))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TotalRatio(0, 0)
    )

    /** Proceeded-event counts grouped by reason (for the "reason breakdown" section). */
    val reasonCounts: StateFlow<List<ReasonCount>> = isPro.flatMapLatest { pro ->
        repository.observeReasonCounts(windowStart(if (pro) ProState.PRO_STATS_DAYS else ProState.FREE_STATS_DAYS))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Maps each stored reason key ("work"/"bored"/"messages"/"other") to its
     * display label. Uses the user's custom text (Pro) when set, otherwise the
     * localized default string resource. Lets the statistics "reason breakdown"
     * render proper labels instead of raw keys, and stay correct when the system
     * language changes.
     */
    val reasonLabels: StateFlow<Map<String, String>> = repository.reasons.map { custom ->
        val ctx = getApplication<AppauseApp>()
        val keys = listOf("work", "bored", "messages", "other")
        val defaults = listOf(
            ctx.getString(R.string.intent_work),
            ctx.getString(R.string.intent_bored),
            ctx.getString(R.string.intent_messages),
            ctx.getString(R.string.intent_other)
        )
        keys.mapIndexed { i, k -> k to (if (custom[i].isBlank()) defaults[i] else custom[i]) }.toMap()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )
}
