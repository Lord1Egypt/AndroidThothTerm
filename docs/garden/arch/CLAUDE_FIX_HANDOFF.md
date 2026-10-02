# ThothTerm Garden — remediation handoff (Claude → Codex QA)

| | |
|---|---|
| Implementation branch | `claude/arch-security-hardening-from-3585730` |
| Starting commit | `3585730` (Codex tested checkpoint, `codex/arch-v0.1.0-from-c49bb03`) |
| Ending commit | the commit that adds this file; code HEAD before it is listed in `git log`; run `git rev-parse origin/claude/arch-security-hardening-from-3585730` |
| Review it answers | `docs/garden/arch/SONNET55_REVIEW.md` (branch `review/sonnet55-arch-codex`, `ea23b31`) |
| Not done, on purpose | no tag, no release, no F-Droid MR, Ubuntu/Trixie F-Droid MRs untouched, no BlackArch, nothing pushed to any other branch |

Review with `git diff 3585730..origin/claude/arch-security-hardening-from-3585730`.
Commits, in order: `544984b` extractor + lifecycle, `58fb5df` extractor gate,
`1399022` sticky bit, `e4eb4de` interrupt test, `bdae7a5` PRoot 0006 +
SYSTEMD_IN_CHROOT, `258c0c5` docs + SigLevel build check, `35c380f`
descriptor leak + read-only dirs, `49b4299` first-install mkdir, `889ac72` LAN
upload race, then the device-gate safety commit and this handoff.

**Nothing in this branch has run on Android.** No physical PASS is claimed.
See "What was and was not run" before trusting any line below.

**Returned from Codex local QA of `d977d13`:** see section 5,
"POST-CODEX-QA REMEDIATION", at the end. It supersedes the device-gate
(`GATE_PACKAGE`, `GATE_ALLOW_REPLACE`) and "Preparing administrator tools"
descriptions in sections 1 and 3.

---

## 1. Findings and what was done

### C1 — an installed rootfs could be re-extracted over /home (Sonnet CRITICAL) — FIXED

- **Root cause.** `isReady()` = state record AND `usr/bin/bash` AND
  `runtime/lib/libtalloc.so.2`. Any of these failing made the setup screen call
  `start()`, which unconditionally did `deleteTree(rootfsDir)` and re-extracted,
  with `/home/thoth` inside the deleted tree.
- **Worse in Ubuntu (found during remediation).** `term-ubuntu`'s `isReady()`
  still required `state.matches(image)`, i.e. the pinned image id and sha. Any
  Ubuntu app update that bumps the rootfs pin would have wiped every user's
  home on first launch. The Arch HANDOFF's "data-loss fix" never reached Ubuntu.
- **Fix.** New shared `RootfsLifecycle`
  (`garden-common/src/shared/java/com/thothterm/linux/RootfsLifecycle.java`)
  with the conditions `NOT_INSTALLED`, `INSTALLED_HEALTHY`,
  `INSTALLED_DAMAGED`, `APP_RUNTIME_DAMAGED`, `REPAIR_REQUIRED`,
  `EXPLICIT_RESET_REQUESTED`. Only `NOT_INSTALLED` installs automatically. A
  first install refuses to replace an existing rootfs
  (`promoteFreshInstall`). Missing runtime libraries are re-staged without
  touching the guest. A missing state record with a usable rootfs is finished
  in place. A damaged guest is never touched without the user. Readiness is
  pin-independent in both editions. A reset happens only after a confirmation
  dialog:
  - `/home` is **moved** with `rename(2)` into the new system, never copied or
    deleted;
  - `recover()` completes or rolls back a crash at any step;
  - a reset refuses to run while any PRoot process of the app is alive.

  Both `RootfsManager`s were rewritten on top of it, and the setup screens
  show a "needs attention" / "reinstall not finished" state with a
  confirmation dialog. Details: `docs/garden/ROOTFS_LIFECYCLE.md`.
- **Files.** `garden-common/src/shared/.../RootfsLifecycle.java`,
  `GuestPaths.java`, both `RootfsManager.java`, both setup activities, layouts
  and strings, `term-ubuntu/.../RootfsState.java` (`isInstalled()`), both
  runtimes (`GUEST_ENTRY_POINTS`).
- **Tests.**
  - `RootfsLifecycleTest`, 16 tests, including:
    - `crashAtAnyResetStepNeverLosesHome`: crashes the reset after every
      single rename/unlink/rmdir/mkdir/create; after recovery, exactly one
      complete system holds the byte-identical `/home`;
    - `resetMovesHomeIntoTheNewSystem`: same inode;
    - missing bash, missing runtime files, lost state, pin change, and
      state-without-rootfs.
  - `LifecycleSourceGuardTest`: neither manager deletes or renames over the
    rootfs itself, both promote only through `RootfsLifecycle`, neither gates
    on the pin, and `prepareSession()` provisions nothing.
- **Not claimed.** This does not explain the earlier Android
  incremental-install deletion (`PACKAGE_FULLY_REMOVED` is the OS removing the
  package, not app code).

### C2 — extractor symlink escape (Sonnet CRITICAL) — FIXED at the FileOps layer

- **Root cause.** `removeNonDirectory` used `File.exists()`, which follows
  links, so a dangling symlink was not removed. Then `FileOutputStream`
  followed it: Sonnet's PoC wrote outside staging. The containment check was
  canonical-path based.
