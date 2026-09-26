# ThothTerm Ubuntu — embedded Ubuntu 26.04 LTS ARM64 runtime

Status: first Linux-runtime milestone on `feature/thotterm-linux`.
Golden tag `terminal-v1.0.0` and the terminal edition (`com.thothterm`) are untouched.

ThothTerm Ubuntu is a separate installable application that carries an official
Ubuntu 26.04 LTS ARM64 base root filesystem inside the APK, extracts it locally
on first launch, and enters it through a packaged rootless PRoot runtime. There
is no distro selector, no root, no runtime download, and no `proot-distro`.

## Identity

| Item | Value |
| --- | --- |
| Module | `term-ubuntu/` |
| Application ID | `com.thothterm.ubuntu` |
| Launcher label | ThothTerm Ubuntu |
| versionName | `0.1.0` |
| versionCode | `100` |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| ABI | `arm64-v8a` only |
| Packaging | `extractNativeLibs=true`, `useLegacyPackaging=true` |

## Product family (ThothTerm Garden)

| Edition | Application ID | Status |
| --- | --- | --- |
| ThothTerm Terminal Emulator | `com.thothterm` | Released (1.0 golden baseline) |
| ThothTerm Ubuntu | `com.thothterm.ubuntu` | This milestone (0.1.0 dev) |
| ThothTerm Kali (future) | `com.thothterm.kali` | Not implemented |
| ThothTerm Arch (future) | `com.thothterm.arch` | Not implemented |
| ThothTerm AlmaLinux (future) | `com.thothterm.almalinux` | Not implemented |

Distinct application IDs let the editions install side by side without clobbering
data or signatures. See `docs/EDITION_PLAN.md`.

## Module architecture

```text
term/            terminal-emulator app        com.thothterm        (unchanged)
term-ubuntu/     Ubuntu app                   com.thothterm.ubuntu (new)
emulatorview/    shared terminal engine       (unchanged)
libtermexec/     shared PTY/JNI engine        (unchanged)
```

`term-ubuntu` depends on `emulatorview` and `libtermexec` unchanged. For this
first milestone its application layer (activities, service, session, settings,
theme, extra keys, diagnostics UI) is a copy of `term`'s, adapted for the Ubuntu
product. This avoids a premature shared-UI refactor; a later milestone may lift
the common layer into a library module.

None of the terminal features were redesigned: PTY/session ownership, multiple
windows, Extra Keys, Arabic/RTL rendering, theming, and Diagnostics all remain.

## Ubuntu rootfs provenance

- Source: official Ubuntu base rootfs from Canonical's image service.
- URL: `https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz`
- Release: **Ubuntu 26.04.1 LTS "Resolute Raccoon"** (`/usr/lib/os-release`).
- Upstream SHA-256: `5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd`
  (listed in the image service `SHA256SUMS`).
- Size: `35,092,106` bytes compressed; ~105.7 MiB of file content extracted.
- Architecture: `aarch64` (ARM64).
- Licence: the image contains many components under their own licences; it is
  redistributed unmodified with its upstream `/usr/share/doc` notices intact.
  Canonical does not endorse this application; Ubuntu is a trademark of
  Canonical Ltd. The product is branded "ThothTerm Ubuntu" only to describe the
  runtime it embeds.

The embedded image is **byte-identical to upstream** (no repack). Build and
runtime both verify the pinned SHA-256 and fail closed on mismatch.

## Rootfs packaging design

The build must not commit a 35 MB binary to Git, so a cache-aware fetch script
stages it:

```
term-ubuntu/tools/prepare-assets.sh
  -> cache ${THOTHTERM_CACHE_DIR:-$HOME/.cache/thothterm}
  -> verify pinned SHA-256 (fail closed)
  -> stage into the module
```

Staged, git-ignored build inputs:

| Input | Location | Purpose |
| --- | --- | --- |
| Ubuntu rootfs | `term-ubuntu/src/main/assets/ubuntu/ubuntu-base-26.04.1-base-arm64.tgz` | embedded rootfs |
| PRoot runtime | `term-ubuntu/src/main/jniLibs/arm64-v8a/libproot.so` | runtime executable |
| PRoot loader | `term-ubuntu/src/main/jniLibs/arm64-v8a/libproot_loader.so` | PRoot's loader |
| Host libraries | `term-ubuntu/src/main/assets/runtime/arm64-v8a/{libtalloc.so.2,libandroid-shmem.so,libandroid-selinux.so}` | copied to app-private storage for `LD_LIBRARY_PATH` |
| Admin packages | `term-ubuntu/src/main/assets/sudo/*.deb` | genuine Ubuntu `sudo` + missing dependencies, installed offline on first run |

Committed provenance metadata: `term-ubuntu/src/main/assets/ubuntu/image.properties`.
Sudo package provenance and the offline provisioning design are documented in
`docs/UBUNTU_SUDO_PROVENANCE.md`.

**`.gz` asset naming gotcha:** Android's asset packager transparently expands
files ending in `.gz` and drops the suffix. The rootfs is therefore staged as
`.tgz`; `image.properties` keeps the upstream `.tar.gz` name for provenance in
the `filename` field and the packaged name in `assetName`.

Build commands:

```bash
export JAVA_HOME=/home/lordegypt/PocketCLaw/.tooling/jdk-17
export ANDROID_HOME=/home/lordegypt/Android/Sdk
export ANDROID_SDK_ROOT=/home/lordegypt/Android/Sdk
export GRADLE_USER_HOME=/tmp/thothterm-gradle-user
export PATH="$JAVA_HOME/bin:$PATH"

# Stage + verify the embedded image and runtime (network needed only the first time)
sh term-ubuntu/tools/prepare-assets.sh

# Build the ARM64 debug APK
./gradlew :term-ubuntu:assembleFullDebug

# Unit tests (JVM)
./gradlew :term-ubuntu:testFullDebugUnitTest
```

## PRoot provenance and Android execution strategy

- Technology: PRoot (rootless, ptrace-based user-space Linux runtime). No root.
- Source bundle: **ProotX support bundle v1.2.0**,
  <https://github.com/Lord1Egypt/ProotX-Assets-Support/releases/tag/v1.2.0>,
  asset `arm64-v8a-assets.zip`, SHA-256
  `42fd0042b18d8145ebb72aece000404bed8a0911c83505c1acf31e2da5033fe7`.
- PRoot lineage: `green-green-avk/proot` (a Termux-PRoot fork with Android
  adaptations), commit `7266fb3e8516535682f5a9c8f3a7e70f6506eddb`
  (tag lineage `v5.1.107.92`). Built with Android NDK 29 (`29.0.14206865`),
  "modern" lane for host API 29+.
- Licence: PRoot is **GPL-2.0**. Upstream notices are retained; see `NOTICE`.
- 16 KB pages: every ELF in this module is `LOAD`-aligned to `0x4000`
  (16 KB) or larger, verified with `readelf -lW`.

### Why it is executable on targetSdk 36

Android 10+ places untrusted apps targeting API 29 or higher in a SELinux
domain that cannot `execve()` files in their own writable data directory
(`neverallow ... app_data_file:file execute_no_trans`). It does **not** block:

- executing files extracted from `jniLibs` into `ApplicationInfo.nativeLibraryDir`
  (`apk_data_file`), and
- `mmap(PROT_EXEC)` of app-private files.

PRoot never `execve()`s the guest binaries: it intercepts `execve` and runs its
own **loader**, which maps the guest ELF with `mmap`. Therefore:

- `proot` and its `loader` are shipped as fake native libraries
  (`libproot.so`, `libproot_loader.so`) and executed from `nativeLibraryDir`.
- the Ubuntu rootfs and all guest binaries live in app-private storage and are
  mapped, not executed.
- `PROOT_LOADER` points PRoot at the loader's `nativeLibraryDir` path.
- `LD_LIBRARY_PATH` points at the app-private support-library directory
  (PRoot links `libtalloc.so.2` and `libandroid-shmem.so`).

