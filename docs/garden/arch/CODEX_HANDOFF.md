# ThothTerm Rolling — local Codex continuation

Updated 2026-09-30 after the fresh rootfs, clean release build, and final
binary checks. Base **c49bb03**; branch
`codex/arch-v0.1.0-from-c49bb03`; code HEAD before this handoff update
**7a5b4f94ace531e152e2f9050dae91c7aee13ba4**. Run `git rev-parse HEAD`
for the exact current commit (a handoff file cannot contain its own commit hash).
The later cloud Arch branch was not used as an implementation baseline. This
is a candidate, not a release. No tag, GitHub release, F-Droid MR, or
BlackArch work was created.

## Current state (supersedes older candidate notes below)

- Fresh September 30 rootfs `03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1`,
  199,826,020 bytes, is pinned in `distro.properties`. Official source,
  signature, inputs, normalization, and package state are recorded in
  `ROOTFS_PROVENANCE.md`. Two independent offline builds from equivalent clean
  captured inputs were byte identical. The fresh physical device package gate
  passed **78 PASS / 0 FAIL** (`/tmp/codex-arch-gate-fresh-final.log`).
- Source collection is complete: 138 installed binary rows, 120 unique source
  archives, `SOURCES.tsv` SHA-256
  `0b55a1198a6bd0881f6badfd2fca3104ae1ddada0463913e59254bd6e811efee`.
  Commit `129a63c` handles an upstream NSS local-file checksum ordering defect
  by matching every original BLAKE2 checksum, then verifying the corrected
  order with `makepkg --verifysource`; its tests pass 2/2.
- Arch Full and F-Droid unit tests pass **28/28 each** on the final pin. A
  detached clean checkout at `/tmp/thoth-arch-release-clean` built Full debug,
  Full/F-Droid release APKs, and both AABs with 316 Gradle tasks successful.
  Its Git worktree remained clean. Release APKs are intentionally unsigned.
- Clean release SHA-256: Full APK
  `b0c5d2653ccec83d34d726115efcbe13a77a667063392a1defda0729d9cf5ab0`,
  F-Droid APK
  `26798cd4085e43e66ddca487010a1d920108e4dbb85f393d31076a5e4df1f87c`,
  Full AAB
  `96d2088fe217f40e3c8c24c72d1faad3e6ddb6c2f9d7902ce5fc8961d64acc81`,
  F-Droid AAB
  `16f87c56e32a82ce67f7888063ede124d96e2bb89ce3300a7321637ccc17d4c4`.
  APKs have `com.thothterm.arch` 0.1.0/100, only `arm64-v8a` packaged JNI
  libraries, and pass `zipalign -c -P 16 -v 4`. Every packaged ELF LOAD
  alignment is `0x4000`. Full APK/AAB embed the exact pinned rootfs SHA;
  F-Droid APK/AAB contain none. R8 output has all seven declared and
  registered JNI methods with their original class/method names and signatures.
- Candidate minified Full and F-Droid ran physically in isolated
  `com.thothterm.arch.codexprobe`, including pacman 7.1.0. Phone File Bridge
  file, Arabic folder, exact hashes, and picker cancellation passed. The
  charging foreground terminal held `FLAG_KEEP_SCREEN_ON`; the original
  `screen_off_timeout` was restored to **30000 ms**.
- New final pinned probes are built but await ADB reconnection:
  `/tmp/thoth-arch-final-full-probe` Full debug SHA
  `608bb64808d67fde765e42791cbbbfe8fb961218f0aed61914155373589ed4fa`
  and `/tmp/thoth-arch-final-fdroid-probe` F-Droid debug SHA
  `18875b615fa881ab46861ae6170ca122c7e02de5213a4a19a8789a0ff79309e2`.
  Their separate application IDs cannot replace primary Rolling. The user
  reported new ADB port `192.168.1.103:34445`. The ADB 37 diagnostic server
  reached that service but its TLS handshake ended with
  `SSLV3_ALERT_CERTIFICATE_UNKNOWN`: the phone rejects this computer's saved
  pairing certificate. A new wireless debugging pairing code was requested.
- The user's `pacman -Syu` photos show coreutils 9.12-2 installed and a
  returned prompt. Warnings about setting `0777` permissions on
  `LC_TIME/coreutils.mo` are for package symlinks to `LC_MESSAGES/coreutils.mo`,
  confirmed in the signed package. PRoot cannot apply that symlink metadata.
  All 251 installed regular file hashes and 46 symlink targets matched the
  signed package before the later APK install incident. This is a known PRoot
  model limitation, not evidence of a failed pacman transaction.
- The Codex branch was pushed to `origin` with a normal non-force push at
  handoff commit `2e1f3d4`; no other branch or tag was pushed.
- **Release blocker:** the primary Rolling HOME was lost during the Android
  incremental-install incident documented below. Do not replace primary
  Rolling again until the cause and backup/recovery status are understood.
  The user has not yet answered the backup question. No release/tag/MR was made.

## Work completed

- Located a clean `wt-arch`, ran the requested status/branch/log checks,
  confirmed `c49bb03`, fetched refs/tags, detached at that exact commit, and
  created the isolated branch. The protected product branches and apps were
  not changed.
