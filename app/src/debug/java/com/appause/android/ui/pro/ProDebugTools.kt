package com.appause.android.ui.pro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appause.android.R
import com.appause.android.data.pro.DebugActivationOverride
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Developer-only Pro controls; this implementation is absent from Release. */
@Composable
fun ProDebugTools(viewModel: ProViewModel) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val override by viewModel.debugActivationOverride.collectAsStateWithLifecycle()

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
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // ── Activation debug ──────────────────────────────────────────
            // Reports what the single entitlement flow currently resolves to,
            // so it is obvious whether a tap actually took effect. "Real" means
            // no override is set and the verified license decides.
            Text(
                stringResource(R.string.debug_activation_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))

            val stateText = when (val current = override) {
                is DebugActivationOverride.None -> stringResource(
                    R.string.debug_activation_state_real,
                    entitlement.status.name
                )
                is DebugActivationOverride.Inactive ->
                    stringResource(R.string.debug_activation_state_inactive)
                is DebugActivationOverride.Active ->
                    stringResource(R.string.debug_activation_state_active)
            }
            Text(stateText, style = MaterialTheme.typography.bodySmall)

            val expiresAt = (override as? DebugActivationOverride.Active)?.expiresAtMillis
            if (expiresAt != null) {
                val formatter = remember {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                }
                Text(
                    stringResource(
                        R.string.debug_activation_expires,
                        formatter.format(Date(expiresAt))
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::activateDebugForSevenDays,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.debug_activation_activate))
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = viewModel::cancelDebugActivation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.debug_activation_cancel))
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = viewModel::clearDebugActivation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.debug_activation_use_real))
            }
        }
    }
}

fun proDebugMessageResId(message: String): Int? = when (message) {
    "pro_debug_unlocked" -> R.string.pro_debug_unlocked
    else -> null
}
