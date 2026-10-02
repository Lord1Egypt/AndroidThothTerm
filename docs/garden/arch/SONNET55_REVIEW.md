# THOTHTERM ROLLING — SONNET 5.5 FORENSIC REVIEW

Reviewed base: **c49bb03**

Reviewed Codex HEAD: **3585730d3c4c6380302ee22e143480a566d711cf**
(`origin/codex/arch-v0.1.0-from-c49bb03`, matches the expected `3585730`)

Scope: the complete diff `c49bb03..3585730` (16 files, +782/−44), plus the
high-priority areas around it that the diff relies on (extractor, rootfs
lifecycle, pacman/PRoot, File Bridge, Full/F-Droid split, R8/JNI).
This was a review only. Nothing was rebuilt, no device or ADB was used, and no
test suites were run. One small standalone proof-of-concept (finding C2) ran
the unmodified `TarballExtractor.java` on the host JVM. No source file was
changed.

## VERDICT: **CHANGES REQUIRED**

Codex's own source change, the backslash fix in
`TarballExtractor.sanitizeEntryName`, is correct. It does **not** weaken
traversal protection. Two **pre-existing** issues in code that Codex audited
and described as safe meet the brief's CRITICAL criteria (user data loss;
arbitrary path write). I stopped there and changed no source. Both are
explained below for a decision before release.

---

## CRITICAL

### C1 — An installed rootfs is silently re-extracted, deleting `/home/thoth`, whenever `isReady()` is false for any reason other than "never installed" (pre-existing, data loss)

- **File / function:** `garden-common/.../linux/RootfsManager.java` —
  `isReady()` (L209-216), `start()` (L258), `extractRootfs()` (L376-456,
  specifically `SafeFileTree.deleteTree(fileOps, linuxDir, rootfsDir)` at
  L445); `GardenSetupActivity.onResume()` (L145-151).
- **What the code does:** `isReady()` requires all three of these:
  `state.isInstalled()`, `rootfs/usr/bin/bash` exists, and
  `runtime/lib/libtalloc.so.2` exists. If any of them fails,
  `GardenSetupActivity.onResume()` calls `manager.start()` **automatically**.
  In the Full flavour, and in F-Droid when a verified `rootfs.tar.gz` is still
  cached, it does this without any user prompt. `runPrepare()` then runs
  `extractRootfs()`, which ends with `deleteTree(rootfsDir)` followed by
  renaming staging over it. `/home/thoth` lives inside `rootfsDir`, so the
  user's HOME is deleted.
- **Why it matters:** The earlier fix (HANDOFF.md "Data-loss fix") only made
  `RootfsState.isInstalled()` independent of the pinned image. Nothing
  distinguishes "never installed" from "installed but a probe file is
  missing". `state.isInstalled()==true` is never used as a reason to refuse a
  destructive re-extract.
- **Concrete failure modes:**
  1. The user runs `pacman -Rdd bash`, a `replaces=` transition, or an
     interrupted transaction that leaves `/usr/bin/bash` absent (exactly what
     `interrupt.sh` simulates for icu). On the next app launch HOME is wiped
     without a prompt.
  2. A future app version renames or drops `libtalloc.so.2`, for example
     with a static PRoot. `RUNTIME_LIBS` and the `isReady()` probe change
     together, so every upgraded user's old runtime dir lacks the new file.
     `isReady()` returns false and every upgraded user loses HOME on first
     launch.
  3. `runtime/lib` is removed by anything (a storage cleaner, a partial
     restore). Same result.
- **Recommended fix direction:** Split readiness into "installed" (the state
  manifest) and "runnable" (the probes). When `state.isInstalled()` is true,
  never call `deleteTree(rootfsDir)` automatically:
  - re-stage the runtime libraries alone (they are app-owned) if they are
    missing;
  - if guest files are missing, show a repair screen that requires explicit
    user consent and first moves `rootfs/home/thoth` aside, for example by
    renaming it to `linuxDir/home.preserved-<ts>`, and restores it after
    extraction.

  Add a JVM test for that decision logic.
- **Relationship to the incremental-install incident:** No code path here
  produces Android's `PACKAGE_FULLY_REMOVED`, so this does **not** explain that
  incident. It is a separate, still-open data-loss path.

### C2 — The tar extractor writes outside the extraction root through a dangling symlink (pre-existing; confirmed by PoC; also in `term-ubuntu`)

