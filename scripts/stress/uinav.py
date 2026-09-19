#!/usr/bin/env python3
"""Small UI helper for driving the emulator over adb.

Why this exists: Appause renders with Jetpack Compose, so the classic
"read resource-id" approach mostly fails — nodes carry text or a
content-description but usually no stable resource-id. This helper does the
only thing that reliably works: dump the accessibility tree, match a node by
its visible text / description, compute the centre of its bounds, and tap it.

Usage:
    python uinav.py nodes                 # list every labelled node
    python uinav.py tap "Settings"        # tap centre of the node
    python uinav.py wait "Your Groups"    # poll until a node appears
"""

from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

REMOTE_XML = "/sdcard/uinav.xml"
LOCAL_XML = Path(__file__).parent / "evidence" / "uinav.xml"


def adb(*args: str) -> str:
    return subprocess.run(
        ["adb", "-s", "emulator-5554", *args], capture_output=True, text=True, encoding="utf-8", errors="replace"
    ).stdout


def dump() -> list[dict]:
    """Dump the current window and return every labelled node."""
    adb("shell", "uiautomator", "dump", REMOTE_XML)
    subprocess.run(["adb", "-s", "emulator-5554", "pull", REMOTE_XML, str(LOCAL_XML)],
                   capture_output=True)
    if not LOCAL_XML.exists():
        return []
    nodes: list[dict] = []
    for node in ET.parse(LOCAL_XML).iter("node"):
        text = node.get("text") or ""
        desc = node.get("content-desc") or ""
        if not text and not desc:
            continue
        bounds = _bounds(node.get("bounds") or "")
        if not bounds:
            continue
        nodes.append(
            {
                "text": text,
                "desc": desc,
                "label": text or desc,
                "bounds": bounds,
                "clickable": node.get("clickable") == "true",
                "class": (node.get("class") or "").split(".")[-1],
            }
        )
    return nodes


def _bounds(raw: str) -> tuple[int, int, int, int] | None:
    """'[84,572][996,698]' -> (84, 572, 996, 698)."""
    numbers = [int(n) for n in re.findall(r"-?\d+", raw)]
    return tuple(numbers) if len(numbers) == 4 else None  # type: ignore[return-value]


def search(pattern: str, nodes: list[dict] | None = None) -> list[dict]:
    needle = re.compile(pattern, re.IGNORECASE)
    return [n for n in (nodes if nodes is not None else dump()) if needle.search(n["label"])]


def centre(node: dict) -> tuple[int, int]:
    x1, y1, x2, y2 = node["bounds"]
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(pattern: str, timeout: float = 5.0) -> bool:
    """Tap the centre of the first node whose label matches, waiting if needed."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        hits = search(pattern)
        if hits:
            # Prefer a clickable node when one exists.
            target = next((n for n in hits if n["clickable"]), hits[0])
            x, y = centre(target)
            adb("shell", "input", "tap", str(x), str(y))
            print(f"tapped {target['label']!r} at ({x},{y})")
            return True
        time.sleep(0.4)
    print(f"NOT FOUND: {pattern!r}")
    return False


def wait(pattern: str, timeout: float = 15.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if search(pattern):
            print(f"found {pattern!r}")
            return True
        time.sleep(0.5)
    print(f"TIMEOUT waiting for {pattern!r}")
    return False


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    command = sys.argv[1]
    if command == "nodes":
        for node in dump():
            print(f"{str(node['bounds']):26} | {node['label'][:44]:44} | {node['class']}")
    elif command == "tap":
        return 0 if tap(sys.argv[2]) else 1
    elif command == "wait":
        return 0 if wait(sys.argv[2]) else 1
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
