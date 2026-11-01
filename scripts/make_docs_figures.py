#!/usr/bin/env python3
"""
make_docs_figures.py — the annotated teaching figures for the docs landing page.

The docs used to open with a wall of API signatures. The landing page now opens with the studio
itself: real screenshots, with gold highlight boxes on the regions that matter. The numbered
badges and the explanatory labels are drawn by the HTML on top of these images (position:
absolute, in % coordinates), so they stay crisp on a phone and stay easy to edit.

Inputs (real captures, never mock-ups):
  /tmp/vc/render/doc_studio.png      paper studio, from  VortexDesktopStudio --shot
  /tmp/vc/render/doc_dark.png        the same window after the palette was switched
  /home/user/preview-voxel-play.png  an actual Blockfall session, with its game UI

Outputs (same bytes to both targets so the phone WebView shows the identical page):
  desktop/resources/docs/*.jpg       -> classpath /docs/*.jpg on desktop
  app/src/main/assets/docs/*.jpg     -> assets/docs/*.jpg on Android

The REGIONS tables below are the single source of truth. They are duplicated in
DocsSite.java (STUDIO_REGIONS / GAME_REGIONS) because the HTML overlay needs the same numbers.
"""
import os
import shutil

from PIL import Image, ImageDraw

VE = "/home/user/VortexEngine"
OUT_DESKTOP = os.path.join(VE, "desktop/resources/docs")
OUT_ASSETS = os.path.join(VE, "app/src/main/assets/docs")

GOLD_INK = (0x8A, 0x62, 0x0A, 255)

# --- source pixel boxes, measured on the real captures ------------------------------
STUDIO_CROP = (0, 36, 1440, 940)
STUDIO_REGIONS = [
    (6, 76, 320, 322),       # 1 scene hierarchy
    (326, 76, 946, 726),     # 2 viewport
    (6, 330, 320, 896),      # 3 inspector
    (960, 78, 1434, 120),    # 4 dock tabs
    (960, 122, 1434, 726),   # 5 code editor
    (326, 736, 960, 896),    # 6 console / terminal
    (1120, 6, 1434, 70),     # 7 palette + build
]
DARK_REGIONS = [
    (960, 78, 1434, 300),    # 1 palette / build row
    (6, 330, 320, 896),      # 2 inspector
    (326, 76, 946, 726),     # 3 viewport
]
GAME_REGIONS = [
    (10, 12, 330, 46),       # 1 HUD row
    (912, 24, 1104, 54),     # 2 objective chip
    (410, 50, 790, 82),      # 3 banner
    (286, 536, 920, 618),    # 4 dialog box
    (350, 636, 850, 660),    # 5 hint strip
    (376, 664, 818, 706),    # 6 hotbar
]


def outline(im, boxes, radius=11, width=2):
    """Gold ink boxes on the screenshot; the wash is drawn underneath so text stays readable."""
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    for b in boxes:
        ImageDraw.Draw(layer).rounded_rectangle(b, radius=radius, fill=(255, 226, 120, 34))
    im = Image.alpha_composite(im, layer)
    d = ImageDraw.Draw(im, "RGBA")
    for b in boxes:
        d.rounded_rectangle(b, radius=radius, outline=GOLD_INK, width=width)
    return im


def load(path, width):
    im = Image.open(path).convert("RGBA")
    if im.width != width:
        im = im.resize((width, int(im.height * width / im.width)), Image.LANCZOS)
    return im


def figure_studio():
    src = load("/tmp/vc/render/doc_studio.png", 1440).crop(STUDIO_CROP)
    return outline(src, STUDIO_REGIONS).convert("RGB")


def figure_dark():
    src = load("/tmp/vc/render/doc_dark.png", 1440).crop(STUDIO_CROP)
    return outline(src, DARK_REGIONS).convert("RGB")


def figure_game_ui():
    src = load("/home/user/preview-voxel-play.png", 1200)
    return outline(src, GAME_REGIONS, radius=8).convert("RGB")


def main():
    for out in (OUT_DESKTOP, OUT_ASSETS):
        os.makedirs(out, exist_ok=True)
    jobs = {
        "studio-tour.jpg": figure_studio(),
        "dark-mode.jpg": figure_dark(),
        "game-ui.jpg": figure_game_ui(),
    }
    for name, im in jobs.items():
        for out in (OUT_DESKTOP, OUT_ASSETS):
            im.save(os.path.join(out, name), "JPEG", quality=80, optimize=True, progressive=True)
        print("  %-16s %4dx%-4d %3d KB" % (name, im.width, im.height,
                                           os.path.getsize(os.path.join(OUT_DESKTOP, name)) // 1024))
    for name in jobs:
        shutil.copyfile(os.path.join(OUT_DESKTOP, name), os.path.join("/tmp/vc/render", name))
    print("done")


if __name__ == "__main__":
    main()
