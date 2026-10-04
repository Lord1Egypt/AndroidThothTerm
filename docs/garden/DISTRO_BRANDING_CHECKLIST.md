# Garden edition branding checklist

The contract every Garden distribution edition meets before its branding is
called done. It was written from what Rolling, Trixie and Security each needed,
and from a real defect: Security's first device build showed a placeholder
launcher icon, grey app chrome and a plain, uncoloured banner and prompt, all
because the edition had not yet supplied the data the shared code looks for.

**Rule: a Garden edition is branded by data and resources it supplies, never by
code.** `garden-common` has no edition artwork, no edition colours and no edition
conditionals; an edition that has not supplied something falls back to neutral
grey and a plain terminal *on purpose*, and that fallback must not survive into a
release candidate. Nothing below changes the terminal renderer, `thothfetch`,
`thothterm-garden.sh` or any `garden-common` Java.

## 0. Names (mandatory, before any artwork)

- [ ] **The product name is neutral.** It names no third-party distribution,
      repository or project, and no word a project's trademark policy reserves
      (Canonical's policy bars "Ubuntu" in a software title; Arch Linux's reserves
      names beginning "Arch"; a project with no published policy gets no benefit of
      the doubt). Compatibility with a project does not imply naming or branding
      rights.
- [ ] **Interoperability is named factually and apart from the product:** a
      third-party distro, repository or keyring may appear only as a plain
      description of what the user runs or can enable (a menu entry such as "Enable
      BlackArch repository", pinned URLs, provenance), always beside a
      non-affiliation notice, never in the app label, title, launcher, splash,
      notification, Gradle or package identity, tag, or next to "Edition".
- [ ] No third-party logo, wordmark, colour scheme presented as theirs, or
      endorsement language, in any resource, screenshot, store text or sheet. A
      delivered artwork sheet carrying a third-party name is retitled before use,
      with the change documented and a test that everything outside the retitled band
      is the delivered sheet's.
- [ ] A third-party trust or keyring bundle is never shipped without established
      redistribution rights (`docs/security/THORNS.md`, rule 10).

## 1. Artwork (mandatory)

- [ ] **One approved sheet**, the edition's own Garden artwork, committed
      byte-for-byte as delivered (or retitled as section 0 requires) under `docs/branding/<edition>/source/`, with its
      SHA-256 and original file name recorded in that directory's `README.md`.
      The artwork is project-owned: no upstream distribution logo, wordmark or
      trademark artwork in any Android resource (naming and trademark questions are
      decided in the edition's design record and are a release gate).
- [ ] **Two masters** extracted from the sheet by
      `tools/garden/branding/extract_masters.py` (or, for an edition derived from
      another's artwork, by `recolor.py`), hashes recorded: the **launcher cup** (the
      cup in its rounded-square frame) and the **emblem** (the round eight-petal
      mark). Neither carries the sheet's wordmark.
- [ ] **Outside the app: the cup.** Launcher and app drawer, adaptive foreground
      inside the 66 dp safe zone, adaptive background, round icon, legacy
      `ic_launcher`, monochrome (themed) layer, every density
      (`ldpi`-`xxxhdpi` where Rolling/Trixie provide them). Made by
      `tools/garden/branding/make_resources.py`; its `--edge`/`--glow`/`--frame`/
      `--radius` arguments are measured by `measure_sheet.py` into
      `make_resources.args`, never chosen by eye. The resource file set equals
      Rolling's.
- [ ] **Inside the app: the emblem, clean.** `drawable-nodpi/ic_splash_mark.webp`
      replaces garden-common's neutral placeholder under the same name, so every
      shared surface shows it with no layout change: the first-run welcome and
      progress screen, the download-consent and declined screens, the damaged /
      reinstall / reset-pending screens, the navigation drawer header, the Android 12+
      splash and About. No square launcher background, no wordmark, no new layout.
- [ ] Functional icons (settings, windows, add session, overflow) are **not**
      replaced.
- [ ] **No placeholder survives:** no hand-drawn stand-in launcher drawable, no
      resource or comment that says "placeholder", and `garden-common` still
      carries no edition artwork.

## 2. App chrome colours (mandatory)

- [ ] `<edition>/src/main/res/values/colors.xml` sets **all eleven** roles
      garden-common leaves neutral grey: `brand_background`, `brand_surface`,
      `brand_surface_light`, `brand_accent`, `brand_accent_dark`,
      `brand_light_background`, `brand_light_primary`, `brand_light_primary_dark`,
      `brand_light_primary_light`, `brand_on_surface`, `brand_on_surface_muted`. None
      equals garden-common's, nor another edition's.
- [ ] Mapped from the derived palette: window and status bar = background; app bar
      and drawer header = surface; switches, progress and selected controls =
      accent; extra-key fill = surface light; primary text = on surface; muted text =
      muted. The light theme's accent (drawn on the near-white background) clears 3:1.
- [ ] The shared theme (`styles.xml`) is not edited for one edition.

## 3. Terminal and guest palette (mandatory)

- [ ] `<edition>/src/main/assets/garden/palette.properties` with exactly the
      shared schema: `primary`, `secondary`, `highlight`, `foreground`, `muted`,
      `promptUser`, `promptHost`, `promptPath`, `background`, `surface` — each once,
      each `#RRGGBB`. `GardenPalette.load` fails closed on anything else.
- [ ] This single asset is the **only** source of the colour of: `thothfetch`
      (mark, edition name, distro line, labels, values), the managed prompt
      `thoth@thothterm:DIR$` (user, host and path in separate roles), the phone
      terminal's default scheme, and the LAN page's chrome (`/edition.css`). It
      reaches the guest as `/etc/thothterm/palette` through the shared managed-config
      path (`RootfsManager`, written on every session start); an edition adds no
      copy mechanism of its own and the rootfs archive is not rebuilt for a palette.
- [ ] **The shared `thothfetch` ASCII mark is unchanged.** Only its colours vary by
      edition. Do not draw an edition-specific ASCII logo.
- [ ] Text roles are drawn with the nearest xterm-256 colour; the five text roles
      land on five different colours, none of them one another edition draws with;
      each reaches 4.5:1 on the background as drawn, and muted reaches 4.5:1 on the
      surface.
- [ ] Programs' own ANSI colours are never changed. `NO_COLOR` still turns the
      banner and prompt colour off, and the text stays readable without colour.
- [ ] No edition colour is hard-coded in `thothfetch`, `thothterm-garden.sh`,
      garden-common Java or any other edition's files.

## 4. Provenance (mandatory)

- [ ] `docs/branding/<edition>/PALETTE.md`: the derivation command, the criteria
      that fixed any stated recipe (what was measured, what was mixed, and why),
      the role table (colour, source band, xterm index, contrast), and where each role
      is used.
- [ ] The palette is **sampled** from the sheet by a script under
      `tools/garden/branding/` (`palette.py` for a coloured sheet, `palette_metallic.py`
      for neutral metal), reproducible by another developer. Nothing is chosen by
      eye.
- [ ] A short identity statement, in one sentence, of how the edition differs from
      every other (for example blue versus metal).

## 5. Regression tests (mandatory)

An edition module has `<Edition>BrandingTest` and `<Edition>PaletteTest`, at least
at the strength of Rolling's and Security's, covering:

- [ ] every launcher layer exists at every density with the expected size; adaptive
      XML references the generated layers; the foreground stays inside the safe zone;
      the in-app mark has the master's size
- [ ] the resource set equals Rolling's; no placeholder resource exists
- [ ] resources are **reproducible**: regenerating from the masters yields identical
      bytes; the masters regenerate from the sheet; the `make_resources.args` are what
      `measure_sheet.py` measures; hashes in the README match the files
- [ ] the palette re-derives from the sheet; every role present once and well formed
- [ ] text-role xterm indexes, distinctness, readability, and difference from the
      other editions
- [ ] `colors.xml` sets all roles, differs from the neutral and other editions, and
      agrees with `palette.properties` where roles overlap
- [ ] the real `thothfetch` and `thothterm-garden.sh` are run with the palette: the
      banner and the prompt receive the edition's colours, `NO_COLOR` removes them
- [ ] the palette reaches the managed guest configuration through the shared path
- [ ] no edition colour or name leaks into garden-common or another edition

## 6. Real-device verification (mandatory before "branding PASS")

On the target phone, with an isolated QA build of the edition (never a production
package), screenshots saved as evidence, and each checked by eye against the sheet:

- [ ] app drawer and launcher: the real edition icon (adaptive, and round if the
      launcher shows it)
- [ ] first-run welcome / progress, and, for the F-Droid flavour, the consent and
      declined screens; the damaged and reinstall screens when reachable
- [ ] navigation drawer header and the app chrome in the edition's colours
- [ ] terminal: edition background, text and cursor; `thothfetch` coloured and its
      ASCII mark unchanged; the prompt coloured with separate user, host and path;
      no escape sequence printed as literal text; `NO_COLOR=1` plain; a program's own
      colours (`ls --color`, `pacman`) untouched
- [ ] a branding PASS is never claimed from screenshots of an earlier build, from
      the unit tests alone, or from a different phone theme or keyboard theme

## 7. Release candidate bar

No release candidate, tag or F-Droid submission may carry a placeholder icon, the
neutral fallback theme (grey chrome), a missing `palette.properties` (plain banner
and prompt), or a Full or F-Droid build whose in-app screens still show the neutral
emblem. If a surface is not yet branded the edition is not yet shippable.
