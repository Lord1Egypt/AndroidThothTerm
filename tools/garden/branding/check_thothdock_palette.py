#!/usr/bin/env python3
"""Check that the ThothDock QA palette.properties agrees with the design tokens.

    check_thothdock_palette.py garden-debian/src/thothdock

The tokens (res/values/thothdock_tokens.xml) are the single source of the
hex values for Android; palette.properties is a different file format that the
terminal reads. Where a role is the token itself it must be equal, and every
text role must reach 4.5:1 on the terminal background.
"""
import re
import sys

root = sys.argv[1]
tokens = dict(re.findall(r'<color name="(\w+)">(#[0-9A-Fa-f]{6})</color>',
                         open(f"{root}/res/values/thothdock_tokens.xml").read()))
pal = dict(l.strip().split("=", 1) for l in open(f"{root}/assets/garden/palette.properties")
           if "=" in l and not l.startswith("#"))
same = {"primary": "thothdock_primary", "highlight": "thothdock_primary_highlight",
        "muted": "thothdock_terminal_dim", "foreground": "thothdock_terminal_text",
        "background": "thothdock_terminal_background", "surface": "thothdock_surface"}
bad = [f"{r}: {pal[r]} != {tokens[t]}" for r, t in same.items() if pal[r].lower() != tokens[t].lower()]


def lum(h):
    c = [int(h[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    c = [v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4 for v in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def contrast(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


for role in ("primary", "secondary", "highlight", "foreground", "muted", "promptUser", "promptHost", "promptPath"):
    c = contrast(pal[role], pal["background"])
    print(f"{role:11s} {pal[role]}  {c:5.1f}:1")
    if c < 4.5:
        bad.append(f"{role} contrast {c:.1f} < 4.5")
if bad:
    print("FAIL:", *bad, sep="\n  ")
    sys.exit(1)
print("palette and tokens agree")
