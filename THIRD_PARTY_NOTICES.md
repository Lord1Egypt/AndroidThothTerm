# Third-Party Notices — ThothTerm Ubuntu

ThothTerm Ubuntu itself is licensed under the Apache License 2.0 (see `LICENSE`
and `NOTICE`). This file inventories every third-party component that is
**bundled in the APK**, **required at runtime**, or **pulled in at build time**.

Licenses below were read from the components themselves wherever the artifact
was available to inspect — they are not inherited from a parent project. Where a
license is recorded from an upstream declaration rather than the artifact, the
"Verified" column says so.

Everything here is staged by `term-ubuntu/tools/prepare-assets.sh`, which pins
every download by SHA-256 and **fails closed** on any mismatch.

---

## 1. Bundled in the APK

### 1.1 Ubuntu base root filesystem

| Field | Value |
|---|---|
| Component | Ubuntu Base 26.04.1 LTS (`arm64`) |
| Upstream | `https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz` |
| Local path | `term-ubuntu/src/main/assets/ubuntu/ubuntu-base-26.04.1-base-arm64.tgz` |
| SHA-256 | `5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd` |
| Size | 35,092,106 bytes compressed / 122,122,240 uncompressed |
| Kind | **full flavour only** — downloaded at build time, embedded verbatim. The fdroid flavour ships no archive and fetches this same URL and digest on first run after explicit consent. |
| License | Aggregate of many licenses, per package |
| Verified | Yes — read from `/usr/share/doc/*/copyright` inside the extracted image |
| Redistribution | Ubuntu Base is redistributable. It is an **aggregate**: the image carries its own per-package copyright files, which are preserved verbatim inside the rootfs and are the authoritative terms. |

Most common declared licenses observed inside the image, by frequency of
`License:` lines: BSL-1.0, Expat (MIT), BSD-3-Clause, BSD-2-Clause, Apache-2.0,
`GPL-1+ or Artistic`, GPL-2+, MIT. **Copyleft (GPL/LGPL) components are present**,
so the source-offer obligations below apply.

The asset is renamed `.tgz` on purpose: Android's asset packager transparently
expands `*.gz` assets, which would break the runtime lookup.

### 1.2 Ubuntu admin packages (offline first-run provisioning)

Fetched from `http://ports.ubuntu.com/ubuntu-ports`, embedded as `.deb` and
installed into the guest on first run so that `sudo` is genuine Ubuntu `sudo`.

| Package | SHA-256 | License | Verified |
|---|---|---|---|
| `sudo_1.9.17p2-1ubuntu3_arm64.deb` | `a1f04e24343ad3b73123ca2dc2b43684ef1562ea3ed43788dc8ec79da574a873` | ISC, with BSD-2-Clause and BSD-3-Clause parts | Yes — `/usr/share/doc/sudo/copyright` |
| `sudo-common_1.2ubuntu_all.deb` | `ba909e8e796115f442d0915ed3baa1b752e8809ec4d8e723d2bbd0d177750d2c` | As `sudo` | Yes |
| `libapparmor1_5.0.0~beta1-0ubuntu7_arm64.deb` | `97bc3adba874fdda34afba6a206d3fcd5b531bb8681ba27c4442ee1379834cfa` | LGPL-2.1+, with GPL-2+ and BSD-3-clause parts | Yes — `/usr/share/doc/libapparmor1/copyright` |

### 1.3 PRoot runtime (built from source)

The runtime is **compiled during the build** from sources in this repository by
`term-ubuntu/tools/build-proot.sh`. Nothing is downloaded and no prebuilt binary
is copied in. The script verifies both submodule commits and fails closed if
either has moved.

