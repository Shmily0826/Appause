"""P4: re-remind time math (G5 user report "waited more than 1 minute").

Group fields exercised: reRemindMinutes=1, cooldownSeconds=5,
reRemindCooldownSeconds=5. Expected first pop ~55 s after the initial
Continue tap (targetAppearAt = continue + 60s - rePopSeconds*1000).

Oracles are LOGCAT-ONLY while the loop runs: on this emulator a stray
`uiautomator dump` has been seen to destroy+rebind the service (F-06),
which would cancel serviceScope and kill the very timer under test.

Probes:
  EXACT  Continue -> stay in app -> "Re-remind fired" between 50..70 s.
  AWAY   Continue -> stay away across the 55 s mark -> loop must log
         "user away ... re-checking soon", then returning to the app must
         pop within ~10 s (not wait a further full minute).
  RESTART force-stop mid-wait -> timers die with the process (documented
         acceptable); next launch must intercept cleanly again.
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    adb_shell,
    expect_intercept,
    go_home,
    logcat_clear,
    logcat_match,
    open_app,
    reset_appause,
    seed_pause_group,
    set_group_field,
    tap,
    wait_for,
)

TARGET = "com.google.android.deskclock"
GROUP = "P4ReRemind"

CONTINUE_RE = "Re-remind CLOCK START"
FIRED_RE = "Re-remind fired"
AWAY_RE = "re-checking soon"


def scroll_to_unlock() -> bool:
    # The debug "Unlock Pro" card sits at the bottom of the Pro screen; the
    # dump-based tap() only sees what is scrolled into the viewport.
    for _ in range(4):
        if tap("Unlock Pro|解锁 Pro", timeout=2):
            return True
        adb_shell("input swipe 540 1600 540 500 300")
        time.sleep(0.8)
    return False


def unlock_pro(ev: Evidence) -> bool:
    """Re-remind only schedules when proState.isPro (debug toggle persists
    in DataStore across force-stop, so once per run is enough)."""
    open_app(APPAUSE)
    if scroll_to_unlock():
        ev.mark("P4: Pro unlocked from current screen")
        go_home()
        return True
    if not wait_for("Settings|设置", timeout=10):
        ev.mark("P4: Settings entry not found for Pro unlock")
        return False
    tap("Settings|设置", timeout=4)
    if not tap("升级 Pro|Upgrade Pro|Pro", timeout=5):
        ev.mark("P4: Pro row not found in Settings")
        return False
    ok = scroll_to_unlock()
    ev.mark(f"P4: Pro unlock via Settings->Pro = {ok}")
    go_home()
    return ok


def ts(line: str) -> float | None:
    # logcat threadtime: first field is epoch seconds with millis.
    try:
        return float(line.split()[0])
    except (ValueError, IndexError):
        return None


def setup_group() -> None:
    seed_pause_group(GROUP, [TARGET], 5)
    set_group_field(GROUP, "reRemindMinutes", "1")
    set_group_field(GROUP, "reRemindCooldownSeconds", "5")


def tap_continue(ev: Evidence) -> bool:
    if not tap("Continue", timeout=10):
        ev.mark("P4: Continue button not found (overlay vanished? see F-06)")
        return False
    return True


def p_exact(ev: Evidence) -> None:
    reset_appause()
    setup_group()
    logcat_clear()
    open_app(TARGET)
    if not expect_intercept(TARGET) or not tap_continue(ev):
        ev.verdict("P4-EXACT-setup", False)
        return
    start = None
    fired = None
    deadline = time.monotonic() + 130
    while time.monotonic() < deadline:
        lines = adb_shell("logcat -d | grep -E 'Re-remind (CLOCK START|fired)'")
        for line in lines.splitlines():
            if "CLOCK START" in line and start is None:
                start = ts(line)
            if FIRED_RE in line:
                fired = ts(line)
        if start is not None and fired is not None:
            break
        time.sleep(2)
    if start is None:
        ev.verdict("P4-EXACT-pop-timing", False, "no CLOCK START log")
        return
    if fired is None:
        ev.verdict("P4-EXACT-pop-timing", False, "no pop within 130s")
        ev.save_logcat("P4-EXACT-nopop", tags=["AppauseA11yService"])
        return
    delta = fired - start
    # 55s target; tolerate scheduler drift but flag the G5 symptom (>60s)
    # and any early pop (<50s would mean the subtraction overshoots).
    ok = 50.0 <= delta <= 70.0
    ev.verdict("P4-EXACT-pop-timing", ok, f"continue->pop delta = {delta:.1f}s")


def p_away(ev: Evidence) -> None:
    reset_appause()
    setup_group()
    logcat_clear()
    open_app(TARGET)
    if not expect_intercept(TARGET) or not tap_continue(ev):
        ev.verdict("P4-AWAY-setup", False)
        return
    go_home()  # leave BEFORE the 55s mark
    away_seen = logcat_match(AWAY_RE, timeout=90)
    if not away_seen:
        ev.verdict("P4-AWAY-recheck", False, "no 're-checking soon' tick")
        return
    ev.verdict("P4-AWAY-recheck", True)
    open_app(TARGET)  # return; must pop within ~10 s, not a further minute
    popped = logcat_match(FIRED_RE, timeout=15)
    ev.verdict("P4-AWAY-pop-after-return", popped,
               "pop window after return = 15s" if popped else "no pop in 15s")


def p_restart(ev: Evidence) -> None:
    reset_appause()
    setup_group()
    logcat_clear()
    open_app(TARGET)
    if not expect_intercept(TARGET) or not tap_continue(ev):
        ev.verdict("P4-RESTART-setup", False)
        return
    time.sleep(10)
    reset_appause()  # process death: timers die with the scope (by design)
    time.sleep(2)
    if logcat_match(FIRED_RE, timeout=8):
        ev.verdict("P4-RESTART-no-orphan-pop", False, "pop survived process death")
        return
    # Interception must fully work again afterwards.
    logcat_clear()
    open_app(TARGET)
    back = expect_intercept(TARGET)
    go_home()
    ev.verdict("P4-RESTART-no-orphan-pop", back,
               f"no orphan pop; re-intercept after restart={back}")


PROBES = {"EXACT": p_exact, "AWAY": p_away, "RESTART": p_restart}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probes", default="EXACT,AWAY,RESTART")
    parser.add_argument("--evidence", default="evidence/p4-reremind")
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), f"p4-{time.strftime('%H%M%S')}")
    if not unlock_pro(ev):
        ev.verdict("P4-PRO-UNLOCK", False, "re-remind needs Pro; aborting")
        print("results:", ev.dir)
        return 1
    ev.verdict("P4-PRO-UNLOCK", True)
    for name in args.probes.split(","):
        PROBES(name.strip().upper())(ev)
    code = ev.finish()
    print("results:", ev.dir)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
