"""P2b: Home-abandonment behavior with Usage-Access turned OFF.

TEST_REPORT records this as a requirements question that was never probed:
session/grace bookkeeping may assume usage stats. The interceptor itself is
event-driven (a11y window events), so the expectation here is that BOTH
properties hold even with usage access denied:

  USAGEOFF-session-holds   Continue -> Home -> immediate re-open: must NOT
                           re-intercept (bypass session survives the Home
                           transition without usage stats).
  USAGEOFF-rearm-after-grace
                           re-open AFTER the 180 s leave cooldown: MUST
                           intercept again (leave timer re-armed without
                           usage stats).

If re-arm fails we rerun the identical sequence with usage ON as a control
(USAGEON-control) to tell A-class (usage-access dependence) apart from a
plain cooldown-timing expectation error.

Usage access is toggled through appops — the same source
ForegroundChecker.isUsageAccessGranted consults — and is always restored.
"""
from __future__ import annotations

import argparse
import time

from campaign_lib import (
    APPAUSE,
    Evidence,
    adb_shell,
    expect_intercept,
    expect_no_intercept,
    go_home,
    logcat_clear,
    logcat_match,
    reset_appause,
    seed_pause_group,
)
# F-10/F-11: blind overlay tap (uiautomator cannot see the pause buttons).
from p2_lifecycle_chaos import CONTINUE_XY, tap_overlay

TARGET = "com.google.android.deskclock"
GROUP = "P2bUsageOff"
LEAVE_GRACE_S = 180  # LEAVE_COOLDOWN_MS hardcoded in the service
WAIT_AFTER_GRACE = LEAVE_GRACE_S + 10


def set_usage_access(granted: bool) -> None:
    # PACKAGE_USAGE_STATS is enforced as an appop; the service checks the op.
    adb_shell(f"appops set {APPAUSE} GET_USAGE_STATS {'allow' if granted else 'deny'}")
    adb_shell(f"pm {'revoke' if not granted else 'grant'} {APPAUSE} android.permission.PACKAGE_USAGE_STATS 2>/dev/null")
    time.sleep(1)


def usage_granted() -> str:
    return adb_shell(f"appops get {APPAUSE} GET_USAGE_STATS").strip()


def run_sequence(ev: Evidence, tag: str) -> tuple[bool, bool]:
    """Returns (session_holds, rearm_ok) for one usage-access setting."""
    ev.mark(f"{tag}: appops says {usage_granted() or '(default)'}")
    if not expect_intercept(TARGET):
        ev.mark(f"{tag}: setup intercept FAILED (B-class setup, sequence aborted)")
        return False, False
    if not tap_overlay(ev, CONTINUE_XY, rf"Session start: {TARGET}"):
        ev.mark(f"{tag}: Continue blind-tap failed")
        return False, False
    go_home()
    time.sleep(8)
    holds = expect_no_intercept(TARGET)
    ev.mark(f"{tag}-session: no re-intercept during grace = {holds}")
    leave = logcat_match("Leave cooldown started", timeout=5)
    ev.mark(f"{tag}-leave-timer line: {leave or 'NONE (within 180s window)'}")
    remaining = WAIT_AFTER_GRACE - 8 - 6  # waits already spent since Home
    time.sleep(max(remaining, 0))
    again = expect_intercept(TARGET)
    ev.mark(f"{tag}-rearm: intercept after grace = {bool(again)}")
    return holds, bool(again)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", default="evidence/p2b")
    args = parser.parse_args()

    ev = Evidence(args.evidence, "p2b-usageoff")
    try:
        if not seed_pause_group(GROUP, [TARGET], 5):
            ev.verdict("P2b-seed", False, "group seeding failed")
            return ev.finish()
        if not reset_appause():
            ev.verdict("P2b-reset", False, "service did not rebind")
            return ev.finish()

        set_usage_access(False)
        holds, rearm = run_sequence(ev, "USAGEOFF")
        ev.verdict("USAGEOFF-session-holds", holds)
        if rearm:
            ev.verdict("USAGEOFF-rearm-after-grace", True)
        else:
            # Control: same sequence with usage access granted.
            ev.verdict("USAGEOFF-rearm-after-grace", False,
                       "no re-intercept after grace with usage OFF — running control")
            if not reset_appause():
                ev.mark("control reset failed")
                return ev.finish()
            set_usage_access(True)
            c_holds, c_rearm = run_sequence(ev, "USAGEON-control")
            ev.verdict("USAGEON-control", c_holds and c_rearm,
                       "control also fails => expectation/timing issue, not usage-access dependence")
        set_usage_access(True)  # always restore for later probes
    finally:
        go_home()
        ev.save_logcat("p2b-final")
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
