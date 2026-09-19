"""REC-BURST FAIL triage: silent Appause process death + null focus?

verify-Z p7 REC-BURST failed with two markers (no focused window for 15s;
overlay still attached) and — reconstructed from logcat — the Appause process
restarted mid-check (pid 11981 -> 12696, 'AccessibilityService connected and
running' again) with NO 'FATAL EXCEPTION' and no ANR captured by the scenario.
This script isolates the repro and samples the full forensic state every 2s so
the death (if real) is caught with its system-side log lines:

  * pidof continuity          -> silent death = A-class candidate
  * top-level mCurrentFocus   -> null-focus window (overview animation?)
  * APPAUSE overlay windows   -> leak vs transient
  * system logcat lines       -> am_kill / 'has died' / 'Fatal signal' / ANR
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    adb,
    adb_shell,
    appause_overlay_attached,
    app_pid,
    expect_intercept,
    focused_window,
    go_home,
    key,
    reset_appause,
)
from p2_lifecycle_chaos import CANCEL_XY, TARGET_A, tap_overlay

DEATH_GREP = ("logcat -d | grep -iE 'appause' | "
              "grep -iE \"has died|Kill|killed|ANR|Fatal signal|FATAL|"
              "Start proc|lowmemory|dumpsched\" | tail -40")


def sample_forever(ev: Evidence, seconds: int, tag: str) -> None:
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        ev.mark(f"{tag} t+{int(seconds - (end - time.monotonic())):02d} "
                f"pid={app_pid() or 'GONE'} focus={focused_window() or 'NONE'} "
                f"ovl={appause_overlay_attached()}")
        time.sleep(2)


def one_run(ev: Evidence, idx: int) -> bool:
    if not expect_intercept(TARGET_A):
        ev.mark(f"RUN{idx}: initial intercept missing")
        return False
    pid0 = app_pid()
    for _ in range(8):
        key("KEYCODE_APP_SWITCH")
        time.sleep(0.5)
    sample_forever(ev, 30, f"RUN{idx}")
    pid1 = app_pid()
    ev.mark(f"RUN{idx}: pid {pid0} -> {pid1} (restart={'YES' if pid0 != pid1 else 'no'})")
    (ev.dir / f"run{idx}.deathlines.txt").write_text(adb_shell(DEATH_GREP), encoding="utf-8")
    (ev.dir / f"run{idx}.focus.txt").write_text(
        adb("shell", "dumpsys", "window") + "\n", encoding="utf-8")
    alive = pid0 != "" and pid1 != "" and pid0 == pid1
    if appause_overlay_attached():
        tap_overlay(ev, CANCEL_XY, "Overlay dismissed")
    go_home()
    return alive


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs", type=int, default=2)
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), "p7-rec-repro")
    assert reset_appause(), "service did not come up"
    results = [one_run(ev, i) for i in range(1, args.runs + 1)]  # no short-circuit
    stable = all(results)
    ev.verdict("REC-REPRO-NO-SILENT-DEATH", stable,
               "" if stable else "process died mid-check — inspect deathlines.txt")
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
