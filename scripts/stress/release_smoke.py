"""Release-build smoke: F-27 re-verification + Recents regression on HyperOS.

Why: F-27 fix (commit 64125a5) was verified on the debug build only. This
proves it in the signed release APK installed over the user's real v0.5.43.
Release is non-debuggable, so oracles are limited to window state + focus +
screenshots (no plog, no Room). The user's "social media" group must contain
the target apps; we never tap Continue/Cancel, so no user data is written —
every escape is via HOME (real user behavior).

Steps per app:
  S1 intercept     — launch, overlay must appear within --arm-timeout
  S2 F-27 escape   — HOME while overlay up => overlay gone + launcher focus
                     in ONE home press (the exact F-27 symptom)
  S3 re-arm        — relaunch => overlay again (escape didn't break arming)
  S4 escape again  — leave app cleanly at home
R3 Recents (last app only):
  T1 overlay up -> APP_SWITCH -> snapshot recents state
  T2 HOME from recents => zero residual overlay windows
"""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import device_lib as D

EV = Path(__file__).parent / "evidence" / "release-smoke"
LAUNCHERS = {"com.miui.home", "com.google.android.launcher"}


def wait_overlay(want: bool, timeout: float) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        if D.overlay_present() == want:
            return True
        time.sleep(0.5)
    return False


def step(name: str, ok: bool, note: str = "") -> bool:
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {note}", flush=True)
    return ok


def main() -> int:
    ap_packages = sys.argv[1:] or ["com.xingin.xhs", "tv.danmaku.bili"]
    timeout = 25.0
    EV.mkdir(parents=True, exist_ok=True)
    D.assert_phone()
    D.ensure_awake()
    results: list[bool] = []

    for pkg in ap_packages:
        # S1 intercept
        D.launch(pkg)
        got = wait_overlay(True, timeout)
        D.screenshot(EV / f"s1-{pkg}.png")
        results.append(step(f"S1 intercept {pkg}", got,
                            f"windows={D.appause_windows()}"))
        if not got:
            print(f"  !! {pkg} not intercepted — is it in the 'social media' "
                  "group AND pause master-switch ON? Skipping this app.")
            D.home()
            time.sleep(2)
            continue

        # S2 F-27: one HOME press must fully dismiss
        D.home()
        gone = wait_overlay(False, 5.0)
        time.sleep(1.0)
        focus = D.focus_pkg()
        D.screenshot(EV / f"s2-{pkg}.png")
        results.append(step(f"S2 F-27 home-escape {pkg}", gone and focus in LAUNCHERS,
                            f"gone={gone} focus={focus}"))

        # S3 re-arm after escape
        D.launch(pkg)
        rearmed = wait_overlay(True, timeout)
        results.append(step(f"S3 re-arm {pkg}", rearmed))

        # S4 clean escape
        D.home()
        wait_overlay(False, 5.0)
        time.sleep(1.0)

    # R3: Recents path regression (on the last app that intercepted)
    target = ap_packages[-1]
    D.launch(target)
    if wait_overlay(True, timeout):
        D.shell("input keyevent KEYCODE_APP_SWITCH")
        time.sleep(2.0)
        D.screenshot(EV / f"t1-recents-{target}.png")
        up_in_recents = D.overlay_present()
        print(f"[INFO] T1 overlay visible while in Recents: {up_in_recents}")
        D.home()
        gone = wait_overlay(False, 5.0)
        time.sleep(1.0)
        focus = D.focus_pkg()
        D.screenshot(EV / f"t2-home-{target}.png")
        results.append(step("T2 recents->home leaves zero overlay",
                            gone and focus in LAUNCHERS,
                            f"gone={gone} focus={focus}"))
    else:
        print(f"[SKIP] T1 {target} not intercepted, recents test skipped")

    # Final residue check
    residual = D.appause_windows()
    results.append(step("FINAL zero residual windows", not residual, f"{residual}"))
    ok = all(results)
    print(f"\n=== RELEASE SMOKE: {'ALL PASS' if ok else 'FAILURES'} "
          f"({sum(results)}/{len(results)}) ===")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
