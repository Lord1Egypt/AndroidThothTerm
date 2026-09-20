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
| Kind | Bundled (downloaded at build time, embedded verbatim) |
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

### 1.3 PRoot runtime

Staged from the ProotX support bundle
`https://github.com/Lord1Egypt/ProotX-Assets-Support/releases/download/v1.2.0/arm64-v8a-assets.zip`
(bundle SHA-256 `42fd0042b18d8145ebb72aece000404bed8a0911c83505c1acf31e2da5033fe7`,
`modern` lane). That bundle's own `THIRD_PARTY_NOTICES.md` records the upstream
each binary was built from; those upstreams are reproduced here.

| Component | Local path | SHA-256 | Upstream | License |
|---|---|---|---|---|
| PRoot | `term-ubuntu/src/main/jniLibs/arm64-v8a/libproot.so` | `ea47e17da8e6ff4882c169c6508861e5b4be9227e477c6020f4f14facc85c10d` | `github.com/termux/proot` tag `v5.1.107.92` | **GPL-2.0** |
| PRoot loader | `term-ubuntu/src/main/jniLibs/arm64-v8a/libproot_loader.so` | `44ef39c1e1a18c09f6e4c4b5d6f8bba82d30596598bd155ec162d05c5122ff04` | same as PRoot | **GPL-2.0** |
| libtalloc | `term-ubuntu/src/main/assets/runtime/arm64-v8a/libtalloc.so.2` | `247045a3292c6dd5eda2b95bb419dd39a6776bb3a42553cb48bcf4b49ce8bfb4` | `talloc-2.4.3.tar.gz` (samba.org) | **GPL-3.0** |
| libandroid-shmem | `term-ubuntu/src/main/assets/runtime/arm64-v8a/libandroid-shmem.so` | `84475798e07c8174dbbfaec70a827fdb02f19ffa69a589380c13e7507fd0e731` | `github.com/termux/libandroid-shmem` tag `v0.7` | BSD-3-Clause |
| libandroid-selinux | `term-ubuntu/src/main/assets/runtime/arm64-v8a/libandroid-selinux.so` | `22de4f5b9fdfd6c2681f91fe5a2629cc01ac965fb20850085228999413734d68` | termux-packages `libandroid-selinux` 14.0.0.11-1 | Apache-2.0 |

Verified: upstream identity is taken from the support bundle's notices, **not**
independently rebuilt here. See §4.

`libproot.so` and `libproot_loader.so` are not libraries. They are executables
shipped under `lib*.so` names so Android extracts them into
`ApplicationInfo.nativeLibraryDir` with the executable bit set; `useLegacyPackaging`
is required for this.

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

## 4. Copyleft obligations

PRoot and its loader are **GPL-2.0**; `libtalloc.so.2` is **GPL-3.0**; the Ubuntu
base image contains GPL/LGPL packages. Distributing the APK therefore carries a
source-availability obligation for those components.

How it is met:

- **PRoot / loader** — corresponding source is the pinned upstream
  `github.com/termux/proot` at tag `v5.1.107.92`. It is recorded here and in
  `term-ubuntu/src/main/assets/ubuntu/image.properties`.
- **libtalloc** — `talloc-2.4.3.tar.gz` from samba.org.
- **Ubuntu packages** — source is available from Ubuntu's archive for the exact
  pinned versions (`ports.ubuntu.com`, matching `*.dsc`/`*.orig.tar.*`).

PRoot runs as a **separate process** (`execve`), not linked into the app, so this
is aggregation rather than a combined work; the Apache-2.0 application and the
GPL-2.0 PRoot binary may be distributed together provided PRoot's own source
offer is honoured.

**Open item:** ThothTerm does not yet publish a written offer or a source mirror
for the GPL components it redistributes. Recording the exact upstream tag, as
done above, is necessary but a release should also carry the offer text. See
`docs/FDROID_READINESS.md`.

---

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
