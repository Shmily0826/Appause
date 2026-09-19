#!/usr/bin/env python3
"""Appause human-like multi-app stress test driver (ADB orchestration).

Goal APPAUSE_HUMAN_LIKE_MULTI_APP_STRESS_TEST_V1.

Emulates realistic user navigation across several apps while Appause is
showing (or about to show) a cooldown pause screen, and records the evidence
needed to decide whether a duplicate interception / countdown reset happens:

  * Appause debug logcat for every Appause-owned tag
  * a screenshot after each scenario step

Everything is driven through adb only. Each scenario is deterministic (fixed
app sequence); the pauses are randomised inside a human-like 200-1500 ms band
so runs are not byte-identical but stay reproducible in structure.

Usage:
    python ui_stress.py [--scenarios S1,S2] [--runs 1] [--cooldown 20]

Prerequisites (set up once by setup_device.py):
    * Appause debug APK installed with the accessibility service enabled
    * at least two target apps configured in a PAUSE group
"""

from __future__ import annotations

import argparse
import os
import random
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------------------
# Device + app inventory (AOSP emulators expose all of these)
# ---------------------------------------------------------------------------

APPAUSE = "com.appause.android.debug"

# Two apps configured inside one PAUSE group -> both get intercepted.
TARGET_A = "com.google.android.deskclock"       # Clock
TARGET_B = "com.google.android.calendar"        # Calendar
# Ordinary app: never intercepted.
PLAIN = "com.google.android.apps.messaging"     # Messages
# Chatty app: emits lots of window/foreground state changes.
CHATTY = "com.android.chrome"                   # Chrome
SETTINGS = "com.android.settings"

HOME_KEY = "KEYCODE_HOME"
RECENTS_KEY = "KEYCODE_APP_SWITCH"
BACK_KEY = "KEYCODE_BACK"
POWER_KEY = "KEYCODE_POWER"

EXPECTED_COOLDOWN_SECONDS = 20

# Every Appause tag that carries interception evidence.
LOGCAT_TAGS = (
    "AppauseA11yService",
    "OverlayManager",
    "PauseActivity",
    "InterceptionManager",
    "PauseAlarmReceiver",
    "AccessibilityHealth",
)


# The emulator SDK's platform-tools is usually not on PATH, so allow an
# override:  ADB=/path/to/adb.exe python ui_stress.py
ADB_BIN = os.environ.get("ADB", "adb")
# Pin ALL traffic to the disposable emulator; a connected real phone must never
# be silently targeted (serial ambiguity also breaks commands outright).
SERIAL = os.environ.get("APPAUSE_CAMPAIGN_SERIAL", "emulator-5554")


def adb(*args: str, check: bool = False) -> str:
    """Run one adb command and return stdout."""
    proc = subprocess.run(
        [ADB_BIN, "-s", SERIAL, *args], capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if check and proc.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


# ---------------------------------------------------------------------------
# Human-like timing
# ---------------------------------------------------------------------------


def human(min_ms: int = 200, max_ms: int = 1500) -> None:
    """Pause for a short, human-plausible amount of time."""
    time.sleep(random.uniform(min_ms, max_ms) / 1000.0)


def pause(seconds: float) -> None:
    """Deterministic pause (used for countdown-wait steps)."""
    time.sleep(seconds)


# ---------------------------------------------------------------------------
# Primitive device actions
# ---------------------------------------------------------------------------


def open_app(package: str) -> None:
    """Bring one app to the foreground the way a launcher icon would."""
    adb("shell", "monkey", "-p", package, "1")


def key(code: str) -> None:
    adb("shell", "input", "keyevent", code)


def go_home() -> None:
    key(HOME_KEY)


def open_recents() -> None:
    key(RECENTS_KEY)


def press_back() -> None:
    key(BACK_KEY)


def open_notifications() -> None:
    adb("shell", "cmd", "statusbar", "expand-notifications")


def close_notifications() -> None:
    adb("shell", "cmd", "statusbar", "collapse")


def rotate(landscape: bool) -> None:
    """Rotate the device; drives Appause's configuration-change path."""
    # 0 disables auto-rotate so the user-rotation below actually sticks.
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "settings", "put", "system", "user_rotation", "1" if landscape else "0")


