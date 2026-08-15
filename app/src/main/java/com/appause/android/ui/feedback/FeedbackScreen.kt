package com.appause.android.ui.feedback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.BuildConfig
import com.appause.android.R
import com.appause.android.ui.diagnostics.DiagnosticsState
import java.net.URLEncoder
import java.util.Locale

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.appause.android.data.pro.ProConfig

/**
 * Feedback Screen — lets the user report a bug or submit a suggestion.
 *
 * Design notes (privacy-first, no backend):
 * - The app never sends anything on its own. The user explicitly chooses a
 *   channel (email or GitHub issue), and only THEN an Intent is fired.
 * - Device/app info (version, Android version, model, locale) is attached
 *   automatically so reports are actionable, but it is shown to the user
 *   before sending — nothing is hidden.
 * - No analytics, no crash reporter, no third-party SDK. Just two system
 *   Intents (ACTION_SENDTO for email, ACTION_VIEW for the GitHub issue form).
 */
private sealed class FeedbackResult {
    object Success : FeedbackResult()
    data class Error(val reason: String) : FeedbackResult()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onNavigateBack: () -> Unit,
    viewModel: FeedbackViewModel = viewModel()
) {
    var type by remember { mutableStateOf("bug") } // "bug" | "suggestion"
    var message by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feedbackResult by remember { mutableStateOf<FeedbackResult?>(null) }

    // On-device diagnostic snapshot attached to every submission (see FeedbackViewModel).
    val diagState by viewModel.state.collectAsStateWithLifecycle()
    val diagnosticBlock = remember(diagState) {
        buildDiagnosticBlock(diagState, viewModel.logTail())
    }

    // Static per-install info. Computed once with remember.
    val deviceInfo = remember {
        buildString {
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
        }.trimEnd()
    }

    // The report body the user will send. Recomposed when inputs change.
    val reportBody = remember(message, contact, deviceInfo, diagnosticBlock) {
        buildString {
            if (contact.isNotBlank()) appendLine("Contact: $contact")
            appendLine()
            appendLine(message)
            appendLine()
            appendLine("---")
            appendLine(deviceInfo)
            if (diagnosticBlock.isNotBlank()) {
                appendLine()
                appendLine(diagnosticBlock)
            }
        }.trimEnd()
    }

    val canSend = message.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feedback_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
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
            Text(
                text = stringResource(R.string.feedback_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Type selector ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == "bug",
                    onClick = { type = "bug" },
                    label = { Text(stringResource(R.string.feedback_bug)) }
                )
                FilterChip(
                    selected = type == "suggestion",
                    onClick = { type = "suggestion" },
                    label = { Text(stringResource(R.string.feedback_suggestion)) }
                )
            }

            // ── Message ──
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.feedback_message_label)) },
                placeholder = { Text(stringResource(R.string.feedback_message_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                singleLine = false
            )

            // ── Optional contact ──
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                label = { Text(stringResource(R.string.feedback_contact_label)) },
                placeholder = { Text(stringResource(R.string.feedback_contact_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Auto-attached info (shown, not hidden) ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.feedback_auto_info),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        deviceInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Diagnostic status notice (transparency) ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.feedback_diag_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Send actions ──
            Button(
                onClick = {
                    scope.launch {
                        feedbackResult = submitFeedbackViaServer(type, message, contact, diagnosticBlock)
                    }
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.feedback_send_appause))
            }
            OutlinedButton(
                onClick = { sendEmail(context, type, reportBody) },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.feedback_send_email))
            }
            OutlinedButton(
                onClick = { openGitHubIssue(context, type, reportBody) },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.feedback_open_issue))
            }
        }
    }

    val fr = feedbackResult
    if (fr != null) {
        val titleRes = if (fr is FeedbackResult.Success) R.string.feedback_sent_title else R.string.feedback_failed_title
        val textRes = when (fr) {
            is FeedbackResult.Success -> R.string.feedback_sent_desc
            is FeedbackResult.Error -> if (fr.reason == "network_error") R.string.feedback_failed_network else R.string.feedback_failed_generic
        }
        AlertDialog(
            onDismissRequest = { feedbackResult = null },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(textRes)) },
            confirmButton = {
                TextButton(onClick = { feedbackResult = null }) {
                    Text(stringResource(R.string.feedback_dialog_ok))
                }
            }
        )
    }
}

