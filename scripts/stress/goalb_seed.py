"""Goal-B seeding: groups + temporary passes injected into the DEBUG slot.

All writes go to com.appause.android.debug only (device_lib enforces the
serial; the release package is never touched). The pattern is the proven
D5/D6 one: pull -> edit with HOST sqlite3 -> push -> rm wal/shm -> REBOOT.
Reboot replaces force-stop as the restart mechanism because force-stop
silently revokes the HyperOS a11y grant while the grant survives reboot (D4).
"""
from __future__ import annotations

import sqlite3
import time
from pathlib import Path

import device_lib as D

GROUPS = {
    # name -> (package, cooldownSeconds)
    "GB_ESC": (D.XHS, 10),    # escape-safety target (D6 used xhs already)
    "GB_BILI": (D.BILI, 10),  # journey second target
}


def seed_groups(ev_dir: Path) -> bool:
    """Idempotent: rewrite the GB_* rows, leave every other row untouched.
    Pull db+WAL together: an open WAL holds recent commits (launch records),
    and pushing a wal-less copy over it DESTROYS them (the cleanup incident of
    2026-09-20 21:0x — test data only, but never again). sqlite3.connect on
    the pulled pair folds the WAL in before our edit."""
    local = D.pull(ev_dir / "appause.db", D.DB_REL)
    D.pull(ev_dir / "appause.db-wal", D.DB_REL + "-wal")
    con = sqlite3.connect(local)
    cur = con.cursor()
    now = int(time.time() * 1000)
    cur.execute("DELETE FROM group_apps WHERE groupId IN (SELECT id FROM app_groups WHERE name LIKE 'GB_%');")
    cur.execute("DELETE FROM app_groups WHERE name LIKE 'GB_%';")
    for name, (pkg, cd) in GROUPS.items():
        cur.execute(
            "INSERT INTO app_groups (name, cooldownSeconds, createdAt, type,"
            " reRemindMinutes, reRemindCooldownSeconds, reRemindRepeat, reRemindEscalate)"
            f" VALUES ('{name}', {cd}, {now}, 'pause', 0, 0, 1, 0);")
        gid = cur.lastrowid
        cur.execute("INSERT OR REPLACE INTO group_apps (packageName, groupId)"
                    f" VALUES ('{pkg}', {gid});")
    con.commit()
    check = cur.execute(
        "SELECT g.name, a.packageName FROM app_groups g JOIN group_apps a"
        " ON a.groupId=g.id WHERE g.name LIKE 'GB_%';").fetchall()
    con.close()
    D.push(local, D.DB_REL)
    return all((n, p) in check for n, (p, _) in GROUPS.items())


def latest_launch_action(ev_dir: Path, pkg: str) -> str:
    """Read-only Room peek: 'cancelled' / 'proceeded' / '' for the last record.
    MUST pull the -wal too — fresh commits live there until checkpointed, and
    a main-db-only pull shows stale data (the P5 false-FAIL of run d7-181650)."""
    local = D.pull(ev_dir / "peek.db", D.DB_REL)
    D.pull(ev_dir / "peek.db-wal", D.DB_REL + "-wal")
    con = sqlite3.connect(local)   # plain open: lets sqlite fold the WAL in
    row = con.execute("SELECT action FROM app_launch_records WHERE packageName=?"
                      " ORDER BY id DESC LIMIT 1;", (pkg,)).fetchone()
    con.close()
    return row[0] if row else ""


def seed_temp_pass(ev_dir: Path, pkg: str, ttl_s: int) -> bool:
    """DataStore stringSet 'temporary_passes' entry, applied via reboot.

    Reuses the campaign's byte-level protobuf editor (field-6 string_set —
    F-12 lesson) so we never hand-encode wire bytes again.
    """
    from p3_pass_expiry import set_string_list  # proven host-side proto editor
    expiry = int(D.shell("date +%s").strip()) * 1000 + ttl_s * 1000
    local = D.pull(ev_dir / "prefs.pb", D.PREFS_REL)
    data = local.read_bytes()
    patched = set_string_list(data, "temporary_passes", [f"{pkg}|{expiry}"])
    local.write_bytes(patched)
    D.push(local, D.PREFS_REL)
    return True


def clear_temp_pass(ev_dir: Path) -> None:
    from p3_pass_expiry import set_string_list
    local = D.pull(ev_dir / "prefs_clear.pb", D.PREFS_REL)
    local.write_bytes(set_string_list(local.read_bytes(), "temporary_passes", []))
    D.push(local, D.PREFS_REL)
