"""P6: group mutation while a session/overlay is live.

The interception decision reads Room per foreground event (campaign_lib
docstring), so these probes hunt for stale-cache or guard leaks when the
group changes underneath in-memory session/bypass state:

  MUT-REMOVE   deskclock is in an active session (bypass on); remove it
               from the group. Leaving + re-entering must NOT intercept,
               and must not crash.
  MUT-MIGRATE  app moves group A -> B mid-session with different cooldown;
               re-entry must intercept once, and the logged cooldown must
               match B (proves per-event read, no stale cache).
  MUT-DELETE   delete the whole group row (with its apps) while the OVERLAY
               is up. Overlay may finish its countdown, but the next launch
               must be clean; no phantom intercept afterwards.
  MUT-COOLDOWN cooldown 20 -> 2 then immediate launch: intercept must
               report cooldown=2s on the very next event.
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

from campaign_lib import (
    Evidence,
    expect_intercept,
    expect_no_intercept,
    go_home,
    logcat_clear,
    logcat_match,
    open_app,
    db,
    purge_all_groups,
    reset_appause,
    seed_pause_group,
    set_group_field,
)
# F-10/F-11: overlay buttons are invisible to uiautomator on this emulator —
# reuse the proven blind-coordinate tap with logcat-marker confirmation.
from p2_lifecycle_chaos import CANCEL_XY, CONTINUE_XY, tap_overlay

A = "com.google.android.deskclock"
B = "com.google.android.calendar"
G1 = "P6Main"
G2 = "P6Alt"


def group_id(name: str) -> str:
    import re
    out = db(f"SELECT id FROM app_groups WHERE name='{name}';")
    m = re.search(r"\d+", out)
    return m.group(0) if m else ""


def start_session(ev: Evidence, pkg: str) -> bool:
    reset_appause()
    logcat_clear()
    open_app(pkg)
    if not expect_intercept(pkg):
        ev.mark(f"P6: no initial intercept for {pkg}")
        return False
    if not tap_overlay(ev, CONTINUE_XY, rf"Session start: {pkg}"):
        ev.mark(f"P6: Continue not tappable for {pkg}")
        return False
    return True


def m_remove(ev: Evidence) -> None:
    reset_appause()
    seed_pause_group(G1, [A, B], 20)
    if not start_session(ev, A):
        ev.verdict("MUT-REMOVE-setup", False)
        return
    gid = group_id(G1)
    db(f"DELETE FROM group_apps WHERE packageName='{A}' AND groupId={gid};")
    go_home()
    time.sleep(1)
    ok = expect_no_intercept(A)
    ev.verdict("MUT-REMOVE-app-from-group-no-intercept", ok,
               f"removed {A} mid-session")
    go_home()


def m_migrate(ev: Evidence) -> None:
    reset_appause()
    seed_pause_group(G1, [A], 20)
    seed_pause_group(G2, [A], 3)
    if not start_session(ev, A):
        ev.verdict("MUT-MIGRATE-setup", False)
        return
    g1, g2 = group_id(G1), group_id(G2)
    db(f"UPDATE group_apps SET groupId={g2} WHERE packageName='{A}';")
    go_home()
    # verify-AC: re-entry within the 180 s leave grace after Continue must
    # NOT re-intercept (P2b semantics) — the old immediate expect_intercept
    # was a wrong oracle (line=None FAIL). Wait out the grace, THEN re-enter.
    time.sleep(185)
    logcat_clear()
    open_app(A)
    hit = expect_intercept(A, timeout=20)
    line = logcat_match(r"INTERCEPT: .*cooldown=\d+s", timeout=0.1)
    correct_cd = bool(line and "cooldown=3s" in line)
    ev.verdict("MUT-MIGRATE-new-cooldown-used", hit and correct_cd,
               f"line={line!r}")
    go_home()
    db(f"UPDATE group_apps SET groupId={g1} WHERE packageName='{A}';")


def m_delete_during_overlay(ev: Evidence) -> None:
    reset_appause()
    seed_pause_group(G1, [A], 20)
    logcat_clear()
    open_app(A)
    if not expect_intercept(A):
        ev.verdict("MUT-DELETE-setup", False)
        return
    gid = group_id(G1)
    db(f"DELETE FROM group_apps WHERE groupId={gid};")
    db(f"DELETE FROM app_groups WHERE id={gid};")
    time.sleep(3)  # overlay stays up; countdown state lives in the view
    if not tap_overlay(ev, CANCEL_XY, "Overlay dismissed"):
        ev.mark("MUT-DELETE: Cancel untappable after group delete (F-06 rebind?)")
    go_home()
    time.sleep(1)
    clean = expect_no_intercept(A)
    ev.verdict("MUT-DELETE-group-deleted-no-phantom", clean)
    go_home()


def m_cooldown_live(ev: Evidence) -> None:
    reset_appause()
    seed_pause_group(G1, [A], 20)
    set_group_field(G1, "cooldownSeconds", "2")
    logcat_clear()
    open_app(A)
    hit = expect_intercept(A, timeout=12)
    line = logcat_match(r"INTERCEPT: .*cooldown=\d+s", timeout=0.1)
    ok = hit and bool(line and "cooldown=2s" in line)
    ev.verdict("MUT-COOLDOWN-read-per-event", ok, f"line={line!r}")
    go_home()
    set_group_field(G1, "cooldownSeconds", "20")


PROBES = {
    "REMOVE": m_remove,
    "MIGRATE": m_migrate,
    "DELETE": m_delete_during_overlay,
    "COOLDOWN": m_cooldown_live,
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probes", default="REMOVE,MIGRATE,DELETE,COOLDOWN")
    parser.add_argument("--evidence", default="evidence/p6-mutation")
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), f"p6-{time.strftime('%H%M%S')}")
    # verify-AC: groups persist across probes; a stale group owning the same
    # app mis-attributes INTERCEPT lines to the wrong cooldown.
    purge_all_groups()
    for name in args.probes.split(","):
        PROBES[name.strip().upper()](ev)
    code = ev.finish()
    print("results:", ev.dir)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
