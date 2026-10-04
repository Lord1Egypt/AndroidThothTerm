#!/usr/bin/env python3
"""Build an edition's 512 x 512 store icon (Fastlane images/icon.png).

    tools/garden/branding/make_store_icon.py CUP_MASTER OUT.png \
        --edge RRGGBB [--frame L,T,R,B]

The same picture as the legacy launcher icon make_resources.py writes: the
cup master, its rounded frame included, cropped to the frame (8 px margin)
and composited over the edge colour. Without --frame the frame is the
bounding box of the master's visible pixels. Only crops and scales down;
output is deterministic for a given Pillow version.
"""
import argparse

from PIL import Image

SIZE = 512


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("cup")
    ap.add_argument("out")
    ap.add_argument("--edge", required=True)
    ap.add_argument("--frame", help="frame line L,T,R,B on the cup master")
    args = ap.parse_args()

    cup = Image.open(args.cup).convert("RGBA")
    edge = tuple(int(args.edge[i:i + 2], 16) for i in (0, 2, 4))
    if args.frame:
        left, top, right, bottom = (int(v) for v in args.frame.split(","))
        box = (left - 8, top - 8, right + 9, bottom + 9)
    else:
        lum = cup.convert("L").point(lambda v: 255 if v > 24 else 0)
        box = lum.getbbox()
    crop = cup.crop(box)
    side = max(crop.size)
    flat = Image.new("RGBA", (side, side), edge + (255,))
    flat.alpha_composite(crop, ((side - crop.width) // 2, (side - crop.height) // 2))
    if side < SIZE:
        raise SystemExit("master too small for a %d px icon without upscaling" % SIZE)
    flat.convert("RGB").resize((SIZE, SIZE), Image.LANCZOS).save(args.out, optimize=True)


if __name__ == "__main__":
    main()
