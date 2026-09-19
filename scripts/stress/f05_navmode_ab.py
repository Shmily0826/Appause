"""F-05 A/B probe: does the pause overlay receive BACK depending on navigation mode?

Hypothesis under test (from verify-M persistent log): with navigation_mode=0
(3-button) an injected KEYCODE_BACK reaches the overlay's OnBackInvokedCallback
(backCB FIRED), while with navigation_mode=2 (gesture) it never does.

Method per mode:
  reset -> clear plog -> launch deskclock -> wait for overlay + cooldown
  -> capture focused window -> inject one KEYCODE_BACK
  -> read plog markers (backCB FIRED / onKeyEvent / Overlay dismissed)
  -> record overlay attached state
No uiautomator dump (F-06: dump kills the service).

Usage: python f05_navmode_ab.py --evidence evidence/verify-N
"""
import argparse
import pathlib
import time

import campaign_lib as lib


def plog_tail():
    out = lib.adb_shell(f"run-as {lib.APPAUSE} cat files/appause-service.log")
    return out or ""


def markers(text):
    return {
        "onKeyEvent": text.count("onKeyEvent BACK"),
        "backCB_FIRED": text.count("backCB FIRED"),
        "dismissed": text.count("Overlay dismissed"),
    }


def show_overlay(ev, tag):
    lib.reset_appause(timeout=15)
    before = plog_tail()
    lib.adb_shell("am start -n com.google.android.deskclock/com.android.deskclock.DeskClock")
    deadline = time.time() + 20
    shown = False
    while time.time() < deadline:
        if "Overlay shown for" in plog_tail()[len(before):]:
            shown = True
            break
        time.sleep(0.5)
    time.sleep(3.5)  # past the 2s pause-shown guard
    ev.append(f"[{tag}] overlay shown={shown} focus={lib.focused_window()}")
    return shown


def test_mode(ev, mode):
    lib.settings_put("secure", "navigation_mode", str(mode))
    time.sleep(2)
    lib.screenshot(ev.dir / f"navmode{mode}-home.png")
    if not show_overlay(ev, f"mode{mode}"):
        ev.append(f"[mode {mode}] INCONCLUSIVE: overlay never shown")
        return None
    before = markers(plog_tail())
    focus_before = lib.focused_window()
    attached_before = lib.appause_overlay_attached()
    lib.key("KEYCODE_BACK")
    time.sleep(2.5)
    after = markers(plog_tail())
    attached_after = lib.appause_overlay_attached()
    delta = {k: after[k] - before[k] for k in after}
    ev.append(f"[mode {mode}] focus_before={focus_before} attached_before={attached_before}")
    ev.append(f"[mode {mode}] after BACK keyevent: markers={delta} attached_after={attached_after}")
    lib.screenshot(ev.dir / f"navmode{mode}-after-back.png")
    return delta, attached_before, attached_after


class Ev:
    pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--evidence", required=True)
    args = ap.parse_args()
    ev = Ev()
    ev.dir = pathlib.Path(args.evidence)
    ev.dir.mkdir(parents=True, exist_ok=True)
    ev.lines = []
    ev.append = lambda s: (ev.lines.append(s), print(s))

    r0 = test_mode(ev, 0)
    r2 = test_mode(ev, 2)
    r0b = test_mode(ev, 0)  # repeat mode 0 to check determinism

    verdict = "INCONCLUSIVE"
    if r0 and r2:
        d0, d2 = r0[0], r2[0]
        dismissed0 = d0["backCB_FIRED"] > 0 or (r0[1] and not r0[2])
        dismissed2 = d2["backCB_FIRED"] > 0 or (r2[1] and not r2[2])
        ev.append(f"dismissed mode0={dismissed0} mode2={dismissed2} keyFilterOnKeyEvent={d0['onKeyEvent']+d2['onKeyEvent']}")
        if dismissed0 and not dismissed2:
            verdict = "NAV-MODE DEPENDENT: 3-button back reaches overlay, gesture back does not"
        elif dismissed0 and dismissed2:
            verdict = "BOTH WORK with injected keyevent (earlier J2 failure may be gesture-swipe-specific)"
        elif not dismissed0 and not dismissed2:
            verdict = "BOTH FAIL (earlier 12:22 backCB FIRED not reproduced)"
    ev.append(f"RESULT: {verdict}")
    (ev.dir / "ab.txt").write_text("\n".join(ev.lines) + f"\nVERDICT: {verdict}\n", encoding="utf-8")
    # leave device in gesture mode as found
    lib.settings_put("secure", "navigation_mode", "2")


if __name__ == "__main__":
    main()
