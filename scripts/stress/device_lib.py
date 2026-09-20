#!/usr/bin/env python3
"""Goal-B device primitives for the Xiaomi HyperOS phone (serial 6036d5b).

Why a separate lib from campaign_lib (which is emulator-pinned):
  * MIUI suppresses third-party logcat -> the oracle is PersistentLog
    files/appause-service.log via run-as (debug slot only) + dumpsys window
    + screencap. (FINDINGS D-session facts 2/4.)
  * `am force-stop` silently REVOKES the a11y grant and re-grant needs the
    manual UI flow -> this lib NEVER force-stops, NEVER uninstalls, NEVER
    clears. DB/prefs edits are done pull -> host-edit -> push -> REBOOT
    (process dead during reboot = no write race; a11y grant survives
    reboot, proven by D4).
  * uiautomator dump returns null root here -> all UI reading is screencap;
    all tapping is device-coordinate `input tap`.
  * The user's RELEASE install (com.appause.android) holds real data and is
    non-debuggable: it is only ever observed through window/focus state.
    Every WRITE path is pinned to com.appause.android.debug.

Privacy guard: SERIAL is asserted against ro.serialno at import, commands
go through adb() which refuses targets other than the phone, and no
notification/content-provider/other-app data is ever read.
"""

from __future__ import annotations

import os
import re
import subprocess
import time
from pathlib import Path

SERIAL = "6036d5b"                      # the ONLY device this lib may touch
DEBUG = "com.appause.android.debug"     # our writable test slot
RELEASE = "com.appause.android"         # user's real app: OBSERVE ONLY
XHS = "com.xingin.xhs"
BILI = "tv.danmaku.bili"               # the REAL bilibili app (installed;
                                       # com.bilibili.studio is 必剪 the editor)
CONTROL = "com.bilibili.studio"        # grouped=NO -> false-positive probe

ADB_BIN = os.environ.get("ADB", "adb")
# git-bash mangles /data paths unless told not to convert them
ENV = {**os.environ, "MSYS_NO_PATHCONV": "1"}

PLOG = f"files/appause-service.log"     # inside the debug app's sandbox


def adb(*args: str, binary: bool = False):
    """adb pinned to SERIAL. binary=True returns raw bytes (screencap).
    NOTE: passing encoding= alone forces text mode in CPython, so the text
    kwargs must be omitted entirely for the binary path."""
    cmd = [ADB_BIN, "-s", SERIAL, *args]
    kw = {"capture_output": True, "env": ENV}
    if binary:
        return subprocess.run(cmd, **kw).stdout
    proc = subprocess.run(cmd, text=True, encoding="utf-8", errors="replace", **kw)
    return proc.stdout


def shell(cmd: str) -> str:
    return adb("shell", cmd)


def assert_phone() -> None:
    """Refuse to run against anything that is not the known test phone."""
    serial = shell("getprop ro.serialno").strip()
    model = shell("getprop ro.product.model").strip()
    if serial != SERIAL:
        raise SystemExit(f"ABORT: connected device is {serial!r}/{model!r}, "
                         f"expected {SERIAL!r} — refusing to touch any other device")
    print(f"[device] {model} serial={serial} OK")


# ---------------------------------------------------------------------------
# state oracles (device-flavored; logcat is suppressed on MIUI so we do NOT
# use it — PersistentLog + dumpsys are the authoritative sources)
# ---------------------------------------------------------------------------

def plog_tail(lines: int = 400) -> str:
    """Tail of the debug app's persistent service log (run-as works: debuggable)."""
    return adb("exec-out", f"run-as {DEBUG} tail -n {lines} {PLOG}")


def plog_has(pattern: str, since_len: int = 0, tail: str | None = None) -> str | None:
    """Return the last matching plog line (search only the NEW tail slice)."""
    text = tail if tail is not None else plog_tail()
    if since_len:
        text = "\n".join(text.splitlines()[-since_len:])
    hits = [ln for ln in text.splitlines() if re.search(pattern, ln)]
    return hits[-1] if hits else None


def appause_windows() -> list[str]:
    """PAUSE-OVERLAY windows only. HyperOS dumpsys layout differs from the
    emulator (the mToken line sits ~9 lines below the header), so match the
    window BLOCK: header 'Window #N Window{... appause ...}' plus a following
    line carrying ty=ACCESSIBILITY_OVERLAY / type=2032 within 12 lines.
    MainActivity blocks carry ty=BASE_APPLICATION and are excluded."""
    out = shell("dumpsys window windows").splitlines()
    hits: list[str] = []
    for i, line in enumerate(out):
        s = line.strip()
        if s.startswith("Window #") and "appause" in s:
            block = "\n".join(out[i + 1:i + 13])
            if "ty=ACCESSIBILITY_OVERLAY" in block or "type=2032" in block:
                hits.append(s)
    return hits


def overlay_present() -> bool:
    return len(appause_windows()) > 0


