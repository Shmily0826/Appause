package com.appause.android.service

import androidx.room.Room
import com.appause.android.AppauseApp
import com.appause.android.data.local.AppDatabase

/**
 * Test Application: identical to [AppauseApp] except the Room database is a
 * fresh IN-MEMORY instance per test method.
 *
 * Why: Robolectric's SQLite shadow tracks connection pointers per thread, and
 * the production on-disk singleton survives across test methods in one JVM —
 * a later test reusing that connection from a different Room executor thread
 * fails with "Illegal connection pointer". An in-memory database per
 * Application instance gives every test a clean database with zero cleanup.
 */
class TestAppauseApp : AppauseApp() {
    override val database: AppDatabase by lazy {
        Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
