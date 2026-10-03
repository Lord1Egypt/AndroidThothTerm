# Garden master closure — report

| | |
|---|---|
| Branch | `claude/garden-master-closure-from-9677a88` (pushed) |
| Start | `9677a88` on `claude/arch-security-hardening-from-3585730` (verified equal to origin) |
| Code final | `3ed4b96`; this report and its evidence are committed on top |
| Date | 2026-10-03 |
| Verdict | **RELEASE BLOCKED: physical device gate (`tests/garden-common/qa/final-device-gate.sh`) not run — the SM-A165F's adb transport stays "offline" (needs authorization on the phone)** |

Nothing was tagged, released or posted. No F-Droid MR was touched. The
historical tags `terminal-v1.4.0`, `ubuntu-v0.3.0` and `trixie-v0.2.0` are
unchanged. No production package was installed, replaced or cleared.

## Start state

`git rev-parse origin/claude/arch-security-hardening-from-3585730` = `9677a88d…`;
the closure branch was created from it in worktree `wt-closure`. A clean
baseline at `9677a88` (separate worktree) on this machine: garden-common 324,
term-ubuntu 584 (both flavours), Arch 56, Trixie 20 unit tests, all passing;
`:term-ubuntu:lintFullDebug` passing. Read before any change:
`SONNET55_REVIEW.md`, `CODEX_QA_839F935.md`, `CODEX_QA_9677A88.md`,
`CLAUDE_FIX_HANDOFF.md`.

## Forensic findings (new in this pass)

