package com.appause.android.ui.pro

import androidx.compose.runtime.Composable

/**
 * Release builds deliberately contribute no developer Pro controls.
 *
 * The debug activation override has no entry point here: this composable
 * renders nothing, so a release build offers no way to set it. The release
 * `DebugActivationStore` is likewise inert, so even the shared ViewModel
 * actions cannot put a release build into the debug path.
 */
@Composable
fun ProDebugTools(viewModel: ProViewModel) = Unit

/** Release builds have no debug-only Pro message resources. */
fun proDebugMessageResId(message: String): Int? = null
