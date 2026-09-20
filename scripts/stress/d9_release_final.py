"""G5 device batch — RC 0.5.44 final acceptance on HyperOS (auto parts).

Run mode:
  d9_release_final.py install   — overwrite-install RC 96 over the user's 95,
                                  verify version/appops/a11y binding, snapshot
                                  F-26-relevant device state (whitelist, adj).
  d9_release_final.py latency   — intercept-latency probes on the user's REAL
                                  groups (xhs/bili), timing launch->overlay.
  d9_release_final.py idleadj   — read release process oom adj right now
                                  (run after the user's idle window).
  d9_release_final.py restore   — final zero-residue + focus + nav-mode report.

Safety: pinned serial, release package only, install -r (never uninstall/-d),
never read user DB (non-debuggable anyway), taps only via HOME key (escape) —
Continue/Cancel taps happen only inside latency probes on overlay coords.
"""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import device_lib as D

RC_APK = Path(__file__).parents[2] / "output/Appause-v0.5.44.apk"
REL = "com.appause.android"
XHS, BILI = "com.xingin.xhs", "tv.danmaku.bili"


def version_code() -> str:
    out = D.shell(f"dumpsys package {REL} | grep versionCode | head -1")
    return out.strip()


def appops_trio() -> str:
    out = D.shell("dumpsys appops")
    lines = out.splitlines()
    try:
        start = next(i for i, l in enumerate(lines) if l.strip() == f"Package {REL}:")
    except StopIteration:
        return "NO appops package block (all default!)"
    seg = "\n".join(lines[start:start + 60])
    return " | ".join(
        op for op in ("GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW", "RUN_ANY_IN_BACKGROUND")
        if f"{op} (allow)" in seg) or "MISSING-ALLOW " + seg[:120]


def a11y_bound() -> str:
    out = D.shell("dumpsys accessibility")
    enabled = REL in D.shell("settings get secure enabled_accessibility_services")
    return f"enabled_in_settings={enabled}"


def release_adj() -> str:
    pid = D.shell(f"pidof {REL}").strip()
    if not pid:
        return "no-process"
    return D.shell(f"cat /proc/{pid.splitlines()[0]}/oom_score_adj").strip()


def whitelist() -> str:
    out = D.shell("dumpsys deviceidle whitelist")
    return REL if REL in out else "NOT-WHITELISTED"


def latency(pkg: str) -> float:
    D.launch(pkg)
    t0 = time.monotonic()
    end = t0 + 30
    while time.monotonic() < end:
        if D.overlay_present():
            return time.monotonic() - t0
        time.sleep(0.15)
    return -1.0


def main() -> int:
    D.assert_phone()
    mode = sys.argv[1] if len(sys.argv) > 1 else "install"
    if mode == "install":
        print("pre :", version_code(), "| ops:", appops_trio(),
              "| whitelist:", whitelist())
        D.adb("install", "-r", str(RC_APK))
        time.sleep(3)
        print("post:", version_code(), "| ops:", appops_trio(),
              "| whitelist:", whitelist())
        print("a11y:", a11y_bound(), "| adj:", release_adj(),
              "| focus:", D.focus_pkg())
    elif mode == "latency":
        for pkg in (XHS, BILI):
            if not D.overlay_present():
                lat = latency(pkg)
                print(f"{pkg}: intercept latency {lat:.2f}s" if lat >= 0
                      else f"{pkg}: NO OVERLAY in 30s")
                D.home(); time.sleep(2.5)
                print(f"   home-dismiss ok={not D.overlay_present()}")
        print("adj:", release_adj())
    elif mode == "idleadj":
        D.ensure_awake()
        print("adj right now:", release_adj(), "| bound:", a11y_bound())
    elif mode == "restore":
        D.home(); time.sleep(2)
        print("residual windows:", D.appause_windows())
        print("focus:", D.focus_pkg(), "| nav:", D.nav_mode(),
              "| version:", version_code())
    else:
        print("mode?")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
