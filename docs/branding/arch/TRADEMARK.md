# ThothTerm Rolling — trademark and naming decision

**Decision (2026-09-28): the product is named ThothTerm Rolling.** It is
described factually as running "an Arch Linux ARM AArch64 environment", with
a non-affiliation notice. The name *ThothTerm Arch* is not used anywhere a user
sees it.

Internal identifiers keep `arch`, because they describe the guest to the
project, not a product name: package `com.thothterm.arch`, Gradle module
`garden-arch`, source tags `arch-v*`, rootfs tags `arch-rootfs-aarch64-*`.

## Evidence

Audited on 2026-09-28 06:43 UTC from the live pages below; SHA-256 of each
page as fetched.

| Source | Version / SHA-256 of the HTML |
|---|---|
| Arch Linux Trademark Policy, <https://terms.archlinux.org/docs/trademark-policy/> | "Version: 2021-04-18"; `58b6739ce803035c43baa4df354a4db8895e669eff5ebbfd82d401e9fbf3e255` |
| Arch Linux ARM home page, <https://archlinuxarm.org/> | `ea838e3f3f738c70f69b4acefe0cd34e3322270c43ee40d5ca07268d136e16fd` |
| Arch Linux ARM Package Signing, <https://archlinuxarm.org/about/package-signing> | `5195799111dacc5a3077e89e10fbc2f4f66f3643896905e0fba640f8c0d223cb` |
| Arch Linux ARM Generic AArch64, <https://archlinuxarm.org/platforms/armv8/generic> | `1d9c6ddbba9cd5528d8187548aad1b856f7a7df442fe38654af6b984de9704e2` |

### What the Arch Linux policy says

- §1: the marks are "ARCHLINUX", "ARCH LINUX", the tagline and the logo, and
  **"Any mark beginning with the letters ARCH is sufficiently similar to one or
  more of the trademarks that permission will be needed in order to use it."**
- §2 "Building on Arch Linux or for Arch Linux": a product for Arch may name it
  descriptively ("System Management for Arch Linux"), but names such as
  "ArchMan", "Arch Management" or "ArchTools" are "strongly discouraged" and
  "likely … problematic".
- §3 lists as unlikely to be approved: a **"combined mark"** — "use that
  integrates other wording with the Trademark in a way that the public may
  think of the use as a new mark (for example Club Arch Linux or ArchBooks)" —
  and use that implies endorsement or presents a product as official.
- §2 "Derived works": a product with more than minimal changes may say it is
  "based on Arch Linux", "but you may not use the Trademarks to refer to your
  product".
- §4: the logo keeps its ™ and its standard form; §2 advocacy use must not
  suggest approval or affiliation.

### What Arch Linux ARM says

Every archlinuxarm.org page ends: "The Arch Linux™ name and logo are used under
permission of the Arch Linux Project Lead." Arch Linux ARM is itself a
separately permitted user of the marks; it publishes no licence that extends
them to third parties.

## Conclusion

"ThothTerm Arch" puts other wording directly before a word that begins with
"ARCH" and would be read as a new product name — the policy's own example of a
combined mark, and a use for which "permission will be needed". That is not a
clearly permitted use, so the brief's rule applies: do not block the project,
use the neutral name. No trademark request was sent; none is needed for the
neutral name.

Permitted, and used, is factual reference to what the app installs:

- "ThothTerm Rolling runs an Arch Linux ARM AArch64 environment."
- "Arch Linux ARM" names the upstream project and its packages, in the About
  screen, the first-run consent text, the store description and the docs.

Every such surface carries the notice:

> This app is independent and is not affiliated with or endorsed by the Arch
> Linux or Arch Linux ARM projects. Arch Linux is a trademark of Levente Polyák
> and Judd Vinet, on behalf of Arch Linux.

## Artwork and colour

- The Arch Linux logo, and any Arch Linux or Arch Linux ARM artwork, is **not**
  used anywhere: not in the launcher icon, the in-app emblem, the LAN page,
  screenshots or store graphics.
- The launcher keeps the ThothTerm cup; inside the app is the eight-petal
  Garden emblem. Both are ThothTerm's own Garden masters turned to a cool blue
  by `tools/garden/branding/recolor.py` (see `PALETTE.md`).
- The edition's blue (petals at hue 218°, `#4F8AF4`) is deliberately not Arch
  Linux's brand blue (`#1793D1`, hue 200°), so that no presentation "conveys an
  impression that the two are tied" (§3).
- No wording says or implies "official", "certified", "endorsed" or "supported
  by" Arch Linux or Arch Linux ARM.
