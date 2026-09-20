"""D8: representative real-app journeys on the HyperOS phone (Phase B).

Six scripted journeys through the ACTUAL grouped apps (小红书 / B站创作) —
chosen for state INTERFERENCE value (session vs isolation, pass vs re-entry,
cooldown gating, lock/wake survival), not volume. D6 already proved 90 min
of raw switching; D4 proved reboot survival; we do NOT repeat either.

Continue taps need device coordinates that depend on the exact card layout
(temporary-pass row present?) -> pass --continue-xy x,y after calibrating on
the first captured overlay screenshot. Cancel is the known (536,1831).

J5 seeds a temporary pass through DataStore + reboot (the grant survives
reboot; force-stop would kill it). It CLEARS the pass and reboots again at
the end so the phone is left in its pre-goal state.
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

import device_lib as D
import goalb_seed as S
from d7_escape_safety import LAUNCHERS, CANCEL_XY, Run, arm_target, escaped

XHS, BILI = D.XHS, D.BILI
COOLDOWN = 30   # GB_* groups were re-seeded to 30s on-device (d7 night fix);
                # every wait that crosses the countdown must use this value.


def tap_cancel(run: Run) -> tuple[bool, str]:
    D.tap(*CANCEL_XY)
    return escaped(run)


def device_now_ms() -> int:
    return int(D.shell("date +%s").strip()) * 1000


def wait_session(run: Run, pkg: str, since_ms: int, timeout: float = 10) -> bool:
    """Session proof = a NEW Room launch record ('proceeded', timestamp after
    since_ms). 'Session start' lives only in logcat = invisible under MIUI,
    and a bare 'latest record' read would match the PREVIOUS proceed."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        local = S.D.pull(run.dir / "peek.db", S.D.DB_REL)
        S.D.pull(run.dir / "peek.db-wal", S.D.DB_REL + "-wal")
        import sqlite3
        con = sqlite3.connect(local)
        row = con.execute("SELECT action,timestamp FROM app_launch_records"
                          " WHERE packageName=? ORDER BY id DESC LIMIT 1;", (pkg,)).fetchone()
        con.close()
        if row and row[0] == "proceeded" and row[1] > since_ms:
            return True
        time.sleep(1.0)
    return False


def expect_quiet(run: Run, pkg: str, settle: float = 10) -> tuple[bool, str]:
    """Launch pkg from home and require NO interception within `settle`."""
    D.home(); time.sleep(1)
    D.launch(pkg)
    deadline = time.monotonic() + settle
    while time.monotonic() < deadline:
        if D.overlay_present():
            return False, "overlay appeared (unexpected intercept)"
        time.sleep(0.5)
    return D.focus_pkg() == pkg, f"focus={D.focus_pkg()}"


# ---------------------------------------------------------------------------
def j1_cancel_reenter(run: Run) -> None:
    ok = arm_target(run, XHS)
    if not ok:
        run.verdict("J1.cancel-reenter", False, "first intercept missing"); return
    esc, why = tap_cancel(run)
    time.sleep(12)  # cooldown 10s lapses
    again = arm_target(run, XHS)
    esc2, _ = tap_cancel(run) if again else (False, "")
    run.verdict("J1.cancel-reenter", esc and again and esc2,
                f"first_escape={esc} re_intercept={again} second_escape={esc2}")
    time.sleep(12)


def j2_continue_hold(run: Run) -> None:
    if not run.continue_xy:
        run.verdict("J2.continue-hold", False, "no --continue-xy (calibrate first)"); return
    if not arm_target(run, BILI):
        run.verdict("J2.continue-hold", False, "intercept missing"); return
    time.sleep(COOLDOWN + 2)  # Continue enables only after the countdown ends
    t0 = device_now_ms()
    D.tap(*run.continue_xy)
    if not wait_session(run, BILI, t0):
        run.verdict("J2.continue-hold", False, "session never started"); return
    D.back(); time.sleep(1.5)          # in-app back = normal navigation
    D.home(); time.sleep(2)
    quiet, why = expect_quiet(run, BILI, settle=8)  # inside 3-min grace: hold
    run.verdict("J2.continue-hold", quiet, f"session hold: {why}")
    time.sleep(12)


def j3_session_isolation(run: Run) -> None:
    # bili session may still be alive from J2 (well inside the 3-min grace)
    hit_xhs = arm_target(run, XHS)
    if not hit_xhs:
        run.verdict("J3.session-isolation", False, "xhs NOT intercepted while bili session held"); return
    esc, why = tap_cancel(run)
    quiet, why2 = expect_quiet(run, BILI, settle=8)  # bili must stay quiet
    run.verdict("J3.session-isolation", esc and quiet,
                f"xhs intercepted, bili quiet={quiet} ({why2})")
    time.sleep(12)


