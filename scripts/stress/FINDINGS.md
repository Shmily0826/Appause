# Appause overnight reliability campaign — findings archive

Device: emulator Appause_Campaign_API34 (emulator-5554). NOTHING here is
physical-device (Xiaomi/HyperOS) validation.
Worktree: D:/CODE/project/Appause-campaign, branch campaign/overnight-reliability-20260919.

Classification key: A=product defect, B=harness defect, C=emulator-specific,
D=flake. Only A-class gets a fix.

## P0 baseline (run-20260919-110120) — PASS
S1–S17 + S12C, cooldown 20 s, 43 overlays shown. analyze.py verdict:
"no duplicate interception, no overlay leak, no blocked retry; every INTERCEPT
had a clean, dismissed predecessor." Full text: evidence/baseline-20260919/verdict.txt

## F-01 (B-class, fixed) setup_device.py not idempotent
Re-running setup after onboarding failed the "onboarding" check because the
app opens straight to home. Fixed: home markers checked first. 6/6 PASS on
rerun (evidence/setup-20260919-110025).

## F-02 (B-class, fixed) scenario_12 shadowing (pre-existing repo bug)
A duplicate `def scenario_12` silently shadowed the control-case variant, so
it never ran. Renamed to scenario_12_control and registered as S12C; it now
runs in the baseline (5 overlays attributed to S12C, all clean).

## F-03 (A-class CANDIDATE, confirming) false "Finish setup" blocking card
Symptom: immediately after `am force-stop` + system rebind of the
accessibility service, Appause home shows the RED blocking card
"Finish setup / Accessibility service — detect the foreground app"
(status = ACCESSIBILITY_NOT_ENABLED or SERVICE_NOT_CONNECTED) while:
  - Settings.Secure says the service is ENABLED (service_bound=True in probe),
  - interception demonstrably works (overlay attaches; earlier session).
Card STILL shown in screenshot taken after the rebind settled
(A0-afterbind-NO-CARD.png at 11:15, service_bound=True) — i.e. not merely a
transient during the bind window.
Root-cause hypothesis (code): AccessibilityHealthPolicy.derive() returns
SERVICE_NOT_CONNECTED whenever processState != CONNECTED even though
systemState == ENABLED; `_processState` only flips to CONNECTED inside
onServiceConnected, and HomeScreen treats any non-HEALTHY non-UNKNOWN status
as blocking-red. If the UI session starts before onServiceConnected lands, or
the flow never re-emits, the red card sticks although the system setting is
correct and interception works.
Evidence so far: evidence/probes-20260919/r1-health/A0-immediate-NO-CARD.png,
A0-afterbind-NO-CARD.png (screenshots prove BLOCKING; text oracle was too
early — fixed with polling in r1_health_repro.py).
Next: rerun probe A with the fixed oracle (3 rounds) + Diagnostics oracle +
PID/lifecycle logcat; if BLOCKING persists after rebind in >=2/3 rounds ->
confirm A-class and design minimal fix (fail-soft: with systemState ENABLED,
pending process evidence should render UNVERIFIED, not blocking-red).

## F-04 (watch) Diagnostics screen renders Chinese-only regardless of app language
Debug-flavor only; no end-user impact; not fixing without further evidence.

## F-05 (RESOLVED — see FINAL section below) Back key does not dismiss pause overlay
P1 J2: overlay shown for deskclock (2032), `input keyevent KEYCODE_BACK`,
+1.5 s → dumpsys still lists the appause 2032 window (FAIL screenshot +
window dump in evidence/probes-20260919/p1-journey/).
Code intends Back to dismiss: StandaloneOverlayHost.dispatchKeyEvent (legacy
KEYCODE_BACK), registerStandaloneBackCallback (OnBackInvokedDispatcher, and
manifest opts in with enableOnBackInvokedCallback=true), Compose
overlayBackModifier onPreviewKeyEvent, plus bridge.onBack wired in
PauseScreenContent. Window is focusable (only FLAG_NOT_TOUCH_MODAL).
Open questions for the runtime repro (single-shot, device idle):
  1. Does the overlay window actually hold input focus? (dumpsys input |
     grep mCurrentFocus while overlay shown)
  2. Any "Overlay dismissed"/cancel logcat line after keyevent?
  3. Retry with longer settle (3-5 s) to rule out dismiss latency.