- **Fix.** `FileOps` is now a no-follow contract:
  - `lstat` types;
  - files created with `O_CREAT|O_EXCL|O_NOFOLLOW`;
  - `fchmod` on the open descriptor;
  - `unlink`/`rmdir`/`rename`/`link`/`symlink` never dereference.

  `TarballExtractor` refuses any entry whose path goes through a symlink or a
  non-directory (libarchive's secure-symlinks model). It unlinks, never
  follows, an existing final component, and walks hardlink targets the same
  way. The SELinux hardlink fallback copies through `O_NOFOLLOW` (a symlink
  target by its text). Names are strict UTF-8; absolute names and `..` (also
  with backslashes) are rejected, while literal backslashes are kept (Codex's
  fix, now for Ubuntu too); `./` is the root entry. `AndroidFileOps` uses
  `android.system.Os` throughout (`Os.read`/`Os.write`/`Os.close`, because
  Android's `FileOutputStream(FileDescriptor)` does not own the descriptor
  and would leak one per file).
- **Files.** `garden-common/src/shared/.../{FileOps,AndroidFileOps,TarballExtractor,SafeFileTree,RootfsArchive}.java`.
- **Tests.**
  - `ExtractorSecurityCases`: 21 cases. Each extracts next to an OUTSIDE
    sentinel tree, which must stay identical: paths, types, modes, link
    targets, sha256 and mtimes.
  - `FileOpsContract`: 12 no-follow properties.
  - Both run on the JVM with `JvmFileOps`, also with every hardlink refused.
  - `fixtureDetectsTheOldFollowingBehaviour` proves the fixture catches the
    old semantics.
  - The same cases run on a device with `AndroidFileOps`
    (`ExtractorDeviceGateTest`); **this has not been run yet**.

### Ubuntu parity — DONE (shared source, not a copy)

`TarballExtractor`, `FileOps`, `AndroidFileOps`, `SafeFileTree`,
`GuestProcesses`, `RootfsArchive`, `RootfsLifecycle`, `GuestPaths`,
`ManagedFiles` and `SetupTimeline` live once, in `garden-common/src/shared/java`.
`garden-common` and `term-ubuntu` both add that directory to their `main`
source set, and the shared tests to `test`/`androidTest` (see the
`build.gradle` `sourceSets` blocks). The duplicated files and tests in
`term-ubuntu` were deleted. `LifecycleSourceGuardTest.bothEditionsUseTheSharedExtractor`
guards it.

### H1 — release gates did not test the real extractor — FIXED

- `tests/garden-common/extractor/manifest.py` builds the expected tree,
  independently, with Python's `tarfile` (strict UTF-8, the extractor's rules).
- `ExtractorGate` runs every adversarial case and the FileOps contract. It then
  runs the real archive through `RootfsArchive`, the app's own
  extract-and-verify path, and compares the result path by path with the
  manifest.
