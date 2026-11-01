#!/usr/bin/env python3
"""
make_icon.py — draws the Vortex Engine app icon and writes every raster the builds need.

Design (matches the studio palette, zero blue):
  * cream stock square with a faint kraft vignette and a hairline ink border
  * drafting registration ticks in the corners
  * one gold "vortex sweep": a chevron that curls into a spiral, tapering to a point
  * a small ink crescent core so the mark reads even at 48 px

Everything is drawn at 8x then downsampled with LANCZOS, so it stays crisp at every
Android density and as the desktop window icon.

Usage:  python3 scripts/make_icon.py
Writes: app/src/main/res/mipmap-*/ic_launcher.png  (48/72/96/144/192)
        desktop/resources/icon.png                 (256)
        desktop/resources/icon.ico                 (multi-size, for the Windows .exe)
        docs/icon-512.png                          (marketing / store listing)
"""
import math
import os

from PIL import Image, ImageDraw

INK = (0x33, 0x29, 0x1D, 255)
INK_SOFT = (0x33, 0x29, 0x1D, 150)
PAPER = (0xFF, 0xFD, 0xF6, 255)
KRAFT = (0xF0, 0xE4, 0xC9, 255)
GOLD_HI = (0xE2, 0xB8, 0x3C, 255)
GOLD = (0xC9, 0x9A, 0x1E, 255)
GOLD_DK = (0x9A, 0x71, 0x0C, 255)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = 1024          # design size
SS = 8            # supersample factor
N = S * SS


def rounded_mask(size, radius_ratio=0.225):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)
    return m


def paper_background():
    """Cream sheet with a soft kraft vignette (radial, warm, no grey)."""
    img = Image.new("RGBA", (N, N), PAPER)
    px = img.load()
    cx = cy = N / 2.0
    maxd = math.hypot(cx, cy)
    for y in range(N):
        for x in range(0, N, 4):  # every 4th column, then stretch: fast enough and invisible
            d = math.hypot(x - cx, y - cy) / maxd
            t = min(1.0, d * 1.18)
            t = t * t * 0.55
            r = int(PAPER[0] * (1 - t) + KRAFT[0] * t)
            g = int(PAPER[1] * (1 - t) + KRAFT[1] * t)
            b = int(PAPER[2] * (1 - t) + KRAFT[2] * t)
            for k in range(4):
                if x + k < N:
                    px[x + k, y] = (r, g, b, 255)
    return img


def draw_sweep(draw):
    """
    The mark: a vortex sweep. Two arms spiral out from the core in opposite directions and
    taper to points; the thick upper arm carries the gold gradient, the thin lower arm is ink
    so the mark keeps contrast on a cream sheet.
    """
    cx = cy = N / 2.0

    def arm(start_angle, turns, r0, r1, w0, w1, colour):
        # sample a logarithmic spiral, build a filled ribbon with tapering width
        steps = 220
        pts_out, pts_in = [], []
        for i in range(steps + 1):
            f = i / steps
            ang = start_angle + turns * 2 * math.pi * f * 0.82
            rad = r0 + (r1 - r0) * (f ** 0.82)
            w = w0 + (w1 - w0) * f
            nx, ny = math.cos(ang), math.sin(ang)
            # perpendicular offset gives the ribbon its width, tapering to a point
            tx, ty = -ny, nx
            pts_out.append((cx + nx * rad + tx * w, cy + ny * rad + ty * w))
            pts_in.append((cx + nx * rad - tx * w, cy + ny * rad - ty * w))
        poly = pts_out + pts_in[::-1]
        draw.polygon(poly, fill=colour)

    r = N * 0.300
    arm(start_angle=-2.42, turns=0.60, r0=r * 0.10, r1=r, w0=N * 0.006, w1=N * 0.072, colour=GOLD)
    arm(start_angle=-2.42, turns=0.60, r0=r * 0.10, r1=r, w0=N * 0.002, w1=N * 0.028, colour=GOLD_HI)
    arm(start_angle=0.72, turns=0.44, r0=r * 0.06, r1=r * 0.80, w0=N * 0.002, w1=N * 0.034, colour=INK)

    # core: ink disc with a gold crescent
    cr = N * 0.052
    draw.ellipse([cx - cr, cy - cr, cx + cr, cy + cr], fill=INK)
    draw.ellipse([cx - cr * 0.42, cy - cr * 0.42, cx + cr * 0.42, cy + cr * 0.42], fill=GOLD_HI)


def mark_layer():
    """Draw the vortex sweep on its own transparent layer so it can be centred by its real bbox."""
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    draw_sweep(ImageDraw.Draw(layer))
    bbox = layer.getbbox()
    if not bbox:
        return layer
    cropped = layer.crop(bbox)
    target = int(N * 0.60)
    scale = target / max(cropped.size)
    resized = cropped.resize((max(1, int(cropped.width * scale)),
                              max(1, int(cropped.height * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    out.paste(resized, ((N - resized.width) // 2, (N - resized.height) // 2), resized)
    return out


def build():
    base = paper_background()
    d = ImageDraw.Draw(base)

    base.alpha_composite(mark_layer())

    # hairline inner rule + drafting ticks
    inset = N * 0.055
    d.rounded_rectangle([inset, inset, N - 1 - inset, N - 1 - inset],
                        radius=int(N * 0.135), outline=INK_SOFT, width=max(1, int(N * 0.006)))
    tick = N * 0.045
    off = N * 0.085
    for (x, y, dx, dy) in [(off, off, 1, 1), (N - off, off, -1, 1),
                           (off, N - off, 1, -1), (N - off, N - off, -1, -1)]:
        d.line([(x, y), (x + dx * tick, y)], fill=INK, width=max(1, int(N * 0.007)))
        d.line([(x, y), (x, y + dy * tick)], fill=INK, width=max(1, int(N * 0.007)))

    # rounded-square silhouette for the launcher formats
    mask = rounded_mask(N)
    base.putalpha(mask)

    # full-bleed square version (kept for the .ico, whose shell draws its own shape)
    return base


def save_all(icon):
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, px in densities.items():
        d = os.path.join(ROOT, "app/src/main/res/mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        icon.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
        print("  mipmap-%-7s %dx%d" % (name, px, px))

    res = os.path.join(ROOT, "desktop/resources")
    os.makedirs(res, exist_ok=True)
    big = icon.resize((256, 256), Image.LANCZOS)
    big.save(os.path.join(res, "icon.png"))
    print("  desktop/resources/icon.png 256x256")

    ico = os.path.join(res, "icon.ico")
    big.save(ico, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("  desktop/resources/icon.ico (16..256)")

    docs = os.path.join(ROOT, "docs")
    os.makedirs(docs, exist_ok=True)
    icon.resize((512, 512), Image.LANCZOS).save(os.path.join(docs, "icon-512.png"))
    print("  docs/icon-512.png")


if __name__ == "__main__":
    print("Vortex Engine icon:", S, "px design,", SS, "x supersampled")
    save_all(build())
    print("done")
