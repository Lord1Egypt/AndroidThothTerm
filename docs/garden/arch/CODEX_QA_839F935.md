# CODEX LOCAL QA — CLAUDE 839f935

Checked out detached at `839f935b35e98ffbd0616d5090902569dea24c72` in `wt-qa-839f935`. Reviewed `CLAUDE_FIX_HANDOFF.md` and `git diff d977d13..839f935` (43 files), with the earlier `3585730..839f935` changes as context. No source was changed. No application was installed, replaced, cleared, or uninstalled.

## Required verdict matrix

| Gate | Result | Evidence and limit |
|---|---|---|
| Diff review | **PASS, static** | Public `Os.remove` with `lstat` directory guard; LAN sign-out and registration use the same lock; QA ID drives AGP, PRoot and Ubuntu native build; terminal-ready notification precedes optional provisioning. Physical behavior remains unverified. |
| Real SDK 36 compile | **PASS** | Six real debug APK/test-APK Gradle targets built, including `AndroidFileOps`, against installed SDK 36. The previous hidden `Os.unlink` compile failure is gone. |
| Gradle | **PASS, builds** | Six debug targets and five release targets built with real AGP/JDK/NDK. Full unit and lint gates fail separately below. |
| Unit tests | **FAIL** | Full `:garden-common:testDebugUnitTest`: 313 tests, 1 failure in `FileOpsContractTest.jvmFileOpsMeetsTheContract`; details below. Arch Full/F-Droid 28/28 each and Trixie Full 20/20 passed. Targeted LAN/setup 159/159 and lifecycle/API/QA runtime 51/51 passed. |
| Lint | **FAIL** | `:term-ubuntu:lintFullDebug` stops on one `GestureBackNavigation` error; details below. Garden common, Arch Full/F-Droid and Trixie Full lint tasks passed. |
| R8/JNI | **PASS** | Five QA release variants built with R8; `apkanalyzer dex code` found all seven exact `RegisterNatives` names/signatures in each APK. |
| 16 KB | **PASS** | `zipalign -c -P 16 4` passed for five debug and five release APKs; all native ELF LOAD alignments checked were at least `0x4000`. |
| QA APK real package isolation | **PASS, APK proof** | Real `aapt2` reads `com.thothterm.{arch,debian,ubuntu}.qa.lifecycle` from five APKs, and Arch test APK targets `com.thothterm.arch.qa.lifecycle`. None is a protected primary ID. No-property Arch F-Droid APK is `com.thothterm.arch`. |
| QA PRoot runtime isolation | **FAIL, physical proof blocked** | Built ARM64 QA PRoot and shmem binaries carry only QA paths; native checker passed on every QA APK, and no-property production APK carries production paths. No device exists to prove PRoot actually starts within QA private storage. |
| Android extractor | **FAIL, device unavailable** | Instrumentation APK built with the QA target; no Android device was connected, so `AndroidFileOps` malicious fixtures and fd exhaustion could not run. |
| Ubuntu host extractor | **FAIL, gate exit 1** | SHA correct, 6,564 entries, 0 rejected, exact 6,564-path manifest; shared JVM chmod contract failed on owner-write-only file. |
| Trixie host extractor | **FAIL, gate exit 1** | SHA correct, 7,050 archive entries, 0 rejected, exact 7,049-path manifest; `NetLock_Arany_=Class_Gold=_Főtanúsítvány.crt` preserved; same JVM chmod failure. |
| Arch host extractor | **FAIL, gate exit 1** | SHA correct, 33,640 archive entries, 0 rejected; same JVM chmod failure and a verifier `AccessDeniedException` at mode-0110 `usr/lib/dbus-daemon-launch-helper`. A separate QA-copy manifest comparison found 33,639/33,639 exact paths after temporary access for hashing, then restored mode 0110. The standard gate still fails. |
| HOME lifecycle | **FAIL, physical proof blocked** | Shared lifecycle tests pass, including crash-step recovery; no isolated app can be launched without a device. HOME sentinel/inode tests were not run. |
| PRoot patch 0006 | **FAIL, physical proof blocked** | ARM64 PRoot compiled with patch 0006, but pacman/fchmodat2 on Android could not run. Host PRoot gate also cannot compile its libarchive probe because `archive.h` is absent locally. |
| Permission warnings | **before: unmeasured / after: unmeasured** | No device; no honest comparison is possible. |
| systemd ENOSYS | **before: unmeasured / after: unmeasured** | No device; `SYSTEMD_IN_CHROOT=1` guest behavior is unverified. |
| LAN sign-out race | **FAIL, physical proof blocked** | New deterministic race tests pass on both JVM modules; no phone/browser/actual file-descriptor stress could run. |
| Ubuntu physical terminal readiness | **FAIL, device unavailable** | Static ordering and setup-state tests pass; no download/extract/finalize/terminal/provisioning timings or external reviewer reproduction could be collected. |
| Arch physical | **FAIL, device unavailable** | No isolated Arch PRoot/pacman/chmod/systemd run. |
| F-Droid path | **FAIL, physical proof blocked** | Both F-Droid debug and minified APKs have zero rootfs entries. Source uses download consent, verified SHA, and removal after successful install; wrong-hash/partial/retry and terminal handoff remain unverified on Android. |