- Read the checkpoint handoff and Arch docs, audited APC/title handling,
  HOME preservation, keyring, pacman sandbox, PRoot patches, File Bridge,
  rootfs builder, source collection, F-Droid split, R8/JNI, and device gate.
- Built Full debug and Full/F-Droid minified release APKs with the local pinned
  JDK 17, Gradle 9.7.1, AGP 9.4.0, SDK 36, NDK 23.2.8568313. The emulatorview
  suite passed 14/14; garden-common 260/260 after commit `0fb7c35` isolates
  its colour test from the host's `NO_COLOR=1`. Arch unit tests: 27/28; the
  remaining provenance test requires the final fresh rootfs document.
- Installed the Full debug APK on the Galaxy A16 via `adb install -r` without
  clearing data. First extraction/keyring setup succeeded. New screenshots
  `/tmp/thoth-codex-apc.png`, `/tmp/thoth-title-active.png`, and
  `/tmp/thoth-title-cleared.png` show one clean prompt, no raw APC payload,
  Arabic OSC title `عنوان عربي`, and the `Window ١` fallback after title clear.
  No tap/resize was used to conceal corruption. Old `x@y:~` screenshot
  `arch-rootfs-work/shots/05-osc.png` shows `bash t.sh`; the old script is
  unavailable, so its exact bytes remain unproven. The new build does not keep
  that title stale.
