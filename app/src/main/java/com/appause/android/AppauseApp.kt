package com.appause.android

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.appause.android.data.local.AppDatabase
import com.appause.android.data.repository.AppGroupRepository
import com.appause.android.data.settings.SettingsDataStore
import java.util.Locale

/**
 * Application class for Appause.
 *
 * Provides app-wide dependencies (database, repository, settings) as lazy singletons.
 * This is a simple alternative to dependency injection frameworks like Hilt/Dagger.
 *
 * Why Application class?
 * - It lives as long as the app process is alive.
 * - It is created before any Activity, so dependencies are ready when needed.
 * - We use `lazy` so objects are only created when first accessed (saves startup time).
 */
class AppauseApp : Application() {

    /**
     * Room database — the single source of persistent data.
     * Created lazily on first access.
     */
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    /**
     * DataStore wrapper — for simple key-value settings.
     */
    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(this)
    }

    /**
     * Repository — the single source of truth for the UI layer.
     * Wraps the DAO and provides clean Flow-based APIs.
     */
    val repository: AppGroupRepository by lazy {
        AppGroupRepository(database.appGroupDao(), database.appLaunchDao(), settingsDataStore)
    }

    /**
     * Override locale before any component initializes.
     * This ensures all strings use the user's selected language.
     */
    override fun attachBaseContext(base: Context) {
        // Read language preference synchronously from SharedPreferences
        val prefs = base.getSharedPreferences("appause_locale_prefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"

        // Apply locale override
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)

        val context = base.createConfigurationContext(config)
        super.attachBaseContext(context)
    }
}