def lock_and_unlock() -> None:
    """Exercise a normal screen-off/on return without changing app state."""
    key(POWER_KEY)
    pause(1.0)
    key(POWER_KEY)
    pause(1.0)
    adb("shell", "input", "swipe", "540", "1800", "540", "500", "250")


def screenshot(path: Path) -> None:
    """Save a screenshot. Uses exec-out so no device temp file is needed."""
    proc = subprocess.run([ADB_BIN, "-s", SERIAL, "exec-out", "screencap", "-p"], capture_output=True)
    if proc.returncode == 0 and proc.stdout:
        path.write_bytes(proc.stdout)


def mark(directory: Path, note: str) -> None:
    """Append a human-readable marker line to the run log."""
    stamp = time.strftime("%H:%M:%S")
    with (directory / "actions.log").open("a", encoding="utf-8") as handle:
        handle.write(f"{stamp} | {note}\n")


# ---------------------------------------------------------------------------
# Clean-state reset
# ---------------------------------------------------------------------------

SERVICE_COMPONENT = f"{APPAUSE}/com.appause.android.service.AppauseAccessibilityService"


def service_bound() -> bool:
    """True when the accessibility service is actually running on the device."""
    out = adb("shell", "dumpsys", "activity", "services", APPAUSE)
    return SERVICE_COMPONENT.split("/")[1] in out


def reset_appause(directory: Path, timeout: float = 15.0) -> bool:
    """Return Appause to a known-clean state before a scenario.

    Why this is mandatory: bypass is in-process memory. A previous scenario
    that ended with the user "continuing" leaves the target in the bypass set,
    and the next open then legitimately logs
    `RESUME: <pkg> (returned within leave window)` instead of intercepting.
    That is correct behaviour, but it silently invalidates the scenario — a
    fake "no interception" result that looks like a bug.

    force-stop is the only way to drop that memory state. It also removes the
    service from Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, so the
    component must be re-registered afterwards; this is verified by polling
    for the real ServiceRecord rather than sleeping and hoping.
    """
    adb("shell", "am", "force-stop", APPAUSE)
    time.sleep(1.5)
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services",
        SERVICE_COMPONENT)
    deadline = time.time() + timeout
    while time.time() < deadline:
        if service_bound():
            mark(directory, "reset: Appause clean (service re-bound)")
            go_home()
            human()
            return True
        time.sleep(0.5)
    mark(directory, "reset: WARNING service did not re-bind")
    return False


# ---------------------------------------------------------------------------
# Logcat capture
# ---------------------------------------------------------------------------


@dataclass
class Capture:
    """One background logcat pull for the whole run."""

    directory: Path
    proc: subprocess.Popen = field(repr=False, default=None)  # type: ignore[assignment]

    def start(self) -> None:
        adb("logcat", "-c")
        # -v epoch gives absolute timestamps so cross-tag ordering is exact.
        # Deliberately NO shell=True: with a shell, terminate() kills the shell
        # and the adb child survives the run, silently appending events from
        # LATER runs into this run's evidence file.
        tag_filters = [f"{t}:D" for t in LOGCAT_TAGS] + ["*:S"]
        self.proc = subprocess.Popen(
            [ADB_BIN, "-s", SERIAL, "logcat", "-v", "epoch", *tag_filters],
            stdout=(self.directory / "logcat.txt").open("wb"),
            stderr=subprocess.DEVNULL,
        )
        # Give logcat a moment to attach before the first action.
        pause(1.0)

    def stop(self) -> None:
        if not self.proc:
            return
        self.proc.terminate()
        try:
            self.proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.proc.kill()
            self.proc.wait(timeout=5)


# ---------------------------------------------------------------------------
# Scenarios — each returns the list of step names it ran
# ---------------------------------------------------------------------------


def scenario_1(d: Path) -> None:
    """Pause A -> count ~3s -> Recents -> app B -> back to A."""
    open_app(TARGET_A)
    mark(d, "S1 open target A")
    pause(3.0)
    open_recents()
    mark(d, "S1 recents")
    human()
    open_app(TARGET_B)
    mark(d, "S1 open app B")
    human()
    open_app(TARGET_A)
    mark(d, "S1 return to A")


