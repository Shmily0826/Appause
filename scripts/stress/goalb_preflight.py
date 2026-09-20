"""Goal-B Phase-B PREFLIGHT — run this FIRST, before d7/d8, on the phone.

Encodes every environment lesson from the 2026-09-20 night session (F-24/25/26):
a run is only trustworthy when the phone is charging, the service process is
NOT frozen (oom_score_adj < 200), the reboot-wiped appops grants are restored,
the doze whitelist entry is back, and a timed intercept actually fires within
15 s of foregrounding the target. Any red line -> fix that first, do NOT
launch the probe battery (its results would be environment garbage).

Exit 0 = green, proceed with d7 --skip-seed then d8.
"""
import sys
import time

import device_lib as D

FAIL = 0


def check(name: str, ok: bool, detail: str = "", fix: str = "") -> None:
    global FAIL
    mark = "OK  " if ok else "FAIL"
    print(f"[{mark}] {name}  {detail}")
    if not ok:
        FAIL = 1
        if fix:
            print(f"        fix: {fix}")


def main() -> int:
    D.assert_phone()

    lvl = D.shell("dumpsys battery | grep 'level:").strip()
    pct = int(lvl.split(":")[1].strip()) if ":" in lvl else -1
    ac = "AC powered: true" in D.shell("dumpsys battery") or "usb powered: true" in \
        D.shell("dumpsys battery")
    check("charging", bool(ac), f"level={pct}%", "plug the cable in")

    pid = D.shell(f"pidof {D.DEBUG}").strip()
    check("debug process alive", bool(pid), pid or "(not running)")
    if pid:
        adj = D.shell(f"cat /proc/{pid.split()[0]}/oom_score_adj").strip()
        try:
            frozen = int(adj) >= 200
        except ValueError:
            frozen = True
        check("process NOT frozen (adj<200)", not frozen, f"adj={adj}",
              "MIUI battery: set Appause Debug to No restrictions + autostart; "
              "keep screen on")

    for op in ("GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW", "RUN_ANY_IN_BACKGROUND"):
        state = D.shell(f"appops get {D.DEBUG} {op}")
        if "allow" not in state:
            D.shell(f"appops set {D.DEBUG} {op} allow")
            state = D.shell(f"appops get {D.DEBUG} {op}")
        check(f"appops {op}", "allow" in state, state.strip().replace("\r", ""))

    wl = D.shell(f"dumpsys deviceidle whitelist | grep {D.DEBUG}")
    if D.DEBUG not in wl:
        D.shell(f"dumpsys deviceidle whitelist +{D.DEBUG}")
        wl = D.shell(f"dumpsys deviceidle whitelist | grep {D.DEBUG}")
    check("doze whitelist", D.DEBUG in wl, wl.strip().replace("\r", ""))

    check("a11y service bound", D.service_running())

    # timing gate: a fresh grouped launch must pause within 15 s
    D.home(); time.sleep(1)
    D.shell("am start -n com.xingin.xhs/.index.v2.IndexActivityV2")
    t0 = time.monotonic(); hit = False
    while time.monotonic() - t0 < 15:
        if D.overlay_present():
            hit = True
            break
        time.sleep(0.3)
    dt = time.monotonic() - t0
    check("timely intercept (<=15s)", hit, f"{dt:.1f}s")
    if hit:
        D.tap(460, 1860); time.sleep(2)
        check("cancel clean", not D.overlay_present())
    D.home()
    print("\nPREFLIGHT", "GREEN — run d7/d8 now" if FAIL == 0 else "RED — fix above first")
    return FAIL


if __name__ == "__main__":
    sys.exit(main())
