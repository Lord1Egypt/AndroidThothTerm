# ThothTerm Rolling 0.1.0 — release checklist

`com.thothterm.arch`, `garden-arch`, 0.1.0 / 100, tag `arch-v0.1.0`. Every
step says where it runs. **Cloud** steps are done (see `HANDOFF.md` for the
evidence); **Local** steps need the WSL box (docker + aarch64 binfmt, the
Android SDK/NDK, gh) and **Phone** steps the Samsung SM-A165F over wireless
adb. Nothing is tagged, published or submitted until every Phone gate is PASS.

Paths are relative to the repository root. `W=arch-rootfs-work` is the
local-only work area (untracked).

## 0. Start (Local)

    git fetch origin feature/arch-v0.1.0 && git checkout feature/arch-v0.1.0 && git pull
    git log --oneline -8          # the HANDOFF.md "ending commit" must be here
    git status --porcelain        # empty

## 1. Host checks (Local; Cloud ran the JVM part, see HANDOFF.md)

    ./gradlew :emulatorview:testDebugUnitTest :garden-common:testDebugUnitTest \
              :garden-debian:testFullDebugUnitTest :garden-arch:testFullDebugUnitTest :garden-arch:testFdroidDebugUnitTest
    tests/garden-arch/host/collect-sources-selftest.sh      # 7/7 PASS (docker)
    tests/garden-arch/host/release-rootfs-selftest.sh       # 13/13 PASS

Expected before step 3: only `ArchEditionTest.provenanceRecordsThePinnedArchive`
fails (no `ROOTFS_PROVENANCE.md` yet). `PageAlignmentTest` needs the PRoot
runtime built, so run it after a build (`./gradlew :garden-arch:assembleFullDebug` first).

## 2. Candidate gates (Phone)

1. Build and install the current candidate (debug, full, pin `ffb1fe11e9bc`):
   `./gradlew :garden-arch:assembleFullDebug`, `adb install -r
   garden-arch/build/outputs/apk/full/debug/garden-arch-full-debug.apk`.
2. **Title/APC regression** (emulatorview fix 1a15273): open a terminal; the
   prompt must not be preceded by `thoth@localhost:~` or any other title text;
   `printf '\033_hidden\033\\visible\n'` prints only `visible`;
   `printf '\033]0;Ünïcødé\007'` sets the title without drawing it.
3. **Device gate rerun** (the previously failing 14): keep a terminal window
   open, then
   `tests/garden-arch/device/gate.sh $W/out3/thothterm-arch-aarch64-rootfs-ffb1fe11e9bc.tar.gz $W/stale/<old-image>.tar.gz`
   — must end `FAIL: 0`. Fix anything else before going on.

## 3. Release-day rootfs (Local, ≤ 24 h before publishing)

    cd garden-arch/rootfs
    INPUTS=$W/inputs ./build-rootfs.sh check          # which pinned packages moved
    INPUTS=$W/inputs ./build-rootfs.sh capture        # fresh inputs (network)
    for n in 1 2 3 4; do INPUTS=$W/inputs OUT_DIR=$W/rel$n ./build-rootfs.sh build; done
    ./release-rootfs.py compare $W/rel1 $W/rel2 $W/rel3 $W/rel4   # identical, committed recipe
    BUILDER_DIGEST=<archlinux:base-devel digest> \
        ./collect-sources.py $W/inputs $W/rel1/<name>.packages.tsv $W/sources   # must exit 0: no UNRESOLVED
    ./release-rootfs.py provenance --inputs $W/inputs --sources $W/sources $W/rel1 $W/rel2 $W/rel3 $W/rel4
    ./release-rootfs.py pin $W/rel1
    cd ../..

Then:

1. Re-run step 1 (all unit tests now pass, including the provenance test).
2. Re-run the device gate (step 2.3) on `$W/rel1/<name>.tar.gz`: `FAIL: 0`.
   Write its result into `docs/garden/arch/PACKAGE_MANAGER.md` (replace the
   PENDING section and the status note).
3. Commit `distro.properties`, `ROOTFS_PROVENANCE.md`, `PACKAGE_MANAGER.md`
   and push.
4. `garden-arch/rootfs/release-rootfs.py stage --inputs $W/inputs --sources $W/sources $W/rel1 $W/stage`
   and publish with the printed `gh release create arch-rootfs-aarch64-<sha12> …`
   (`--target` = the commit from 3; not draft, not prerelease, `--latest=false`).
5. `garden-arch/rootfs/release-rootfs.py verify-published $W/rel1` — 3 PASS.

## 4. Clean-clone release builds (Local)

    git clone --recurse-submodules --branch feature/arch-v0.1.0 \
        https://github.com/Lord1Egypt/AndroidThothTerm.git /tmp/arch-clean && cd /tmp/arch-clean
    ./gradlew :garden-arch:assembleFullRelease :garden-arch:assembleFdroidRelease
    ./gradlew :garden-arch:testFullReleaseUnitTest :garden-arch:testFdroidReleaseUnitTest

