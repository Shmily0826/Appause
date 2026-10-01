"""R2 button scan: find where the pause overlay's Cancel / Continue really are.

The coordinate table in the stress-test skill was measured on an older build.
Rather than trusting it, this probe intercepts once and then taps candidate
points one at a time, watching logcat for the reaction after each tap:

  'Bypass started'                    -> that point is Continue
  launcher focus after the tap        -> that point is Cancel
  nothing                             -> that point hit dead space

Only coordinates are used — a uiautomator dump while the overlay is up would
destroy the a11y service and the pause screen with it.

Usage: python r2_button_scan.py [--points 540,1580 540,1713 ...]
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import campaign_lib as C  # noqa: E402
import device_lib as D  # noqa: E402

D.SERIAL = os.environ.get("APPAUSE_CAMPAIGN_SERIAL", "emulator-5554")

TARGET = "com.google.android.deskclock"
LAUNCHER_HINTS = ("launcher", "Launcher")


def fresh_intercept(cooldown_wait: float) -> bool:
    C.reset_appause()
    D.home()
    time.sleep(1.5)
    D.shell("logcat -c")
    D.launch(TARGET)
    end = time.time() + 20
    while time.time() < end:
        if D.overlay_present():
            time.sleep(cooldown_wait)
            return True
        time.sleep(0.5)
    return False


def reaction() -> str:
    log = D.shell(
        "logcat -d -v epoch -s InterceptionManager:V OverlayManager:V AppauseA11yService:V"
    )
    if "Bypass started" in log:
        return "CONTINUE (bypass started)"
    low = log.lower()
    if "overlay dismissed" in low or "overlay removed" in low:
        return "dismissed"
    return "no reaction"


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--points", nargs="*",
                   default=["540,1580", "540,1713", "540,1806", "540,1918"])
    p.add_argument("--wait", type=float, default=22.0,
                   help="seconds to wait out the cooldown before tapping")
    args = p.parse_args()

    if not fresh_intercept(args.wait):
        print("FAIL: no overlay after launch")
        return 1
    print("overlay is up; scanning tap points")

    for raw in args.points:
        x, y = (int(v) for v in raw.split(","))
        if not D.overlay_present():
            print("  -> overlay already gone, stopping")
            break
        D.tap(x, y)
        time.sleep(2.0)
        gone = not D.overlay_present()
        focus = D.focus_pkg()
        to_launcher = any(h in focus for h in LAUNCHER_HINTS)
        print(f"  ({x},{y}) -> gone={gone} focus={focus} launcher={to_launcher} | {reaction()}")
        if gone:
            print(f"  => that point behaves like {'CANCEL' if to_launcher else 'CONTINUE-or-pass'}")
            break

    return 0


if __name__ == "__main__":
    sys.exit(main())
