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

## F-05 (A-class CANDIDATE, repro pending) Back key does not dismiss pause overlay
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
