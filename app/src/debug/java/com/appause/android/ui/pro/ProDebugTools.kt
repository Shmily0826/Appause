package com.appause.android.ui.pro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appause.android.R

/** Developer-only Pro controls; this implementation is absent from Release. */
@Composable
fun ProDebugTools(viewModel: ProViewModel, isPro: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.pro_debug_tools_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (!isPro) {
                OutlinedButton(
                    onClick = viewModel::unlockProDebug,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pro_debug_unlock))
                }
            } else {
                OutlinedButton(
                    onClick = viewModel::relockProDebug,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pro_debug_relock))
                }
            }
        }
    }
}

fun proDebugMessageResId(message: String): Int? = when (message) {
    "pro_debug_unlocked" -> R.string.pro_debug_unlocked
    "pro_debug_relocked" -> R.string.pro_debug_relocked
    else -> null
}
