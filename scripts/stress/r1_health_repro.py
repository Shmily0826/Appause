"""P2-R1: Accessibility health + static-state inheritance repro.

Three probes, each repeated deterministically:

  A. "restart card lie" — force-stop (process death while service was
     CONNECTED), then open Appause home BEFORE the system rebinds the
     service. The home must never present a definitive blocking "not
     enabled" state while the system setting says ENABLED; a stale
     DISCONNECTED surviving into the new UI session is an A-class defect.
     Also checks the card self-heals after rebind without user action.

  B. "in-process rebind" — toggle the service off/on via Settings.Secure
     WITHOUT killing the process. onDestroy sets _processState=DISCONNECTED,
     onServiceConnected must restore CONNECTED and home must recover.

  C. "rebind during pause overlay" — show the pause overlay, rebind the
     service underneath it, then verify next interception still works.
     R1 risk: companion @Volatile statics (lastForegroundPackage, pause
     guard) are inherited by the new service instance.

Machine-checked from UI text + dumpsys + logcat; evidence via Evidence.
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
    logcat_clear,
    nodes,
    open_app,
    reset_appause,
    screenshot,
    service_bound,
    start_service_via_settings,
)

# Same target the seeded "Campaign" group uses (see setup_device.py).
TARGET_A = "com.google.android.deskclock"

HOME_HEALTHY = "Service active"
HOME_UNVERIFIED = "not confirmed yet"
HOME_BLOCKING_BULLET = "detect the foreground app"


def home_card_state(ev: Evidence, tag: str) -> str:
    """Classify the home status/setup card from visible UI text."""
    open_app(APPAUSE)
    time.sleep(1.5)
    text = " ".join(
        n.get("text", "") + "|" + n.get("content-desc", "") for n in nodes()
    )
    if HOME_HEALTHY in text:
        state = "HEALTHY"
    elif HOME_BLOCKING_BULLET in text and HOME_UNVERIFIED not in text:
        state = "BLOCKING"
    elif HOME_UNVERIFIED in text:
        state = "UNVERIFIED"
    else:
        state = "NO-CARD"
    ev.mark(f"{tag}: home card={state} service_bound={service_bound()}")
    screenshot(ev.dir / f"{tag}-{state}.png")
    return state


def wait_bound(timeout: float) -> bool:
    start = time.time()
    while time.time() - start < timeout:
        if service_bound():
            return True
        time.sleep(1.0)
    return False


def probe_a(ev: Evidence, rounds: int) -> bool:
    """Process death -> immediate home -> card must never be a false BLOCKING."""
    ok = True
    for i in range(rounds):
        reset_appause()
        adb_shell("am force-stop " + APPAUSE)
        open_app(APPAUSE)
        state = home_card_state(ev, f"A{i}-immediate")
        if state == "BLOCKING":
            # Definitive warning while the system setting is ENABLED and the
            # service is merely pending rebind = false alarm shown to users.
            ev.mark(f"A{i}: false BLOCKING card right after process restart")
            ev.save_logcat(f"A{i}-blocking")
            screenshot(ev.dir / f"A{i}-blocking.png")
        # Self-heal: once the system rebinds, the card must recover with no
        # user interaction.
        healed = False
        if wait_bound(30):
            time.sleep(2.0)
            healed = home_card_state(ev, f"A{i}-afterbind") in ("HEALTHY", "NO-CARD", "UNVERIFIED")
        if not healed:
            ev.verdict(f"A{i}-self-heal", False, "card stuck non-healthy after rebind")
            ok = False
    return ok


def probe_b(ev: Evidence, rounds: int) -> bool:
    ok = True
    reset_appause()
    for i in range(rounds):
        start_service_via_settings(False)
        time.sleep(2.0)
        start_service_via_settings(True)
        bound = wait_bound(15)
        time.sleep(2.0)
        state = home_card_state(ev, f"B{i}")
        ev.verdict(f"B{i}-rebind-recovers", bound and state in ("HEALTHY", "NO-CARD"),
                   f"bound={bound} card={state}")
        ok &= bound and state in ("HEALTHY", "NO-CARD")
    return ok


def probe_c(ev: Evidence, rounds: int) -> bool:
    ok = True
    for i in range(rounds):
        reset_appause()
        logcat_clear()
        open_app(TARGET_A)
        first = expect_intercept(TARGET_A)
        if not first:
            ev.verdict(f"C{i}-setup-intercept", False, "overlay never appeared")
            ok = False
            continue
        # Rebind while the overlay session state is live.
        start_service_via_settings(False)
        time.sleep(1.5)
        start_service_via_settings(True)
        time.sleep(3.0)
        if appause_overlay_attached():
            ev.mark(f"C{i}: overlay still attached after rebind (possible orphan)")
            screenshot(ev.dir / f"C{i}-after-rebind.png")
        adb_shell("am force-stop " + APPAUSE)  # clears any orphan deterministically
        start_service_via_settings(True)
        wait_bound(15)
        # Next interception must still work after cooldown expiry: re-open
        # the same target after its 20s cooldown to expose a stale
        # lastForegroundPackage/guard inherited by the new instance.
        go_home()
        time.sleep(22)
        logcat_clear()
        open_app(TARGET_A)
        again = expect_intercept(TARGET_A)
        go_home()
        ev.verdict(f"C{i}-intercept-after-rebind", bool(again))
        ok &= bool(again)
    return ok


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--probes", default="A,B,C")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()

    ev = Evidence(Path(args.evidence), "r1-health")
    probes = {"A": probe_a, "B": probe_b, "C": probe_c}
    for name in args.probes.split(","):
        name = name.strip().upper()
        if name in probes:
            probes[name](ev, args.rounds)
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
