# ThothTerm Rolling — edition palette

ThothTerm Rolling's identity is a cool blue: the Garden artwork, turned. Its
colours are derived, not picked. The Garden sheet's own values are sampled by
`tools/garden/branding/palette.py` from
`docs/branding/debian/source/thothterm-debian-branding-sheet.png`, exactly as
ThothTerm Trixie's are, and turned by the same −122° hue rotation that made
this edition's artwork (`source/recolor.args`):

    tools/garden/branding/palette.py docs/branding/debian/source/thothterm-debian-branding-sheet.png \
        $(cat docs/branding/arch/source/palette.args)

`ArchPaletteTest` reruns it and requires the same values.

## Why −122°, and the one different recipe

The phone's terminal renders 256-colour SGR only, so the text roles are drawn
with the nearest xterm-256 colour (CIE Lab), and the xterm cube has few blues.
The rotation was chosen by stated criteria over −150°…−106°, in 2° steps:
the three accents (primary, secondary, highlight) must land on three different
xterm colours, none of them used by ThothTerm Ubuntu (214, 44, 252, 246, 39) or
ThothTerm Trixie (204, 217, 211, 247); every one must reach 4.5:1 on the
background; and the petals must stay at least 15° from Arch Linux's brand blue
(`#1793D1`, hue 200°), so that nothing suggests an affiliation
(`TRADEMARK.md`). −122° and −120° qualify; −122° keeps the brightest secondary.

**muted** is the one role with a different recipe. Trixie's (62 % glyph cream
over the backdrop) comes out a neutral grey in a cool palette, and lands on
xterm 246 — ThothTerm Ubuntu's muted. This edition takes 70 % of the rim glow
over the backdrop instead (`--muted-rim 0.70`): a slate blue, xterm 103.

| Role | Colour | Derived from | Terminal (xterm-256) | Contrast on background |
|---|---|---|---|---|
| primary | `#4F7FF4` | lit petal face, turned | 69 `#5F87FF` | 6.1 |
| secondary | `#9AADFD` | rim glow around the petals, turned | 111 `#87AFFF` | 9.2 |
| highlight | `#749BF8` | the sheet's title lettering, turned | 68 `#5F87D7` | 5.7 |
| foreground | `#F3EDFE` | the emblem's `>_` glyph, turned | 255 `#EEEEEE` | 17.3 |
| muted | `#6C7BB6` | 70 % rim glow over 30 % backdrop | 103 `#8787AF` | 5.8 |
| background | `#020811` | the sheet's backdrop, turned | — | — |
| surface | `#061224` | the backdrop's lifted tone, turned | — | — |
| accent dark | `#174FD1` | petal body in shade, turned | — | — |
| deep | `#032569` | deep tone, turned | — | — |
| surface light | `#051A40` | 60 % surface over 40 % deep, turned | — | — |
| on surface | `#F8F5FE` | the sheet's near-white, turned | — | — |

The foreground lands on the neutral 255, as Trixie's does: the cube has no
tinted off-white closer to it.

## Where each role is used

The same surfaces as every Garden edition (`docs/branding/debian/PALETTE.md`
describes the mechanism):

| Surface | Roles |
|---|---|
| Welcome banner (`thothfetch`) | mark petals **primary**, mark centre and `>_` **secondary**, "ThothTerm Rolling" **highlight**, the `PRETTY_NAME` line ("Arch Linux ARM") and values **foreground**, labels **muted** |
| Managed prompt `thoth@thothterm:~$` | user **primary**, host **secondary**, path **foreground** |
| Phone terminal default scheme | text **foreground**, background **background**, cursor **primary** |
| App chrome (`garden-arch/src/main/res/values/colors.xml`) | window/status bar **background**, app bar and drawer header **surface**, switches/progress/selected keys **primary**, extra-key fill **surface light**, muted text **muted** |
| LAN page chrome (`/edition.css`) | page **background**, header **surface**, text **foreground**, title and connected badge **primary**/**secondary** |

Programs' own ANSI output (pacman, ls, editors) is never changed. ThothTerm
Ubuntu's and Trixie's appearance is untouched: this edition only adds its own
`palette.properties` and `colors.xml`.
