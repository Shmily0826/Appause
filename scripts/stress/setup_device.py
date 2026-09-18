#!/usr/bin/env python3
"""One-command disposable device setup for the reliability campaign.

This is the missing `setup_device.py` that ui_stress.py always referenced but
never existed — every previous campaign step was manual, which made runs
unreproducible. Running this on a wiped emulator leaves the device in exactly
the state ui_stress.py and the chaos scenarios assume:

    APK installed -> permissions granted -> onboarding completed through the
    real UI -> PAUSE group seeded -> first interception verified -> clean reset.

Every step ends in a machine check; the script exits non-zero (and saves
evidence) if any step does not hold. Usage:

    python setup_device.py [--apk PATH] [--group-name Campaign] [--cooldown 20]
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from campaign_lib import (  # noqa: E402
    APPAUSE, Evidence, adb, appause_overlay_attached, grant_runtime_permissions,
    open_app, reset_appause, screenshot, seed_pause_group, service_bound,
    tap, wait_for, wait_for_boot, db,
)

TARGET_A = "com.google.android.deskclock"
TARGET_B = "com.google.android.calendar"
DEFAULT_APK = Path(__file__).resolve().parents[2] / "app/build/outputs/apk/debug/app-debug.apk"


def install(apk: Path) -> bool:
    out = adb("install", "-r", "-g", str(apk))
    return "Success" in out


def complete_onboarding(ev: Evidence) -> bool:
    """Drive the real onboarding UI to completion (no DataStore surgery).

    Language page picks English explicitly; afterwards we tap every 'Next'
    until the final group page offers 'Later' — tapping Later completes
    onboarding without persisting a half-made group, then the group is seeded
    below. Pre-granted permissions make the a11y page show 'Enabled'.
    """
    open_app(APPAUSE)
    # Re-running setup on an already-onboarded app must PASS, so first check
    # for home-screen markers instead of assuming the language page.
    if wait_for("New group|Your Groups|Service active", timeout=8):
        ev.mark("onboarding: already completed, home screen shown")
        return True
    if not wait_for("Choose your language", timeout=20):
        ev.mark("onboarding: neither home nor first onboarding screen appeared")
        return False
    tap("English", timeout=4)
    if wait_for("Later", timeout=3):
        # Already onboarded from a previous run (app went straight to home).
        return wait_for("New group", timeout=10)
    for step in range(12):
        if tap("Next", timeout=3):
            ev.mark(f"onboarding: tapped Next (step {step})")
            time.sleep(0.7)
            continue
        if tap("Later", timeout=3):
            ev.mark("onboarding: completed via Later")
            return wait_for("New group", timeout=10)
        ev.mark(f"onboarding: stuck at step {step}, no Next/Later visible")
        return False
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", default=str(DEFAULT_APK))
    parser.add_argument("--group-name", default="Campaign")
    parser.add_argument("--cooldown", type=int, default=20)
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()

    root = Path(args.evidence)
    root.mkdir(parents=True, exist_ok=True)
    ev = Evidence(root, time.strftime("setup-%Y%m%d-%H%M%S"))
    ok = True

    if not wait_for_boot(timeout=60):
        ev.mark("FAIL: emulator not booted")
        return 2
    ev.mark("boot: emulator online")

    ok &= ev.verdict("install", install(Path(args.apk)), f"apk={args.apk}")
    if not ok:
        return ev.finish()

    grant_runtime_permissions()
    ev.mark("permissions: a11y + usage + alert + battery exemption granted")
    time.sleep(2.0)
    ok &= ev.verdict("service-bound", service_bound())

    ok &= ev.verdict("onboarding", complete_onboarding(ev))

    ok &= ev.verdict(
        "group-seed",
        seed_pause_group(args.group_name, [TARGET_A, TARGET_B], args.cooldown),
        f"name={args.group_name} cooldown={args.cooldown}",
    )

    # The home screen must RENDER the seeded rows — proves the app's own Room
    # read path sees what sqlite3 wrote (not just that the file changed).
    # The group card can sit below the fold when the 'Finish setup' card is
    # shown, so scroll the list before giving up (first run failed exactly
    # there: card existed, node search simply never saw it off-screen).
    open_app(APPAUSE)
    visible = wait_for(args.group_name, timeout=6)
    if not visible:
        from campaign_lib import adb_shell
        adb_shell("input swipe 540 1600 540 600 300")
        visible = wait_for(args.group_name, timeout=8)
    ok &= ev.verdict("group-visible-in-ui", visible)

    # First interception smoke: the precondition for every scenario.
    reset_appause()
    shown = None
    open_app(TARGET_A)
    deadline = time.time() + 20
    while time.time() < deadline and shown is None:
        out = adb("logcat", "-d", "-v", "epoch", "AppauseA11yService:V", "OverlayManager:V", "*:S")
        if f"Overlay shown for {TARGET_A}" in out:
            shown = "logcat"
        elif appause_overlay_attached():
            shown = "window"
        time.sleep(0.5)
    screenshot(ev.dir / "smoke-intercept.png")
    ok &= ev.verdict("smoke-intercept", shown is not None, str(shown))

    reset_appause()
    ev.mark(f"setup {'OK' if ok else 'BROKEN'} — evidence: {ev.dir}")
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
