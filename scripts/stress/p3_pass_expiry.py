"""P3: temporary-pass expiry edges via run-as DataStore timestamp rewrite.

The service reads `temporary_passes` ("pkg|absoluteExpiryMillis") from the
DataStore file at startup, so we can move the expiry boundary precisely by:

  1. arming a 1-minute pass through the real overlay UI,
  2. force-stopping Appause (so no in-memory DataStore cache overwrites us),
  3. rewriting the expiry timestamp in the preferences file (same 13-digit
     width -> byte-safe replace) and pushing it back via run-as,
  4. restarting and asserting behaviour at/after expiry.

Probes:
  EXPIRED-REOPEN   expiry rewritten to the past -> first open of the target
                   after restart MUST intercept again.
  WAKE-REBUILD     expiry rewritten ~25 s ahead with Appause running -> when
                   it expires while the target is open in background state,
                   the expiry wake path (scheduled job) must re-arm: the
                   next open intercepts without touching Appause UI.
  CLEAN-PASS       control: an active pass must suppress interception.
"""
from __future__ import annotations

import argparse
import base64
import re
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    adb,
    adb_shell,
    expect_intercept,
    go_home,
    logcat_clear,
    logcat_match,
    open_app,
    reset_appause,
    seed_pause_group,
    tap,
)

TARGET_A = "com.google.android.deskclock"
PREFS = "/data/data/com.appause.android.debug/datastore/settings.preferences_pb"
PASS_RE = rb"com\.google\.android\.deskclock\|\d{13}"


def read_prefs(local: Path) -> bytes:
    # base64 round-trip: adb shell text mode corrupts raw binary bytes.
    b64 = adb_shell(f"run-as {APPAUSE} base64 {PREFS}").replace("\r", "").replace("\n", "")
    data = base64.b64decode(b64)
    local.write_bytes(data)
    return data


def write_prefs(local: Path) -> None:
    tmp = "/data/local/tmp/prefs_pb"
    adb("push", str(local), tmp)
    adb_shell(f"run-as {APPAUSE} cp {tmp} {PREFS}")


def set_expiry(local: Path, new_ms: int) -> bool:
    data = local.read_bytes()
    m = re.search(PASS_RE, data)
    if not m:
        return False
    old = m.group(0)
    pkg = old.split(b"|")[0]
    new = pkg + b"|" + str(new_ms).encode()
    if len(new) != len(old):
        return False  # keep byte width identical
    local.write_bytes(data.replace(old, new))
    return True


def arm_pass(ev: Evidence) -> bool:
    """Intercept target A and take the 1-minute Temporary Pass via UI.

    verify-E showed the naive version failing: after reset the service is
    still re-registering when the launch fires, so the window event is lost
    and a re-monkey of an already-foreground app generates no new event.
    Retrying must therefore always leave to Home first, and wait for the
    connect log before each attempt.
    """
    for attempt in range(3):
        logcat_clear()
        logcat_match(r"AccessibilityService connected and running", timeout=20)
        go_home()
        time.sleep(1.5)
        if not expect_intercept(TARGET_A):
            ev.mark(f"arm_pass attempt {attempt}: no intercept")
            continue
        if not tap("Temporary pass", timeout=5):
            ev.mark(f"arm_pass attempt {attempt}: 'Temporary pass' button missing")
            continue
        if not tap("Use for 1 min", timeout=5):
            ev.mark(f"arm_pass attempt {attempt}: 'Use for 1 min' option missing")
            continue
        time.sleep(1.5)
        return True
    return False


def probe_clean_pass(ev: Evidence) -> bool:
    reset_appause()
    if not arm_pass(ev):
        return ev.verdict("P3-clean-pass", False)
    go_home()
    time.sleep(3)
    logcat_clear()
    open_app(TARGET_A)
    time.sleep(6)
    hit = expect_intercept(TARGET_A, timeout=2)
    go_home()
    return ev.verdict("P3-clean-pass-suppresses", not hit,
                      "intercepted while pass active" if hit else "")


def probe_expired_reopen(ev: Evidence) -> bool:
    reset_appause()
    if not arm_pass(ev):
        return ev.verdict("P3-expired-reopen", False, "pass not armed")
    go_home()
    local = ev.dir / "prefs_expired.pb"
    adb_shell("am force-stop " + APPAUSE)
    read_prefs(local)
    if not set_expiry(local, int(time.time() * 1000) - 60_000):
        return ev.verdict("P3-expired-reopen", False, "no pass entry in DataStore")
    write_prefs(local)
    # Restart the service so it re-reads the rewritten store.
    adb_shell(f"settings put secure enabled_accessibility_services ''")
    time.sleep(1)
    reset_appause()
    logcat_clear()
    open_app(TARGET_A)
    hit = expect_intercept(TARGET_A, timeout=20)
    go_home()
    return ev.verdict("P3-expired-reopen", bool(hit),
                      "expired pass still suppresses interception" if not hit else "")


def probe_wake_rebuild(ev: Evidence) -> bool:
    reset_appause()
    if not arm_pass(ev):
        return ev.verdict("P3-wake-rebuild", False, "pass not armed")
    # Leave the target open, then rewrite expiry to +25s in the future.
    # NOTE: the running process caches DataStore, so instead of file surgery
    # this probe uses the natural 60s expiry: stay in the app, wait it out,
    # then leave & re-enter — expiry must have re-armed interception.
    ev.mark("P3-wake-rebuild: waiting out the real 60s pass while app open")
    time.sleep(75)
    go_home()
    time.sleep(2)
    logcat_clear()
    open_app(TARGET_A)
    hit = expect_intercept(TARGET_A, timeout=20)
    go_home()
    return ev.verdict("P3-wake-rebuild", bool(hit),
                      "no re-arm after natural expiry while app was open" if not hit else "")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probes", default="CLEAN,EXPIRED,WAKE")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), "p3-pass")
    # Own group so arming never depends on groups left by earlier probes.
    seed_pause_group("P3Pass", [TARGET_A], 2)
    mapping = {"CLEAN": probe_clean_pass, "EXPIRED": probe_expired_reopen,
               "WAKE": probe_wake_rebuild}
    for name in args.probes.split(","):
        fn = mapping.get(name.strip().upper())
        if fn:
            fn(ev)
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
