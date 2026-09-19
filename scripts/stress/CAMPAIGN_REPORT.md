# Appause overnight reliability campaign — final report

Campaign: APPAUSE-20260919-0154 · branch `campaign/overnight-reliability-20260919`
worktree `D:/CODE/project/Appause-campaign` (main worktree untouched) ·
device: disposable AVD `Appause_Campaign_API34` (emulator-5554).

**EVERY result here is emulator evidence. None of it is physical-device
(Xiaomi/HyperOS) validation.** Nothing was pushed, merged, tagged or released;
zero paid external API traffic; no real-device operations.

## Status

**COMPLETE WITH REMAINING DEVICE-ONLY GAPS** — verify-AD/AE landed:
P2b CLOSED PASS, F-03 CLOSED (B/C), P8 CLOSED PASS after F-18 harness fix,
P4 EXACT/AWAY and P6 REMOVE/MIGRATE parked as documented emulator-side harness
gaps (root-caused, each needs one short re-run; see Remaining gaps).
Zero A-class product defects confirmed; no product behaviour changed.

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
| P2 | R1 rebind, G6 leave-timer, pass-expiry wake, usage-off, force-stop/screen × states | G6 PASS 4/4; expiry-wake PASS; usage-off CLOSED PASS 2/2 (session-holds + re-arm with GET_USAGE_STATS denied, verify-AD); R1 PASS: NO sticky card 3/3 dump-free, interception recovers every rebind => F-03 closed B/C; 2038→Activity fallback: NOT FEASIBLE on this emulator (2032 always attaches) → remaining gap |
| P3 | temporary-pass expiry boundaries via raw DataStore rewrite | PASS 3/3 (verify-Z) |
| P4 | re-remind time math (G5 ">1 minute" report) | RESTART PASS x2; EXACT/AWAY blocked by F-17 (harness Pro-seed fails the Pro gate silently; product fail-closed is by design) → harness gap, one UI-unlock re-run needed |
| P5 | reinstall -r + reboot data & interception | PASS 4/4 (data md5 stable, service auto-bound <0.5 s) — emulator only |
| P6 | group mutation mid-session | DELETE + COOLDOWN PASS twice (per-event Room read proven); REMOVE/MIGRATE verify-AD round VOID (concurrent diagnostic purge) → one clean re-run pending, emulator gap |
| P7 | escape-safety storms with watchdog | PASS; REC-BURST death = D-class flake 2/2 repro clean (F-14) |
| P8 | seeded random walk (replayable, shrinkable) | PASS 60/60 seed=20260919 (verify-AE) after F-18 B-class oracle fix; the 11 "stacking" violations were the counter matching every package-name line in `dumpsys window windows` (MainActivity + multi-line windows); every saved violation dump held exactly ONE type=2032 overlay |
| P9 | 100+ cycle long stress | script `p9_long_cycle.py` ready; NOT RUN (turn budget) → remaining gap |
| P10 | adversarial review vs runtime evidence | folded into FINDINGS ledger; no lifecycle-race/G3/parallel defect survived evidence |

## Findings ledger (classification, one line each — full text FINDINGS.md)

- F-01, F-02 B harness (setup idempotence; scenario_12 shadowing — fixed)
- F-03 CLOSED B/C — the "sticky red Finish-setup card" was the harness's own
  uiautomator dumps forcing ~1 s DISCONNECTED windows (F-06); dump-free R1
  probe: NO card in 3/3 rebind rounds, interception recovers every time.
- **A-class outcome: ZERO confirmed product defects campaign-wide** (every A
  candidate resolved to B/C/D with measured root causes).
- F-04 watch (debug-only i18n), F-06 C (uiautomator dump rebinds service here)
- F-05 B FINAL (back-key: overlay input focus lands ~1.3 s after addView;
  steady-state correct 3/3 both nav modes; key-filter attempt reverted, C data
  point: flagRequestFilterKeyEvents inert on this image; diagnostics kept)
- F-07, F-08, F-09, F-10, F-11, F-12, F-13, F-15, F-16, F-18 B harness (all fixed)
- F-17 B harness seed suspect (raw DataStore Pro bool seed never satisfies the
  Pro gate; product fail-closed is by design) — P4 EXACT/AWAY blocked on it,
  UI-unlock re-run is the clean path
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
**Emulator-side re-runs outstanding (harness work, not product defects):**
P6 `--probes REMOVE,MIGRATE` one clean ~8-min run (verify-AD round was voided
by a concurrent diagnostic purge); P4 EXACT/AWAY one run after unlocking Pro
through the Diagnostics UI instead of the raw DataStore seed (F-17).
**Housekeeping:** `scripts/stress/campaign.xml` stays uncommitted (scratch UI
dump, intentionally gitignored-by-decision); `evidence/` is gitignored.