If confirmed: A-class (escape-safety path dead on API 34 with predictive
back opt-in) — likely because findOnBackInvokedDispatcher() is queried
synchronously right after addView before the ViewRootImpl dispatcher is
attached, and there is no retry nor any log on the null branch.

## F-06 (C-class, harness/environment discovery) uiautomator dump rebinds the a11y service on this emulator
P2 chain logcat: every `uiautomator dump` process start is followed within
~25 ms by "AccessibilityService destroyed" and ~1 s later by a reconnect on
the SAME PID (11:24:03.5 -> destroyed 03.539 -> connected 04.606, etc.).
Because onDestroy dismisses a showing overlay, any harness wait_for/tap that
runs while the pause overlay is up makes the overlay vanish.
Explains: P2 LEAVE-HOLD-setup FAIL ("Continue button not found") and the
transient nature of SCREEN-overlay-tappable-after-wake FAIL.
Impact: NOT a product escape defect by itself (process death dismissal is by
design per AGENTS.md), but it (a) invalidates any overlay-interacting oracle
that uses uiautomator on this emulator, (b) is a live demo of the R1 concern:
a UI-test-like client can force rebinds; companion-static state survives
(same PID) and must not leave pauseShown/bypass guards stuck. J3-miss-13
("Bypass started: calendar" with no INTERCEPT) is the leading suspect for a
guard/bypass leak across a dump-triggered rebind -> dedicated repro pending.
Workaround for probes: use logcat INTERCEPT + dumpsys window (shell-only)
instead of uiautomator while the overlay matters.

---

# Chain-1 (verify-A..E, 11:28-11:36) outcome updates

## F-03 (UPDATE: A-class status UNDER RECHECK, likely B/C artifact)
verify-A text oracle labeled every round NO-CARD while screenshots showed the
red card -> the polling loop's uiautomator dumps themselves destroy+rebind
the service (F-06), and the screenshot is taken right after such a dump, i.e.
inside the genuine ~1 s DISCONNECTED window. The sticky-card claim therefore
needs one dump-free confirmation: f03_recheck.py (wait for the connect log,
screenshot at +4 s / +9 s after an OFF->ON rebind while Home is open).
Classification decision deferred to that result.

## F-05 (UPDATE, superseded by FINAL section at bottom) repro CONFIRMED 3/3, root cause narrowed
f05_back_repro.py verify-B: KEYCODE_BACK with overlay attached -> window
still attached after 5 s in 3/3 attempts. NEW runtime signal from back0
logcat: the Back keypress produced `AutofillManagerService.onBackKeyPressed`
+ a CLOSE transition of the DESKCLOCK task (home to front) while the appause
2032 window stayed attached -> the overlay window does not receive/route
Back at all; the key falls through to the app task below. Combined with code
review: `overlayHost.requestFocus()` is a no-op (plain FrameLayout is not
focusable-in-touch-mode), and registerStandaloneBackCallback has no retry /
no null-branch log. Next: instrument all three back paths with debug logs,
rebuild, rerun f05 with the fixed focus probe -> pin dispatcher-null vs
window-focus, then minimal fix + JVM regression test.

## F-07 (B-class, FIXED) P3 arm_pass raced the post-reset service re-register
verify-E: all three P3 probes died at `arm_pass: no intercept`; the saved
logcat shows 'AccessibilityService connected and running' ~20 ms AFTER the
monkey launch — the window event was lost, and re-monkeying an already
foreground app emits no new event. Fixed in p3_pass_expiry.py: arm_pass now
waits for the connect log, returns to Home, and retries up to 3x; main()
seeds its own 'P3Pass' group instead of relying on leftover groups.

