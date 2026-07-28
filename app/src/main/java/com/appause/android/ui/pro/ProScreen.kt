package com.appause.android.ui.pro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.BuildConfig
import com.appause.android.R
import com.appause.android.data.pro.ProState

/**
 * Appause Pro screen — shows the free/Pro comparison, lets the user unlock Pro
 * (debug build only for now) or import/export a license token.
 *
 * Plan A: no backend. Unlock is a local flag; the license token round-trips
 * through DataStore so Pro can be restored offline after a reset. Plan B will
 * verify the token's signature against a server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProViewModel = viewModel()
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportedToken by viewModel.exportedToken.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var codeInput by remember { mutableStateOf("") }

    // Show transient messages as a toast, then clear them.
    LaunchedEffect(message) {
        if (message != null) {
            val resId = when (message) {
                "pro_debug_unlocked" -> R.string.pro_debug_unlocked
                "pro_imported" -> R.string.pro_imported
                "pro_import_failed" -> R.string.pro_import_failed
                else -> null
            }
            resId?.let { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pro_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Current status ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isPro) stringResource(R.string.pro_status_pro)
                        else stringResource(R.string.pro_status_free),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPro) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Free vs Pro comparison ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.pro_upgrade_desc), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    CompareRow(
                        label = stringResource(R.string.pro_feature_groups),
                        free = stringResource(R.string.pro_free_groups),
                        pro = stringResource(R.string.pro_pro_groups)
                    )
                    CompareRow(
                        label = stringResource(R.string.pro_feature_cooldown),
                        free = stringResource(R.string.pro_free_cooldown),
                        pro = stringResource(R.string.pro_pro_cooldown)
                    )
                    CompareRow(
                        label = stringResource(R.string.pro_feature_reremind),
                        free = null,
                        pro = stringResource(R.string.pro_pro_reremind)
                    )
                    CompareRow(
                        label = stringResource(R.string.pro_feature_history),
                        free = stringResource(R.string.pro_free_history),
                        pro = stringResource(R.string.pro_pro_history)
                    )
                    CompareRow(
                        label = stringResource(R.string.pro_feature_prompt),
                        free = null,
                        pro = stringResource(R.string.pro_pro_prompt)
                    )
                }
            }

            // ── Activate (only when not Pro yet) ──
            if (!isPro) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.pro_activate_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it },
                            label = { Text(stringResource(R.string.pro_enter_code)) },
                            placeholder = { Text(stringResource(R.string.pro_code_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.importLicense(codeInput)
                                codeInput = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.pro_activate))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.pro_token_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Debug-only unlock — not shown in release builds.
                        if (BuildConfig.DEBUG) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = viewModel::unlockProDebug,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.pro_debug_unlock))
                            }
                        }
                    }
                }
            }

            // ── License management (export/import) ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.pro_license_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.pro_license_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::exportLicense,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.pro_export))
                    }
                }
            }
        }
    }

    // Dialog showing the exported token so the user can copy it.
    if (exportedToken != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearExportedToken,
            title = { Text(stringResource(R.string.pro_exported_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.pro_exported_desc), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = exportedToken ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Appause License", exportedToken ?: ""))
                        Toast.makeText(context, context.getString(R.string.pro_copied), Toast.LENGTH_SHORT).show()
                        viewModel.clearExportedToken()
                    }
                ) {
                    Text(stringResource(R.string.pro_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearExportedToken) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * One row in the Free/Pro comparison table.
 * @param free text for the free tier, or null to show a cross (not included).
 * @param pro text for the Pro tier.
 */
@Composable
private fun CompareRow(label: String, free: String?, pro: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Cell(text = free, icon = if (free == null) Icons.Default.Close else null)
        Spacer(modifier = Modifier.width(12.dp))
        Cell(text = pro, icon = Icons.Default.Check)
    }
}

@Composable
private fun Cell(text: String?, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (icon == Icons.Default.Check) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.width(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
