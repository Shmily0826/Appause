#!/usr/bin/env python3
"""Shared campaign primitives: ADB actions + machine-checkable oracles.

Why this exists: ui_stress.py predates the reliability campaign and can only
produce evidence for a HUMAN to judge. Unattended overnight runs need every
scenario to decide PASS/FAIL from observable state instead, so every campaign
script builds on the same small set of oracles defined here:

  * focused window / package    (dumpsys window mCurrentFocus)
  * Appause overlay attached?   (dumpsys window windows for the debug pkg)
  * service bound?              (dumpsys activity services)
  * logcat marker appeared?     (epoch logcat grep)
  * Room/DataStore state        (sqlite3 through run-as, debug build only)

All destructive operations target the disposable emulator only.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

APPAUSE = "com.appause.android.debug"
SERVICE_COMPONENT = f"{APPAUSE}/com.appause.android.service.AppauseAccessibilityService"
LAUNCHER_HINTS = ("com.google.android.apps.nexuslauncher", "launcher", "Launcher")

ADB_BIN = os.environ.get("ADB", "adb")
REMOTE_XML = "/sdcard/campaign.xml"


def adb(*args: str, check: bool = False) -> str:
    proc = subprocess.run(
        [ADB_BIN, *args], capture_output=True, text=True,
        encoding="utf-8", errors="replace",
    )
    if check and proc.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


def adb_shell(cmd: str) -> str:
    return adb("shell", cmd)


def wait_for_boot(timeout: float = 240.0) -> bool:
    """Block until the emulator finished booting (sys.boot_completed == 1)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if "1" in adb_shell("getprop sys.boot_completed"):
            return True
        time.sleep(2.0)
    return False


# ---------------------------------------------------------------------------
# UI primitives (Compose: match by text/content-desc, never resource-id)
# ---------------------------------------------------------------------------

def nodes() -> list[dict]:
    """Every labelled node in the current window (uiautomator dump)."""
    adb_shell(f"uiautomator dump {REMOTE_XML} >/dev/null")
    local = Path(os.environ.get("CAMPAIGN_TMP", ".")) / "campaign.xml"
    local.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([ADB_BIN, "pull", REMOTE_XML, str(local)], capture_output=True)
    try:
        root = ET.parse(local)
    except Exception:
        return []
    out: list[dict] = []
    for node in root.iter("node"):
        text = node.get("text") or ""
        desc = node.get("content-desc") or ""
        if not text and not desc:
            continue
        raw = node.get("bounds") or ""
        numbers = [int(n) for n in re.findall(r"-?\d+", raw)]
        if len(numbers) != 4:
            continue
        out.append({
            "label": text or desc,
            "bounds": numbers,
            "clickable": node.get("clickable") == "true",
        })
    return out


def find(pattern: str) -> dict | None:
    needle = re.compile(pattern, re.IGNORECASE)
    hits = [n for n in nodes() if needle.search(n["label"])]
    if not hits:
        return None
    return next((n for n in hits if n["clickable"]), hits[0])


