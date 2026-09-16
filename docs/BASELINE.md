# ThothTerm reproducible baseline

Baseline captured on 2026-09-16 before Phase 1 source changes.

## Source state

- Repository: `https://github.com/Lord1Egypt/AndroidThothTerm`
- Starting branch: `master`
- Starting commit: `b48c119ec6ce6928c9c3f83750492715b2b7e392`
- Starting status: clean (`master...origin/master`)
- Work branch created after the baseline: `feature/thotterm-visual-foundation`

## Toolchain and application metadata

| Item | Baseline value |
| --- | --- |
| Gradle wrapper | 9.7.1 |
| Android Gradle Plugin | 9.4.0 |
| Project Kotlin | None; Kotlin is disabled in all modules |
| Gradle embedded Kotlin | 2.4.0 |
| Java | Temurin 17.0.20.1 |
| Java toolchain requested | Java 17, JetBrains vendor; Java sources target 1.8 bytecode |
| compileSdk / targetSdk / minSdk | 36 / 36 / 16 |
| Build tools | 37.0.0 |
| NDK | 23.2.8568313 |
| CMake used by AGP | Android SDK CMake 3.22.1 |
| System CMake (not used by AGP) | 3.28.3 |
| Namespace / release application ID | `com.thothterm` / `com.thothterm` |
| Debug application ID | `com.thothterm.devel` |
| Version | 5.7.0, code 570; Full version name is `5.7.0/X` |
| Flavors | `play`, `full` on the `catalog` dimension |
| Native ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Release shrinking | Disabled |
| Release signing | Optional `signing.properties`; not present or fabricated |

The SDK initially lacked build-tools 37.0.0 and NDK 23.2.8568313. AGP installed both after accepting the already-present SDK licenses. The host had no `java` on `PATH`; the build used `/home/lordegypt/PocketCLaw/.tooling/jdk-17` explicitly.

## Baseline build

The unmodified build first ran:

```bash
JAVA_HOME=/home/lordegypt/PocketCLaw/.tooling/jdk-17 \
ANDROID_HOME=/home/lordegypt/Android/Sdk \
ANDROID_SDK_ROOT=/home/lordegypt/Android/Sdk \
GRADLE_USER_HOME="$PWD/.gradle-user" \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew :term:assembleFullDebug
```

All Java, AIDL, resources, manifests, dex work, and native targets for all four ABIs compiled. The build then failed in the custom `:term:elfcleaner` finalizer because the external `elf-cleaner` executable is not installed. This is a pre-existing reproducibility dependency in `term/build.gradle`, not a native compilation failure.

The reproducible fallback is:

```bash
./gradlew :term:assembleFullDebug -x :term:elfcleaner
```

That command succeeded on the untouched source.

- Baseline APK: `term/build/outputs/apk/full/debug/term-full-debug.apk`
- Baseline size: 6,828,075 bytes
- Baseline SHA-256: `4aa3db341a1033771f71be672fc1978719347e24e348ad67493478136655e2c1`
- Native outputs found for every ABI: `libappwrap.so`, `libcmd-t1plus.so`, `libexec-t1plus.so`, `libterm-system.so`

The baseline APK was subsequently replaced by the Phase 1 build at the same ignored output path. The size/hash above were recorded immediately after the untouched build.

## Baseline checks

- `:emulatorview:testDebugUnitTest`: `NO-SOURCE`
- `:libtermexec:testDebugUnitTest`: `NO-SOURCE`
- `:term:testFullDebugUnitTest`: `NO-SOURCE`
- `:term:lintFullDebug`: failed with 1 error and 72 warnings
- Existing lint error: `GestureBackNavigation` at `jackpal/androidterm/Term.java:670` for legacy `KEYCODE_BACK` handling under target SDK 36
- Files under `tests/` are legacy/manual fixtures and an old standalone Android test project; they are not wired into the current Gradle modules

No emulator/device was available. `adb devices -l` outside the restricted sandbox returned an empty device list (the restricted sandbox itself cannot bind ADB's smart socket), so launch, PTY interaction, keyboard, ANSI, scrolling, orientation, and session UI behavior require device validation.
