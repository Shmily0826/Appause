"""Generate the social share card (Open Graph / Twitter) and favicon assets.

Output (English):
  images/og-card.png            1200x630  social preview
  images/favicon-32.png          32x32    browser tab icon
  images/apple-touch-icon.png   180x180   iOS home screen icon

Output (Chinese, --lang zh):
  images/og-card-zh.png         1200x630  social preview

The card matches the landing page palette of the language it belongs to —
Cobalt (index.html) for English, 纸墨朱红 (zh.html) for Chinese — and uses real
app captures from images/screenshots/<lang>/, so sharing the link
shows the actual product rather than a mock. The version string is read from
app/build.gradle.kts, so re-run this after every release:

    python scripts/release/make_og_card.py           # English card + icons
    python scripts/release/make_og_card.py --lang zh # Chinese card
"""

from __future__ import annotations

import argparse
import math
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
FONT_CJK_BOLD = Path("C:/Windows/Fonts/msyhbd.ttc")
FONT_CJK_REGULAR = Path("C:/Windows/Fonts/msyh.ttc")

# Per-language copy. Chinese wording follows the app's own values-zh strings
# (分组 / 冷却 / 再次提醒 / 自定义提示) rather than a literal translation.
COPY = {
    "en": {
        "shot": Path("screenshots/en/pause.png"),
        "output": "og-card.png",
        "fonts": (FONT_SEMIBOLD, FONT_REGULAR, FONT_MONO),
        "label": "AN INTENTIONAL OPENING STARTS HERE",
        "label_tracking": 2.4,
        "headline": "Put intention between tap and scroll.",
        "headline_fit": "Put intention between",
        "body": (
            "Appause interrupts distracting app openings at the moment of action, "
            "so continuing becomes a decision you make on purpose."
        ),
        "alt": "Appause pause screen asking for a reason before a selected app opens",
    },
    "zh": {
        "shot": Path("screenshots/zh/pause.png"),
        "output": "og-card-zh.png",
        "fonts": (FONT_CJK_BOLD, FONT_CJK_REGULAR, FONT_CJK_REGULAR),
        "label": "每 一 次 打 开 都 可 以 是 一 次 选 择",
        "label_tracking": 0.0,
        "headline": "先停一下，再决定。",
        "headline_fit": "先停一下，",
        "body": "Appause 在你选定的应用打开前加一道冷却，让「继续」重新成为你自己做的决定。",
        "alt": "Appause 暂停页：在选定应用打开前先问你为什么",
    },
}



# --------------------------------------------------------------------------
# Palette: parsed straight out of index.html's oklch() custom properties so the
# card cannot drift away from the page.
# --------------------------------------------------------------------------
def oklch(lightness: float, chroma: float, hue_deg: float) -> tuple[int, int, int]:
    """Convert oklch(L% C H) to an 8-bit sRGB tuple."""
    hue = math.radians(hue_deg)
    a = chroma * math.cos(hue)
    b = chroma * math.sin(hue)

    l_ = lightness + 0.3963377774 * a + 0.2158037573 * b
    m_ = lightness - 0.1055613458 * a - 0.0638541728 * b
    s_ = lightness - 0.0894841775 * a - 1.2914855480 * b

    l, m, s = l_**3, m_**3, s_**3

    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s

    def encode(channel: float) -> int:
        channel = max(0.0, min(1.0, channel))
        gamma = 1.055 * (channel ** (1 / 2.4)) - 0.055 if channel > 0.0031308 else 12.92 * channel
        return round(max(0.0, min(1.0, gamma)) * 255)

    return encode(r), encode(g), encode(bl)


# One palette per landing page. A share card painted in the other page's theme
# reads as a different product from the page it links to, so they are kept apart.
PALETTE: dict[str, dict[str, tuple[int, int, int]]] = {
    "en": {
        "paper": oklch(0.980, 0.006, 255),
        "panel": oklch(0.930, 0.035, 260),
        "ink": oklch(0.240, 0.045, 258),
        "ink_soft": oklch(0.470, 0.035, 258),
        "rule": oklch(0.880, 0.018, 258),
        "accent": oklch(0.440, 0.180, 263),
    },
    "zh": {
        # Copied from zh.html's :root tokens. If that page is restyled, re-check these.
        "paper": (0xF5, 0xF1, 0xE8),      # --paper         #f5f1e8
        "panel": (0xEC, 0xE7, 0xD9),      # --paper-raise   #ece7d9
        "ink": (0x1D, 0x1A, 0x16),        # --ink           #1d1a16
        "ink_soft": (0x5C, 0x55, 0x4A),   # --ink-soft      #5c554a
        "rule": (0xD9, 0xD2, 0xC3),       # --rule          #d9d2c3
        "accent": (0x7F, 0x2C, 0x20),     # --cinnabar-deep #7f2c20
    },
}

# The icon art is ~9.2% empty on every side (Android adaptive icon safe zone),
# so crop that padding before drawing it in the wordmark row.
ICON_CROP_INSET = 0.092


def version_name() -> str:
    """Read versionName from app/build.gradle.kts so the card tracks releases."""
    gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    if not match:
        raise SystemExit("versionName not found in app/build.gradle.kts")
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
    shot = content_crop(Image.open(IMAGES / copy["shot"]).convert("RGBA"))
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
              font=ImageFont.truetype(str(font_head), 34), fill=pal["ink"])

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
    head_lines = wrap(copy["headline"], head_font, text_max)
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
        f"v{version}  \u00b7  Android 8.0+  \u00b7  no account required  \u00b7  MIT"
        if lang == "en"
        else f"v{version}  \u00b7  Android 8.0 及以上  \u00b7  无需注册账号  \u00b7  MIT 开源"
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
