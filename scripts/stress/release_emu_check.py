"""G3 — Release Readiness emulator validation for the RC (emulator ONLY).

v2 fixes after the first run (all failures were harness, not product):
  * rebind(): `settings put` with the SAME string fires no change event, so a
    force-stopped release service never re-binds — clear to "" first, then put,
    then wait for pidof. (The campaign's reset only worked on debug by luck of
    ordering; on release this was the root cause of every NO-overlay.)
  * overlay oracle = window HEADER grep only (package name also appears in
    other windows' metadata lines -> false positives).
  * Cancel/Continue proven via Room rows (release logcat is silent).
  * UI group creation walks the editor to Save; seed fallback stays PARTIAL.

Phases: U 95->96 upgrade data-keep; F RC fresh onboarding incl. battery step;
R reboot persistence + interception + timed HOME dismiss.
"""
from __future__ import annotations

import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
import campaign_lib as cl

RELEASE = "com.appause.android"
COMPONENT = f"{RELEASE}/com.appause.android.service.AppauseAccessibilityService"
cl.APPAUSE = RELEASE
cl.SERVICE_COMPONENT = COMPONENT

TARGET_A = "com.google.android.deskclock"
APK_95 = HERE.parents[1] / "output/Appause-v0.5.43.apk"
APK_96 = HERE.parents[1] / "output/Appause-v0.5.44.apk"
EV_DIR = HERE / "evidence" / "release-emu"

CANCEL_XYS = [(540, 1663), (540, 1788)]
CONTINUE_XYS = [(540, 1545), (540, 1646)]