- `host-gate.sh ARCHIVE SHA256` runs it on the JVM (labelled "not proof of
  AndroidFileOps").
- `device-gate.sh MODULE ARCHIVE SHA256` builds the module's full debug and
  test APKs and installs them with `--no-incremental`, then runs
  `ExtractorDeviceGateTest` with `AndroidFileOps`. (Superseded: the package
  is now an isolated QA id proven from the APK; see section 5.)
- The tar-based package gates stay, but `PACKAGE_MANAGER.md` and
  `ROOTFS_PROVENANCE.md` now say they are not extractor proof.

### H2 — "Can't set permissions to 0777" — ROOT CAUSE FOUND, FIXED IN PROOT (patch 0006)

- **Exact origin.**
  - The call chain is libalpm → libarchive `set_mode()` on a symlink entry →
    glibc ≥ 2.39 `lchmod()` → `fchmodat2(AT_FDCWD, "/usr/lib/…", 0777,
    AT_SYMLINK_NOFOLLOW)` (syscall 452).
  - termux PRoot v5.1.107.92 has no 452 in its syscall tables or seccomp
    filter, so the call reaches the kernel **untranslated**.
  - The absolute guest path does not exist on Android, so the kernel returns
    `ENOENT`. libarchive accepts only `ENOTSUP`/`ENOSYS`/`EOPNOTSUPP` and
    turns anything else into `ARCHIVE_WARN`, which pacman prints.
  - The same bug made `fchmodat(path, mode, AT_SYMLINK_NOFOLLOW)` of a
    **regular file** silently do nothing (`ENOENT`), for example Python's
    `os.chmod(..., follow_symlinks=False)`. Had the guest path existed on the
    host, the host file would have been the one changed.
- **Fix.** `0006-translate-fchmodat2-with-the-kernel-semantics.patch` (in
  `garden-common/patches` and `term-ubuntu/patches`, byte-identical) handles
  `fchmodat2` at entry with calls PRoot already translates:

  | Call | Result |
  |---|---|
  | flags 0 | `fchmodat` on the translated path |
  | `AT_SYMLINK_NOFOLLOW` on a symlink | `EOPNOTSUPP`, as Linux returns it |
  | `AT_SYMLINK_NOFOLLOW` on anything else | `fchmodat` on the translated, unfollowed path |
  | `AT_EMPTY_PATH` with `""` | `fchmod` |
  | other flags | `EINVAL` |

  Nothing is faked or followed. `fake_id0` treats it like `fchmodat`, and a
  `link2symlink` faked hard link is the regular file it stands for.
- **Evidence (x86_64 host, kernel 6.18, glibc 2.39).**
  `tests/garden-common/proot/host-check.sh` builds the pinned PRoot with
  0001-0005 and with all patches:
  - every probe result with 0006 is identical to the native kernel's;
  - without 0006, a libalpm-style extraction prints exactly
    `warning: warning given when extracting /x/libdemo.so (Can't set
    permissions to 0777)`;
  - with 0006 it prints nothing.

  All four checks PASS. `ProotRuntimeHostTest`, which applies every patch,
  still passes its hard-link, hangup and cwd cases.
- **Gate.**
  - New `zero.sh` P6: `bsdtar -xpPf` with absolute member names must print
    nothing.
  - `zero.sh` D and `stale.sh` no longer just tolerate the warning: every
    named path must be a resolving symlink, and `pacman -Qkk` must pass for
    **every package owning one** (not only coreutils).
- The old docs' explanation ("Android cannot chmod a symlink") was wrong and
  is corrected.

### H3 — interrupt test accepted "invalid or corrupted package" — FIXED

`tests/garden-arch/device/interrupt.sh`:
- Any of these lines is a FAIL, never an interrupted-state symptom:
  `invalid or corrupted package`, PGP, `signature from … is invalid/unknown/marginal`,
  required/unknown key, checksum, corrupted.
- A retry may fail only with messages naming the package: vim-runtime's own
  `/usr/share/vim/*` files existing (recovered with the documented narrow
  `--overwrite`), or its own "could not fully load metadata".
- Every cached package is signature-verified by the script itself with gpg
  (`VALIDSIG` for the ALARM key plus full trust) before it is used to restore
  files or recreate metadata.
- Each case ends with `pacman -Qkk <pkg>`, `-Dk`/`-Qk` clean, no lock, and a
  full `-Syu` with no `error:`.
- The run ends with `SigLevel` checked globally and for every repository.
- The missing-desc repair prints `INFO … not hit` instead of a PASS when the
  kill missed that window.

### systemd/chroot ENOSYS — ROOT CAUSE FOUND, FIXED HONESTLY

- **Exact origin.**
  - `systemd-detect-virt --chroot` (`src/detect-virt/detect-virt.c:117`, run
    by Arch's alpm systemd hooks) prints "Failed to check for chroot()
    environment: %m".
  - `running_in_chroot()` (`src/basic/virt.c`) compares `/proc/1/root` with
    `/`. When that returns `ENOENT` while `/proc` is mounted, systemd returns
    a synthetic `-ENOSYS` ("fake /proc").
  - The `ENOENT` comes from Android hiding other users' processes (procfs
    `hidepid`), PID 1 included.

  So it is not a syscall PRoot lacks, and no PRoot change can make PID 1
  visible.
- **Fix.** `SYSTEMD_IN_CHROOT=1` in every guest environment (`GardenRuntime`,
  `UbuntuRuntime`, so windows, LAN windows and provisioning). It is systemd's
  documented override, honoured since v257 (checked in the v257-v260
  sources). The managed sudoers entry keeps it across `sudo`
  (`Defaults:thoth env_keep += "SYSTEMD_IN_CHROOT"`; `visudo -c` parses it).
  It is true: the guest root is not PID 1's.
  - `systemctl try-restart`/`daemon-reload` now say "Running in chroot,
    ignoring command" and exit 0, as in any chroot.
  - No hook removed, `systemctl` not replaced, PID 1 not faked.
  - `stale.sh` no longer excuses the sshd hook failure.
  - `zero.sh` checks `systemd-detect-virt --chroot` (expects yes) and
    `daemon-reload`, and prints whether `/proc/1/root` is visible.
- **Unproven.** The hidepid explanation is inferred from the code path. On
  the phone, `ls /proc/1` in the app's shell should fail.

### F-Droid Ubuntu reviewer ("Extracting Ubuntu… 100%", no terminal)

- **Not reproduced and no cause claimed.** But a real contradiction was found
  and fixed. In the F-Droid flavour, Ubuntu Base has no sudo, so after
  extraction `runPrepare()` ran `apt-get update && apt-get install sudo` and a
  verify step. Each could take up to the 180 s provisioning timeout, all
  **before** the terminal opened, while the screen still read "Extracting
  Ubuntu… 100%".
- `prepareSession()` (UI thread, every new window) also ran the same network
  provisioning synchronously whenever sudo was absent. The code's own comment
  said it "never blocks the terminal".
- **Now:**
  - 100% means the archive is extracted and verified. Download progress is
    separate.
  - The named stages "Finalizing Ubuntu environment…" and "Preparing
    administrator tools…" follow. The latter does only local, bounded work:
    setuid, an interrupted dpkg configure, or the full flavour's bundled
    .debs.
  - Network sudo installs and keyring recreation run on one background thread
    after the terminal opens, under their own lock. `prepareSession()` never
    provisions.
  - `SetupTimeline` logs `DOWNLOAD_COMPLETE`, `ARCHIVE_EXTRACTED` (entries,
    rejected, hardlink fallbacks, bytes), `ARCHIVE_VERIFIED`,
    `SETUP_ROOTFS_COMPLETE`, `ROOTFS_PROMOTED`, `KEYRING_PROVISIONED`,
    `STATE_WRITTEN`, `ADMIN_TOOLS_READY` and `TERMINAL_HANDOFF` in ms, then a
    summary. Background sudo logs its own duration and result.

### Medium/low findings and additional defects found during remediation

| Item | Status |
|---|---|
| M1 rejected entries never failed install | FIXED: `RootfsArchive.verify` fails on any rejection. `./` no longer counts as one (Debian's archive starts with it). |
| M2 names decoded as US-ASCII | FIXED: strict UTF-8. **Confirmed real**: the pinned trixie rootfs has two non-ASCII CA files, one package-owned (`usr/share/ca-certificates/mozilla/NetLock…Főtanúsítvány.crt`), which the old extractor mangled. |
| M3 interrupted-transaction docs | FIXED (`PACKAGE_MANAGER.md`, manager javadoc) |
| M4 shipped SigLevel not enforced | FIXED: `build-rootfs.sh` checks the shipped `pacman.conf` with `pacman-conf`, globally and per repo; `ArchEditionTest` requires the check. **Not executed** (needs the Docker/QEMU builder). |
| M5 nss / source-reuse provenance | DOCUMENTED as unproven in `ROOTFS_PROVENANCE.md`; the sources were not recollected |
| M6 F-Droid archive kept forever | FIXED: deleted after a successful install |
| L1 LAN upload begin race | FIXED: the terminal is reserved under the first lock (both copies identical); no dedicated concurrency test |
| L2 stale provisioning label | unchanged (the label still says keyring-verified; signatures are verified by the stale `-Syu`) |
| L3 non-atomic state write | unchanged, but no longer dangerous: a lost state record now means `REPAIR_REQUIRED` (finish in place), not reinstall |
| New: SHA-256 depended on read-ahead | FIXED: `RootfsArchive` drains the gzip stream and any trailing bytes into the digest (`digestCoversTheWholeFileNotJustTheTar`) |
| New: managed-file UTF-8 corruption | FIXED: `readText` decoded 8 KiB chunks separately, so a large `.bashrc` with Arabic text was rewritten with U+FFFD on every session. Files are now decoded whole, non-UTF-8 files are left alone, and writes are atomic. |
| New: managed writes followed guest symlinks on the Android side | FIXED: `~/.bashrc -> /home/thoth/dotfiles/bashrc` used to make the app write `/home/thoth/...` on *Android*, which failed and broke every new window. It now resolves inside the guest; links leaving the rootfs are never written through. |
| New: `/tmp` sticky bit | FIXED: the extractor dropped it and the manager forced 0777; the gate's "1777" check only passed because it used tar. Directories keep sticky; setuid/setgid are never applied. |
| New: fchmodat2 regular-file no-op | FIXED with H2 |

---

## 2. What was and was not run here

**Environment.** Cloud container, x86_64, Linux 6.18, JDK 21, running as
root. Google Maven (`dl.google.com`, behind `maven.google.com`) is blocked by
the egress policy, so **AGP, androidx and aapt2 were unavailable**: no Gradle
Android build, no resource compile, no lint, no R8, no APK.

**Run and passing:**
- Java compile of both modules' `com.thothterm.linux` packages (`--release 8`)
  against the API-36 framework jar (Robolectric `android-all`), with small
  stubs for androidx, generated `R` and the classes outside the package.
  Same for the device test sources.
- JVM unit tests (JUnit 4.13.2):
  - garden-common `linux` package plus all shared tests: **136 run, 4
    failures**;
  - term-ubuntu `linux` plus shared: **120 run, 1 failure**.

  All five failures fail identically on the untouched baseline `3585730` in
  this container:
  - three `GardenBrandingScriptsTest` prompt cases expect `$` but the
    container runs as root (`#`);
  - `ProotRuntimeHostTest`'s "a SIGKILLed proot takes its tracees with it
    (EXITKILL)" needs ptrace behaviour this container lacks.
- `ArchEditionTest`: 11/11. `LanUploadTest` and `LanServerTest`: 39/39 (UTF-8
  locale).
- Host extractor gate on the real ubuntu-base 26.04.1 archive (6564 paths)
  and the pinned trixie rootfs (7049 paths): 0 rejected, trees identical to
  the independent manifest, 35/35 checks each.
- `tests/garden-common/proot/host-check.sh`: 4/4.
- `bash -n` of every changed gate script.

**Not run (Codex):**
- Anything on Android: `AndroidFileOps` (each `Os` call), the device
  extractor gate, both setup flows, the lifecycle on a phone, the UI states,
  the background admin tools, timings in logcat.
- A Gradle/AGP build: resources, layouts, lint (including NewApi), R8,
  minified runtime, APK contents.
- The host extractor gate on the Arch archive, which is not published here.
  Because a rejected entry now fails the install, an Arch archive entry that
  the new policy refuses would surface as a setup failure. Run the host gate
  first.
- PRoot 0006 on arm64/Android; the device package gate (`gate.sh`) with the
  changed `zero.sh`/`interrupt.sh`/`stale.sh`.
- `build-rootfs.sh` with the new SigLevel check.

---

## 3. Exact local verification commands (Codex)

```sh
git fetch origin claude/arch-security-hardening-from-3585730
git checkout --detach origin/claude/arch-security-hardening-from-3585730
git diff 3585730..HEAD --stat
git submodule update --init --recursive

# Unit tests (all modules; the shared tests run in garden-common and term-ubuntu)
./gradlew :garden-common:testDebugUnitTest \
  :garden-arch:testFullDebugUnitTest :garden-arch:testFdroidDebugUnitTest \
  :garden-debian:testFullDebugUnitTest \
  :term-ubuntu:testFullDebugUnitTest :term-ubuntu:testFdroidDebugUnitTest
# Release builds, as before (Full/F-Droid APK + AAB, R8, alignment, JNI 7/7).

# Host extractor gate (shared policy) on each pinned archive
tests/garden-common/extractor/host-gate.sh <thothterm-arch-aarch64-rootfs-03a4c669ed6f.tar.gz> \
  03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1
tests/garden-common/extractor/host-gate.sh <thothterm-debian-trixie-arm64-rootfs-f6520ff1c6ee.tar.gz> \
  f6520ff1c6eeb28ead2d175e6733da4c970459a291b1bd60f992f1655c5d0677
tests/garden-common/extractor/host-gate.sh <ubuntu-base-26.04.1-base-arm64.tar.gz> \
  5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd

# PRoot fchmodat2 reproduction (Linux >= 6.6, glibc >= 2.39, gcc, libarchive-dev, bsdtar)
tests/garden-common/proot/host-check.sh

# Device extractor gate: the APK's AndroidFileOps, as <base id>.qa.extractorgate
# (section 5). Needs ANDROID_HOME (aapt2) and one device (or ANDROID_SERIAL).
# GATE_PACKAGE / GATE_ALLOW_REPLACE are refused.
tests/garden-common/extractor/device-gate-selftest.sh
tests/garden-common/extractor/device-gate.sh garden-arch <arch archive> <sha256>
tests/garden-common/extractor/device-gate.sh garden-debian <trixie archive> <sha256>
tests/garden-common/extractor/device-gate.sh term-ubuntu <ubuntu archive> <sha256>

# Device package gate (now with P6, systemd chroot, verified-bytes interrupt cases)
tests/garden-arch/device/gate.sh <arch archive> <stale upstream userland archive>
```

**Physical lifecycle checks** (isolated package, `adb install --no-incremental -r`
only, sentinel in `/home/thoth` with its sha256 and inode recorded first):

1. A clean first install, Full and F-Droid.
   - Expect the stages on screen, with download % separate from extraction %.
   - Expect `adb logcat | grep "Setup stage"` to show every stage and the
     summary.
   - Expect the terminal to open right after "Finalizing", with sudo set up
     in the background afterwards, both flavours (log lines
     `Setup stage TERMINAL_READY`, `TERMINAL_HANDOFF`, then
     `OPTIONAL_SETUP_STARTED` and `OPTIONAL_SETUP_FINISHED`/`_FAILED`;
     section 5).
2. Delete the runtime library:
   `run-as <pkg> rm files/linux/runtime/lib/libtalloc.so.2`, then relaunch.
   Expect it re-staged automatically and the sentinel unchanged.
3. Remove the state record:
   `run-as <pkg> mv files/linux/<distro>/state.properties /data/local/tmp/`,
   then relaunch. Expect "finished in place" (no extraction in logcat) and the
   sentinel unchanged.
4. Break the guest:
   `run-as <pkg> mv files/linux/<distro>/rootfs/usr/bin/bash files/bash.bak`,
   then relaunch.
   - Expect the "needs attention" screen and nothing deleted.
   - Move bash back, relaunch, and expect a healthy start.
5. Reinstall: in the damaged state, tap **Reinstall system files**, then
   confirm.
   - Expect the sentinel at the same sha256 and the same inode.
   - Expect a package you installed earlier to be gone, and `/etc` back to
     defaults.
6. Kill a reinstall: start one, then `adb shell am force-stop <pkg>` during
   extraction, then relaunch.
   - Expect "reinstall not finished" with Continue and Cancel.
   - Expect both paths to keep the sentinel.
7. Upgrade over the old builds without touching HOME:
   - from a `3585730` build (Arch);
   - from Ubuntu 0.3.0 to this build (the pin-dependence fix);
   - also with a changed rootfs pin.
8. In a window:
   - `env | grep SYSTEMD_IN_CHROOT` → 1;
   - `sudo env | grep SYSTEMD_IN_CHROOT` → 1;
   - `systemd-detect-virt --chroot; echo $?` → 0;
   - `ls /proc/1` → expected to fail (hidepid);
   - `sudo pacman -S <pkg with symlinks>` → no "Can't set permissions"
     lines.
9. Run a dangling-symlink fixture through the device gate. It is already in
   `ExtractorSecurityCases`, so the device gate covers it.

## 4. Remaining unproven items

- Everything listed under "Not run" above, above all on-device behaviour of
  `AndroidFileOps`, the lifecycle UI, and PRoot 0006 on arm64.
- The cause of the F-Droid reviewer's stall (a fixed contradiction, not a
  proven cause) and of the earlier incremental-install HOME loss (unrelated
  to these fixes).
- That the procfs `hidepid` setting is what hides PID 1 on the test phones
  (check 8 above).
- Arch archive extraction under the stricter policy (host gate on Arch, then
  the device gate).
- The `build-rootfs.sh` SigLevel check in a real build; M5 source provenance.
- The new strings are English only (MissingTranslation is ignored by the
  lint config).

If any check fails, report it back; source fixes stay with Claude.

---

## 5. POST-CODEX-QA REMEDIATION

Codex locally tested `d977d13` and returned three findings plus an Ubuntu
product requirement. Source fixes, in order, on this branch:

| Commit | What |
|---|---|
| `3cf44c7` | Finding 1: `Os.unlink` → public `Os.remove`; public-SDK guard |
| `6a6ad48` | Finding 2: LAN upload begun across a sign-out can never stay open |
| `ee2c8c7` | Finding 3: the device gate installs only a proven, isolated QA package |
| `582a1e4` | Ubuntu (and Garden) requirement: the terminal opens at `TERMINAL_READY` |
| this commit | this section |

**The QA report itself was not readable here.** It was referenced as
`wt-qa-d977d13/docs/garden/arch/CODEX_QA_D977D13.md`, a local worktree, and is
on no pushed branch. The findings below are taken from the return message,
not from the report; if the report holds anything the message did not, it is
not addressed yet.

### 5.1 Finding 1 — SDK 36 compile: `Os.unlink` is not public API

**Root cause.** `AndroidFileOps.unlink` called `android.system.Os.unlink(String)`.
It exists in libcore, but it is `@hide`, so the real `android.jar` (SDK 36)
does not have it. Every compile here ran against Robolectric's `android-all`
jar, which includes hidden APIs, so the stub build compiled and proved nothing.

**Replacement.** `Os.remove(String)` (public since API 21), after an `lstat`
type check:

```java
if (type(file) == Type.DIRECTORY) throw new IOException("Is a directory: " + file);
Os.remove(file.getAbsolutePath());
```

**Why no-follow holds.** `Os.remove` is `remove(3)`. In bionic that is
`unlink(2)`, falling back to `rmdir(2)` only when unlink fails with `EISDIR`.
`unlink(2)` never follows a final symlink: a link, dangling or not, is removed
itself, and its target is untouched. A directory is refused by `lstat` before
the call, so `unlink` can never act as `rmdir`. The installation tree has a
single writer, the setup thread, so nothing can swap the entry between the
check and the call.

Nothing else changed:
- creation is still `open(O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC)`;
- the mode is still applied with `fchmod` on that descriptor;
- `DescriptorOutputStream`/`DescriptorInputStream` still own the descriptor
  and close it explicitly with `Os.close`;
- there is no `java.io.File.delete` or other `java.io` fallback anywhere in
  `AndroidFileOps`.

**Coverage.**
- `tests/garden-common/api/android.system.Os.txt` and
  `android.system.OsConstants.txt` list the public members, from the
  developer.android.com reference pages for API 36 (fetched 2026-10-02).
- `PublicSdkApiTest` (sharedTest, so it runs in garden-common and
  term-ubuntu) scans every main, shared and device-test source root of
  garden-common, term-ubuntu, garden-arch, garden-debian, libtermexec and
  emulatorview. It fails on any `Os.x(` or `OsConstants.X` not in the lists,
  and asserts that `unlink` is absent and `remove` present. Re-adding
  `Os.unlink` fails it with "not public SDK API: [Os.unlink in …]".
- Separately, `AndroidFileOps` was compiled against a public-only
  `android.system.Os` stub (scratch, generated from the list and `javap`
  signatures). It reproduced the SDK 36 error before the fix and compiles
  after. This is not a substitute for the real SDK; see 5.6.
- The `FileOps` contract gained three cases:
  - `unlinkOfDanglingSymlinkRemovesTheLink`;
  - `unlinkOfSymlinkToDirectoryRemovesTheLink` (target directory and its
    contents kept);
  - `unlinkRefusesADirectory` (`IOException`, directory kept).

  They run on the JVM (`FileOpsContractTest`) and on a device through
  `ExtractorDeviceGateTest`, against `AndroidFileOps` itself.

### 5.2 Finding 2 — a LAN upload survived sign-out

**Root cause (a race, reproduced).**
1. `POST /api/upload/begin` checks the token.
2. `LanUploads.begin` reserves the terminal under the lock, then, unlocked,
   reads the terminal's directory (`Host.target`) and creates the staging
   directory (`UploadBatch.begin`). Only then does it register the upload
   under the lock.
3. A sign-out in that window (`auth.revoke`, then `cancelBrowser`) found
   nothing registered to cancel.
4. The begin then registered the upload of a signed-out browser. It stayed
   open, with its staging directory and journal entry, until the 120 s idle
   sweep or LAN Mode off.

`begin` only re-checked `closed`, never whether its browser was still signed
in.

**Fix (`LanUploads`, both identical copies).**
- `signOut(browser)` replaces `cancelBrowser`. Under the lock that registers
  uploads, it records the browser as signed out (`signedOut` set) and removes
  its uploads. The batches are then cancelled outside the lock.
- `begin` refuses a signed-out browser at reservation. At registration it
  checks `closed` and `signedOut` again under the same lock. If either is
  set, it does not register and cancels the batch it opened (staging deleted,
  journal entry removed), returning `CANCELLED` (HTTP 410).

So every interleaving ends in one of two states:
- the upload is registered before sign-out takes the lock, and sign-out
  cancels it;
- sign-out ran first, and begin cancels its own batch.

There is no third state. LAN Mode off (`closeAll` sets `closed`) works the
same way.

Further points:
- Browser ids are never reused within a run (`LanAuth.nextBrowser++`), and a
  new LAN run gets a new `LanUploads`. The `signedOut` set cannot block a
  later browser, and no upload can be reached from another session; `find`
  still matches the owning browser.
- Cancelling closes the stream: `UploadBatch.cancel` closes the `Body` of a
  file being received, which closes the socket input, so the receiving
  thread stops at its next read and `receive` deletes the `.part` file. The
  upload is not just hidden.
- Abandoned uploads are removed from the `active` (receiving) set, and
  `receiving(upload, true)` never re-tracks one that is no longer
  registered: no stale handles.
- `signOut` and `closeAll` are idempotent; `UploadBatch.cancel` already was.
- Lock order is `LanUploads` → `UploadBatch` only, with batches cancelled
  outside the `LanUploads` lock, as before.

**Tests (`LanUploadTest`, identical in garden-common and term-ubuntu).** A
latch in the test `Host.target` holds a begin inside the race window. The
wait ignores interrupts, the slowest host there can be, because LAN Mode off
interrupts its workers.
- `signingOutWhileAnUploadIsBeingOpenedLeavesNothingOpen`: sign-out while
  begin is held, then release.
  - Expects 410 `cancelled`, no staging directory, an empty journal, and a
    second logout still 204.
  - Expects a new begin to get 401, and a second browser's upload to be
    untouched and finish.
  - **Against the old code it fails with 200 and an upload id**, the exact
    QA symptom.
- `lanModeOffWhileAnUploadIsBeingOpenedLeavesNothingOpen`: the same for LAN
  Mode off, with a repeated `stop` being harmless.
- `signingOutClosesTheStreamOfAFileBeingReceived`: a half-sent 4 MiB PUT. On
  sign-out the server ends the connection (the client sees EOF or a reset,
  not a stall), and nothing is left on disk.

### 5.3 Finding 3 — device gate package isolation was not real

**Root cause.** `GATE_PACKAGE` only changed the package the script looked
for, ran `run-as` on and instrumented. The APK it built and installed was
still the module's own application id, so on a phone with the primary app it
installed over it (or, with `GATE_ALLOW_REPLACE=1`, was told to).

**Redesign.**
- **The build.** The `debug` build type of garden-arch, garden-debian and
  term-ubuntu takes `-PthothtermQaApplicationIdSuffix=<suffix>` as
  `applicationIdSuffix`. Gradle refuses anything not matching
  `\.qa\.[a-z][a-z0-9]*`. Release builds are untouched, and without the
  property nothing changes.
- **The gate always builds with `.qa.extractorgate`.** The app is
  `com.thothterm.{arch,debian,ubuntu}.qa.extractorgate`, and AGP derives the
  test APK as `….qa.extractorgate.test`. Manifest permissions and the
  `sharedUserId` use `${applicationId}`, so they are suffixed too; nothing
  can collide with an installed primary app.
- **Identity proof before adb.** `apk-identity.sh` reads, with `aapt2`:
  - `dump badging`: the package of the app APK and of the test APK;
  - `dump xmltree`: the test APK's `<instrumentation targetPackage>`.

  It aborts (exit 4) unless they are exactly `<id>.qa.extractorgate`,
  `<id>.qa.extractorgate.test` and `<id>.qa.extractorgate`.
- **Protected packages.** `com.thothterm`, `com.thothterm.devel`,
  `com.thothterm.ubuntu`, `com.thothterm.debian`, `com.thothterm.arch`, and
  anything containing `pocketclaw` (any case, `-`/`_` variants) abort, wherever
  they appear.
- **Fail closed.** No aapt2 (no `ANDROID_HOME`/`AAPT2`), an unreadable APK, a
  missing package or instrumentation target, or several targets all abort.
- **Order.** The script deletes stale APKs from the output directories,
  builds, requires exactly one APK in each, and checks identity. Only then
  does it `adb install`, `push` or `shell`. Before that it touches adb only
  to count devices: it needs exactly one, or `ANDROID_SERIAL`.
- **Install.** Every install goes through `gate_install`, which always
  passes `--no-incremental`. `-r` only ever replaces an earlier build of the
  QA package.
- **Refused overrides.** `GATE_PACKAGE` and `GATE_ALLOW_REPLACE` abort.
- **Nothing removed.** The gate never uninstalls or clears any app.
- **Limitation.** The QA app's PRoot runtime is built for the base id (PRoot
  bakes in `/data/data/<id>/files/linux/runtime`), so the QA app is for the
  extractor gate only; its terminal will not start. It cannot reach the
  primary app's data: different uid.

