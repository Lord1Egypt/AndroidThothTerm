#!/usr/bin/env python3
"""Derive a Garden edition's artwork from another edition's by rotating its hue.

    tools/garden/branding/recolor.py SRC_DIR SRC_EDITION DST_DIR DST_EDITION \
        --hue-shift DEGREES [--title-top Y]

For an edition that has no delivered branding sheet of its own. It reads
SRC_DIR/thothterm-SRC_EDITION-{branding-sheet,launcher-cup-master,
emblem-eight-petal-master}.png and writes the same three files for
DST_EDITION. Every pixel keeps its lightness, saturation and alpha; only the
hue turns by --hue-shift degrees, so the artwork, its shading and its glow are
ThothTerm's own, unchanged in shape. The sheet's title band (y >= --title-top)
names the source edition, so it is replaced by the sheet's own backdrop
colour, each column continuing the median of its 20 pixels directly above.

Deterministic: plain arithmetic on 8-bit channels, PNG written without
metadata.
"""
import argparse
import os

import numpy as np
from PIL import Image

KINDS = ("branding-sheet", "launcher-cup-master", "emblem-eight-petal-master")


def rotate_hue(rgb, shift):
    """HLS hue rotation of an (N, 3) float array in [0, 1]; L and S are kept."""
    r, g, b = rgb[:, 0], rgb[:, 1], rgb[:, 2]
    mx = rgb.max(axis=1)
    mn = rgb.min(axis=1)
    light = (mx + mn) / 2
    delta = mx - mn
    grey = delta == 0
    safe = np.where(grey, 1, delta)
    sat = np.where(grey, 0, np.where(light <= 0.5, delta / np.where(grey, 1, mx + mn),
                                      delta / np.where(grey, 1, 2 - mx - mn)))
    rc, gc, bc = (mx - r) / safe, (mx - g) / safe, (mx - b) / safe
    hue = np.where(r == mx, bc - gc, np.where(g == mx, 2 + rc - bc, 4 + gc - rc))
    hue = (hue / 6 + shift) % 1.0

    m2 = np.where(light <= 0.5, light * (1 + sat), light + sat - light * sat)
    m1 = 2 * light - m2

    def channel(h):
        h = h % 1.0
        return np.where(h < 1 / 6, m1 + (m2 - m1) * h * 6,
                        np.where(h < 0.5, m2,
                                 np.where(h < 2 / 3, m1 + (m2 - m1) * (2 / 3 - h) * 6, m1)))

    out = np.stack([channel(hue + 1 / 3), channel(hue), channel(hue - 1 / 3)], axis=1)
    return np.where(grey[:, None], rgb, out)


def recolor(image, shift):
    mode = image.mode
    arr = np.asarray(image.convert("RGBA"), dtype=np.float64) / 255.0
    flat = arr[:, :, :3].reshape(-1, 3)
    arr[:, :, :3] = rotate_hue(flat, shift).reshape(arr.shape[0], arr.shape[1], 3)
    out = Image.fromarray(np.rint(arr * 255).clip(0, 255).astype(np.uint8), "RGBA")
    return out if mode == "RGBA" else out.convert(mode)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("src_dir")
    ap.add_argument("src_edition")
    ap.add_argument("dst_dir")
    ap.add_argument("dst_edition")
    ap.add_argument("--hue-shift", type=float, required=True, help="degrees")
    ap.add_argument("--title-top", type=int, default=880)
    args = ap.parse_args()
    shift = args.hue_shift / 360.0
    os.makedirs(args.dst_dir, exist_ok=True)
    for kind in KINDS:
        src = Image.open(os.path.join(args.src_dir, f"thothterm-{args.src_edition}-{kind}.png"))
        out = recolor(src, shift)
        if kind == "branding-sheet":
            rgb = np.asarray(out.convert("RGB")).copy()
            top = args.title_top
            backdrop = np.median(rgb[top - 20:top], axis=0).astype(np.uint8)
            rgb[top:] = backdrop[None, :, :]
            out = Image.fromarray(rgb, "RGB")
        out.save(os.path.join(args.dst_dir, f"thothterm-{args.dst_edition}-{kind}.png"),
                 "PNG", optimize=False)


if __name__ == "__main__":
    main()
