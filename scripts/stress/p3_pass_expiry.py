"""P3: temporary-pass expiry edges via run-as DataStore timestamp rewrite.

The service reads `temporary_passes` ("pkg|absoluteExpiryMillis") from the
DataStore file at startup, so we can move the expiry boundary precisely by:

  1. seeding the pass entry directly into settings.preferences_pb with a
     small protobuf-wire editor (add/replace the string-set key),
  2. doing this while Appause is force-stopped (no in-memory cache overwrite),
  3. restarting the service — onServiceConnected then re-reads the store and
     rebuilds the expiry wake, which is the very path under test.

Why not arm through the overlay UI (first attempt, verify-F/G): the
"Temporary pass" link only renders AFTER the cooldown finishes, and every
uiautomator dump kills the a11y service on this emulator within ~25 ms (F-06,
C-class) — so the chooser never survives to receive the second tap. The
single-tap UI flow stays un-testable here; real-device coverage is a listed
remaining gap.

Probes:
  CLEAN-PASS     future expiry -> interception must be suppressed.
  EXPIRED-REOPEN expiry in the past -> first open after restart intercepts.
  WAKE-REBUILD   expiry ~40 s ahead -> stay in target across expiry, leave and
                 re-enter; the rebuilt wake must have re-armed interception.
"""
from __future__ import annotations

import argparse
import base64
import struct
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    adb,
    adb_shell,
    expect_intercept,
    expect_no_intercept,
    go_home,
    reset_appause,
    seed_pause_group,
)

TARGET_A = "com.google.android.deskclock"
PREFS = "/data/data/com.appause.android.debug/files/datastore/settings.preferences_pb"
PASSES_KEY = "temporary_passes"


def device_now_ms() -> int:
    """Expiry is compared against the DEVICE clock in the service — seed with it."""
    return int(adb_shell("date +%s").strip()) * 1000


# ── minimal androidx-DataStore Preferences protobuf editor ─────────────────
# Wire shape (androidx.datastore.preferences.core):
#   PreferenceMap   { repeated Preference preferences = 1; }
#   Preference      { string key = 1; PreferenceValue value = 2; }
#   PreferenceValue { StringList stringList = 7; }
#   StringList      { repeated string items = 1; }
# We only ever touch the `temporary_passes` Preference; every other field is
# copied byte-for-byte, so the rewrite cannot corrupt unrelated settings.

def _varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        out.append(b | (0x80 if n else 0))
        if not n:
            return bytes(out)


def _read_varint(buf: bytes, pos: int) -> tuple[int, int]:
    val = 0
    shift = 0
    while True:
        b = buf[pos]
        pos += 1
        val |= (b & 0x7F) << shift
        if not (b & 0x80):
            return val, pos
        shift += 7


def _tlv(field: int, payload: bytes) -> bytes:
    return _varint((field << 3) | 2) + _varint(len(payload)) + payload


def _parse_fields(buf: bytes) -> list[tuple[int, int, bytes]]:
    """Returns (field_no, wire_type, raw) with raw INCLUDING the header."""
    out = []
    pos = 0
    while pos < len(buf):
        key_start = pos
        key, pos = _read_varint(buf, pos)
        fno, wt = key >> 3, key & 7
        if wt == 0:
            _, pos = _read_varint(buf, pos)
        elif wt == 2:
            ln, pos = _read_varint(buf, pos)
            pos += ln
        elif wt == 5:
            pos += 4
        elif wt == 1:
            pos += 8
        else:
            raise ValueError(f"unsupported wire type {wt}")
        out.append((fno, wt, buf[key_start:pos]))
    return out


def set_string_list(prefs_bytes: bytes, key: str, values: list[str]) -> bytes:
    """Replace (or append) one string-set preference; other entries verbatim."""
    desired_value = _tlv(7, b"".join(_tlv(1, v.encode()) for v in values))
    desired_pref = _tlv(1, key.encode()) + _tlv(2, desired_value)
    out = bytearray()
    replaced = False
    for fno, wt, raw in _parse_fields(prefs_bytes):
        if fno == 1 and wt == 2:
            inner = _parse_fields(raw[len(raw) - _payload_len(raw):])
            k = ""
            for f2, w2, r2 in inner:
                if f2 == 1 and w2 == 2:
                    ln, p = _read_varint(r2, 1)
                    k = r2[p:p + ln].decode()
            if k == key:
                if not replaced:
                    out += _tlv(1, desired_pref)
                    replaced = True
                continue  # drop duplicates
        out += raw
    if not replaced:
        out += _tlv(1, desired_pref)
    return bytes(out)