- **File / function:** `garden-common/.../linux/TarballExtractor.java` —
  `removeNonDirectory()` (L318-322), used by the regular-file (L228-252),
  symlink (L254-267) and hardlink (L269-288, through `linkOrCopy` →
  `copyFile` → `createFile`) branches. `AndroidFileOps.exists()` /
  `isDirectory()` (L37-44) use `File.exists()`, which **follows** symlinks.
  An identical copy is in `term-ubuntu/.../TarballExtractor.java`
  (L318-322) and `term-ubuntu/.../AndroidFileOps.java`.
- **Mechanism:**
  1. An archive contains symlink `etc/x -> ../../OUTSIDE`. The symlink branch
     checks only the *parent's* containment and creates the link.
  2. A later regular-file entry named `etc/x` arrives. `removeNonDirectory`
     calls `exists(etc/x)`. That follows the link to a non-existent target and
     returns false, so the link is **not** removed.
  3. `createFile` → `new FileOutputStream(etc/x)` follows the link and
     creates `OUTSIDE` beyond the staging root.

  `rejectedEntries` stays 0. The hardlink branch reaches the same write: `link()`
  fails with EEXIST, the code falls back to `copyFile`, and the copy follows
  the link.
- **Evidence (PoC):** I compiled the unmodified `TarballExtractor.java` and
  `FileOps.java` with a `FileOps` that uses the same follow-symlink calls as
  `AndroidFileOps`, then fed it the two-entry archive above. Output:
  `rejected=0 outsideExists=true content=pwned`, and the file appeared next to
  `rootfs.staging/`, outside it.
- **Exploitability today:**
  - The pinned archive is trusted. F-Droid verifies the SHA-256 *before*
    extraction (`RootfsDownloader`). **The Full flavour verifies only after
    extraction** (`DigestInputStream` checked at L417-420, after
    `extractor.extract` at L412). For Full, the hash pin therefore offers no
    protection against this primitive. Only APK signing does.
  - Nothing in v0.1.0 feeds an untrusted archive to the extractor, so this is
    not remotely exploitable now.
  - However, the class javadoc claims the extractor "refuses path-traversal
    attempts". It is shared by every edition, and any future import, restore
    or "custom rootfs" feature would make it a real arbitrary-write bug.
- **Recommended fix direction:**
  - Decide replacement with `lstat`, not `File.exists`: unlink the final
    component whenever it is not a real directory, symlinks included.
  - Create files with `Os.open(path, O_CREAT|O_EXCL|O_WRONLY|O_NOFOLLOW)`.
  - Treat a directory entry that lands on an existing symlink as a rejection.
    Today `ops.mkdirs` is a no-op there, and `applyFinalDirectoryModes` later
    `chmod`s the link's target, which can be outside the root.
  - For Full, hash the asset before extracting, at the cost of one extra
    200 MB read, or document that APK signing is the only control.
  - Add tests for a dangling symlink followed by a file, a dangling symlink
    followed by a hardlink, and a symlink-to-outside-dir followed by a
    directory entry.

---

## HIGH

### H1 — The release gate still does not exercise the app's extractor

- **Files:** `tests/garden-arch/device/gate.sh` (`push_rootfs`, `mkroot`),
  `mkroot.sh`.
- The gate repacks the archive with Python `tarfile` and extracts it with
  toybox tar on the device. CODEX_HANDOFF admits that this is why the
  backslash bug passed "78/0". The gate was not changed to close that gap.
  The only evidence that the app's own extraction gives `-Qk` 0 missing is
  two manual probe runs recorded in `/tmp` logs on the workstation, which are
  not in the repo.
- **Failure mode:** The next extractor-specific defect passes the gate again:
  non-ASCII names (M2), rejected entries (M1), or hardlink fallback
  differences.
- **Fix direction:** Add an instrumentation test (or a gate step) that
  extracts the pinned archive with `TarballExtractor` + `AndroidFileOps`. It
  should then diff name, type, mode, size and link target against a
  `bsdtar -tvf` manifest generated at build time, and run `pacman -Qk` on
  that tree.

### H2 — The "Can't set permissions to 0777" warning is tolerated, not handled, and its root cause is misdescribed

