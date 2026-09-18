"""F-03 recheck WITHOUT uiautomator dumps.

verify-A's red-card screenshots are suspect: every `uiautomator dump` rebinds
the a11y service (F-06) and onDestroy flips the process state to DISCONNECTED,
so a screenshot taken right after a dump sequence can capture a transient red
card even when the status heals within ~1 s.

This probe never calls uiautomator. Oracle = screenshots only, each taken
after the logcat 'connected and running' line proves the service is live plus
a settle delay.

  S1 steady-home:     reset -> wait connect -> open Home -> screenshot.
                      Expect HEALTHY look ("Service active").
  S2 rebind-while-home: a11y OFF -> ON -> wait connect -> settle 4 s ->
                      screenshot (A), +5 s screenshot (B).
                      If red persists here the sticky-card defect is real
                      (A-class); if healthy, F-03 was an F-06 artifact.

Read the PNGs manually — the script only records timing evidence.
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    go_home,
    logcat_clear,
    logcat_match,
    open_app,
    reset_appause,
    screenshot,
    start_service_via_settings,
)


def wait_connected(ev: Evidence, label: str, timeout: float = 25.0) -> bool:
    logcat_clear()
    line = logcat_match(r"AccessibilityService connected and running", timeout=timeout)
    ev.mark(f"{label}: connect-log {'SEEN' if line else 'MISSING'}")
    return bool(line)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), "f03-recheck")

    # S1: steady state after a normal reset (which rebinds via settings put).
    reset_appause()
    ok1 = wait_connected(ev, "S1")
    time.sleep(3)
    open_app(APPAUSE)
    time.sleep(2.5)
    screenshot(ev.dir / "S1-steady-home.png")
    ev.verdict("S1-connected", ok1)
    go_home()
    time.sleep(1)

    # S2: rebind (OFF->ON) while Home is open, no dumps anywhere.
    open_app(APPAUSE)
    time.sleep(2)
    screenshot(ev.dir / "S2-before-rebind.png")
    start_service_via_settings(False)
    time.sleep(2.5)
    start_service_via_settings(True)
    ok2 = wait_connected(ev, "S2-rebind")
    time.sleep(4)
    screenshot(ev.dir / "S2-afterbind+4s.png")
    time.sleep(5)
    screenshot(ev.dir / "S2-afterbind+9s.png")
    ev.verdict("S2-rebind-connected", ok2)
    go_home()
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