| # | Severity | Finding | Evidence |
|---|---|---|---|
| F1 | Critical (Codex R3 #1) | JVM chmod fallback: `lstat`, then `chmod(path)` follows a symlink swapped in between. **The same shape was in production `AndroidFileOps`** (EACCES path). | PoC: sentinel 0600 → 0640, `evidence/toctou-before-9677a88.txt` |
| F2 | High (Codex R3 #2) | Owner-unreadable content verified from the write-time digest only: same-size rewrite / replacement undetected. | static; now an executable test |
| F3 | Medium (Codex R3 #3) | Renaming one name of a hardlink pair dropped the pair from the inode check. | static; now an executable test |
| F4 | High | `forceSudoSetuid` (both managers): `lstat` then `Os.chmod(path, 04755)` in the **live** rootfs while guest processes run. | code |
| F5 | High (latent) | On Android every hardlink is a copy; a target whose final mode lacks owner read (Arch's 04110 helper) was already unreadable → copy EACCES → install fails. No pinned archive has such a link today. | `TarballExtractorTest.hardlinkCopyOfAnOwnerUnreadableTargetWorks` fails on the old code |
| F6 | Medium | A hardlink copy took the link header's mode, not its target's (a link is the target's inode; `manifest.py` already assumed so). | test |
| F7 | High | Rolling ran `pacman-key --init/--populate` (GnuPG) **before** TERMINAL_READY: the terminal waited for keyring work, and on a kernel like the reviewer's the whole install would fail. | code |
| F8 | Medium | Provisioning stdin was an open pipe nobody wrote (a reading command waited 180 s); nothing enforced "not on the main thread". | code |
| F9 | High (process) | `tests/garden-arch/device/gate.sh` hard-coded `com.thothterm.arch` (production) and wrote into its private `files/ga` via run-as. | code |
| F10 | High | PRoot: `GETEVENTMSG` failing or yielding 0 leaves a pre-stopped child stopped forever (reviewer's `t`/`D`). | reproduced, `evidence/proot-fork-recovery-host.txt` |
| F11 | Low | `--bind=/proc/mounts:/etc/mtab` is resolved once at PRoot start; on the reviewer's ROM that failed and dropped `/etc/mtab` (Ubuntu/Trixie ship none). | reviewer log; cause on device not established |
| F12 | Low | `device-gate.sh` did not run newly added device tests (only one class). | code |

Audited and found sound or already fixed: prepareSession never provisions
(the 0.3.0 main-thread `waitFor` path is gone since 582a1e4), OptionalSetup
single-flight (16-way test), lifecycle HOME preservation (crash at every step,
`RootfsLifecycleTest`), LAN sign-out race (`LanUploadTest`), no
`adb install` without `--no-incremental`, no uninstall/`pm clear` anywhere.

**Bounded, stated limitation (not fixed):** the extractor walks intermediate
components with `lstat` and then operates by path; the *final* component of
every operation is now atomic no-follow (O_EXCL|O_NOFOLLOW create,
O_PATH|O_NOFOLLOW chmod, unlink/rename/link/symlink never follow). An
intermediate-component swap needs a concurrent writer in the app-private
staging directory; first install has none and a reinstall refuses to start
while any PRoot of the app runs. A guest process is the same Linux uid as the
app, so it gains nothing by racing. Android's public `Os` has no `*at` calls; a
full dirfd walk would need JNI or `/proc/self/fd/N/name` paths.

## Implementation (commits on the branch)

| Commit | What |
|---|---|
| `c2f648a` | F1: chmod through one O_PATH\|O_NOFOLLOW descriptor (`AndroidFileOps`; JVM: open(O_NOFOLLOW)+fchmod, EACCES → O_PATH helper process); race tests |
| `9ec42d3` | F5/F6: owner-unreadable file modes applied after every entry; copy takes the target's mode |
| `c6eaf8e` | F2/F3: verifier proves every final object; recorder pins, inode-group tracking |
| `ab40535` | F10: PRoot patch 0007 (both patch sets, byte-identical) + host check |
| `93e41c0` | F11: `--bind=/proc/self/mounts:/etc/mtab` |
| `32baddb` | F4/F7/F8: no guest command before the terminal; setuid via one descriptor; provisioning stdin /dev/null, main-thread refusal, timeout tail |
| `e108de9` | F9: Rolling pacman gate requires an installed isolated QA package |
| `475d314` | the single final device gate script |
| `752278b` | Ubuntu 0.3.1 (301), Trixie 0.2.1 (201) candidates + changelogs |
| `37c8c2e` | `verify-apks.sh`, PRoot/runtime-id evidence, reviewer draft (F12 in `32baddb`) |
| `3ed4b96` | docs: keyring is optional setup |

## Round 3 security closure

**TOCTOU.** Before: Codex's PoC against unmodified `9677a88` →
`outside=640` (`evidence/toctou-before-9677a88.txt`). After: the chmod is
decided and applied on one descriptor (`O_PATH|O_NOFOLLOW` → `fstat` type →
`fchmod` → `fstat` check; bionic's `fchmod` routes O_PATH through
`/proc/self/fd/N`, verified in bionic source). Codex's unchanged PoC is
**vacuous** against the new code (it swaps on a 2nd `lstat` that no longer
happens — noted in `evidence/toctou-after.txt`); the proof is
`JvmFileOpsRaceTest.swapAfterAnyTypeObservationNeverReachesTheTarget` (swap
after the 1st, 2nd, … observation) plus a mutation run: with the 9677a88
fallback restored it fails "swap after observation 2 expected:<384> but
was:<416>" (0600→0640); with the fix it passes. Also: seam swaps for a file and
a directory, a FIFO is refused without being opened, and a free-running swap
race (file / link to outside file / link to outside dir / absent; ≥200
attempts and ≥3 real applications) in `FileOpsContract`, which the device gate
runs against `AndroidFileOps` (30/30 runs green on the JVM).

**Finding 2.** A file that would become owner-unreadable is pinned with a
read descriptor while still readable; at verification the descriptor's inode
must equal the final path's `lstat` inode and its *current* bytes are hashed
through it. Tests: same-size rewrite → `FINAL-OBJECT-…`, truncation →
`PINNED-INODE-SIZE-…`, replacement by an identical-content file →
`FINAL-PATH-IS-NOT-THE-PINNED-INODE`, unreadable-but-unpinned → fail. Mutant
"trust the record" is caught.

**Finding 3.** Names map to per-object nodes; groups are recomputed from
surviving names. Tests: rename of a member, unlink of the first name, member
inside a renamed directory, rename over a member, a member replaced by a copy
behind the recorder's back (caught). Mutant "rename drops the link" is caught.

## PRoot / Redmi case

**Proven (host):** with patches 0001–0006 and the event pid forced to 0
(test-only shim), `fork-probe vfork` reproduces the device exactly — parent
`D`, child `t`, `ptrace(GETEVENTMSG)` warning. With 0007 under the same forced
condition: fork, vfork, posix_spawn, CLONE_PARENT, threads recovered;
concurrent forks recovered 5/5; concurrent thread creation in one process is
**refused and the session stops cleanly** 5/5 (never a hang or leftover
process). With 0007 on a normal kernel every workload behaves as without it.
`THOTHTERM_TEST_NO_EVENTMSG` is guarded out of both `build-proot.sh`.

**Hypothesis, not proven:** that the reviewer's kernel withholds the fork
event pid (failure or 0; the printed errno can be stale when the call
succeeds with 0). Why it would do so is unknown.

**Physical matrix:** no device ran 0007. Kernel 4.14 / crDroid: **unverified**.
`final-device-gate.sh` runs a static arm64 fork probe under the app's PRoot and
records the kernel.

Reference: scarif-labs/horus `native/patches/proot-android.patch` (MIT,
compatible) was studied; 0007 is a separate implementation (zombies excluded,
a vanished child is let go, the stop reason is reported).

## Ubuntu provisioning

Core path (`runPrepare`): download (F-Droid) → verify → extract → verify →
managed config → promote → state → `TERMINAL_READY` → `notifyComplete` →
`TERMINAL_HANDOFF`; no guest command on it (source guard). Then
`scheduleAdminTools("after setup")` → `OptionalSetup.queue()` (single-flight)
→ one background executor → sudo (bundled .debs or apt) with stdin /dev/null,
180 s timeout, PRoot SIGKILL + EXITKILL on timeout, last output logged.
`runProvisioning` throws on the main looper. `prepareSession()` (UI thread)
only writes managed files and sets sudo's setuid bit through one descriptor.
Device timing: **unverified**.

## Extractor / verifier — real archives, host JVM, uid 1000

| Archive | SHA-256 | Paths | Files proven | Notes |
|---|---|---|---|---|
| Ubuntu 26.04.1 base | `5a1906…19fd` | 6564 = manifest | 5522 re-read | 2 link groups / 117 names |
| Trixie | `f6520f…0677` | 7049 = manifest (7050 entries) | 5454 re-read | NetLock…Főtanúsítvány.crt in the exact manifest match |
| Arch (Rolling) | `03a4c6…48d1` | 33639 = manifest (33640 entries) | 24089 re-read + 1 via pin | `usr/lib/dbus-daemon-launch-helper` stays 0110, never chmodded; 1027 link groups / 2848 names |

Each host gate: 46 PASS, `GATE PASS`: 21 adversarial extractor cases (incl.
literal backslash, UTF-8, malformed UTF-8, symlink/hardlink escapes), 22
FileOps contract cases (incl. the chmod swap race and FIFO) and 3 real-archive checks, outside sentinel unchanged. Files:
`evidence/final-host-gate-*.txt`.

## Build (clean, `3ed4b96`, JDK 17, SDK 36, NDK 23.2.8568313)

- Unit: garden-common 335/335, term-ubuntu 604/604, Arch 56/56, Trixie 40/40.
- Lint: 0 errors in all 7 reports (garden-common, Arch/Trixie/Ubuntu × Full/F-Droid).
- 6 release (R8) + 6 debug + 3 androidTest APKs built.
- `verify-apks.sh`: 60/60 — identity, `zipalign -c -P 16 4`, ELF LOAD ≥ 0x4000,
  JNI 7/7 (R8 kept `TermIO$Native`×2, `Process$Native`×5), Full embeds the
  pinned rootfs, F-Droid embeds none.
- PRoot host: fork recovery all PASS; fchmodat2 4/4 (headers via `CPATH`, no
  system package changed); runtime ids PASS; device-gate selftest OK.

## Release candidates (not Goldens)

| APK | Package | Version | SHA-256 |
|---|---|---|---|
| garden-arch-full-release-unsigned | com.thothterm.arch | 0.1.0 (100) | `d792d8d676de450460ce7e470c4d330ed380f75d4eb95fe39398eb6596529050` |
| garden-arch-fdroid-release-unsigned | com.thothterm.arch | 0.1.0 (100) | `4de243cdbd3e5bcd4d591c804202ab1eb5751b595415eda6b206d707d63e5e26` |
| garden-debian-full-release-unsigned | com.thothterm.debian | 0.2.1 (201) | `a7deabf9329e3fd838b7fb571f99a509f2bc777e9b095f895003dc8290b326d2` |
| garden-debian-fdroid-release-unsigned | com.thothterm.debian | 0.2.1 (201) | `bb1b10d27f2a9691520356ae17fbf6110cfd15238e43544db9ac791bce4f04fe` |
| term-ubuntu-full-release-unsigned | com.thothterm.ubuntu | 0.3.1 (301) | `815409ca2019206022f4cb3590f54b1eab3f160c87226ea494db58ee8545e1ea` |
| term-ubuntu-fdroid-release-unsigned | com.thothterm.ubuntu | 0.3.1 (301) | `398ebfecf0c6b12e8fec7103db0b8d98d8715011981acc78447d06a962520815` |

All 15 hashes: `evidence/final-rc-sha256.txt`; files in
`/home/lordegypt/AndroidThothTerm/rc-3ed4b96/` (outside git). AGP embeds the
commit, so a rebuild at another commit changes the Garden hashes.
Versions follow the repo scheme (patch release = +1 code). Regular Terminal:
`3585730..HEAD` touches none of `term/`, `emulatorview/`, `libtermexec/` → no release.

## Device

**Not run.** The SM-A165F (`RK8Y6016N5V`) advertised wireless debugging over
mDNS; the advertised ports refused connections, a scan found 39629 open, and
`adb connect` gave a transport that stayed `offline` (authorization/pairing on
the phone). An unknown `_adb._tcp` device at 192.168.1.100 was not touched.
Every physical item — AndroidFileOps cases and swap race, setuid helper, QA
PRoot launch, HOME preservation, 0006/0007 on arm64, pacman/apt, systemd
chroot, LAN, ANR/timings, F-Droid consent/retry — is **UNVERIFIED**, not FAIL.

## Package managers

Static only: shipped `pacman.conf` checks unchanged (`DisableSandboxFilesystem`
only, `DownloadUser = alpm`, `SigLevel` Required). Rolling's keyring is now
created after the terminal opens; pacman refuses packages until it verifies.
pacman/apt on device: **unverified** (in the final gate).

## LAN / HOME

LAN unit tests pass (both copies); device: unverified. HOME: lifecycle unit
tests pass (crash at every reset step keeps a byte-identical `/home`); device
sentinel checks are in the final gate: unverified.

## F-Droid

No change to !49556 / !50342, no Rolling MR: there is no Golden. When there
is: replace each Builds stanza with the single latest one, squash ON,
auto-merge OFF, never merge. Reviewer reply: `FDROID_REVIEWER_REPLY_49556.md`,
a draft — says what was and was not proven, no 4.14 claim, asks for a retest.

## The one remaining gate

On the SM-A165F: accept the wireless-debugging authorization (or re-pair),
then, with someone at the phone:

```sh
export ANDROID_HOME=~/Android/Sdk ANDROID_NDK_HOME=~/Android/Sdk/ndk/23.2.8568313
export JAVA_HOME=~/.gradle/jdks/jetbrains_s_r_o_-17-amd64-linux.2 PATH=$JAVA_HOME/bin:$PATH
adb connect 192.168.1.103:<port>
tests/garden-common/qa/final-device-gate.sh ~/AndroidThothTerm/qa-final ~/AndroidThothTerm/archives \
    ~/AndroidThothTerm/arch-rootfs-work/stale/alarm-upstream-20260805-userland.tar.gz
```

Exit 0 (no FAIL, no UNVERIFIED) is the condition for tagging
`arch-v0.1.0`, `ubuntu-v0.3.1`, `trixie-v0.2.1` from the tested commit and then
updating the F-Droid metadata. The kernel-4.14 item stays open until the
reviewer (or another 4.14 device) runs the CI build.
