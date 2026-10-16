"""Generate the social share card (Open Graph / Twitter) and favicon assets.

Output (English):
  images/og-card.png            1200x630  social preview
  images/favicon-32.png          32x32    browser tab icon
  images/apple-touch-icon.png   180x180   iOS home screen icon

Output (Chinese, --lang zh):
  images/og-card-zh.png         1200x630  social preview

The card matches the landing page palette of the language it belongs to —
the shared cool-paper and cobalt theme for both languages — and uses real
emulator captures from assets/landing/shots/, so sharing the link
shows the actual product rather than a mock. The version string is read from
assets/landing/release.js, so unpublished Android builds do not appear on the card:

    python scripts/release/make_og_card.py           # English card + icons
    python scripts/release/make_og_card.py --lang zh # Chinese card
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
IMAGES = ROOT / "images"

CANVAS = (1200, 630)

# Windows system fonts. Segoe UI matches the landing page's font stack
# ("Segoe UI Variable Display" falls back to Segoe UI), Consolas stands in for
# the utility/mono face used by the page. Microsoft YaHei covers Chinese.
FONT_SEMIBOLD = Path("C:/Windows/Fonts/seguisb.ttf")
FONT_REGULAR = Path("C:/Windows/Fonts/segoeui.ttf")
FONT_MONO = Path("C:/Windows/Fonts/consola.ttf")
FONT_SERIF = Path("C:/Windows/Fonts/georgiab.ttf")
FONT_CJK_SERIF = Path("C:/Windows/Fonts/NotoSerifSC-VF.ttf")
FONT_CJK_BOLD = Path("C:/Windows/Fonts/msyhbd.ttc")
FONT_CJK_REGULAR = Path("C:/Windows/Fonts/msyh.ttc")

# Per-language copy. Chinese wording follows the app's own values-zh strings
# (分组 / 冷却 / 再次提醒 / 自定义提示) rather than a literal translation.
COPY = {
    "en": {
        "shot": Path("en-pause-counting.png"),
        "output": "og-card.png",
        "fonts": (FONT_SERIF, FONT_REGULAR, FONT_REGULAR),
        "label": "Free Android focus tool",
        "label_tracking": 0.0,
        "headline": "Pause first.\nThen decide.",
        "headline_fit": "Then decide.",
        "body": (
            "A short cooldown before an app opens. "
            "Continue, or go home. You decide."
        ),
        "alt": "Appause pause screen asking for a reason before a selected app opens",
    },
    "zh": {
        "shot": Path("zh-pause-counting.png"),
        "output": "og-card-zh.png",
        "fonts": (FONT_CJK_SERIF, FONT_CJK_REGULAR, FONT_CJK_REGULAR),
        "label": "安卓专注工具 · 免费开源",
        "label_tracking": 0.0,
        "headline": "先停一下，\n再决定。",
        "headline_fit": "先停一下，",
        "body": "Appause 在你选定的应用打开前加一道冷却，让「继续」重新成为你自己做的决定。",
        "alt": "Appause 暂停页：在选定应用打开前先问你为什么",
    },
}



# Both published pages use the same Appause palette.
PAGE_PALETTE = {
    "paper": (245, 247, 252),
    "panel": (220, 227, 255),
    "ink": (27, 27, 31),
    "ink_soft": (74, 79, 99),
    "rule": (217, 221, 232),
    "accent": (41, 98, 255),
}
PALETTE = {"en": PAGE_PALETTE, "zh": PAGE_PALETTE}

# The icon art is ~9.2% empty on every side (Android adaptive icon safe zone),
# so crop that padding before drawing it in the wordmark row.
ICON_CROP_INSET = 0.092


def version_name() -> str:
    """Use the public website version, not an unpublished Android build."""
    release = (ROOT / "assets" / "landing" / "release.js").read_text(encoding="utf-8")
    match = re.search(r"version:\s*'([^']+)'", release)
    if not match:
        raise SystemExit("version not found in assets/landing/release.js")
    return match.group(1)


def load_icon() -> Image.Image:
    icon = Image.open(IMAGES / "appause-icon.png").convert("RGBA")
    inset = round(min(icon.size) * ICON_CROP_INSET)
    return icon.crop((inset, inset, icon.width - inset, icon.height - inset))


def rounded(img: Image.Image, radius: int) -> Image.Image:
    """Apply an anti-aliased rounded-corner mask to an RGBA image."""
    scale = 4
    mask = Image.new("L", (img.width * scale, img.height * scale), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, img.width * scale - 1, img.height * scale - 1),
        radius=radius * scale,
        fill=255,
    )
    img = img.copy()
    img.putalpha(mask.resize(img.size, Image.LANCZOS))
    return img


def drop_shadow(canvas: Image.Image, box: tuple[int, int, int, int], radius: int,
                blur: int = 26, offset: int = 14, alpha: int = 60) -> None:
    """Draw a soft shadow for a rounded rectangle directly onto the canvas."""
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(
        (box[0], box[1] + offset, box[2], box[3] + offset),
        radius=radius,
        fill=(20, 26, 40, alpha),
    )
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(blur)))


def tracked_text(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str,
                 font: ImageFont.FreeTypeFont, fill, tracking: float) -> None:
    """Draw text with letter spacing, which Pillow has no native support for."""
    x, y = xy
    for char in text:
        draw.text((x, y), char, font=font, fill=fill)
        x += font.getlength(char) + tracking


def wrap(text: str, font: ImageFont.FreeTypeFont, max_width: float) -> list[str]:
    """Greedy wrap measured with real font metrics.

    Chinese has no spaces, so a "word" can be far wider than one line. Tokens
    that do not fit on their own fall back to character-level breaking, which
    makes the same function usable for both languages.
    """
    lines: list[str] = []
    current = ""
    for word in text.split():
        if font.getlength(word) > max_width:
            # CJK run: break it character by character.
            for char in word:
                if font.getlength(current + char) > max_width and current:
                    lines.append(current)
                    current = char
                else:
                    current += char
            continue
        candidate = f"{current} {word}".strip()
        if font.getlength(candidate) <= max_width or not current:
            current = candidate
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def fit_font(path: Path, text: str, max_width: float, start: int) -> ImageFont.FreeTypeFont:
    """Largest font size at which `text` still fits within `max_width`."""
    for size in range(start, 12, -1):
        font = ImageFont.truetype(str(path), size)
        if font.getlength(text) <= max_width:
            return font
    return ImageFont.truetype(str(path), 12)


def content_crop(img: Image.Image, padding: int = 32) -> Image.Image:
    """Trim the empty band below the capture's content.

    The raw captures are 1080x2400 and the pause screen only occupies the top
    ~66% of that, so keeping the full frame would render the interface tiny on a
    share card. Cropping to the content keeps the reason chips legible.
    """
    grey = img.convert("L")
    width, height = grey.size
    pixels = grey.load()
    last_row = 0
    for y in range(height):
        row = [pixels[x, y] for x in range(0, width, 8)]
        if any(channel < 245 for channel in row):
            last_row = y
    # Guard against over-cropping if a capture is ever mostly blank.
    last_row = max(last_row, round(height * 0.4))
    bottom = min(height, last_row + padding)
    return img.crop((0, 0, width, bottom))


def build_card(lang: str) -> Image.Image:
    copy = COPY[lang]
    pal = PALETTE[lang]
    font_head, font_body, font_util = copy["fonts"]

    canvas = Image.new("RGBA", CANVAS, pal["paper"] + (255,))

    margin = 72
    version = version_name()
    draw = ImageDraw.Draw(canvas)

    # ---- right stage: tinted panel holding the signature pause screen -----
    # The panel is sized to the capture plus even padding, so the shot is never
    # cropped further and the copy column gets a fixed width.
    shot = content_crop(Image.open(ROOT / "assets" / "landing" / "shots" / copy["shot"]).convert("RGBA"))
    pad = 32
    shot_h = 608 - 58 - pad * 2
    shot_w = round(shot_h * shot.width / shot.height)
    panel = (CANVAS[0] - margin - shot_w - pad * 2, 58, CANVAS[0] - margin, 608)

    draw.rounded_rectangle(panel, radius=38, fill=pal["panel"] + (255,))

    shot_x = panel[0] + pad
    shot_y = 58 + pad
    shot = rounded(shot.resize((shot_w, shot_h), Image.LANCZOS), 24)
    drop_shadow(canvas, (shot_x, shot_y, shot_x + shot_w, shot_y + shot_h), 24, alpha=58)
    canvas.alpha_composite(shot, (shot_x, shot_y))
    draw.rounded_rectangle(
        (shot_x, shot_y, shot_x + shot_w - 1, shot_y + shot_h - 1),
        radius=24,
        outline=pal["rule"] + (255,),
        width=1,
    )

    text_max = panel[0] - margin - 56  # keep copy clear of the stage

    # ---- wordmark row -----------------------------------------------------
    icon = rounded(load_icon(), 12).resize((52, 52), Image.LANCZOS)
    canvas.alpha_composite(icon, (margin, 66))
    draw.text((margin + 68, 74), "Appause",
              font=ImageFont.truetype(str(FONT_SEMIBOLD), 34), fill=pal["ink"])

    # ---- label ------------------------------------------------------------
    tracked_text(
        draw,
        (margin, 186),
        copy["label"],
        ImageFont.truetype(str(font_head), 19),
        pal["accent"],
        copy["label_tracking"],
    )

    # ---- headline ---------------------------------------------------------
    # Chinese glyphs are roughly one em wide, so the same column fits far fewer
    # characters per line. Both the start size and the line height differ.
    is_zh = lang == "zh"
    head_start = 64 if is_zh else 76
    head_lead = 1.22 if is_zh else 1.02
    head_y = 226 if is_zh else 244
    body_size = 22 if is_zh else 25
    body_lead = 33 if is_zh else 35

    head_font = fit_font(font_head, copy["headline_fit"], text_max, head_start)
    head_lines = [line for paragraph in copy["headline"].split("\n") for line in wrap(paragraph, head_font, text_max)]
    y = head_y
    for line in head_lines:
        draw.text((margin, y), line, font=head_font, fill=pal["ink"])
        y += round(head_font.size * head_lead)

    # ---- supporting copy --------------------------------------------------
    body_font = ImageFont.truetype(str(font_body), body_size)
    body_lines = wrap(copy["body"], body_font, text_max)
    y += 16
    for line in body_lines:
        draw.text((margin, y), line, font=body_font, fill=pal["ink_soft"])
        y += body_lead

    # ---- footer facts -----------------------------------------------------
    mono = ImageFont.truetype(str(font_util), 18)
    facts = (
        f"v{version}  \u00b7  Android 8.0+  \u00b7  MIT"
        if lang == "en"
        else f"v{version}  \u00b7  Android 8.0 及以上  \u00b7  MIT 开源"
    )
    draw.text((margin, 546), facts, font=mono, fill=pal["ink_soft"])

    # Guard rails: the copy must stay in its column and above the footer.
    if mono.getlength(facts) > text_max:
        raise SystemExit(f"footer facts overflow the copy column: {mono.getlength(facts):.0f}px")
    if y > 546:
        raise SystemExit(f"body copy runs into the footer row (ends at y={y})")
    for line in head_lines + body_lines:
        font = head_font if line in head_lines else body_font
        if font.getlength(line) > text_max:
            raise SystemExit(f"line overflows the copy column: {line!r}")
    # An orphan line (a lone full stop, a stray syllable) means the column is
    # too narrow for the chosen size, so fail instead of shipping it.
    for label, lines in (("headline", head_lines), ("body", body_lines)):
        if len(lines) > 1 and len(lines[-1].strip()) < 3:
            raise SystemExit(f"{label} leaves an orphan final line: {lines!r}")

    return canvas.convert("RGB")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--lang",
        choices=sorted(COPY),
        default="en",
        help="which card to render (icons are only written for --lang en)",
    )
    args = parser.parse_args()

    card = build_card(args.lang)
    card.save(IMAGES / COPY[args.lang]["output"], optimize=True)
    print(f"wrote images/{COPY[args.lang]['output']} {card.size}")

    if args.lang != "en":
        return

    icon = load_icon()
    icon.resize((32, 32), Image.LANCZOS).save(IMAGES / "favicon-32.png", optimize=True)
    # iOS masks the icon itself and renders transparency as black, so flatten first.
    apple = Image.new("RGB", (180, 180), PALETTE["en"]["paper"])
    apple.paste(icon.resize((180, 180), Image.LANCZOS), (0, 0), icon.resize((180, 180), Image.LANCZOS))
    apple.save(IMAGES / "apple-touch-icon.png", optimize=True)
    print("wrote images/favicon-32.png 32x32")
    print("wrote images/apple-touch-icon.png 180x180")


if __name__ == "__main__":
    main()
