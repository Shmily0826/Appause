"""D7: HyperOS system-level escape safety around the pause overlay (Phase B).

Every probe answers one release-blocking question: can the user ALWAYS get
away from the interception, and does the interception come back afterwards?
Automatable escape paths only — real edge-swipes are the Phase C manual
batch (adb cannot inject system gestures on this device, D3 fact).

Oracles (MIUI suppresses logcat, so):
  shown     = plog 'Overlay shown for' OR appause window attached + focus=debug
  escaped   = overlay windows == 0 within 6s AND focus == launcher
  no-trap   = launcher stays overlay-free for 10s afterwards
  no-false  = CONTROL app never intercepted
  alive     = debug pid unchanged + service still in dumpsys accessibility

NEVER force-stops / clears / uninstalls anything (a11y grant would die).
Cooldown 10s groups + a HOME + sleep between probes reset interception.
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

import device_lib as D
import goalb_seed as S

LAUNCHERS = ("com.miui.home", "launcher")
# device-px calibration from Goal-B Phase B screenshots (1080x2400 panel):
# Cancel text center image(460,1550) -> device(460,1860); Continue (450,1420)->(540,1704)
CANCEL_XY = (460, 1860)
CONTINUE_XY = (540, 1704)


class Run:
    def __init__(self, ev_root: Path):
        self.dir = ev_root / time.strftime("d7-%Y%m%d-%H%M%S")
        self.dir.mkdir(parents=True, exist_ok=True)
        self.log = (self.dir / "actions.log").open("a", encoding="utf-8")
        self.results: dict[str, str] = {}
        self.plog_len = 0

    def mark(self, note: str) -> None:
        line = f"{time.strftime('%H:%M:%S')} | {note}"
        self.log.write(line + "\n"); self.log.flush(); print(line)

    def snapshot(self, tag: str) -> None:
        (self.dir / f"{tag}.windows.txt").write_text("\n".join(D.appause_windows()), encoding="utf-8")
        (self.dir / f"{tag}.focus.txt").write_text(D.focus_pkg(), encoding="utf-8")
        (self.dir / f"{tag}.plog.txt").write_text(D.plog_tail(600), encoding="utf-8")
        D.screenshot(self.dir / f"{tag}.png")

    def verdict(self, name: str, passed: bool, detail: str = "") -> bool:
        self.results[name] = "PASS" if passed else "FAIL"
        self.mark(f"{name}: {'PASS' if passed else 'FAIL'} {detail}")
        if not passed:
            self.snapshot(name + ".FAIL")
        return passed

    def finish(self) -> int:
        fails = [k for k, v in self.results.items() if v == "FAIL"]
        (self.dir / "results.json").write_text(
            "{\n" + ",\n".join(f'  "{k}": "{v}"' for k, v in self.results.items()) + "\n}\n",
            encoding="utf-8")
        self.log.close()
        print(f"\n{len(self.results) - len(fails)}/{len(self.results)} PASS"
              + (f" — FAILURES: {fails}" if fails else ""))
        return 1 if fails else 0


# ---------------------------------------------------------------------------
# B-class harness fix (D7 run 1): wait_overlay used to accept ANY 'Overlay
# shown' line in the last 120 log lines — including PREVIOUS probes' — so
# arm_target returned True before the pause even existed and every tap/BACK
# landed on bare xhs. The window oracle (fixed for HyperOS block layout) is
# the sole truth: the 2032 window exists only while a pause is on screen.
def arm_target(run: Run, pkg: str, timeout: float = 45) -> bool:
    """Home -> cold-launch the grouped app -> wait until the pause window is
    really attached. 45s covers HyperOS cold starts of heavy apps."""
    D.home(); time.sleep(1)
    D.launch(pkg)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if D.overlay_present():
            return True
        time.sleep(0.5)
    return False


def escaped(run: Run, within: float = 6.0) -> tuple[bool, str]:
    """Overlay gone AND launcher focused within `within` seconds."""
    deadline = time.monotonic() + within
    while time.monotonic() < deadline:
        if not D.overlay_present() and any(h in D.focus_pkg() for h in LAUNCHERS):
            time.sleep(10)  # no-trap: a ghost re-show lands within seconds
            if D.overlay_present():
                return False, "overlay RE-APPEARED on launcher (ghost/false-positive)"
            return True, ""
        time.sleep(0.5)
    return False, f"still on screen after {within}s (focus={D.focus_pkg()})"


# ---------------------------------------------------------------------------
def overlay_has_focus() -> bool:
    """adb `input keyevent` reaches the FOCUSED window only (unlike a real
    press, which the system routes to the topmost focusable window). If the
    overlay does not hold focus, a BACK probe measures the injection path,
    not the product — report INCONCLUSIVE instead of a false FAIL. (D3
    proved manual BACK works 4/4; F-05 says focus lands ~1.3 s after attach.)
    """
    out = D.shell("dumpsys window | grep mCurrentFocus")
    return D.DEBUG in out or "appause" in out


def settle_focus(run: Run, tag: str, tries: int = 6) -> bool:
    for _ in range(tries):
        if overlay_has_focus():
            return True
        time.sleep(1.0)
    return False


def p1_back_steady(run: Run) -> None:
    for i in (1, 2, 3):
        if not arm_target(run, D.XHS):
            run.verdict(f"P1.back-steady-{i}", False, "intercept never appeared"); return
        time.sleep(3)  # steady state, past the F-05 focus window
        if not settle_focus(run, f"P1.{i}"):
            run.verdict(f"P1.back-steady-{i}", False,
                        "INCONCLUSIVE: overlay never held input focus — adb BACK "
                        "cannot reach it (manual-press coverage = Phase C)")
            D.home(); time.sleep(12); continue
        D.back()
        ok, why = escaped(run)
        fired = D.plog_has(r"backCB FIRED|dispatchKeyEvent BACK", since_len=150)
        run.verdict(f"P1.back-steady-{i}", ok, f"{why} backCB/dispatch={bool(fired)}")
        time.sleep(12)  # let the 30s cooldown... next arm re-fires anyway


def p2_back_focuswindow(run: Run) -> None:
    """BACK within ~0.4s of the overlay attaching — the F-05 focus window."""
    if not arm_target(run, D.XHS):
        run.verdict("P2.back-focuswindow", False, "intercept never appeared"); return
    D.back()                      # immediate: may land pre-focus
    ok, why = escaped(run, within=3.0)
    if not ok:
        D.back()                  # second press at ~+3s — a real user retries
        ok, why = escaped(run)
    if not ok and not settle_focus(run, "P2", tries=2):
        run.verdict("P2.back-focuswindow", False,
                    "INCONCLUSIVE (focus-routing, see P1 note): " + why)
    else:
        run.verdict("P2.back-focuswindow", ok, "escaped" if ok else f"TRAPPED: {why}")
    time.sleep(12)


def p3_home(run: Run, tag: str, mode: str | None) -> None:
    old = D.nav_mode()
    if mode and old != mode:
        D.set_nav_mode(mode); time.sleep(3)
        if D.nav_mode() != mode:
            # HyperOS may not honor every mode value over adb — refuse to run
            # the probe under an unknown nav state instead of guessing.
            run.verdict(tag, False, f"navigation_mode '{mode}' not honored (read {D.nav_mode()!r}) -> SKIP")
            return
    try:
        if not arm_target(run, D.XHS):
            run.verdict(tag, False, "intercept never appeared"); return
        D.home()
        ok, why = escaped(run)
        run.verdict(tag, ok, why)
        # interception must come back after the cooldown
        if ok:
            time.sleep(12)
            back_again = arm_target(run, D.XHS)
            run.mark(f"{tag}: re-intercept after Home escape = {back_again}")
            if back_again:
                D.back(); escaped(run)
    finally:
        if mode and D.nav_mode() != old:
            D.set_nav_mode(old); time.sleep(3)
    time.sleep(12)


def p5_cancel_tap(run: Run) -> None:
    if not arm_target(run, D.XHS):
        run.verdict("P5.cancel-tap", False, "intercept never appeared"); return
    D.tap(*CANCEL_XY)
    ok, why = escaped(run)
    action = S.latest_launch_action(run.dir, D.XHS)
    run.verdict("P5.cancel-tap", ok and action == "cancelled",
                f"{why} room_action={action!r}")
    time.sleep(12)


def p6_gesture_back_injection(run: Run) -> None:
    """Regression for D3: injected edge swipe must NOT half-dismiss anything.
    (Real gesture = Phase C manual.) PASS = overlay survives injection with no
    crash, then Home escapes cleanly."""
    old = D.nav_mode()
    D.set_nav_mode("2"); time.sleep(3)
    try:
        if not arm_target(run, D.XHS):
            run.verdict("P6.gesture-injection-noop", False, "intercept missing"); return
        pid_before = D.shell(f"pidof {D.DEBUG}").strip()
        D.shell("input swipe 15 1200 700 1200 250")  # left-edge swipe attempt
        time.sleep(2)
        still = D.overlay_present()
        D.home(); ok, why = escaped(run)
        pid_after = D.shell(f"pidof {D.DEBUG}").strip()
        run.verdict("P6.gesture-injection-noop", still and ok and pid_before == pid_after,
                    f"overlay_survived={still} home_escape={ok} pid_stable={pid_before == pid_after}")
    finally:
        D.set_nav_mode(old); time.sleep(3)
    time.sleep(12)


def p7_residual(run: Run) -> None:
    D.home(); time.sleep(3)
    windows = D.appause_windows()
    alive = D.service_running()
    run.verdict("P7.residual-clean", not windows and alive,
                f"windows={len(windows)} service_alive={alive}")


def p8_control_falsepositive(run: Run) -> None:
    """The ungrouped control app must never see an overlay."""
    D.home(); time.sleep(1)
    D.launch(D.CONTROL); time.sleep(8)
    hit = D.overlay_present()
    run.verdict("P8.control-never-intercepted", not hit,
                "overlay on control app!" if hit else f"focus={D.focus_pkg()}")
    D.home(); time.sleep(2)


# ---------------------------------------------------------------------------
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--evidence", default=str(Path(__file__).parent / "evidence" / "goalB"))
    ap.add_argument("--skip-seed", action="store_true", help="groups already on device")
    ap.add_argument("--min-battery", type=int, default=40)
    args = ap.parse_args()
    D.assert_phone()
    # Battery gate: this battery of probes includes up to 3 reboots; a phone
    # under the floor could die mid-session and be left in a half-tested state.
    level = D.shell("dumpsys battery | grep level").strip()
    run_mark = f"battery: {level}"
    try:
        pct = int(level.split(":")[1])
    except Exception:
        pct = 100
    if pct < args.min_battery:
        print(f"ABORT: battery {pct}% < {args.min_battery}% — charge the phone before Phase B")
        return 2
    run = Run(Path(args.evidence))
    run.mark(f"recon: {run_mark} nav_mode={D.nav_mode()} focus={D.focus_pkg()} "
             f"debug_pid={D.shell(f'pidof {D.DEBUG}').strip()} service={D.service_running()}")
    if not args.skip_seed:
        if not S.seed_groups(run.dir):
            run.mark("SEED FAILED — aborting (host sqlite edit did not verify)")
            return 2
        run.mark("groups seeded; rebooting to load them (grant survives reboot)")
        assert D.reboot_and_wait(), "device did not come back — STOP, investigate manually"
        run.mark(f"post-boot: service={D.service_running()} focus={D.focus_pkg()}")

    p8_control_falsepositive(run)
    p1_back_steady(run)
    p2_back_focuswindow(run)
    p3_home(run, "P3.home-3button", "0")
    p3_home(run, "P4.home-gesture-mode", "2")
    p5_cancel_tap(run)
    p6_gesture_back_injection(run)
    p7_residual(run)
    return run.finish()


if __name__ == "__main__":
    raise SystemExit(main())
