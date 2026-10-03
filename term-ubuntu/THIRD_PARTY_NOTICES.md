# ThothTerm Ubuntu third-party notices

ThothTerm Ubuntu embeds third-party software. Upstream copyright notices and
licence files shipped inside the Ubuntu root filesystem are preserved
unmodified under `/usr/share/doc/` in the extracted image. This file summarises
the direct components.

## Ubuntu 26.04.1 LTS base root filesystem

- Component: official Ubuntu base root filesystem, ARM64.
- Source: `https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz`
- SHA-256: `5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd`
- The image is redistributed **unmodified** (no repack). It aggregates many
  packages, each under its own licence; the corresponding licence texts are
  contained in the image. Ubuntu is a trademark of Canonical Ltd.; this
  application is not affiliated with, sponsored by, or endorsed by Canonical.
  The name "ThothTerm Ubuntu" only describes the embedded runtime.

## PRoot

- Component: PRoot, rootless user-space Linux runtime (ptrace based).
- Source lineage: `https://github.com/green-green-avk/proot`
  (a Termux-PRoot fork with Android adaptations), commit
  `7266fb3e8516535682f5a9c8f3a7e70f6506eddb`.
- Binary bundle: ProotX support bundle v1.2.0, asset `arm64-v8a-assets.zip`,
  SHA-256 `42fd0042b18d8145ebb72aece000404bed8a0911c83505c1acf31e2da5033fe7`,
  modern lane for Android API 29+, built with NDK 29.
- Licence: GNU General Public License v2.0 (GPL-2.0). The bundled `proot` and
  `loader` are redistributed as part of the APK. The complete corresponding
  source is available from the upstream repository and the ProotX release
  provenance metadata (`v1.2.0-provenance.json`, `v1.2.0.spdx.json`).
- It must not be replaced at application runtime; it ships with the APK.

## Host support libraries

Bundled from the same ProotX v1.2.0 modern lane and copied to app-private
storage at first run: `libtalloc.so.2` (Samba talloc, LGPL-3.0-or-later),
`libandroid-shmem.so` (MIT), `libandroid-selinux.so` (Apache-2.0).

## Engine modules

`emulatorview/` and `libtermexec/` are the original ThothTerm terminal-engine
modules and are covered by the repository's top-level `NOTICE` and `LICENSE`.

## DejaVu Sans Mono

- Component: DejaVu Sans Mono, version 2.37 (bundled terminal font).
- Source: `https://dejavu-fonts.github.io/`.
- Licence: permissive DejaVu Fonts licence; the complete text is packaged at
  `assets/font/DejaVu.lic`.
