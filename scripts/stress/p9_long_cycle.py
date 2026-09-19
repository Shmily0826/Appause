"""P9: long-running intercept-cycle stress (default 30, built for 100+).

One cycle = launch target -> pause overlay appears -> tap Cancel -> overlay
must detach -> home -> short cooldown sleep. Cancel-only keeps every cycle
fast and deterministic (no Continue => no 3-min leave-grace involved).

Per-cycle monitors (the point is NEW failure modes, not raw count):
  * pause must appear within 20s, and must be GONE <=6s after a hit Cancel;
  * Appause PID — any change is logged as a crash/restart anomaly;
  * logcat FATAL/ANR greps at the boundaries, not every cycle (cheap).
Every 10th cycle additionally records TOTAL PSS (memory trend) and the
'Overlay shown for' / 'Overlay dismissed' marker counts (if shown-vs-dismiss
counts drift apart, timers or windows are leaking).

Final verdict: cycles PASS == requested count, PSS trend under +40% from
baseline, zero attached overlays over launcher, service still bound.
"""
from __future__ import annotations

import argparse
import re
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    adb_shell,
    appause_overlay_attached,
    app_pid,
    db,
    expect_intercept,
    go_home,
    logcat_clear,
    reset_appause,
    screenshot,
    seed_pause_group,
    service_bound,
)
from p2_lifecycle_chaos import CANCEL_XY, TARGET_A, tap_overlay


def pss_kb() -> int:
    out = adb_shell(f"dumpsys meminfo {APPAUSE} | grep 'TOTAL PSS'")
    m = re.search(r"(\d[\d,]*)", out)
    return int(m.group(1).replace(",", "")) if m else -1


def overlay2032_count() -> int:
    """F-18 lesson: count ONLY pause-overlay windows (type=2032), never raw
    grep -c lines — a window dumps its package on several lines and Appause's
    MainActivity also matches the package name."""
    out = adb_shell(
        "dumpsys window windows | grep -A1 'Window .*com\\.appause' | grep -c 'type=2032'"
    )
    try:
        return int(out.strip() or 0)
    except ValueError:
        return -1


def marker_count(pattern: str) -> int:
    out = adb_shell(f"logcat -d | grep -c '{pattern}'")
    try:
        return int(out.strip() or -1)
    except ValueError:
        return -1


# F-22 (B-class, this run): "Overlay dismissed" is logged by EVERY dismiss
# path — Cancel AND Continue AND temporary-pass. On this AVD the overlay
# renders the FREE layout (Cancel at y=1788), so the old candidate order
# [(1663=pro-Cancel), 1788] blind-tapped Continue on even cycles: a session
# started, the next launch was legitimately session-suppressed, and the
# harness logged that as "intercept MISSING". Cancel is now proven from
# Room (app_launch_records.action='cancelled'), never from the shared
# dismiss marker alone.
CANCEL_XY_FREE = (540, 1788)


def last_launch_action() -> str:
    out = db(
        f"SELECT action FROM app_launch_records WHERE packageName='{TARGET_A}'"
        " ORDER BY id DESC LIMIT 1;"
    )
    return out.strip()


def action_count(action: str) -> int:
    out = db(
        "SELECT COUNT(*) FROM app_launch_records"
        f" WHERE packageName='{TARGET_A}' AND action='{action}';"
    )
    try:
        return int(out.strip())
    except ValueError:
        return -1


def cancel_overlay(ev: Evidence, idx: int) -> bool:
    """Tap Cancel and PROVE via Room that the interception ended cancelled."""
    if not tap_overlay(ev, [CANCEL_XY_FREE], "Overlay dismissed"):
        ev.mark(f"cycle {idx}: Cancel tap never dismissed the overlay")
        screenshot(ev.dir / f"cycle{idx}.stuck.png")
        return False
    time.sleep(1.0)  # logLaunch writes on the IO dispatcher
    action = last_launch_action()
    if action == "cancelled":
        return True
    ev.mark(f"cycle {idx}: B-class mis-tap — overlay dismissed but Room says "
            f"action={action!r} (wrong layout at {CANCEL_XY_FREE}?); clearing state")
    reset_appause()  # drops any session started by the mis-tap
    if action == "proceeded":
        return False
    # no row at all: DB read/seed problem, also harness-side — fail the cycle
    return False


