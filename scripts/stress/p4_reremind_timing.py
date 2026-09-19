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
    adb,
    adb_shell,
    expect_intercept,
    go_home,
    logcat_clear,
    logcat_match,
    open_app,
    purge_all_groups,
    reset_appause,
    seed_pause_group,
    set_group_field,
)
# F-10: blind overlay taps proven on this AVD (1080x2340).
from p2_lifecycle_chaos import CONTINUE_XY, tap_overlay
# p3 owns the verified raw-proto DataStore codec (F-12 wire-type lessons).
from p3_pass_expiry import _parse_fields, _payload_len, _read_varint, read_prefs, set_bool, write_prefs

TARGET = "com.google.android.deskclock"
GROUP = "P4ReRemind"

CONTINUE_RE = "Re-remind CLOCK START"
FIRED_RE = "Re-remind fired"
AWAY_RE = "re-checking soon"


def _has_bool(buf: bytes, key: str, value: bool) -> bool:
    want = b"\x18" + (b"\x01" if value else b"\x00")
    for fno, wt, raw in _parse_fields(buf):
        if fno == 1 and wt == 2:
            payload = raw[len(raw) - _payload_len(raw):]
            k = v = None
            for f2, w2, r2 in _parse_fields(payload):
                if f2 == 1 and w2 == 2:
                    ln, p = _read_varint(r2, 1)
                    k = r2[p:p + ln]
                elif f2 == 2 and w2 == 2:
                    v = r2[len(r2) - _payload_len(r2):]
            if k == key.encode() and v == want:
                return True
    return False


def unlock_pro(ev: Evidence) -> bool:
    """Re-remind only schedules when proState.isPro.

    F-11: the Pro screen's buttons are invisible to uiautomator dumps on this
    AVD (nodes come back text-empty), so the UI toggle path cannot be driven
    unattended. Seed the same preference the toggle writes —
    SettingsDataStore PRO_UNLOCKED_KEY ("pro_unlocked") — while the process is
    force-stopped, then cold-restart so DataStore reads it from disk.
    Persists across force-stop, so once per run is enough.
    """
    adb_shell("am force-stop " + APPAUSE)
    local = ev.dir / "prefs_pro_seed.pb"
    try:
        data = read_prefs(local)
    except Exception as exc:  # noqa: BLE001
        ev.mark(f"P4: pro seed read failed: {exc}")
        return False
    local.write_bytes(set_bool(data, "pro_unlocked", True))
    write_prefs(local)
    logcat_clear()
    reset_appause()
    time.sleep(2)
    check = read_prefs(ev.dir / "prefs_pro_check.pb")
    ok = _has_bool(check, "pro_unlocked", True)
    # F-12 sentinel: a byte-level round-trip can pass while the real androidx
    # serializer rejects the file — that would kill the service loop and turn
    # every P4 probe into a false result.
    if ok and "CorruptionException" in adb("logcat", "-d", "-s", "AppauseA11yService:E"):
        ev.mark("P4: DataStore CorruptionException after pro seed")
        ok = False
    ev.mark(f"P4: pro_unlocked seeded = {ok}")
    return ok


def ts(line: str) -> float | None:
    # logcat threadtime: first field is epoch seconds with millis.
    try:
        return float(line.split()[0])
    except (ValueError, IndexError):
        return None


def setup_group() -> None:
    # verify-AC: deskclock lingered in stale groups (P3Pass/P6Main, reRemind=0)
    # and the service attributed the intercept to one of them -> no CLOCK
    # START at all. Start from a clean group table.
    purge_all_groups()
    seed_pause_group(GROUP, [TARGET], 5)
    set_group_field(GROUP, "reRemindMinutes", "1")
    set_group_field(GROUP, "reRemindCooldownSeconds", "5")


def tap_continue(ev: Evidence) -> bool:
    # F-10: the overlay is invisible to uiautomator — blind-tap and confirm
    # the "Session start" marker instead of hunting a node.
    return tap_overlay(ev, CONTINUE_XY, rf"Session start: {TARGET}")


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
        PROBES[name.strip().upper()](ev)
    code = ev.finish()
    print("results:", ev.dir)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
