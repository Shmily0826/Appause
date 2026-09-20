"""Goal-B Phase C: ONE guided manual batch (~10 min, user performs actions,
the script arms targets, harvests evidence and renders PASS/FAIL verdicts).

Run interactively with the phone in hand, charging, AFTER d7/d8 are done:
    python goalb_phase_c.py --evidence evidence/goalB

Each step prints what to do, waits for the action to be detected (or a
timeout you can extend with Enter), then judges from window/plog state.
J4 (lock/wake) and J5 (temp-pass across reboot) ride along at the end so
the user never has to come back a third time.
"""
import sys
import time
from pathlib import Path

import device_lib as D
import goalb_seed as S
from d7_escape_safety import Run, escaped, CANCEL_XY

READY_WAIT = 90   # seconds we wait for a human action before asking


def arm_xhs(run: Run) -> bool:
    D.home(); time.sleep(1)
    D.shell("am start -n com.xingin.xhs/.index.v2.IndexActivityV2")
    dl = time.monotonic() + 45
    while time.monotonic() < dl:
        if D.overlay_present():
            return True
        time.sleep(0.5)
    return False


def human(prompt: str, detect, wait: int = READY_WAIT) -> bool:
    print(f"\n>>> {prompt}\n    (waiting up to {wait}s for the action...)")
    dl = time.monotonic() + wait
    while time.monotonic() < dl:
        if detect():
            return True
        time.sleep(0.5)
    ans = input("    still waiting — [c]ontinue waiting / [s]kip? ").strip().lower()
    if ans == "c":
        return human(prompt, detect, wait)
    return False


def main() -> int:
    D.assert_phone()
    run = Run(Path(sys.argv[sys.argv.index("--evidence") + 1]) if "--evidence" in sys.argv
              else Path(__file__).parent / "evidence" / "goalB")
    print("Phase C batch — keep the phone CHARGING. 6 steps, ~10 minutes.")

    # C1: real edge-swipe BACK on the live overlay (gesture mode)
    old = D.nav_mode()
    D.set_nav_mode("2"); time.sleep(3)
    if arm_xhs(run):
        ok = human("小红书拦截层出现后，做 3 次【左边缘右滑返回】手势。"
                   "完成判定：每次滑动后拦截层仍在（手势 Back 不 dismiss 是设计预期）。",
                   lambda: not D.overlay_present() or time.sleep(0))  # any change ends wait
        time.sleep(3)
        still = D.overlay_present()
        run.verdict("C1.gesture-back-noop", still,
                    "overlay survived real edge-swipes (matches D3)")
        # C2: real HOME swipe-up must always escape
        ok2 = human("现在从【底部上滑回桌面】(Home 手势) 一次。",
                    lambda: not D.overlay_present())
        esc_ok = ok2 and not D.overlay_present()
        f = D.focus_pkg()
        run.verdict("C2.home-gesture-escape", esc_ok and ("home" in f or "launcher" in f),
                    f"focus={f}")
    else:
        run.verdict("C1.gesture-back-noop", False, "could not arm xhs overlay")
    D.set_nav_mode(old); time.sleep(2)

    # C3: overlay just appeared -> instant HOME (focus-window feel)
    for i in (1, 2):
        D.home(); time.sleep(1)
        D.shell("am start -n com.xingin.xhs/.index.v2.IndexActivityV2")
        # detect attach fast, then ask for an immediate swipe-up
        dl = time.monotonic() + 45; armed = False
        while time.monotonic() < dl:
            if D.overlay_present():
                armed = True; break
            time.sleep(0.2)
        if not armed:
            run.verdict(f"C3.focus-window-home-{i}", False, "arming failed"); continue
        t0 = time.monotonic()
        human("立刻【上滑回桌面】(越快越好)！", lambda: not D.overlay_present(), wait=30)
        dt = time.monotonic() - t0
        ok, why = escaped(run, within=8)
        run.verdict(f"C3.focus-window-home-{i}", ok, f"reaction {dt:.1f}s {why}")
        time.sleep(33)  # cooldown 30s for the next arm

    # C4: rapid BACK+HOME mash on the overlay (3-key mode)
    if arm_xhs(run):
        human("连续快速按【返回+桌面】组合 5 次（三键导航栏）。",
              lambda: not D.overlay_present(), wait=40)
        ok, why = escaped(run, within=8)
        run.verdict("C4.mash-escape", ok, why)
    time.sleep(33)

    # C5: subjective jank (one word)
    ans = input("\n>>> 整个过程中拦截层有没有让你觉得'卡住/点了没反应'？[y/n] ").strip().lower()
    run.verdict("C5.no-perceived-stuck", ans != "y", f"user said {ans!r}")

    # J4: lock/wake survival (needs the phone, hence here)
    pid0 = D.shell(f"pidof {D.DEBUG}").strip()
    D.home(); time.sleep(1)
    human("请把手机【锁屏】(电源键)，等 20 秒后再解锁回到桌面。",
          lambda: "Awake" in D.shell("dumpsys power | grep mWakefulness=")
          and time.time() > time.time(), wait=180)
    time.sleep(20)
    hit = arm_xhs(run)
    pid1 = D.shell(f"pidof {D.DEBUG}").strip()
    if hit:
        D.tap(*CANCEL_XY); time.sleep(2)
    run.verdict("J4.lock-wake-survival", hit and pid0 == pid1,
                f"intercept_after_wake={hit} pid_stable={pid0 == pid1}")
    time.sleep(33)

    # J5: temp pass across reboot (last: ends on a reboot)
    print("\n>>> J5 需要一次重启：重启后请解锁手机，脚本会自动继续（最多等 8 分钟）。")
    input("    按 Enter 开始 J5...")
    import d8_real_app_journeys as d8
    d8_run = run
    d8_run.continue_xy = None
    d8.j5_temp_pass_cycle(d8_run)

    return run.finish()


if __name__ == "__main__":
    sys.exit(main())