The full build downloads the pinned archive from the release in step 3.4 and
refuses anything but the pinned bytes. Name the outputs
`ThothTerm-Rolling-v0.1.0-{full,fdroid}-release-unsigned.apk`, and make the
test builds from them with the debug key:

    apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
        --out ThothTerm-Rolling-v0.1.0-full-test.apk ThothTerm-Rolling-v0.1.0-full-release-unsigned.apk
    (same for fdroid)

Verify all four (R8/JNI, 16 KB ELF and zip alignment, rootfs embedding,
identity, signing):

    D=garden-arch/src/main/assets/garden/distro.properties
    V="tools/garden/release/verify-apk.py --distro $D --package com.thothterm.arch --version-name 0.1.0 --version-code 100 --minified"
    python3 $V --flavour full   --unsigned ThothTerm-Rolling-v0.1.0-full-release-unsigned.apk
    python3 $V --flavour fdroid --unsigned ThothTerm-Rolling-v0.1.0-fdroid-release-unsigned.apk
    python3 $V --flavour full   --signed   ThothTerm-Rolling-v0.1.0-full-test.apk
    python3 $V --flavour fdroid --signed   ThothTerm-Rolling-v0.1.0-fdroid-test.apk
    zipalign -c -P 16 -v 4 <each apk>      # second opinion (verify-apk runs it too when on PATH)
    sha256sum ThothTerm-Rolling-v0.1.0-*.apk > SHA256SUMS.txt

Each must end `0 failed`. Record sizes and SHA-256 in `HANDOFF.md`.

## 5. Phone acceptance with the minified test build (Phone)

`adb install -r ThothTerm-Rolling-v0.1.0-full-test.apk` over the candidate
(no data loss: `/home/thoth/t.sh` and installed packages survive; the install
is **not** re-extracted although the pin changed — `RootfsState.isInstalled`).
Then a fresh install (uninstall first) of the same build: first run, keyring,
terminal.

- Terminal: Ctrl-C, CTRL-key reset, resize, rotation override, pinch and menu
  zoom, extra keys, clipboard including Arabic, left-edge selection, multiple
  windows, background/resume, notification tap, restart, busy-close warning,
  `nohup`, Exit leaves no processes.
- File Bridge on the phone (SAF upload into the current directory) and in the
  browser: sessions A/B, Unicode names, 150 MB, cancel, LAN off mid-transfer.
- LAN browser suite and attack suite (recreate lanaccept/lanattack from the
  Trixie session's scripts): PIN pairing, per-browser terminals, no mobile
  data/Internet bind, off ends access.
- Charging screen-awake: record `settings get system screen_off_timeout`
  (was **30000**), test, restore it.
- F-Droid flavour (`fdroid-test`): consent, decline, 404, wrong size, wrong
  hash, partial download, retry — point `sourceUrl` at a local test server in
  a scratch build, or test against the published release from step 3.4.
- Garden File Bridge and LAN host tests already pass (Cloud: LanServerTest,
  LanUploadTest, UploadBatchTest, SessionDirectoryTest, UploadNamesTest).

## 6. Tag and GitHub release (Local, only after 2, 3 and 5 all PASS)

    git tag -a arch-v0.1.0 -m "ThothTerm Rolling 0.1.0" <commit of step 4> && git push origin arch-v0.1.0
    gh release create arch-v0.1.0 --latest=false --title "ThothTerm Rolling 0.1.0 — Garden Golden Baseline" \
        --notes-file <notes> ThothTerm-Rolling-v0.1.0-*.apk SHA256SUMS.txt

Not draft, not prerelease. Download every asset back and `sha256sum -c`.

## 7. F-Droid (Local, only after 6)

1. Fork `gitlab.com/Lord1Egypt/fdroiddata`, branch `com.thothterm.arch`.
2. `metadata/com.thothterm.arch.yml` from
   `docs/garden/arch/fdroid/com.thothterm.arch.yml.template`: `@COMMIT@` = the
   full hash `arch-v0.1.0` resolves to, `@ROOTFS_TAG@/SIZE/SHA@` from
   `distro.properties`. Exactly **one** `Builds` entry (0.1.0 / 100).
3. `fdroid lint com.thothterm.arch` and `fdroid rewritemeta com.thothterm.arch`
   (no change) — Cloud verified both on the template with fdroidserver 2.4.5
   and master; then the local buildserver job
   (`APP=com.thothterm.arch VC=100 docs/garden/arch/fdroid/ci-build.sh`).
4. One MR "New app: ThothTerm Rolling (com.thothterm.arch)" with the official
   template, **Squash commits on**, no auto-merge, never merge; wait for a
   green pipeline. Do not touch !49556 (Ubuntu) or !50342 (Trixie).