**Proof without a device.** `device-gate-selftest.sh`, run by the unit test
`DeviceGateIdentityTest`, uses a stub `aapt2` reading fake APKs and a stub
`adb` logging every call. It covers:
- the right QA ids pass for all three modules;
- the d977d13 situation (the APK still the primary id), a wrong test
  package and a wrong instrumentation target abort;
- every protected id and PocketClaw spellings abort, as app, test, target or
  expected id;
- an unreadable identity and a missing aapt2 abort;
- no identity check ever calls adb;
- `gate_install` passes `--no-incremental`;
- the gate script has no direct `adb install`, no uninstall/`pm clear`,
  checks identity before any install/push/shell, and builds with the suffix;
- all three `build.gradle` files apply the suffix;
- `GATE_PACKAGE`/`GATE_ALLOW_REPLACE` abort before the build. This runs
  against a sandboxed copy whose `gradlew` only records.

Against the previous `device-gate.sh` it reports 7 failures; on this one it
reports OK.

### 5.4 Ubuntu requirement — the terminal never depends on optional provisioning

Before this change, ThothTerm Ubuntu's full flavour installed its bundled
sudo packages (dpkg, up to 180 s per step) before the handoff, behind
"Preparing administrator tools…".

**Explicit states.** `SetupState` and `OptionalSetup`, shared code:
- `NOT_INSTALLED`;
- `CORE_SETUP_RUNNING`;
- `TERMINAL_READY`;
- `OPTIONAL_SETUP_PENDING` (queued or running);
- `OPTIONAL_SETUP_FAILED`;
- `HEALTHY`;
- `REPAIR_REQUIRED` (every installed non-healthy condition).

