"""R2 render probe: does the pause overlay actually COMPOSITE on this device?

Window-state oracles can lie: WindowManager reports the 2032 overlay as added,
VISIBLE and focused, and it even receives input, while SurfaceFlinger has no
layer for it and both the screen and screencap show the target app untouched.
That state is "intercepted but invisible" — the user sees nothing and their
touches are eaten by an unseen window.

So this probe triples up:
  1. WindowManager: is a ty=2032 window attached?
  2. SurfaceFlinger: does that window have a layer?
  3. logcat: did OverlayManager report 'Overlay shown'?

PASS requires 1 AND 2 AND 3. A 2032 window with zero SF layers is the
invisible-overlay signature and must be reported as FAIL, never as 'shown'.

Usage: python r2_render_probe.py [--target com.google.android.deskclock]
                                 [--shot output/probe.png]
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import campaign_lib as C  # noqa: E402
import device_lib as D  # noqa: E402

# device_lib is pinned to the physical Xiaomi; this probe runs on the emulator.
D.SERIAL = __import__("os").environ.get("APPAUSE_CAMPAIGN_SERIAL", "emulator-5554")


def count(text: str) -> int:
    try:
        return int(text.strip().splitlines()[0])
    except (ValueError, IndexError):
        return 0


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--target", default="com.google.android.deskclock")
    p.add_argument("--shot", default="")
    args = p.parse_args()

    C.reset_appause()
    D.home()
    time.sleep(1.5)
    D.shell("logcat -c")
    D.launch(args.target)
    time.sleep(6)

    wm = count(D.shell("dumpsys window windows | grep -c ty=2032"))
    sf = count(D.shell("dumpsys SurfaceFlinger --list | grep -ci appause"))
    log = D.shell("logcat -d -v epoch -s OverlayManager:V")
    shown = "Overlay shown for" in log

    print(f"WM ty=2032 windows : {wm}")
    print(f"SF appause layers  : {sf}")
    print(f"logcat 'Overlay shown': {shown}")

    if args.shot:
        path = Path(args.shot)
        try:
            D.screenshot(path)
            print(f"screenshot         : {path}")
        except Exception as exc:  # noqa: BLE001 - evidence must never abort the verdict
            print(f"screenshot FAILED  : {exc}")

    ok = wm >= 1 and sf >= 1 and shown
    if wm >= 1 and sf == 0:
        print("VERDICT: FAIL — invisible overlay (window attached, no SurfaceFlinger layer)")
    elif ok:
        print("VERDICT: PASS — overlay composited")
    else:
        print("VERDICT: FAIL — overlay never attached")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