def scenario_2(d: Path) -> None:
    """Pause A -> Home -> reopen A immediately -> Home -> A."""
    open_app(TARGET_A)
    mark(d, "S2 open target A")
    human()
    go_home()
    mark(d, "S2 home")
    human(200, 600)
    open_app(TARGET_A)
    mark(d, "S2 reopen A (immediate)")
    human(200, 600)
    go_home()
    mark(d, "S2 home again")
    human(200, 600)
    open_app(TARGET_A)
    mark(d, "S2 reopen A again")


def scenario_3(d: Path) -> None:
    """A -> Continue -> B -> A -> B -> A."""
    open_app(TARGET_A)
    mark(d, "S3 open target A")
    human()
    open_app(TARGET_B)
    mark(d, "S3 switch to target B")
    human()
    open_app(TARGET_A)
    mark(d, "S3 back to A")
    human()
    open_app(TARGET_B)
    mark(d, "S3 B again")
    human()
    open_app(TARGET_A)
    mark(d, "S3 A again")


def scenario_4(d: Path) -> None:
    """Pause A -> Back / Home / Recents interleaved."""
    open_app(TARGET_A)
    mark(d, "S4 open target A")
    human()
    press_back()
    mark(d, "S4 back")
    human()
    go_home()
    mark(d, "S4 home")
    human()
    open_recents()
    mark(d, "S4 recents")
    human()
    press_back()
    mark(d, "S4 back")
    human()
    open_app(TARGET_A)
    mark(d, "S4 reopen A")


def scenario_5(d: Path) -> None:
    """Pause A -> notification shade -> close."""
    open_app(TARGET_A)
    mark(d, "S5 open target A")
    human()
    open_notifications()
    mark(d, "S5 expand notifications")
    human()
    close_notifications()
    mark(d, "S5 collapse notifications")
    human()
    open_app(TARGET_A)
    mark(d, "S5 return to A")


def scenario_6(d: Path) -> None:
    """Pause A -> rotate -> back to the target app."""
    open_app(TARGET_A)
    mark(d, "S6 open target A")
    human()
    rotate(landscape=True)
    mark(d, "S6 rotate landscape")
    human()
    open_app(TARGET_A)
    mark(d, "S6 return to A (landscape)")
    human()
    rotate(landscape=False)
    mark(d, "S6 rotate portrait")
    human()
    open_app(TARGET_A)
    mark(d, "S6 return to A (portrait)")


def scenario_7(d: Path) -> None:
    """Pause A -> switch to controlled app B -> back to A."""
    open_app(TARGET_A)
    mark(d, "S7 open target A")
    human()
    open_app(TARGET_B)
    mark(d, "S7 open target B (also controlled)")
    human()
    open_app(TARGET_A)
    mark(d, "S7 back to A")


def scenario_8(d: Path) -> None:
    """Rapid leave / return against the target app."""
    for i in range(4):
        open_app(TARGET_A)
        mark(d, f"S8 burst {i} open A")
        human(200, 500)
        open_app(PLAIN)
        mark(d, f"S8 burst {i} leave to plain app")
        human(200, 500)


def scenario_9(d: Path) -> None:
    """Flood the event stream while the pause screen is up."""
    open_app(TARGET_A)
    mark(d, "S9 open target A")
    human()
    open_app(CHATTY)
    mark(d, "S9 open chatty browser")
    human()
    open_app(SETTINGS)
    mark(d, "S9 open settings")
    human(200, 500)
    open_app(CHATTY)
    mark(d, "S9 back to chatty browser")
    human(200, 500)
    open_app(TARGET_A)
    mark(d, "S9 return to A")


def scenario_10(d: Path) -> None:
    """Continuous multi-round switching with no cold restart per round."""
    for round_index in range(3):
        for package, label in (
            (TARGET_A, "target A"),
            (PLAIN, "plain app"),
            (TARGET_B, "target B"),
            (SETTINGS, "settings"),
        ):
            open_app(package)
            mark(d, f"S10 round {round_index} -> {label}")
            human()
    open_app(TARGET_A)
    mark(d, "S10 final return to A")