Only `INSTALLED_HEALTHY` with no core setup running is terminal-usable, and
it stays usable whatever optional setup does.

**Core path (`runPrepare`).** Download, extraction, verification, managed
configuration, promotion, (Arch) the offline keyring, then the state record.
Then `TERMINAL_READY` is logged, the setup screen is told to open the
terminal, and `TERMINAL_HANDOFF` and the summary are logged. No package
install, network or `dpkg` runs in between; `prepareAdminToolsOffline` is
gone.

**Optional path (`runOptionalSetup`).** Queued only after the handoff, on the
existing single background thread under `adminLock`, and coalesced by
`OptionalSetup.queue()`. It covers:
- the setuid bit on an installed sudo;
- an interrupted dpkg configure;
- the bundled .debs (Ubuntu full);
- apt from Ubuntu's archive (Ubuntu F-Droid);
- a missing pacman keyring (Arch).

Success means sudo is `INSTALLED` (and, on Arch, the keyring exists).
Otherwise:
- it is `FAILED` with a reason;
- the terminal stays open and usable;
- the terminal shows "Administrator tools (sudo) could not be set up. The
  terminal works normally; retry from the menu." once;
- the menu shows **Retry administrator tools setup**;
- the next session start also retries.

It never extracts, resets, deletes, writes the state record or rewrites
managed files (guarded), so it can never re-extract or touch `/home`.

