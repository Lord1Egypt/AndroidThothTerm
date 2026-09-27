# ThothTerm Garden — third-party components of garden-common

`garden-common` is the layer every Garden edition shares. This file lists what
it contributes to an edition's APK. Each edition's own
`THIRD_PARTY_NOTICES.md` adds its distribution userland and is the complete
notice for that app.

## PRoot runtime (built from source)

Compiled during the edition's build by `garden-common/tools/build-proot.sh`
from sources in this repository; nothing is downloaded and no prebuilt binary
is copied in.

| Component | Source | Revision | License |
|---|---|---|---|
| PRoot + loader | `third_party/proot` (termux/proot) | tag `v5.1.107.92` = `7266fb3e8516535682f5a9c8f3a7e70f6506eddb` | GPL-2.0 |
| libandroid-shmem | `third_party/libandroid-shmem` (termux/libandroid-shmem) | tag `v0.7` = `7f0bd7e25dbdd146265aff7c6a890029e374622d` | BSD-3-Clause |
| talloc | `third_party/talloc/talloc.c`, `talloc.h`, verbatim from talloc 2.4.3 | sha256 of the tarball `dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd` | LGPL-3.0 |

Local modifications are the four patches in `garden-common/patches/`, kept
byte-identical to `term-ubuntu/patches/`. The corresponding source for the
PRoot binaries is the pinned submodule plus those patches plus the build
script, all carried by every release tag.

## Terminal font

DejaVu Sans Mono 2.37, `src/main/assets/font/DejaVuSansMono.ttf`; its licence
ships beside it as `DejaVu.lic`.

## LAN Mode web terminal

`src/main/assets/lan/`, served only to paired browsers on the local network and
never fetched from a CDN. Built by `lan-web/build.mjs` from npm packages pinned
by sha512 in `lan-web/package-lock.json`; provenance in `lan-web/README.md`,
notices in `assets/lan/licenses.txt`.

| Component | Version | License |
|---|---|---|
| xterm.js (bundled unminified from source) | 6.0.0 | MIT |
| @xterm/addon-fit | 0.11.0 | MIT |
| VS Code base library, as vendored by xterm.js | — | MIT |
| bidi-js | 1.0.3 | MIT |
| Cascadia Mono (served as "ThothTerm Mono") | @fontsource/cascadia-mono 5.3.0 | SIL OFL 1.1 |

## Application source lineage

ThothTerm is a fork of TermOne Plus, derived from Terminal Emulator for Android
and the Android Open Source Project, all Apache-2.0; see the repository's
`NOTICE`. `emulatorview/` and `libtermexec/` are covered by the top-level
`NOTICE` and `LICENSE`.