| Component | Source in this repo | Upstream | Exact revision | License |
|---|---|---|---|---|
| PRoot + loader | `third_party/proot` (submodule) | `https://github.com/termux/proot` | tag `v5.1.107.92` = commit `7266fb3e8516535682f5a9c8f3a7e70f6506eddb` | **GPL-2.0** |
| libandroid-shmem | `third_party/libandroid-shmem` (submodule) | `https://github.com/termux/libandroid-shmem` | tag `v0.7` = commit `7f0bd7e25dbdd146265aff7c6a890029e374622d` | BSD-3-Clause |
| talloc | `third_party/talloc/talloc.c`, `talloc.h` | `https://www.samba.org/ftp/talloc/talloc-2.4.3.tar.gz` (sha256 `dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd`) | 2.4.3, files copied verbatim | **LGPL-3.0** |

talloc publishes release tarballs but no git repository, so the two files PRoot
needs are vendored rather than submoduled. They are byte-identical to the
tarball: `talloc.c` sha256 `eeefb4b7545b7411d2fd0d7fdce4a2f0c3ebdb2153215dd1494e1f61fd73bb73`,
`talloc.h` sha256 `e01fb092aaed2b431be26674e2b791c77fb5984537c29b514e957582c6b31465`.

#### Local modifications

One patch, kept in `term-ubuntu/patches/` and applied in filename order:

| Patch | Applies to | What it does |
|---|---|---|
| `0001-ashmem_memfd-include-string.h.patch` | `third_party/proot` | Adds `#include <string.h>` to `src/extension/ashmem_memfd/ashmem_memfd.c`. The file calls `strcmp()` and `memset()` without declaring them; clang 21 (NDK r29) rejects the implicit declarations, and an implicitly declared `memset()` would return `int`, truncating the pointer on arm64. No behavioural change. |

No other upstream source is modified. `libandroid-shmem` is built unpatched,
with `_PATH_TMP` defined at compile time to the app's runtime scratch directory.

#### Build contract

`arm64-v8a`, API 26, NDK pinned by `ext.ndkVersion` (currently `23.2.8568313`),
`PROOT_WITH_LIBANDROID_SHMEM=1`, unbundled loader, and
`-Wl,-z,max-page-size=16384` so shared objects work on 16 KB page devices. The
script asserts the resulting alignment is `0x4000` and that `libproot.so` links
against both `libtalloc.so.2` and `libandroid-shmem.so`, rather than assuming it.

Artifacts produced by the pinned toolchain at the time of writing:

| Artifact | Size | SHA-256 |
|---|---|---|
| `libproot.so` | 271,864 | `f45d9c262d3a50951107a8861960a28582a8a9eab4929d8bb764980f4782140e` |
| `libproot_loader.so` | 6,328 | `72c7a54f61ae83e47d3920577558f1a988b1a311a9b1a2f2aaa846989fea4ae5` |
| `libtalloc.so.2` | 46,072 | `7bf984aad2595ee6dd3dba2530c8c159780815880dbcd5727493a9697cc6c3e5` |
| `libandroid-shmem.so` | 18,432 | `3f26c27c6c18ad65d244a88862b63fb888554ff0bcf013d30a00c0cd92b738aa` |

These are outputs of a specific toolchain, not a reproducibility claim across
machines. `libproot.so` and `libproot_loader.so` are executables, not libraries;
they ship under `lib*.so` names so Android extracts them into
`nativeLibraryDir` with the executable bit, which needs `useLegacyPackaging`.

`libandroid-selinux.so` was carried by the former prebuilt bundle and is **no
longer shipped**: the binary built here does not reference it.

### 1.4 Font

| Component | Local path | License | Verified |
|---|---|---|---|
| DejaVu Sans Mono | `term-ubuntu/src/main/assets/font/DejaVuSansMono.ttf` | Bitstream Vera Fonts Copyright (permissive); DejaVu changes public domain; Arev glyphs © Tavmjong Bah | Yes — `term-ubuntu/src/main/assets/font/DejaVu.lic` ships beside it |

---

## 2. Application source lineage

ThothTerm is a fork of **TermOne Plus**
(`https://gitlab.com/termapps/termoneplus`, Roumen Petrov and contributors),
itself derived from **Terminal Emulator for Android** (Jack Palevich) and the
**Android Open Source Project**. All three are Apache-2.0, upstream copyright
headers are retained in the source files, and the lineage is recorded in
`NOTICE`. Verified: yes — file headers and `NOTICE`.