## Reproduction and findings

### 1. JVM FileOps contract blocks full unit and all host extractor gates — high, QA gate

**File/function:** `garden-common/src/sharedTestFixtures/java/com/thothterm/linux/JvmFileOps.java:110`, `chmodNoFollow`; fixture `FileOpsContract.java:127`, `chmodWorksOnUnreadableFile`.

**Reproduce:** With real JDK 17, run `./gradlew --offline :garden-common:testDebugUnitTest`, or `tests/garden-common/extractor/host-gate.sh` against any of the three pinned rootfs archives. The fixture sets `wo` to mode `0200`, then requests `0640`. `JvmFileOps` uses `Files.setAttribute(path, "unix:mode", ..., NOFOLLOW_LINKS)`.

**Expected:** Owner can change permissions on the owner-write-only regular file without opening it for reading; the contract and host gates pass.

**Actual:** `java.nio.file.AccessDeniedException: .../chmodWorksOnUnreadableFile/wo`; the unit run stopped at 313 tests/1 failure, and each host gate records `FAIL contract chmodWorksOnUnreadableFile`. This is the same gate issue observed during the previous `d977d13` QA round; it was not in the three findings available to Claude in the handoff. No code fix was attempted.

**Logs:** `/tmp/qa-839f935-unit.log`, `/tmp/qa-839f935-ubuntu-host.log`, `/tmp/qa-839f935-trixie-host.log`, `/tmp/qa-839f935-arch-host.log`.

### 2. Arch host manifest verifier cannot read its mode-0110 helper — high, QA gate

**File/function:** `tests/garden-common/extractor/manifest.py` manifest verification path via `ExtractorGate`; extracted `usr/lib/dbus-daemon-launch-helper` is mode `0110`.

**Reproduce:** Run the standard host gate against the pinned Arch archive `03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1`.

**Expected:** Manifest verifier compares the extracted tree exactly, including owner-nonreadable files.

**Actual:** Extraction and SHA check pass with 33,640 entries and zero rejections, then `FAIL real archive: java.nio.file.AccessDeniedException: .../rootfs/usr/lib/dbus-daemon-launch-helper`. An independent QA-only comparison on the extracted copy temporarily enabled read access, recorded the original `0110` mode, found 33,639 expected and actual paths with zero differences, and restored `0110`. The unmodified standard gate still exits 1. No source or pinned archive was changed.

**Logs:** `/tmp/qa-839f935-arch-host.log`, `/tmp/qa-839f935-arch-manifest-workaround.log`.

### 3. Ubuntu SDK 36 lint failure — medium, release gate

**File/function:** `term-ubuntu/src/main/java/jackpal/androidterm/Term.java:818`, `onKeyUp` / `KEYCODE_BACK` handling.

**Reproduce:** Run `./gradlew --offline :term-ubuntu:lintFullDebug` with SDK 36.

**Expected:** Lint task succeeds.

**Actual:** `GestureBackNavigation` error: “If intercepting back events, this should be handled through the registration of callbacks.” Lint reports 1 error and 137 warnings and fails the task. The `KEYCODE_BACK` branch existed at `d977d13` (then line 798), so this is an existing gate failure exposed by the real build, rather than a new change in this diff. No fix was attempted.

