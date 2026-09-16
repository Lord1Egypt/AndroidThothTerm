# ThothTerm Terminal Emulator 1.0.0 Golden Baseline

Permanent, reproducible recovery point for the accepted terminal-only product.

## Identity

| Item | Value |
| --- | --- |
| Product name | ThothTerm Terminal Emulator |
| Launcher label | ThothTerm |
| Positioning | Terminal emulator for Android |
| Repository | https://github.com/Lord1Egypt/AndroidThothTerm |
| Release date | 2026-09-16 |
| Annotated tag | `terminal-v1.0.0` |
| Baseline commit SHA (tag target) | `7a3c88e52075a0a7abaa9dd61150c122c119718f` |
| Release (feature-branch) commit | `108d3e79bf68a6c51044e48519ec628da64cd2b4` |
| GitHub Release | https://github.com/Lord1Egypt/AndroidThothTerm/releases/tag/terminal-v1.0.0 |
| Production application ID | `com.thothterm` |
| Debug application ID | `com.thothterm.devel` |
| versionName | `1.0.0` |
| versionCode | `10000` |
| Supported ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| compileSdk / targetSdk / minSdk | 36 / 36 / 16 |

This is the **terminal-emulator edition only**. It does not contain Ubuntu, a
Linux rootfs, PRoot, apt, or any embedded Linux runtime. A future, separate
product is reserved as `com.thothterm.linux`; see `docs/EDITION_PLAN.md`.

## Build toolchain

| Item | Value |
| --- | --- |
| Gradle wrapper | 9.7.1 |
| Android Gradle Plugin | 9.4.0 |
| JDK | Temurin 17.0.20.1 |
| Build tools | 37.0.0 |
| NDK | 23.2.8568313 |
| CMake (AGP) | Android SDK CMake 3.22.1 |
| Kotlin | disabled (`enableKotlin = false`) |
| Java bytecode target | 1.8 |
| Flavors | `play`, `full` (`catalog` dimension) |
| Release shrinking | disabled |

## Exact build commands

```bash
export JAVA_HOME=/home/lordegypt/PocketCLaw/.tooling/jdk-17
export ANDROID_HOME=/home/lordegypt/Android/Sdk
export ANDROID_SDK_ROOT=/home/lordegypt/Android/Sdk
export GRADLE_USER_HOME=/tmp/thothterm-gradle-user
export PATH="$JAVA_HOME/bin:$PATH"

# Clean debug (regression) + unsigned release APK/AAB
./gradlew :term:clean \
  :term:assembleFullDebug :term:assemblePlayDebug \
  :term:assembleFullRelease :term:assemblePlayRelease \
  :term:bundleFullRelease :term:bundlePlayRelease \
  -x :term:elfcleaner

# Unit tests and lint
./gradlew :emulatorview:testDebugUnitTest :libtermexec:testDebugUnitTest \
  :term:testFullDebugUnitTest :term:testPlayDebugUnitTest -x :term:elfcleaner
./gradlew :term:lintFullDebug -x :term:elfcleaner
```

### elf-cleaner workaround

`term/build.gradle` finalizes debug symbol-strip tasks with `:term:elfcleaner`,
which requires the host `elf-cleaner` executable. It is not installed here, so
all builds pass `-x :term:elfcleaner`. This only skips an optional symbol-cleanup
pass and does not affect compiled native code.

## Artifacts and SHA-256

Produced by one `:term:clean` build (`-x :term:elfcleaner`) on 2026-09-16.
Artifacts are build outputs and are not committed to the repository.

