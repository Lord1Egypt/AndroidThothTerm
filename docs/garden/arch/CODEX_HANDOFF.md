# ThothTerm Rolling — local Codex continuation

Updated 2026-09-30 after the physical browser gate. Base **c49bb03**; branch
`codex/arch-v0.1.0-from-c49bb03`; last code commit before this update
**e12eb12** and previous handoff commit **a1d5392**. Run `git rev-parse HEAD`
for the exact current commit (a handoff file cannot contain its own commit hash).
The later cloud Arch branch was not used as an implementation baseline. This
is a candidate, not a release. No tag, GitHub release, F-Droid MR, or
BlackArch work was created.

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
- No protected app was uninstalled or cleared. The primary Rolling HOME
  sentinel is still in place. Disposable device-gate roots live under
  `files/ga`, separate from `files/linux`.

## Remaining gates

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

The on-device `KeepScreenAwakeTest` and `UploadFsDeviceTest` run had 19 tests,
18 pass and one overflow-menu automation failure after the phone slept. The
single failed test then passed alone. Upload filesystem's four tests all
passed. Rerun the full suite with the recorded original timeout temporarily
extended and restore **30000 ms** immediately afterward; classify the first
failure as a test harness timeout only if the full rerun confirms it.

The user independently ran signed `sudo pacman -Syu` on the primary rootfs;
their photos show `coreutils` 9.12-2 installed and a returned prompt. The
locale symlink chmod warnings match the known PRoot model limitation, but
package integrity still needs direct confirmation on the primary rootfs.

Minified physical runtime, isolated F-Droid consent/download/retry,
cancellation and phone-side File Bridge UI, and final fresh-rootfs acceptance
remain. The complete device gate must be rerun on the fresh final rootfs. Test
the isolated F-Droid consent/download/retry flow.
Resolve the clean source collector trial. Once candidate defects are fixed,
capture a fresh rootfs, build twice from equivalent clean inputs and compare
bytes, collect exact sources, write provenance and pin the new hash. Re-run
physical package and app gates on that final candidate, then clean-clone
Full/F-Droid APK builds and binary checks. Do not publish or tag without
explicit authorization.

## Exact next command

`adb devices -l && adb shell settings get system screen_off_timeout && adb shell dumpsys window | grep -m1 mCurrentFocus`