# Clock has no second launcher-visible Activity, so the watchdog scenario uses
# Calendar: its SearchActivity is a DIFFERENT Activity in the SAME package, so
# starting it emits a genuine TYPE_WINDOW_STATE_CHANGED for the target package
# while the user never leaves the app. Re-starting the already-resumed main
# Activity produces no event at all (verified — the first run of this scenario
# was a no-op because of exactly that).
TARGET_B_SECOND_ACTIVITY = "com.google.android.calendar/.search.SearchActivity"


def intra_app_switch() -> None:
    """Deliver a foreground event for the target WITHOUT leaving the target."""
    adb("shell", "am", "start", "-n", TARGET_B_SECOND_ACTIVITY)


def scenario_11(d: Path) -> None:
    """Hold a 2032 pause past the old 30 s guard cap, then switch away and back.

    Regression target: a primary 2032 overlay must keep the logical guard after
    30 s while the countdown is still running:

        t = 0   pause shown, guard raised, no bypass
        t = 30  2032 still attached -> guard remains raised
        t = 30+ user leaves to another real app -> normal abandon/dismiss

    Returning after that clean abandon may legitimately create a fresh
    interception. The regression failure is a watchdog release or a second
    attachment before a clean dismissal.

    Run with --cooldown 60 so the countdown outlives the watchdog.
    """
    open_app(TARGET_B)
    mark(d, "S11 open target B (calendar), 60s cooldown")
    pause(3.0)
    mark(d, "S11 waiting 33s — past the 30s guard watchdog, countdown still running")
    pause(33.0)
    screenshot(d / "S11-before-leaving.png")
    open_app(PLAIN)
    mark(d, "S11 leave to non-controlled app (overlay expected to STAY)")
    human(500, 900)
    open_app(TARGET_B)
    mark(d, "S11 return to target after clean abandon")
    pause(3.0)
    screenshot(d / "S11-after-return.png")
    mark(d, "S11 captured")
    open_app(PLAIN)
    mark(d, "S11 leave again")
    human(500, 900)
    open_app(TARGET_B)
    mark(d, "S11 return again")
    pause(3.0)
    screenshot(d / "S11-after-second-return.png")
    mark(d, "S11 captured second return")


def scenario_12_control(d: Path) -> None:
    """Control case: the same switching, but INSIDE the guard window.

    Identical app switching to S11, stopped well before the 30 s watchdog, so
    the guard is still genuinely raised. Any second INTERCEPT here would mean
    the guard never worked at all; none here plus findings in S11 would isolate
    the watchdog release as the trigger.
    """
    open_app(TARGET_B)
    mark(d, "S12 open target B (control, inside guard window)")
    pause(2.0)
    for i in range(4):
        open_app(PLAIN)
        mark(d, f"S12 control leave {i}")
        human(200, 400)
        open_app(TARGET_B)
        mark(d, f"S12 control return {i}")
        human(200, 400)
    pause(3.0)
    screenshot(d / "S12-control.png")
    mark(d, "S12 captured")


def scenario_12(d: Path) -> None:
    """Rapid repeated foreground events for the same target app."""
    open_app(TARGET_B)
    mark(d, "S12 open target B")
    pause(2.0)
    for i in range(5):
        intra_app_switch()
        mark(d, f"S12 repeated target event {i}")
        human(200, 400)
    pause(3.0)
    screenshot(d / "S12-after-repeats.png")
    mark(d, "S12 captured")


def scenario_13(d: Path) -> None:
    """Rapid Home -> target returns, like repeated muscle-memory app checking."""
    open_app(TARGET_A)
    mark(d, "S13 open target A")
    pause(2.0)
    for i in range(8):
        go_home()
        mark(d, f"S13 home {i}")
        human(150, 450)
        open_app(TARGET_A)
        mark(d, f"S13 return A {i}")
        human(150, 450)


def scenario_14(d: Path) -> None:
    """Repeated Recents toggling while the target is paused."""
    open_app(TARGET_A)
    mark(d, "S14 open target A")
    pause(2.0)
    for i in range(6):
        open_recents()
        mark(d, f"S14 recents {i}")
        human(200, 500)
        open_app(TARGET_A)
        mark(d, f"S14 return A {i}")
        human(200, 500)


def scenario_15(d: Path) -> None:
    """Lock/unlock the emulator and return to the paused target."""
    open_app(TARGET_A)
    mark(d, "S15 open target A")
    pause(2.0)
    lock_and_unlock()
    mark(d, "S15 lock/unlock")
    human(300, 700)
    open_app(TARGET_A)
    mark(d, "S15 return A")


