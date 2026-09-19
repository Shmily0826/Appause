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

## F-12 (B-class, FIXED) p3 seeded a DataStore file the real serializer rejects
verify-X and verify-Y (evidence/verify-X, evidence/verify-Y). Two independent harness bugs:
1. PREFS path: the file lives at
   `/data/data/com.appause.android.debug/files/datastore/settings.preferences_pb`
   (found via `run-as ls -R`); the old `.../datastore/...` path made every seed read 0 bytes
   (verified=False). Fixed in b84ab30.
2. Wire format: string-set PreferenceValue is proto field **6** (`string_set`), not 7
   (7 is `float`; verified against PreferencesProto classes in the 1.1.2 gradle artifacts).
   Encoding it as field 7 makes `PreferencesSerializer.addProtoEntryToPreferences` throw
   `CorruptionException: Value not set.` on every DataStore read. Symptom chain:
   `Error in handleForegroundChange for <pkg>` (E) on EVERY foreground event -> interception
   goes fully silent -> EXPIRED/WAKE FAIL and, worse, CLEAN-PASS was a FALSE PASS.
   The launcher-focused activity.txt in verify-Y was not a launch failure: the target WAS
   launched (logcat shows 'Event received: package=com.google.android.deskclock') but the
   decision crashed before showing any overlay.
Classification: B (harness). Product behaved acceptably: exception caught per-event, no
crash/ANR, service stayed alive; DataStore only self-repairs on the next app write.
Device state repaired by rewriting temporary_passes with field 6 and health-checking
interception (PASS). seed_pass now has a corruption sentinel (grep CorruptionException in
service logcat after restart) so a future bad seed can never produce a false PASS.
Real p3 verdicts come from verify-Z only.

## F-13 (B-class, FIXED) harness left adb serial unpinned; real phone connected mid-campaign
verify-AA (aborted+relaunched): at ~13:33 `adb devices` showed BOTH the emulator
and a physical device (6036d5b). Every campaign script invoked adb WITHOUT `-s`,
so all commands failed with "more than one device" (p5 snapshots came back empty;
repro assert-failed on reset_appause). No command executed on the phone — adb
refuses ambiguous targets outright — so no real-device operation occurred.
This also invalidated nothing earlier (verify-Z finished at 13:29, phone joined
after), but the campaign MUST be safe against this by construction: campaign_lib,
ui_stress and uinav now pin every adb/ sub-process call to
APPAUSE_CAMPAIGN_SERIAL (default emulator-5554). Commit b7813b8.

## F-14 (D-class flake + B-class oracle note) verify-Z REC-BURST FAIL does not reproduce
verify-Z p7 REC-BURST reported FAIL: after an 8x KEYCODE_APP_SWITCH storm the
harness saw "no focused window 15s", overlay attach-check flapped, and the
Appause pid changed 11981 -> 12696 with NO FATAL/ANR/"has died" in any dump —
an A-class "silent process death" candidate. Targeted repro (p7_rec_repro.py,
verify-AA, 2 runs): during the identical storm `mCurrentFocus` STAYS on the
Appause overlay window the whole time, `appause_overlay_attached()` stays True,
and the pid is stable 13081 -> 13081 in both runs; run{1,2}.deathlines.txt are
empty (zero Kill/ANR/Fatal/Start-proc matches). Verdict REC-REPRO-NO-SILENT-DEATH: PASS.
Classification: D-class flake for the process restart (unreproduced 2/2; the one
observed restart was followed by correct intercept+dismiss behavior anyway), plus a
B-class oracle limitation to remember: foreground_package() deliberately ignores
com.appause.android.debug, so while the overlay legitimately HOLDS focus during a
recents storm the "no focused window" timeout fires even though nothing is stuck.
A user-visible stuck state would need ovl=False AND no app-package focus; future
P7 verdicts must check that pair before calling FAIL. No product change.
Evidence: evidence/verify-AA/p7-rec-repro/ (actions.log, run*.focus.txt, results.json).