## F-08 (B-class, FIXED) focus probe used a key that no longer exists on API 34
`dumpsys input | grep mCurrentFocus` always empty (format is now a
`FocusedWindows:` list) -> verify-B silently lost the focus signal.
f05_back_repro.py now reads `dumpsys window` mCurrentFocus AND the
FocusedWindows section of `dumpsys input`.

## J3-miss-13 (UPDATE: D-class flake, F-06-related)
verify-C reran the alternating matrix 12/12 rounds with zero misses. The
single calendar miss in the 30-round run matches the F-06 rebind window
("Bypass started" without INTERCEPT). Not product-fixable evidence; logged
as flake with known mechanism.

## LEAVE-HOLD (UPDATE: B/C environment, superseded by F-07 pattern)
verify-D failed identically ("Continue button not found"): same race as
F-07 but on the tap side — uiautomator dump during the tap sequence rebinds
the service and onDestroy dismisses the overlay. Needs the same
connect-wait + retry treatment inside p2_lifecycle_chaos.py before any
product conclusion can be drawn from LEAVE-HOLD.
## F-05 (FINAL: reclassified B-class harness timing artifact — no product fix; fix code reverted)
verify-N/O/P/Q/R/S closed the investigation on the API-34 emulator:

1. ROOT CAUSE (measured, verify-N + focus timing): the 2032 overlay window
   becomes the InputDispatcher focused window ~1.2-1.7 s AFTER addView
   (attached at +0.47 s, input focus at +1.73 s in the instrumented run).
   A KEYCODE_BACK injected before that instant is routed to the blocked app
   below — which is exactly what the original f05_back_repro did (it pressed
   Back ~0.5-1.5 s after attachment), so the "6/6 deterministic FAIL" was a
   harness timing artifact (B-class), not a steady-state product defect.
2. STEADY-STATE BEHAVIOUR IS CORRECT: once the overlay holds input focus,
   injected BACK fires the OnBackInvokedCallback (persistent log marker
   "backCB FIRED"), the overlay dismisses within 0.5 s, and this reproduces
   3/3 (verify-S on the reverted APK) in BOTH navigation modes
   (navigation_mode=0 and =2, verify-N A/B probe).
3. KEY-FILTER FIX ATTEMPT WAS INEFFECTIVE HERE (C-class data point):
   with XML flagRequestFilterKeyEvents compiled into the APK (verified via
   aapt dump: android:accessibilityFlags=0x20) AND runtime
   serviceInfo.flags=32 logged on every connect, `dumpsys accessibility`
   still reported capabilities=0 and AccessibilityService.onKeyEvent NEVER
   fired (0 markers across verify-L + verify-N). FLAG_REQUEST_FILTER_KEY_
   EVENTS therefore did not take effect on this emulator image; the
   onKeyEvent/activeBackBridge/XML/PauseActivity plumbing was fully reverted
   (OverlayKeyPressPolicyTest deleted with it). OverlayManager keeps only
   the F-05 diagnostic logging (backCB registered/FIRED/dispatcher=NULL,
   OverlayHost dispatchKeyEvent BACK), which is what produced this
   evidence chain.
4. HARNESS FIXES (B-class): f05_back_repro.py now (a) launches the target
   with explicit `am start -n` because `monkey -p <pkg> 1` silently stopped
   launching it after reinstalls (same failure family as F-07), (b) retries
   the home->target transition up to 3x to survive the post-reset rebind
   race, and (c) waits for real INPUT focus (dumpsys input FocusedWindows)
   before injecting Back, logging the focus latency it measures.
5. RESIDUAL PRODUCT NOTE (minor, emulator-measured): during the ~1.3 s
   focus-acquisition window a Back press goes to the blocked app instead of
   the overlay (app usually exits to home, which then dismisses the overlay
   via target-leave, so the user still escapes; countdown input is
   unaffected — touch goes to the overlay immediately). Not enough of a
   defect to change the window type/flags for; documented only.