**UI.** The setup screen goes from "Extracting Ubuntu… 100%" (archive
extracted *and* verified) to "Finalizing Ubuntu environment…" (indeterminate
bar), then straight to the terminal. There is no longer an admin-tools
screen to sit on.

**Timing logs (ROOTFS).**
- `Setup stage DOWNLOAD_COMPLETE`;
- `ARCHIVE_EXTRACTED` (entries, rejected, hardlink fallbacks, bytes), plus a
  warning line with the hardlink fallback count;
- `ARCHIVE_VERIFIED`;
- `SETUP_ROOTFS_COMPLETE`;
- `ROOTFS_PROMOTED`;
- `KEYRING_PROVISIONED` (Arch);
- `STATE_WRITTEN`;
- `TERMINAL_READY state=…`;
- `TERMINAL_HANDOFF`;
- `Setup timeline …` (the summary);
- `OPTIONAL_SETUP_STARTED reason=…`;
- `OPTIONAL_SETUP_FINISHED` or `OPTIONAL_SETUP_FAILED`, each with `ms=`,
  `sudo=`, `reason=` and `state=`.

All are in ms since setup start; `t=-` when a later session queued the
optional work.

**Garden editions** get the same split for parity: shared state classes, the
same `Term` menu and message.

**Tests.**
- `LifecycleSourceGuardTest.theTerminalNeverWaitsForOptionalProvisioning`
  checks both managers:
  - `runPrepare` calls no sudo/provisioning method;
  - `TERMINAL_READY` comes before `notifyComplete()`, then
    `TERMINAL_HANDOFF`, then `scheduleAdminTools("after setup", …)`;
  - `runOptionalSetup` contains no extraction/reset/delete/state-write/
    managed-file call and reports its outcome.

  It fails on the previous managers.