## P5 (persistence, emulator-only) closed: reinstall + reboot data + interception all PASS
verify-AA clean re-run (post-F-13): P5-reinstall-data PASS, P5-reinstall-interception
PASS (service auto-bound 0.2s after `install -r -t`), P5-reboot-data PASS (boot
completed 50s, prefs md5 identical 38bb4be0), P5-reboot-interception PASS (auto-bound
0.4s). 4/4 PASS. DataStore + Room survive same-versionCode reinstall and reboot on
API34 emulator; interception works again without manual rebind. This is emulator
evidence ONLY — HyperOS/MIUI may kill or defer the service at boot and must still be
verified on the physical device by the user. The 13:33–13:34 lines in the same log are
from the aborted unpinned-serial attempt (F-13) and are superseded.

---

# Chain2 (verify-AC/AD, 13:50-14:17) — P4/P6/P2b harness root-causes

## F-15 (B-class, FIXED) Pro unlock unreachable via UI tap; deterministic DataStore seed instead
Two stacked causes made every UI-path unlock fail in chain1:
1. The debug Pro button text is "Unlock (debug)"; p4's pattern was
   "Unlock Pro|解锁 Pro" and never matched (proof: scripts/stress/campaign.xml
   dump shows the real label). 
2. Even matching, the button sits below the fold of the Pro screen.
Fix: p3's verified raw-proto codec gained `set_bool()` (Value.boolean = field 3
`bool_value`, WIRE_TYPE_VARINT -> bytes `0x18 0x01`), and p4 `unlock_pro()` now
force-stops, seeds `pro_unlocked=true` directly into settings.preferences_pb,
restarts, and re-reads to verify + greps CorruptionException (F-12 sentinel).
Live-verified twice (13:54, 14:00 runs): `_has_bool` confirms and the re-remind
Pro gate at AppauseAccessibilityService.kt:1973 (`proState.isPro.first()`) opens.

## F-16 (B-class, FIXED x2) overlay geometry + stale-group pollution broke setup preconditions
a) **Pro-layout displacement + Continue-disabled-during-countdown.** With Pro
   seeded, the overlay gains reason chips + "Temporary pass", shifting Continue
   1646 -> ~1545 and Cancel 1788 -> ~1663 (the free-layout Cancel point hits
   "Temporary pass" on the Pro layout — hence Pro candidates FIRST). Chain2
   screenshots showed P6 REMOVE/MIGRATE setup FAIL: taps landed but NOTHING
   happened for the full 4-attempt window — because Continue stays DISABLED
   until the group countdown finishes (P6Main cooldown=20s), so 7s of retries
   can never succeed (DELETE's Cancel works mid-countdown; only Continue gates).
   CONTINUE_XY/CANCEL_XY are now candidate lists (Pro-first) and tap_overlay
   retries by wall-clock for 28s, confirming via the "Session start"/"Overlay
   dismissed" logcat marker (commit 62c5d24 + 11a4761).
b) **Stale-group misattribution.** Groups persist across batteries
   (reset_appause keeps app data); deskclock ended up in 7 simultaneous groups,
   so INTERCEPT lines were attributed to a reRemind=0 group -> P4 "no CLOCK
   START" despite "Session start" logging fine, and any cooldown oracle could
   read the wrong group. Fix: `purge_all_groups()` (DELETE group_apps +
   app_groups — only campaign rows exist on this AVD) at the start of P4/P6/P2b
   batteries (11a4761).
c) **P2b foreground no-op.** USAGEOFF-rearm FAILED and the USAGEON control
   FAILED identically (=> not usage-access dependence, B-class per the script's
   own design); "Leave cooldown started" NONE in both arms was the tell that
   the leave transition never registered: expect_no_intercept RE-LAUNCHES the
   target, leaving it foreground, so the post-grace monkey launch no-ops
   (F-07). Fix: go_home() after the session-holds check (11a4761).
   USAGEOFF-session-holds PASS stays valid (bypass across Home works with
   GET_USAGE_STATS denied).
