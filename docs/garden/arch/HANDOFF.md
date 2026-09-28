# ThothTerm Rolling (Arch edition) — handoff, 2026-09-28

Nothing is tagged or released yet. Read this first, then
`docs/branding/arch/TRADEMARK.md`, then **`docs/garden/arch/RELEASE_CHECKLIST.md`**,
which is now the step-by-step plan for everything that is left.

**Status: ARCH GITHUB GOLDEN — not yet. ARCH F-DROID — not yet.**
**Cloud work: complete. What remains needs the WSL box (docker+binfmt,
Android SDK/NDK, gh) and the SM-A165F.**

## Cloud session, 2026-09-28 (c49bb03 → see `git log`)

A cloud container continued from c49bb03. It has no phone and no adb, and its
network policy blocks `dl.google.com` (so no Android SDK, NDK or Android
Gradle Plugin: **no APK was built in the cloud**), every archlinuxarm.org
host and the upstream source hosts (so **no rootfs capture/build and no
source collection** in the cloud). GitHub, Maven Central, PyPI, gitlab.com
and Docker Hub were reachable. No device result below comes from the cloud.

Commits (all on `feature/arch-v0.1.0`):

- `7ce3a06` **fix(arch): collect the aarch64 sources; the freshness check can
  download.** Real bugs:
  - `collect-sources.py` ran makepkg in an x86_64 container, which fetches
    `source` + `source_x86_64`, and exported from `.SRCINFO`, which lists
    `source_<arch>` only for arches in `arch=()`. For any recipe with
    `source_aarch64` it exported the x86_64 file (or failed) instead of the
    corresponding source of the aarch64 binary. Now `CARCH=aarch64`,
    `--ignorearch`, and the export reads the PKGBUILD's `source` +
    `source_aarch64`. New `tests/garden-arch/host/collect-sources-selftest.sh`:
    7/7 PASS; the old script 5/7 (it exported x86.txt in place of arm.txt).
    **Re-run the source collection from scratch (`sources-trial2/` was made by
    the old script).**
  - `build-rootfs.sh check` ran `pacman -Sy` without `--disable-sandbox`
    (capture has it): under qemu-user pacman 7's download sandbox cannot start.
  - `build_script_revision` now says `uncommitted` when the recipe has local
    changes (it printed the last commit's hash regardless).
  - `ArchEditionTest.theSourceCollectorTakesTheAarch64Sources`.
- `656fa65` docs: the store description and the F-Droid MaintainerNotes now say
  which settings the rootfs changes besides "unmodified packages".
- `fd3d101` **release tooling**:
  - `garden-arch/rootfs/release-rootfs.py compare | pin | provenance | stage |
    verify-published`: byte-compare ≥2 builds (refuses an uncommitted
    recipe), pin exactly the 7 keys in `distro.properties`, write
    `ROOTFS_PROVENANCE.md` from the build outputs (refuses a package without
    recorded source), stage the rootfs release assets + `SHA256SUMS` and print
    the `gh` command (publishes nothing), download back and verify.
    `tests/garden-arch/host/release-rootfs-selftest.sh`: 13/13 PASS.
  - `tools/garden/release/verify-apk.py`: SDK-free APK checks — identity, not
    debuggable, arm64-v8a only, 16 KB ELF alignment (lib/ and
    assets/runtime/), zip alignment as `zipalign -c -P 16 4`, distro.properties
    byte-identical, full = exactly one stored rootfs with the pinned
    size/SHA-256, fdroid = none, the two JNI classes R8 keeps (TermIO$Native 2
    + Process$Native 5 = 7 native methods on Trixie 0.2.0), R8 ran,
    signed/unsigned. Validated on the published Trixie 0.2.0 APKs (all PASS)
    and on a doctored APK (misaligned .so and 4 KB PT_LOAD both FAIL).
- the final cloud commit: `docs/garden/arch/PACKAGE_MANAGER.md` (drafted; the
  device-gate result section is **PENDING**), `RELEASE_CHECKLIST.md`, this
  section, `tools/garden/cloud-hosttest/` (see below), and
  `tests/garden-arch/device/interrupt.sh` case 3 restores icu with `tar -xf`
  (xz or zstd detected) instead of `-xJf`.

Automated results in the cloud:

- JVM unit tests with `tools/garden/cloud-hosttest/run.sh` (Robolectric
  android-all 16 made into AGP-style mockable jars; Gradle could not run):
  emulatorview 14/14 (includes ControlStringTest, the APC fix);
  garden-common 257/257 (all but TermServiceTest's 3, not runnable without
  AppCompat; includes LanServerTest, LanUploadTest, UploadBatchTest,
  SessionDirectoryTest, UploadNamesTest = Garden File Bridge and LAN);
  garden-debian 19/20; garden-arch 27/29. The only failures are the expected
  ones: `PageAlignmentTest` (needs the NDK-built PRoot runtime) and
  `provenanceRecordsThePinnedArchive` (no `ROOTFS_PROVENANCE.md` until the
  release rootfs). Gradle must still run them locally (checklist step 1).
- shellcheck: build-rootfs.sh and its three container scripts, the device
  gate scripts, ci-build.sh — no real findings.
- `fdroid lint` and `fdroid rewritemeta` (no change) on the filled template,
  with fdroidserver 2.4.5 (PyPI) and master, against fdroiddata's current
  config (eb5ab142): clean. The template has exactly one Builds entry.
- Static audit of the pacman/keyring code (DistroInfo, GuestConfig,
  GuestProcesses, RootfsManager, RootfsState): no defect found. The keyring
  is made after the rename and state is written only after it verifies; an
  image keyring is refused; the stale `db.lck` is removed only with no PRoot
  running; sudo is reinstalled with `-S --needed`, never `-Sy`.
- Not possible in the cloud: any APK build, R8/JNI/16 KB/zipalign on Arch
  APKs (tooling is ready: checklist step 4), rootfs capture/build and
  reproducibility runs, source collection, anything on the phone.

## Where things are

| | |
|---|---|
| Branch | `feature/arch-v0.1.0` (pushed), from `trixie-v0.2.0` = a2030f2 (contains ubuntu-v0.3.0 and the newest garden-common). terminal-v1.4.0 is the separate `master` line and is untouched. |
| Package / module | `com.thothterm.arch` / `garden-arch`, 0.1.0 / 100 |
| Visible name | **ThothTerm Rolling** (trademark decision recorded in `docs/branding/arch/TRADEMARK.md`) |
| LAN port | 7683 (`docs/garden/PORTS.md`) |
| rootfs pin in `distro.properties` | **candidate** `ffb1fe11e9bc…` (not published; the release rootfs must be rebuilt, see below) |
| Local-only work area (this WSL box) | `/home/lordegypt/AndroidThothTerm/arch-rootfs-work/` — pinned inputs (upstream tarball 829 MB, 145 packages), `out1..3/` builds, `stale/` old-image tarball, gate logs, screenshots, `sources-trial2/` |
| F-Droid draft | `docs/garden/arch/fdroid/com.thothterm.arch.yml.template` + `ci-build.sh` (the local fdroiddata CI job used for Ubuntu/Trixie) |

## Done and verified

- **Trademark audit** (Arch Linux policy 2021-04-18: any mark beginning ARCH
  needs permission; combined marks unlikely) -> neutral name ThothTerm Rolling,
  factual "Arch Linux ARM AArch64 environment", non-affiliation notice, no Arch
  artwork, blue kept away from #1793D1.
- **Branding**: Garden masters hue-rotated −122° (`tools/garden/branding/recolor.py`),
  resources via `make_resources.py`, palette via `palette.py --hue-shift -122
  --muted-rim 0.70` (xterm 69/111/68/255/103). Tests regenerate and compare bytes.
- **garden-common** (commit 1f843a4): `packageManager=pacman`, `adminGroup=wheel`,
  `pacmanKeyring`, `packageSigningKey` in distro.properties (Debian defaults
  unchanged). First run creates the installation's own keyring
  (`pacman-key --init`, `--populate archlinuxarm`), proves the Build System key
  is fully valid and a genuine signed package (`/usr/share/thothterm/signature-check/`)
  verifies, **before** setup is marked complete. Image keyring refused. Stale
  `db.lck` removed only when no PRoot of the app runs (`GuestProcesses`).
  sudo via pacman (never -Sy). No debconf on pacman guests.
- **Data-loss fix** (same commit): `isReady()` no longer needs the install to
  match the current pin — a future app with a newer rootfs pin would have
  re-extracted over /home/thoth.
- **Shared tests** (a167bc3): PageAlignmentTest and TerminalZoomGestureTest
  moved to `garden-common/src/editionTest` / `editionAndroidTest`; Trixie uses them too.
- **emulatorview fix** (1a15273): DCS/SOS/PM/APC strings are consumed. Found on
  the phone: Arch's bash.bashrc sets the title with APC for TERM=screen and the
  engine drew `thoth@localhost:~` before every prompt. Also fixes Unicode in
  OSC titles. `ControlStringTest` (5/6 fail on the old engine).
- **Full-flavour staging fix** (e231ee9): stageRootfs empties its directory
  (an incremental build had embedded two archives).
- **rootfs builder** `garden-arch/rootfs/build-rootfs.sh`:
  - upstream `ArchLinuxARM-aarch64-latest.tar.gz` (829,367,415 B, sha256
    42a4eeaa…b319, Last-Modified 2026-08-05 12:41:36 GMT) verified with gpgv:
    VALIDSIG 68B3537F39A313B3E574D06777193F152BDBE6A6 (key pinned from
    archlinuxarm/archlinuxarm-keyring @ 91e6b116, sha256 50a08f82…c518); a
    truncated copy gives BAD signature.
  - method: upstream image = arm64 docker builder (qemu binfmt); throwaway
    keyring; kernel/firmware/mkinitcpio removed from the builder with pacman;
    builder -Syu; pacstrap-style `pacman -r /target -S base archlinuxarm-keyring
    sudo ca-certificates curl`; second -Su must say "nothing to do"; -Dk, -Qk,
    ldd checks; no keyring/sync DBs/cache/lock/log shipped; machine-id empty;
    all accounts locked; INSTALLDATE and mtimes clamped; GNU tar sorted, gzip -9 -n.
  - `capture` (network, retries) then `build` (offline, `--network none`).
  - **Reproducible**: build #1 and #2 from the same inputs byte-identical
    (3a9d40da61f0…, 199,615,366 B, 674,027,520 B uncompressed, 138 packages).
  - Candidate #3 `ffb1fe11e9bc…` (199,616,962 B) adds the pacman.conf line below.
- **Android pacman finding**: pacman 7's downloader sandbox fails on the phone
  ("Landlock is not supported by the kernel"). Tested each option on the
  SM-A165F: only `DisableSandboxFilesystem` is needed; seccomp filter and
  DownloadUser=alpm work and stay on. The image sets exactly that (tested by
  `ArchEditionTest`). Signature checking untouched.
- **Phone (SM-A165F, Android 16, kernel 6.12)**: debug full build with candidate
  #3 installed as a NEW package `com.thothterm.arch` (no user data of value).
  Real first run: extraction 26 s (1821 hard links copied), in-app keyring
  provisioning 8 s, terminal opens, banner/prompt/extra keys blue.
  Guest config checked: `wheel:x:998:thoth`, sudoers, sudo setuid, state complete.
- **Device pacman gate** (`tests/garden-arch/device/gate.sh`, runs as the app
  via run-as; needs a terminal session open so Android lets the app uid use the
  network): 55 PASS / 14 FAIL on the last run. PASS: first-run keyring, populate
  with no trust errors, -Syu, second -Syu nothing to do, install/reinstall/
  remove tree, -Dk/-Qk clean (138 pkgs, 0 missing), restart + jq transaction,
  sudo -n id -un = root, DNS, HTTPS, compression round trips, hard/sym links,
  patch 0003 exe identity, UTF-8, ldd of pacman/bash/sudo/curl/gpg/tar/zstd/xz/ls.
  Stale image (upstream 2026-08-05 userland) came forward with one -Syu
  (54 packages; keyring 20260909, openssl 3.6.4, systemd 262).
  The 14 FAILs were harness bugs or the interrupt design, all fixed in 978389e
  but **not yet re-run**: C (curl did not follow the mirror's 302), G (bash hash),
  interrupt test (it killed an `icu` reinstall, which breaks pacman itself —
  now case 3 with a GNU-tar recovery), stale (openssh hook "systemd not PID 1"
  line is now INFO).
- Unit tests at the last local Gradle run: garden-common 260, garden-debian
  20, garden-arch 27 per flavour (only the provenance-doc test failed, expected
  until the release rootfs doc exists), emulatorview 14. Cloud results: above.

## Known, documented, not blocking

- PRoot patch 0003 returns `/dir/./name` for a *relative* invocation through a
  hard link (absolute and PATH are exact; same file and basename). Not changed.
- Before the first `pacman -Syu`, pacman prints "database file for 'core' does
  not exist" (no sync DBs are shipped, like Arch's own container image). The
  first command is `sudo pacman -Syu`. Document in PACKAGE_MANAGER.md.
- systemd 262 ships `tmpfiles.d/root.conf` (`z / 555`): archive root is 0555;
  the app's extractor rejects the `./` entry, so an installed root stays writable.
- Size: Arch `base` is 674 MB installed / 200 MB download (glibc, binutils via
  pacman deps, systemd, icu, glib2, locale files). Kept for pacman consistency.
- A package hook that calls systemctl (e.g. openssh's) prints "System has not
  been booted with systemd" — PRoot model, harmless.
- Harness only: Android blocks network for an app with no foreground process
  (Android 16 APP_BACKGROUND); run the gate with a terminal open.

## Remaining work, in order

All of it is in `docs/garden/arch/RELEASE_CHECKLIST.md` with exact commands:

1. Local: Gradle unit tests (step 1) and the two host self-tests.
2. Phone: install the latest candidate build; title/APC regression; re-run
   `tests/garden-arch/device/gate.sh <candidate> <stale>` until `FAIL: 0`
   (the 14 earlier FAILs were harness bugs fixed in 978389e, never re-run).
3. Local: release-day rootfs — `check`, `capture`, `build` ×4,
   `release-rootfs.py compare`, `collect-sources.py` from scratch (0
   UNRESOLVED), `release-rootfs.py provenance` + `pin`, gate on the final
   archive, fill PACKAGE_MANAGER.md's PENDING section, commit, `stage`,
   publish `arch-rootfs-aarch64-<sha12>`, `verify-published`.
4. Local: clean clone, full/fdroid release + debug-signed test APKs,
   `verify-apk.py` on all four, SHA256SUMS.
5. Phone: full acceptance with the minified test build (terminal, File
   Bridge phone+browser, LAN browser + attack suites, charging screen-awake
   with `screen_off_timeout` 30000 restored, F-Droid consent/failure cases,
   no data loss across `install -r`).
6. Only then: tag `arch-v0.1.0`, GitHub release, F-Droid MR (one Builds
   entry, Squash on, never merge; do not touch !49556 or !50342).

## Phone state left behind

- `com.thothterm.arch` 0.1.0 debug (full, candidate #3) installed and opened;
  test files in its data: `files/ga/` (gate copies), `home/thoth/t.sh`.
- Protected apps untouched: com.thothterm, com.thothterm.devel,
  com.thothterm.ubuntu, com.thothterm.debian, PocketClaw.
- `screen_off_timeout` never changed (still 30000). A temporary deviceidle
  allowlist entry for com.thothterm.arch was added and removed again.
- Wireless adb was on 192.168.1.103:46745 (port changes per session).