No `targetSdk` was lowered. This mirrors the approach used by maintained
PRoot-based apps on modern Android (for example UserLAnd).

## First-run extraction

Layout under application-private storage:

```text
files/
  linux/
    ubuntu-26.04/
      rootfs/            # final rootfs (only present when complete)
      rootfs.staging/    # crash-safe staging (removed on success)
      state.properties   # completion manifest
    runtime/
      lib/               # libtalloc.so.2, libandroid-shmem.so, libandroid-selinux.so
      tmp/               # PROOT_TMP_DIR
```

Flow (`RootfsManager`):

1. Read `assets/ubuntu/image.properties`.
2. Copy runtime host libraries to `runtime/lib`.
3. Delete any stale staging directory; extract into `rootfs.staging`.
4. Verify the SHA-256 of the compressed stream against the pinned value.
5. Require `usr/bin/bash` and `etc/os-release` in staging.
6. Prepare `/home/thoth` (copy `/etc/skel`, append the prompt), write
   `/etc/profile.d/thothterm-ubuntu.sh` and `/etc/hostname`.
7. Delete any incomplete `rootfs`, atomically rename `rootfs.staging` to
   `rootfs`, and only then write `state.properties` with `complete=true`.

The presence of the `rootfs` directory alone is never treated as proof of a
valid installation; `isReady()` requires a complete state manifest matching the
current image id and SHA-256 plus sentinels.

### Safe archive extraction

`TarballExtractor` is a small Android-free tar reader (ustar/GNU/pax) used so the
safety logic can be unit-tested:

- rejects absolute names, `..` components, and NUL bytes; such entries are
  refused and counted (`SECURITY` log);
- verifies each 512-byte header checksum;
- writes only after resolving the destination parent's canonical path and
  confirming it is inside the extraction root (defeats symlink-parent escapes);
- preserves directories, regular files, symlinks, hardlinks, and permission
  bits; skips device/FIFO entries;
- drops setuid/setgid/sticky bits (meaningless in a rootless app and a needless
  risk);
- ignores unknown pax records and handles GNU long names and pax `path`/`linkpath`.

Validated against the real Ubuntu tarball: 6,564 entries, 0 rejected, symlinks
and coreutils hardlinks preserved, `usr/bin/bash` present.

## Runtime launch

`UbuntuRuntime` builds the command; `UbuntuTermSession extends ShellTermSession`
plugs it into the existing PTY/session engine. No new PTY or session code.

```text
nativeLibraryDir/libproot.so
  --rootfs=<files>/linux/ubuntu-26.04/rootfs
  --root-id --link2symlink --cwd=/home/thoth --hangup-on-exit
  --kernel-release=6.1.0-thothterm
  --bind=/dev --bind=/proc --bind=/sys --bind=/proc/mounts:/etc/mtab
  --bind=<files>/linux/runtime/resolv.conf:/etc/resolv.conf
  /usr/bin/su -m -s /bin/bash thoth
```

`--hangup-on-exit` hangs the session up like a terminal when the shell
exits, and nohup'd jobs survive. Provisioning uses `--kill-on-exit` instead.
See `docs/garden/SESSION_LIFECYCLE.md`.

Environment: `HOME=/home/thoth`, `USER=thoth`, `LOGNAME=thoth`,
`TERM=<configured>`, a Linux `PATH`, `LANG=C.UTF-8`, `TMPDIR=/tmp`,
`PROOT_TMP_DIR`, `PROOT_LOADER`, `LD_LIBRARY_PATH`. The Android `ENV`,
`LD_PRELOAD`, and the full environment are not inherited.

Only `/dev`, `/proc`, `/sys`, and `/proc/mounts` are bound. The whole Android
`/data` is deliberately **not** bound.

## HOME, user, and prompt

- `HOME` / working directory: `/home/thoth`.
- A login bash starts there, so the prompt is `thoth@android:~$` (the
  `etc/profile.d` and `.bashrc` set the label; `/etc/hostname` is `android`).
