#!/usr/bin/env python3
"""Extract the two Garden master marks from an approved distro branding sheet.

A Garden branding sheet is one square RGB image carrying two approved marks on
a dark backdrop -- the launcher cup (left) and the round eight-petal emblem
(right) -- above a title. This script separates them into two square RGBA
masters without redrawing, recolouring or scaling anything:

1. Core pixels of each mark are found by brightness, split between the marks
   by geometry (the emblem is a disc around its measured centre).
2. Core pixels belong wholly to their mark. Glow around them, out to a
   margin, is shared between the marks by a smooth weight on the difference
   of the distances to the two cores, so where the glows meet neither master
   gets a hard cut and together they still add up to the sheet. The title
   band is excluded outright.
3. The backdrop is estimated as a smooth field from pixels far from any mark.
4. Straight alpha is solved per pixel so that the master composited over that
   backdrop reproduces the sheet: the smallest alpha for which the foreground
   colour stays within 0..255, counting only light added to the backdrop and
   ignoring backdrop grain below a small noise floor. Fully-lit artwork comes
   out opaque and unchanged; glow becomes partial alpha; the backdrop,
   including dark areas enclosed by the artwork, becomes transparent.
5. Each mark is centred on a transparent square canvas at native resolution
   with the same padding on both masters.

The script verifies step 4 by re-compositing and reports the largest error.

Usage: extract_masters.py <sheet.png> <distro> <out-dir> [geometry overrides]
"""
import argparse
import hashlib
import json
import sys

import numpy as np
from PIL import Image

# Geometry of the approved Garden sheet layout, measured on the Debian sheet
# (1254x1254). Overridable per sheet.
DEFAULTS = {
    "core_threshold": 150,     # max(R,G,B) above which a pixel is mark artwork
    "emblem_centre": [945, 550],
    "emblem_radius": 300,      # petal tips reach ~290
    "emblem_min_x": 666,       # the cup frame's right edge line peaks at x=663; the petal tip starts at 664
    "art_top": 190,            # nothing of either mark above this row
    "art_bottom": 880,         # the title band starts below this row
    "glow_margin": 80,         # glow fades into the backdrop within ~40-60 px
    "padding": 80,             # transparent padding around each core, both masters
    "backdrop_clearance": 100, # backdrop is sampled this far from any core
    "noise_floor": 6,          # backdrop grain, in levels, treated as backdrop
    "share_softness": 3.0,     # px; how gently glow is shared where the marks meet
}


def sha256(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def box_blur(img, r):
    """Box blur of a 2-D array with radius r (edge-normalised)."""
    k = 2 * r + 1
    pad = np.pad(img, r, mode="constant")
    c = np.cumsum(np.cumsum(pad, axis=0), axis=1)
    c = np.pad(c, ((1, 0), (1, 0)), mode="constant")
    return c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]


def smooth_fill(values, weights, r, passes=3):
    """Normalised convolution: fill a sparse field smoothly from weighted samples."""
    num = values * weights
    den = weights.astype(float)
    for _ in range(passes):
        num = box_blur(num, r)
        den = box_blur(den, r)
    return num / np.maximum(den, 1e-9)


