"""F-05 focused repro: does the hardware Back key dismiss the pause overlay?

Static analysis says Back SHOULD dismiss (three wiring paths exist in
OverlayManager/PauseScreenContent), yet P1/J2 saw the 2032 window survive
`input keyevent KEYCODE_BACK` for 1.5 s. This script decides between:
  A) real product defect on API 34 + enableOnBackInvokedCallback=true,
  B) harness oracle too early (dismiss latency),
  C) overlay never held input focus (key went to the target app instead).

Evidence collected per attempt:
  - `dumpsys input` mCurrentFocus while the overlay is shown (C?),
  - back press, then detachment polled at 0.5/1.5/3/5 s (B?),
  - unfiltered logcat window around the press (dismiss/cancel markers),
  - screenshot at the 5 s mark.
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

from campaign_lib import (
    Evidence,
    adb_shell,
    appause_overlay_attached,
    screenshot,
    logcat_clear,
    open_app,
    reset_appause,
    key,
    go_home,
    expect_intercept,
    adb,
)

TARGET = "com.google.android.deskclock"


def current_focus() -> str:
    # verify-B lesson: API 34 `dumpsys input` no longer has an `mCurrentFocus`
    # key (it prints a `FocusedWindows:` list), so the old grep always came
    # back empty and the focus signal was silently lost. Read both surfaces.
    win = adb_shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -3")
    inp = adb_shell("dumpsys input | sed -n '/FocusedWindows:/,+6p'")
    out = f"window:[{win.strip()}] input:[{inp.strip()}]"
    return out if win.strip() or inp.strip() else "(no focus line in window/input dumps)"


def overlay_shown() -> bool:
    # Deliberately avoids uiautomator-based wait_for(): on this emulator every
    # `uiautomator dump` transiently destroys+rebinds the a11y service (seen at
    # 11:24:03/11:24:22 in the P2 chain), which dismisses the overlay via
    # onDestroy and would destroy the exact window we want to test Back on.
    # logcat INTERCEPT + dumpsys window are shell-only and non-invasive.
    # verify-Q/R lesson: right after reset_appause the first am start can be
    # missed (service still rebinding / target already foreground from a prior
    # failed attempt, and a failed attempt returns before its go_home).
    # Retry a clean home→target transition up to 3 times.
    for _ in range(3):
        # verify-Q lesson: `monkey -p <pkg> 1` silently stopped launching the
        # target on this emulator, so the repro saw "overlay never shown".
        # Explicit am start is deterministic (used by f05_navmode_ab 6/6).
        go_home()
        time.sleep(1.5)
        adb_shell("am start -n com.google.android.deskclock/com.android.deskclock.DeskClock")
        if expect_intercept(TARGET, timeout=12):
            break
    else:
        return False
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        if appause_overlay_attached():
            return True
        time.sleep(0.3)
    return False


def wait_input_focus(timeout: float = 10.0) -> float | None:
    # verify-N lesson (F-05 root cause): the 2032 overlay window becomes the
    # InputDispatcher focused window only ~1.2-1.7 s AFTER addView returns.
    # A BACK injected before that instant is routed to the blocked app below,
    # which is why the original repro "failed" 6/6. Real steady-state Back is
    # handled by the overlay. Wait for actual input focus before testing.
    started = time.monotonic()
    while time.monotonic() - started < timeout:
        f = adb_shell("dumpsys input | sed -n '/FocusedWindows:/,+3p'")
        if "appause" in f:
            return time.monotonic() - started
        time.sleep(0.1)
    return None


def one_attempt(ev: Evidence, idx: int) -> None:
    tag = f"back{idx}"
    reset_appause()
    if not overlay_shown():
        ev.verdict(f"{tag}: overlay never shown", False)
        return
    focus_before = current_focus()
    ev.mark(f"{tag}: focus while overlay shown: {focus_before}")
    focus_lat = wait_input_focus()
    if focus_lat is None:
        ev.verdict(f"{tag}: overlay gained input focus", False,
                   "no focus within 10s")
        return
    ev.mark(f"{tag}: overlay gained INPUT focus after {focus_lat:.2f}s")
    attached_before = appause_overlay_attached()
    ev.mark(f"{tag}: dumpsys says attached={attached_before}")
    logcat_clear()
    key("KEYCODE_BACK")
    checkpoints = (0.5, 1.0, 1.5, 3.0, 5.0)
    gone_at = None
    next_cp = 0
    started = time.monotonic()
    while next_cp < len(checkpoints):
        elapsed = time.monotonic() - started
        if elapsed >= checkpoints[next_cp]:
            if not appause_overlay_attached():
                gone_at = checkpoints[next_cp]
                break
            ev.mark(f"{tag}: t={checkpoints[next_cp]}s still attached")
            next_cp += 1
        time.sleep(0.2)
    if gone_at is not None:
        ev.verdict(f"{tag}: back dismissed overlay", True, f"gone by {gone_at}s")
    else:
        ev.verdict(f"{tag}: back dismissed overlay", False, "still attached after 5s")
        screenshot(ev.dir / f"{tag}-still-attached.png")
        out = adb("logcat", "-d")
        (ev.dir / f"{tag}-logcat.txt").write_text(out, encoding="utf-8", errors="replace")
        ev.mark(f"{tag}: focus after back: {current_focus()}")
    go_home()
    time.sleep(2)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--attempts", type=int, default=3)
    parser.add_argument("--evidence", default="evidence/f05-back-repro")
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), f"f05-{time.strftime('%H%M%S')}")
    for i in range(args.attempts):
        one_attempt(ev, i)
    code = ev.finish()
    print("results:", ev.dir)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
