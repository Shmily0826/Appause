#!/usr/bin/env python3
"""Package the already-built Release APK using the version in app/build.gradle.kts.

This script is intentionally local-only: it neither creates GitHub releases nor
uses credentials. Run `./gradlew assembleRelease` first, then run this script.
"""

from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD_FILE = ROOT / "app" / "build.gradle.kts"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
OUTPUT_DIR = ROOT / "output"


def read_version() -> tuple[str, str]:
    text = BUILD_FILE.read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    if name is None or code is None:
        raise RuntimeError("Could not read versionName/versionCode from app/build.gradle.kts")
    return name.group(1), code.group(1)


def main() -> int:
    version_name, version_code = read_version()
    destination = OUTPUT_DIR / f"Appause-v{version_name}.apk"
    if not APK.is_file():
        print(f"Missing Release APK: {APK}", file=sys.stderr)
        print("Run ./gradlew assembleRelease first.", file=sys.stderr)
        return 1

    OUTPUT_DIR.mkdir(exist_ok=True)
    shutil.copy2(APK, destination)
    print(f"Packaged {destination.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
