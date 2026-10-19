#!/usr/bin/env python3
"""Rasterise fonts and weather icons into src/assets.h.

Everything the device needs is baked into the binary, so the Nook needs no
font files, no freetype and no image libraries.

Bitmaps are 8-bit "ink" (0 = paper, 255 = full black).
"""
import sys
from PIL import Image, ImageDraw, ImageFont

FONT_DIR = "/usr/share/fonts/truetype/dejavu/"
SANS = FONT_DIR + "DejaVuSans.ttf"
BOLD = FONT_DIR + "DejaVuSans-Bold.ttf"

ASCII = "".join(chr(c) for c in range(32, 127)) + "°"
DIGITS = "0123456789:"

# (C name, ttf, pixel size, charset)
FONTS = [
    ("font_clock_l", BOLD, 220, DIGITS),   # landscape clock
    ("font_clock_p", BOLD, 175, DIGITS),   # portrait clock
    ("font_temp", BOLD, 96, ASCII),
    ("font_big", SANS, 40, ASCII),
    ("font_med", SANS, 30, ASCII),
    ("font_med_b", BOLD, 30, ASCII),
    ("font_small", SANS, 22, ASCII),
]

ICON_NAMES = ["clear_day", "clear_night", "partly_day", "partly_night",
              "cloudy", "fog", "drizzle", "rain", "snow", "thunder"]
ICON_SIZES = [("icon_l", 170), ("icon_s", 76)]


def c_bytes(data):
    out = []
    for i in range(0, len(data), 24):
        out.append(",".join(str(b) for b in data[i:i + 24]))
    return ",\n".join(out)


def gen_font(name, path, size, charset):
    font = ImageFont.truetype(path, size)
    asc, desc = font.getmetrics()
    glyphs, blob = [], bytearray()
    for cp in range(32, 177):
        ch = chr(cp)
        if ch not in charset:
            glyphs.append((0, 0, 0, 0, 0, 0))
            continue
        adv = int(round(font.getlength(ch)))
        x0, y0, x1, y1 = font.getbbox(ch, anchor="ls")
        w, h = x1 - x0, y1 - y0
        if w <= 0 or h <= 0:
            glyphs.append((0, 0, 0, 0, adv, len(blob)))
            continue
        img = Image.new("L", (w, h), 0)
        ImageDraw.Draw(img).text((-x0, -y0), ch, font=font, fill=255, anchor="ls")
        glyphs.append((w, h, x0, y0, adv, len(blob)))
        blob += img.tobytes()
    lines = [f"static const unsigned char {name}_bmp[] = {{\n{c_bytes(blob)}\n}};",
             f"static const glyph_t {name}_glyphs[] = {{"]
    lines += ["  {%d,%d,%d,%d,%d,%d}," % g for g in glyphs]
    lines.append("};")
    lines.append(f"static const font_t {name} = {{ {asc}, {desc}, {name}_glyphs, {name}_bmp }};")
    return "\n".join(lines)


