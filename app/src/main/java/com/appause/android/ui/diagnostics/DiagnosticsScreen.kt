package com.appause.android.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.BuildConfig
import com.appause.android.data.local.AppGroup
import com.appause.android.util.LogBuffer
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Diagnostics screen — DEBUG BUILDS ONLY (the entry point in Settings is gated
 * on BuildConfig.DEBUG, and LogBuffer only records in debug builds).
 *
 * Text on this screen is intentionally hardcoded rather than going through
 * strings.xml: it is an internal troubleshooting tool that never ships to end
 * users, so adding ~30 strings to every translation file would be pure noise.
 */

private val OkGreen = Color(0xFF4CAF50)
private val WarnAmber = Color(0xFFFFB300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiagnosticsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logs by LogBuffer.logLines.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Live view: re-read every signal once a second while the screen is open,
    // so the tester can watch counters move as they open apps.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运行诊断") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item { VerdictCard(state = state, context = context, viewModel = viewModel) }

            item { StatusCard(state = state) }

            item { GroupsCard(state = state) }

            item { PersistentLogCard(state = state, viewModel = viewModel) }

            item {
                LogHeader(
                    lineCount = logs.size,
                    onClear = { LogBuffer.clear() },
                    onCopy = {
                        copyToClipboard(context, buildReport(state, logs))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    onShare = { shareReport(context, buildReport(state, logs)) }
                )
            }

            if (logs.isEmpty()) {
                item {
                    Text(
                        text = "暂无日志。保持本页打开或退出后去打开一个被分组的 app，日志会自动记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // Newest first — on a phone you want the latest line without scrolling.
            items(logs.asReversed()) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = logLineColor(line)
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** One-sentence conclusion: the most likely reason nothing is being intercepted. */
@Composable
private fun VerdictCard(
    state: DiagnosticsState,
    context: Context,
    viewModel: DiagnosticsViewModel
) {
    val (color, title, detail) = when (state.verdict) {
        DiagnosticsState.Verdict.NO_PERMISSION -> Triple(
            MaterialTheme.colorScheme.error,
            "无障碍权限未开启",
            "系统里没有勾选 Appause 的无障碍服务，拦截无法工作。"
        )
        DiagnosticsState.Verdict.SERVICE_DEAD -> Triple(
            MaterialTheme.colorScheme.error,
            "服务没有在运行",
            "系统设置里显示已开启，但服务进程不存在——通常是被省电策略杀掉了。请关掉再重新打开无障碍开关，并把 Appause 加入自启动白名单。"
        )
        DiagnosticsState.Verdict.MASTER_OFF -> Triple(
            MaterialTheme.colorScheme.error,
            "Appause 总开关已关闭",
            "首页的总开关处于关闭状态，所有拦截都会被跳过。"
        )
        DiagnosticsState.Verdict.NO_GROUPS -> Triple(
            WarnAmber,
            "没有可拦截的分组",
            "需要至少一个「暂停」类型的分组，并且里面至少加了一个 app。注意：「推荐/学习」类型的分组永远不会拦截。"
        )
        DiagnosticsState.Verdict.NO_EVENTS -> Triple(
            WarnAmber,
            "服务在跑，但还没收到任何前台事件",
            "去打开任意一个 app 再回来看。如果计数一直是 0，说明系统没有把事件发给我们。"
        )
        DiagnosticsState.Verdict.OK -> Triple(
            OkGreen,
            "配置看起来正常",
            "可以去打开被分组的 app 测试了。若仍不拦截，下面的日志会显示每次判断被跳过的原因。"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.verdict == DiagnosticsState.Verdict.OK) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = color
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Quick jump to the system page the tester needs most often.
            if (state.verdict == DiagnosticsState.Verdict.NO_PERMISSION ||
                state.verdict == DiagnosticsState.Verdict.SERVICE_DEAD
            ) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { openAccessibilitySettings(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("打开无障碍设置")
                }
                if (state.verdict == DiagnosticsState.Verdict.SERVICE_DEAD) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.forceStartService() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("尝试强制启动服务")
                    }
                    state.forceStartResult?.let { result ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** The gate-by-gate checklist, in the same order the service evaluates it. */
@Composable
private fun StatusCard(state: DiagnosticsState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle("状态检查")
            InfoRow("本机构建时间", BuildConfig.BUILD_TIME)

            StatusRow(
                ok = state.accessibilityEnabledInSettings,
                label = "无障碍权限（系统设置）",
                value = if (state.accessibilityEnabledInSettings) "已开启" else "未开启"
            )
            StatusRow(
                ok = state.serviceAlive,
                label = "服务进程存活",
                value = if (state.serviceAlive) "运行中 · ${sinceText(state.connectedAt)}" else "未运行"
            )
            StatusRow(
                ok = state.masterEnabled,
                label = "Appause 总开关",
                value = if (state.masterEnabled) "已开启" else "已关闭"
            )
            StatusRow(
                ok = state.usageAccessGranted,
                warnOnly = true,
                label = "使用情况访问权限",
                value = if (state.usageAccessGranted) "已授权" else "未授权（可能误判前台 app）"
            )
            StatusRow(
                ok = state.overlayPermissionGranted,
                warnOnly = true,
                label = "悬浮窗权限（显示悬浮窗）",
                value = if (state.overlayPermissionGranted) "已授权" else "未授权（小红书等 app 可能盖住停顿页）"
            )
            StatusRow(
                ok = state.eventCount > 0,
                label = "已收到前台事件",
                value = "${state.eventCount} 次"
            )

            Spacer(Modifier.height(8.dp))
            InfoRow("最近事件", state.lastEventPackage?.let { "$it · ${sinceText(state.lastEventAt)}" } ?: "无")
            InfoRow("系统认定的前台", state.foregroundPackage ?: "未知（需使用情况权限）")
            InfoRow("最近一次判断", state.lastDecision ?: "无")
            InfoRow(
                "阻断界面",
                when (state.overlayResult) {
                    "overlay_ok" -> "悬浮窗已显示（系统覆盖层）"
                    "fallback_pauseactivity" -> "已退回 PauseActivity（AlarmManager 启动）"
                    else -> "尚未拦截"
                }
            )
            if (state.bypassed.isNotEmpty()) {
                InfoRow("放行中的 app", state.bypassed.joinToString(", "))
            }
        }
    }
}

/** Which groups exist, and whether each one can actually intercept. */
@Composable
private fun GroupsCard(state: DiagnosticsState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle("分组（${state.groups.size} 个）")

            if (state.groups.isEmpty()) {
                Text(
                    text = "还没有任何分组。Debug 版是独立应用，不会读取正式版的数据——需要在这里重新建分组并加入要测试的 app。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                return@Column
            }

            state.groups.forEach { group ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (group.intercepts && group.packages.isNotEmpty()) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Cancel
                        },
                        contentDescription = null,
                        tint = if (group.intercepts && group.packages.isNotEmpty()) OkGreen else WarnAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (group.type == AppGroup.TYPE_LEARNING) "推荐（不拦截）" else "暂停 · ${group.cooldownSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (group.type == AppGroup.TYPE_LEARNING) WarnAmber else MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (group.packages.isEmpty()) {
                        "（分组里没有 app —— 不会拦截任何东西）"
                    } else {
                        group.packages.joinToString("\n")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = if (group.packages.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(start = 26.dp, top = 2.dp)
                )
            }
        }
    }
}

/** Persistent service lifecycle log — survives process death / service kills. */
@Composable
private fun PersistentLogCard(state: DiagnosticsState, viewModel: DiagnosticsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "持久化服务日志（进程死后仍保留）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新持久日志")
                }
            }
            if (state.persistentLog.isBlank()) {
                Text(
                    text = "还没有持久日志。点击「尝试强制启动服务」或重新开关无障碍后会生成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val lines = state.persistentLog.trim().lines()
                Text(
                    text = lines.takeLast(80).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogHeader(
    lineCount: Int,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Column {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "实时日志（$lineCount 行）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("分享")
            }
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("复制")
            }
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ── Small building blocks ──

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * @param warnOnly when the check fails this is amber (degraded) rather than red
 *                 (broken) — used for usage access, which is optional.
 */
@Composable
private fun StatusRow(
    ok: Boolean,
    label: String,
    value: String,
    warnOnly: Boolean = false
) {
    val tint = when {
        ok -> OkGreen
        warnOnly -> WarnAmber
        else -> MaterialTheme.colorScheme.error
    }
    val icon: ImageVector = when {
        ok -> Icons.Default.CheckCircle
        warnOnly -> Icons.Default.Warning
        else -> Icons.Default.Cancel
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = tint
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun logLineColor(line: String): Color = when {
    line.contains("INTERCEPT") -> OkGreen
    line.contains(" E/") -> MaterialTheme.colorScheme.error
    line.contains(" W/") -> WarnAmber
    line.contains("SKIP") -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

// ── Plain helpers (no Compose state) ──

/** "3 秒前" / "2 分钟前" — relative time is easier to read than a timestamp. */
private fun sinceText(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    val seconds = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        seconds < 60 -> "$seconds 秒前"
        seconds < 3600 -> "${seconds / 60} 分钟前"
        else -> "${seconds / 3600} 小时前"
    }
}

/**
 * Build the full text report: device info + every status signal + the log tail.
 * This is what gets copied / shared, so it must stand on its own.
 */
private fun buildReport(state: DiagnosticsState, logs: List<String>): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    return buildString {
        appendLine("=== Appause 诊断报告 ===")
        appendLine("时间: $stamp")
        appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) / ${BuildConfig.APPLICATION_ID}")
        appendLine("构建时间: ${BuildConfig.BUILD_TIME}")
        appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("--- 状态 ---")
        appendLine("结论: ${state.verdict}")
        appendLine("无障碍权限(系统): ${state.accessibilityEnabledInSettings}")
        appendLine("服务进程存活: ${state.serviceAlive} (connectedAt=${state.connectedAt})")
        appendLine("总开关: ${state.masterEnabled}")
        appendLine("使用情况权限: ${state.usageAccessGranted}")
        appendLine("悬浮窗权限(SYSTEM_ALERT_WINDOW): ${state.overlayPermissionGranted}")
        appendLine("事件数: ${state.eventCount}")
        appendLine("最近事件: ${state.lastEventPackage} @ ${state.lastEventAt}")
        appendLine("系统前台: ${state.foregroundPackage}")
        appendLine("最近判断: ${state.lastDecision}")
        appendLine("阻断界面方式: ${state.overlayResult ?: "无"}")
        appendLine("放行中: ${state.bypassed}")
        appendLine()
        appendLine("--- 分组 (${state.groups.size}) ---")
        state.groups.forEach { group ->
            appendLine("[${group.type}] ${group.name} cooldown=${group.cooldownSeconds}s apps=${group.packages.size}")
            group.packages.forEach { appendLine("    $it") }
        }
        appendLine()
        appendLine("--- 内存日志 (${logs.size} 行, 旧→新) ---")
        logs.forEach { appendLine(it) }
        appendLine()
        appendLine("--- 持久化服务日志 ---")
        appendLine(state.persistentLog.ifBlank { "（无）" })
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Appause diagnostics", text))
}

/**
 * Write the report to a cache file and hand it to the system share sheet, so the
 * tester can send it over QQ / email without touching adb. Uses FileProvider
 * because raw file:// URIs are blocked from Android 7 onwards.
 */
private fun shareReport(context: Context, text: String) {
    try {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, "appause-diagnostics.txt")
        file.writeText(text)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Appause diagnostics")
            putExtra(Intent.EXTRA_TEXT, "Appause 诊断报告（附件）")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "分享诊断报告"))
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun openAccessibilitySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
    }
}