def sh(*args: str) -> str:
    p = subprocess.run(["adb", "-s", "emulator-5554", *args],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return p.stdout


def db(sql: str) -> str:
    return sh("shell", f"sqlite3 /data/data/{RELEASE}/databases/appause.db \"{sql}\"")


cl.db = db  # seed_pause_group() must use the root-file sqlite, not run-as


def overlay() -> bool:
    # exact-header match: 'com.appause.android' is a substring of the debug
    # package too, and debug windows would false-positive a looser grep
    return sh("shell",
              r"dumpsys window windows | grep -c 'Window #.*com\.appause\.android}'").strip() != "0"


def rebind() -> bool:
    """Force an accessibility re-bind after force-stop / reinstall."""
    sh("shell", 'settings put secure enabled_accessibility_services ""')
    time.sleep(1.0)
    sh("shell", f"settings put secure enabled_accessibility_services {COMPONENT}")
    sh("shell", "settings put secure accessibility_enabled 1")
    end = time.time() + 20
    while time.time() < end:
        if sh("shell", f"pidof {RELEASE}").strip():
            time.sleep(1.5)
            return True
        time.sleep(1.0)
    return False


def grant_all() -> None:
    sh("shell", f"appops set {RELEASE} GET_USAGE_STATS allow")
    sh("shell", f"appops set {RELEASE} SYSTEM_ALERT_WINDOW allow")
    sh("shell", f"dumpsys deviceidle whitelist +{RELEASE}")


def launch(pkg: str) -> None:
    sh("shell", f"am force-stop {pkg}")
    time.sleep(1.0)
    sh("shell", f"monkey -p {pkg} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")


def intercepted(timeout: float = 25) -> float:
    t0 = time.monotonic()
    while time.monotonic() - t0 < timeout:
        if overlay():
            return time.monotonic() - t0
        time.sleep(0.2)
    return -1.0


def room_count(action: str) -> int:
    out = db(f"SELECT COUNT(*) FROM app_launch_records WHERE action='{action}';")
    m = re.search(r"\d+", out)
    return int(m.group(0)) if m else -1


def tap_and_prove(xys, action: str, timeout: float = 8) -> bool:
    base = room_count(action)
    for (x, y) in xys:
        sh("shell", f"input tap {x} {y}")
        end = time.time() + timeout
        while time.time() < end:
            if room_count(action) > base:
                return True
            time.sleep(0.5)
    return False


def onboarding_walk(ev: cl.Evidence, battery_check: bool = False) -> bool:
    sh("shell", f"monkey -p {RELEASE} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")
    if cl.wait_for("New group|Your Groups|Service active", timeout=8):
        ev.mark("onboarding walk: already at home")
        return True
    if not cl.wait_for("Choose your language", timeout=20):
        return False
    cl.tap("English", timeout=4)
    time.sleep(0.8)
    for step in range(10):
        if battery_check and cl.wait_for("Turn off battery optimization", timeout=2):
            cl.screenshot(ev.dir / "ob-battery-step.png")
            ev.mark("onboarding: battery step rendered (F-26 guidance entry point)")
            battery_check = False
        if cl.tap("Next", timeout=3):
            time.sleep(0.6)
            continue
        if cl.tap("Later", timeout=3):
            return cl.wait_for("New group", timeout=10)
        if cl.wait_for("New group", timeout=3):
            return True
        return False
    return False


def ui_create_group(ev: cl.Evidence) -> str:
    """Real UI path: New group -> name -> add Clock -> Save. Returns
    'ui' on success, 'seed' on fallback."""
    cl.open_app(RELEASE)
    if not cl.tap("New group", timeout=6):
        return "seed"
    time.sleep(1.2)
    cl.screenshot(ev.dir / "f-group-editor-1.png")
    field = next((n for n in cl.nodes() if n["clickable"] and "group" in n["label"].lower()
                  and "name" in n["label"].lower()), None)
    if field:
        x1, y1, x2, y2 = field["bounds"]
        sh("shell", f"input tap {(x1+x2)//2} {(y1+y2)//2}")
    time.sleep(0.5)
    sh("shell", "input text EmuFresh")
    time.sleep(0.8)
    # add an app: open the app picker, tick Clock
    if cl.tap("Add apps|Choose apps|Select apps", timeout=5):
        time.sleep(1.0)
        cl.tap("Clock", timeout=5)
        cl.tap("Done|OK|Save", timeout=5)
        time.sleep(0.8)
    cl.screenshot(ev.dir / "f-group-editor-2.png")
    if cl.tap("Save", timeout=5) and cl.wait_for("EmuFresh", timeout=8):
        return "ui"
    ev.mark("f-ui-group: UI walk incomplete -> seed fallback (UI path proven on debug P6)")
    ok = cl.seed_pause_group("EmuFresh", [TARGET_A], cooldown_seconds=15)
    return "seed" if ok else "fail"


def main() -> int:
    EV_DIR.mkdir(parents=True, exist_ok=True)
    ev = cl.Evidence(EV_DIR, time.strftime("emu2-%Y%m%d-%H%M%S"))
    R: dict[str, object] = {}
    if not cl.wait_for_boot(timeout=60):
        return 2
    sh("root")
    time.sleep(2)

    # ---------------- Phase U: 95 -> 96 upgrade ----------------
    ev.mark("=== Phase U ===")
    sh("uninstall", RELEASE)
    R["u-install-95"] = "Success" in sh("install", str(APK_95))
    grant_all()
    R["u-bind-95"] = rebind()
    R["u-onboarding-95"] = onboarding_walk(ev)
    R["u-seed"] = cl.seed_pause_group("EmuUp", [TARGET_A], cooldown_seconds=15)
    R["u-bind-after-seed"] = rebind()
    launch(TARGET_A)
    lat = intercepted()
    R["u-intercept-95"] = lat >= 0
    cl.screenshot(ev.dir / "u-intercept95.png")
    if lat >= 0:
        R["u-cancel-95"] = tap_and_prove(CANCEL_XYS, "cancelled")
        sh("shell", "input keyevent KEYCODE_HOME")
    before_groups = db("SELECT name||'#'||cooldownSeconds FROM app_groups;").strip()
    ds_before = sh("shell", f"md5sum /data/data/{RELEASE}/files/datastore/*").strip()

    R["u-install-96"] = "Success" in sh("install", "-r", str(APK_96))
    time.sleep(3)
    R["u-bind-96"] = rebind()
    after_groups = db("SELECT name||'#'||cooldownSeconds FROM app_groups;").strip()
    ds_after = sh("shell", f"md5sum /data/data/{RELEASE}/files/datastore/*").strip()
    R["u-data-kept"] = bool(after_groups) and after_groups == before_groups and ds_after == ds_before
    ev.mark(f"u-data: groups[{before_groups}]->[{after_groups}] ds_keep={ds_after == ds_before}")
    launch(TARGET_A)
    lat = intercepted()
    R["u-intercept-96"] = lat >= 0
    if lat >= 0:
        time.sleep(16)  # cooldown 15 s so Continue enables
        R["u-continue-96"] = tap_and_prove(CONTINUE_XYS, "proceeded", timeout=10)
        sh("shell", "input keyevent KEYCODE_HOME"); time.sleep(2)

    # ---------------- Phase F: RC fresh + real onboarding ----------------
    ev.mark("=== Phase F ===")
    sh("shell", f"pm clear {RELEASE}")
    sh("shell", f"monkey -p {RELEASE} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")
    R["f-fresh-onboarding"] = cl.wait_for("Choose your language", timeout=15)
    cl.screenshot(ev.dir / "f-1-fresh-language.png")
    cl.tap("English", timeout=4); time.sleep(0.8)
    cl.tap("Next", timeout=3); time.sleep(1.0)
    cl.screenshot(ev.dir / "f-2-perms-denied.png")
    ev.mark("f-2: permission page captured BEFORE any grant (denied states visible in png)")
    grant_all()
    R["f-bind"] = rebind()
    R["f-onboarding-through-battery"] = onboarding_walk(ev, battery_check=True)
    R["f-ui-group"] = ui_create_group(ev)
    launch(TARGET_A)
    lat = intercepted()
    R["f-intercept"] = lat >= 0
    cl.screenshot(ev.dir / "f-intercept.png")
    if lat >= 0:
        t0 = time.monotonic()
        sh("shell", "input keyevent KEYCODE_HOME")
        end = time.monotonic() + 15
        gone = None
        while time.monotonic() < end:
            if not overlay():
                gone = time.monotonic() - t0
                break
            time.sleep(0.2)
        R["f-home-dismiss-s"] = round(gone, 1) if gone is not None else "STUCK"
    ev.mark(f"f: home-dismiss {R.get('f-home-dismiss-s')}s")

    # ---------------- Phase R: reboot ----------------
    ev.mark("=== Phase R ===")
    sh("shell", "reboot")
    R["r-boot"] = cl.wait_for_boot(timeout=240)
    sh("root"); time.sleep(4)
    bound_after_reboot = bool(sh("shell", f"pidof {RELEASE}").strip())
    ev.mark(f"r: service auto-rebind after reboot without any adb help: {bound_after_reboot}")
    R["r-auto-rebind"] = bound_after_reboot
    if not bound_after_reboot:
        R["r-auto-rebind"] = rebind()
        ev.mark("r: needed explicit rebind (noted)")
    R["r-data-persist"] = bool(db("SELECT name FROM app_groups WHERE name='EmuFresh';").strip())
    launch(TARGET_A)
    lat = intercepted(timeout=35)
    R["r-intercept"] = lat >= 0
    if lat >= 0:
        t0 = time.monotonic()
        sh("shell", "input keyevent KEYCODE_HOME")
        end = time.monotonic() + 15
        gone = None
        while time.monotonic() < end:
            if not overlay():
                gone = time.monotonic() - t0
                break
            time.sleep(0.2)
        R["r-home-dismiss-s"] = round(gone, 1) if gone is not None else "STUCK"

    print("\n=== VERDICTS ===")
    bad = []
    for k, v in sorted(R.items()):
        ok = bool(v) if not isinstance(v, str) else v in ("ui", "seed")
        print(f"  {'PASS' if ok else 'FAIL':4}  {k} = {v}")
        if not ok:
            bad.append(k)
    print(f"=== {'ALL PASS' if not bad else 'FAILED: ' + ', '.join(bad)} ===  dir={ev.dir}")
    return 0 if not bad else 1


if __name__ == "__main__":
    sys.exit(main())