---

## 3. Build-time and compile-time dependencies

All versions are pinned; there are **no dynamic versions** and no floating
ranges. None of these ship code into the APK beyond the AndroidX/Material
runtime libraries, which are Apache-2.0.

| Dependency | Version | License | Kind |
|---|---|---|---|
| `androidx.appcompat:appcompat` | 1.6.1 | Apache-2.0 | Runtime |
| `com.google.android.material:material` | 1.11.0 | Apache-2.0 | Runtime |
| `androidx.activity:activity` | 1.8.2 | Apache-2.0 | Runtime |
| `androidx.preference:preference` | 1.2.1 | Apache-2.0 | Runtime |
| `androidx.annotation:annotation` | 1.10.0 | Apache-2.0 | Compile |
| Android Gradle Plugin | 9.4.0 | Apache-2.0 | Build |
| Gradle | 9.7.1 (wrapper pinned by SHA-256) | Apache-2.0 | Build |
| Android NDK | 23.2.8568313 | Android SDK terms | Build |
| CMake | 3.22.1 | BSD-3-Clause | Build |
| `junit:junit` | 4.13.2 | EPL-1.0 | Test only |
| `androidx.test:runner` | 1.6.2 | Apache-2.0 | Test only |
| `androidx.test.ext:junit` | 1.2.1 | Apache-2.0 | Test only |
| `androidx.test.uiautomator:uiautomator` | 2.3.0 | Apache-2.0 | Test only |

Test-only dependencies are never packaged into the release APK.

---

## 4. Copyleft obligations and corresponding source

PRoot and its loader are **GPL-2.0**, talloc is **LGPL-3.0**, and the Ubuntu base
image contains GPL/LGPL packages. Distributing the app carries a corresponding
source obligation for those components.

How it is met, per component:

**PRoot and its loader.** The corresponding source is not merely "the upstream
project": it is `third_party/proot` at commit
`7266fb3e8516535682f5a9c8f3a7e70f6506eddb`, **plus** the patch in
`term-ubuntu/patches/`, **plus** the build script
`term-ubuntu/tools/build-proot.sh` which records the exact compiler, flags and
link contract. All three are in this repository and are carried by every release
tag, so a recipient of any build can reproduce the exact binary from the tag
alone. Because the submodule pin is recorded in the tagged tree, this remains
true even if upstream later moves or disappears.

**talloc.** The two files compiled into `libtalloc.so.2` are vendored verbatim in
`third_party/talloc/`, with the upstream tarball URL and its sha256 recorded
above, and the compile flags in the same build script.

**libandroid-shmem.** BSD-3-Clause rather than copyleft, but pinned and recorded
the same way.

**Ubuntu packages.** Source for each pinned version is available from Ubuntu's
archive (`ports.ubuntu.com`, matching `*.dsc` and `*.orig.tar.*`). ThothTerm
redistributes these binaries unmodified in the full flavour; the fdroid flavour
does not redistribute them at all, since the user obtains them from Canonical
directly.

PRoot runs as a **separate process** (`execve`), not linked into the
application, so the Apache-2.0 app and the GPL-2.0 PRoot binary are an
aggregation rather than a combined work.

**Practical effect:** the source tag is the offer. A release should point at the
tag (and, if binaries are published, attach or link the same tag), so that the
corresponding source for a distributed binary is identified exactly rather than
by a moving branch. This is a practical implementation of the requirement, not
legal advice.

## 5. Trackers, analytics and proprietary components

Audited across all Gradle files, manifests and Java sources:

- No Firebase, Crashlytics, Google Analytics, AdMob, Play Services or any GMS
  dependency.
- No `google-services.json` anywhere in the repository.
- No third-party analytics, attribution, crash-reporting or telemetry SDK.
- No proprietary runtime library.
- The app performs no network I/O of its own; network use is whatever the user
  runs inside the guest (for example `apt`).

Result: **no known non-free or tracking component.**
