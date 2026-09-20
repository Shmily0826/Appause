"""R-C/R-D user batch runner — one command, follow the prompts.

The script arms targets, times everything, and renders PASS/FAIL from window
state; the user only performs physical gestures and presses Enter when told.
Run:  python r_d_manual.py     (phone must be connected via USB)
"""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import device_lib as D

XHS, BILI = "com.xingin.xhs", "tv.danmaku.bili"
LAUNCHERS = {"com.miui.home"}
EV = Path(__file__).parent / "evidence" / "release-smoke"


def wait_enter(prompt: str) -> None:
    input(f"\n>>> {prompt}\n    (完成后按回车) ")


def overlay_until(timeout: float) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        if D.overlay_present():
            return True
        time.sleep(0.2)
    return False


def arm(pkg: str) -> float:
    D.launch(pkg)
    t0 = time.monotonic()
    ok = overlay_until(30)
    return (time.monotonic() - t0) if ok else -1.0


def main() -> int:
    D.assert_phone()
    D.ensure_awake()
    EV.mkdir(parents=True, exist_ok=True)
    results: dict[str, object] = {}

    print("== R-C 静置后采集（假设你刚完成拔线静置）==")
    print("   当前电量/供电:")
    b = D.shell("dumpsys battery | grep -E 'level|USB powered|AC powered'")
    print("   " + b.replace("\r", "").replace("\n", " | ").strip())
    print(f"   release 进程 oom adj = {D.shell('cat /proc/' + (D.shell('pidof com.appause.android').strip().splitlines()[0] if D.shell('pidof com.appause.android').strip() else '1') + '/oom_score_adj').strip()}")
    for pkg in (XHS, BILI):
        lat = arm(pkg)
        results[f"R-C latency {pkg}"] = round(lat, 2) if lat >= 0 else "NO-OVERLAY"
        print(f"   {pkg}: 拦截延迟 {'%.2fs' % lat if lat >= 0 else '30s 内未出现!'}")
        D.home(); time.sleep(2.5)

    print("\n== R-D-1 真实手指 Home 手势（F-27 关键项）==")
    for pkg, name in ((XHS, "小红书"), (BILI, "B站")):
        lat = arm(pkg)
        if lat < 0:
            print(f"   {name}: 卡片没出现，跳过"); continue
        wait_enter(f"{name} 的暂停卡片已出现 —— 现在用【一次手指 Home 手势】回桌面，然后按回车")
        D.screenshot(EV / f"rd1-{pkg}.png")
        gone = not D.overlay_present()
        focus = D.focus_pkg()
        ok = gone and focus in LAUNCHERS
        results[f"R-D-1 finger-home {pkg}"] = "PASS" if ok else f"gone={gone} focus={focus}"
        print(f"   自动判定: {'PASS 一次手势干净回桌面' if ok else f'需人工确认 gone={gone} focus={focus}'}")
        verdict = input("   你自己的感受：一次手势够干净吗？(y/n) ").strip().lower()
        results[f"R-D-1 subjective {pkg}"] = verdict

    print("\n== R-D-2 多任务路径 ==")
    lat = arm(XHS)
    if lat >= 0:
        wait_enter("卡片出现时：上滑停留进入【多任务】，再点回小红书卡片，然后按回车")
        state = D.overlay_present()
        D.screenshot(EV / "rd2-recents-return.png")
        results["R-D-2 after-recents-return overlay"] = "still-shown" if state else "gone"
        print(f"   返回后卡片状态: {'还在（正常：回到受限App应重新看到卡片）' if state else '已消失（若你能正常用App也算合理，稍后人工确认）'}")
        wait_enter("再用一次 Home 手势回桌面，确认没有残留卡片，按回车")
        results["R-D-2 final clean"] = not D.overlay_present()
    else:
        results["R-D-2"] = "no-overlay-skip"

    print("\n== R-D-3 主观体验 + 引导核对 ==")
    jank = input("   卡片出现过程有没有明显卡顿/闪烁？(无/轻微/明显) ").strip()
    results["R-D-3 jank"] = jank
    D.launch("com.appause.android")
    time.sleep(3)
    wait_enter("打开的是正式版 Appause 首页：请看一眼顶部有没有'完成设置/Finish setup'卡片，电池项是否出现？看完按回车")
    D.screenshot(EV / "rd3-checklist.png")
    results["R-D-3 checklist screenshot"] = "rd3-checklist.png"

    print("\n== 收尾 ==")
    D.home(); time.sleep(2)
    residual = D.appause_windows()
    results["FINAL residual"] = residual or "clean"
    print(f"   残留窗口: {residual or '无，桌面干净'}")
    print(f"   焦点: {D.focus_pkg()}  导航: {D.nav_mode()}")
    print("\n=== 批次结果 ===")
    for k, v in results.items():
        print(f"  {k}: {v}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