- Created `/home/thoth/codex-home-preservation-sentinel`; `adb install -r`
  of the same APK preserved it. A changed-pin F-Droid APK from
  `/tmp/thoth-arch-pin-probe` was installed with `adb install -r`; the primary
  rootfs state, `installedAt=1790730892546`, and sentinel SHA-256
  `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
  survived. Reinstalling the normal Full debug APK preserved them again.
  Do not clear Rolling data.
- The complete strict candidate package-manager gate on disposable app-owned
  roots passed **76 PASS / 0 FAIL** (`/tmp/codex-arch-gate-final-candidate.log`).
  It checked signed provisioning, two `pacman -Syu` runs, package integrity,
  install/remove/reinstall, DNS/TLS, sudo, restart, interrupted transactions,
  and the stale-image update. The earlier 14 failures were harness bugs or
  classified PRoot model limitations; no product failure remains in this gate.
  Commits `58172ff`, `fcf9cd8`, and `e12eb12` harden it. The old-image update
  emitted 6404 symlink chmod warnings; all links resolve, `pacman -Qk` found
  zero missing files, and only two expected `.pacnew` files plus the inherited
  journal directory mode remained. The stronger warning check was run directly
  on that disposable root and is committed for the final-rootfs rerun.
- Source collector: `b3bcc6e` selects common plus `source_aarch64` with
  `CARCH=aarch64`; `5a15454` archives Mercurial sources; `0c4dd6e` retries
  transient fetches; `03347ba` reproduces the checksum-pinned zlib patch from
  its immutable upstream commit. The source-selection regression test passes.
  Old invalid source archives were discarded. A clean candidate recollection
  was interrupted when local Docker disappeared. It was restarted after Docker
  recovered in `/tmp/codex-sources-candidate-retry.log` and
  `arch-rootfs-work/sources-codex-candidate/`; it is fetching GCC's large git
  history. Check its process and log. Final-rootfs sources must be collected
  again from their own package state.
- Added `PACKAGE_MANAGER.md` with the signed-keyring, narrow Landlock, and
  interrupted-transaction model. Commit `1d5d147` fixed the rootfs builder
  freshness check under QEMU. Its live check at 2026-09-30 02:17:08 UTC found
  all pinned key packages unchanged but 208 repository entries moved; a fresh
  final capture is required.
- Built an isolated `com.thothterm.arch.codexprobe` F-Droid debug APK in
  `/tmp/thoth-arch-fdroid-test` for local HTTPS consent/retry tests. Its test
  CA and `adb reverse` URL are confined to that temporary package; production
  code and Rolling data remain unchanged. It is not installed yet.

## Candidate binaries and phone state

- Phone: SM-A165F, Android 16/API 36, arm64, ADB last seen at
  `192.168.1.103:43977` (discover again; port can change). It is AC charging.
  The terminal window had `FLAG_KEEP_SCREEN_ON` while foreground. LAN Mode is
  now off. The original system `screen_off_timeout` is **30000 ms**; it was
  temporarily set to 600000 for the browser gate and restored to **30000**.
  The phone subsequently locked while the LAN settings screen was in front;
  the user has been asked to unlock it again. Recheck the terminal window's
  flag once it is unlocked and foreground.
- Full debug APK SHA-256:
  `849c870c80ee6d8165a22078c0c4772cc0acdc86deccd3fbae68efd7b9e7f377`.
- Candidate rootfs SHA-256 (September 28, **not fresh final**):
  `ffb1fe11e9bc7ededff40fbe90b6bc6e3f0cb0fd03babeb2d381bd0fcac6c584`,
  199,616,962 bytes.
- Unsigned minified release APKs: Full SHA-256
  `97f30470ec9ea6fdb202885c58cc612ca7de277ef9349a19d4c605bb96439c50`;
  F-Droid SHA-256
  `b12ac31843a8a8bd96153c15d3fc278e9a646d4bcb627e7b73d92247efbb464f`.
  Full embeds exactly the candidate rootfs; F-Droid embeds none. Both have
  package `com.thothterm.arch`, version 0.1.0/100, arm64-v8a, pass
  `zipalign -c -P 16 -v 4`; native ELF LOAD alignment is `0x4000`. Actual
  registered JNI declarations/implementations are 7/7, and R8 retains the
  required class and method names. Debug-signed minified copies exist under
  `/tmp/thoth-rolling-{full,fdroid}-minified-test.apk` for physical runtime.
- Separate F-Droid HTTPS probe APK SHA-256:
  `9e2f2e3d962002d75b462d9f55a0ff2a30ab5cc507c01d3f0a94387b6e914983`.
- The regular Terminal, Ubuntu, Trixie, and PocketClaw apps were not touched.
  **Incident at 2026-09-30 10:52 local:** `adb install -r` of the 203 MB
  debug-signed minified Full APK selected Android's incremental install path.
  It reported `Success` and initially retained data, but Android fully removed
  `com.thothterm.arch` about 15 seconds later. Logcat records
  `PACKAGE_FULLY_REMOVED` and incremental mount cleanup; the exact initiating
  cause is not yet established. A nonincremental `adb install --no-incremental
  -r` restored the Full debug APK, but its old private data, including
  `/home/thoth/codex-home-preservation-sentinel`, is absent. **The primary
  Rolling HOME was lost.** No `pm clear` or explicit uninstall was run. Do not
  replace the primary APK again until this is understood. Check for a user
  backup. Test future release APKs under an isolated package and always use
  `--no-incremental` for any authorized primary replacement. Original screen
  timeout remains **30000 ms**.

## Candidate phone gates and current remaining work

The physical browser gate on the candidate passed: real Chromium pairing,
wrong PIN, Host/Origin/token rejection, two separate browser sessions and
terminal IDs, cross-session upload refusal, sign-out revocation, reload
reattachment, resize, Ctrl-C, pasted input, Arabic output, LAN-off disconnect,
and occupied-port fallback from 7683 to 7684. File Bridge targeted the
browser shell's current `/home/thoth/codex upload target`, kept duplicate
files, uploaded a nested Arabic folder, and transferred a 150 MiB file whose
phone SHA-256 matches the fixture
`12ba578486fc98e3d601b534901ce1e0cb2743f02de2adbba06a4ab860f85415`.
Eleven forged paths (including traversal, encoded traversal, absolute paths,
backslashes, NUL and control attempts) were rejected. Wrong token/upload ID
were refused. A symlink target was renamed safely and the outside directory
remained clean. Screenshots: `/tmp/thoth-lan-browser.png`,
`/tmp/thoth-lan-uploads.png`, `/tmp/thoth-lan-large.png`,
`/tmp/thoth-lan-security.png`, `/tmp/thoth-lan-reconnect-ctrlc-arabic.png`,
and `/tmp/thoth-lan-off-browser.png`.

The on-device `KeepScreenAwakeTest` and `UploadFsDeviceTest` full rerun passed
**19/19** with the timeout temporarily extended. The original **30000 ms**
was restored. The first overflow-menu failure was a test harness timeout after
the phone slept, not a product failure. Log:
`/tmp/codex-android-instrument-rerun.txt`.

The user independently ran signed `sudo pacman -Syu` on the then-primary
rootfs; their photos show `coreutils` 9.12-2 installed and a returned prompt.
The cached package's signature verified against the pinned Arch Linux ARM
Build System key. Before the install incident, all **251 regular file SHA-256
hashes and 46 symlink targets** in the installed coreutils matched the signed
package. `pacman -Qkk` reported UID/GID mismatch for all files due to
Android/PRoot ownership mapping; this did not indicate changed contents.

The isolated F-Droid consent/download/retry flow passed physically in
`com.thothterm.arch.codexprobe`: decline made no request, HTTP 404, wrong
size, partial body, wrong SHA, and connection failure all offered retry without
installing a rootfs; explicit consent and a valid HTTPS archive installed
successfully and `pacman --version` ran. The test fixture is stopped and ADB
reverse removed. The minified runtime and phone-side upload and cancel gates
also passed in that isolated package. The fresh rootfs package gate, source
collection, reproducibility, and clean release binary checks are now complete
as recorded at the top of this file. Remaining: reconnect ADB, test the
fresh-pinned Full and F-Droid isolated probes physically, resolve or explain
the primary data-loss incident and backup status, check final rootfs freshness
at the eventual publication date, and obtain explicit release authorization.
Do not publish or tag without it.

## Exact next command

`adb start-server; adb devices -l; adb mdns services`

After the user supplies the new pairing endpoint and six-digit code, run
`adb pair <pairing-ip>:<pairing-port>`, enter the code, then connect to the
phone's current main wireless debugging `IP:port` and install only the two
isolated final probe packages with `adb install --no-incremental -r`.
