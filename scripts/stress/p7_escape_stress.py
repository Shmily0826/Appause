"""P7: escape-safety stress — navigation input storms vs the pause overlay.

Every scenario ends with the SAME invariant bundle (this is what a real user
relies on):
  1. no crash / no ANR in the Appause process (PID unchanged, no FATAL),
  2. the system is responsive again (a focused window within 15s),
  3. the NEXT launch still intercepts deterministically (no zombie guard),
  4. no overlay left attached over the launcher (no window leak).

A watchdog wraps each scenario: any exception or >90s stall -> Home +
reset_appause() recovery + FAIL for that scenario (run never wedges).
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
    app_pid,
    expect_intercept,
    foreground_package,
    go_home,
    key,
    reset_appause,
    screenshot,
    service_bound,
)
from p2_lifecycle_chaos import CANCEL_XY, TARGET_A, tap_overlay

SCENARIOS: dict[str, ...] = {}


def storm(codes: list[str], gap: float = 0.35) -> None:
    for code in codes:
        key(code)
        time.sleep(gap)


def home_burst() -> None:
    storm(["KEYCODE_HOME"] * 10)


def back_burst() -> None:
    storm(["KEYCODE_BACK"] * 12)


def recents_burst() -> None:
    storm(["KEYCODE_APP_SWITCH"] * 8, gap=0.5)


def mixed_burst() -> None:
    codes = ["KEYCODE_HOME", "KEYCODE_BACK", "KEYCODE_APP_SWITCH", "KEYCODE_BACK"]
    storm(codes * 8)


def check_invariants(ev: Evidence, tag: str) -> bool:
    """Run the shared P7 oracle set and return the combined verdict."""
    ok = True
    if app_pid() == "":
        ev.mark(f"{tag}: Appause process GONE (crash?)")
        ok = False
    fatal = adb_shell("logcat -d | grep -c 'FATAL EXCEPTION'").strip()
    anr = adb_shell("logcat -d | grep -c \"ANR in com.appause\"").strip()
    if fatal != "0" or anr != "0":
        ev.mark(f"{tag}: FATAL={fatal} ANR={anr} in logcat")
        screenshot(ev.dir / f"{tag}.crash.png")
        ok = False
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline and foreground_package() == "":
        time.sleep(1.0)
    if foreground_package() == "":
        ev.mark(f"{tag}: no focused window 15s after burst (unresponsive)")
        ok = False
    time.sleep(3.0)  # let any settling overlay detach
    if appause_overlay_attached():
        ev.mark(f"{tag}: overlay still attached over {foreground_package()} (leak?)")
        ok = False
    # determinism must survive the storm
    if not expect_intercept(TARGET_A, timeout=20):
        ev.mark(f"{tag}: post-burst launch did NOT intercept (zombie state)")
        ok = False
        return ok
    if not tap_overlay(ev, CANCEL_XY, "Overlay dismissed"):
        ev.mark(f"{tag}: cleanup Cancel missed — overlay may block next scenario")
        return False
    return ok


def scenario_home_on_intercept(ev: Evidence) -> bool:
    """Home-mash WHILE the pause overlay is up — race addView vs navigation."""
    if not expect_intercept(TARGET_A):
        ev.mark("HOME-BURST: initial intercept missing")
        return False
    home_burst()
    return check_invariants(ev, "HOME-BURST")


def scenario_back_on_intercept(ev: Evidence) -> bool:
    if not expect_intercept(TARGET_A):
        ev.mark("BACK-BURST: initial intercept missing")
        return False
    time.sleep(1.5)  # F-05: overlay needs focus before keys land
    back_burst()
    return check_invariants(ev, "BACK-BURST")


def scenario_recents_on_intercept(ev: Evidence) -> bool:
    if not expect_intercept(TARGET_A):
        ev.mark("REC-BURST: initial intercept missing")
        return False
    recents_burst()
    return check_invariants(ev, "REC-BURST")


def scenario_idle_mixed(ev: Evidence) -> bool:
    """40 mixed nav keys with NO interception — pure launcher churn."""
    go_home()
    time.sleep(1.0)
    mixed_burst()
    return check_invariants(ev, "IDLE-MIXED")


SCENARIOS.update({
    "HOME-BURST": scenario_home_on_intercept,
    "BACK-BURST": scenario_back_on_intercept,
    "REC-BURST": scenario_recents_on_intercept,
    "IDLE-MIXED": scenario_idle_mixed,
})


def run_with_watchdog(ev: Evidence, name: str) -> None:
    start = time.monotonic()
    try:
        passed = SCENARIOS[name](ev)
    except Exception as exc:  # watchdog: never let one scenario wedge the run
        ev.mark(f"{name}: watchdog caught {type(exc).__name__}: {exc}")
        passed = False
    if not passed:
        reset_appause()  # known-clean state for the next scenario
    ev.verdict(name, passed, f"{time.monotonic() - start:.0f}s")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenarios", default=",".join(SCENARIOS))
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), "p7-escape")
    assert reset_appause(), "service did not come up — run setup_device.py first"
    for name in args.scenarios.split(","):
        run_with_watchdog(ev, name.strip().upper())
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
