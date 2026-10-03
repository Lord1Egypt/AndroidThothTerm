# Third-party notices — ThothTerm BlackArch

**Development.** A host-validated rootfs candidate exists and is not published;
nothing here is released.

ThothTerm BlackArch is independent and is not affiliated with or endorsed by
the BlackArch, Arch Linux or Arch Linux ARM projects. Arch Linux is a trademark
of Levente Polyák and Judd Vinet, on behalf of Arch Linux. Those names are used
only to describe what the app installs, and no BlackArch, Arch Linux or Arch
Linux ARM logo or artwork is used.

## 1. Environment

| | |
|---|---|
| What | An Arch Linux ARM aarch64 root filesystem identical in its Arch Linux ARM packages to ThothTerm Rolling's (`base` plus `archlinuxarm-keyring sudo ca-certificates curl` and their dependencies, unmodified, as of 2026-09-30), plus the `blackarch-keyring` package from the BlackArch aarch64 repository and a `[blackarch]` section in `pacman.conf` that keeps the global signature level. 139 packages. No BlackArch tool is preinstalled. |
| Built by | `garden-blackarch/rootfs/build-rootfs.sh`, reproducibly (three byte-identical builds), from the signature-verified official `ArchLinuxARM-aarch64-latest.tar.gz`, packages verified against the Arch Linux ARM Build System key, and a BlackArch keyring pinned by SHA-256 and signer |
| Archive | named and pinned in `src/main/assets/garden/distro.properties` (`thothterm-blackarch-aarch64-rootfs-<first 12 of SHA-256>.tar.gz`); **not published** |
| Full flavour | embeds that archive, byte for byte, when it is staged locally |
| F-Droid flavour | embeds nothing; no F-Droid release can be packaged until the archive is published |
| Licences | each package under its own licence (95 of 139 copyleft; texts in `/usr/share/licenses/` in the rootfs). `blackarch-keyring` declares `custom:unknown`: unresolved, a release gate |
| Source | `rootfs/collect-sources.py` collects the corresponding source of all 139 packages (Rolling's verified collection for the 138 Arch Linux ARM packages, the pinned recipe and sources for `blackarch-keyring`); not published. See `docs/garden/blackarch/ROOTFS_PROVENANCE.md` |

## 2. Shared Garden components

As in `garden-common/THIRD_PARTY_NOTICES.md`: the PRoot runtime (GPL-2.0,
talloc LGPL-3.0, libandroid-shmem BSD-3-Clause), the terminal font, and the LAN
Mode web terminal.

## 3. Artwork

The launcher icon in this tree is a plain placeholder. The edition's artwork
will be ThothTerm's own Garden artwork (`logos/`), not BlackArch's.
