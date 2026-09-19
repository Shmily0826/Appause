"""One-shot: unlock Pro through the debug UI (F-17 close-out).

Path proven from code: Home "Upgrade to Pro" -> Pro screen -> debug-only
ProDebugTools card shows an "Unlock Pro" button while !isPro; the tap calls
settings.setProUnlocked(true) (the same key p4 seeds raw, but written by
DataStore itself). Uses uiautomator freely — no pause overlay is up here, so
the F-06 rebind side effect is harmless.
"""
from __future__ import annotations

import re
import time

from campaign_lib import APPAUSE, adb_shell


def dump_text() -> str:
    adb_shell("uiautomator dump /sdcard/ui_unlock.xml >/dev/null")
    return adb_shell("cat /sdcard/ui_unlock.xml")


def centers(xml: str, want: str) -> list[tuple[int, int]]:
    out = []
    for node in re.finditer(r"<node[^>]*>", xml):
        s = node.group(0)
        if f'text="{want}"' in s:
            m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                out.append(((x1 + x2) // 2, (y1 + y2) // 2))
    return out


def texts(xml: str) -> str:
    return " | ".join(t.strip() for t in re.findall(r'text="([^"]+)"', xml) if t.strip())


def tap(xy: tuple[int, int]) -> None:
    adb_shell(f"input tap {xy[0]} {xy[1]}")


def main() -> int:
    adb_shell(f"am force-stop {APPAUSE}")
    adb_shell(f"monkey -p {APPAUSE} 1 >/dev/null")
    time.sleep(4)
    xml = dump_text()
    hits = centers(xml, "Upgrade to Pro") or centers(xml, "Pro")
    print("HOME texts:", texts(xml)[:400])
    if not hits:
        print("NO-Pro-ENTRY")
        return 1
    tap(hits[0])
    time.sleep(3)
    xml = dump_text()
    print("PRO texts:", texts(xml)[:400])
    hits = centers(xml, "Unlock Pro")
    if not hits:
        print("NO-UNLOCK-BUTTON (already unlocked? look at PRO texts)")
        # The card hides Unlock Pro once isPro; report the state line instead.
        return 2
    tap(hits[0])
    time.sleep(3)
    xml = dump_text()
    print("AFTER texts:", texts(xml)[:400])
    ok = "Unlock Pro" not in texts(xml)
    print("UNLOCKED" if ok else "STILL-LOCKED")
    return 0 if ok else 3


if __name__ == "__main__":
    raise SystemExit(main())
