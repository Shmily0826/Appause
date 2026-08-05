package com.appause.android.ui.onboarding

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appause.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-launch onboarding screen.
 *
 * A short, skippable guide that walks a new user through four things that are
 * otherwise easy to miss:
 *   1. Pick a language (applied immediately, the rest of the app follows).
 *   2. What Appause does (one-line welcome).
 *   3. Enable the accessibility service (reusing the system settings Intent).
 *   4. Create the first group (deep-links to the existing group editor).
 *   5. Done — mark onboarding complete and enter the app.
 *
 * The whole flow is optional: "Skip" (top-right) finishes it at any point.
 *
 * Notes on the group step (page 3):
 * - Creating a group is NOT required. A "Later" button lets the user continue
 *   without one. When they do open the group editor, the step index is advanced
 *   to the finish page first, so returning lands on "All set" rather than
 *   restarting the guide.
 * - A small looping countdown is shown next to the group CTA as a gentle nudge;
 *   it is purely decorative and never blocks proceeding.
 */
@Composable
fun OnboardingScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToGroupEditor: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val page by viewModel.page
    val language by viewModel.language.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()

    // Re-query accessibility status every time the screen resumes (e.g. after the
    // user enables the service in system settings and comes back).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshServiceStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Each step gets a friendly icon so the guide feels visual, not text-heavy.
    val stepIcons = listOf(
        Icons.Default.Language,
        Icons.Default.Pause,
        Icons.Default.Accessibility,
        Icons.Default.GroupAdd,
        Icons.Default.CheckCircle
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Top bar: step dots + Skip ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PageDots(current = page, total = stepIcons.size)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = {
                viewModel.completeOnboarding()
                onNavigateToHome()
            }) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        // ── Content (scrollable, changes per page) ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Hero icon for the current step.
            StepHero(icon = stepIcons[page])
            Spacer(modifier = Modifier.height(16.dp))

            when (page) {
                0 -> LanguageStep(
                    current = language,
                    onPick = { code ->
                        scope.launch {
                            viewModel.applyLanguage(code)
                            // Recreate so attachBaseContext applies the new locale.
                            activity?.recreate()
                        }
                    }
                )
                1 -> InfoStep(
                    title = R.string.onboarding_welcome_title,
                    desc = R.string.onboarding_welcome_desc
                )
                2 -> ServiceStep(
                    isRunning = isServiceRunning,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                )
                3 -> GroupStep()
                4 -> InfoStep(
                    title = R.string.onboarding_finish_title,
                    desc = R.string.onboarding_finish_desc
                )
            }
        }

        // ── Bottom navigation ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0) {
                OutlinedButton(onClick = { viewModel.prevPage() }) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            when (page) {
                3 -> {
                    // Primary: open the existing group editor. We advance the step
                    // to "finish" first, so returning from the editor lands on the
                    // "All set" page instead of restarting the guide.
                    Button(onClick = {
                        viewModel.setPage(4)
                        onNavigateToGroupEditor()
                    }) {
                        Text(stringResource(R.string.onboarding_group_add))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Secondary: continue without creating a group (distinct from the
                    // top-right "Skip", which exits the whole guide).
                    TextButton(onClick = { viewModel.setPage(4) }) {
                        Text(stringResource(R.string.onboarding_group_later))
                    }
                }
                4 -> Button(onClick = {
                    viewModel.completeOnboarding()
                    onNavigateToHome()
                }) {
                    Text(stringResource(R.string.onboarding_finish))
                }
                else -> Button(onClick = { viewModel.nextPage() }) {
                    Text(stringResource(R.string.onboarding_next))
                }
            }
        }
    }
}

/** A row of small dots showing progress through the guide. */
@Composable
private fun PageDots(current: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            val selected = index == current
            Box(
                modifier = Modifier
                    .size(if (selected) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
            if (index < total - 1) Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

/** A 72dp tinted circle holding the step's icon — the visual anchor of each page. */
@Composable
private fun StepHero(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun LanguageStep(current: String, onPick: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.onboarding_language_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_language_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        LanguageOption(
            code = "zh",
            label = stringResource(R.string.onboarding_lang_zh),
            selected = current == "zh",
            onPick = onPick
        )
        Spacer(modifier = Modifier.height(12.dp))
        LanguageOption(
            code = "en",
            label = stringResource(R.string.onboarding_lang_en),
            selected = current == "en",
            onPick = onPick
        )
    }
}

@Composable
private fun LanguageOption(
    code: String,
    label: String,
    selected: Boolean,
    onPick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(code) },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (selected) {
            BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else {
            null
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun InfoStep(title: Int, desc: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Group step (page 3): the explanation plus a decorative, looping countdown
 * next to the call-to-action. The countdown is purely cosmetic — the buttons
 * in the bottom bar are always enabled.
 */
@Composable
private fun GroupStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        InfoStep(
            title = R.string.onboarding_group_title,
            desc = R.string.onboarding_group_desc
        )
        Spacer(modifier = Modifier.height(16.dp))
        GroupCountdownHint()
    }
}

/**
 * Loops 3 → 2 → 1 → 3 … as a gentle visual suggestion to add a group now.
 * It never blocks the user: the Continue/Later buttons stay fully clickable.
 */
@Composable
private fun GroupCountdownHint() {
    var remaining by remember { mutableStateOf(3) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            remaining = if (remaining <= 1) 3 else remaining - 1
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = "$remaining",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.onboarding_group_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ServiceStep(
    isRunning: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.onboarding_service_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_service_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        val statusColor = if (isRunning) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
        Text(
            text = if (isRunning) {
                stringResource(R.string.onboarding_service_on)
            } else {
                stringResource(R.string.onboarding_service_off)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_service_open))
        }
    }
}
