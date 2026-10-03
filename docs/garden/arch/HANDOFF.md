# ThothTerm Rolling (Arch edition) — handoff, 2026-09-28

State of the one-shot "Arch edition" task at the moment work stopped (weekly
usage limit). Nothing is tagged or released yet. Read this first, then
`docs/branding/arch/TRADEMARK.md`.

**Status: ARCH GITHUB GOLDEN — not yet. ARCH F-DROID — not yet.**

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
  re-extracted over /home/thoth. (Incomplete: any other failed readiness probe
  still re-extracted over /home; replaced by the lifecycle in
  `docs/garden/ROOTFS_LIFECYCLE.md`.)
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
- Unit tests at last run: garden-common 260, garden-debian 20, garden-arch 27
  per flavour (only the provenance-doc test failed, expected until the release
  rootfs doc exists), emulatorview 14.

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

1. Re-run `tests/garden-arch/device/gate.sh com.thothterm.arch.qa.<name> <candidate> <stale>`; fix until
   0 FAIL. Write `docs/garden/arch/PACKAGE_MANAGER.md` from the results.
2. Corresponding source: finish `collect-sources.py` (trial was running, 0
   unresolved so far); publish per-package `*.source.tar.gz` + `SOURCES.tsv`
   with the rootfs release.
3. App acceptance on the phone with a **minified release** build (debug-signed,
   `install -r`): terminal smoke (Ctrl-C, CTRL reset, resize, rotation override,
   pinch/menu zoom, extra keys, clipboard incl. Arabic, left-edge selection,
   multiple windows, background/resume, notification tap, restart, busy-close
   warning, nohup, Exit leaves no processes), File Bridge phone + browser
   (sessions A/B, Unicode names, 150 MB, cancel, LAN-off), LAN browser suite
   and attack suite (earlier sessions' scripts lived in the scratchpad:
   lanaccept/lanattack/chargetest/phoneup/smoke — recreate), charging
   screen-awake (record `screen_off_timeout`, original **30000**, restore),
   F-Droid flavour consent/decline/404/wrong size/wrong hash/partial/retry
   (point sourceUrl at a local test server or test before the rootfs release),
   no data loss across `install -r`.
4. **Release-day rootfs** (≤24 h before publishing): `build-rootfs.sh check`,
   then `capture` again, `build` twice (≥2, prefer 4) from a *committed*
   script, compare bytes, re-run the device gate on the final archive.
   Publish GitHub release `arch-rootfs-aarch64-<sha12>` with the archive,
   manifest, packages.tsv, scan, upstream .sig + provenance, SOURCES, SHA256SUMS;
   download back, verify; pin in distro.properties; write
   `docs/garden/arch/ROOTFS_PROVENANCE.md` (ArchEditionTest checks it).
5. Clean clone; build full/fdroid release + debug-signed test APKs; R8 (two JNI
   keeps, 6/6 natives), 16 KB (`zipalign -c -P 16 -v 4`, all ELFs 0x4000),
   full embeds the exact rootfs bytes, fdroid embeds none.
6. Annotated tag `arch-v0.1.0`; GitHub release "ThothTerm Rolling 0.1.0 —
   Garden Golden Baseline", not draft/prerelease, `--latest=false`; assets
   `ThothTerm-Rolling-v0.1.0-{full,fdroid}-{release-unsigned,test}.apk` +
   SHA256SUMS.txt; download back and verify.
7. F-Droid: fork `gitlab.com/Lord1Egypt/fdroiddata` (project 86673174), new
   branch `com.thothterm.arch`, `metadata/com.thothterm.arch.yml` from the
   template (ONE Builds entry, `Tags ^arch-v[0-9.]+$`, AutoUpdateMode Version),
   lint/rewritemeta/local buildserver-trixie CI (`ci-build.sh`), ONE New App MR
   "New app: ThothTerm Rolling (com.thothterm.arch)" with the official template,
   Squash on, no auto-merge, never merge; wait for a green pipeline.
   Do not touch !49556 (Ubuntu) or !50342 (Trixie).

## Phone state left behind

- `com.thothterm.arch` 0.1.0 debug (full, candidate #3) installed and opened;
  test files in its data: `files/ga/` (gate copies), `home/thoth/t.sh`.
- Protected apps untouched: com.thothterm, com.thothterm.devel,
  com.thothterm.ubuntu, com.thothterm.debian, PocketClaw.
- `screen_off_timeout` never changed (still 30000). A temporary deviceidle
  allowlist entry for com.thothterm.arch was added and removed again.
- Wireless adb was on 192.168.1.103:46745 (port changes per session).
