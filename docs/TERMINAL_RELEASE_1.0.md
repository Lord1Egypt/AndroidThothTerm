# ThothTerm Terminal Emulator 1.0 release baseline

Release documentation for the first stable, terminal-only product release.

## Product identity

| Item | Value |
| --- | --- |
| Product name (store-facing) | ThothTerm Terminal Emulator |
| Launcher label | ThothTerm |
| Public description | Terminal emulator for Android |
| Release application ID | `com.thothterm` |
| Debug application ID | `com.thothterm.devel` |
| Version name | `1.0.0` |
| Version code | `10000` |
| Edition scope | Terminal emulator only. No Ubuntu, PRoot, Linux rootfs, or package manager. |

The future Linux edition is reserved conceptually as `com.thothterm.linux` and is
**not** implemented, built, or published by this release. See
`docs/EDITION_PLAN.md` for the edition split and shared-engine strategy.

This release is the frozen **golden baseline**; see `docs/GOLDEN_BASELINE.md`.

## Versioning scheme

This is a new ThothTerm product baseline, independent of the upstream TermOne
Plus 5.7.0 lineage. The version code is derived from the semantic version so
future upgrades stay monotonic and human-decodable:

```text
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
```

Examples: `1.0.0 -> 10000`, `1.0.1 -> 10001`, `1.1.0 -> 10100`, `2.0.0 -> 20000`.

`10000` is deliberately well above the legacy upstream `570`, so a future
upgrade over any earlier ThothTerm build is always accepted. The upstream
product version (`5.7.0`) and the `full` flavor `/X` version-name suffix were
removed from user-visible product UI. Upstream attribution and licensing are
unaffected and remain in `NOTICE` and the source headers.

## Feature summary (frozen in the terminal edition)

- Android terminal emulator with a Java PTY/process engine.
- Foreground-service-owned sessions that survive activity stop/recreate.
- Multiple terminal windows with a Windows list and New/Close window support.
- Polished ThothTerm visual identity with an adaptive, legacy, round, and
  Android monochrome launcher icon.
- Dark, Light, System, and AMOLED app themes.
- Extra Keys toolbar with NAV and SYM pages, fit-to-screen without horizontal
  scrolling.
- CTRL/ALT one-shot and lock behavior with textual `[1]`/`[L]` state labels.
- Terminal cursor/text alignment fix sharing one coordinate system between
  text and cursor.
- UTF-8 support.
- Arabic contextual shaping and RTL/BiDi terminal rendering (API 23+ shaped
  runs; API 16-22 `StaticLayout` fallback).
- Mixed Arabic/English/numeric rendering with logical-to-visual cursor and
  selection mapping.
- Arabic natural glyph advance (no artificial tracking) with a forced-LTR
  paragraph base so ASCII/LTR commands are never reordered.
- Right-edge cell-clipping fix (fractional cell-width centering).
- Friendly `app_HOME` prompt that shortens `$HOME` without changing the
  filesystem or `pwd`.
- Reorganized Settings and cleaned-up navigation drawer.
- Renamed user-facing wording (Show keyboard, Restart terminal, Keep screen
  awake / Allow screen to sleep, Keep Wi-Fi on / Allow Wi-Fi to sleep).

## Supported ABIs