- **Files:** `tests/garden-arch/device/zero.sh` (`clean_symlink_chmod`),
  `tests/garden-arch/device/stale.sh`, `docs/garden/arch/PACKAGE_MANAGER.md`
  ("Android cannot chmod a symbolic link"), CODEX_HANDOFF ("PRoot cannot
  apply that symlink metadata").
- **Finding:** No Linux kernel can chmod a symlink. On stock Arch, libarchive
  calls `lchmod`, gets `EOPNOTSUPP`/`ENOTSUP`/`ENOSYS`, and stays silent
  (`archive_write_disk_posix.c`, `set_mode`). The warning appears here
  because the PRoot/Android stack returns a **different errno** for that
  call, probably through `fchmodat2`, or through the `O_PATH` +
  `/proc/self/fd` fallback being translated by PRoot.

  Package contents are unaffected: pacman treats `ARCHIVE_WARN` as
  non-fatal. But the code neither suppresses nor fixes the warning, and the
  documented explanation is not the actual cause. Other tools that rely on
  that errno (`cp -a`, `rsync`, `tar`, `chmod -h`, systemd-tmpfiles) may also
  report errors under ThothTerm.

  The screenshots in this request show the warning for `libyyjson.so`,
  `libcurl.so`, `libdb*.so` and `/usr/lib/git-core/*`, which are normal
  package symlinks. Users see hundreds of these lines. 6404 appeared in the
  stale gate.
- **Test-side defects:**
  - `zero.sh` D runs `pacman -Qkk coreutils` only. It does not check the
    packages that actually warned. PACKAGE_MANAGER.md claims "the installed
    package's integrity check is clean", which overstates what the test
    checks.
  - CODEX_HANDOFF also says `pacman -Qkk` on the user's rootfs reported
    UID/GID mismatches on every file. That conflicts with D's `-Qkk` exit 0
    in the gate. The gate runs as `--root-id` root, while the user ran `sudo`
    from a `thoth` session, so the two paths differ and the gate does not
    represent the user path.
- **Fix direction:**
  - Capture the real errno on the device:
    `strace -f -e trace=fchmodat,fchmodat2 pacman -S …`, or a one-line C
    `lchmod` probe under PRoot.
  - Then fix it in the PRoot patch set (`garden-common/patches`): map
    `fchmodat(AT_SYMLINK_NOFOLLOW)` and `fchmodat2` on a symlink to
    `EOPNOTSUPP`, as Linux does. Garden-common.
  - Until that fix lands, run `-Qkk` on the warned packages, and correct the
    docs.

### H3 — `interrupt.sh` whitelist broadened, including `invalid or corrupted package`

- **File:** `tests/garden-arch/device/interrupt.sh`, case 2.
- **Finding:** Before Codex, case 2 required `exists in filesystem` and the
  documented `--overwrite` recovery. Now it also accepts `could not fully
  load metadata|invalid or corrupted package` as an "expected" interrupted
  state, then repairs and retries. `invalid or corrupted package` is the
  string pacman prints for **signature and integrity failures** (for example
  `invalid or corrupted package (PGP signature)`).
- **Failure mode:** A real signature or cache-corruption regression in this
  path becomes an INFO line followed by a successful repair, and the gate
  stays green.
- **Fix direction:** Remove `invalid or corrupted package` from the accepted
  set, or fail if the log contains `PGP signature` or `checksum`.

---

## MEDIUM

### M1 — Rejected archive entries never fail installation

- **File / function:** `RootfsManager.extractRootfs()` (L423-427) logs
  `rejected=` at debug level and continues. `TarballExtractor.extractEntry()`
  silently skips rejected names.
- **Why it matters:** This is the same silent-loss class as the backslash
  bug. A trusted, pinned image should contain zero rejectable entries.
  Codex's fix deliberately rejects legal Linux names such as
  `a\..\b`, which is conservative and fine, but any such file would vanish
  without notice.
- **Fix direction:** Throw if `rejectedEntries() > 0` for the pinned image,
  or record a build-time manifest count and compare against it.

### M2 — Archive names are decoded as US-ASCII

- **File / function:** `TarballExtractor.parseString` / `readDataString` /
  `parsePaxHeaders` decode names and link targets with US-ASCII.
- **Failure mode:** Every non-ASCII byte becomes U+FFFD, which is then
  written back as UTF-8 `EF BF BD`. Non-ASCII names are mangled. Distinct
  names can collide, and the later entry overwrites the earlier one. pax
  `path=` is UTF-8 by spec.

  The current image's `-Qk 0 missing` suggests no package-owned non-ASCII
  path exists today. Hook-generated `/etc/ca-certificates/extracted/cadir/*`
  names (for example Turkish, Hungarian and Greek CA labels) are probably
  mangled consistently on both the link side and the file side, so the hash
  links still resolve. This is unverified; check it with
  `bsdtar -tf rootfs.tgz | LC_ALL=C grep -nP '[\x80-\xff]'`.
- **Fix direction:** Decode as strict UTF-8 and reject malformed input. Strict
  UTF-8 cannot smuggle `/` or NUL. Add a test with a non-ASCII name.
  Garden-common.

### M3 — The docs say an interrupted transaction completes with `pacman -Syu`; the gate shows manual DB surgery is sometimes needed

- **Files:** the `RootfsManager.clearStalePacmanLock()` javadoc ("The
  interrupted transaction itself is completed by the user's next
  `pacman -Syu`"), `PACKAGE_MANAGER.md`, `interrupt.sh`
  `repair_missing_desc` (`rm -rf /var/lib/pacman/local/<pkg>-<ver>` +
  `pacman -S --dbonly`).
- **Finding:**
  - The gate needs an expert repair that no user doc spells out step by
    step.
  - `repair_missing_desc` returns 0 when `desc` exists, so PASS lines such as
    "damaged tzdata metadata restored" print even when nothing was damaged.
    Whether the missing-desc window was actually hit is nondeterministic, so
    "78 PASS" does not prove that path was exercised.
  - The whitelist `tzdata|vim-runtime|icu` is narrow, which is good.
- **Fix direction:**
  - Correct the javadoc.
  - Document the exact user recovery.
  - Have the test print `INFO not hit` vs `PASS repaired`.

### M4 — The provenance claim that `SigLevel = Required DatabaseOptional` "remain in effect" is not enforced on the shipped config

- **File:** `garden-arch/rootfs/build-rootfs.sh` L114-115 checks the
  **builder's** `/etc/pacman.conf`, and only for `Never`. The shipped
  `$T/etc/pacman.conf` (L264-268) is checked only for the sandbox lines.
- **Failure mode:** If the shipped config ever has `Optional`, `TrustAll`, or
  a per-repo override, the build still passes. The only `Required` check is
  in the test-only `provision.sh` that Codex added. The app's
  `pacmanKeyringScript()` has none.
- **Fix direction:** Run
  `pacman-conf --config $T/etc/pacman.conf SigLevel` and each repository's
  `SigLevel` in the build, and require `Required` with no
  `Optional`/`Never`/`TrustAll`. Mirror this in `pacmanKeyringScript()`.

### M5 — Source-provenance edge cases (GPL corresponding source)

- **File:** `garden-arch/rootfs/collect-sources.py`.
- **nss:** The pinned ALARM recipe commit cannot pass its own
  `makepkg --verifysource`. That means the shipped `ca-certificates-mozilla`
  binary was most likely **not** built from that exact recipe commit; it may
  have been built from a later commit or with `--skipchecksums`. Codex's
  hard-coded checksum permutation fails closed, which is good, but it
  confirms that the sources match the hashes. It does not confirm that the
  sources match the binary.
- **zlib:** The patch is regenerated with `git format-patch --abbrev=9` and
  must still match the recipe checksum, which is acceptable.
- **Mixed collection:** ROOTFS_PROVENANCE says 135 of 138 source rows were
  *reused* from an earlier recollection rather than collected from the final
  captured inputs in one run.
- **Fix direction:**
  - Find the ALARM commit whose recipe verifies for nss 3.130-1.
  - Run a single clean collection against the final package TSV before
    publication.

### M6 — F-Droid keeps the 200 MB archive forever

- **File:** `RootfsManager` (`downloadedImage`, L122/L284-295). It is never
  deleted after a successful install.
- **Failure mode:** About 200 MB of permanent duplicate storage. It also
  keeps C1's auto re-extract path prompt-free for F-Droid.
- **Fix direction:** Delete the archive after `writeState()`.

---

## LOW

### L1 — `LanUploads.begin()` checks and inserts under separate locks

- **File / function:** `garden-common/.../lan/LanUploads.java`, `begin()`
  (L89-115). The "terminal busy" and `MAX_UPLOADS` checks run in one
  `synchronized` block. `host.target()` and `UploadBatch.begin()` run
  unlocked, and `put` happens in a second block.
- **Failure mode:** Two concurrent begins for the same terminal can both
  succeed. Only the same authenticated browser session can trigger this.
  Impact: two batches into the same cwd, or more than 8 uploads.
- **Fix direction:** Reserve a placeholder under the first lock.
- **Related:** `end()` does not clear `active`, a minor leak.

### L2 — Stale-gate provisioning does not use the app's keyring script

- **File:** `tests/garden-arch/device/provision.sh`, `upstream-stale` mode.
- It skips the signature-fixture verification but still prints
  `keyring-verified`, and gate.sh labels the result "PASS stale-image keyring
  provisioning". The real package signatures are verified later by `-Syu`,
  so security is fine. However, the label overstates what was checked, and
  this path differs from `pacmanKeyringScript()`.

### L3 — `RootfsState.write()` is not atomic

- It writes in place, with no temp file + rename and no fsync. A torn write
  makes `read()` return `complete=false`, which feeds C1. The risk is low
  because the file is written once, but fixing C1 removes the consequence.

### L4 — Missing hardlink and backslash test

- `TarballExtractorTest` has no hardlink whose target name contains a
  backslash, although the path is consistent by construction.
- `extractsLiteralBackslashesWithoutMakingDirectories` would fail on a
  Windows host (JVM separator). That does not matter for CI.

### L5 — `ThothfetchTest` change is fine

- The `NO_COLOR` removal only isolates the test from the developer shell. No
  assertion was weakened.

---

## Codex's own change: verification detail

`sanitizeEntryName` (L335-358) is correct.

- **Safety pass:** It checks a copy with `\` mapped to `/`, and rejects a
  leading separator and any `..` component.
- **Destination:** It is built from the raw name split on `/` only.
- **Cases checked by reading the code:**
  - `\..`, `..\`, `a\..\evil`, `\etc\passwd`, `\\server\x`, and `\` alone
    are all rejected.
  - `C:\x` and `C:/x` are kept as a literal relative component named `C:`,
    which is harmless on Linux.
  - `...`, `..x` and `x..` are accepted as names, which is correct.
  - NUL and the 4096-character limit are unchanged.
  - There are no percent-decoding paths, so encoded traversal like `%2e%2e`
    is literal and safe.
- **Hardlink targets** go through the same function, so the source and
  target spellings agree.
- **Containment:** The canonical-path checks for parents and hardlink
  targets are unchanged. TOCTOU is not material because the staging dir is
  app-private and single-writer.

The fix was **not** ported to `term-ubuntu/.../TarballExtractor.java`, which
still has `raw.replace('\\', '/')` at L337. Ubuntu/Debian systemd ships the
same `system-systemd\x2d*.slice` units, so the primary Ubuntu app likely
has the same silent file loss. See Garden-common candidates.

## TEST QUALITY CONCERNS

- H1: the gate does not use the app extractor.
- H2: `-Qkk` runs only on coreutils, and the gate runs as root rather than as
  the user path.
- H3: `invalid or corrupted package` was whitelisted.
- M3: repair PASS lines print even when the repair path was not hit.
- L2: the stale provisioning label overstates the check.
- `stale.sh`: the sshd/systemd awk filter is exact and narrow (good). The
  `.pacnew` and journal-mode allowlist is exact-match (good). Every warned
  path must be a resolving symlink (good).
- No unit test covers `RootfsManager`'s install, re-extract decision (C1),
  or the extractor's symlink-replacement behaviour (C2).
- About the "14 device failures fixed": the diff shows only harness changes
  (exit-code propagation, `push_rootfs` guards, the provisioning mode, and
  the classifications above). No product code changed for them. That is
  consistent with the "harness bugs or PRoot limitations" claim, except
  where H2 and H3 note that the classification is tolerance rather than
  proof.

## DOCUMENTATION CLAIMS NOT FULLY PROVEN

- **"78 PASS / 0 FAIL"** in ROOTFS_PROVENANCE: it was produced with toybox
  extraction, not the app's (H1). The provenance doc has no caveat.
- **"Android cannot chmod a symbolic link … known PRoot model limitation"**:
  the root cause is an errno mismatch, not chmod capability (H2).
- **"The tolerance applies only when … the installed package's integrity
  check is clean"**: only coreutils is checked (H2).
- **"SigLevel = Required DatabaseOptional remain in effect"**: not enforced
  on the shipped config (M4).
- **"Interrupted transaction is completed by the next pacman -Syu"** (code
  javadoc): contradicted by `repair_missing_desc` (M3).
- **HOME preservation:** it was tested only for `adb install -r` with
  pin/version changes. The isReady-probe paths in C1 were never tested.
- **"Data-loss fix" (HANDOFF):** it addresses only the pin-change trigger
  (C1).
- **The handoff's on-device claims** (Qk logs, APK hashes, 7/7 JNI, File
  Bridge browser gate): their evidence is in workstation `/tmp`, not in the
  repo, so they cannot be audited from source.
  - JNI 7/7 *does* match source: `process.c` registers 5, `termio.c`
    registers 2, and `consumer-rules.pro` keeps only
    `TermIO$Native` / `Process$Native` `native <methods>`. That is narrow,
    with no broad keeps.
- **`sourceUrl`:** points to an unpublished release tag. The F-Droid flavour
  returns 404 until it is published, which is expected but should be a
  release checklist item.

## GARDEN-COMMON CANDIDATES

- The C1 fix: install vs runnable readiness, and HOME-preserving repair.
  `RootfsManager` and `GardenSetupActivity` are shared by every edition.
- The C2 fix: lstat-based replacement, `O_NOFOLLOW`, and pre-extraction hash
  for embedded assets.
- M1 (fail on rejected entries) and M2 (UTF-8 names).
- Backslash preservation: port to `term-ubuntu`'s duplicate
  `TarballExtractor` (and `AndroidFileOps`), or better, make term-ubuntu
  consume garden-common's extractor. The duplication is how the fix got
  missed.
- The PRoot `fchmodat`/`fchmodat2` symlink errno fix (H2). It lives in
  `garden-common/patches` and affects apt/dpkg too.
- The `GuestProcesses`-guarded stale-lock pattern is already common. Keep the
  pacman specifics out of it.

## ARCH-SPECIFIC ITEMS

- `DisableSandboxFilesystem` only (Landlock), `DownloadUser = alpm`, and the
  build-time `--disable-sandbox` for builder and QEMU use only (the
  `build-rootfs.sh check` change is correct and does not touch the shipped
  config).
- The `pacman-key` keyring provisioning and offline signature fixture, plus
  the SigLevel enforcement (M4).
- `db.lck` recovery semantics, the interrupted-transaction docs and
  `interrupt.sh`.
- `collect-sources.py`: the nss and zlib workarounds, Mercurial support, and
  `source_aarch64` selection. These are ALARM-specific, and the hard-coded
  nss and zlib exceptions should be removed once upstream fixes them.
- The sshd/systemd hook-message classification in `stale.sh`.
  `systemd` is not PID 1 under PRoot, so the `System has not been booted
  with systemd` lines are expected.

## RELEASE BLOCKERS

1. **C1:** an automatic, prompt-free re-extract can delete HOME on an
   installed system.
2. **C2:** the extractor's documented traversal guarantee is false. It must
   be fixed or the guarantee withdrawn, at minimum before any feature feeds
   it non-pinned input. For the Full flavour, also hash before extracting.
3. **The earlier primary-app HOME loss** (incremental install) remains
   unexplained, as CODEX_HANDOFF itself states.
4. **The rootfs release asset** at `sourceUrl` must be published, with
   freshness re-checked, before the F-Droid flavour can work.
5. **Strongly recommended before tagging:** H1 (the gate must exercise the
   app extractor) and H3 (remove the `invalid or corrupted package`
   whitelist).

## CLAUDE RECOMMENDED NEXT ACTION

1. Decide on C1 and C2. Both are small, local changes in garden-common:
   - C1: guard `deleteTree(rootfsDir)` behind `!state.isInstalled()`, plus a
     consented repair path that preserves HOME.
   - C2: in `removeNonDirectory`, check `isSymlink` first, and use
     `O_NOFOLLOW` on create.

   Add JVM tests for both, including the PoC archive from C2. Port the
   backslash and C2 fixes to `term-ubuntu`'s extractor copy.
2. Add the app-extractor manifest comparison to the device gate (H1). Narrow
   the H3 whitelist. Run `-Qkk` on the warned packages (H2).
3. Capture the real errno behind `Can't set permissions to 0777` on the
   device with one `strace`, then decide between a PRoot patch and
   documented tolerance.
4. Enforce SigLevel on the shipped `pacman.conf` (M4). Delete the F-Droid
   archive after install (M6).
5. Only then rebuild, re-run the existing gates once, and proceed to release
   and the F-Droid MR work.
