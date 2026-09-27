#!/usr/bin/env python3
"""Build a Garden edition's launcher and splash resources from its masters.

    tools/garden/branding/make_resources.py CUP_MASTER EMBLEM_MASTER RES_DIR \
        --edge RRGGBB --glow RRGGBB --frame L,T,R,B --radius R

Outside the app is the cup; inside is the emblem. This script never swaps
them, and it redraws, recolours and rescales-up nothing: every pixel it writes
is a master pixel, cropped, masked or scaled down.

- Adaptive foreground (mipmap-*/ic_launcher_foreground.webp): the cup master
  has its rounded-square container baked in. A launcher applies its own mask,
  so the container is cut away along a rounded rectangle 18 px inside the
  frame line (--frame, --radius, measured on the master), leaving the cup,
  its petals and their glow. That box is centred on the 108 dp canvas with
  its longest side at 66 dp, the adaptive safe zone.
- Monochrome layer: white, with the foreground's alpha where the artwork is
  solid (alpha 120-200 ramps in). Android uses only alpha, so the soft glow
  the colour layer keeps would otherwise print as a grey box in themed icons.
- Legacy square icon (API < 26 never runs this app, but launchers and stores
  still read mipmap ic_launcher): the whole cup master, frame included,
  cropped to the frame and composited over the edge colour.
- Legacy round icon: the adaptive composite in a circle.
- Splash / in-app mark (drawable-nodpi/ic_splash_mark.webp): the emblem master
  at its native size.

Output is deterministic for a given Pillow version.
"""
import argparse
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LEGACY_DENSITIES = dict(DENSITIES, ldpi=0.75)
CANVAS_DP = 108
SAFE_DP = 66
LEGACY_DP = 48
INSET = 18
FEATHER = 3


def rgb(text):
    text = text.lstrip("#")
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4))


def rounded_mask(size, box, radius, feather):
    # Drawn at 4x and reduced, so the curve is anti-aliased.
    scale = 4
    big = Image.new("L", (size[0] * scale, size[1] * scale), 0)
    ImageDraw.Draw(big).rounded_rectangle(
        [c * scale for c in box], radius=radius * scale, fill=255)
    mask = big.resize(size, Image.LANCZOS)
    return mask.filter(ImageFilter.GaussianBlur(feather)) if feather else mask


def background(size, edge, glow):
    """The radial gradient ic_launcher_background.xml draws, for the legacy round icon."""
    w, h = size
    cx, cy, r = 0.5 * w, 0.46 * h, 0.62 * w
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = min(1.0, ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / r)
            px[x, y] = tuple(round(g + (e - g) * t) for g, e in zip(glow, edge))
    return img


def save(img, path, lossless=True, quality=90):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if lossless:
        img.save(path, "WEBP", lossless=True, quality=100, method=6, exact=True)
    else:
        img.save(path, "WEBP", quality=quality, method=6)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("cup")
    ap.add_argument("emblem")
    ap.add_argument("res")
    ap.add_argument("--edge", required=True)
    ap.add_argument("--glow", required=True)
    ap.add_argument("--frame", required=True, help="frame line L,T,R,B on the cup master")
    ap.add_argument("--radius", type=int, required=True, help="frame corner radius")
    args = ap.parse_args()

    cup = Image.open(args.cup).convert("RGBA")
    emblem = Image.open(args.emblem).convert("RGBA")
    edge, glow = rgb(args.edge), rgb(args.glow)
    left, top, right, bottom = (int(v) for v in args.frame.split(","))

    # Foreground: the cup without its container.
    box = (left + INSET, top + INSET, right - INSET, bottom - INSET)
    mask = rounded_mask(cup.size, box, args.radius - INSET, FEATHER)
    cut = cup.copy()
    cut.putalpha(ImageChops.multiply(cup.getchannel("A"), mask))
    cut = cut.crop(box)
    side = max(cut.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(cut, ((side - cut.width) // 2, (side - cut.height) // 2))

    for name, density in DENSITIES.items():
        canvas_px = round(CANVAS_DP * density)
        art_px = round(SAFE_DP * density)
        art = square.resize((art_px, art_px), Image.LANCZOS)
        fg = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        offset = (canvas_px - art_px) // 2
        fg.alpha_composite(art, (offset, offset))
        save(fg, f"{args.res}/mipmap-{name}/ic_launcher_foreground.webp")

        mono = Image.new("RGBA", fg.size, (255, 255, 255, 0))
        mono.putalpha(fg.getchannel("A").point(
            lambda a: 0 if a <= 120 else 255 if a >= 200 else round((a - 120) * 255 / 80)))
        save(mono, f"{args.res}/mipmap-{name}/ic_launcher_monochrome.webp")

    # Legacy icons.
    frame_crop = cup.crop((left - 8, top - 8, right + 9, bottom + 9))
    for name, density in LEGACY_DENSITIES.items():
        px = round(LEGACY_DP * density)
        flat = Image.new("RGBA", frame_crop.size, edge + (255,))
        flat.alpha_composite(frame_crop)
        save(flat.convert("RGB").resize((px, px), Image.LANCZOS),
             f"{args.res}/mipmap-{name}/ic_launcher.webp")

        big = round(CANVAS_DP * density)
        layered = background((big, big), edge, glow).convert("RGBA")
        art_px = round(SAFE_DP * density)
        o = (big - art_px) // 2
        layered.alpha_composite(square.resize((art_px, art_px), Image.LANCZOS), (o, o))
        # A legacy icon shows the middle 72 of the 108 dp canvas.
        crop = round(18 * density)
        legacy = layered.crop((crop, crop, big - crop, big - crop)).resize((px, px), Image.LANCZOS)
        circle = rounded_mask((px, px), (0, 0, px, px), px // 2, 0)
        legacy.putalpha(circle)
        save(legacy, f"{args.res}/mipmap-{name}/ic_launcher_round.webp")

    # Inside the app: the emblem, native size, lossy for the petal gradients.
    save(emblem, f"{args.res}/drawable-nodpi/ic_splash_mark.webp", lossless=False)


if __name__ == "__main__":
    main()
