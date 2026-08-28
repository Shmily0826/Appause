package com.appause.android.interception

import com.appause.android.data.local.AppGroup

/**
 * The outcome of running the foreground-event filter chain, BEFORE any side
 * effect is executed. The AccessibilityService maps each decision to the exact
 * same effects the original inline code performed (timers, bypass changes,
 * overlay calls, diagnostics writes).
 *
 * These decision types + [InterceptionDecider] are the characterization seam
 * for the interception logic: every historical regression (v0.5.11–v0.5.27)
 * maps to one of these outcomes, so each can be pinned by a unit test without
 * an emulator.
 */
sealed class PreGroupDecision {

    /**
     * Message the service passes to decide()/decideTarget(). Null when the
     * original code logged WITHOUT updating the Diagnostics "last decision"
     * field (steps 2.6 and the abandon path) — those carry their own log line
     * instead. Keeping the exact strings matters: the Diagnostics screen and
     * saved feedback reports show them verbatim.
     */
    abstract val diagnosticsReason: String?

    /**
     * True when the original flow reached step 3.5 (a real, non-system app
     * became foreground, which clears the just-cancelled suppression). The
     * service uses this flag to run that clear for exactly the decisions that
     * passed the system gate — nothing else.
     */
    abstract val passedSystemGate: Boolean

    /** Appause is turned off in settings. */
    object SkipDisabled : PreGroupDecision() {
        override val diagnosticsReason = "SKIP: Appause is disabled"
        override val passedSystemGate = false
    }

    /** The event is for Appause itself. */
    data class SkipSelf(val packageName: String) : PreGroupDecision() {
        override val diagnosticsReason = "SKIP: Appause itself"
        override val passedSystemGate = false
    }

    /**
     * Stale window event for the app the user just cancelled out of (fires in
     * the brief moment before the launcher takes over the screen).
     */
    data class SkipStaleCancelled(val packageName: String) : PreGroupDecision() {
        override val diagnosticsReason = "SKIP: stale event for just-cancelled app ($packageName)"
        override val passedSystemGate = false
    }

    /**
     * Duplicate of the current foreground (poller echo / in-app Activity
     * switch) while no pause is on screen. The original code logged this
     * WITHOUT writing lastDecision, so it carries a logLine, not a reason.
     */
    data class SkipDedup(
        val packageName: String,
        val lastForegroundPackage: String?,
        val previousEventPackage: String?
    ) : PreGroupDecision() {
        override val diagnosticsReason: String? = null
        override val passedSystemGate = false
        val logLine = "2.6 dedup skip: $packageName (lastFg=$lastForegroundPackage, prevEvent=$previousEventPackage)"
    }

    /** Launcher / system UI / always-ignored system component. */
    data class SkipSystem(val packageName: String) : PreGroupDecision() {
        override val diagnosticsReason = "SKIP: system package ($packageName)"
        override val passedSystemGate = false
    }

    /**
     * The user returned to a bypassed app within the 3-minute leave window —
     * let them continue without a new cooldown.
     */
    data class Resume(val packageName: String) : PreGroupDecision() {
        override val diagnosticsReason = "RESUME: $packageName (returned within leave window)"
        override val passedSystemGate = true
    }

    /**
     * A different real app became foreground while the cooldown was showing
     * and the user never tapped Continue — the cooldown was abandoned. The
     * service dismisses the overlay and releases the guard (the HyperOS
     * hidden-but-attached-overlay fix).
     */
    data class AbandonCooldown(val targetPackage: String, val userWentTo: String) : PreGroupDecision() {
        override val diagnosticsReason: String? = null
        override val passedSystemGate = true
        val warnLine =
            "Cooldown abandoned (user left to $userWentTo before continuing) — dismissing overlay to release guard"
    }

    /** A cooldown is on screen and this event is not an abandonment. */
    data class SkipPauseShown(val packageName: String) : PreGroupDecision() {
        override val diagnosticsReason = "SKIP: cooldown overlay is showing ($packageName)"
        override val passedSystemGate = true
    }

