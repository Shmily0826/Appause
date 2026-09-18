"""Generate the social share card (Open Graph / Twitter) and favicon assets.

Output:
  images/og-card.png            1200x630  social preview
  images/favicon-32.png          32x32    browser tab icon
  images/apple-touch-icon.png   180x180   iOS home screen icon

The card matches the landing page palette (Cobalt theme from index.html) and
uses real app captures from images/screenshots/en/, so sharing the link shows
the actual product rather than a mock. The version string is read from
app/build.gradle.kts, so re-run this after every release:

    python scripts/make_og_card.py
"""

from __future__ import annotations

import math
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMAGES = ROOT / "images"
SCREENSHOTS = IMAGES / "screenshots" / "en"

CANVAS = (1200, 630)

# Windows system fonts. Segoe UI matches the landing page's font stack
# ("Segoe UI Variable Display" falls back to Segoe UI), Consolas stands in for
# the utility/mono face used by the page.
FONT_SEMIBOLD = Path("C:/Windows/Fonts/seguisb.ttf")
FONT_REGULAR = Path("C:/Windows/Fonts/segoeui.ttf")
FONT_MONO = Path("C:/Windows/Fonts/consola.ttf")


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


PAPER = oklch(0.980, 0.006, 255)
PAPER_BLUE = oklch(0.930, 0.035, 260)
SURFACE = (255, 255, 255)
INK = oklch(0.240, 0.045, 258)
INK_SOFT = oklch(0.470, 0.035, 258)
RULE = oklch(0.880, 0.018, 258)
ACCENT_DEEP = oklch(0.440, 0.180, 263)

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
    """Greedy word wrap measured with real font metrics."""
    lines: list[str] = []
    current = ""
    for word in text.split():
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


def build_card() -> Image.Image:
    canvas = Image.new("RGBA", CANVAS, PAPER + (255,))

    margin = 72
    version = version_name()
    draw = ImageDraw.Draw(canvas)

    # ---- right stage: tinted panel holding the signature pause screen -----
    # The panel is sized to the capture plus even padding, so the shot is never
    # cropped further and the copy column gets a fixed width.
    shot = content_crop(Image.open(SCREENSHOTS / "pause.png").convert("RGBA"))
    pad = 32
    shot_h = 608 - 58 - pad * 2
    shot_w = round(shot_h * shot.width / shot.height)
    panel = (CANVAS[0] - margin - shot_w - pad * 2, 58, CANVAS[0] - margin, 608)

    draw.rounded_rectangle(panel, radius=38, fill=PAPER_BLUE + (255,))

    shot_x = panel[0] + pad
    shot_y = 58 + pad
    shot = rounded(shot.resize((shot_w, shot_h), Image.LANCZOS), 24)
    drop_shadow(canvas, (shot_x, shot_y, shot_x + shot_w, shot_y + shot_h), 24, alpha=58)
    canvas.alpha_composite(shot, (shot_x, shot_y))
    draw.rounded_rectangle(
        (shot_x, shot_y, shot_x + shot_w - 1, shot_y + shot_h - 1),
        radius=24,
        outline=RULE + (255,),
        width=1,
    )

    text_max = panel[0] - margin - 56  # keep copy clear of the stage

    # ---- wordmark row -----------------------------------------------------
    icon = rounded(load_icon(), 12).resize((52, 52), Image.LANCZOS)
    canvas.alpha_composite(icon, (margin, 66))
    draw.text((margin + 68, 74), "Appause",
              font=ImageFont.truetype(str(FONT_SEMIBOLD), 34), fill=INK)

    # ---- label ------------------------------------------------------------
    tracked_text(
        draw,
        (margin, 186),
        "AN INTENTIONAL OPENING STARTS HERE",
        ImageFont.truetype(str(FONT_SEMIBOLD), 19),
        ACCENT_DEEP,
        2.4,
    )

    # ---- headline ---------------------------------------------------------
    headline = "Put intention between tap and scroll."
    head_font = fit_font(FONT_SEMIBOLD, "Put intention between", text_max, 76)
    head_lines = wrap(headline, head_font, text_max)
    y = 244
    for line in head_lines:
        draw.text((margin, y), line, font=head_font, fill=INK)
        y += round(head_font.size * 1.02)

    # ---- supporting copy --------------------------------------------------
    body_font = ImageFont.truetype(str(FONT_REGULAR), 25)
    body_lines = wrap(
        "Appause interrupts distracting app openings at the moment of action, so continuing "
        "becomes a decision you make on purpose.",
        body_font,
        text_max,
    )
    y += 16
    for line in body_lines:
        draw.text((margin, y), line, font=body_font, fill=INK_SOFT)
        y += 35

    # ---- footer facts -----------------------------------------------------
    mono = ImageFont.truetype(str(FONT_MONO), 18)
    facts = f"v{version}  \u00b7  Android 8.0+  \u00b7  no account required  \u00b7  MIT"
    draw.text((margin, 546), facts, font=mono, fill=INK_SOFT)

    # Guard rails: the copy must stay in its column and above the footer.
    if mono.getlength(facts) > text_max:
        raise SystemExit(f"footer facts overflow the copy column: {mono.getlength(facts):.0f}px")
    if y > 546:
        raise SystemExit(f"body copy runs into the footer row (ends at y={y})")
    for line in head_lines + body_lines:
        font = head_font if line in head_lines else body_font
        if font.getlength(line) > text_max:
            raise SystemExit(f"line overflows the copy column: {line!r}")

    return canvas.convert("RGB")


def main() -> None:
    card = build_card()
    card.save(IMAGES / "og-card.png", optimize=True)
    print(f"wrote images/og-card.png {card.size}")

    icon = load_icon()
    icon.resize((32, 32), Image.LANCZOS).save(IMAGES / "favicon-32.png", optimize=True)
    # iOS masks the icon itself and renders transparency as black, so flatten first.
    apple = Image.new("RGB", (180, 180), PAPER)
    apple.paste(icon.resize((180, 180), Image.LANCZOS), (0, 0), icon.resize((180, 180), Image.LANCZOS))
    apple.save(IMAGES / "apple-touch-icon.png", optimize=True)
    print("wrote images/favicon-32.png 32x32")
    print("wrote images/apple-touch-icon.png 180x180")


if __name__ == "__main__":
    main()