- `SetupStateTest`:
  - every optional status keeps a healthy installation usable;
  - nothing optional changes a non-healthy decision;
  - core running is never usable;
  - fail → retry → succeed, with the listener sequence;
  - out-of-order transitions are refused;
  - 16 concurrent `queue()` calls start exactly one run.

**What this does not claim (fdroid/fdroiddata!49556).** The reviewer
reported a Xiaomi Redmi Note 10 Pro on Android 16 that stayed at "Extracting
Ubuntu… 100%", with SELinux hard-link denials in the log. This change:
- makes sure the terminal can never wait on optional provisioning;
- makes every stage visible in the log, so a repeat report shows where the
  time goes.

It does **not** claim to be the reviewer's root cause, does not claim the
issue is fixed, and does not claim the SELinux denials are the cause. The
extractor already falls back to copying, and the log counts it. Nothing is
assumed about the reviewer's device. Only a run on such a device, with this
log, can say more.

### 5.5 Tests run here (this container)

| What | Result |
|---|---|
| garden-common linux + shared JVM suite (146) | 142 pass. 4 fail, all environment-only and identical at `d977d13`: 3 `GardenBrandingScriptsTest` (container runs as root, prompt shows `#`) and `ProotRuntimeHostTest` EXITKILL (container ptrace) |
| term-ubuntu linux + shared JVM suite (129) | 128 pass; 1 fails: the same EXITKILL case |
| `PublicSdkApiTest`, `FileOpsContractTest` (3 new cases), `SetupStateTest`, new guard test | pass, in both modules |
| LAN: `LanUploadTest` + `LanServerTest` + `LanAuthTest` | garden-common 52/52, term-ubuntu 51/51; `LanUploadTest` 15× in a row 21/21 |
| New LAN race test against the old `LanUploads` | fails (200 + upload id), as expected |
| `DeviceGateIdentityTest` / `device-gate-selftest.sh` | OK; 7 failures against the old gate script |
| `ArchEditionTest`/`Branding`/`Palette` | 25/25 |
| `DebianEditionTest`/`Branding`/`Palette` | 17/17 |
| Host gate, Ubuntu 26.04.1 (`5a1906…19fd`) | GATE PASS: 6564 entries, 0 rejected, 0 hardlink fallbacks, manifest match |
| Host gate, Trixie (`f6520f…0677`) | GATE PASS: 7050 entries, 0 rejected, manifest match (non-ASCII names) |
| Host gate, Arch (`03a4c6…48d1`) | **not run here**: the release asset URL returns 404 from this container. Codex ran it at `d977d13`; the extractor policy is unchanged since, only `AndroidFileOps.unlink` changed |
| `AndroidFileOps` against a public-only `Os` stub | compiles; failed with `Os.unlink` before the fix |
| `Term.java` (both) | only partially checked: compiled with the R stubs, but androidx is unavailable. Every error is a missing androidx class, except one stub artefact (`doShowAbout`, which this change does not touch). None involves the new code |