| Artifact | Path | Size (bytes) | SHA-256 | Package ID | Signing |
| --- | --- | --- | --- | --- | --- |
| Full debug APK | `term/build/outputs/apk/full/debug/term-full-debug.apk` | 6,730,700 | `e1cf1074a888a9aaac740fc7db012d404ee4dd48ba4ed661f70c7d02f1871e53` | `com.thothterm.devel` | Android debug key |
| Play debug APK | `term/build/outputs/apk/play/debug/term-play-debug.apk` | 6,730,038 | `ccef6d61a496b54a58fe9a537c0d30cf8c77088516143d05d02d33a9cd135cf4` | `com.thothterm.devel` | Android debug key |
| Full release APK (unsigned) | `term/build/outputs/apk/full/release/term-full-release-unsigned.apk` | 5,548,608 | `df5a23780731c70e592125b6916cf2fb6121acd5c9fe9f5cc63c02a40a2d5156` | `com.thothterm` | unsigned |
| Play release APK (unsigned) | `term/build/outputs/apk/play/release/term-play-release-unsigned.apk` | 5,547,588 | `de3724466e8f7f7350ca0f287e5d1ec022749f4abea814ca56d670b13841798f` | `com.thothterm` | unsigned |
| Full release AAB (unsigned) | `term/build/outputs/bundle/fullRelease/term-full-release.aab` | 4,838,346 | `2dbbbbfc94c6276f6c03df1b6d14cbfe7eb9847232f9988fb2295ed9d617edad` | `com.thothterm` | unsigned |
| Play release AAB (unsigned) | `term/build/outputs/bundle/playRelease/term-play-release.aab` | 4,837,316 | `bec19bfae70b30ef5d5c877f0f74c05c1bf1cdf0602b825fe927c6e689b93cc6` | `com.thothterm` | unsigned |

All APKs contain `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` native code
(`libappwrap.so`, `libcmd-t1plus.so`, `libexec-t1plus.so`, `libterm-system.so`).

Production store signing is **pending**: no ThothTerm signing credentials are
configured (`signing.properties` absent), so no signed production artifact was
created and no signing identity was invented.

## Accepted features

- Modern ThothTerm visual identity and terminal-emulator product identity.
- Android terminal emulator with a PTY/process engine and
  foreground-service-owned sessions.
- Multiple terminal windows (New Window, Close Window, Windows list).
- Cleaned navigation drawer and polished settings organization.
- Dark / Light / System / AMOLED themes.
- Extra Keys toolbar with NAV and SYM pages, fit-to-screen.
- CTRL/ALT one-shot and locked modifiers.
- Terminal cursor/cell alignment fixes.
- Right-edge cell-clipping fix.
- Friendly `app_HOME` prompt (presentation only; `pwd` unchanged).
- UTF-8 terminal support.
- Arabic contextual shaping and Unicode BiDi visual handling.
- Mixed Arabic/English/numeric display.
- Arabic natural glyph advance (no artificial tracking).
- Logical/visual cursor and selection mapping.

## Known limitations

- Predictive back is not migrated; the legacy `KEYCODE_BACK` path is the sole
  lint error.
- Automated tests are effectively absent (all Gradle unit-test tasks are
  `NO-SOURCE`).
- Production artifacts remain unsigned until signing credentials are supplied.
- `TermService` calls `System.exit(0)` on API 35+ destruction after timeout;
  lifecycle behavior deserves isolated review.
- No durable session restoration after service/process death.
- Legacy compatibility surfaces remain (`sharedUserId`, exported legacy
  service/interface behavior, minSdk 16, broad storage code).

## Lint baseline

`:term:lintFullDebug`: **1 error, 67 warnings**.
The single error is the pre-existing `GestureBackNavigation` finding at
`Term.java:644`. No new findings were introduced by this release.

## Restoration instructions

```bash
# Fetch the golden tag and inspect it
git fetch --tags
git tag --list terminal-v1.0.0
git show terminal-v1.0.0

# Check out the exact golden baseline (detached HEAD)
git checkout terminal-v1.0.0

# Preferred: create a recovery branch from the immutable tag
git switch -c recovery/terminal-v1.0.0 terminal-v1.0.0
```

Rebuild the baseline exactly:

```bash
./gradlew :term:clean :term:assembleFullDebug :term:assemblePlayDebug -x :term:elfcleaner
```

Compare a rebuilt artifact against the recorded SHA-256 above before trusting
it; release builds are not byte-for-byte reproducible across hosts, so treat the
recorded hashes as coming from the build documented here.

Do not commit build outputs, signing keys, or secrets into the repository.