def dilate(mask):
    out = mask.copy()
    out[1:, :] |= mask[:-1, :]
    out[:-1, :] |= mask[1:, :]
    out[:, 1:] |= mask[:, :-1]
    out[:, :-1] |= mask[:, 1:]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("sheet")
    ap.add_argument("distro")
    ap.add_argument("out_dir")
    ap.add_argument("--geometry", help="JSON object overriding DEFAULTS")
    args = ap.parse_args()
    g = dict(DEFAULTS)
    if args.geometry:
        g.update(json.loads(args.geometry))

    sheet = Image.open(args.sheet)
    if sheet.mode != "RGB":
        sys.exit("expected an RGB sheet, got " + sheet.mode)
    P = np.asarray(sheet).astype(np.float64)
    H, W, _ = P.shape
    lum = P.max(axis=2)
    yy, xx = np.mgrid[0:H, 0:W]

    band = (yy >= g["art_top"]) & (yy <= g["art_bottom"])
    bright = (lum > g["core_threshold"]) & band
    cx, cy = g["emblem_centre"]
    in_disc = (xx - cx) ** 2 + (yy - cy) ** 2 <= g["emblem_radius"] ** 2
    emblem_core = bright & in_disc & (xx >= g["emblem_min_x"])
    cup_core = bright & ~emblem_core & (xx < g["emblem_min_x"])

    # Distance (in dilation steps) from each core, out to the glow margin.
    # Where both marks' glow overlaps, the light is shared by a smooth weight on
    # the difference of the two distances instead of a hard boundary, so the
    # two masters together still add up to the sheet.
    far = float(g["glow_margin"] + 1)
    def distance_from(core):
        d = np.full((H, W), far)
        d[core] = 0.0
        reach = core.copy()
        for step in range(1, g["glow_margin"] + 1):
            nxt = dilate(reach) & ~reach
            d[nxt] = step
            reach |= nxt
        return d
    d_cup, d_emb = distance_from(cup_core), distance_from(emblem_core)
    share_cup = 1.0 / (1.0 + np.exp(np.clip((d_cup - d_emb) / g["share_softness"], -60, 60)))
    # Artwork itself is never shared: each core pixel belongs wholly to its mark.
    share_cup[cup_core] = 1.0
    share_cup[emblem_core] = 0.0
    title_cut = yy > g["art_bottom"] + g["glow_margin"] // 2  # never the title
    weight = {1: np.where((d_cup < far) & ~title_cut, share_cup, 0.0),
              2: np.where((d_emb < far) & ~title_cut, 1.0 - share_cup, 0.0)}

    # Backdrop: smooth field from pixels well clear of both marks and the title.
    near = cup_core | emblem_core
    for _ in range(g["backdrop_clearance"] // 4):
        near = dilate(dilate(dilate(dilate(near))))
    title = yy > g["art_bottom"]
    sample = (~near & ~title).astype(float)
    B = np.stack([smooth_fill(P[..., c], sample, 60) for c in range(3)], axis=-1)
    B = np.clip(B, 0, 255)

    # Smallest straight alpha that lets the foreground colour reach the sheet
    # from the backdrop. Only light added to the backdrop counts: the backdrop
    # is grainy while its estimate is smooth, so pixels a little darker than
    # the estimate, and grain within the noise floor, are backdrop, not mark.
    # Dark areas enclosed by the artwork (the cup's screen, the emblem's disc)
    # therefore become transparent, as in the Ubuntu masters' runtime assets.
    t = g["noise_floor"]
    up = np.clip(P - B - t, 0.0, None) / np.maximum(255.0 - B - t, 1e-9)
    alpha = np.clip(up.max(axis=2), 0.0, 1.0)
    F = (P - (1.0 - alpha[..., None]) * B) / np.maximum(alpha[..., None], 1e-9)
    F = np.clip(F, 0, 255)

    report = {"sheet": args.sheet.split("/")[-1], "sheet_sha256": sha256(args.sheet),
              "sheet_size": [W, H], "geometry": g, "masters": {}}
    names = {1: "launcher-cup", 2: "emblem-eight-petal"}
    for lab, core in ((1, cup_core), (2, emblem_core)):
        region = weight[lab] > 0.0
        owned = weight[lab] > 0.99
        ys, xs = np.nonzero(core)
        x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
        side = int(max(x1 - x0 + 1, y1 - y0 + 1) + 2 * g["padding"])
        ox = (x0 + x1 + 1) // 2 - side // 2
        oy = (y0 + y1 + 1) // 2 - side // 2

        rgba = np.zeros((side, side, 4), np.uint8)
        a8 = np.rint(alpha * weight[lab] * 255).astype(np.uint8)
        f8 = np.rint(F).astype(np.uint8)
        sx0, sy0 = max(ox, 0), max(oy, 0)
        sx1, sy1 = min(ox + side, W), min(oy + side, H)
        dx0, dy0 = sx0 - ox, sy0 - oy
        rgba[dy0:dy0 + (sy1 - sy0), dx0:dx0 + (sx1 - sx0), :3] = f8[sy0:sy1, sx0:sx1]
        rgba[dy0:dy0 + (sy1 - sy0), dx0:dx0 + (sx1 - sx0), 3] = a8[sy0:sy1, sx0:sx1]
        rgba[rgba[..., 3] == 0, :3] = 0

        # Verify: master over the backdrop == sheet, inside the mark's region.
        a = rgba[dy0:dy0 + (sy1 - sy0), dx0:dx0 + (sx1 - sx0), 3:4] / 255.0
        f = rgba[dy0:dy0 + (sy1 - sy0), dx0:dx0 + (sx1 - sx0), :3].astype(float)
        recon = a * f + (1 - a) * B[sy0:sy1, sx0:sx1]
        reg = owned[sy0:sy1, sx0:sx1]
        err = np.abs(recon - P[sy0:sy1, sx0:sx1])[reg]
        core_err = np.abs(recon - P[sy0:sy1, sx0:sx1])[core[sy0:sy1, sx0:sx1]]

        name = "thothterm-%s-%s-master.png" % (args.distro, names[lab])
        path = "%s/%s" % (args.out_dir, name)
        Image.fromarray(rgba, "RGBA").save(path, optimize=True)
        report["masters"][name] = {
            "sha256": sha256(path), "size": [int(side), int(side)],
            "sheet_origin": [int(ox), int(oy)],
            "core_bbox": [int(x0), int(y0), int(x1), int(y1)],
            "recomposition_max_abs_error": float(err.max()),
            "recomposition_mean_abs_error": float(err.mean()),
            "core_max_abs_error": float(core_err.max()),
            "canvas_outside_sheet_px": int(side * side - (sx1 - sx0) * (sy1 - sy0)),
        }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