def _payload_len(raw: bytes) -> int:
    # raw = key varint + length varint + payload; skip both varints properly.
    _, p = _read_varint(raw, 0)
    _, p = _read_varint(raw, p)
    return len(raw) - p


# ── device I/O ─────────────────────────────────────────────────────────────

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


def seed_pass(ev: Evidence, expiry_ms: int) -> bool:
    """Write a temporary_passes entry with Appause stopped, then restart."""
    adb_shell("am force-stop " + APPAUSE)
    local = ev.dir / "prefs_seed.pb"
    try:
        data = read_prefs(local)
    except Exception as exc:  # noqa: BLE001
        ev.mark(f"seed_pass read failed: {exc}")
        return False
    entry = f"{TARGET_A}|{expiry_ms}"
    patched = set_string_list(data, PASSES_KEY, [entry])
    local.write_bytes(patched)
    write_prefs(local)
    reset_appause()
    time.sleep(2)
    # Confirm the store survived the restart by re-reading it.
    check = read_prefs(ev.dir / "prefs_check.pb")
    ok = entry.encode() in check
    ev.mark(f"seed_pass expiry={expiry_ms} verified={ok}")
    return ok


# ── probes ─────────────────────────────────────────────────────────────────

def probe_clean_pass(ev: Evidence) -> None:
    reset_appause()
    now = device_now_ms()
    if not seed_pass(ev, now + 5 * 60_000):
        ev.verdict("P3-clean-pass", False, "pass not seeded")
        return
    # Deterministic launch + settle window; PASS = target stays foreground
    # with no 'Overlay shown' marker (cooldown is 2s, so a broken pass
    # would have intercepted by now).
    hit = not expect_no_intercept(TARGET_A, settle=8)
    go_home()
    ev.verdict("P3-clean-pass-suppresses", not hit,
               "intercepted while pass active" if hit else "")
    # cleanup: drop the seeded pass so later probes start clean
    adb_shell("am force-stop " + APPAUSE)
    local = ev.dir / "prefs_clear.pb"
    data = read_prefs(local)
    local.write_bytes(set_string_list(data, PASSES_KEY, []))
    write_prefs(local)
    reset_appause()


def probe_expired_reopen(ev: Evidence) -> None:
    reset_appause()
    now = device_now_ms()
    if not seed_pass(ev, now - 60_000):
        ev.verdict("P3-expired-reopen", False, "pass not seeded")
        return
    hit = expect_intercept(TARGET_A, timeout=20)
    go_home()
    ev.verdict("P3-expired-reopen", bool(hit),
               "expired pass still suppresses interception" if not hit else "")


def probe_wake_rebuild(ev: Evidence) -> None:
    reset_appause()
    now = device_now_ms()
    # Seed ~40s ahead; the restart inside seed_pass exercises the
    # onServiceConnected wake rebuild, and expiry fires while we sit in the app.
    if not seed_pass(ev, now + 40_000):
        ev.verdict("P3-wake-rebuild", False, "pass not seeded")
        return
    if not expect_no_intercept(TARGET_A, settle=6):
        go_home()
        ev.verdict("P3-wake-rebuild", False, "intercepted before expiry")
        return
    ev.mark("wake-rebuild: inside target, waiting past expiry")
    time.sleep(50)
    go_home()
    time.sleep(2)
    hit = expect_intercept(TARGET_A, timeout=20)
    go_home()
    ev.verdict("P3-wake-rebuild", bool(hit),
               "no re-arm after natural expiry while app was open" if not hit else "")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probes", default="CLEAN,EXPIRED,WAKE")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()
    ev = Evidence(Path(args.evidence), "p3-pass")
    # Own group so probes never depend on groups left by earlier runs.
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
