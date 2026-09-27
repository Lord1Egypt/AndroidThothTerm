# Third-party notices — ThothTerm Trixie

ThothTerm Trixie is an independent application and is not affiliated with or
endorsed by the Debian Project. Debian is a registered trademark owned by
Software in the Public Interest, Inc. "Debian GNU/Linux 13 (trixie)" is named
only to describe the userland the app installs; the app uses no Debian logo.

## 1. Debian userland

| | |
|---|---|
| What | A Debian 13 "trixie" arm64 root filesystem: debootstrap's minbase plus `sudo ca-certificates curl netbase procps`, 109 unmodified Debian binary packages |
| Built by | `garden-debian/rootfs/build-rootfs.sh` (debuerreotype, reproducible) from snapshot.debian.org, verified against `debian-archive-keyring` |
| Archive | `thothterm-debian-trixie-arm64-rootfs-f6520ff1c6ee.tar.gz`, SHA-256 `f6520ff1c6eeb28ead2d175e6733da4c970459a291b1bd60f992f1655c5d0677` |
| Full flavour | embeds that archive |
| F-Droid flavour | embeds nothing; downloads that archive on first run, after consent, and refuses it unless its size and SHA-256 match |
| Licences | each package under its own licence; the texts ship inside the rootfs in `/usr/share/doc/*/copyright` |
| Source | every package's source at the same moment on snapshot.debian.org; see `docs/garden/debian/ROOTFS_PROVENANCE.md` |

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

The launcher cup and the in-app eight-petal emblem are ThothTerm's own,
generated reproducibly from the approved masters in
`docs/branding/debian/source/` by `tools/garden/branding/make_resources.py`.

## 5. Trackers and proprietary components

None. No Google Play Services, Firebase, analytics, crash reporting or
advertising SDK. The app's only own network request is the rootfs download of
the F-Droid flavour, after consent, from the project's GitHub release; LAN Mode
is a server on the local network that is off by default. Everything else is
what the user runs inside Debian, such as `apt`.
