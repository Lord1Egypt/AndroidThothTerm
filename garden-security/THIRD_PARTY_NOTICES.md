# Third-party notices — ThothTerm Security

ThothTerm Security runs an Arch Linux ARM AArch64 environment, and can
optionally enable a third-party package repository. This app is independent
and is not affiliated with or endorsed by the BlackArch, Arch Linux or Arch
Linux ARM projects. Arch Linux is a trademark of Levente Polyák and Judd Vinet,
on behalf of Arch Linux. The other projects' names are used only to describe
what the app installs or can connect to; no BlackArch, Arch Linux or Arch Linux
ARM logo or artwork is used, and none of them is part of this app's name
(`docs/garden/security/DESIGN.md`, "Naming").

## 1. Base system

| | |
|---|---|
| What | The Arch Linux ARM aarch64 root filesystem ThothTerm Rolling ships, identical byte for byte: `base` plus `archlinuxarm-keyring sudo ca-certificates curl` and their dependencies, all unmodified Arch Linux ARM binary packages, as of 2026-09-30. No security tool and no third-party repository, keyring or key is in it |
| Built by | `garden-arch/rootfs/build-rootfs.sh`, reproducibly, from the signature-verified official `ArchLinuxARM-aarch64-latest.tar.gz` and packages verified against the Arch Linux ARM Build System key. Rebuilt twice for this edition: `docs/garden/security/ROOTFS_PROVENANCE.md` |
| Archive | `thothterm-arch-aarch64-rootfs-03a4c669ed6f.tar.gz`, pinned in `src/main/assets/garden/distro.properties` (size and SHA-256), published in the immutable GitHub release `arch-rootfs-aarch64-03a4c669ed6f` |
| Full flavour | embeds that archive, byte for byte |
| F-Droid flavour | embeds nothing; downloads that archive on first run, after consent, and refuses it unless its size and SHA-256 match |
| Licences | each package under its own licence; the texts ship inside the rootfs in `/usr/share/licenses/` |
| Source | the corresponding source of every package is published beside the archive in the same GitHub release |

## 2. Optional third-party repository (not bundled)

| | |
|---|---|
| What | The BlackArch aarch64 package repository, enabled only if the user chooses to, after a consent screen |
| Shipped by this app | nothing: no package, no keyring, no key file, no repository configuration. The edition's `distro.properties` holds only pins (URLs, sizes, SHA-256 digests, key fingerprints) |
| Fetched at run time | `blackarch-keyring-20251011-2-any.pkg.tar.zst` and its detached signature from `https://blackarch.org/blackarch/blackarch/os/aarch64/`, then the repository databases. The keyring package carries no licence file and declares `custom:unknown`; that is why it is not redistributed |
| Integrity | exact size and SHA-256 of both files, the four trusted fingerprints and the revoked one compared before any key is imported, the package signature checked against the pinned signer, then installed by pacman with `LocalFileSigLevel = Required` |
| Signature checking | stays required for every repository; nothing here lowers a `SigLevel` |

## 3. Shared Garden components

As in `garden-common/THIRD_PARTY_NOTICES.md`: the PRoot runtime (GPL-2.0,
talloc LGPL-3.0, libandroid-shmem BSD-3-Clause), the terminal font, and the LAN
Mode web terminal.

## 4. Artwork

The launcher cup and the in-app eight-petal emblem are ThothTerm's own Garden
artwork in graphite and silver (`docs/branding/security/source/`).

## 5. Trackers and proprietary components

None. No Google Play Services, Firebase, analytics, crash reporting or
advertising SDK. The app's own network requests are the rootfs download of the
F-Droid flavour, after consent, from the project's GitHub release, and the
optional repository setup described in section 2, only after a second consent.
LAN Mode is a server on the local network that is off by default. Everything
else is what the user runs inside the environment, such as `pacman`.
