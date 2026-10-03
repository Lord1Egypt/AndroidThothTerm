#!/usr/bin/env python3
"""Sample a Garden edition's palette from its approved branding sheet.

    tools/garden/branding/palette.py SHEET.png [--hue-shift DEGREES [--muted-rim T]]

Prints role=#RRGGBB lines. Every value is the median of a band of the sheet's
own pixels, or a stated mix of two such medians; nothing is chosen by eye.
With --hue-shift, every value is then turned by the same HLS hue rotation
recolor.py applies to the artwork, for an edition whose artwork is derived
from this sheet's edition that way. --muted-rim T takes muted from T of the
rim glow over the backdrop instead of the glyph cream: in a cool palette the
cream mix is a neutral grey that another edition already draws with.

The sheet, not the masters, is the colour authority: the masters store
straight alpha over the removed backdrop, so their RGB is brighter than what
anyone sees. The art band (y 120-880) holds both marks; the title band below
it holds the edition lettering.
"""
import colorsys
import statistics
import sys

from PIL import Image


def median(pixels):
    return tuple(int(statistics.median(p[i] for p in pixels)) for i in range(3))


def hexed(rgb):
    return '#%02X%02X%02X' % rgb


def mix(a, b, t):
    """t of a over (1 - t) of b, rounded."""
    return tuple(round(t * x + (1 - t) * y) for x, y in zip(a, b))


def rotated(rgb, degrees):
    h, l, s = colorsys.rgb_to_hls(*(c / 255 for c in rgb))
    return tuple(round(c * 255) for c in colorsys.hls_to_rgb((h + degrees / 360) % 1.0, l, s))


def main():
    args = sys.argv[2:]
    shift = float(args[args.index('--hue-shift') + 1]) if '--hue-shift' in args else 0.0
    muted_rim = float(args[args.index('--muted-rim') + 1]) if '--muted-rim' in args else None
    sheet = Image.open(sys.argv[1]).convert('RGB')
    art = [sheet.getpixel((x, y)) for y in range(120, 880, 2) for x in range(40, 1240, 2)]

    def band(test):
        return median([p for p in art if test(*colorsys.rgb_to_hls(*(c / 255 for c in p)))])

    def pink(h):
        return h > 0.9 or h < 0.03

    backdrop = band(lambda h, l, s: l < 0.06)
    maroon = band(lambda h, l, s: 0.06 <= l < 0.15)
    crimson = band(lambda h, l, s: pink(h) and 0.15 <= l < 0.35 and s > 0.4)
    petal_body = band(lambda h, l, s: pink(h) and 0.35 <= l < 0.55 and s > 0.5)
    petal_lit = band(lambda h, l, s: pink(h) and 0.55 <= l < 0.72 and s > 0.5)
    rim = band(lambda h, l, s: pink(h) and 0.72 <= l < 0.90 and s > 0.4)
    cream = band(lambda h, l, s: 0.02 <= h < 0.16 and l >= 0.85)
    near_white = band(lambda h, l, s: l >= 0.94)
    title = median([p for p in (sheet.getpixel((x, y)) for y in range(900, 1010)
                                for x in range(300, 960)) if max(p) > 150])

    roles = [
        ('primary', petal_lit),
        ('secondary', rim),
        ('highlight', title),
        ('foreground', cream),
        ('muted', mix(rim, backdrop, muted_rim) if muted_rim else mix(cream, backdrop, 0.62)),
        ('background', backdrop),
        ('surface', maroon),
        # Android chrome only:
        ('accent_dark', petal_body),
        ('deep', crimson),
        ('surface_light', mix(maroon, crimson, 0.6)),
        ('on_surface', near_white),
    ]
    for role, rgb in roles:
        print('%s=%s' % (role, hexed(rotated(rgb, shift) if shift else rgb)))


if __name__ == '__main__':
    main()