# ---------------------------------------------------------------- icons ----
def icon_image(kind, size):
    S = size * 4                      # supersample for anti-aliasing
    img = Image.new("L", (S, S), 255)
    d = ImageDraw.Draw(img)
    t = max(4, S // 22)               # stroke thickness

    def sun(cx, cy, r):
        import math
        for i in range(8):
            a = i * math.pi / 4
            d.line([(cx + math.cos(a) * r * 1.35, cy + math.sin(a) * r * 1.35),
                    (cx + math.cos(a) * r * 1.85, cy + math.sin(a) * r * 1.85)],
                   fill=0, width=t)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=0)

    def moon(cx, cy, r):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=0)
        o = r * 0.55
        d.ellipse([cx - r + o, cy - r - o * 0.5, cx + r + o, cy + r - o * 0.5], fill=255)

    def cloud(cx, cy, w, fill_dark=False):
        # union of circles: draw enlarged in black, then normal in white
        u = w / 10
        parts = [(-2.6, 0.8, 2.2), (0.2, -0.6, 3.2), (2.9, 0.9, 2.0)]
        base = [cx - 4.6 * u, cy + 0.8 * u, cx + 4.8 * u, cy + 3.0 * u]
        for pad, col in ((t, 0), (0, 80 if fill_dark else 255)):
            for px, py, pr in parts:
                r = pr * u + pad
                d.ellipse([cx + px * u - r, cy + py * u - r, cx + px * u + r, cy + py * u + r], fill=col)
            d.rounded_rectangle([base[0] - pad, base[1] - pad, base[2] + pad, base[3] + pad],
                                radius=1.1 * u + pad, fill=col)

    c = S / 2
    if kind == "clear_day":
        sun(c, c, S * 0.2)
    elif kind == "clear_night":
        moon(c, c, S * 0.3)
    elif kind in ("partly_day", "partly_night"):
        if kind == "partly_day":
            sun(S * 0.6, S * 0.36, S * 0.12)
        else:
            moon(S * 0.6, S * 0.3, S * 0.2)
        cloud(S * 0.44, S * 0.6, S * 0.66)
    else:
        cy = S * 0.36
        cloud(c, cy, S * 0.8, fill_dark=(kind == "thunder"))
        low = cy + 0.3 * S * 0.8 + t
        if kind == "fog":
            for i in range(3):
                y = low + i * S * 0.08
                d.line([(S * 0.18 + i * S * 0.05, y), (S * 0.82 - i * S * 0.05, y)], fill=0, width=t)
        elif kind in ("rain", "drizzle"):
            n, ln = (5, 0.07) if kind == "drizzle" else (4, 0.16)
            for i in range(n):
                x = S * 0.25 + i * S * 0.5 / (n - 1)
                d.line([(x, low + S * 0.03), (x - S * 0.05, low + S * (0.03 + ln))], fill=0, width=t)
        elif kind == "snow":
            for i in range(3):
                x, y = S * 0.28 + i * S * 0.22, low + S * 0.1 + (i % 2) * S * 0.05
                r = S * 0.075
                for dx, dy in ((1, 0), (0.5, 0.87), (-0.5, 0.87)):
                    d.line([(x - dx * r, y - dy * r), (x + dx * r, y + dy * r)], fill=0, width=max(3, t * 2 // 3))
        elif kind == "thunder":
            x, y = S * 0.5, low - S * 0.06
            d.polygon([(x + S * 0.02, y), (x - S * 0.12, y + S * 0.2), (x - S * 0.01, y + S * 0.2),
                       (x - S * 0.08, y + S * 0.38), (x + S * 0.14, y + S * 0.13),
                       (x + S * 0.03, y + S * 0.13), (x + S * 0.1, y)], fill=0)
    img = img.resize((size, size), Image.LANCZOS)
    return bytes(255 - p for p in img.tobytes())


def gen_icons():
    out = []
    for cname, size in ICON_SIZES:
        blob = bytearray()
        for k in ICON_NAMES:
            blob += icon_image(k, size)
        out.append(f"#define {cname.upper()}_SIZE {size}")
        out.append(f"static const unsigned char {cname}_bmp[] = {{\n{c_bytes(blob)}\n}};")
    out.append("enum { " + ", ".join("IC_" + n.upper() for n in ICON_NAMES) + ", IC_COUNT };")
    return "\n".join(out)


def preview(path):
    """Write a PNG contact sheet of all icons (for eyeballing)."""
    sheet = Image.new("L", (170 * len(ICON_NAMES), 170 + 76), 255)
    for i, k in enumerate(ICON_NAMES):
        for size, y in ((170, 0), (76, 170)):
            ink = icon_image(k, size)
            im = Image.frombytes("L", (size, size), bytes(255 - p for p in ink))
            sheet.paste(im, (i * 170, y))
    sheet.save(path)


def android_pngs(resdir):
    """Icons for the Android app: black ink with alpha, plus a launcher icon."""
    import os
    os.makedirs(resdir, exist_ok=True)
    for k in ICON_NAMES:
        for size, name in ((170, f"wx_{k}.png"), (76, f"wx_{k}_s.png")):
            ink = icon_image(k, size)
            a = Image.frombytes("L", (size, size), ink)
            img = Image.new("LA", (size, size), 0)
            img.putalpha(a)
            img.save(os.path.join(resdir, name))
    ink = icon_image("partly_day", 48)
    img = Image.new("LA", (48, 48), 0)
    img.putalpha(Image.frombytes("L", (48, 48), ink))
    img.save(os.path.join(resdir, "ic_launcher.png"))


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
        return
    if len(sys.argv) > 2 and sys.argv[1] == "--android":
        android_pngs(sys.argv[2])
        return
    out = ["/* Generated by tools/genassets.py - do not edit. */",
           "#ifndef ASSETS_H", "#define ASSETS_H",
           "typedef struct { short w, h, xoff, yoff, adv; int off; } glyph_t;",
           "typedef struct { int ascent, descent; const glyph_t *g; const unsigned char *bmp; } font_t;"]
    for f in FONTS:
        out.append(gen_font(*f))
    out.append(gen_icons())
    out.append("#endif")
    with open(sys.argv[1] if len(sys.argv) > 1 else "src/assets.h", "w") as fh:
        fh.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
