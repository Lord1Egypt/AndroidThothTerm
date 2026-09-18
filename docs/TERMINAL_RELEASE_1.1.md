# ThothTerm Terminal Emulator 1.1.0 — Diagnostics Release

## Identity

| Item | Value |
| --- | --- |
| Product | ThothTerm Terminal Emulator |
| Launcher label | ThothTerm |
| Positioning | Terminal emulator for Android |
| Production applicationId | `com.thothterm` |
| Debug applicationId | `com.thothterm.devel` |
| versionName | `1.1.0` |
| versionCode | `10100` |
| Tag | `terminal-v1.1.0` (annotated) |
| Release title | ThothTerm Terminal Emulator 1.1.0 — Diagnostics Release |
| Repository | https://github.com/Lord1Egypt/AndroidThothTerm |

Terminal 1.1.0 = **ThothTerm Terminal Emulator 1.0 Golden Baseline + the
completed Diagnostics / Structured Logging feature**. It contains no Ubuntu,
PRoot, rootfs, or Linux-edition code.

## Relationship to terminal-v1.0.0

`terminal-v1.0.0` (`7a3c88e52075a0a7abaa9dd61150c122c119718f`) remains the
**immutable Golden Baseline** and trusted recovery point. It has not been
modified, moved, deleted, or retagged. Terminal 1.1.0 builds on top of it; it
does not replace historical provenance.

Base chain:

```
terminal-v1.0.0        7a3c88e  release: ThothTerm Terminal Emulator 1.0 golden baseline
55b4ba9                docs: record terminal-v1.0.0 golden baseline commit and release
37a37ad                docs: overhaul README for ThothTerm 1.0
5308000                feat: add ThothTerm diagnostics and structured logging  (cherry-pick of 7e716b7)
<release commit>       release: prepare ThothTerm Terminal Emulator 1.1.0
```

The Diagnostics implementation was authored on `feature/thotterm-linux` as
commit `7e716b7` and cherry-picked onto the terminal release branch as
`5308000`. It was audited before use and contains no Linux/Ubuntu code (see the
audit below).

## What's new in 1.1.0

- `Settings → Diagnostics`: View logs, Log level, Developer logging, Clear logs,
  Export logs.
- Live in-app log viewer with timestamp, level, category, and message; search;
  level and category filters; pause/resume; auto-scroll; clear; export.
- Levels `ERROR` / `WARN` / `INFO` / `DEBUG` / `VERBOSE` (default `INFO`).
  Developer logging is off by default and gates `DEBUG`/`VERBOSE`.
- Persistent rotating diagnostic logs in application-private storage.
- Privacy-safe logging: terminal input, terminal output, commands, passwords,
  tokens, environment values, clipboard, SSH secrets, and file contents are
  never recorded.
- Diagnostic events for app, UI, session, PTY, shell, installer, and storage
  activity.

Existing capabilities retained (not new in 1.1.0): Extra Keys NAV/SYM toolbar
with one-shot/locked CTRL/ALT, Arabic/RTL rendering, multiple terminal windows,
Dark/Light/System/AMOLED themes, and the terminal-engine/PTY architecture.

## Logger architecture

Central API: `com.thothterm.logging.ThothLog`.

- Threshold filtering with an effective level (developer logging gates
  `DEBUG`/`VERBOSE`).
- In-memory bounded ring buffer (4,000 records) for the live viewer.
- Bounded asynchronous queue (4,096) and a single low-priority daemon writer
  thread; no disk I/O on the calling thread.
- Logcat mirroring at the corresponding severity.
- Failures are swallowed; logging can never crash the terminal, and the
  renderer is independent of the logger.
- Structured categories: `APP`, `UI`, `SESSION`, `PTY`, `SHELL`, `INSTALLER`,
  `STORAGE`, `NETWORK`. Reserved (unused) categories: `RUNTIME`, `LINUX`,
  `ROOTFS`, `PROOT`, `WEB`, `SECURITY`. The terminal edition emits no fake
  Linux events.

## Privacy rules

Logs describe system behaviour only. Never logged: terminal keyboard input,
command text, terminal output, passwords, API keys, authentication tokens,
environment variable values, clipboard contents, SSH credentials, or file
contents. Messages are normalized to a single line and length-capped. System
metadata such as PID, rows/columns, session counts, exit codes, and error
class/type is acceptable.

## Persistence and rotation

- Location: application-private `<files>/logs/`.
- Files: `thotterm.log`, `thotterm.1.log`, `thotterm.2.log`, `thotterm.3.log`.
- Limit: 4 MB per file, 4 files, ~16 MB total; the oldest file is deleted
  automatically. Logging never grows without bound.
- Clearing logs removes persisted files, the in-memory buffer, and queued
  records.

## Export

Export uses the Android Storage Access Framework (`CreateDocument("text/plain")`),
off the main thread. The human-readable header may include app version,
Android/API level, ABI, app flavor, log level, and export time. No device
serial, account, or other private identifier is included.

