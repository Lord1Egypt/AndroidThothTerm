# Third-party notices — ThothTerm Rolling

ThothTerm Rolling runs an Arch Linux ARM AArch64 environment. This app is
independent and is not affiliated with or endorsed by the Arch Linux or Arch
Linux ARM projects. Arch Linux is a trademark of Levente Polyák and Judd Vinet,
on behalf of Arch Linux. "Arch Linux ARM" is named only to describe the
environment the app installs; the app uses no Arch Linux or Arch Linux ARM
logo or artwork (`docs/branding/arch/TRADEMARK.md`).

## 1. Arch Linux ARM AArch64 environment

| | |
|---|---|
| What | An Arch Linux ARM aarch64 root filesystem: `base` plus `archlinuxarm-keyring sudo ca-certificates curl` and their dependencies, all unmodified Arch Linux ARM binary packages, fully upgraded as of its build |
| Built by | `garden-arch/rootfs/build-rootfs.sh`, reproducibly, from the signature-verified official `ArchLinuxARM-aarch64-latest.tar.gz` and packages verified against the Arch Linux ARM Build System key |
| Archive | named and pinned in `garden-arch/src/main/assets/garden/distro.properties` (content-addressed: `thothterm-arch-aarch64-rootfs-<first 12 of SHA-256>.tar.gz`) |
| Full flavour | embeds that archive, byte for byte |
| F-Droid flavour | embeds nothing; downloads that archive on first run, after consent, and refuses it unless its size and SHA-256 match |
| Licences | each package under its own licence; the texts ship inside the rootfs in `/usr/share/licenses/` |
| Source | the corresponding source of every package (recipe, patches, upstream sources) is published beside the archive in its GitHub release; see `docs/garden/arch/ROOTFS_PROVENANCE.md` |

## 2. Shared Garden components

The PRoot runtime (PRoot GPL-2.0, talloc LGPL-3.0, libandroid-shmem
BSD-3-Clause) built from source, the DejaVu Sans Mono terminal font, and the
LAN Mode web terminal (xterm.js, its fit addon, bidi-js, Cascadia Mono): see
`garden-common/THIRD_PARTY_NOTICES.md`. PRoot runs as a separate process, not
linked into the app. Its corresponding source is the pinned submodule, the
patches in `garden-common/patches/` and `garden-common/tools/build-proot.sh`,
all carried by every release tag.

## 3. Runtime libraries in the APK

AndroidX AppCompat, Activity, Preference, Annotation and Material Components
for Android, all Apache-2.0. Test-only dependencies (JUnit, EPL-1.0) are never
packaged.

## 4. Artwork

The launcher cup and the in-app eight-petal emblem are ThothTerm's own Garden
artwork, turned blue by `tools/garden/branding/recolor.py` and made into
resources by `tools/garden/branding/make_resources.py`
(`docs/branding/arch/source/`).

## 5. Trackers and proprietary components

None. No Google Play Services, Firebase, analytics, crash reporting or
advertising SDK. The app's only own network request is the rootfs download of
the F-Droid flavour, after consent, from the project's GitHub release; LAN Mode
is a server on the local network that is off by default. Everything else is
what the user runs inside the environment, such as `pacman`.