`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (no ABI filters are declared).

Native outputs packaged per ABI: `libappwrap.so`, `libcmd-t1plus.so`,
`libexec-t1plus.so`, `libterm-system.so`.

## Build configuration

| Item | Value |
| --- | --- |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 16 |
| Build tools | 37.0.0 |
| NDK | 23.2.8568313 |
| CMake (AGP) | Android SDK CMake 3.22.1 |
| Gradle wrapper | 9.7.1 |
| Android Gradle Plugin | 9.4.0 |
| JDK | Temurin 17.0.20.1 |
| Kotlin | disabled in all modules (`enableKotlin = false`) |
| Java source/target | 1.8 bytecode |
| Flavors | `play`, `full` on the `catalog` dimension |
| Release shrinking | Disabled (`minifyEnabled = false`) |
| Release signing | Optional `signing.properties`; **not present** |

`play` does not request/manage all-files access. `full` adds
`MANAGE_EXTERNAL_STORAGE` and enables the request flow.

## Build commands

Environment:

```bash
export JAVA_HOME=/home/lordegypt/PocketCLaw/.tooling/jdk-17
export ANDROID_HOME=/home/lordegypt/Android/Sdk
export ANDROID_SDK_ROOT=/home/lordegypt/Android/Sdk
export GRADLE_USER_HOME=/tmp/thothterm-gradle-user
export PATH="$JAVA_HOME/bin:$PATH"
```

Debug (regression) builds:

```bash
./gradlew :term:clean :term:assembleFullDebug :term:assemblePlayDebug -x :term:elfcleaner
```

Unsigned release build (no signing config present):

```bash
./gradlew :term:assembleFullRelease :term:assemblePlayRelease \
  :term:bundleFullRelease :term:bundlePlayRelease -x :term:elfcleaner
```

Tests and lint:

```bash
./gradlew :emulatorview:testDebugUnitTest :libtermexec:testDebugUnitTest \
  :term:testFullDebugUnitTest :term:testPlayDebugUnitTest -x :term:elfcleaner
./gradlew :term:lintFullDebug -x :term:elfcleaner
```

### Known elf-cleaner workaround

Every debug symbol-strip task is finalized by `:term:elfcleaner`, which shells
out to the host `elf-cleaner` executable (`term/build.gradle`). That tool is not
installed on this host, so all builds pass `-x :term:elfcleaner`. This is a
packaging/symbol-cleanup convenience only; it does not affect compiled native
code. Installing `elf-cleaner` should allow the command without `-x`. Do not
silently delete the task without a release decision.

### Known lint issue

`:term:lintFullDebug` reports **1 error, 67 warnings**. The single error is the
pre-existing `GestureBackNavigation` finding at `Term.java:644` for legacy
`KEYCODE_BACK` handling under target SDK 36 (predictive back not yet migrated).
This matches the Phase 1.7 baseline exactly; this release introduced no new lint
findings. The remaining warnings (`UnusedResources`, `RtlHardcoded`,
`TypographyEllipsis`, etc.) are pre-existing.

Predictive-back migration is intentionally **not** scoped into this release: it
is not required for build correctness, and back can close sessions or send
terminal characters, so it needs explicit behavior tests. Track it as a focused
maintenance task.

## Build artifacts

All artifacts below were produced by one `:term:clean` invoke (`-x
:term:elfcleaner`) on 2026-09-16. Debug APKs are signed with the standard Android
debug key.

### Debug (regression) artifacts

| Variant | Path | Size (bytes) | SHA-256 |
| --- | --- | --- | --- |
| Full debug | `term/build/outputs/apk/full/debug/term-full-debug.apk` | 6,730,700 | `e1cf1074a888a9aaac740fc7db012d404ee4dd48ba4ed661f70c7d02f1871e53` |
| Play debug | `term/build/outputs/apk/play/debug/term-play-debug.apk` | 6,730,038 | `ccef6d61a496b54a58fe9a537c0d30cf8c77088516143d05d02d33a9cd135cf4` |

- Application ID: `com.thothterm.devel`; versionName `1.0.0`; versionCode `10000`.
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.

### Release artifacts (unsigned, store signing pending)

These use the production application ID `com.thothterm`. They are **unsigned**
because no release signing credentials are configured; see
"Signed-release status" below.

| Variant | Path | Size (bytes) | SHA-256 |
| --- | --- | --- | --- |
| Full release (unsigned APK) | `term/build/outputs/apk/full/release/term-full-release-unsigned.apk` | 5,548,608 | `df5a23780731c70e592125b6916cf2fb6121acd5c9fe9f5cc63c02a40a2d5156` |
| Play release (unsigned APK) | `term/build/outputs/apk/play/release/term-play-release-unsigned.apk` | 5,547,588 | `de3724466e8f7f7350ca0f287e5d1ec022749f4abea814ca56d670b13841798f` |
| Full release (unsigned AAB) | `term/build/outputs/bundle/fullRelease/term-full-release.aab` | 4,838,346 | `2dbbbbfc94c6276f6c03df1b6d14cbfe7eb9847232f9988fb2295ed9d617edad` |
| Play release (unsigned AAB) | `term/build/outputs/bundle/playRelease/term-play-release.aab` | 4,837,316 | `bec19bfae70b30ef5d5c877f0f74c05c1bf1cdf0602b825fe927c6e689b93cc6` |

- Application ID: `com.thothterm`; versionName `1.0.0`; versionCode `10000`.
- ABIs (APKs): `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- `apksigner verify` reports `DOES NOT VERIFY: Missing META-INF/MANIFEST.MF`
  (expected for an unsigned package); the AABs contain no signing block either.

