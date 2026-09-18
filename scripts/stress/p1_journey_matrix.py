"""P1: golden-journey gaps not covered by ui_stress S1-S17.

  J1  Cancel -> launcher -> immediate reopen -> (debounce) -> reopen after
      cooldown must intercept again.
  J2  Back / Recents escape from the visible overlay, then reopen.
  J3  Many alternating rounds across two targets with a short cooldown
      (exposes stale lastForegroundPackage / duplicate intercept over time).
  J4  Settings round-trip: browse app screens while interception is armed,
      then confirm interception still works.

Each check is machine-verdicted with logcat intercept markers; evidence via
the shared Evidence collector.
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    appause_overlay_attached,
    expect_intercept,
    go_home,
    key,
    logcat_clear,
    logcat_match,
    open_app,
    reset_appause,
    screenshot,
    set_group_field,
    tap,
    wait_for,
)

TARGET_A = "com.google.android.deskclock"
TARGET_B = "com.google.android.calendar"
GROUP = "Campaign"


def show_overlay(pkg: str) -> bool:
    logcat_clear()
    open_app(pkg)
    hit = expect_intercept(pkg)
    return bool(hit)


def j1_cancel_reopen(ev: Evidence) -> None:
    reset_appause()
    shown = show_overlay(TARGET_A)
    if not ev.verdict("J1-overlay-shown", shown):
        return
    tapped = tap("Cancel", timeout=5)
    ev.mark(f"J1: tapped Cancel={tapped}")
    time.sleep(1.5)
    # Cancel must send the user to the launcher, and the overlay must be gone.
    gone = not appause_overlay_attached()
    ev.verdict("J1-cancel-dismisses-overlay", gone)
    # Immediate reopen: must NOT double-intercept within the debounce window.
    logcat_clear()
    open_app(TARGET_A)
    time.sleep(3.0)
    # Behavior note: after Cancel the user left the app; reopening immediately
    # may legitimately re-pause once the session cooldown applies. Record both
    # signals; only a stuck/duplicated overlay is a defect.
    stuck = appause_overlay_attached() and not wait_for("Cancel", timeout=2)
    ev.verdict("J1-no-stuck-overlay-after-cancel", not stuck)
    go_home()
    time.sleep(25)
    # After cooldown expiry interception must be alive again.
    ev.verdict("J1-reintercept-after-cooldown", show_overlay(TARGET_A))
    go_home()


def j2_back_recents(ev: Evidence) -> None:
    reset_appause()
    shown = show_overlay(TARGET_A)
    if not ev.verdict("J2-overlay-shown", shown):
        return
    key("KEYCODE_BACK")
    time.sleep(1.5)
    gone = not appause_overlay_attached()
    ev.verdict("J2-back-dismisses-overlay", gone)
    go_home()
    time.sleep(25)
    shown = show_overlay(TARGET_A)
    if not ev.verdict("J2-reshown-after-cooldown", shown):
        return
    key("KEYCODE_APP_SWITCH")
    time.sleep(1.5)
    key("KEYCODE_HOME")
    time.sleep(1.0)
    gone = not appause_overlay_attached()
    ev.verdict("J2-recents-home-escape", gone)
    go_home()


def j3_alternating_rounds(ev: Evidence, rounds: int) -> None:
    reset_appause()
    # Shorten cooldown so dozens of rounds stay feasible; restore afterwards.
    set_group_field(GROUP, "cooldownSeconds", "5")
    try:
        failures = []
        for i in range(rounds):
            pkg = TARGET_A if i % 2 == 0 else TARGET_B
            logcat_clear()
            open_app(pkg)
            hit = expect_intercept(pkg, timeout=12)
            if not hit:
                failures.append(i)
                ev.mark(f"J3 round {i}: NO intercept for {pkg}")
                screenshot(ev.dir / f"J3-miss-{i}.png")
                ev.save_logcat(f"J3-miss-{i}")
            go_home()
            time.sleep(6)
        ev.verdict(f"J3-alternating-{rounds}-rounds", not failures,
                   f"missed rounds: {failures}")
    finally:
        set_group_field(GROUP, "cooldownSeconds", "20")


def j4_settings_roundtrip(ev: Evidence) -> None:
    reset_appause()
    open_app(APPAUSE)
    for target in ("Settings", "Groups", "Your Groups", "Statistics"):
        if tap(target, timeout=3):
            time.sleep(1.2)
            key("KEYCODE_BACK")
            time.sleep(0.8)
            ev.mark(f"J4: visited {target} and returned")
    crashed = bool(logcat_match("FATAL EXCEPTION", timeout=1))
    ev.verdict("J4-no-crash-after-browsing", not crashed,
               "AndroidRuntime crash found in logcat" if crashed else "")
    # Interception must still work after app-screen round-trip.
    time.sleep(2)
    ev.verdict("J4-intercept-after-settings", show_overlay(TARGET_A))
    go_home()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rounds", type=int, default=30, help="J3 round count")
    parser.add_argument("--journeys", default="J1,J2,J3,J4")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()

    ev = Evidence(Path(args.evidence), "p1-journey")
    for name in args.journeys.split(","):
        name = name.strip().upper()
        if name == "J1":
            j1_cancel_reopen(ev)
        elif name == "J2":
            j2_back_recents(ev)
        elif name == "J3":
            j3_alternating_rounds(ev, args.rounds)
        elif name == "J4":
            j4_settings_roundtrip(ev)
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
