# ThothTerm Rolling — master branding artwork

Reference sources for the ThothTerm Rolling runtime assets. Nothing under
`docs/` is an Android source set, so none of these files reaches an APK.

The edition has no branding sheet of its own. Its artwork is ThothTerm's own
Garden artwork — the cup and the eight-petal emblem from
`docs/branding/debian/source/` — turned from pink to a cool blue:

    tools/garden/branding/recolor.py docs/branding/debian/source debian \
        docs/branding/arch/source arch $(cat recolor.args)

Every pixel keeps its lightness, saturation and alpha; only the hue turns, by
the amount in `recolor.args` (−122°). Shape, shading and glow are unchanged.
The sheet's title band, which names the other edition, is replaced by its own
backdrop. The Arch Linux logo and any Arch Linux or Arch Linux ARM artwork are
not used (`../TRADEMARK.md`). `ArchBrandingTest` reruns the script and
requires these exact bytes.

| File | Role | SHA-256 |
|---|---|---|
| `thothterm-arch-branding-sheet.png` | The Garden sheet, turned; title band cleared. 1254 × 1254 RGB | `7eea21c4d8c080be15a0fee7a6ca4f8b24815831933c87496510f036e5cc5109` |
| `thothterm-arch-launcher-cup-master.png` | **Launcher / outside-the-app** mark — the cup inside its rounded-square frame. 794 × 794 RGBA | `fc74cb0142fe89909ebec716dca2c807322b646d99236d215e03acd64cb9574c` |
| `thothterm-arch-emblem-eight-petal-master.png` | **Inside-the-app** mark — the round eight-petal emblem. 736 × 736 RGBA | `576984003bef16a569387f43c88b0964e947a3bbdc19e83b997bb98177007107` |

The launcher resources are made from these masters exactly as for the other
editions, with this edition's backdrop colours (the Garden ones, turned):

    tools/garden/branding/make_resources.py thothterm-arch-launcher-cup-master.png \
        thothterm-arch-emblem-eight-petal-master.png garden-arch/src/main/res $(cat make_resources.args)

## Use

- **Outside the app:** the cup — launcher, app drawer, shortcuts, round icon,
  adaptive and monochrome layers.
- **Inside the app:** the emblem — Android 12+ splash, first-run setup screen,
  navigation drawer header, About.
- Functional icons (settings, windows, add session, overflow, toolbar controls)
  are not replaced.