def focus_pkg() -> str:
    out = shell("dumpsys window | grep mCurrentFocus")
    m = re.search(r"u0\s+([a-zA-Z0-9._]+)/", out)
    if m:
        return m.group(1)
    # HyperOS: mCurrentFocus can be a bare PopupWindow/overlay name — fall
    # back to mFocusedApp, which always carries the task's package.
    out2 = shell("dumpsys window | grep mFocusedApp | head -1")
    m2 = re.search(r"u0\s+([a-zA-Z0-9._]+)/", out2)
    return m2.group(1) if m2 else ""


def service_running() -> bool:
    """Debug a11y service alive: pidof + bound entry in dumpsys accessibility."""
    pid = shell(f"pidof {DEBUG}").strip()
    if not pid:
        return False
    out = shell("dumpsys accessibility")
    return DEBUG in out


def screenshot(path: Path) -> None:
    data = adb("exec-out", "screencap", "-p", binary=True)
    # exec-out can still smear \n -> \r\n on some builds; PNG magic check keeps it honest
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


# ---------------------------------------------------------------------------
# actions
# ---------------------------------------------------------------------------

def launch(pkg: str) -> None:
    ensure_awake()
    shell(f"monkey -p {pkg} -c android.intent.category.LAUNCHER 1")


def ensure_awake() -> None:
    """The test phone is not charging; HyperOS sleeps fast and a sleeping
    screen silently voids every launch probe. Wake + swipe-up (works while
    the 'show PIN after sleep' policy stays satisfied by the user's unlock)."""
    if "Awake" in shell("dumpsys power | grep mWakefulness="):
        return
    shell("input keyevent KEYCODE_WAKEUP")
    time.sleep(1.5)
    shell("input swipe 540 2000 540 900 250")  # dismiss the aod/keyguard peek
    time.sleep(1.5)


def home() -> None:
    shell("input keyevent KEYCODE_HOME")


def back() -> None:
    shell("input keyevent KEYCODE_BACK")


def tap(x: int, y: int) -> None:
    """DEVICE coordinates (screenshot px x1.2 when converting from an image)."""
    shell(f"input tap {x} {y}")


def nav_mode() -> str:
    return shell("settings get secure navigation_mode").strip()


def set_nav_mode(mode: str) -> None:
    shell(f"settings put secure navigation_mode {mode}")


def reboot_and_wait(timeout_s: float = 180) -> bool:
    """Reboot is our 'clean restart' — the a11y grant SURVIVES it (D4), unlike
    force-stop which silently revokes it. Waits for boot + appause autostart.

    WARNING (Goal-B run 1): the phone has a SECURE keyguard — after reboot no
    automation can unlock it, so the a11y service may sit unbound behind the
    lock screen. Only use this when the user can unlock afterwards; prefer
    restart_via_kill() below for unattended flows."""
    shell("reboot")
    time.sleep(20)
    deadline = time.time() + timeout_s
    while time.monotonic() < deadline:
        if "1" in shell("getprop sys.boot_completed"):
            time.sleep(25)  # D4: service auto-binds within ~30 s of boot
            return True
        time.sleep(3)
    return False


def restart_via_kill(timeout_s: float = 40) -> bool:
    """Kill the debug process WITHOUT the force-stop grant revocation: a bare
    `kill` leaves enabled_accessibility_services untouched, and the system
    rebinds the accessibility service, which re-reads DataStore on start.
    Returns True when the service is back and bound."""
    pid = shell(f"pidof {DEBUG}").strip().split()
    if pid:
        shell(f"kill {pid[0]}")
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        time.sleep(2)
        if service_running():
            time.sleep(3)  # let onServiceConnected finish its DataStore read
            return True
    return False


# ---------------------------------------------------------------------------
# Room / DataStore injection (pull -> host-edit -> push -> reboot)
# ---------------------------------------------------------------------------

# All paths below are SANDBOX-RELATIVE (run-as roots them at /data/data/<pkg>/)
DB_REL = "databases/appause.db"
PREFS_REL = "files/datastore/settings.preferences_pb"


def pull(local: Path, rel: str) -> Path:
    """exec-out must be a CLIENT-side subcommand (D5 fact: embedding it in
    `adb shell` returns empty). run-as cat gives us the raw bytes."""
    raw = adb("exec-out", f"run-as {DEBUG} cat {rel}", binary=True)
    # exec-out on some builds smears \n -> \r\n in TEXT mode; binary=True
    # avoids that entirely, so what we write is byte-exact.
    local.parent.mkdir(parents=True, exist_ok=True)
    local.write_bytes(raw)
    return local


def push(local: Path, rel: str) -> None:
    tmp = f"/data/local/tmp/goalb_{Path(rel).name}"
    adb("push", str(local), tmp)
    shell(f"run-as {DEBUG} cp {tmp} {rel}")
    # drop WAL/SHM so the rebooted process reads OUR file, not a stale journal
    shell(f"run-as {DEBUG} rm -f {rel}-wal {rel}-shm")
    shell(f"rm -f {tmp}")


# NOTE: host-side sqlite editing lives in the d7/d8 scripts (they import the
# stdlib sqlite3 and the p3 protobuf helpers); this lib only moves bytes.