- No Android private path (`/data/user/0/...`) appears in the normal prompt.

**Fake-root disclosure.** PRoot runs with `--root-id` (fake uid/gid 0) because
package managers and maintainer scripts expect root, and because the app cannot
`chown` a rootfs it extracted. This is user-space emulation inside a rootless
Android application: it grants no host privileges, does not modify the Android
system, and does not escalate beyond the app's own sandbox. The `thoth@` prompt
is a presentation label for the configured user, not a claim of real root.

## Diagnostics integration

All events use the existing `ThothLog` categories reserved for the Linux phase:

- `ROOTFS` — image detected, extraction started/complete, failures.
- `PROOT` — runtime launch requested/started, failures.
- `LINUX` — shell started/exited.
- `SECURITY` — rejected unsafe archive entries, checksum mismatch.
- `RUNTIME` — reserved for the runtime lifecycle follow-up.

Terminal input, output, commands, environment values, and secrets are never
logged. Extraction progress is logged by count only (`ROOTFS DEBUG`).

## Networking

Networking is intentionally **not** a milestone requirement. The Ubuntu image is
present and the shell works locally. Basic DNS/network hardening (and any
`resolv.conf` handling) is deferred; `apt update` is not a blocking acceptance
criterion.

## Bind mounts and the future shared folder

`/home/thoth/shared` is reserved for a future bind mount to
`Download/ThothTerm/` on Android shared storage. The runtime architecture
already supports adding a `--bind` for it; this milestone does not implement the
external-storage permission flow, and the Linux `HOME` stays app-private.

## Recovery

- First-run failure: the setup screen offers **Retry** and **View logs**; no
  state is marked ready, and a partial staging directory is never used.
- Full reset: Android Settings → Apps → ThothTerm Ubuntu → Storage → Clear
  data, or `adb shell pm clear com.thothterm.ubuntu`. This removes the extracted
  rootfs and state; the next launch re-extracts from the APK.
- A failed checksum always aborts before the rootfs is finalized.

## Known limitations

- v0.1.0 is a development build; release signing is not configured.
- Only `arm64-v8a` is packaged. Non-arm64 devices are rejected at startup.
- The terminal source includes `PRoot`/Ubuntu; the APK is ~40.8 MB and the
  extracted rootfs is ~106 MB.
- Android 16 KB page devices: the runtime and guest binaries are 16 KB aligned;
  the ProotX note that the *whole* support bundle is not yet declared 16 KB
  compatible is respected by shipping only the modern arm64 lane.
- Networking/DNS, the shared folder, GUI/desktop, and systemd are out of scope.
  This starts a shell environment, not a booted machine.
- Localized launcher labels still read "ThothTerm" in some locales; the default
  (English) label is "ThothTerm Ubuntu".
- Application-layer code is duplicated from `term` for this milestone; a shared
  library extraction is a later, separate refactor.

## Device acceptance checklist

1. Install ThothTerm Ubuntu alongside ThothTerm Terminal Emulator.
2. Both apps coexist (distinct application IDs).
3. Open ThothTerm Ubuntu with network disabled.
4. Welcome screen appears.
5. Local preparation starts without downloading Ubuntu.
6. Rootfs extraction completes.
7. Terminal opens automatically.
8. `cat /etc/os-release` reports Ubuntu 26.04.
9. `uname -m` reports `aarch64`.
10. `pwd` begins in `/home/thoth`.
11. No `/data/user/0/...` path appears in the normal prompt.
12. `bash --version` works.
13. `apt --version` works.
14. Extra Keys work.
15. Arabic/RTL rendering still works.
16. New Window opens a second Ubuntu shell.
17. New Window does not re-extract the rootfs.
18. Closing and reopening the app skips extraction.
19. Diagnostics contains ROOTFS/PROOT/LINUX events.
20. Logs contain no terminal commands/output/secrets.
21. The terminal-emulator app remains independently installed and functional.
22. No network is needed for first boot.
