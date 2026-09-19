"""F-05 fix probe: does the accessibility key filter deliver Back to onKeyEvent?

Two Back sources are compared against the same shown overlay:
  1. `adb shell input keyevent KEYCODE_BACK`  -> untrusted INJECTED key
     (goes straight into InputDispatcher, may bypass the a11y input filter)
  2. tapping SystemUI's 3-button navigation Back button -> SystemUI itself
     produces the KEYCODE_BACK (trusted, like a real user press)

The oracle is the persistent service log marker "onKeyEvent BACK" plus the
window-attached state; uiautomator dump is never used (F-06 kills the service).
"""
import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import campaign_lib as lib

TARGET = "com.google.android.deskclock"
PLOG = "files/appause-service.log"


def plog_tail(n: int = 2000) -> str:
    out = lib.adb_shell(f"run-as {lib.APPAUSE} cat {PLOG}")
    return "\n".join(out.splitlines()[-n:])


def back_marks() -> int:
    return plog_tail().count("onKeyEvent BACK")


def show_overlay(ev_dir: Path) -> bool:
    lib.reset_appause()
    lib.adb("shell", "am", "start", "-n", f"{TARGET}/com.android.deskclock.DeskClock")
    deadline = time.time() + 12
    while time.time() < deadline:
        if "Overlay shown for" in plog_tail(30):
            break
        time.sleep(0.3)
    time.sleep(3.0)  # let the 2s cooldown finish
    shown = lib.appause_overlay_attached()
    (ev_dir / "shown.txt").write_text(f"attached={shown}\n{plog_tail(10)}")
    return shown


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--evidence", required=True)
    args = ap.parse_args()
    ev = Path(args.evidence)
    ev.mkdir(parents=True, exist_ok=True)

    mode = lib.adb_shell("settings get secure navigation_mode").strip()
    print(f"navigation_mode={mode} (0 = 3-button)")
    if mode != "0":
        lib.settings_put("secure", "navigation_mode", "0")
        time.sleep(2)

    marks0 = back_marks()
    if not show_overlay(ev):
        print("RESULT: BLOCKED (overlay did not show)")
        return 2

    lib.adb_shell("input keyevent KEYCODE_BACK")
    time.sleep(1.5)
    marks1 = back_marks()
    att1 = lib.appause_overlay_attached()
    print(f"after adb keyevent: onKeyEvent marks +{marks1 - marks0}, attached={att1}")

    # 3-button nav bar bottom-left Back button, 1080x2340 screen.
    lib.adb_shell("input tap 180 2275")
    time.sleep(1.5)
    marks2 = back_marks()
    att2 = lib.appause_overlay_attached()
    print(f"after nav-bar tap: onKeyEvent marks +{marks2 - marks1}, attached={att2}")
    lib.screenshot(ev / "after-nav-tap.png")
    (ev / "probe.txt").write_text(
        f"inject_marks={marks1 - marks0}\nnav_tap_marks={marks2 - marks1}\n"
        f"attached_after_inject={att1}\nattached_after_nav={att2}\n")

    if marks2 - marks0 > 0 and not att2:
        print("RESULT: PASS — trusted Back reached onKeyEvent and dismissed overlay")
    elif marks2 - marks0 > 0:
        print("RESULT: PARTIAL — onKeyEvent fired but overlay still attached")
    else:
        print("RESULT: FAIL — onKeyEvent never fired, even for trusted nav Back")
    lib.reset_appause()
    return 0


if __name__ == "__main__":
    sys.exit(main())
