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
    foreground_package,
    go_home,
    key,
    launch_from_home,
    logcat_clear,
    logcat_match,
    reset_appause,
    screenshot,
    service_bound,
    set_group_field,
    start_service_via_settings,
    wait_for_boot,
)

TARGET_A = "com.google.android.deskclock"
GRACE_SECONDS = 180  # LEAVE_COOLDOWN_MS in the service

# verify-V lesson: uiautomator dumps never see the 2032 overlay content and
# transiently kill the service (F-06), so overlay buttons must be tapped at
# fixed coordinates. Geometry below is for this 1080x2340 AVD only.
# verify-AC lesson: with Pro unlocked the overlay gains reason chips +
# "Temporary pass", shifting Continue/Cancel UP ~100px (see MUT-REMOVE
# screenshot) — so each button carries a candidate list, tried in order.
# Pro layout FIRST: pro_unlocked now persists on this AVD, and the free-layout
# Cancel position (1788) would hit "Temporary pass" on the Pro layout.
CONTINUE_XY = [(540, 1545), (540, 1646)]
CANCEL_XY = [(540, 1663), (540, 1788)]


def tap_overlay(ev: Evidence, xy, marker: str) -> bool:
    """Wait for the overlay to actually render, then blind-tap a button.

    verify-V lesson: a single tap 1s after window attach reliably MISSES on
    this emulator — the Compose button is not hit-testable yet (proven A/B:
    same tap a few seconds later fires the marker). Retry until the logcat
    marker lands; a real user's reaction time always exceeds this window.
    `xy` is a list of candidate points (free vs Pro overlay layouts).
    """
    points = [xy] if isinstance(xy, tuple) else list(xy)
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline and not appause_overlay_attached():
        time.sleep(0.3)
    # verify-AC: Continue stays DISABLED until the group countdown finishes
    # (P6Main cooldown=20 -> every tap in a 4-attempt/7s window was a no-op),
    # so retry by wall-clock for long enough to cover a 20 s cooldown.
    tap_deadline = time.monotonic() + 28
    attempt = 0
    while time.monotonic() < tap_deadline:
        time.sleep(2.0 if attempt == 0 else 1.5)
        x, y = points[attempt % len(points)]
        adb_shell(f"input tap {x} {y}")
        hit = logcat_match(marker, timeout=2)
        if hit is not None:
            ev.mark(f"overlay tap ok (attempt {attempt + 1} at {x},{y}): {hit.strip()}")
            return True
        if not appause_overlay_attached():
            ev.mark(f"tap at {(x, y)} detached the overlay without {marker!r}")
            return False
        attempt += 1
    ev.mark(f"taps at {points} produced no {marker!r} in {attempt} attempts")
    return False


def arm_session(ev: Evidence, tag: str) -> bool:
    """Intercept the target and tap Continue so a session becomes active."""
    if not expect_intercept(TARGET_A):
        ev.mark(f"{tag}: setup intercept missing")
        return False
    if not tap_overlay(ev, CONTINUE_XY, rf"Session start: {TARGET_A}"):
        ev.mark(f"{tag}: Continue tap did not start a session")
        return False
    time.sleep(1.0)
    return True


def leave_and_return(ev: Evidence, tag: str, away_seconds: float) -> bool:
    """Home (starts the leave-timer), wait, come back. True = re-intercepted."""
    logcat_clear()
    go_home()
    started = logcat_match(rf"Leave cooldown started for {TARGET_A}", timeout=8)
    ev.mark(f"{tag}: leave-timer started: {bool(started)}")
    time.sleep(away_seconds)
    logcat_clear()
    if not launch_from_home(TARGET_A):
        ev.mark(f"{tag}: return launch FAILED (B-class harness)")
        raise SystemExit(2)
    time.sleep(8)  # give a (wrong) re-intercept time to appear
    re_hit = appause_overlay_attached() or logcat_match(
        rf"Overlay shown for {TARGET_A}", timeout=0.1) is not None
    return bool(re_hit)


def probe_leave_hold(ev: Evidence) -> None:
    reset_appause()
    if not arm_session(ev, "LEAVE-HOLD"):
        ev.verdict("LEAVE-HOLD-setup", False)
        return
    re_hit = leave_and_return(ev, "LEAVE-HOLD", 20)  # well inside the grace window
    ev.verdict("LEAVE-HOLD-return-in-grace-no-reintercept", not re_hit,
               "overlay re-appeared inside grace window" if re_hit else "")
    go_home()


def probe_leave_expire(ev: Evidence) -> None:
    reset_appause()
    if not arm_session(ev, "LEAVE-EXPIRE"):
        ev.verdict("LEAVE-EXPIRE-setup", False)
        return
    re_hit = leave_and_return(ev, "LEAVE-EXPIRE", GRACE_SECONDS + 15)
    ev.verdict("LEAVE-EXPIRE-rearm-after-grace", re_hit,
               "" if re_hit else "cooldown did NOT re-arm after grace expired")
    go_home()


def probe_screen(ev: Evidence) -> None:
    reset_appause()
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
        # Cancel must dismiss the overlay AND land on the launcher.
        adb_shell(f"input tap {CANCEL_XY[0]} {CANCEL_XY[1]}")
        time.sleep(2.5)
        gone = not appause_overlay_attached()
        home = foreground_package() not in ("", TARGET_A)
        ev.verdict("SCREEN-overlay-tappable-after-wake", gone and home,
                   f"gone={gone} left-target={home}")
    go_home()


def probe_fstop(ev: Evidence) -> None:
    reset_appause()
    if not expect_intercept(TARGET_A):
        ev.mark("FSTOP: initial intercept missing")
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