def one_cycle(ev: Evidence, idx: int) -> bool:
    if not expect_intercept(TARGET_A, timeout=20):
        ev.mark(f"cycle {idx}: intercept MISSING (new failure mode — dumping state)")
        ev.dump_state(f"cycle{idx}.miss")
        screenshot(ev.dir / f"cycle{idx}.miss.png")
        return False
    # expect_intercept just cleared logcat, so >1 shown-markers = one launch
    # produced several pauses (duplicate-interception monitor).
    shown = marker_count(f"Overlay shown for {TARGET_A}")
    if shown > 1:
        ev.mark(f"cycle {idx}: DUPLICATE interception ({shown} 'Overlay shown' lines from one launch)")
    if not cancel_overlay(ev, idx):
        return False
    deadline = time.monotonic() + 6
    while time.monotonic() < deadline and overlay2032_count() > 0:
        time.sleep(0.5)
    if overlay2032_count() > 0:
        ev.mark(f"cycle {idx}: marker said dismissed but 2032 window lingers (leak)")
        return False
    go_home()
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cycles", type=int, default=30)
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), "p9-longcycle")

    assert reset_appause(), "service did not come up — run setup_device.py first"
    seed_pause_group("P9Loop", [TARGET_A], cooldown_seconds=1)  # idempotent-ish churn
    baseline_pid = app_pid()
    baseline_pss = pss_kb()
    baseline_cancel = action_count("cancelled")
    ev.mark(f"baseline: pid={baseline_pid} pss={baseline_pss}KB cancelled_rows={baseline_cancel}")
    logcat_clear()  # marker counts below are then relative to this run
    ev.save_logcat("start")

    ok_cycles = 0
    warm_pss = -1
    consecutive_dead = 0  # cycles that stay dead even after reset+retry
    for idx in range(1, args.cycles + 1):
        start = time.monotonic()
        passed = False
        try:
            passed = one_cycle(ev, idx)
        except Exception as exc:  # keep the long run alive; the anomaly is the data
            ev.mark(f"cycle {idx}: watchdog caught {type(exc).__name__}: {exc}")
        if not passed:
            reset_appause()  # flake vs permanent breakage: retry ONCE after clean state
            retry = one_cycle(ev, idx)
            ev.mark(f"cycle {idx}: {'recovered after reset (D-class flake?)' if retry else 'DEAD after reset — investigate'}")
            passed = retry
        consecutive_dead = 0 if passed else consecutive_dead + 1
        if consecutive_dead >= 3:
            ev.mark(f"STOP at cycle {idx}: 3 consecutive cycles dead after reset — "
                    "harness drift or product breakage, not worth burning the run")
            ev.dump_state("stop")
            ev.save_logcat("stop")
            break
        if passed:
            ok_cycles += 1
        pid = app_pid()
        if pid != baseline_pid:
            ev.mark(f"cycle {idx}: PID CHANGED {baseline_pid} -> {pid} (restart/crash)")
            ev.save_logcat(f"cycle{idx}.crashlog")
            baseline_pid = pid
        if idx % 10 == 0:
            shown = marker_count("Overlay shown for")
            dismissed = marker_count("Overlay dismissed")
            if idx == 10:
                # F-23 trend gate: the cold-start baseline (measured right after
                # force-stop, before JIT/profile warmup) inflates the % by ~+50
                # with NO leak — plateau evidence from the 125-cycle run:
                # 74->114(c10)->124(c20)->113(c110)->112 MB. Compare the final
                # PSS against the WARMED cycle-10 value instead.
                warm_pss = pss_kb()
                ev.mark(f"cycle {idx}: warmed pss baseline={warm_pss}KB")
            ev.mark(f"cycle {idx}: ok={ok_cycles} pss={pss_kb()}KB "
                    f"shown={shown} dismissed={dismissed} "
                    f"elapsed={time.monotonic() - start:.0f}s last")
    final_pss = pss_kb()
    ref_pss = warm_pss if warm_pss > 0 else baseline_pss
    trend = (final_pss - ref_pss) / ref_pss * 100 if ref_pss > 0 else 0
    room_cancels = action_count("cancelled") - baseline_cancel
    ev.mark(f"final: {ok_cycles}/{args.cycles} cycles ok, room_cancelled={room_cancels}, "
            f"pss {baseline_pss}->{final_pss}KB ({trend:+.0f}%)")
    fatal = adb_shell("logcat -d | grep -c 'FATAL EXCEPTION'").strip()
    anr = adb_shell("logcat -d | grep -c 'ANR in com.appause'").strip()
    ev.mark(f"final: FATAL={fatal} ANR={anr} bound={service_bound()} "
            f"overlay_over_launcher={overlay2032_count()}")
    ev.save_logcat("final")
    screenshot(ev.dir / "final.png")
    ev.verdict("LONG-CYCLE",
               ok_cycles == args.cycles
               and room_cancels >= ok_cycles
               and trend < 40
               and fatal == "0" and anr == "0"
               and service_bound()
               and overlay2032_count() == 0,
               f"{ok_cycles}/{args.cycles}, pss {trend:+.0f}%")
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