def scenario_16(d: Path) -> None:
    """Mix rotation, notification shade, and app switching in one pause session."""
    open_app(TARGET_A)
    mark(d, "S16 open target A")
    pause(2.0)
    rotate(landscape=True)
    mark(d, "S16 rotate landscape")
    human(200, 500)
    open_notifications()
    mark(d, "S16 notifications open")
    human(200, 500)
    close_notifications()
    mark(d, "S16 notifications close")
    human(200, 500)
    open_app(PLAIN)
    mark(d, "S16 switch to plain app")
    human(200, 500)
    open_app(TARGET_A)
    mark(d, "S16 return A")
    human(200, 500)
    rotate(landscape=False)
    mark(d, "S16 rotate portrait")


def scenario_17(d: Path) -> None:
    """Stress the last countdown seconds without intentionally leaving target A."""
    open_app(TARGET_A)
    mark(d, "S17 open target A")
    near_end_wait = max(1.0, float(EXPECTED_COOLDOWN_SECONDS) - 5.0)
    mark(d, f"S17 wait {near_end_wait:.1f}s (near countdown end)")
    pause(near_end_wait)
    open_notifications()
    mark(d, "S17 near-end notifications open")
    human(150, 300)
    close_notifications()
    mark(d, "S17 near-end notifications close")
    rotate(landscape=True)
    mark(d, "S17 near-end rotate landscape")
    human(150, 300)
    for i in range(3):
        open_app(TARGET_A)
        mark(d, f"S17 near-end reopen target A {i}")
        human(150, 300)
    rotate(landscape=False)
    mark(d, "S17 near-end rotate portrait")
    pause(6.0)
    screenshot(d / "S17-after-near-end-events.png")


SCENARIOS = {
    "S1": scenario_1,
    "S2": scenario_2,
    "S3": scenario_3,
    "S4": scenario_4,
    "S5": scenario_5,
    "S6": scenario_6,
    "S7": scenario_7,
    "S8": scenario_8,
    "S9": scenario_9,
    "S10": scenario_10,
    "S11": scenario_11,
    "S12": scenario_12,
    # Registered separately because this control scenario was shadowed by a
    # duplicate `scenario_12` definition and silently never ran before.
    "S12C": scenario_12_control,
    "S13": scenario_13,
    "S14": scenario_14,
    "S15": scenario_15,
    "S16": scenario_16,
    "S17": scenario_17,
}


# ---------------------------------------------------------------------------
# Runner
# ---------------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenarios", default=",".join(SCENARIOS))
    parser.add_argument("--runs", type=int, default=1)
    parser.add_argument("--cooldown", type=int, default=20,
                        help="Expected pause duration in seconds (used for wait sizing only).")
    parser.add_argument("--seed", type=int, default=20260916)
    parser.add_argument("--no-reset", action="store_true",
                        help="Skip the per-scenario clean-state reset (faster, but "
                             "bypass state leaks between scenarios).")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()

    global EXPECTED_COOLDOWN_SECONDS
    EXPECTED_COOLDOWN_SECONDS = args.cooldown

    random.seed(args.seed)

    root = Path(args.evidence) / time.strftime("run-%Y%m%d-%H%M%S")
    root.mkdir(parents=True, exist_ok=True)
    print(f"evidence dir: {root}")

    capture = Capture(directory=root)
    capture.start()
    try:
        for run in range(1, args.runs + 1):
            for name in [s.strip() for s in args.scenarios.split(",") if s.strip()]:
                scenario = SCENARIOS.get(name)
                if scenario is None:
                    print(f"unknown scenario {name}", file=sys.stderr)
                    continue
                print(f"[run {run}] {name}")
                mark(root, f"=== {name} START (run {run}) ===")
                if not args.no_reset:
                    reset_appause(root)
                scenario(root)
                mark(root, f"=== {name} END ===")
                screenshot(root / f"{name}-run{run}.png")
                # Let the current countdown (if any) drain so scenarios do not
                # start on top of each other's state.
                pause(args.cooldown + 2)
                go_home()
                human()
    finally:
        capture.stop()

    print(f"done — evidence in {root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