## Signed-release status

No ThothTerm release signing credentials exist in this repository
(`signing.properties` is absent; the only keystore on the host is an unrelated
`pocketclaw-release.p12`). No keys were invented and no production signing was
faked. The signed production APK/AAB is **pending** until real credentials are
supplied via `-Psigning.properties=<path>`.

## Manual device validation already completed

The application was physically tested and **accepted by the user on a real
device** through Phase 1.9, including the terminal rendering/cursor fixes, the
right-edge clipping fix, and the friendly `app_HOME` prompt. Phase 1.8 and 1.9
were driven directly by real-device observations, and the accepted state is now
frozen as the golden baseline.

No emulator/device is attached to this build host, so the release-freeze pass
performs no new on-device run; it re-verifies the build, packaging, and lint
baseline only.

## Remaining known limitations

- Predictive-back is not migrated; the legacy `KEYCODE_BACK` path is the sole
  lint error and needs dedicated session-close/send-character tests.
- Automated tests are effectively absent from the active Gradle build (all
  unit-test tasks are `NO-SOURCE`).
- The Phase 1.8 (forced-LTR base, Arabic natural advance) and Phase 1.9
  (right-edge clipping, friendly `app_HOME` prompt) fixes are accepted on the
  user's device.
- A broader on-device matrix (cursor alignment with both fonts and several
  sizes; Arabic/mixed BiDi cursor/selection; API 16-22 shaping fallback and OEM
  fonts; both Extra Keys pages on the narrowest supported phone; all four theme
  modes; adaptive/monochrome icon under OEM masks) is still recommended on
  API 23/29/31/36.
- `TermService` calls `System.exit(0)` on API 35+ destruction after timeout;
  lifecycle behavior deserves isolated review.
- Release artifacts remain unsigned until signing credentials are provided.
- No durable session restoration after service/process death.
- Legacy compatibility surfaces remain (`sharedUserId`, exported legacy
  service/interface behavior, minSdk 16, broad storage code).

## Scope statement

This edition does **NOT** contain Ubuntu, PRoot, a Linux rootfs, apt, a
distro manager, Node.js, npm, Python provisioning, a Web Terminal, a LAN
server, or a WebSocket server. It is a standalone Android terminal emulator.

## Verification summary (this pass)

- `git diff --check`: clean.
- Full debug build: success.
- Play debug build: success.
- Release compile check (Full + Play, unsigned): success.
- `:emulatorview:testDebugUnitTest`, `:libtermexec:testDebugUnitTest`,
  `:term:testFullDebugUnitTest`, `:term:testPlayDebugUnitTest`: `NO-SOURCE`.
- `:term:lintFullDebug`: 1 error, 67 warnings (unchanged Phase 1.7 baseline).
