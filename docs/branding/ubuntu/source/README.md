# ThothTerm Ubuntu — master branding artwork

The two PNGs beside this file are the **approved master artwork** for the
ThothTerm Ubuntu edition. They are reference sources for regenerating the
runtime assets, and are deliberately **not packaged**: nothing under `docs/`
is an Android source set, so neither file reaches the APK. They are stored
as plain files in git — no Git LFS — and are byte-for-byte as delivered.

These masters belong to the **Ubuntu** edition only. The regular ThothTerm
Terminal edition has its own branding, which is unrelated and untouched.

## The two masters

| File | Produces | Runtime resource |
|---|---|---|
| `thothterm-ubuntu-launcher-cup-master-1254.png` | **Launcher icon** — the terminal/cup mark | `mipmap-*/ic_launcher*`, `mipmap-anydpi-v26/ic_launcher*.xml` |
| `thothterm-ubuntu-splash-eight-petal-master-1254.png` | **In-app identity mark** — the round eight-petal emblem | `drawable-nodpi/ic_splash_mark.webp`, wrapped by `drawable/ic_splash_emblem.xml` for the splash |

Both are **1254 × 1254** px, RGB, on a pure black backdrop.

    4b57c1147c7ac17e0684d4c20d17f8ba19b75ffe22823d29459e310d2e85818b  thothterm-ubuntu-launcher-cup-master-1254.png
    155d64adf113868d8a7c4d3eb0516e77201a6504fc0b3b0cb8df6e901a4f19f3  thothterm-ubuntu-splash-eight-petal-master-1254.png

## Separating the artwork from its baked container

Both masters are finished square icons with a rounded-square container already
drawn in. That container must **never** reach an adaptive foreground, so it is
removed first. The two need different methods, because the containers differ.

**Launcher master — geometric, inset `114` px.** Its container is a bright
orange rounded-rect outline that is as bright as the petals, so no luminance
threshold can tell them apart. The boundary is positional instead: measured down
the centre column, the rim and its inner glow occupy y 38–113 and the artwork
starts abruptly at y = 115. Keeping only what lies inside a rounded rect inset
by 114 px (corner radius 150, edge softened ~1.5 px) drops the container exactly.

**Splash master — chroma.** Its container is neutral grey while the emblem is
orange, so it is separated by colour rather than position: keep
`R − B > 40`, grow the mask, flood-fill the enclosed interior, and use the
result as the keep-mask.

In both cases alpha is `max(R, G, B)` — straight alpha over the black backdrop.
Do **not** force the enclosed dark areas (the cup's screen, the emblem's centre
disc) opaque; a crude hole-fill leaves visible seams, and both layers sit on the
app's own dark background, where transparency reads exactly as intended.

## Runtime geometry — do not drift

**Adaptive icon.** 108 dp canvas, artwork's longest side scaled to the
**66 dp safe zone**, centred. Foreground and background are separate layers and
the foreground carries no mask, so circle, squircle and every other launcher
mask crop padding rather than artwork. Verify with `art / canvas == 0.611` at
every density (66 ÷ 108); at mdpi the artwork measures exactly 66 × 66 on 108.

**Splash.** Android 12+ draws `windowSplashScreenAnimatedIcon` into a 288 dp box
and expects the art to fit a 192 dp circle. The mark is therefore a **square**
bitmap wrapped in `drawable/ic_splash_emblem.xml`:

    <inset android:inset{Left,Top,Right,Bottom}="16.667%" />

192 ÷ 288 leaves one sixth of padding a side. A square bitmap scaled into a
square box cannot distort, on any screen shape or orientation. `InsetDrawable`
accepts fraction insets from API 26, and this app's `minSdk` is 26.

**Background.** Sampled from the launcher master's own backdrop and reproduced
as a full-bleed radial gradient in `drawable/ic_launcher_background.xml`:

    #2B1C19  ->  #100B0A     (ic_launcher_background_glow -> ic_launcher_background)

## Generated runtime formats

All runtime assets are **WebP**, not PNG:

| Asset | Densities | Encoding |
|---|---|---|
| `ic_launcher_foreground` | mdpi … xxxhdpi | lossless |
| `ic_launcher` (legacy square) | ldpi … xxxhdpi | lossless |
| `ic_launcher_round` (legacy circle) | ldpi … xxxhdpi | lossless |
| `ic_launcher_monochrome` (themed icons) | mdpi … xxxhdpi | lossless |
| `ic_splash_mark` | single `drawable-nodpi`, 768 px | lossy, quality 90 |

The splash mark is lossy on purpose: it carries smooth petal gradients that a
256-colour PNG bands visibly, and WebP q90 holds them at 182 KB instead of
653 KB for the equivalent full-quality PNG.

## Notes

- The split is **outside vs inside the app**: the launcher and every shortcut
  icon carry the cup mark, and everything drawn inside the app — the Android
  cold-start splash, the first-run setup screen and the navigation drawer header
  — carries the round eight-petal emblem. The drawer and setup screens reference
  `drawable-nodpi/ic_splash_mark` directly, at their own `ImageView` size; only
  the splash goes through `ic_splash_emblem`, whose one-sixth inset exists purely
  to satisfy Android's 288 dp splash box and would shrink the mark anywhere else.
- The Terminal edition's hand-drawn `drawable/ic_brand_mark.xml` is a different
  mark with no relation to these masters. The Ubuntu edition no longer carries a
  copy of it.
- On a **warm** start Android draws the splash background without the icon. That
  is platform behaviour, not a packaging fault; the icon appears on cold start.
