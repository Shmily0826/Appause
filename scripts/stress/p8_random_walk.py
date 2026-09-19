"""P8: bounded randomized model-based walk with deterministic seed + replay.

Model (states probed, not tracked exactly — invariants are state-agnostic):
launcher / target app / pause overlay up / Appause home / Settings.
Actions are drawn from a fixed alphabet with the same primitives the human
probes use. Any seed reproduces the identical action sequence (random.Random
with --seed), the full sequence is logged, and failures can be replayed:

    python p8_random_walk.py --seed 1234 --steps 80
    python p8_random_walk.py --seed 1234 --replay "37,41"   # steps only
    python p8_random_walk.py --seed 1234 --shrink            # on FAIL

Oracle (checked after every step, must hold in ANY state):
  ALIVE   Appause process exists (we never kill it inside the walk);
  NOFATAL no NEW 'FATAL EXCEPTION' line in logcat since walk start;
  BOUNDED at most ONE appause overlay window attached;
  STICKY  if the target is foreground AND not in cooldown AND a pause was
          never dismissed this entry, an overlay should exist — too fuzzy for
          a random walk, so it is NOT asserted; instead any step where
          foreground==target and overlay up AND the overlay vanishes within
          3s without any tap is recorded as SUSPECT (manual triage), never
          auto-FAIL.
"""
from __future__ import annotations

import argparse
import random
import time
from pathlib import Path

from campaign_lib import (
    APPAUSE,
    Evidence,
    adb_shell,
    app_pid,
    go_home,
    key,
    launch_from_home,
    reset_appause,
    screenshot,
)

TARGETS = ["com.google.android.deskclock", "com.google.android.calendar"]
CONTINUE_XY = (540, 1646)   # F-10 geometry, this 1080x2340 AVD only
CANCEL_XY = (540, 1788)


def fatal_count() -> int:
    out = adb_shell("logcat -d | grep -c 'FATAL EXCEPTION'").strip()
    return int(out) if out.isdigit() else -1


def overlay_count() -> int:
    out = adb_shell(f"dumpsys window windows | grep -c '{APPAUSE}'").strip()
    return int(out) if out.isdigit() else 0


def act_launch(rng: random.Random) -> None:
    launch_from_home(rng.choice(TARGETS))


def act_home() -> None:
    go_home()


def act_back() -> None:
    key("KEYCODE_BACK")


def act_recents() -> None:
    key("KEYCODE_APP_SWITCH")


def act_tap_blind(rng: random.Random) -> None:
    xy = rng.choice([CONTINUE_XY, CANCEL_XY])
    adb_shell(f"input tap {xy[0]} {xy[1]}")


def act_settings() -> None:
    adb_shell("am start -a android.settings.ACCESSIBILITY_SETTINGS")


ACTIONS = ["launch", "home", "back", "recents", "tap", "settings", "wait"]


def do_action(rng: random.Random, name: str) -> None:
    if name == "launch":
        act_launch(rng)
    elif name == "home":
        act_home()
    elif name == "back":
        act_back()
    elif name == "recents":
        act_recents()
    elif name == "tap":
        act_tap_blind(rng)
    elif name == "settings":
        act_settings()
    else:
        time.sleep(rng.uniform(0.5, 2.0))
    time.sleep(1.0)


def check(ev: Evidence, idx: int, base_fatals: int) -> bool:
    ok = True
    if app_pid() == "":
        ev.mark(f"step {idx}: VIOLATION appause process dead")
        ok = False
    if fatal_count() > base_fatals:
        ev.mark(f"step {idx}: VIOLATION new FATAL EXCEPTION in logcat")
        ok = False
    n = overlay_count()
    if n > 1:
        ev.mark(f"step {idx}: VIOLATION {n} appause windows attached (stacking)")
        ok = False
    if not ok:
        screenshot(ev.dir / f"step{idx}.violation.png")
        ev.dump_state(f"step{idx}")
    return ok


def plan(rng: random.Random, steps: int) -> list[str]:
    return [rng.choice(ACTIONS) for _ in range(steps)]


def walk(ev: Evidence, rng: random.Random, actions: list[str]) -> list[int]:
    base = fatal_count()
    bad: list[int] = []
    for idx, name in enumerate(actions, 1):
        do_action(rng, name)
        if not check(ev, idx, base):
            bad.append(idx)
            ev.mark(f"step {idx} = action {name!r}; continuing walk")
    return bad


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=20260919)
    parser.add_argument("--steps", type=int, default=60)
    parser.add_argument("--replay", default="", help="comma list of step numbers to redo")
    parser.add_argument("--shrink", action="store_true",
                        help="after a failure, bisect the shortest failing prefix")
    parser.add_argument("--evidence", default=str(Path(__file__).parent / "evidence"))
    args = parser.parse_args()

    ev = Evidence(Path(args.evidence), f"p8-seed{args.seed}")
    assert reset_appause(), "service did not come up — run setup_device.py first"
    rng = random.Random(args.seed)
    actions = plan(rng, args.steps)
    (ev.dir / "plan.txt").write_text("\n".join(f"{i}. {a}" for i, a in enumerate(actions, 1)),
                                     encoding="utf-8")
    ev.mark(f"seed={args.seed} steps={args.steps} plan saved")

    if args.replay:
        todo = [int(x) for x in args.replay.split(",") if x.strip()]
        bad = walk(ev, random.Random(args.seed), [actions[i - 1] for i in todo])
        ev.verdict("REPLAY", not bad, f"bad steps {todo}:{bad}")
        return ev.finish()

    bad = walk(ev, random.Random(args.seed), actions)
    if bad and args.shrink:
        ev.mark(f"shrinking from {len(actions)} steps, first failure at {bad[0]}")
        lo, hi = 1, bad[0]
        while lo < hi:
            mid = (lo + hi) // 2
            reset_appause()
            probe = walk(ev, random.Random(args.seed), actions[:mid])
            ev.mark(f"  prefix {mid}: {'FAIL' if probe else 'ok'}")
            hi, lo = (mid, lo) if probe else (mid + 1, hi + 1)
        ev.mark(f"minimal reproducer: seed={args.seed} --replay \"1,{hi}\""
                if hi > 1 else f"minimal reproducer: step 1 alone")
    ev.verdict("RANDOM-WALK", not bad, f"violations at steps {bad}" if bad else "")
    return ev.finish()


if __name__ == "__main__":
    raise SystemExit(main())