/**
 * Open the device's email chooser pre-filled with the report.
 * ACTION_SENDTO + a mailto: URI restricts this to email apps only.
 */
private fun sendEmail(context: Context, type: String, body: String) {
    val subject = if (type == "bug") "Appause Bug Report" else "Appause Suggestion"
    val uri = Uri.parse(
        "mailto:rng2018520@gmail.com?subject=${encode(subject)}&body=${encode(body)}"
    )
    val intent = Intent(Intent.ACTION_SENDTO, uri)
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.feedback_send_email)))
}

/**
 * Open the GitHub "new issue" form pre-filled with the report.
 * The title gets a [Bug]/[Suggestion] prefix and the right label is applied.
 */
private fun openGitHubIssue(context: Context, type: String, body: String) {
    val title = if (type == "bug") "[Bug] " else "[Suggestion] "
    val label = if (type == "bug") "bug" else "enhancement"
    val url = "https://github.com/Shmily0826/Appause/issues/new" +
        "?title=${encode(title)}&body=${encode(body)}&labels=${encode(label)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

/** UTF-8 URL-encode; replace + with %20 so GitHub/mailto parse spaces correctly. */
private fun encode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/**
 * Submit feedback anonymously to the Appause Worker (/api/feedback).
 * Mirrors the Pro redeem network call: HttpURLConnection + JSONObject, on IO.
 * No email or account is required. Returns a [FeedbackResult].
 *
 * @param diagnostics Human-readable on-device diagnostic status (structured
 *   signals only — see [buildDiagnosticBlock]). Attached so the developer can
 *   see why interception may be failing for this user, without any usage history.
 */
private suspend fun submitFeedbackViaServer(
    type: String,
    message: String,
    contact: String,
    diagnostics: String
): FeedbackResult = withContext(Dispatchers.IO) {
    val base = ProConfig.WORKER_BASE_URL
    if (base.isBlank()) return@withContext FeedbackResult.Error("worker_not_configured")
    try {
        val bodyJson = JSONObject().apply {
            put("type", type)
            put("message", message)
            put("contact", contact)
            put("appVersion", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            put("androidVersion", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("language", Locale.getDefault().toLanguageTag())
            put("diagnostics", diagnostics)
        }.toString()
        val url = URL("$base/api/feedback")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val resp = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        conn.disconnect()
        if (code !in 200..299) {
            val reason = runCatching {
                JSONObject(resp).optString("error", "http_$code")
            }.getOrDefault("http_$code")
            return@withContext FeedbackResult.Error(reason)
        }
        FeedbackResult.Success
    } catch (e: Exception) {
        FeedbackResult.Error("network_error")
    }
}

/**
 * Build the on-device diagnostic block attached to every feedback submission.
 * Uses only the structured status signals the Diagnostics screen already shows —
 * never free-text production logs. [logTail] is the in-memory ring buffer, which
 * is empty in release builds (debug-only), so it is appended only when present.
 */
private fun buildDiagnosticBlock(state: DiagnosticsState?, logTail: String): String {
    if (state == null) return ""
    return buildString {
        appendLine("--- 诊断状态 / Diagnostics ---")
        appendLine("verdict: ${state.verdict}")
        appendLine("accessibility(系统): ${state.accessibilityEnabledInSettings}")
        appendLine("serviceAlive: ${state.serviceAlive}")
        appendLine("masterEnabled: ${state.masterEnabled}")
        appendLine("usageAccess: ${state.usageAccessGranted}")
        appendLine("overlayPermission: ${state.overlayPermissionGranted}")
        appendLine("batteryExempted: ${state.batteryExempted}")
        appendLine("otherAppauseBuilds: ${state.otherAppauseBuilds.ifEmpty { "无/none" }}")
        appendLine("overlayResult: ${state.overlayResult ?: "无/none"}")
        appendLine("lastDecision: ${state.lastDecision ?: "无/none"}")
        appendLine("lastTargetDecision: ${state.lastTargetDecision ?: "无/none"}")
        appendLine("foreground: ${state.foregroundPackage ?: "无/none"}")
        appendLine("groups: ${state.groups.size} (activeIntercepting: ${state.activeGroups.size})")
        if (logTail.isNotBlank()) {
            appendLine()
            appendLine("--- 内存日志 (debug only) ---")
            appendLine(logTail)
        }
    }.trimEnd()
}
