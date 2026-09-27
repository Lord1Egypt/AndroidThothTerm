# ThothTerm Debian — edition palette

Every colour ThothTerm itself draws in the Debian edition comes from the
approved Debian sheet, `source/thothterm-debian-branding-sheet.png`
(sha256 `e89e5d80…5583`, byte-for-byte as delivered). Nothing is chosen by eye
and the artwork is not recoloured: the surrounding UI is matched to it.

## How the values were taken

`tools/garden/branding/palette.py` samples the sheet itself, not the masters:
the masters store straight alpha over the removed backdrop, so their RGB is
brighter than what anyone sees. Each value is the median of a band of the
sheet's pixels (art band y 120–880, title band below it) or a stated mix of two
medians. `DebianPaletteTest` reruns the script and requires the same values.

| Role | Colour | From the sheet | Terminal (xterm-256) | Contrast on background |
|---|---|---|---|---|
| primary | `#F44F7A` | lit petal face (pink, lightness .55–.72) | 204 `#FF5F87` | 7.0 |
| secondary | `#FD9AAA` | rim glow around the petals (lightness .72–.90) | 217 `#FFAFAF` | 11.6 |
| highlight | `#F87497` | the sheet's own "DEBIAN" lettering | 211 `#FF87AF` | 9.0 |
| foreground | `#FEF4ED` | cream of the emblem's `>_` glyph | 255 `#EEEEEE` | 17.5 |
| muted | `#A49896` | 62 % glyph cream over 38 % backdrop | 247 `#9E9E9E` | 7.6 |
| background | `#110208` | the sheet's backdrop (lightness < .06) | — | — |
| surface | `#240611` | the backdrop's lifted maroon (.06–.15) | — | — |
| accent dark | `#D11749` | petal body in shade (.35–.55) | — | — |
| deep | `#690322` | deep crimson (.15–.35) | — | — |
| surface light | `#400518` | 60 % surface over 40 % deep | — | — |
| on surface | `#FEF8F5` | the sheet's near-white | — | — |

The phone's terminal renders 256-colour SGR only, so text roles are drawn with
the nearest xterm-256 colour (CIE Lab, indices 16–255). xterm.js uses the same
table, so the browser shows the same colours. The cream foreground lands on the
neutral 255: the cube has no warm off-white closer to it.

## Where each role is used

| Surface | Roles |
|---|---|
| Welcome banner (`thothfetch`) | mark petals **primary**, mark centre and `>_` **secondary**, "ThothTerm Debian" **highlight**, the `PRETTY_NAME` line and values **foreground**, labels **muted** |
| Managed prompt `thoth@thothterm:~$` | user **primary**, host **secondary**, path **foreground**; punctuation default |
| Phone terminal default scheme | text **foreground**, background **background**, cursor **primary** |
| App chrome (`garden-debian/src/main/res/values/colors.xml`) | window/status bar **background**, app bar and drawer header **surface**, switches/progress/selected keys **primary**, extra-key fill **surface light**, muted text **muted** |
| LAN page chrome (`/edition.css`) | page **background**, header **surface**, text **foreground**, title and connected badge **primary**/**secondary**; browser terminal as on the phone |

Warnings and errors keep their functional colours, and programs' own ANSI
output (apt, ls, editors) is never changed: the 16/256-colour tables are
xterm's standard ones on both the phone and the browser.

## The mechanism

`garden-common` holds no colour of its own beyond neutral greys. The edition
supplies `assets/garden/palette.properties` (terminal, banner, prompt, web) and
`res/values/colors.xml` (Android chrome). The app writes the text roles to
`/etc/thothterm/palette` as `role=SGR` lines, which `thothfetch` and the managed
prompt read as data; `NO_COLOR`, or no palette, gives plain text. A new
edition adds those two files; no terminal code changes. With ThothTerm
Ubuntu's palette the same scripts draw exactly what term-ubuntu draws today
(`GardenBrandingScriptsTest`).
