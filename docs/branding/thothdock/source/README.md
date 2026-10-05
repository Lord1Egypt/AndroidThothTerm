# ThothDock — master branding artwork (QA integration)

| File | Role | SHA-256 |
|---|---|---|
| `thothdock-icon-master.png` | Approved ThothDock icon, 1254 × 1254 RGB, byte for byte as delivered by the owner. Canonical copy lives in the ThothDock repository: `docs/branding/thothdock-icon-master.png` | `159f499f34c3403504a1b763294ca794b6e486a6380b8a0e0b3aa2f08555a4f2` |

Everything under `garden-debian/src/thothdock/res/` (launcher layers at every
density, round and legacy icons, `ic_splash_mark.webp`) is generated from this
file by

    tools/garden/branding/make_thothdock_resources.py \
        docs/branding/thothdock/source/thothdock-icon-master.png \
        garden-debian/src/thothdock/res --edge 040508 --glow 06090d

`--edge` is the median of the icon's four 24 px corners (4,5,8); `--glow` is a
step lighter for the legacy round icon's gradient. The script redraws and
recolours nothing; it is deterministic for Pillow 12.2.0.

The design tokens and terminal palette are documented in the ThothDock
repository, `docs/branding/VISUAL_IDENTITY.md`. Nothing here is part of any
production edition: the overlay is enabled only by
`-PthothtermThothDockSource` on a QA application id.
