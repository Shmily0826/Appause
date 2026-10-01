"""R2: regression for the 2026-10 P0 batches B2/B5 on a real interception loop.

Why this exists: B2 (shared timer/signal maps -> ConcurrentHashMap) and B5
(PauseIntentFactory for all three pause launch paths) touch the interception
main chain, and the only evidence so far is unit tests. This drives the chain
that a user actually walks and reads logcat + window state as the oracle.

Probes:
  CANCEL-REARM   intercept -> Cancel -> must land on the launcher -> reopening
                 the target must intercept again (Cancel must not leave a
                 bypass/overlay leak behind).
  CONTINUE-STAY  intercept -> wait out the cooldown -> Continue -> staying in
                 the target must NOT pop a second pause (duplicate
                 interception / window leak).
  LEAVE-RETURN   after Continue, go Home, come back -> decision is logged;
                 we record the log line instead of guessing the leave window.

Oracles are window-state + logcat. No uiautomator dump is ever issued while an
overlay is up (it destroys the a11y service and the pause screen with it), so
overlay taps are coordinate-only at 1080x2340.
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import campaign_lib as C  # noqa: E402
import device_lib as D  # noqa: E402

# device_lib is hard-pinned to the physical Xiaomi (privacy guard: it refuses
# any other serial). This harness runs on the disposable emulator instead, so
# re-point it here — otherwise every adb call silently targets an offline
# device and returns "" (which reads as "no overlay" and fakes a FAIL).
D.SERIAL = os.environ.get("APPAUSE_CAMPAIGN_SERIAL", "emulator-5554")

TARGET = "com.google.android.deskclock"
# Measured 2026-10-02 on Appause_Campaign_API34 (1080x2340) with
# r2_button_scan.py: Continue -> 'Bypass started', Cancel -> launcher focus.
# The skill table's (540,1713) for Cancel is dead space on this build.
CANCEL = (540, 1700)
CONTINUE = (540, 1580)
LAUNCHERS = {"com.google.android.apps.nexuslauncher", "com.google.android.launcher",
             "com.android.launcher", "com.android.launcher3"}

TAGS = "AppauseA11yService:V OverlayManager:V InterceptionManager:V PauseActivity:V"


def logcat() -> str:
    out = D.shell(f"logcat -d -v epoch -s {TAGS}")
    return out or ""


def clear_logcat() -> None:
    D.shell("logcat -c")


def wait_overlay(want: bool, timeout: float) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        if D.overlay_present() == want:
            return True
        time.sleep(0.5)
    return False


def step(name: str, ok: bool, note: str = "") -> bool:
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}{(' — ' + note) if note else ''}")
    return ok


def probe_cancel_rearm(ev: Path) -> bool:
    print("CANCEL-REARM")
    ok = True
    C.reset_appause()
    clear_logcat()
    D.launch(TARGET)
    ok &= step("intercept on open", wait_overlay(True, 20), f"focus={D.focus_pkg()}")
    # Settle before tapping: right after attach the overlay's input channel can
    # still swallow the tap (measured ~1 in 3 runs without this), which reads
    # as "Cancel does nothing" — a harness artefact, not a product failure.
    time.sleep(2.0)
    D.screenshot(ev / "r1-overlay.png")

    D.tap(*CANCEL)
    ok &= step("overlay dismissed by Cancel", wait_overlay(False, 10))
    time.sleep(1.0)
    focus = D.focus_pkg()
    ok &= step("Cancel went to launcher", any(l in focus for l in LAUNCHERS) or "launcher" in focus.lower(), f"focus={focus}")
    D.screenshot(ev / "r1-after-cancel.png")

    clear_logcat()
    D.launch(TARGET)
    ok &= step("re-intercept after Cancel", wait_overlay(True, 20), f"focus={D.focus_pkg()}")
    D.screenshot(ev / "r1-rearm.png")
    tail = logcat()
    for marker in ("INTERCEPT", "Overlay shown"):
        if marker in tail:
            print(f"    logcat saw: {marker}")
    (ev / "r1-logcat.txt").write_text(tail, encoding="utf-8")
    return ok


def probe_continue_stay(ev: Path, cooldown: int) -> bool:
    print("CONTINUE-STAY")
    ok = True
    C.reset_appause()
    clear_logcat()
    D.launch(TARGET)
    ok &= step("intercept on open", wait_overlay(True, 20))

    # The Continue button is disabled until the countdown finishes.
    print(f"    waiting out the {cooldown}s cooldown...")
    time.sleep(cooldown + 6)
    D.tap(*CONTINUE)
    ok &= step("overlay dismissed by Continue", wait_overlay(False, 10))
    time.sleep(1.0)
    ok &= step("target is front after Continue", TARGET in D.focus_pkg(), f"focus={D.focus_pkg()}")
    D.screenshot(ev / "r2-after-continue.png")

    clear_logcat()
    stayed = not wait_overlay(True, 10)
    ok &= step("no duplicate pause while staying in target", stayed)
    tail = logcat()
    for marker in ("Pause screen already showing", "Overlay shown", "SKIP", "RESUME"):
        if marker in tail:
            print(f"    logcat saw: {marker}")
    (ev / "r2-logcat.txt").write_text(tail, encoding="utf-8")
    return ok


def probe_leave_return(ev: Path) -> bool:
    print("LEAVE-RETURN")
    C.reset_appause()
    clear_logcat()
    D.home()
    time.sleep(12)
    D.launch(TARGET)
    shown = wait_overlay(True, 20)
    tail = logcat()
    for marker in ("SKIP", "RESUME", "INTERCEPT", "Overlay shown"):
        if marker in tail:
            print(f"    logcat saw: {marker}")
    (ev / "r3-logcat.txt").write_text(tail, encoding="utf-8")
    D.screenshot(ev / "r3-return.png")
    return step("decision after leaving and returning", shown,
                "overlay shown" if shown else "suppressed (leave window / bypass) — see logcat")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--probes", default="CANCEL-REARM,CONTINUE-STAY,LEAVE-RETURN")
    p.add_argument("--cooldown", type=int, default=20)
    p.add_argument("--evidence", default=str(Path(__file__).parent / "evidence" / "r2-p0-regression"))
    args = p.parse_args()

    ev = Path(args.evidence)
    ev.mkdir(parents=True, exist_ok=True)
    print(f"evidence: {ev}")

    results = {}
    if "CANCEL-REARM" in args.probes:
        results["CANCEL-REARM"] = probe_cancel_rearm(ev)
    if "CONTINUE-STAY" in args.probes:
        results["CONTINUE-STAY"] = probe_continue_stay(ev, args.cooldown)
    if "LEAVE-RETURN" in args.probes:
        results["LEAVE-RETURN"] = probe_leave_return(ev)

    bad = [k for k, v in results.items() if not v]
    print(f"\n{'ALL PASS' if not bad else 'FAILURES: ' + ', '.join(bad)}")
    return 0 if not bad else 1


if __name__ == "__main__":
    sys.exit(main())