### 5.6 Not run here, explicitly

- **No real Gradle build and no real SDK 36 compile.** `maven.google.com`
  redirects to `dl.google.com`, which this container's proxy refuses, so the
  Android Gradle Plugin and androidx cannot be resolved. "Compiles against
  SDK 36" is **not** shown here: what is shown is the public-API scan and the
  public-only stub compile. This is not a PASS for the real build.
- No lint and no R8 run; no APK built; the `applicationIdSuffix` property was
  never evaluated by real AGP.
- No aapt2 run on a real APK. The parsing of `dump badging` and
  `dump xmltree` follows aapt2's documented output, but only stubs exercised
  it.
- Nothing ran on Android: not the LAN fix on a phone, not the setup flow or
  the retry menu, not the device gate, not `AndroidFileOps.unlink` on bionic.

### 5.7 Remaining Codex verification

1. **Real build:** `./gradlew :garden-arch:assembleFullRelease
   :garden-arch:assembleFdroidRelease :garden-debian:… :term-ubuntu:…`
   (SDK 36). Confirm `AndroidFileOps` compiles, and that lint and R8 are
   clean.
2. **Gate build:** `./gradlew -PthothtermQaApplicationIdSuffix=.qa.extractorgate
   :garden-arch:assembleFullDebug :garden-arch:assembleFullDebugAndroidTest`.
   - Check `aapt2 dump badging` shows `com.thothterm.arch.qa.extractorgate`
     and `….test`, and the instrumentation `targetPackage`.
   - Check that `-PthothtermQaApplicationIdSuffix=bad` fails the build.
3. **Device gate:** `tests/garden-common/extractor/device-gate-selftest.sh`,
   then `device-gate.sh` for garden-arch, garden-debian and term-ubuntu with
   the pinned archives, on a phone **with the primary apps installed**.
   Before and after, record `pm list packages | grep -i -e thothterm -e
   pocketclaw` and the primary apps' `firstInstallTime`/`lastUpdateTime`
   (`dumpsys package`), and check they are unchanged.
4. **LAN on a phone:**
   - begin an upload and sign out at once (repeat ~20×), and begin one then
     turn LAN Mode off. Expect no `.thothterm-upload-*` left in the
     terminal's directory, and `upload-staging` empty after the next start;
   - mid-transfer sign-out: expect the browser's PUT to end, and no partial
     file.
5. **Ubuntu first run** (Full and F-Droid, isolated install, a test phone):
   - expect the terminal to open right after "Finalizing";
   - expect `logcat | grep "Setup stage"` to show `TERMINAL_READY` before
     `TERMINAL_HANDOFF`, and `OPTIONAL_SETUP_*` after;
   - with the network off on F-Droid: expect a usable terminal,
     `OPTIONAL_SETUP_FAILED`, the one-time message and the retry menu item.
     After re-enabling the network and retrying, expect
     `OPTIONAL_SETUP_FINISHED` and a working `sudo`;
   - expect no re-extraction and `/home/thoth` unchanged throughout.

   Note: the gate's `.qa.extractorgate` app cannot do this, because its PRoot
   is built for the base id (5.3). Lifecycle checks need a build whose native
   runtime matches its id, which this branch does not yet provide for a
   suffixed id.
6. **Arch host gate** on `03a4c669…48d1`, since this container could not
   fetch it.
7. **Earlier fixes are not regressed:**
   - re-run the section 3 checks: HOME lifecycle, patch 0006, the
     `SYSTEMD_IN_CHROOT` checks, the `/tmp` sticky bit, `.bashrc` through a
     symlink, and the signature gates;
   - confirm the ARM64 PRoot runtime's 16 KB alignment (`PageAlignmentTest`
     / `zipalign -c -P 16`). This change does not touch PRoot or its build.

Not done, on purpose: no release, no tag, no F-Droid MR, the Ubuntu/Trixie
F-Droid MRs untouched, no BlackArch, nothing pushed to any other branch, no
history rewritten.
