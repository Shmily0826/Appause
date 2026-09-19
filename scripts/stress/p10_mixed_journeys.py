"""P10: real-user mixed journeys — chained behaviors P1–P8 never combined.

Not another random walk: each journey stitches ALREADY-proven primitives into
one continuous sequence whose value is the INTERFERENCE between states
(multi-group isolation, session vs recents, shade during countdown,
pass-vs-reentry, cooldown change mid-journey). Fully scripted (no RNG) so the
actions.log IS the replay spec. Deterministic oracles only — from
campaign_lib / p2 / p3, never raw counters.

Emulator lessons baked in (FINDINGS.md):
  * F-06: uiautomator dumps kill the a11y service briefly -> no dumps while
    an overlay is up; overlay buttons are blind coordinate taps (p2 tap_overlay).
  * F-18: overlay presence = type=2032 window count, never grep -c package.
  * verify-AC: this AVD runs the PRO overlay layout; button coords carry
    free/pro candidates in order.

Journeys:
  J1  A-cancel -> normal app -> B-continue -> in-app page switch -> back to A
      (A must intercept: B's session must not leak) -> B still session-held.
  J2  A-continue -> quick switch to normal -> return (hold) -> recents ->
      home -> immediate reopen: still inside grace, NO re-intercept.
  J3  intercept mid-countdown -> notification shade -> dismiss -> home ->
      re-enter -> must intercept again -> Cancel still works.
  J4  temp pass active -> 3x enter/exit target, zero interceptions -> after
      natural expiry the next entry intercepts again.
  J5  group cooldown 120 -> Cancel -> Room-mutate to 1 -> reopen -> Continue
      must become usable almost immediately (new value effective).
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

from campaign_lib import (
    Evidence,
    adb_shell,
    db,
    expect_intercept,
    expect_no_intercept,
    foreground_package,
    go_home,
    key,
    launch_from_home,
    logcat_clear,
    logcat_match,
    purge_all_groups,
    reset_appause,
    screenshot,
    seed_pause_group,
    service_bound,
    set_group_field,
)
from p2_lifecycle_chaos import CONTINUE_XY, tap_overlay
from p3_pass_expiry import device_now_ms, seed_pass

TARGET_A = "com.google.android.deskclock"   # group A / B / J-targets
TARGET_B = "com.android.chrome"             # second restricted app (J1)
NORMAL = "com.android.settings"             # never in any group

# F-22 (from the P9 run): "Overlay dismissed" is logged by Cancel AND
# Continue AND temp-pass paths, and on this AVD the overlay renders the FREE
# layout (Cancel at y=1788; y=1663 is the Continue button). Cancel is only
# proven when Room's latest launch record for the package says 'cancelled'.
CANCEL_XY_FREE = (540, 1788)


def last_action(pkg: str) -> str:
    out = db(f"SELECT action FROM app_launch_records WHERE packageName='{pkg}'"
             " ORDER BY id DESC LIMIT 1;")
    return out.strip()


def dismiss_current(ev: Evidence, pkg: str, tag: str) -> bool:
    """Tap Cancel (free layout) and prove via Room the interception ended cancelled."""
    if not tap_overlay(ev, [CANCEL_XY_FREE], "Overlay dismissed"):
        ev.mark(f"{tag}: cancel tap never dismissed the overlay")
        return False
    time.sleep(1.0)  # logLaunch runs on the IO dispatcher
    action = last_action(pkg)
    if action == "cancelled":
        return True
    ev.mark(f"{tag}: B-class mis-tap — Room says action={action!r}, clearing state")
    reset_appause()
    return False


# --------------------------------------------------------------------------- J1
def journey_1(ev: Evidence) -> None:
    name = "J1-multi-target-isolation"
    reset_appause()
    seed_pause_group("P10A", [TARGET_A], cooldown_seconds=1)
    seed_pause_group("P10B", [TARGET_B], cooldown_seconds=1)
    ok = True
    if not expect_intercept(TARGET_A):
        ev.mark("J1: A intercept missing"); ok = False
    elif not dismiss_current(ev, TARGET_A, "J1 A-cancel"):
        ok = False
    # normal app must stay untouched right after a Cancel elsewhere
    if not expect_no_intercept(NORMAL, settle=6):
        ev.mark("J1: normal app was intercepted"); ok = False
    if not expect_intercept(TARGET_B):
        ev.mark("J1: B intercept missing after A cycle"); ok = False
    elif not tap_overlay(ev, CONTINUE_XY, rf"Session start: {TARGET_B}"):
        ev.mark("J1: B continue did not start session"); ok = False
    else:
        # in-app page switches via explicit intents (no UI dumps needed)
        adb_shell(f"am start -a android.intent.action.VIEW -d 'https://example.com' -p {TARGET_B}")
        time.sleep(3)
        adb_shell(f"am start -a android.intent.action.VIEW -d 'https://www.wikipedia.org' -p {TARGET_B}")
        time.sleep(3)
        if foreground_package() != TARGET_B:
            ev.mark(f"J1: chrome lost foreground during page switch ({foreground_package()})")
            ok = False
        go_home(); time.sleep(2)
        # A must still intercept: B's active session must NOT leak across apps
        if not expect_intercept(TARGET_A):
            ev.mark("J1: A NOT intercepted after B session (session leak?)"); ok = False
        else:
            dismiss_current(ev, TARGET_A, "J1 A-cancel-2")
        # B inside its session: returning must be quiet
        if not expect_no_intercept(TARGET_B, settle=6):
            ev.mark("J1: B re-intercepted while session active"); ok = False
    go_home()
    purge_all_groups()
    ev.verdict(name, ok and service_bound())


# --------------------------------------------------------------------------- J2
def journey_2(ev: Evidence) -> None:
    name = "J2-session-recents-grace"
    reset_appause()
    seed_pause_group("P10J2", [TARGET_A], cooldown_seconds=1)
    ok = True
    if not expect_intercept(TARGET_A):
        ev.mark("J2: intercept missing"); ok = False
    elif not tap_overlay(ev, CONTINUE_XY, rf"Session start: {TARGET_A}"):
        ev.mark("J2: no session"); ok = False
    else:
        launch_from_home(NORMAL)          # quick switch away
        time.sleep(5)
        logcat_clear()
        launch_from_home(TARGET_A)        # straight back — session must hold
        time.sleep(6)
        if logcat_match(rf"Overlay shown for {TARGET_A}", timeout=0.1) or \
           foreground_package() != TARGET_A:
            ev.mark("J2: re-intercepted on quick return"); ok = False
        key("KEYCODE_APP_SWITCH")         # recents, no swipe — just peek
        time.sleep(2)
        key("KEYCODE_APP_SWITCH")
        go_home(); time.sleep(2)
        logcat_clear()
        launch_from_home(TARGET_A)        # immediate reopen inside 3-min grace
        time.sleep(8)
        # overlay steals window focus, so "front is not the target" OR a fresh
        # shown-marker both mean the grace window was violated
        re_hit = foreground_package() != TARGET_A or \
            logcat_match(rf"Overlay shown for {TARGET_A}", timeout=0.1) is not None
        if re_hit:
            ev.mark("J2: re-intercept after recents+home inside grace"); ok = False
        screenshot(ev.dir / "j2-reopen.png")
    go_home()
    purge_all_groups()
    ev.verdict(name, ok and service_bound())


# --------------------------------------------------------------------------- J3
def journey_3(ev: Evidence) -> None:
    name = "J3-shade-during-countdown"
    reset_appause()
    seed_pause_group("P10J3", [TARGET_A], cooldown_seconds=15)  # act mid-countdown
    ok = True
    if not expect_intercept(TARGET_A):
        ev.mark("J3: intercept missing"); ok = False
    else:
        time.sleep(3)                     # still counting down
        key("KEYCODE_NOTIFICATION")       # pull the shade over the overlay
        time.sleep(3)
        key("KEYCODE_NOTIFICATION")       # toggle it away
        time.sleep(2)
        go_home(); time.sleep(2)
        if not expect_intercept(TARGET_A):
            ev.mark("J3: no re-intercept after shade+home churn"); ok = False
        elif not dismiss_current(ev, TARGET_A, "J3 cancel"):
            ok = False
    purge_all_groups()
    ev.verdict(name, ok and service_bound())


# --------------------------------------------------------------------------- J4
def journey_4(ev: Evidence) -> None:
    name = "J4-temppass-reentries"
    reset_appause()
    seed_pause_group("P10J4", [TARGET_A], cooldown_seconds=1)
    expiry = device_now_ms() + 60_000
    if not seed_pass(ev, expiry):
        ev.verdict(name, False, "pass seed failed (B-class)")
        return
    ok = True
    for i in range(3):                    # in/out/in/out while the pass lives
        if not expect_no_intercept(TARGET_A, settle=4):
            ev.mark(f"J4: entry {i + 1} intercepted while pass active"); ok = False
            break
        go_home(); time.sleep(2)
    remaining = (expiry - device_now_ms()) / 1000
    ev.mark(f"J4: pass alive {remaining:.0f}s more — parking until expiry")
    time.sleep(max(remaining, 0) + 8)
    if not expect_intercept(TARGET_A, timeout=20):
        ev.mark("J4: no intercept after natural expiry"); ok = False
    else:
        dismiss_current(ev, TARGET_A, "J4 cancel")
    # cleanup: strip the pass entry so a later run starts clean
    adb_shell("am force-stop com.appause.android.debug")
    from p3_pass_expiry import read_prefs, write_prefs, PASSES_KEY, set_string_list
    local = ev.dir / "prefs_clear.pb"
    data = read_prefs(local)
    local.write_bytes(set_string_list(data, PASSES_KEY, []))
    write_prefs(local)
    reset_appause()
    go_home()
    ev.verdict(name, ok and service_bound())


# --------------------------------------------------------------------------- J5
def journey_5(ev: Evidence) -> None:
    name = "J5-cooldown-change-effective"
    reset_appause()
    seed_pause_group("P10J5", [TARGET_A], cooldown_seconds=120)
    ok = True
    if not expect_intercept(TARGET_A):
        ev.mark("J5: intercept missing"); ok = False
    else:
        set_group_field("P10J5", "cooldownSeconds", "1")  # mid-journey settings edit
        ev.mark("J5: cooldown mutated 120 -> 1 while overlay up")
        if not dismiss_current(ev, TARGET_A, "J5 cancel"):
            ok = False
        go_home(); time.sleep(2)
        if not expect_intercept(TARGET_A):
            ev.mark("J5: no re-intercept after mutation"); ok = False
        else:
            start = time.monotonic()
            cont = tap_overlay(ev, CONTINUE_XY, rf"Session start: {TARGET_A}")
            dt = time.monotonic() - start
            if not cont:
                ev.mark(f"J5: Continue never enabled ({dt:.0f}s) — stale 120s cooldown?")
                ok = False
            else:
                ev.mark(f"J5: Continue effective after {dt:.0f}s (new cooldown in force)")
            go_home()  # leave the session behind
    purge_all_groups()
    ev.verdict(name, ok and service_bound())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--journeys", default="1,2,3,4,5")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), "p10-mixed")
    mapping = {"1": journey_1, "2": journey_2, "3": journey_3,
               "4": journey_4, "5": journey_5}
    for jid in args.journeys.split(","):
        fn = mapping.get(jid.strip())
        if not fn:
            ev.mark(f"unknown journey id {jid!r}")
            continue
        try:
            fn(ev)
        except Exception as exc:  # keep the rest of the suite alive; dump the scene
            ev.mark(f"JOURNEY {jid} raised {type(exc).__name__}: {exc}")
            ev.dump_state(f"journey{jid}.crash")
            ev.verdict(f"journey-{jid}-crashed", False, str(exc))
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
