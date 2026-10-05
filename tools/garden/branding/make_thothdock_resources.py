#!/usr/bin/env python3
"""Build the ThothDock launcher and in-app resources from the approved icon.

    tools/garden/branding/make_thothdock_resources.py ICON.png RES_DIR \
        --edge RRGGBB --glow RRGGBB

The approved ThothDock artwork (ThothDock repository, docs/branding/
thothdock-icon-master.png) is ONE tile on a near-black margin, not Garden's
two-mark sheet, so this sibling of make_resources.py keeps its output layout
and its rule: every pixel written is an icon pixel, cropped, feathered or
scaled down -- nothing is redrawn, recoloured or scaled up.

- Adaptive foreground: the whole icon. Its margin is the backdrop colour, so
  the layer's outer 6% is feathered to transparent and the adaptive
  background (the margin colour) shows through without a seam. The icon is
  scaled so the artwork's furthest solid point (ART_RADIUS native px from the
  centre, measured from the T bar's corners) lies on the 33 dp circle of the
  66 dp safe zone: no launcher mask -- circle, squircle, rounded square --
  crops the symbol.
- Monochrome: the artwork's silhouette, white, alpha from brightness (the flat
  tile is dark and drops out; the T, the bird and the containers stay).
- Legacy square and round icons: as make_resources.py.
- In-app mark (drawable-nodpi/ic_splash_mark.webp): the icon cropped to the
  tile, edges feathered, at 736 px.
Output is deterministic for a given Pillow version.
"""
import argparse
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LEGACY_DENSITIES = dict(DENSITIES, ldpi=0.75)
CANVAS_DP, SAFE_DP, LEGACY_DP = 108, 66, 48
ART_RADIUS = 580          # native px, centre to the T bar's far corners (measured)
CENTER = 627              # icon centre, native px
MARK_CROP, MARK_PX = 1090, 736
SYMBOL_BOX = (180, 205, 1074, 1078)   # native px: the symbol inside the tile's bevel


def rgb(text):
    text = text.lstrip("#")
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4))


def feather_mask(size, fraction):
    """Opaque in the middle, fading to 0 across the outer `fraction` of each side."""
    w, h = size
    m = round(min(w, h) * fraction)
    base = Image.new("L", size, 0)
    ImageDraw.Draw(base).rectangle([m, m, w - m - 1, h - m - 1], fill=255)
    return base.filter(ImageFilter.GaussianBlur(m / 2.2))


def background(size, edge, glow):
    w, h = size
    cx, cy, r = 0.5 * w, 0.46 * h, 0.62 * w
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = min(1.0, ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / r)
            px[x, y] = tuple(round(g + (e - g) * t) for g, e in zip(glow, edge))
    return img


def save(img, path, lossless=True, quality=92):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if lossless:
        img.save(path, "WEBP", lossless=True, quality=100, method=6, exact=True)
    else:
        img.save(path, "WEBP", quality=quality, method=6)


def silhouette(rgba, keep):
    """White, alpha from brightness (smoothstep 45..95), inside the box `keep`
    (the symbol's footprint; the tile's bright bevel outside it is not part of
    the symbol and would print as a blob in themed icons)."""
    lum = ImageChops.lighter(ImageChops.lighter(rgba.getchannel("R"), rgba.getchannel("G")),
                             rgba.getchannel("B"))
    a = lum.point(lambda v: 0 if v <= 45 else 255 if v >= 95 else round((v - 45) * 255 / 50))
    a = ImageChops.multiply(a, rgba.getchannel("A"))
    inner = Image.new("L", rgba.size, 0)
    ImageDraw.Draw(inner).rounded_rectangle(keep, radius=round(rgba.width * 0.03), fill=255)
    a = ImageChops.multiply(a, inner.filter(ImageFilter.GaussianBlur(rgba.width * 0.004)))
    out = Image.new("RGBA", rgba.size, (255, 255, 255, 0))
    out.putalpha(a)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("icon")
    ap.add_argument("res")
    ap.add_argument("--edge", required=True)
    ap.add_argument("--glow", required=True)
    args = ap.parse_args()
    edge, glow = rgb(args.edge), rgb(args.glow)

    icon = Image.open(args.icon).convert("RGB")
    assert icon.size[0] == icon.size[1] == 1254, icon.size
    rgba = icon.convert("RGBA")
    rgba.putalpha(feather_mask(icon.size, 0.06))

    for name, density in DENSITIES.items():
        canvas_px = round(CANVAS_DP * density)
        # ART_RADIUS native px must land on the 33 dp circle.
        side_px = round(icon.size[0] * (SAFE_DP / 2 * density) / ART_RADIUS)
        art = rgba.resize((side_px, side_px), Image.LANCZOS)
        fg = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        off = (canvas_px - side_px) // 2
        fg.alpha_composite(art, (off, off))
        save(fg, f"{args.res}/mipmap-{name}/ic_launcher_foreground.webp")
        k = side_px / icon.size[0]
        keep = tuple(round(off + v * k) for v in SYMBOL_BOX)
        save(silhouette(fg, keep), f"{args.res}/mipmap-{name}/ic_launcher_monochrome.webp")

    half = MARK_CROP // 2
    box = (CENTER - half, CENTER - half, CENTER + half, CENTER + half)
    square = rgba.crop(box)
    flat_src = icon.crop(box)
    for name, density in LEGACY_DENSITIES.items():
        px = round(LEGACY_DP * density)
        save(flat_src.resize((px, px), Image.LANCZOS), f"{args.res}/mipmap-{name}/ic_launcher.webp")
        big = round(CANVAS_DP * density)
        layered = background((big, big), edge, glow).convert("RGBA")
        side_px = round(icon.size[0] * (SAFE_DP / 2 * density) / ART_RADIUS)
        o = (big - side_px) // 2
        layered.alpha_composite(rgba.resize((side_px, side_px), Image.LANCZOS), (o, o))
        crop = round(18 * density)
        legacy = layered.crop((crop, crop, big - crop, big - crop)).resize((px, px), Image.LANCZOS)
        circle = Image.new("L", (px * 4, px * 4), 0)
        ImageDraw.Draw(circle).ellipse([0, 0, px * 4 - 1, px * 4 - 1], fill=255)
        legacy.putalpha(circle.resize((px, px), Image.LANCZOS))
        save(legacy, f"{args.res}/mipmap-{name}/ic_launcher_round.webp")

    mark = square.resize((MARK_PX, MARK_PX), Image.LANCZOS)
    save(mark, f"{args.res}/drawable-nodpi/ic_splash_mark.webp", lossless=False)


if __name__ == "__main__":
    main()
