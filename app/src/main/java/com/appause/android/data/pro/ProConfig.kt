package com.appause.android.data.pro

/**
 * Configuration for Appause Pro online activation (Plan B server side).
 *
 * [WORKER_BASE_URL] points at your deployed Cloudflare Worker (see
 * worker/README.md). It is intentionally empty by default — the app stays
 * fully offline until you fill this in and the user taps "Redeem online".
 * Activation is a one-time network call; daily use never touches the network.
 */
object ProConfig {
    const val WORKER_BASE_URL = "" // e.g. "https://appause-pro.<subdomain>.workers.dev"
}
