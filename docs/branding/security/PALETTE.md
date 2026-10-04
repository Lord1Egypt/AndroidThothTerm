# ThothTerm Security — edition palette

ThothTerm Security's identity is metal on black: bright silver, steel, gunmetal
and graphite over a near-black backdrop, with only the sheet's own faint cold
tint. ThothTerm Rolling is blue and deep blue; Security is silver, steel grey and
graphite.

The colours are derived, not picked. `tools/garden/branding/palette_metallic.py`
samples them from the edition's own sheet:

    tools/garden/branding/palette_metallic.py \
        docs/branding/security/source/thothterm-security-branding-sheet.png

`palette.py` (Trixie, Rolling) finds its roles in hue bands of a coloured sheet;
this sheet is neutral metal, so the metallic script takes lightness bands of its
low-saturation (< 0.25) pixels instead. `SecurityPaletteTest` reruns it and
requires the same values.

## Criteria

The role recipes (two of them stated mixes) were fixed by these criteria, as
ThothTerm Rolling's rotation was:

1. the five text roles land on five different xterm-256 colours;
2. none of them is one ThothTerm Ubuntu (214, 44, 252, 246, 39), Trixie
   (204, 217, 211, 247) or Rolling (69, 111, 68, 255, 103) draws with;
3. every text role reaches 4.5:1 on the background as drawn, and muted reaches
   4.5:1 on the surface (app chrome);
4. they form a metallic ramp: foreground > primary > highlight > secondary >
   muted in lightness.

The plain steel band draws as xterm 247 (Trixie's muted, criterion 2), so
**secondary** is 50 % steel over graphite (xterm 245). The plain graphite band is
4.37:1 on the surface (criterion 3), so **muted** is the smallest 5 % step of steel
over graphite that passes: 10 % (xterm 243). **accent_dark** is graphite because the
light theme draws its accent on the near-white background, where steel would be
2.6:1 and graphite is 4.3:1 (a control needs 3:1).

| Role | Colour | Derived from | Terminal (xterm-256) | Contrast on background |
|---|---|---|---|---|
| primary | `#DFE4E8` | bright metal, lightness 0.85–0.94 | 254 `#E4E4E4` | 16.2 |
| secondary | `#7E8C98` | 50 % steel (0.55–0.70) over graphite (0.40–0.55) | 245 `#8A8A8A` | 6.0 |
| highlight | `#C3CAD2` | the silver "SECU" title lettering | 251 `#C6C6C6` | 12.0 |
| foreground | `#FBFBFC` | brightest metal, lightness ≥ 0.94 | 231 `#FFFFFF` | 20.5 |
| muted | `#6D7D89` | 10 % steel over graphite | 243 `#767676` | 4.5 |
| background | `#020406` | the sheet's backdrop, lightness < 0.03 | — | — |
| surface | `#080C0F` | lightness 0.03–0.08 | — | — |
| surface light | `#151C22` | lightness 0.08–0.15 | — | — |
| accent dark | `#697985` | graphite | — | — |
| deep | `#2F363C` | dark metal, lightness 0.15–0.30 | — | — |
| on surface | `#FBFBFC` | brightest metal | — | — |

The hex values keep the sheet's faint cold tint (blue channel a few levels
higher); the terminal draws every text role on xterm's neutral grey ramp or pure
white, so the banner and prompt read as metal, not blue.

## Where each role is used

The same surfaces as every Garden edition (`docs/branding/debian/PALETTE.md`
describes the mechanism; nothing here is edition-specific code):

| Surface | Roles |
|---|---|
| Welcome banner (`thothfetch`, the shared ASCII mark unchanged) | mark petals **primary** (silver), mark centre and `>_` **secondary** (gunmetal), "ThothTerm Security" **highlight**, the `PRETTY_NAME` line and values **foreground**, labels **muted** |
| Managed prompt `thoth@thothterm:~$` | user **primary** (silver), host **secondary** (gunmetal), path **foreground** (near-white) |
| Phone terminal default scheme | text **foreground**, background **background**, cursor **primary** |
| App chrome (`garden-security/src/main/res/values/colors.xml`) | window/status bar **background**, app bar and drawer header **surface**, switches/progress/selected keys **primary**, extra-key fill **surface light**, muted text **muted**; light theme accent **accent dark** |
| LAN page chrome (`/edition.css`) | page **background**, header **surface**, text **foreground**, title and connected badge **primary**/**secondary** |

The palette reaches the guest the same way as every edition's: `RootfsManager`
loads `assets/garden/palette.properties` and writes `/etc/thothterm/palette` as a
managed file on every session start (`prepareSession`), so an installed system
gets it with the app; the rootfs archive is not changed. Programs' own ANSI output
(pacman, ls, editors) is never changed. ThothTerm Ubuntu's, Trixie's and Rolling's
appearance is untouched: this edition only adds its own `palette.properties` and
`colors.xml`. `NO_COLOR` still turns the banner and prompt colours off.
