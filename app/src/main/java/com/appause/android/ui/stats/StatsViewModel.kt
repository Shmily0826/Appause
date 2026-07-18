package com.appause.android.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppInterceptionCount
import com.appause.android.data.local.DailyStats
import com.appause.android.data.local.TotalRatio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * All queries look back 7 days from today (start of day).
 *
 * Why AndroidViewModel?
 * - We need application context to access the repository singleton.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AppauseApp).repository

    /**
     * Calculate the start of 7 days ago (midnight).
     * This gives us a rolling window for the charts.
     */
    private val sevenDaysAgo: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -6) // -6 so today is included = 7 days total
    }.timeInMillis

    /** Daily stats for the past 7 days (for bar chart). */
    val dailyStats: StateFlow<List<DailyStats>> = repository.observeDailyStats(sevenDaysAgo)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Top 5 intercepted apps in the past 7 days. */
    val topApps: StateFlow<List<AppInterceptionCount>> = repository.observeTopApps(sevenDaysAgo)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Overall proceeded vs cancelled ratio for the past 7 days. */
    val totalRatio: StateFlow<TotalRatio> = repository.observeTotalRatio(sevenDaysAgo)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TotalRatio(0, 0)
        )
}