## Build commands

```bash
export JAVA_HOME=/home/lordegypt/PocketCLaw/.tooling/jdk-17
export ANDROID_HOME=/home/lordegypt/Android/Sdk
export ANDROID_SDK_ROOT=/home/lordegypt/Android/Sdk
export GRADLE_USER_HOME=/tmp/thothterm-gradle-user
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :term:clean \
  :term:assembleFullDebug :term:assemblePlayDebug \
  :term:assembleFullRelease :term:assemblePlayRelease \
  :term:bundleFullRelease :term:bundlePlayRelease \
  -x :term:elfcleaner

./gradlew :emulatorview:testDebugUnitTest :libtermexec:testDebugUnitTest \
  :term:testFullDebugUnitTest :term:testPlayDebugUnitTest -x :term:elfcleaner
./gradlew :term:lintFullDebug -x :term:elfcleaner
```

`-x :term:elfcleaner` is the documented workaround because the host
`elf-cleaner` executable is absent; it only skips an optional symbol-cleanup
pass.

## Artifacts and SHA-256

Produced by one clean build on 2026-09-18. Build outputs are not committed.

| Artifact | Path | Size (bytes) | SHA-256 | Package | Signing | ABIs |
| --- | --- | --- | --- | --- | --- | --- |
| Full debug APK | `term/build/outputs/apk/full/debug/term-full-debug.apk` | 6,764,123 | `340d2ba51c0de239c432111f708a189b785161ff7dc2b9d38e51ae0f0793ac77` | `com.thothterm.devel` | Android debug key | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Play debug APK | `term/build/outputs/apk/play/debug/term-play-debug.apk` | 6,763,372 | `9371dcd50a90d066a96b853020445d96cda9370c41c62a5161f022e3ad09fcf5` | `com.thothterm.devel` | Android debug key | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Full release APK | `term/build/outputs/apk/full/release/term-full-release-unsigned.apk` | 5,577,256 | `72f65237001f249a3c0ba2f4ecd06f33874cb7f3af3da96ed2ecb834b5808f37` | `com.thothterm` | **unsigned** | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Play release APK | `term/build/outputs/apk/play/release/term-play-release-unsigned.apk` | 5,575,956 | `93dafaf5f4effaaa10e5d6f156e15cce388dc67e115a55a41c7d890a8da33571` | `com.thothterm` | **unsigned** | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Full release AAB | `term/build/outputs/bundle/fullRelease/term-full-release.aab` | 4,858,337 | `e3d3714eb673cb497e8470ea2c7eeb046f6357ffef38b9d73ebcc894814fee38` | `com.thothterm` | **unsigned** | all four |
| Play release AAB | `term/build/outputs/bundle/playRelease/term-play-release.aab` | 4,857,025 | `909e15c29055b215687f9b74d3d16c599cdeba94a716d1be04e5879f824728d4` | `com.thothterm` | **unsigned** | all four |

## Signing state

No ThothTerm production signing credentials exist in this repository
(`signing.properties` is absent). No key was invented. Debug APKs are signed
with the Android debug key for device testing only. Release APKs and AABs are
**unsigned**. They must not be described as production-signed.

## Lint

`:term:lintFullDebug`: **1 error, 67 warnings** — unchanged from the 1.0
baseline. The single error is the pre-existing `GestureBackNavigation` finding
at `Term.java:653` (predictive-back migration is out of scope for this
release). The Diagnostics feature introduced no new lint findings.

## Tests

`:emulatorview:testDebugUnitTest`, `:libtermexec:testDebugUnitTest`,
`:term:testFullDebugUnitTest`, and `:term:testPlayDebugUnitTest` are
`NO-SOURCE` (the terminal edition has no automated unit tests). `git diff
--check` is clean.

## Manual acceptance (owner-reported)

The Diagnostics feature was tested on a physical device. The following
behaviours were reported verified:

- Logs screen opens.
- Startup events appear.
- `DEBUG` logging works with Developer logging enabled.
- PTY resize events appear.
- Session create/switch events appear.
- Export works.
- No terminal commands or terminal output were present in exported logs.

This build environment had no arm64 Android device or `adb` attached, so this
repository does not independently re-verify the physical checklist; the items
above are retained as the owner's device-verification record. The automated
build, packaging, and lint evidence in this document was produced here.

## No Ubuntu contamination

Before the release build the terminal edition was checked:

- `settings.gradle` does not include `term-ubuntu`; no module depends on it.
- No tracked file path contains `term-ubuntu`, `ubuntu`, `proot`, or `rootfs`.
- The release APK contains only the terminal native libraries (`libappwrap.so`,
  `libcmd-t1plus.so`, `libexec-t1plus.so`, `libterm-system.so`) across the four
  ABIs; no PRoot library.
- No Ubuntu rootfs asset (`.tgz`/`.tar.gz`) or `com.thothterm.ubuntu` string is
  present in the APK.
- APK sizes are 5.5–6.8 MB (terminal scale), not tens of MB.
