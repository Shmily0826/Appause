"""P2: lifecycle & process chaos probes.

  LEAVE-HOLD   G6: intercept -> Continue -> session active -> Home -> return
               within the 3-min grace window -> must NOT re-intercept.
  LEAVE-EXPIRE G6: same, but return AFTER the grace window -> cooldown must
               re-arm (interception shows again).
  SCREEN       Pause overlay visible -> screen off -> screen on -> overlay
               must still be attached and tappable (no silent loss).
  FSTOP        Force-stop Appause while overlay is up -> rebind -> the next
               open of the target must intercept again (no zombie state).
  REBOOT       Device reboot with a seeded group -> after boot the service
               must be bound by the system setting alone and interception
               must work with no user interaction.

LEAVE-* use real waits (LEAVE_COOLDOWN_MS is a hardcoded 3-minute value in
the service; there is no injectable clock, and elapsedRealtime-based delays
cannot be accelerated from outside — wall-clock tricks are deliberately
avoided so results transfer to devices).
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    adb_shell,
    appause_overlay_attached,
    expect_intercept,
    go_home,
    key,
    logcat_clear,
    open_app,
    reset_appause,
    screenshot,
    service_bound,
    set_group_field,
    start_service_via_settings,
    tap,
    wait_for,
    wait_for_boot,
)

TARGET_A = "com.google.android.deskclock"
GRACE_SECONDS = 180  # LEAVE_COOLDOWN_MS in the service


def arm_session(ev: Evidence, tag: str) -> bool:
    """Intercept the target and tap Continue so a session becomes active."""
    logcat_clear()
    open_app(TARGET_A)
    if not expect_intercept(TARGET_A):
        ev.mark(f"{tag}: setup intercept missing")
        return False
    if not tap("Continue", timeout=5):
        ev.mark(f"{tag}: Continue button not found")
        return False
    time.sleep(1.5)
    return True


def probe_leave_hold(ev: Evidence) -> None:
    reset_appause()
    if not arm_session(ev, "LEAVE-HOLD"):
        ev.verdict("LEAVE-HOLD-setup", False)
        return
    go_home()
    time.sleep(20)  # well inside the 3-min grace window
    logcat_clear()
    open_app(TARGET_A)
    time.sleep(8)  # give a (wrong) re-intercept time to appear
    re_hit = appause_overlay_attached()
    ev.verdict("LEAVE-HOLD-return-in-grace-no-reintercept", not re_hit,
               "overlay re-appeared inside grace window" if re_hit else "")
    go_home()


def probe_leave_expire(ev: Evidence) -> None:
    reset_appause()
    if not arm_session(ev, "LEAVE-EXPIRE"):
        ev.verdict("LEAVE-EXPIRE-setup", False)
        return
    go_home()
    time.sleep(GRACE_SECONDS + 15)
    ev.verdict("LEAVE-EXPIRE-rearm-after-grace", bool(expect_intercept(TARGET_A, timeout=15)))
    go_home()


def probe_screen(ev: Evidence) -> None:
    reset_appause()
    logcat_clear()
    open_app(TARGET_A)
    if not expect_intercept(TARGET_A):
        ev.verdict("SCREEN-setup", False)
        return
    key("KEYCODE_POWER")  # screen off
    time.sleep(3)
    key("KEYCODE_POWER")  # screen on (no lockscreen on this AVD)
    time.sleep(2)
    key("KEYCODE_ENTER")  # dismiss keyguard if present
    time.sleep(1.5)
    still = appause_overlay_attached()
    ev.verdict("SCREEN-overlay-survives-off-on", still)
    if still:
        ev.verdict("SCREEN-overlay-tappable-after-wake",
                   bool(tap("Cancel", timeout=5)))
    go_home()


def probe_fstop(ev: Evidence) -> None:
    reset_appause()
    logcat_clear()
    open_app(TARGET_A)
    expect_intercept(TARGET_A)
    # Kill the whole app mid-overlay: the overlay window dies with the process.
    adb_shell("am force-stop " + APPAUSE)
    time.sleep(2)
    if appause_overlay_attached():
        ev.mark("FSTOP: overlay window survived force-stop (leaked?)")
        screenshot(ev.dir / "FSTOP-orphan.png")
    start_service_via_settings(True)
    time.sleep(3)
    go_home()
    # Short cooldown so the reopen re-intercepts deterministically.
    set_group_field("Campaign", "cooldownSeconds", "5")
    time.sleep(7)
    logcat_clear()
    open_app(TARGET_A)
    ok = bool(expect_intercept(TARGET_A, timeout=15))
    set_group_field("Campaign", "cooldownSeconds", "20")
    ev.verdict("FSTOP-intercept-recovers-after-kill", ok)
    go_home()


def probe_reboot(ev: Evidence) -> None:
    reset_appause()
    adb_shell("reboot")
    if not wait_for_boot(timeout=300):
        ev.verdict("REBOOT-boot", False)
        return
    time.sleep(25)  # let SystemServer bind accessibility services
    bound = service_bound()
    ev.mark(f"REBOOT: service bound after boot with no UI interaction: {bound}")
    logcat_clear()
    open_app(TARGET_A)
    ok = bound and bool(expect_intercept(TARGET_A, timeout=25))
    ev.verdict("REBOOT-interception-works-after-boot", ok)
    go_home()


PROBES = {
    "LEAVE-HOLD": probe_leave_hold,
    "LEAVE-EXPIRE": probe_leave_expire,
    "SCREEN": probe_screen,
    "FSTOP": probe_fstop,
    "REBOOT": probe_reboot,
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probes", default="LEAVE-HOLD,SCREEN,FSTOP")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), "p2-lifecycle")
    for name in args.probes.split(","):
        fn = PROBES.get(name.strip().upper())
        if fn:
            fn(ev)
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