## Chain2 verdict ledger (pre-fix, superseded where re-run)
P4: PRO-UNLOCK PASS(seed), EXACT FAIL(F-16b), AWAY FAIL(F-16b), RESTART PASS.
P6: DELETE PASS, COOLDOWN PASS, REMOVE setup FAIL(F-16a), MIGRATE setup FAIL(F-16a).
P2b: session-holds PASS, rearm FAIL(F-16c), USAGEON-control FAIL(confirms B).
verify-AD chain (P4+P6 REMOVE/MIGRATE + P2b rerun, then R1 probes A/B/C for the
F-03 sticky-card dump-free recheck, then P8 random walk seed=20260919 steps=60)
runs under evidence/verify-AD/; results append below when it completes.

## F-17 (P4 EXACT/AAY STILL FAIL AFTER PURGE — root-cause chain, B-class seed suspect)
verify-AD p4 re-run: EXACT/AWAY still "no Re-remind line" even with purge +
correct group attribution (logcat proves INTERCEPT group=P4ReRemind cooldown=5s,
DB row proves reRemindMinutes=1). Standalone diagnostics (evidence/verify-AD/p4x):
Session start fires, but NO "Scheduling re-remind loop" at all ->
ReRemindSchedulePolicy.request() returned null -> proStatus != UNLOCKED (fails
CLOSED silently by design: minutes>0 && proStatus==UNLOCKED required).
Ruled out: override store (shared_prefs has ONLY appause_locale_prefs.xml, no
appause_debug_activation.xml -> None); DEBUG entitlement path requires
settings.isProDebug==true, i.e. DataStore boolean "pro_unlocked".
=> Prime suspect: the seeded 0x18-varint entry is NOT the shape DataStore's
PreferenceData uses for `boolean` (our own parser reading it back proves
nothing about androidx's schema), and/or the exception inside
runCatching{isPro.first()} is silently swallowed -> UNKNOWN -> fail-closed.
The F-12 CorruptionException sentinel CANNOT see this (runCatching eats it).
This is a HARNESS seed defect at worst (B); the product behaved as designed.
CAUTION: p4x + a home-dump during the verify-AD chain may have polluted the
chain's P6-REMOVE round (purged mid-flight) — that round is void if it FAILs;
a clean P6 REMOVE/MIGRATE re-run or a remaining-gap note closes it.
Device-only next step deferred: UI unlock path ("Unlock (debug)" label +
scroll) is the authoritative seed alternative.

## verify-AD chain results (appended when bcuivopmb completes)
P2b (post-F-16c fix): FULL PASS 2/2 with usage access DENIED — session-holds
across Home AND re-arm after the 180 s grace (leave-timer line "Leave cooldown
started for deskclock (180s)" now logged; go_home-after-holds restored the
launch oracle). The TEST_REPORT "usage-access mismatch" question is CLOSED:
grace/session bookkeeping does not depend on usage stats. (emulator only)
P6 REMOVE setup FAIL + MIGRATE FAIL(line=None): VOID rounds — my concurrent
p4x diagnostic purged P6Main/P6Alt mid-probe (lesson recorded in F-17 CAUTION:
never touch device state while a chain runs). DELETE+COOLDOWN remain proven
2/2 across batteries. REMOVE/MIGRATE: no clean verdict yet -> remaining gap
(one rerun of `p6_group_mutation.py --probes REMOVE,MIGRATE` needed, ~8 min).
P4 EXACT/AWAY: still FAIL, root cause = F-17 (seed fail-closed at the Pro
gate), NOT a product timing defect; RESTART PASS again.
R1 (F-03 dump-free recheck): F-03 CLOSED as B/C artifact — with the fixed
polling oracle, Home shows NO red card at immediate+afterbind in ALL 3 OFF->ON
rebind rounds (A0..A2), rebind-recovers PASS 3/3, and interception works
after every rebind (C0/C1 PASS). The earlier "sticky red card" screenshots
were captured inside the genuine ~1 s DISCONNECTED window that the harness's
own uiautomator dumps provoked (F-06). Zero A-class product defects confirmed
campaign-wide.
## F-18 (B-class, FIXED) P8 overlay_count oracle over-counted -> phantom "stacking"
verify-AD: 11/60 walk steps flagged "3|5 appause windows attached (stacking)".
Triage: EVERY saved violation dump (step2/19/28/29...) contained exactly ONE
appause window header, type=2032, different hash per step (overlays rotate,
never stack), and the walk never crashed or ANRed. Root cause: the oracle was
`dumpsys window windows | grep -c 'com.appause'` — grep -c counts LINES, and
a window dumps its package name on several lines (header + mActivityRecord/
surface lines), plus Appause's own MainActivity window after any 'settings'
step (step 1 was settings). Fixed to count only appause windows whose token
line carries type=2032. Replay with the SAME seed 20260919, 60 steps:
PASS 60/60 (verify-AE). Conclusion: no overlay-stacking product defect;
P8 CLOSED. (emulator-only evidence)

## F-17 CLOSED + F-19 (B-class, harness; with one product-fragility NOTE) verify-AE
Closed the two remaining emulator-side gaps in one chain:
- UI unlock works: debug Pro page "Unlock (debug)" button (label differs from
  strings.xml; found live via uiautomator at 540,1490 after scrolling) calls
  settings.setProUnlocked(true) through DataStore itself; state line flipped
  to "Effective: REAL (licensed state: DEBUG)".
- F-19, root cause of F-17: launching Appause with the raw-seeded
  "pro_unlocked" entry produced FATAL `ClassCastException: Integer cannot be
  cast to Boolean` in SettingsDataStore's map (crash buffer 15:22) — the p3
  set_bool wire shape is read back by androidx as an INT, so (a) the Pro gate
  never saw true (fail-closed by design), and (b) the mismatched type CRASHES
  the app on every launch. Both sides are harness-caused (only a raw writer
  can produce this), classified B. PRODUCT NOTE (not fixed, per scope rules):
  a malformed-type preference entry is not defended against in the
  SettingsDataStore flow map — unreachable for real users, but worth knowing;
  the corrupt entry was surgically dropped from the proto rather than pm
  clear. F-17 seed path in p4 stays available but --unlock ui is the
  authoritative mode from now on.
- F-20 (B, fixed): p_exact polled `logcat -d` (default MM-DD format) while
  ts() requires epoch seconds -> start/fired always None -> "no CLOCK START"
  no matter what; fixed with `logcat -d -v epoch`.
Results (verify-AE-p4ui / verify-AE-p6, emulator only):
P4 EXACT PASS continue->pop delta = 55.0s (design 60-5s; G5 time math OK);
P4 AWAY PASS x2 (re-checking-soon tick + pop within 15s of return);
P4 RESTART was already PASS x2. P4 fully CLOSED.
P6 REMOVE PASS (no intercept after mid-session removal) and
P6 MIGRATE PASS (new group's cooldown=3s used on re-attribution) -> all four
P6 probes green. Remaining A-class candidates: NONE.

## DEVICE session (Xiaohongshu/HyperOS, 2026-09-19 PM) — D1..D4 real-device results
Device: Xiaomi 2410DPN6CC, Android 16 HyperOS, serial 6036d5b, 3-key nav.
Debug APK (build 95, campaign HEAD) installed alongside release. Group
DEVTEST_XHS (cooldown 300s, reRemind off) seeded via pull->host sqlite3->push
(device sandbox denies sqlite3). All evidence below is REAL-DEVICE, distinct
from every emulator row in this file.
- D1 PASS: pause overlay (type=2032, owner com.appause.android.debug) renders
  fully over 小红书 despite its setHideOverlayWindows anti-tamper: window is
  #1 in Z-order, mCurrentFocus=overlay, screenshot shows icon+countdown+CTA
  (EVIDENCE_D1_overlay_over_xhs.png). Cancel -> overlay removed, focus=launcher.
- D2 PASS: 3x HyperOS OFF->ON rebind through the UI dialogs (Turn-off confirm +
  Danger checkbox + countdown OK). After each rebind: service RUNNING, events
  flowing, interception recovered (overlays at 16:37/17:06). No sticky red
  card: home card tracked Finish setup -> Setup complete -> Service active
  correctly; the transient "usage access pending" was REAL (appops showed
  GET_USAGE_STATS back to default after the toggle cycles), i.e. honest
  reporting, not F-03/R1 recurrence. (emulator-side F-03 stays CLOSED; this is
  the device-side verdict.)
- D3 PASS (3-key): 4/4 BACK presses on the live overlay logged `backCB FIRED`
  -> overlay dismissed -> focus=launcher (16:26:40, 16:39:56, 17:23:53, plus
  the 16:53:06 trial). PASS (gesture nav): with navigation_mode=2, edge-swipe
  back does NOT dismiss the overlay (user manual swipe + adb injection both
  no-op; no backCB FIRED) -> no accidental escape path. NOTE: adb `input
  swipe/motionevent` cannot trigger system edge gestures on this device
  (needs developer-option "USB debugging (Security settings)"), so the
  gesture verdict rests on the user's manual swipe.
- D4 PASS: `adb reboot` -> boot_completed in ~30s; enabled_accessibility_services
  SURVIVED reboot (UI-granted persists; adb-written does not); process
  auto-started (pid 12092) with AccessibilityService.onCreate +
  onServiceConnected SETUP OK + startForeground OK before any user launch;
  events flowed; PAUSE decision observed 17:33:24 post-boot. HyperOS kill
  policy did NOT block autostart for an a11y service with battery-unrestricted.
- Device-environment findings (harness knowledge, not product defects):
  1. `am force-stop <pkg>` silently REMOVES the pkg's a11y grant on HyperOS
     (settings rewritten; must re-enable via UI). Never force-stop between
     probes; prefer pull/edit/push with the app killed only when re-granting.
  2. MIUI suppresses third-party logcat entirely -> PersistentLog
     files/appause-service.log is the reliable oracle.
  3. `uiautomator dump` -> "null root node" (Security-settings toggle off);
     drive via screencap + taps (image 900x1956 -> device x1.2).
  4. PersistentLog file TRIMS oldest lines -> count-based oracles unreliable;
     use tail or dumpsys window presence instead.
  5. Two "Overlay shown" log lines ~90ms apart appeared twice (17:06:38,
     17:22:42) while visually one overlay — event+poller double-show race,
     cosmetic only (B, same window reused; no stacking seen in P8 or Z-order).
  6. A temporary pass got granted mid-navigation (tap landed on Continue after
     countdown layout shift) -> "SKIP: temporary pass active" decisions are
     correct behavior; wait out the 5-min pass before re-arm probes.

## F-21 (B-class harness, FIXED) attribution of the 14:39 "Appause Debug keeps stopping" dump

Context: at close-out the worktree had a dirty `scripts/stress/campaign.xml`
(mtime 14:39, device-session hours) showing the crash dialog
"Appause Debug keeps stopping" over the debug app home screen — previously
unattributable because the DEVICE reboot wiped its crash buffer.

Attribution (DEVICE evidence vs EMULATOR evidence kept separate):
- Real phone (6036d5b): `dumpsys dropbox` has ZERO appause entries for
  2026-09-19 — the device never crashed during D1..D4. Today's device
  tombstones are all com.apkpure.aegon (unrelated). The only appause
  data_app_crash records on device are 09-17/18 v92 StatsViewModel (already
  fixed in 0.5.42).
- Emulator (emulator-5554): dropbox data_app_crash at 14:28, 14:30, 14:31,
  14:32, 14:33 and 15:22, process com.appause.android.debug v95, stack
  `ClassCastException: Integer cannot be cast to Boolean` at
  SettingsDataStore map$13 (pro_unlocked typed read) — the exact F-19
  signature. campaign.xml is a campaign_lib dump (default serial
  emulator-5554) of THAT screen.

Root cause (code-level): p3_pass_expiry.set_bool encoded Value.boolean as
oneof field 3 (tag 0x18). The app's own writes prove it is field 1 (tag
0x08, e.g. `12 02 08 01`). Field 3 deserializes as an Int32 wrapper, so the
next typed read crashed the process. p4._has_bool checked the same wrong tag,
so the byte-level sentinel passed while the app crashed (the F-12 sentinel
only covered CorruptionException = parse failure, not CCE = typed-read
mismatch). This is the mechanism behind F-19; the seed path still carried it.

Fix (harness only, no product code): set_bool + _has_bool now use tag 0x08.
Verification (EMULATOR):
1. Byte-level: fixed set_bool(cur,"pro_unlocked",True) == app-written file
   bytes exactly; False round-trip reads back via _has_bool. PASS
2. E2E: seed False -> cold start -> alive, no new appause-crash.log entry,
   prefs intact; seed True -> cold start -> same. PASS (Pro left unlocked)
3. py_compile p3+p4. PASS
Device: NOT applicable (never crashed). campaign.xml left dirty in the
worktree as the original evidence; not committed.

## DEVICE session #2 (2026-09-19 evening, TASK APPAUSE-20260919-1835) — D5 upgrade/migrations + D6 soak

### D5 Room-migration upgrade test — CLOSED (previously "untestable")

Premise corrections found during execution:
- GitHub release APKs cannot cover migrations 1_2..3_4: the oldest tagged
  release (v0.3.9) already ships DB version 4. Only 4_5 and 5_6 are
  reachable by real users.
- The device's RELEASE app is already 0.5.43 (= main versionCode 95) and
  cannot be downgraded for an upgrade test: release builds are not
  debuggable (no run-as backup path), so user data would be at risk.
  => pivoted to the DEBUG package (disposable data, run-as available).
- Historical debug builds use applicationId com.appause.android (no
  ".debug" suffix existed before 0.5.x); built them with a temporary
  applicationId patch to install side-by-side with the user's release.
- 0ce84ba (DB1) is data-layer-only: its UI never opens Room, so no DB1
  artifact is producible by running the app -> chain starts at v2 (DB2).
- bc1113d (the DB3->4 bump commit) does not compile (HomeViewModel
  isRunning unresolved); tag v0.3.14 (DB4) used as the DB4 representative.

EMULATOR rehearsal (EMULATOR evidence, not device):
- Full install chain v2->v3->v4->v5->v6->v95main on emulator-5554: every
  hop PASS — user_version advances 2->3->4->5->6->6, seeded group
  D5CHAIN_GRP + member survive, PRAGMA integrity_check ok, app alive.

DEVICE execution (DEVICE evidence, Xiaomi 2410DPN6CC / HyperOS):
- HyperOS revokes USB-install permission after EACH successful install
  (INSTALL_FAILED_USER_RESTRICTED on the next), so a 7-install chain is
  impractical. Redesigned as ONE install + DB-file injection:
- Installed v95main (build 95 debug) once; then:
  - Scenario A: pushed seeded DB2 (user_version=2, D5CHAIN_GRP present)
    via run-as, cold launch -> device ran migrations 2->3->4->5->6.
    RESULT PASS: user_version=6, group+member survived, integrity ok,
    app alive.
  - Scenario B: pushed seeded DB4 -> device ran 4->5->6 (the only path
    real users can hit). RESULT PASS: same checks.
- D5c post-migration behavior: seeded DEVTEST_XHS group
  (cooldownSeconds=300) into the migrated DB; debug a11y granted via UI;
  live interception of com.xingin.xhs confirmed on device (pause card
  with 297 s countdown rendered over rednote; Cancel -> launcher).
  Release a11y temporarily disabled to keep the soak measurement clean
  (restored at session end per task spec).
- Room schema evidence: post-migration app_groups carries all v6 columns
  (type/reRemind* with defaults) and rows read back correctly.

### D6 90-minute soak (DEVICE evidence) — results appended below when done
