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


def marker_count(pattern: str) -> int:
    out = adb_shell(f"logcat -d | grep -c '{pattern}'")
    try:
        return int(out.strip() or -1)
    except ValueError:
        return -1


def one_cycle(ev: Evidence, idx: int) -> bool:
    if not expect_intercept(TARGET_A, timeout=20):
        ev.mark(f"cycle {idx}: intercept MISSING (new failure mode — dumping state)")
        ev.dump_state(f"cycle{idx}.miss")
        screenshot(ev.dir / f"cycle{idx}.miss.png")
        return False
    if not tap_overlay(ev, CANCEL_XY, "Overlay dismissed"):
        ev.mark(f"cycle {idx}: Cancel tap never dismissed the overlay")
        screenshot(ev.dir / f"cycle{idx}.stuck.png")
        return False
    deadline = time.monotonic() + 6
    while time.monotonic() < deadline and appause_overlay_attached():
        time.sleep(0.5)
    if appause_overlay_attached():
        ev.mark(f"cycle {idx}: marker said dismissed but window lingers (leak)")
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
    ev.mark(f"baseline: pid={baseline_pid} pss={baseline_pss}KB")
    logcat_clear()  # marker counts below are then relative to this run
    ev.save_logcat("start")

    ok_cycles = 0
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
            ev.mark(f"cycle {idx}: ok={ok_cycles} pss={pss_kb()}KB "
                    f"shown={shown} dismissed={dismissed} "
                    f"elapsed={time.monotonic() - start:.0f}s last")
    final_pss = pss_kb()
    trend = (final_pss - baseline_pss) / baseline_pss * 100 if baseline_pss > 0 else 0
    ev.mark(f"final: {ok_cycles}/{args.cycles} cycles ok, pss {baseline_pss}->{final_pss}KB ({trend:+.0f}%)")
    fatal = adb_shell("logcat -d | grep -c 'FATAL EXCEPTION'").strip()
    anr = adb_shell("logcat -d | grep -c 'ANR in com.appause'").strip()
    ev.mark(f"final: FATAL={fatal} ANR={anr} bound={service_bound()} "
            f"overlay_over_launcher={appause_overlay_attached()}")
    ev.save_logcat("final")
    screenshot(ev.dir / "final.png")
    ev.verdict("LONG-CYCLE",
               ok_cycles == args.cycles
               and trend < 40
               and fatal == "0" and anr == "0"
               and service_bound()
               and not appause_overlay_attached(),
               f"{ok_cycles}/{args.cycles}, pss {trend:+.0f}%")
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
