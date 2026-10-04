#!/usr/bin/env python3
"""Generate the 360w / 720w WebP variants used by the landing pages.

Why this exists
---------------
The zh landing page has shipped `<picture>` + srcset with 360w/720w WebP
variants since commit 6aedeee, but that batch arrived without a generator
script, so the derivatives could not be rebuilt. The en page had no WebP at
all and was serving 1080x2400 PNGs (up to ~465 KB above the fold) into boxes
that never render wider than ~333 CSS px.

Source of truth for the sizes
-----------------------------
Measured rendered widths of `figure img` on en/index.html (see the landing
review notes): the widest any shot gets is 333 CSS px at 768-1440px, i.e.
~666 px at devicePixelRatio 2. So 720w covers every real case and 360w
covers 1x. Zoom beyond 200% is served by `sizes` picking the 720w, which is
still far lighter than the original PNG.

Usage
-----
    python scripts/release/make_webp.py            # write the variants
    python scripts/release/make_webp.py --check    # report only, no writes

Requires Pillow with WebP support (present in the system Python 3.14 on this
machine; the managed 3.13 does not have Pillow at all).
"""

from __future__ import annotations

import argparse
import os
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover - environment guard
    sys.exit(
        "Pillow is required. On this machine use the system Python:\n"
        "  C:/Users/Shmily/AppData/Local/Microsoft/WindowsApps/python.exe "
        "scripts/release/make_webp.py"
    )

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHOTS = os.path.join(REPO, "images", "screenshots")

# Output widths. 360 = 1x at the widest mobile render, 720 = 2x at the widest
# desktop render. Anything larger would be bytes the layout never uses.
WIDTHS = (360, 720)

# Shared quality settings. `method=6` is the slowest/most thorough WebP
# encoder setting and costs nothing at build time for files this small.
WEBP_QUALITY = 80
WEBP_METHOD = 6

# Which source PNGs get derivatives, per language directory.
# Keys are the source basename; values are the slug used for the output name,
# so `pause` on the zh side becomes `pause-crop` after the crop step.
#
# The zh entries are suppressed by default: those six files are already live
# and referenced by zh.html, so regenerating them would churn a page that is
# working, for a ~5% size win. Pass --include-zh when you deliberately want to
# rebuild them (e.g. after re-shooting a screenshot).
TARGETS = {
    "en": {"pause": "pause", "home": "home", "group": "group", "statistics": "statistics"},
}
ZH_TARGETS = {"pause-crop": "pause-crop", "group": "group", "statistics": "statistics"}


def variants_for(src_path: str, slug: str) -> list[tuple[str, int, int]]:
    """Return [(out_path, width, height)] for one source image."""
    with Image.open(src_path) as im:
        src_w, src_h = im.size
    out = []
    for w in WIDTHS:
        h = round(src_h * w / src_w)
        out.append((os.path.join(os.path.dirname(src_path), f"{slug}-{w}.webp"), w, h))
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="report only, write nothing")
    ap.add_argument(
        "--include-zh",
        action="store_true",
        help="also rebuild the zh variants (rewrites files the live zh.html serves)",
    )
    args = ap.parse_args()

    targets = dict(TARGETS)
    if args.include_zh:
        targets["zh"] = ZH_TARGETS

    total_before = 0
    total_after = 0
    written = 0

    for lang, mapping in targets.items():
        lang_dir = os.path.join(SHOTS, lang)
        if not os.path.isdir(lang_dir):
            print(f"skip {lang}: {lang_dir} not found")
            continue
        print(f"\n[{lang}]")
        for src_name, slug in mapping.items():
            src = os.path.join(lang_dir, f"{src_name}.png")
            if not os.path.exists(src):
                print(f"  ! missing source {src}")
                continue
            src_bytes = os.path.getsize(src)
            total_before += src_bytes
            line = []
            for out_path, w, h in variants_for(src, slug):
                with Image.open(src) as im:
                    # Screenshots are RGBA but fully opaque; WebP with an alpha
                    # channel costs bytes for nothing, so flatten to RGB.
                    if im.mode in ("RGBA", "LA", "P"):
                        im = im.convert("RGBA")
                        bg = Image.new("RGB", im.size, (255, 255, 255))
                        bg.paste(im, mask=im.split()[-1])
                        resized = bg.resize((w, h), Image.LANCZOS)
                    else:
                        resized = im.convert("RGB").resize((w, h), Image.LANCZOS)
                    if not args.check:
                        resized.save(out_path, "WEBP", quality=WEBP_QUALITY, method=WEBP_METHOD)
                        written += 1
                    size = os.path.getsize(out_path) if not args.check else 0
                line.append(f"{w}w={size:,}B" if not args.check else f"{w}w")
            if args.check:
                print(f"  {src_name}.png {src_bytes:,}B -> " + ", ".join(line))
            else:
                got = sum(os.path.getsize(p) for p, _, _ in variants_for(src, slug))
                total_after += got
                saved = (1 - got / src_bytes) * 100
                print(f"  {src_name}.png {src_bytes:,}B -> {got:,}B  (-{saved:.1f}%)  " + ", ".join(line))

    if not args.check:
        print(f"\n{written} files written")
        if total_after:
            print(f"every source PNG vs its 720w variant: {total_before:,}B -> {total_after:,}B")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