def j4_lock_wake(run: Run) -> None:
    pid0 = D.shell(f"pidof {D.DEBUG}").strip()
    D.home(); time.sleep(1)
    D.shell("input keyevent KEYCODE_SLEEP"); time.sleep(20)
    D.shell("input keyevent KEYCODE_WAKEUP"); time.sleep(2)
    D.shell("input swipe 540 2200 540 900 300"); time.sleep(2)  # dismiss keyguard
    focus = D.focus_pkg()
    if "keyguard" in focus or focus in ("",):
        run.verdict("J4.lock-wake", False, f"secure lockscreen blocks automation (focus={focus}) -> MANUAL")
        return
    hit = arm_target(run, XHS)
    pid1 = D.shell(f"pidof {D.DEBUG}").strip()
    if hit:
        tap_cancel(run)
    run.verdict("J4.lock-wake", hit and pid0 == pid1,
                f"intercept_after_wake={hit} pid_stable={pid0 == pid1}")
    time.sleep(12)


def j5_temp_pass_cycle(run: Run) -> None:
    """Pass active -> 2 entries quiet; after expiry -> re-intercept.
    F-24.4 lesson: adb kill is IGNORED on HyperOS, so the DataStore reload
    needs a real REBOOT — and the secure keyguard means the user must unlock
    once afterwards. Run this journey INSIDE the Phase C window (user nearby);
    it polls up to 8 min for the post-unlock service bind."""
    if not S.seed_temp_pass(run.dir, XHS, ttl_s=150):
        run.verdict("J5.temp-pass", False, "prefs seed failed"); return
    D.reboot_and_wait()
    run.mark("J5: rebooted — waiting for user unlock + service rebind (<=8 min)")
    deadline = time.monotonic() + 480
    while time.monotonic() < deadline and not D.service_running():
        time.sleep(5)
    if not D.service_running():
        run.verdict("J5.temp-pass", False, "no rebind within 8 min (user absent?)")
        return
    # F-25: reboot wiped the appops grants — restore before judging anything
    for op in ("GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW", "RUN_ANY_IN_BACKGROUND"):
        D.shell(f"appops set {D.DEBUG} {op} allow")
    D.shell(f"dumpsys deviceidle whitelist +{D.DEBUG}")
    time.sleep(3)
    q1, w1 = expect_quiet(run, XHS, settle=8)
    D.home(); time.sleep(2)
    q2, w2 = expect_quiet(run, XHS, settle=8)
    remaining = 150 - 30  # boot+grants+entries ate ~30s of the pass
    run.mark(f"J5: parking {remaining}s for natural expiry")
    time.sleep(max(remaining, 0) + 15)
    hit = arm_target(run, XHS)
    if hit:
        tap_cancel(run)
    run.verdict("J5.temp-pass", q1 and q2 and hit,
                f"quiet1={q1} quiet2={q2} re_arm_after_expiry={hit}")
    S.clear_temp_pass(run.dir)
    D.shell("reboot")
    run.mark("J5: pass cleared, rebooted again — unlock when convenient")
    time.sleep(60)


def j6_cooldown_gate(run: Run) -> None:
    """Continue must be DEAD before the 10s cooldown ends and LIVE after."""
    if not run.continue_xy:
        run.verdict("J6.cooldown-gate", False, "no --continue-xy"); return
    if not arm_target(run, BILI):
        run.verdict("J6.cooldown-gate", False, "intercept missing"); return
    time.sleep(3)
    t0 = device_now_ms()
    D.tap(*run.continue_xy)
    early = wait_session(run, BILI, t0, timeout=3)
    time.sleep(COOLDOWN - 3 + 5)  # now past the 30s cooldown
    t1 = device_now_ms()
    D.tap(*run.continue_xy)
    late = wait_session(run, BILI, t1, timeout=8)
    ok = (not early) and late
    run.verdict("J6.cooldown-gate", ok, f"early_start={early} late_start={late}")
    if late:
        D.home()
    time.sleep(12)


# ---------------------------------------------------------------------------
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--evidence", default=str(Path(__file__).parent / "evidence" / "goalB"))
    ap.add_argument("--continue-xy", default="", help="device px 'x,y' calibrated from screenshot")
    ap.add_argument("--journeys", default="1,2,3,6",
                    help="J4+J5 are MANUAL-window items (Phase C): J4 needs "
                         "real lock/unlock, J5 needs reboot+user unlock")
    args = ap.parse_args()
    D.assert_phone()
    run = Run(Path(args.evidence))
    if args.continue_xy:
        x, y = (int(v) for v in args.continue_xy.split(","))
        run.continue_xy = (x, y)
    else:
        run.continue_xy = None
    mapping = {1: j1_cancel_reenter, 2: j2_continue_hold, 3: j3_session_isolation,
               4: j4_lock_wake, 5: j5_temp_pass_cycle, 6: j6_cooldown_gate}
    for jid in args.journeys.split(","):
        fn = mapping.get(int(jid.strip()))
        if not fn:
            run.mark(f"unknown journey {jid}"); continue
        try:
            fn(run)
        except Exception as exc:
            run.mark(f"JOURNEY {jid} raised {type(exc).__name__}: {exc}")
            run.snapshot(f"journey{jid}.crash")
            run.verdict(f"journey-{jid}-crashed", False, str(exc))
    return run.finish()


if __name__ == "__main__":
    raise SystemExit(main())
