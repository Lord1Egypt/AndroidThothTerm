# ThothTerm Security — master branding artwork

Reference sources for the ThothTerm Security runtime assets. Nothing under
`docs/` is an Android source set, so none of these files reaches an APK. They
are plain files in git (no LFS).

The edition has its own approved Garden sheet: ThothTerm's cup and eight-petal
emblem in black graphite and silver with an ice-blue edge glow, "SECURITY
EDITION" below. It is project-owned artwork; no BlackArch, Arch Linux or Arch
Linux ARM logo or artwork is used (`docs/garden/security/DESIGN.md`,
"Naming"). The wordmark appears in no Android resource.

The sheet was first delivered with another project's name as its wordmark
(`4f7e13fe…dcba`, below). That name cannot be a product name, so the wordmark
line was replaced with the neutral one; nothing above it changed, so both
masters and `make_resources.args` are bit-identical to the first extraction
(`SecurityBrandingTest.theSourceArtworkIsTheDeliveredSheetRetitledAndDocumented`
compares every pixel above the wordmark band) and the palette re-derives to the
same values.

| File | Role | SHA-256 |
|---|---|---|
| `thothterm-security-branding-sheet.png` | Approved sheet (1254 × 1254 RGB): cup mark left, eight-petal emblem right, "SECURITY EDITION" below. The delivered sheet with its wordmark line retitled | `0b243e0a309b8bd64a0a1ce64659bdddce4463b21eb41fc38e9ca86cb25f1d83` |
| `thothterm-security-launcher-cup-master.png` | **Launcher / outside-the-app** mark — the cup inside its rounded-square frame. 795 × 795 RGBA | `a0a7406a82eb0b64224bf7e583a1415a099fbbf2a6416877c329c856de4aa320` |
| `thothterm-security-emblem-eight-petal-master.png` | **Inside-the-app** mark — the round eight-petal emblem. 743 × 743 RGBA | `ff6870f5efb389ff119a0173b44e3e3eef315e47e1e9ddf3bf011c9f0e3441e2` |

The sheet was delivered as `logos/ChatGPT Image Sep 26, 2026, 07_39_07 AM.png`
(SHA-256 `4f7e13fe5c7884774e3d2bc7ef0bb98fcd4aca17b80d4c495916f53f0092dcba`). The green alternative and the Kali/Rolling artwork are
not used.

## How the masters were made

    tools/garden/branding/extract_masters.py thothterm-security-branding-sheet.png security .

The sheet uses the Garden layout the script's defaults were measured on: the
cup frame's right edge peaks at x = 663 and the emblem's left petal tip starts
at x = 664/665 on row 550, exactly as on the Debian sheet, so no geometry
override is needed. Deterministic: re-running reproduces both hashes above.

Measured fidelity (master over the estimated backdrop vs the sheet): cup
artwork max 3.04 levels, owned-region mean 2.51; emblem artwork max 3.07, mean
2.38. As for the other editions, dark areas enclosed by the artwork (the cup's
screen, the emblem's centre disc) become transparent: the backdrop shows
through them.

## How the launcher resources were made

    tools/garden/branding/measure_sheet.py thothterm-security-branding-sheet.png \
        thothterm-security-launcher-cup-master.png > make_resources.args
    tools/garden/branding/make_resources.py thothterm-security-launcher-cup-master.png \
        thothterm-security-emblem-eight-petal-master.png garden-security/src/main/res \
        $(cat make_resources.args)

`measure_sheet.py` measures the backdrop edge and glow and the cup's frame line
(on the Trixie sheet and master it reproduces the recorded frame 82,89,711,709
radius 130 exactly). `SecurityBrandingTest` reruns both and requires the same
bytes.

## Use

- **Outside the app:** the cup — launcher, app drawer, shortcuts, round icon,
  adaptive and monochrome layers.
- **Inside the app:** the emblem — Android 12+ splash, first-run setup screen,
  navigation drawer header, About.
- Functional icons (settings, windows, add session, overflow, toolbar controls)
  are not replaced. The `thothfetch` ASCII mark is the shared Garden mark,
  unchanged; only its colours are this edition's.
