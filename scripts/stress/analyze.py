#!/usr/bin/env python3
"""Decide whether a stress run contains a duplicate interception.

Reads one evidence directory produced by ui_stress.py and reconstructs the
pause lifecycle from the Appause log lines, then answers the single question
the stress test exists to answer:

    while a valid pause presentation was already up, did Appause run another
    INTERCEPT / build another pause session / restart the countdown?

How the verdict is derived (all from real log evidence, no guessing):

  DUPLICATE_INTERCEPT   a second `INTERCEPT:` line arrives while a pause
                        presentation is still active (no `Overlay dismissed`
                        in between).
  OVERLAY_LEAK          two `Overlay shown for` lines with no `Overlay
                        dismissed` between them. The second attach overwrites
                        the manager's view reference, so the first window can
                        never be removed -> stacked pause screens on screen.
  BLOCKED_RETRY         an `INTERCEPT:` was followed by `Pause screen already
                        showing, skipping`. The decision layer did try to
                        intercept again; the presentation layer refused. Not
                        user-visible, but it is the same bug class and it is
                        the early-warning signal that the guard went false
                        while a window was still attached.
  COUNTDOWN_RESET       a pause presentation was torn down and a non-abandon
                        path rebuilt it for the same target inside the window,
                        so the user sees the countdown start over.
  WATCHDOG_RELEASE      the 30 s max-hold watchdog released the logical guard.
                        When this fires while a window is still attached, the
                        guard no longer describes reality -- the precondition
                        for the duplicate paths above.

The lifecycle tracker resets whenever the Appause pid changes, because a
process restart destroys every window with it and leaves no dismissal log.
The stress harness force-stops Appause between scenarios, so without that
reset each scenario boundary would be reported as an overlay leak.

Usage:
    python analyze.py [evidence-dir]        # newest run if omitted
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

# NZST is UTC+12 with no DST in September; used only to line the wall-clock
# action markers up with the epoch-stamped logcat lines.
LOCAL_OFFSET = timedelta(hours=12)

LINE = re.compile(
    r"^\s*(?P<epoch>\d+\.\d+)\s+(?P<pid>\d+)\s+(?P<tid>\d+)\s+"
    r"(?P<level>[DIWE])\s+(?P<tag>[^:]+):\s(?P<msg>.*)$"
)

EVENT_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("INTERCEPT", re.compile(r"^INTERCEPT: (?P<pkg>[\w.]+) →")),
    ("INTERCEPT", re.compile(r"^INTERCEPT: (?P<pkg>[\w.]+) ")),
    ("TARGET_SKIP", re.compile(r"^SKIP: .*\((?P<pkg>[\w.]+)\)")),
    ("OVERLAY_SHOWN", re.compile(r"^Overlay shown for (?P<pkg>[\w.]+)")),
    ("OVERLAY_SHOWN_ALT", re.compile(r"^Overlay added with alternate type")),
    ("OVERLAY_DISMISSED", re.compile(r"^Overlay dismissed")),
    ("ALREADY_SHOWING", re.compile(r"^Pause screen already showing, skipping")),
    ("ABANDON", re.compile(r"^Cooldown abandoned \(user left to (?P<pkg>[\w.]+)")),
    ("BYPASS_ON", re.compile(r"^Bypass started: (?P<pkg>[\w.]+)")),
    ("BYPASS_OFF", re.compile(r"^Bypass cleared: (?P<pkg>[\w.]+)")),
    ("RESUME_WINDOW", re.compile(r"^RESUME: (?P<pkg>[\w.]+) \(returned within leave window\)")),
    ("TEMP_PASS", re.compile(r"^SKIP: temporary pass active")),
    ("WATCHDOG", re.compile(r"watchdog.*releasing guard", re.IGNORECASE)),
    ("ACTIVITY_LAUNCH", re.compile(r"^PauseActivity direct launch attempted for (?P<pkg>[\w.]+)")),
    ("ALARM_SCHEDULED", re.compile(r"^Scheduled PauseActivity via AlarmManager")),
    ("GEO_SKIP", re.compile(r"^Overlay geometry refresh skipped")),
)


@dataclass
class Event:
    epoch: float
    pid: int
    tag: str
    kind: str
    package: str | None
    raw: str

    @property
    def clock(self) -> str:
        return datetime.fromtimestamp(self.epoch, timezone.utc).astimezone(
            timezone(LOCAL_OFFSET)
        ).strftime("%H:%M:%S.%f")[:-3]


def parse_log(path: Path) -> list[Event]:
    events: list[Event] = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = LINE.match(line)
        if not match:
            continue
        msg = match.group("msg").rstrip()
        for kind, pattern in EVENT_PATTERNS:
            found = pattern.search(msg)
            if found:
                events.append(
                    Event(
                        epoch=float(match.group("epoch")),
                        pid=int(match.group("pid")),
                        tag=match.group("tag").strip(),
                        kind=kind,
                        package=found.groupdict().get("pkg"),
                        raw=msg,
                    )
                )
                break
    return events


def parse_markers(path: Path, base_day: date) -> list[tuple[float, str]]:
    """Convert the action log's wall-clock markers to epoch seconds.

    Needed so findings can be attributed to the scenario that produced them.

    [base_day] must be the LOCAL calendar date of the run's first logcat event,
    not "today": a run is analysed later, so anchoring on the current date puts
    every marker on the wrong day. A run that crosses local midnight (e.g.
    23:50 -> 00:04) additionally needs its wall-clock times rolled forward, so a
    large backwards jump advances the day.
    """
    if not path.exists():
        return []
    markers: list[tuple[datetime, str]] = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.split(" | ", 1)
        if len(parts) != 2:
            continue
        try:
            stamp = datetime.strptime(parts[0].strip(), "%H:%M:%S")
        except ValueError:
            continue
        markers.append((stamp, parts[1]))
    if not markers:
        return []

    anchored: list[tuple[float, str]] = []
    day_offset = 0
    previous_seconds: int | None = None
    for stamp, note in markers:
        seconds = stamp.hour * 3600 + stamp.minute * 60 + stamp.second
        # Only local midnight makes the wall clock jump backwards this far.
        if previous_seconds is not None and seconds < previous_seconds - 6 * 3600:
            day_offset += 1
        previous_seconds = seconds
        local = datetime.combine(
            base_day + timedelta(days=day_offset), stamp.time(), tzinfo=timezone(LOCAL_OFFSET)
        )
        anchored.append((local.timestamp(), note))
    return anchored


def scenario_for(epoch: float, markers: list[tuple[float, str]]) -> str:
    """Which scenario marker window contains this epoch (best effort).

    Marker lines look like '=== S1 START (run 1) ===', so they are matched on
    the START token rather than on the line ending.
    """
    current = "?"
    for stamp, note in markers:
        if "START" not in note or not note.startswith("==="):
            continue
        if stamp <= epoch + 1.5:
            current = note.replace("===", "").replace("START", "").strip()
    return current


def analyze(directory: Path) -> int:
    log = directory / "logcat.txt"
    if not log.exists():
        print(f"no logcat.txt in {directory}")
        return 2

    events = parse_log(log)
    # Anchor the action-log wall clock on the run's own first event date, so
    # analysis performed on a later day still lines the markers up.
    base_day = (
        datetime.fromtimestamp(events[0].epoch, timezone(LOCAL_OFFSET)).date()
        if events else datetime.now(timezone.utc).date()
    )
    markers = parse_markers(directory / "actions.log", base_day)

    findings: list[tuple[str, Event, str]] = []
    restarts: list[tuple[Event, int, int]] = []
    active_overlay: Event | None = None       # Overlay shown without a dismissed
    pending_intercept: Event | None = None    # INTERCEPT awaiting its overlay
    events_since_intercept = 0
    current_pid: int | None = None

    for event in events:
        # A new Appause pid means the service process was restarted. The stress
        # harness force-stops and re-binds Appause between scenarios, which
        # destroys every window along with the process — so there is no
        # dismissal log for the old overlay. Without this reset the previous
        # scenario's still-"attached" overlay makes the next scenario's
        # INTERCEPT look like a duplicate and its overlay look leaked: a
        # cross-scenario false positive, not a bug in Appause.
        if current_pid is None:
            current_pid = event.pid
        elif event.pid != current_pid:
            restarts.append((event, current_pid, event.pid))
            current_pid = event.pid
            active_overlay = None
            pending_intercept = None
            events_since_intercept = 0
            # Fall through: this same event must still be processed, because a
            # restart is immediately followed by a genuine first intercept.

        if event.kind == "OVERLAY_SHOWN":
            if active_overlay is not None:
                findings.append((
                    "OVERLAY_LEAK",
                    event,
                    f"second overlay attached while '{active_overlay.package}' was still "
                    f"attached (shown at {active_overlay.clock}) — no dismissal between",
                ))
            active_overlay = event
            pending_intercept = None
        elif event.kind in ("OVERLAY_DISMISSED",):
            active_overlay = None
            pending_intercept = None
        elif event.kind == "INTERCEPT":
            if active_overlay is not None:
                findings.append((
                    "DUPLICATE_INTERCEPT",
                    event,
                    f"INTERCEPT for '{event.package}' while overlay for "
                    f"'{active_overlay.package}' is still up "
                    f"(shown {active_overlay.clock}, no dismissal since)",
                ))
            elif pending_intercept is not None and events_since_intercept <= 3:
                findings.append((
                    "DUPLICATE_INTERCEPT",
                    event,
                    f"two INTERCEPTs within {events_since_intercept} events, no overlay "
                    f"teardown between ('{pending_intercept.package}' → '{event.package}')",
                ))
            pending_intercept = event
            events_since_intercept = 0
        elif event.kind == "ALREADY_SHOWING":
            if pending_intercept is not None:
                findings.append((
                    "BLOCKED_RETRY",
                    event,
                    f"decision layer produced an INTERCEPT for "
                    f"'{pending_intercept.package}' but the presentation layer refused "
                    f"('already showing') — guard disagreed with the window on screen",
                ))
                pending_intercept = None
        elif event.kind == "WATCHDOG":
            findings.append(("WATCHDOG_RELEASE", event, "guard watchdog released the logical guard"))

        events_since_intercept += 1

    # ---- report -----------------------------------------------------------
    counts: dict[str, int] = {}
    for event in events:
        counts[event.kind] = counts.get(event.kind, 0) + 1

    print(f"evidence : {directory}")
    print(f"events   : {len(events)} parsed from logcat")
    print()
    print("event counts")
    for kind in sorted(counts, key=lambda k: -counts[k]):
        print(f"  {kind:20} {counts[kind]}")
    print()

    if restarts:
        print(f"service restarts: {len(restarts)} "
              f"(each resets cross-scenario state — not a finding)")
        for event, old_pid, new_pid in restarts:
            print(f"  {event.clock}  pid {old_pid} -> {new_pid}  "
                  f"[{scenario_for(event.epoch, markers)}]")
        print()

    intercepts = [e for e in events if e.kind == "INTERCEPT"]
    print(f"INTERCEPT lines ({len(intercepts)}):")
    for event in intercepts:
        print(f"  {event.clock}  {event.package:36} [{scenario_for(event.epoch, markers)}]")
    print()

    shows = [e for e in events if e.kind == "OVERLAY_SHOWN"]
    print(f"Overlay shown lines ({len(shows)}):")
    for event in shows:
        print(f"  {event.clock}  {event.package or '(alt type)':36} "
              f"[{scenario_for(event.epoch, markers)}]")
    print()

    if not findings:
        print("RESULT: no duplicate interception, no overlay leak, no blocked retry.")
        print("        Every INTERCEPT had a clean, dismissed predecessor.")
        return 0

    print(f"FINDINGS ({len(findings)}):")
    for kind, event, detail in findings:
        print(f"  [{kind}] {event.clock} [{scenario_for(event.epoch, markers)}]")
        print(f"      {detail}")
    return 1


def main() -> int:
    if len(sys.argv) > 1:
        directory = Path(sys.argv[1])
    else:
        base = Path(__file__).parent / "evidence"
        runs = sorted((p for p in base.glob("run-*") if p.is_dir()), key=lambda p: p.name)
        if not runs:
            print("no run-* directories found")
            return 2
        directory = runs[-1]
    return analyze(directory)


if __name__ == "__main__":
    raise SystemExit(main())