def tap(pattern: str, timeout: float = 6.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        hit = find(pattern)
        if hit:
            x1, y1, x2, y2 = hit["bounds"]
            adb_shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
            return True
        time.sleep(0.4)
    return False


def wait_for(pattern: str, timeout: float = 15.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if find(pattern):
            return True
        time.sleep(0.5)
    return False


# ---------------------------------------------------------------------------
# Device state oracles
# ---------------------------------------------------------------------------

def open_app(package: str) -> None:
    adb_shell(f"monkey -p {package} 1 >/dev/null 2>&1")


def key(code: str) -> None:
    adb_shell(f"input keyevent {code}")


def go_home() -> None:
    key("KEYCODE_HOME")


def focused_window() -> str:
    return adb_shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -4")


def foreground_package() -> str:
    """Package owning the focused window, '' when unknown/launcher."""
    out = adb_shell("dumpsys window windows | grep -E 'mCurrentFocus|FocusedWindow'")
    match = re.search(r"u0\s+([a-zA-Z0-9._]+)/", out)
    return match.group(1) if match else ""


def appause_overlay_attached() -> bool:
    """True when an Appause-owned window is currently attached.

    The pause screen lives in a WindowManager overlay owned by OUR package
    (2032/2038), so its window shows up under the debug package name. PauseActivity
    fallback also counts — both mean a pause is on screen.
    """
    out = adb_shell(f"dumpsys window windows | grep -c '{APPAUSE}'")
    try:
        return int(out.strip() or 0) > 0
    except ValueError:
        return APPAUSE in out


def service_bound() -> bool:
    out = adb_shell(f"dumpsys activity services {APPAUSE}")
    return SERVICE_COMPONENT.split("/")[1] in out


def app_pid(package: str = APPAUSE) -> str:
    out = adb_shell(f"pidof {package}")
    return out.strip()


def start_service_via_settings(enabled: bool) -> None:
    """Toggle our component in ENABLED_ACCESSIBILITY_SERVICES.

    This is the emulator-equivalent of the user flipping the switch in
    Settings → Accessibility, including the rebind-without-process-death path
    (R1) that the campaign needs to exercise.
    """
    if enabled:
        adb_shell(f"settings put secure enabled_accessibility_services {SERVICE_COMPONENT}")
        adb_shell("settings put secure accessibility_enabled 1")
    else:
        adb_shell("settings put secure enabled_accessibility_services \"\"")
        adb_shell("settings put secure accessibility_enabled 0")


def reset_appause(timeout: float = 15.0) -> bool:
    """Known-clean state: drop memory state + rebind the service.

    Same reasoning as ui_stress.reset_appause: bypass/session/guard state is
    in-process memory, force-stop is the only reliable way to clear it, and it
    wipes the secure-settings registration that must be restored afterwards.
    """
    adb_shell(f"am force-stop {APPAUSE}")
    time.sleep(1.5)
    start_service_via_settings(True)
    deadline = time.time() + timeout
    while time.time() < deadline:
        if service_bound():
            go_home()
            time.sleep(0.5)
            return True
        time.sleep(0.5)
    return False


def logcat_clear() -> None:
    adb("logcat", "-c")


def logcat_match(pattern: str, timeout: float = 15.0, tags: tuple[str, ...] = (
    "AppauseA11yService", "OverlayManager", "PauseActivity",
    "InterceptionManager", "PauseAlarmReceiver",
)) -> str | None:
    """Poll the logcat ring buffer for a marker; return the matching line."""
    needle = re.compile(pattern)
    filters = [f"{t}:V" for t in tags] + ["*:S"]
    deadline = time.time() + timeout
    while time.time() < deadline:
        out = adb("logcat", "-d", "-v", "epoch", *filters)
        for line in out.splitlines():
            if needle.search(line):
                return line
        time.sleep(0.5)
    return None


def expect_intercept(package: str, timeout: float = 20.0) -> str | None:
    """PASS oracle for 'the target got paused': overlay log line OR live window."""
    logcat_clear()
    open_app(package)
    deadline = time.time() + timeout
    while time.time() < deadline:
        line = logcat_match(rf"Overlay shown for {re.escape(package)}", timeout=0.1)
        if line:
            return line
        line = logcat_match(rf"PauseActivity.*{re.escape(package)}", timeout=0.1)
        if line:
            return line
        if appause_overlay_attached() and foreground_package() == APPAUSE:
            return f"live-window: {foreground_package()}"
        time.sleep(0.5)
    return None


def expect_no_intercept(package: str, settle: float = 6.0) -> bool:
    """PASS oracle for 'must NOT be intercepted': target stays foreground."""
    logcat_clear()
    open_app(package)
    time.sleep(settle)
    shown = logcat_match(r"Overlay shown for", timeout=0.1)
    front = foreground_package()
    return shown is None and front == package


# ---------------------------------------------------------------------------
# Room / DataStore observation (run-as works because the debug build is debuggable)
# ---------------------------------------------------------------------------

def db(sql: str) -> str:
    return adb_shell(f"run-as {APPAUSE} sqlite3 databases/appause.db \"{sql}\"")


def seed_pause_group(name: str, packages: list[str], cooldown_seconds: int) -> bool:
    """Create a PAUSE group directly in Room's SQLite file.

    Why direct seeding: the group-editor UI (slider drag) is flaky to automate
    unattended, and the interception decision reads Room per event, so a seeded
    row is indistinguishable from a UI-created one for the scenarios we run.
    The UI creation path itself is exercised separately in P6.
    """
    now_ms = int(time.time() * 1000)
    db(f"DELETE FROM app_groups WHERE name='{name}';")
    out = db(
        "INSERT INTO app_groups (name, cooldownSeconds, createdAt, type,"
        " reRemindMinutes, reRemindCooldownSeconds, reRemindRepeat, reRemindEscalate)"
        f" VALUES ('{name}', {cooldown_seconds}, {now_ms}, 'pause', 0, 0, 1, 0);"
        " SELECT last_insert_rowid();"
    )
    match = re.search(r"\d+", out)
    if not match:
        return False
    gid = match.group(0)
    for pkg in packages:
        db(f"INSERT OR REPLACE INTO group_apps (packageName, groupId) VALUES ('{pkg}', {gid});")
    check = db(f"SELECT g.name||'/'||a.packageName FROM app_groups g JOIN group_apps a ON a.groupId=g.id WHERE g.id={gid};")
    return all(pkg in check for pkg in packages)


def set_group_field(name: str, column: str, value: str) -> str:
    """Update one column of a named group — used by P4/P6 scenarios."""
    allowed = {"cooldownSeconds", "reRemindMinutes", "reRemindCooldownSeconds",
               "reRemindRepeat", "reRemindEscalate", "type", "name"}
    if column not in allowed:
        raise ValueError(f"column {column} not seedable")
    return db(f"UPDATE app_groups SET {column}={value} WHERE name='{name}'; SELECT changes();")


def data_store_dump() -> str:
    """Raw DataStore preferences file as escaped bytes — good enough for
    grepping string values like temporary pass entries."""
    return adb_shell(
        f"run-as {APPAUSE} sh -c 'for f in data/data/{APPAUSE}/files/datastore/*;"
        " do echo == $f; cat $f | tr -c \"[:print:]\" \"\\n\"; done'"
    )


def settings_put(kind: str, keyname: str, value: str) -> None:
    adb_shell(f"settings put {kind} {keyname} {value}")


def grant_runtime_permissions() -> None:
    """Pre-grant everything the permission onboarding would ask for.

    appops/deviceidle are the emulator-reliable equivalents of the toggles a
    user flips in system settings; the campaign cares about behaviour WITH the
    permission, and the settings round-trip itself is tested in P2 separately.
    """
    start_service_via_settings(True)
    adb_shell(f"appops set {APPAUSE} GET_USAGE_STATS allow")
    adb_shell(f"appops set {APPAUSE} SYSTEM_ALERT_WINDOW allow")
    adb_shell(f"dumpsys deviceidle whitelist +{APPAUSE}")


def screenshot(path: Path) -> None:
    proc = subprocess.run([ADB_BIN, "exec-out", "screencap", "-p"], capture_output=True)
    if proc.returncode == 0 and proc.stdout:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(proc.stdout)


class Evidence:
    """Per-scenario evidence bundle: markers, screenshots, dumpsys, verdicts."""

    def __init__(self, root: Path, run_id: str):
        self.dir = root / run_id
        self.dir.mkdir(parents=True, exist_ok=True)
        self.results: dict[str, str] = {}
        self.log = (self.dir / "actions.log").open("a", encoding="utf-8")

    def mark(self, note: str) -> None:
        stamp = time.strftime("%H:%M:%S")
        self.log.write(f"{stamp} | {note}\n")
        self.log.flush()
        print(f"  {note}")

    def dump_state(self, tag: str) -> None:
        (self.dir / f"{tag}.window.txt").write_text(
            adb_shell("dumpsys window windows | grep -E 'Window #|mCurrentFocus|type='"),
            encoding="utf-8", errors="replace")
        (self.dir / f"{tag}.activity.txt").write_text(
            adb_shell("dumpsys activity activities | grep -E 'Resumed|mFocused' | head -20"),
            encoding="utf-8", errors="replace")
        (self.dir / f"{tag}.a11y.txt").write_text(
            adb_shell("dumpsys accessibility | head -60"), encoding="utf-8", errors="replace")

    def save_logcat(self, tag: str, tags: tuple[str, ...] = ()) -> None:
        filters = [f"{t}:V" for t in tags] + ["*:S"] if tags else []
        (self.dir / f"{tag}.logcat.txt").write_text(
            adb("logcat", "-d", "-v", "epoch", *filters), encoding="utf-8", errors="replace")

    def verdict(self, scenario: str, passed: bool, detail: str = "") -> bool:
        self.results[scenario] = "PASS" if passed else "FAIL"
        self.mark(f"{scenario}: {'PASS' if passed else 'FAIL'} {detail}")
        if not passed:
            self.dump_state(scenario + "-FAIL")
            self.save_logcat(scenario + "-FAIL", ("AppauseA11yService", "OverlayManager",
                                                  "PauseActivity", "AndroidRuntime"))
            screenshot(self.dir / f"{scenario}-FAIL.png")
        return passed

    def finish(self) -> int:
        with (self.dir / "results.json").open("w", encoding="utf-8") as handle:
            json.dump(self.results, handle, indent=2)
        self.log.close()
        failed = [s for s, v in self.results.items() if v == "FAIL"]
        print(f"\n{len(self.results) - len(failed)}/{len(self.results)} PASS"
              + (f" — FAILURES: {', '.join(failed)}" if failed else ""))
        return 1 if failed else 0
