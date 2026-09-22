package com.appause.android.data.pro

import com.appause.android.BuildConfig

/**
 * Configuration for Appause Pro online activation (Plan B server side).
 *
 * Release builds use the deployed Cloudflare Worker. Debug builds retain that
 * production endpoint by default and may opt in to a temporary local endpoint
 * at build time; debug-only overrides are not included in release values.
 * Activation is a one-time network call; daily use never touches the network.
 */
object ProConfig {
    private const val PRODUCTION_WORKER_BASE_URL =
        "https://appause-pro-worker.rng2018520.workers.dev"

    val WORKER_BASE_URL: String
        get() = if (BuildConfig.DEBUG) {
            BuildConfig.DEBUG_WORKER_BASE_URL
        } else {
            PRODUCTION_WORKER_BASE_URL
        }
}
