# Goal B readiness — HyperOS device safety acceptance (Phase A output)

Prepared 2026-09-19/20 without touching the phone. Everything here is the
plan + tooling for Phase B (auto) and Phase C (one manual batch).

## 1. What is already proven on THIS phone (do not re-run)

| Item | Verdict | Evidence |
|------|---------|----------|
| D1 2032 overlay over 小红书 anti-tamper | PASS | FINDINGS DEVICE session |
| D2 a11y rebind ×3 (UI flow) | PASS | no sticky card, recovery each time |
| D3 3-key Back escape | PASS 4/4 | backCB FIRED → dismiss → launcher |
| D3 gesture-mode edge-back | does NOT dismiss (by design no accidental escape; real-swipe verdict pending) | manual swipe + injection both no-op |
| D4 reboot survival | PASS | grant persists, autostart, PAUSE post-boot |
| D5 Room migrations | PASS | emulator + device chains |
| D6 90-min soak | PASS | 86 rounds, cooldown-exact decisions, no leak |
| P9/P10 (emulator) | PASS | 125 cycles + 5 journeys |

## 2. Remaining device gaps → Goal B scope

P0 escape safety (new value):
- G1 Home-key escape with overlay up, in BOTH nav modes (3-button / gesture) — never explicitly probed; overlay is a system-Z window, must not haunt the launcher.
- G2 Back during the F-05 focus window (~0–1.3 s after attach) on real device — emulator proved steady-state; device focus-window behavior untested.
- G3 Ghost/false re-interception on the launcher right after any escape (10 s no-trap watch).
- G4 Cancel tap Room-verified end-to-end on device (`app_launch_records.action='cancelled'`).
- G5 Injected edge-swipe must not half-trigger anything (regression guard for D3).

P1 real-app journeys (combinations D6 did not cover):
- J1 Cancel→re-enter re-intercept; J2 Continue→in-app back→Home→grace hold;
  J3 cross-app session isolation; J4 lock/sleep→wake→intercept works, pid
  stable; J5 temporary pass across reboot → 2 quiet entries → re-arm after
  expiry; J6 cooldown gate (Continue dead before 10 s, live after).

## 3. Tooling (all new files compile-verified)

- `device_lib.py` — serial-pinned (6036d5b, asserted at import), PersistentLog
  + dumpsys-window + screencap oracles, pull/edit/push+REBOOT injection path
  (never force-stop: it revokes the HyperOS a11y grant).
- `goalb_seed.py` — GB_ESC (xhs, 10 s) / GB_BILI (bili, 10 s) groups;
  temporary-pass DataStore edit; read-only Room peek.
- `d7_escape_safety.py` — probes P1–P8 above; every FAIL auto-dumps
  windows/focus/plog/screenshot; nav mode is always restored.
- `d8_real_app_journeys.py` — journeys J1–J6; `--continue-xy` must be
  calibrated by me from the first overlay screenshot during Phase B run.

Phase B order: `d7` (seeds + reboots once) → screenshot calibration of
Continue → `d8 --journeys 1,2,3,4,6,5` (J5 last: two reboots, clears its
pass and restores state).

## 4. Phase C — ONE manual batch (~8–12 min, user only performs actions)

Pre-staged: plog harvest + window dumps + screenshots run automatically
before/after each step; PASS/FAIL is computed from our oracles, not the user.

1. (gesture mode pre-set by us) open 小红书 → overlay shows →
   do a REAL left-edge back swipe ×3 → (we read: dismissed or not, any state damage)
2. overlay up → REAL home swipe-up ×3 → (we read: launcher clean, no ghost overlay)
3. overlay JUST appearing → instant home swipe ×2 (focus-window feel)
4. 3-button mode restored → overlay up → rapid Back+Home mash ×2
5. subjective: does the overlay ever feel stuck/janky? (one-word answers)

## 5. Safety & privacy checklist (binding for Phase B/C)

- [ ] every adb call via `device_lib.adb` (serial assert refuses other devices)
- [ ] NEVER: kill-server, uninstall, pm clear, force-stop, downgrade, touch
      `com.appause.android` (release) data — release is observe-only
- [ ] writes confined to the `.debug` slot; GB_* rows only; all other user
      groups/apps untouched; prefs edit replaces ONLY the temporary_passes key
- [ ] no notifications/content-provider/other-app reads; plog is our own file
- [ ] navigation_mode restored immediately after each probe; reboot leaves
      settings at their persistent values anyway
- [ ] at session end: GB_* groups + temp pass removed, debug a11y left ON as
      found, phone charged/on launcher, one final residual-window check

## 6. Exit criteria mapping

Acceptance #1/#2/#3 → d7 + d8 verdicts; #4 → FINDINGS entries per anomaly;
#5 → Phase D fixes (emulator-regressed first); #6 → results.json + evidence
dirs; #7 → checklist §5 + final state dump; #8 → GOAL_B_REPORT conclusion.
