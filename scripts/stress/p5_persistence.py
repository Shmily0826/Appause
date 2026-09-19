"""P5: persistence across data-preserving reinstall and reboot.

Two probes, both observing the same state snapshot (Room rows that drive
interception + the DataStore prefs file):

  REINSTALL  `adb install -r -t` the SAME debug apk (versionCode unchanged,
             like a hotfix reflash) -> groups/settings must survive and the
             a11y service must be usable again (auto-rebind expected; if it
             needs a manual toggle we record that as a C-class observation
             and recover through Settings).
  REBOOT     full `adb reboot` -> boot completes, a11y service auto-binds
             (system rebinds enabled services), Room/DataStore survive,
             interception fires again.

Emulator-only evidence: never extrapolate HyperOS boot-rebind behaviour
from these results.
"""
from __future__ import annotations

import argparse
import hashlib
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    adb,
    adb_shell,
    expect_intercept,
    go_home,
    reset_appause,
    service_bound,
)
from p3_pass_expiry import TARGET_A, read_prefs

APK = Path(__file__).resolve().parents[2] / "app/build/outputs/apk/debug/app-debug.apk"


def snapshot(ev: Evidence, tag: str) -> dict[str, str]:
    """Observable persistence state: the rows interception depends on + prefs."""
    snap = {
        "groups": adb_shell(
            f"run-as {APPAUSE} sqlite3 databases/appause.db "
            "\"SELECT name||'|'||accessibility_policy||'|'||cooldown_seconds FROM app_groups ORDER BY name;\""
        ).replace("\r", ""),
        "group_apps": adb_shell(
            f"run-as {APPAUSE} sqlite3 databases/appause.db \"SELECT COUNT(*) FROM group_apps;\""
        ).strip().replace("\r", ""),
        "prefs_md5": hashlib.md5(read_prefs(ev.dir / f"prefs_{tag}.pb")).hexdigest(),
    }
    ev.mark(f"snapshot[{tag}]: groups={snap['groups'].strip()!r} "
            f"group_apps={snap['group_apps']} prefs_md5={snap['prefs_md5'][:8]}")
    return snap


def wait_boot(ev: Evidence, timeout: int = 180) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if adb_shell("getprop sys.boot_completed").strip() == "1":
                ev.mark(f"boot completed after {int(timeout - (deadline - time.monotonic()))}s")
                return True
        except Exception:  # noqa: BLE001 - device link drops during reboot
            pass
        time.sleep(5)
    return False


def wait_bound(ev: Evidence, timeout: int = 60) -> bool:
    """Poll the a11y auto-rebind; mark how long it took so C-class latency is visible."""
    start = time.monotonic()
    while time.monotonic() - start < timeout:
        if service_bound():
            ev.mark(f"service auto-bound after {time.monotonic() - start:.1f}s")
            return True
        time.sleep(3)
    return False


def probe_reinstall(ev: Evidence) -> None:
    before = snapshot(ev, "pre-reinstall")
    out = adb("install", "-r", "-t", str(APK))
    ev.mark(f"install -r output: {out.strip().splitlines()[-1] if out.strip() else 'empty'}")
    auto = wait_bound(ev, 40)
    if not auto:
        ev.mark("REINSTALL: a11y did not auto-rebind; recovering via Settings (C-class note)")
        reset_appause()
    after = snapshot(ev, "post-reinstall")
    ev.verdict("P5-reinstall-data", before == after,
               f"diff before={before} after={after}" if before != after else "")
    ok = expect_intercept(TARGET_A, timeout=25)
    go_home()
    ev.verdict("P5-reinstall-interception", ok and auto, "" if ok and auto
               else f"intercept={ok} auto_rebind={auto} (auto_rebind False => C-class emulator note)")


def probe_reboot(ev: Evidence) -> None:
    before = snapshot(ev, "pre-reboot")
    adb("reboot")
    if not wait_boot(ev):
        ev.verdict("P5-reboot-data", False, "device never finished booting")
        return
    time.sleep(15)  # let the launcher settle before judging anything
    adb_shell("wm dismiss-keyguard")
    auto = wait_bound(ev, 60)
    if not auto:
        ev.mark("REBOOT: a11y did not auto-bind after boot")
    after = snapshot(ev, "post-reboot")
    ev.verdict("P5-reboot-data", before == after,
               f"diff before={before} after={after}" if before != after else "")
    ok = expect_intercept(TARGET_A, timeout=25)
    go_home()
    ev.verdict("P5-reboot-interception", ok and auto, "" if ok and auto
               else f"intercept={ok} auto_bind={auto}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probes", default="REINSTALL,REBOOT")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    if not APK.exists():
        raise SystemExit(f"APK missing: {APK} (run assembleDebug first)")
    ev = Evidence(Path(args.evidence), "p5-persistence")
    mapping = {"REINSTALL": probe_reinstall, "REBOOT": probe_reboot}
    for name in args.probes.split(","):
        fn = mapping.get(name.strip().upper())
        if fn:
            fn(ev)
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
