#!/usr/bin/env python3
"""Sample a metallic (silver / steel / graphite) Garden edition palette from its sheet.

    tools/garden/branding/palette_metallic.py SHEET.png

For a sheet whose artwork is neutral metal over near-black with only a faint cold
tint (ThothTerm BlackArch), where palette.py's hue bands find nothing. Prints
role=#RRGGBB lines in palette.py's order. Every value is the median of a band of
the sheet's own pixels, or a stated mix of two such medians; nothing is chosen by
eye. The art band (y 120-880) holds both marks; the title band below it holds the
edition lettering.

Bands, by HLS lightness, over "metal" pixels (saturation < 0.25, so the faint
ice-blue glow does not pull the greys towards blue):

    graphite  0.40-0.55      steel   0.55-0.70      bright  0.85-0.94
    white     0.94-1.00      deep    0.15-0.30
and, over all pixels: backdrop l < 0.03, surface 0.03-0.08, lifted 0.08-0.15;
title = median of the bright (max channel > 150) pixels of the "BLACK" lettering
(x 330-639, y 960-1079), which is silver, not the blue "ARCH".

Roles (the terminal draws text roles with the nearest xterm-256 colour):

    primary    bright             bright silver: mark petals, prompt user, cursor
    secondary  50 % steel over graphite   gunmetal: mark centre, prompt host
    highlight  title              light metallic grey: the edition name
    foreground white              near-white: the distro line, values, prompt path
    muted      10 % steel over graphite   graphite: labels
    background backdrop; surface surface
    accent_dark graphite (the light theme's accent: 4.3:1 on the near-white, where
                steel would be 2.6:1, under the 3:1 a control needs)
    deep deep; surface_light lifted; on_surface white

Why the two mixes: the steel band alone draws as xterm 247, ThothTerm Trixie's
muted, so secondary sits halfway between the steel and graphite bands (xterm 245);
graphite alone is 4.37:1 on the surface, under the 4.5 the chrome needs, and 10 %
steel is the smallest 5 % step that clears 4.5 on both background and surface.
docs/branding/blackarch/PALETTE.md records the criteria; BlackArchPaletteTest reruns
this script and requires the same values.
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


def main():
    sheet = Image.open(sys.argv[1]).convert('RGB')
    art = [sheet.getpixel((x, y)) for y in range(120, 880, 2) for x in range(40, 1240, 2)]
    hls = {p: colorsys.rgb_to_hls(*(c / 255 for c in p)) for p in set(art)}

    def band(lo, hi, metal=True):
        return median([p for p in art if lo <= hls[p][1] < hi and (not metal or hls[p][2] < 0.25)])

    backdrop = band(0.0, 0.03, metal=False)
    surface = band(0.03, 0.08, metal=False)
    lifted = band(0.08, 0.15, metal=False)
    deep = band(0.15, 0.30)
    graphite = band(0.40, 0.55)
    steel = band(0.55, 0.70)
    bright = band(0.85, 0.94)
    white = band(0.94, 1.01)
    title = median([p for p in (sheet.getpixel((x, y)) for y in range(960, 1080)
                                for x in range(330, 640)) if max(p) > 150])

    roles = [
        ('primary', bright),
        ('secondary', mix(steel, graphite, 0.5)),
        ('highlight', title),
        ('foreground', white),
        ('muted', mix(steel, graphite, 0.10)),
        ('background', backdrop),
        ('surface', surface),
        # Android chrome only:
        ('accent_dark', graphite),
        ('deep', deep),
        ('surface_light', lifted),
        ('on_surface', white),
    ]
    for role, rgb in roles:
        print('%s=%s' % (role, hexed(rgb)))


if __name__ == '__main__':
    main()