    /** In-app Activity switch caught by the second (step 5) duplicate guard. */
    data class SkipDuplicate(val packageName: String) : PreGroupDecision() {
        override val diagnosticsReason = "SKIP: duplicate event ($packageName)"
        override val passedSystemGate = true
    }

    /** All pre-group checks passed — the service may now look up the group. */
    object ProceedToGroupLookup : PreGroupDecision() {
        override val diagnosticsReason: String? = null
        override val passedSystemGate = true
    }
}

/** The final outcome after the group lookup and the burst/state guards. */
sealed class PostGroupDecision {
    abstract val diagnosticsReason: String

    /** The app is not in any configured group. */
    data class SkipNoGroup(val packageName: String) : PostGroupDecision() {
        override val diagnosticsReason = "SKIP: not in any group ($packageName)"
    }

    /**
     * The event is a HyperOS/MIUI recents replay (a tight burst of window
     * events for every cached task), not a genuine open.
     */
    data class SkipBurstReplay(
        val packageName: String,
        val burstRealPackages: Set<String>
    ) : PostGroupDecision() {
        // Renders the set the same way the original inline code did
        // ("SKIP: recents replay (burst=[a, b, c])").
        override val diagnosticsReason = "SKIP: recents replay (burst=$burstRealPackages)"
    }

    /** The pause guard or bypass state changed while the group was loading. */
    data class SkipStateChanged(val packageName: String) : PostGroupDecision() {
        override val diagnosticsReason = "SKIP: state changed while intercepting ($packageName)"
    }

    /** Genuine open of a grouped app — show the cooldown. */
    data class Intercept(
        val packageName: String,
        val groupName: String,
        val cooldownSeconds: Int
    ) : PostGroupDecision() {
        override val diagnosticsReason =
            "INTERCEPT: $packageName → group=$groupName, cooldown=${cooldownSeconds}s"
    }
}

/**
 * InterceptionDecider — the pure decision layer for foreground events.
 *
 * It is a faithful, behavior-preserving extraction of the filter chain that
 * used to live inline in AppauseAccessibilityService.handleForegroundChange()
 * (steps 1–6.6). It performs NO side effects: it only answers "given this
 * snapshot of the state, what should happen?". The service still owns every
 * effect (timers, bypass set, overlay, diagnostics fields).
 *
 * The split is two-stage because the original code suspends BETWEEN the
 * dedup/guard checks and the group lookup (a Room query), and re-reads the
 * pause guard afterwards (step 6.6). Keeping two calls preserves those exact
 * read points:
 *  - decidePreGroup runs before the group query,
 *  - decidePostGroup runs after it, with freshly-read guard/bypass values.
 *
 * pauseShown is passed as a function (not a Boolean) because the original
 * guard property has a staleness watchdog that can RELEASE the guard when
 * read. Evaluating it lazily at the same logical points as the inline code
 * kept that watchdog firing at the same moments.
 */
object InterceptionDecider {

    /**
     * Inputs for the pre-group stage. All plain snapshot values except
     * [pauseShown], which is read lazily (see class docs).
     */
    data class PreGroupInput(
        val packageName: String,
        /** Package seen in the PREVIOUS handleForegroundChange call (event or poller). */
        val previousEventPackage: String?,
        val lastForegroundPackage: String?,
        val justCancelledPackage: String?,
        val isEnabled: Boolean,
        val isOwnPackage: Boolean,
        val isSystemPackage: Boolean,
        /** True only for a resolved launcher package, not generic system noise. */
        val isHomePackage: Boolean,
        /** UsageStats confirmation that the launcher is actually foreground. */
        val isHomeForegroundConfirmed: Boolean,
        /** Whether the candidate is currently bypassed. */
        val isBypassed: Boolean,
        /** Read lazily at the exact points the inline code read the guard. */
        val pauseShown: () -> Boolean,
        val pauseTargetPackage: String?,
        /** Whether the current pause target (if any) is bypassed. */
        val isPauseTargetBypassed: Boolean
    )