6. REAL-DEVICE STATUS: NOT TESTED (no Xiaomi/HyperOS device in this
   campaign). Gesture-navigation Back and key-filter behaviour on HyperOS
   remain the top device-only verification item; the kept diagnostics
   (PersistentLog backCB markers) are exactly what a device session needs.

## F-09 (B-class, FIXED) foreground_package() silently dead on this image
`dumpsys window windows` no longer prints mCurrentFocus on this API-34 build
(grep count 0 — verified 00:53 verify-T), so the old command made
foreground_package() return '' for EVERY caller: expect_intercept's live-window
branch, expect_no_intercept, and the new launch_from_home confirmation all
degraded silently. Fixed in campaign_lib to grep the top-level `dumpsys window`
section instead. Evidence: manual step-through showing '' while deskclock was
visibly foreground.

## F-10 (B-class harness + C-class environment) overlay buttons unreachable via uiautomator; coordinate taps work
Two-part discovery while arming G6 sessions (verify-U/verify-V):
1. A `uiautomator dump` taken while the 2032 pause overlay is up returns the
   TARGET APP's tree (15 deskclock nodes, zero pause-screen nodes) and the
   overlay is gone immediately after — the dump kills the pause (F-06 family,
   C-class emulator behaviour). tap("Continue") can therefore never work here.
2. Replacement oracle that is dump-free and deterministic: wait for
   appause_overlay_attached(), sleep 1.0s (the first Compose frame lands AFTER
   window attach — a screenshot taken at attach time shows the target app
   still, overlay_shot.png), then `input tap` at fixed coordinates measured
   from a screencap (Continue = 540,1646 on this 1080x2340 AVD). The tap fired
   the real product handler: logcat 'Session start: com.google.android.deskclock'
   appeared, overlay detached, deskclock stayed foreground (bypass active).
   Cancel coordinate = 540,1788.
Consequence: p2_lifecycle_chaos LEAVE-*/SCREEN now use tap_overlay(); the
S-scenario taps that "passed" before were passing via absence-of-node
short-circuits, not real button hits — flagged for re-audit of any journey
that claims to have tapped an overlay button.

## F-11 (B/C-class, FIXED) coordinate tap at attach+1s reliably misses the Compose button
verify-V: SCREEN and FSTOP passed, but both LEAVE setups died with "tap at
(540,1646) produced no 'Session start' within 6s". Controlled A/B on the same
interception (00:06): tap 1.0s after appause_overlay_attached() -> no marker,
overlay stayed up; the SAME tap a few seconds later -> logcat
'Session start: com.google.android.deskclock', overlay detached. So the
F-10 recipe's 1.0s settle is not enough for the button to become
hit-testable on this slow AVD (first FRAME renders ~1s after attach, but
Compose touch slop/layout settles later). Not a product defect: a human
reaction time always exceeds this window. Fix: tap_overlay() now retries up
to 4 taps (2.0s then 1.5s apart) and re-checks attachment between attempts;
verified by the verify-W re-run of LEAVE-HOLD/LEAVE-EXPIRE.

## G6 result (emulator, verify-V + verify-W) leave-timer/reArm — PASS both sides
With tap_overlay retry in place, all four P2 lifecycle probes pass
deterministically on emulator-5554 (NOT real-device validation):
LEAVE-HOLD (return 20s into the 180s grace -> no re-intercept),
LEAVE-EXPIRE (return at grace+15s -> cooldown re-armed, pause shown again),
SCREEN (overlay survives off/on and stays tappable), FSTOP (interception
recovers after process death; "Session start" needed attempt 2, matching
F-11). Both LEAVE setups logged 'Leave cooldown started for' as expected —
the G6 service-level coverage gap named in the campaign brief is closed at
emulator level; HyperOS/OEM behaviour remains device-only.