**Log:** `/tmp/qa-839f935-lint.log`; report `term-ubuntu/build/intermediates/lint_intermediate_text_report/fullDebug/lintReportFullDebug/lint-results-fullDebug.txt`.

### 4. Physical gate unavailable — critical QA coverage gap, no observed product defect

**Reproduce:** `/home/lordegypt/Android/Sdk/platform-tools/adb devices -l` returned only `List of devices attached` (empty list), twice; `adb mdns services` was also empty. No installed protected-package inventory could be recorded because no device was present. No `adb install`, `pm clear`, or uninstall command was run.

**Expected:** A physical device with an isolated QA APK permits Android extractor, HOME, PRoot, pacman, systemd, LAN and Ubuntu readiness tests without touching primary apps.

**Actual:** These checks cannot be run. This does **not** show a product failure. It prevents a `QA PASS`, and no claim is made about permission-warning or systemd message counts. No screenshots or device logs exist.

## Passing evidence and commands

- Real toolchain: JDK 17, installed Android SDK 36, AGP/Gradle, NDK; no fake SDK or stub build. Verified local pinned archive SHA-256 values before supplying them to ignored Gradle download cache (Arch release URL returned HTTP 404). Debug command built `:garden-arch:assembleFullDebug :garden-arch:assembleFdroidDebug :garden-arch:assembleFullDebugAndroidTest :garden-debian:assembleFullDebug :term-ubuntu:assembleFullDebug :term-ubuntu:assembleFdroidDebug`. **BUILD SUCCESSFUL**, 276 tasks. Log: `/tmp/qa-839f935-build.log`.
- Release command built `:garden-arch:assembleFullRelease :garden-arch:assembleFdroidRelease :garden-debian:assembleFullRelease :term-ubuntu:assembleFullRelease :term-ubuntu:assembleFdroidRelease`. **BUILD SUCCESSFUL**, 345 tasks. `aapt2`, `zipalign -c -P 16 4`, ELF `readelf -lW`, and `apkanalyzer dex code` checks pass on all five release APKs. Log: `/tmp/qa-839f935-r8.log`.
- `aapt2` reads the five QA APKs' actual package IDs and the Arch instrumentation target. `gate_check_runtime_ids` accepts all five real APKs; QA PRoot has `/data/data/com.thothterm.arch.qa.lifecycle/files/linux/runtime/loader/loader` and shmem uses its QA tmp path, with no protected ID leak. The no-property Arch F-Droid APK rebuilt successfully as `com.thothterm.arch` with production loader path. `runtime-ids-host-check.sh` passes 24/24 real host compiled PRoot/shmem checks; both checker/device-gate self-tests pass. Log: `/tmp/qa-839f935-runtime-host.log`.
- Targeted real Gradle LAN and `SetupStateTest`: Garden 81/81, Ubuntu 78/78. Targeted lifecycle, SDK API and runtime ID tests: Garden 28/28, Ubuntu 23/23. Logs: `/tmp/qa-839f935-targeted-unit.log`, `/tmp/qa-839f935-core-targeted-unit.log`. These do not replace the failed full suite or on-device stress.
- Independent Arch Full and F-Droid unit suites passed 28/28 each; Trixie Full unit suite passed 20/20; Trixie Full lint passed. Log: `/tmp/qa-839f935-arch-debian-tests-lint.log`.
- Host archives: Ubuntu `5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd`; Trixie `f6520ff1c6eeb28ead2d175e6733da4c970459a291b1bd60f992f1655c5d0677`; Arch `03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1`. Every extraction had zero rejected entries. UTF-8 and literal-backslash adversarial cases passed on the JVM, and the external sentinel remained unchanged. The host gate result is still FAIL for the reasons above.
- The host PRoot 0006 gate was attempted; it stopped during compilation of `tests/garden-common/proot/alpm-extract.c` because this machine lacks `archive.h` (`libarchive-dev`). Log: `/tmp/qa-839f935-proot-host.log`. This is a local dependency block, not a PRoot verdict.

## FINAL VERDICT

**RETURN TO CLAUDE**

The SDK 36 compile, QA APK identity and static runtime isolation, R8/JNI, and 16 KB checks pass. The full unit and lint gates fail, all three standard host extractor gates exit nonzero, and the required physical Android evidence is unavailable. Claude owns any source changes. Repeat physical QA with an isolated application ID once a device is connected; do not touch the primary packages.