    /** Inputs for the post-group stage (after the Room query suspension). */
    data class PostGroupInput(
        val packageName: String,
        val group: AppGroup?,
        val burstSuppressed: Boolean,
        val burstRealPackages: Set<String>,
        val pauseShown: () -> Boolean,
        val isBypassed: Boolean
    )

    /**
     * Steps 1–5 of the original chain, in the original order. Do not reorder
     * the checks: several of them shadow each other deliberately (e.g. the
     * 2.6 dedup only fires when no pause is on screen).
     */
    fun decidePreGroup(input: PreGroupInput): PreGroupDecision {
        val pkg = input.packageName

        // 1. Appause disabled.
        if (!input.isEnabled) return PreGroupDecision.SkipDisabled

        // 2. Appause itself.
        if (input.isOwnPackage) return PreGroupDecision.SkipSelf(pkg)

        // 2.5. Stale event for the just-cancelled app.
        if (input.justCancelledPackage != null && pkg == input.justCancelledPackage
            && input.lastForegroundPackage == input.justCancelledPackage
        ) {
            return PreGroupDecision.SkipStaleCancelled(pkg)
        }

        // 2.6. Poller/duplicate dedup — only when the foreground package has
        // not changed, the previous event was ALSO this app, and no pause is
        // on screen. The short-circuit order (lastFg check reads the guard
        // second) mirrors the original expression exactly.
        if (pkg == input.lastForegroundPackage && !input.pauseShown() && pkg == input.previousEventPackage) {
            return PreGroupDecision.SkipDedup(pkg, input.lastForegroundPackage, input.previousEventPackage)
        }

        // A confirmed launcher transition is a genuine way to abandon an
        // active cooldown. Check it before broad system filtering so Home does
        // not leave a 2032 overlay over the launcher. The foreground check
        // prevents incidental OEM noise from dismissing a valid overlay.
        if (input.isHomePackage && input.isHomeForegroundConfirmed && input.pauseShown()) {
            val target = input.pauseTargetPackage
            if (target != null && pkg != target) {
                return PreGroupDecision.AbandonCooldown(targetPackage = target, userWentTo = pkg)
            }
        }

        // 3. System package (launcher, system UI, always-ignored components).
        if (input.isSystemPackage) return PreGroupDecision.SkipSystem(pkg)

        // (Step 3.5 — clearing the cancel suppression — is signalled via
        // passedSystemGate and executed by the service.)

        // 4. RESUME within the leave window.
        if (input.isBypassed) return PreGroupDecision.Resume(pkg)

        // 4.5. A pause is on screen: abandonment or skip.
        if (input.pauseShown()) {
            val target = input.pauseTargetPackage
            if (target != null && pkg != target && !input.isPauseTargetBypassed) {
                return PreGroupDecision.AbandonCooldown(targetPackage = target, userWentTo = pkg)
            }
            return PreGroupDecision.SkipPauseShown(pkg)
        }

        // 5. Second duplicate guard (in-app Activity switch).
        if (pkg == input.lastForegroundPackage && pkg == input.previousEventPackage) {
            return PreGroupDecision.SkipDuplicate(pkg)
        }

        // Fall through to the group lookup.
        return PreGroupDecision.ProceedToGroupLookup
    }

    /** Steps 6–6.6 of the original chain (after the group query). */
    fun decidePostGroup(input: PostGroupInput): PostGroupDecision {
        val pkg = input.packageName

        // 6. Not in any configured group.
        val group = input.group ?: return PostGroupDecision.SkipNoGroup(pkg)

        // 6.5. Recents-replay burst suppression.
        if (input.burstSuppressed) {
            return PostGroupDecision.SkipBurstReplay(pkg, input.burstRealPackages)
        }

        // 6.6. State changed while the group was loading.
        if (input.pauseShown() || input.isBypassed) {
            return PostGroupDecision.SkipStateChanged(pkg)
        }

        // 7. Intercept.
        return PostGroupDecision.Intercept(pkg, group.name, group.cooldownSeconds)
    }
}
