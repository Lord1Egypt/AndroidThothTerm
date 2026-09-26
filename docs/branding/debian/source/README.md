# ThothTerm Debian — master branding artwork

Reference sources for the ThothTerm Debian runtime assets. Nothing under
`docs/` is an Android source set, so none of these files reaches an APK. They
are plain files in git (no LFS).

| File | Role | SHA-256 |
|---|---|---|
| `thothterm-debian-branding-sheet.png` | Approved sheet, **byte-for-byte as delivered** (1254 × 1254 RGB): cup mark left, eight-petal emblem right, "DEBIAN EDITION" title below | `e89e5d80f08b83cc3ed18d07b45402e4726b0999aeda9d29622d050ac3ed5583` |
| `thothterm-debian-launcher-cup-master.png` | **Launcher / outside-the-app** mark — the cup inside its rounded-square frame. 794 × 794 RGBA | `7594c8a63d37e7bcbabcd69be5941596825007106a935df7e49df3ce88532d5f` |
| `thothterm-debian-emblem-eight-petal-master.png` | **Inside-the-app** mark — the round eight-petal emblem. 736 × 736 RGBA | `cb1e5d7c3bd48a197ffee92942c4aab157878fbc0b892f10078cc988aa6abdf5` |

The sheet was delivered as `ChatGPT Image Sep 26, 2026, 04_02_10 AM-5.png`.

## How the masters were made

    tools/garden/branding/extract_masters.py thothterm-debian-branding-sheet.png debian .

The script is deterministic — re-running it reproduces both hashes above. It
redraws, recolours and rescales nothing:

- **Separation.** The marks sit close together: the cup frame's right edge is a
  bright line peaking at x = 663 and the emblem's left petal tip starts at
  x = 664 (row 550). Pixels brighter than 150 are artwork; artwork inside the
  emblem's disc (centre 945, 550, radius 300) and at x ≥ 666 is the emblem, the
  rest in the art band is the cup. Artwork pixels belong wholly to their mark.
  Glow around them (out to 80 px) is shared between the two masters by a smooth
  weight on the distance to each mark, so neither master has a hard cut where
  the glows meet. The title band (below y = 880) is excluded outright.
- **Backdrop removal.** The backdrop is a dark maroon gradient, not black. It is
  estimated as a smooth field from pixels at least 100 px from any mark, and
  straight alpha is solved per pixel so that the master composited over that
  backdrop reproduces the sheet. Only light added to the backdrop counts; grain
  within 6 levels of it is backdrop. Consequence, deliberately the same as the
  Ubuntu runtime assets: dark areas enclosed by the artwork (the cup's screen,
  the emblem's centre disc) are transparent.
- **Canvas.** Each mark's artwork is centred on a transparent square with the
  same 80 px padding on both masters, at the sheet's native resolution. The
  sheet cuts the glow at its left and right edges; that part of the canvas
  (cup 38,112 px, emblem 47,840 px) is transparent.

Measured fidelity (master over the estimated backdrop vs the sheet):

| Master | Artwork (max abs error) | Owned region (mean abs error) |
|---|---|---|
| cup | 3.62 levels | 1.54 levels |
| emblem | 3.20 levels | 1.40 levels |

The larger local differences (up to ~24 levels) are the enclosed dark areas
that became transparent, as described above.

## Use

- **Outside the app:** the cup — launcher, app drawer, shortcuts, round icon,
  adaptive and monochrome layers.
- **Inside the app:** the emblem — Android 12+ splash, first-run setup screen,
  navigation drawer header, About.
- Functional icons (settings, windows, add session, overflow, toolbar controls)
  are not replaced.
