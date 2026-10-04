"""P11: NON-force-stop process death — the real Android kill semantics.

`am force-stop` is NOT process death: it also REVOKES the accessibility
registration (device_lib header fact) and stops every component, so every
restart built on it overestimates what a plain kill loses. What actually
happens when the OS kills the process (LMK / OOM / 用户侧一键清理):

  * the a11y registration in Settings.Secure SURVIVES -> the system rebinds
    AppauseAccessibilityService automatically,
  * Room + DataStore survive (they are on-disk),
  * InterceptionManager bypass / SessionState / leave timers / re-remind jobs
    are process memory -> lost by design (AGENTS.md: "Process death is
    acceptable"; the user simply sees the cooldown again).

Probes (each records pid BEFORE and AFTER; a case is INVALID unless the pid
really changed — exactly the guard the task demands):

  RUNTIME-LOSS   Continue session (bypass) -> `kill -9` -> rebind -> opening
                 the target MUST re-intercept (bypass is runtime-only and
                 must NOT survive), with at most ONE pause window at any time
                 (no duplicate overlay).
  PASS-SURVIVES  a persisted Temporary Pass (DataStore) -> `kill -9` ->
                 rebind -> while the pass is active the target must NOT be
                 intercepted (persisted state must survive); after the pass
                 is cleared interception must be back.

Emulator-only evidence (google_apis AVD, adb shell may kill the debug uid);
on the HyperOS device a plain `kill` from adb is NOT permitted — that part
stays a manual-validation item.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import time
from pathlib import Path

from campaign_lib import (
    ADB_BIN,
    APPAUSE,
    Evidence,
    SERIAL,
    adb,
    adb_shell,
    expect_intercept,
    expect_no_intercept,
    go_home,
    logcat_clear,
    reset_appause,
    seed_pause_group,
    service_bound,
)
from p3_pass_expiry import (
    PASSES_KEY,
    TARGET_A,
    read_prefs,
    seed_pass,
    set_string_list,
    write_prefs,
)

DEBUG_PKG = APPAUSE


def validate_emulator_identity(serial: str, qemu: str, model: str, device: str) -> str:
    """Accept only a canonical ADB emulator backed by Android's qemu property."""
    if not re.fullmatch(r"emulator-\d+", serial):
        raise ValueError(f"serial {serial!r} is not an emulator serial")
    if qemu.strip() != "1":
        raise ValueError(f"serial {serial!r} is not proven to be an emulator (ro.kernel.qemu={qemu!r})")
    if not model.strip() or not device.strip():
        raise ValueError(f"emulator identity incomplete (model={model!r}, device={device!r})")
    return f"serial={serial} model={model.strip()} device={device.strip()} qemu={qemu.strip()}"


def verify_emulator_target() -> str:
    """Fail closed before any campaign mutation unless ADB proves this is an AVD."""
    print(f"[safety] resolved target serial={SERIAL!r} model=<not queried>")
    # A physical device serial cannot pass this check, so do not even issue a
    # read-only ADB query to a supplied phone serial.
    if not re.fullmatch(r"emulator-\d+", SERIAL):
        raise SystemExit(f"ABORT: process-death campaign requires an emulator serial, got {SERIAL!r}")

    properties: dict[str, str] = {}
    for name in ("ro.kernel.qemu", "ro.product.model", "ro.product.device"):
        proc = subprocess.run(
            [ADB_BIN, "-s", SERIAL, "shell", "getprop", name],
            capture_output=True, text=True, encoding="utf-8", errors="replace",
        )
        if proc.returncode != 0:
            raise SystemExit(
                f"ABORT: cannot verify emulator identity for {SERIAL!r}; "
                f"getprop {name} failed: {proc.stderr.strip()}"
            )
        properties[name] = proc.stdout.strip()

    print(
        "[safety] resolved device: "
        f"serial={SERIAL} model={properties['ro.product.model']!r} "
        f"device={properties['ro.product.device']!r} "
        f"qemu={properties['ro.kernel.qemu']!r}"
    )
    try:
        identity = validate_emulator_identity(
            SERIAL, properties["ro.kernel.qemu"],
            properties["ro.product.model"], properties["ro.product.device"],
        )
    except ValueError as error:
        raise SystemExit(f"ABORT: {error}") from error
    print(f"[safety] emulator verified: {identity}")
    return identity


def pid_of() -> list[str]:
    out = adb_shell(f"pidof {DEBUG_PKG}").strip()
    return [p for p in out.split() if p]


def kill_process(ev: Evidence) -> bool:
    """Kill the process WITHOUT force-stop and prove the PID changed.

    Returns True only when the pid really changed (i.e. this is a genuine
    process-death case). Falls back SIGKILL -> SIGTERM; if neither kills, the
    case is reported invalid instead of silently passing.
    """
    verify_emulator_target()
    pids = pid_of()
    if not pids:
        ev.mark("kill: no running process before kill — case invalid")
        return False
    pid_before = pids[0]

    # `adb shell kill` is EPERM on a non-rooted (Play) AVD — but run-as runs
    # as the APP's own uid, which may kill it. Same SIGKILL semantics as the
    # kernel's LMK kill; nothing force-stop-related touches the package.
    adb_shell(f"run-as {DEBUG_PKG} kill -9 {pid_before}")
    time.sleep(1.5)
    still = pid_of()
    if pid_before in still:
        ev.mark(f"run-as kill -9 did not kill pid {pid_before}; falling back to SIGTERM")
        adb_shell(f"run-as {DEBUG_PKG} kill {pid_before}")
        time.sleep(1.5)
        still = pid_of()
        if pid_before in still:
            ev.mark("kill: process did not die — case invalid")
            return False

    # Wait for the system rebind (Settings.Secure registration survived).
    deadline = time.time() + 40
    pid_after = ""
    while time.time() < deadline:
        time.sleep(2)
        now = pid_of()
        if now and now[0] != pid_before:
            pid_after = now[0]
            break
    if not pid_after:
        ev.mark(f"kill: pid changed to nothing (no rebind within 40s) — case invalid")
        return False
    if not service_bound():
        ev.mark("kill: new process exists but a11y service never re-bound — case invalid")
        return False
    time.sleep(3)  # let onServiceConnected finish its DataStore reads
    ev.mark(f"process death verified: pid {pid_before} -> {pid_after}")
    return True


