package com.appause.android.ui.pro

import androidx.compose.runtime.Composable

/** Release builds deliberately contribute no developer Pro controls. */
@Composable
fun ProDebugTools(viewModel: ProViewModel, isPro: Boolean) = Unit

/** Release builds have no debug-only Pro message resources. */
fun proDebugMessageResId(message: String): Int? = null
