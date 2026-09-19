# Appause overnight reliability campaign — final report

Campaign: APPAUSE-20260919-0154 · branch `campaign/overnight-reliability-20260919`
worktree `D:/CODE/project/Appause-campaign` (main worktree untouched) ·
device: disposable AVD `Appause_Campaign_API34` (emulator-5554).

**EVERY result here is emulator evidence. None of it is physical-device
(Xiaomi/HyperOS) validation.** Nothing was pushed, merged, tagged or released;
zero paid external API traffic; no real-device operations.

## Status

**PENDING verify-AD** — set to one of
`COMPLETE WITH REMAINING DEVICE-ONLY GAPS` / other once the final chain lands
(P4/P6/P2b re-runs + R1/F-03 dump-free recheck + P8 random walk).

## Acceptance criteria mapping

| # | Criterion | Result |
|---|-----------|--------|
| 1 | Unattended P0 harness (reset → run → PASS/FAIL → evidence) | PASS — `setup_device.py`, `ui_stress.py` S1–S17+S12C, per-run `Evidence` dirs (actions.log, results.json, screenshots, logcat), all adb pinned to emulator-5554 (F-13) |
| 2 | S1–S17 baseline + P1–P4 high-value combos with records | PASS (see queue table; P4/P6/P2b final confirmation in verify-AD) |
| 3 | All findings classified A/B/C/D | PASS — F-01…F-16 in FINDINGS.md |
| 4 | A-class fixes with regression + full gates | See "A-class outcome" — currently ZERO confirmed A-class defects; F-03 decision owned by verify-AD R1 probe |
| 5 | Reviewable per-commit campaign branch | PASS — 27 commits, one task each |
| 6 | Main worktree untouched | PASS — all writes confined to campaign worktree; verified by git status there |
| 7 | Final report with remaining gaps + status | This file |

## Priority queue outcome

| Phase | Scope | Verdict (emulator) |
|-------|-------|--------------------|
| P0 | harness + S1–S17 regression baseline | PASS (run-20260919-110120: 43 overlays, no dup-intercept/leak/blocked-retry) |
| P1 | journey matrix: Back/Recents escape, Cancel→Home→reopen, multi-target×30, Settings round-trip | PASS after F-05 resolved as B-class timing artifact; steady-state back-dismiss works 3/3 in both nav modes |
| P2 | R1 rebind, G6 leave-timer, pass-expiry wake, usage-off, force-stop/screen × states | G6 PASS 4/4; expiry-wake PASS; usage-off session-holds PASS (rearm re-run in verify-AD); 2038→Activity fallback: NOT FEASIBLE on this emulator (2032 always attaches) → remaining gap; R1/F-03 verdict in verify-AD |
| P3 | temporary-pass expiry boundaries via raw DataStore rewrite | PASS 3/3 (verify-Z) |
| P4 | re-remind time math (G5 ">1 minute" report) | RESTART PASS; EXACT/AWAY re-run in verify-AD after F-16b purge fix |
| P5 | reinstall -r + reboot data & interception | PASS 4/4 (data md5 stable, service auto-bound <0.5 s) — emulator only |
| P6 | group mutation mid-session | DELETE + COOLDOWN PASS twice (per-event Room read proven); REMOVE/MIGRATE re-run in verify-AD |
| P7 | escape-safety storms with watchdog | PASS; REC-BURST death = D-class flake 2/2 repro clean (F-14) |
| P8 | seeded random walk (replayable, shrinkable) | verify-AD run seed=20260919 steps=60 |
| P9 | 100+ cycle long stress | script `p9_long_cycle.py` ready; NOT RUN (turn budget) → remaining gap |
| P10 | adversarial review vs runtime evidence | folded into FINDINGS ledger; no lifecycle-race/G3/parallel defect survived evidence |

## Findings ledger (classification, one line each — full text FINDINGS.md)

- F-01, F-02 B harness (setup idempotence; scenario_12 shadowing — fixed)
- F-03 **A-candidate UNDER RECHECK** — sticky red "Finish setup" card; chain-1
  evidence shows the screenshot was taken inside a genuine ~1 s DISCONNECTED
  window that the harness itself provoked (F-06); verify-AD R1 probe decides
  dump-free. If A → minimal fail-soft fix + gates (would be the only product fix).
- F-04 watch (debug-only i18n), F-06 C (uiautomator dump rebinds service here)
- F-05 B FINAL (back-key: overlay input focus lands ~1.3 s after addView;
  steady-state correct 3/3 both nav modes; key-filter attempt reverted, C data
  point: flagRequestFilterKeyEvents inert on this image; diagnostics kept)
- F-07, F-08, F-09, F-10, F-11, F-12, F-13, F-15, F-16 B harness (all fixed)
- F-14 D flake + oracle note · J3-miss-13 D flake

## Product-code delta on this branch

Only `7ee30f7`/`c5e1d1b`: persistent diagnostic logging on the three overlay
back paths (backCB registered/FIRED/dispatcher=NULL, dispatchKeyEvent BACK) —
this is what produced the F-05 evidence chain and is what a future HyperOS
session needs. Reverted: the onKeyEvent/XML flagPause key-filter experiment
(incl. its test) after it proved inert on this emulator. No behavioral product
change; no refactoring.

## Remaining gaps

**Xiaomi/HyperOS device-only (never reachable from this campaign):** overlay
visibility over anti-tamper apps (小红书 setHideOverlayWindows), boot-kill /
autostart policy, real-device a11y rebind behaviour, gesture-nav Back, and the
~1.3 s focus-acquisition window (F-05 note 5).
**Production-Worker-only:** real license JWT redeem (G3) — Pro state was
locally seeded via DataStore, which exercises the gate but not activation.
**Emulator-infeasible:** 2038 → PauseActivity fallback chain (2032 always
attaches here).
**Not run:** P9 long-running 100-cycle stress (script ready), Room migrations
2–5 (no schema JSONs exist).
**Housekeeping:** `scripts/stress/campaign.xml` stays uncommitted (scratch UI
dump, intentionally gitignored-by-decision); `evidence/` is gitignored.