def wait_rebind_settled(timeout: float = 30.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if service_bound():
            time.sleep(3)
            return True
        time.sleep(1)
    return False


def pause_window_count() -> int:
    """How many Appause-owned windows exist right now (duplicate-overlay guard).

    Counts the debug package's windows in `dumpsys window windows` — after a
    rebind there must be at most ONE pause surface, never two.
    """
    out = adb_shell("dumpsys window windows")
    return len(re.findall(r"Window #\d+ Window\{.*com\.appause\.android\.debug", out))


def probe_runtime_loss(ev: Evidence) -> None:
    verify_emulator_target()
    reset_appause()
    seed_pause_group("P11Death", [TARGET_A], 2)
    logcat_clear()

    # Build a real runtime session: intercept, let the 2s cooldown finish
    # (the countdown-finish callback runs startBypass + session begin).
    if not expect_intercept(TARGET_A, timeout=20):
        ev.verdict("P11-runtime-loss", False, "setup: target was never intercepted")
        return
    time.sleep(3)  # countdown finishes -> "Session start" / "Bypass started"
    from campaign_lib import logcat_match

    if not logcat_match(rf"Bypass started: {TARGET_A}"):
        ev.verdict("P11-runtime-loss", False, "setup: bypass was never started (no runtime state to lose)")
        return
    go_home()
    time.sleep(1)
    max_windows = pause_window_count()
    ev.mark(f"before kill: windows={max_windows}")

    if not kill_process(ev):
        ev.verdict("P11-runtime-loss", False, "kill/rebind not verifiable (see marks)")
        return

    # Runtime-only state MUST be gone: opening the target re-intercepts.
    hit = expect_intercept(TARGET_A, timeout=25)
    go_home()
    time.sleep(1)
    windows_now = pause_window_count()
    ev.mark(f"after rebind + reopen: windows={windows_now}")
    dup_ok = windows_now <= 1
    ev.verdict(
        "P11-runtime-loss",
        bool(hit) and dup_ok,
        "bypass SURVIVED the kill (runtime state leaked!)" if not hit
        else (f"duplicate pause windows: {windows_now}" if not dup_ok else ""),
    )


def probe_pass_survives(ev: Evidence) -> None:
    verify_emulator_target()
    reset_appause()
    now = int(adb_shell("date +%s").strip()) * 1000
    if not seed_pass(ev, now + 10 * 60_000):
        ev.verdict("P11-pass-survives", False, "setup: pass not seeded")
        return
    logcat_clear()

    if not kill_process(ev):
        ev.verdict("P11-pass-survives", False, "kill/rebind not verifiable (see marks)")
        return

    # Persisted state MUST survive: while the pass is active, no interception.
    suppressed = not expect_no_intercept(TARGET_A, settle=8)
    go_home()
    if suppressed:
        ev.verdict("P11-pass-survives", False, "pass was LOST across the kill (intercepted while pass active)")
        return

    # Interception must still work: clear the persisted pass -> next open cools.
    adb_shell("am force-stop " + APPAUSE)
    local = ev.dir / "prefs_clear.pb"
    data = read_prefs(local)
    local.write_bytes(set_string_list(data, PASSES_KEY, []))
    write_prefs(local)
    reset_appause()
    hit = expect_intercept(TARGET_A, timeout=20)
    go_home()
    ev.verdict(
        "P11-pass-survives",
        bool(hit),
        "interception did NOT recover after the pass was cleared" if not hit else "",
    )


def probe_rebind_health(ev: Evidence) -> None:
    """Health semantics after rebind: bound, receiving events, single window."""
    verify_emulator_target()
    reset_appause()
    seed_pause_group("P11Health", [TARGET_A], 2)
    logcat_clear()

    if not kill_process(ev):
        ev.verdict("P11-rebind-health", False, "kill/rebind not verifiable (see marks)")
        return

    from campaign_lib import logcat_match

    connected = logcat_match(r"connected and running")
    windows = pause_window_count()
    hit = expect_intercept(TARGET_A, timeout=25)
    go_home()
    ev.verdict(
        "P11-rebind-health",
        bool(connected) and windows <= 1 and bool(hit),
        "no 'connected and running' log after rebind" if not connected
        else (f"stale windows after rebind: {windows}" if windows > 1
              else "interception dead after rebind" if not hit else ""),
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probes", default="RUNTIME,PASS,HEALTH")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    identity = verify_emulator_target()
    ev = Evidence(Path(args.evidence), "p11-process-death")
    ev.mark(f"verified destructive-action target: {identity}")
    # adb root helps plain `kill` succeed on this verified AVD.
    adb("root")
    time.sleep(1)
    mapping = {
        "RUNTIME": probe_runtime_loss,
        "PASS": probe_pass_survives,
        "HEALTH": probe_rebind_health,
    }
    for name in args.probes.split(","):
        fn = mapping.get(name.strip().upper())
        if fn:
            fn(ev)
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
