#!/usr/bin/env python3
"""Measure the make_resources.py arguments of a Garden edition from its own artwork.

    tools/garden/branding/measure_sheet.py SHEET.png CUP_MASTER.png

Prints the arguments make_resources.py takes, each measured, nothing chosen by eye:

- --edge: the median of the sheet's four 24 x 24 px corners (the backdrop at the
  icon's edge);
- --glow: the 95th percentile, per channel, of the dark backdrop pixels
  (max channel < 90) in the bands above and below the art (rows 150-199 and
  870-929, columns 250-999): the lit backdrop around the marks;
- --frame L,T,R,B: on the cup master, the column (row) of greatest mean
  premultiplied brightness in the outer quarter on each side, over the middle
  240 rows (columns): the frame line;
- --radius: from the top-left corner along the diagonal, the distance k to the
  frame line's brightest point, as the radius r with k = r (1 - 1/sqrt 2).

On the ThothTerm Trixie sheet and master this reproduces the recorded
--frame 82,89,711,709 --radius 130 exactly, and the recorded edge/glow
080104/290A1C within 5 levels (070004/270713).
"""
import sys

import numpy as np
from PIL import Image


def main():
    sheet = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(int)
    corners = [sheet[:24, :24], sheet[:24, -24:], sheet[-24:, :24], sheet[-24:, -24:]]
    edge = np.median(np.concatenate([c.reshape(-1, 3) for c in corners]), axis=0)
    band = np.concatenate([sheet[150:200, 250:1000].reshape(-1, 3), sheet[870:930, 250:1000].reshape(-1, 3)])
    glow = np.percentile(band[band.max(axis=1) < 90], 95, axis=0)

    cup = np.asarray(Image.open(sys.argv[2]).convert("RGBA")).astype(float)
    lit = cup[..., :3].max(axis=2) * cup[..., 3] / 255
    h, w = lit.shape
    mid = slice(h // 2 - 120, h // 2 + 120)
    left = int(np.argmax(lit[mid, :w // 4].mean(axis=0)))
    right = 3 * w // 4 + int(np.argmax(lit[mid, 3 * w // 4:].mean(axis=0)))
    top = int(np.argmax(lit[:h // 4, mid].mean(axis=1)))
    bottom = 3 * h // 4 + int(np.argmax(lit[3 * h // 4:, mid].mean(axis=1)))
    diagonal = [lit[top + k, left + k] for k in range(200)]
    k = int(np.argmax(diagonal[5:])) + 5
    radius = round(k / (1 - 1 / np.sqrt(2)))
    print("--edge %02X%02X%02X --glow %02X%02X%02X --frame %d,%d,%d,%d --radius %d" % (
        *(int(v) for v in edge), *(int(v) for v in glow), left, top, right, bottom, radius))


if __name__ == "__main__":
    main()
