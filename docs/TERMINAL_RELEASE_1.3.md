# ThothTerm Terminal Emulator 1.3.0 — Golden Baseline

## Identity

| Item | Value |
| --- | --- |
| Product | ThothTerm Terminal Emulator |
| Launcher label | ThothTerm |
| Positioning | Local terminal emulator for Android |
| Production applicationId | `com.thothterm` |
| Debug applicationId | `com.thothterm.devel` |
| versionName | `1.3.0` |
| versionCode | `10300` |
| Tag | `terminal-v1.3.0` (annotated) |
| Release title | ThothTerm Terminal Emulator 1.3.0 — Golden Baseline |
| Repository | https://github.com/Lord1Egypt/AndroidThothTerm |

Terminal 1.3.0 = **ThothTerm Terminal Emulator 1.2.1 + the generic terminal
fixes proven in ThothTerm Ubuntu 0.1.3–0.1.7, the notification and
window-close lifecycle fixes found during this release's device acceptance, a
portable build, and R8**. It is the new Regular ThothTerm Terminal Golden
Baseline. The launcher icon, branding and package are unchanged; it contains no
Ubuntu, PRoot, rootfs, or network/web-terminal code.

## Relationship to prior releases

`terminal-v1.0.0`, `terminal-v1.1.0`, `terminal-v1.2.0` and `terminal-v1.2.1`
(`2eb28ae853c2c72a12676c5dca8c87beb06d080a`) are unmodified. 1.3.0 was built
on `release/terminal-v1.3.0`, branched from `2eb28ae`, and merged into `master`
with `--no-ff` as earlier releases were.

Shared modules were ported by hunk, not by copying files: `emulatorview`
(`EmulatorView`, `SimpleClipboardManager`, `TermKeyListener`, `TermSession`,
`TerminalEmulator`, `TranscriptScreen`, `UnicodeTranscript`) and `libtermexec`
(`Process`, `process.c`) now match the Ubuntu line, plus this release's
SIGHUP fix in `process.c`. `term-ubuntu/` is not part of this branch.

## What's new in 1.3.0

| Commit | Change |
| --- | --- |
| `3c099cd` | Build: the JetBrains Java 17 toolchain, Foojay resolver and daemon-JVM pin are removed. Measured: the old build could not even run `gradlew help` in the JDK 21-only `fdroidserver:buildserver-trixie` image; the new one builds and tests there, and bytecode stays at class version 52. |
| `9e944c4` | Clipboard gates on `text/*` and reads the item's text; URI/Intent-only clips are refused; failed reads log the exception class only. |
| `b978ac5` | Drawer locked closed while shut (left-edge selection); `TermKeyListener.resetTransientState()` on pause/resume/reset; `CSI 3 J` + *Clear scrollback*. |
| `0e4435e` | Pinch zoom (`ScaleGestureDetector`) and menu zoom on the global font-size preference (6–32 pt). |
| `564b3df` | Extra Keys metrics from resources: compact default, `values-h500dp` keeps portrait sizing. |
| `6ac6479` | `POST_NOTIFICATIONS` requested once; `STOP_FOREGROUND_REMOVE` on service destroy. |
| `defa54f` | *Exit*; `Process.killChilds()` (SIGKILL of a process group) in libtermexec. API 21/23 calls guarded for minSdk 16. |
| `febfaa7` | R8 enabled; `proguard-rules.pro` with two JNI keeps (replaces the reference to a `proguard-rules.txt` that never existed). |
| `f5b04c7` | Notification text "ThothTerm is running"; re-posted when the terminal resumes if it was blocked before consent. |
| `9df405d` | `FOREGROUND_SERVICE_IMMEDIATE`, so Android 12+ does not defer it. |
| `274f101` | Window close hangs up the window's whole session; SIGHUP reset to default in the forked child; guarded SIGKILL backstop. |

### Window-close root cause (`274f101`)

Android app processes ignore SIGHUP and an ignored disposition survives
`execve()`: `/proc/<pid>/status` showed `SigIgn` bit 0 set in the app, the
shell and every job, so the SIGHUP sent on window close and on Exit had never
had any effect. Job control also gives the foreground job and each background
job process groups of their own, and closing the PTY master does not hang the
terminal up while the reader thread is still blocked in `read()`. The child now
restores `SIGHUP` to `SIG_DFL` before `execve()`, close sends SIGHUP to every
process of the window's session (found through `/proc`, which only shows the
app's own processes), and after 400 ms SIGKILLs the shell's group if the shell
is unreaped and the recorded foreground group if it is still in that session.

## R8

- Configuration: `proguard-android-optimize.txt` + `term/proguard-rules.pro`:

  ```
  -keep class com.thothterm.TermIO$Native  { native <methods>; }
  -keep class com.thothterm.Process$Native { native <methods>; }
  ```

  No `-dontwarn`, `-dontshrink`, `-dontobfuscate` or wildcard keep in project
  rules. The merged configuration's `-dontwarn` lines come only from the
  platform file and the kotlinx-coroutines consumer rules. R8 reports no
  warnings and writes no `missing_rules.txt`. Resource shrinking is off.
- JNI contract: `libterm-system.so` registers its natives by literal class and
  method names. The six `JNINativeMethod` entries (`createSubprocess`,
  `waitExit`, `finishChilds`, `killChilds`, `setUTF8Input`, `setWindowSize`)
  match the minified DEX of both flavours exactly by class, name and
  signature; `TermIO` and `Process` are renamed, their `$Native` classes keep
  their binary names.
- Why the rules exist: measured without them, the platform file's
  `-keepclasseswithmembernames` already keeps the names, but lets R8 delete a
  native method Java stops calling, and `RegisterNatives` fails the whole
  table if one entry is missing.
- Other findings: every manifest and layout class is a seed with its name
  kept; R8 merges `jackpal.androidterm.Term` into `TermActivity` and
  `ITerminal.Stub` into the service binder (neither is named anywhere by
  string; both AIDL `DESCRIPTOR`s are present); `LogLevel` is unboxed, which is
  safe because it is parsed by its own labels.

## Native / 16 KB

16 ELFs: `libappwrap`, `libcmd-t1plus`, `libexec-t1plus`, `libterm-system` for
`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`. 64-bit LOAD segments are aligned
to `0x4000`, 32-bit to `0x1000`. `zipalign -c -P 16 -v 4` passes on both
release APKs. The test device uses 4 KB pages, so 16 KB is verified statically.

## Verification

- Unit tests: 68 (both flavours) — `ScrollbackAndModifierTest` (8, new),
  `TranscriptSelectionTest` (8), `TerminalZoomTest` (12), `NotificationPermissionTest` (5),
  `SessionProcessesTest` (6, new), `TermServiceTest` (3); counted per flavour
  for `term`. The new engine tests were mutation-checked.
- Lint: the pre-existing `GestureBackNavigation` error only; new warnings are
  two `InlinedApi` notes for `POST_NOTIFICATIONS` (guarded) and
  `NotShrinkingResources` (deliberate).
- Builds: JetBrains 17 locally with toolchain auto-download and auto-detection
  disabled; JDK 21 in `fdroidserver:buildserver-trixie`.

### Physical acceptance — Samsung SM-A165F, Android 16 (API 36), arm64

Minified release code, debug-signed with the existing debug key, installed as a
fresh `com.thothterm` (only `com.thothterm.devel` was present; it was not
touched). `dumpsys` showed no `DEBUGGABLE` flag. Every later build was an
in-place `install -r`, keeping `firstInstallTime`.

- First launch: the notification prompt and the full flavour's "All files
  access" dialog stack but both answer cleanly; the Settings round trip
  returns to a live terminal.
- PTY `/dev/pts/N`, `stty size`, shell in its own process group, `TERM=screen`,
  HOME start; Extra Keys CTRL+C → `rc=130`; Ctrl-D ends `cat`.
- Arabic, mixed Arabic/English, numbers, emoji and a mixed URL render shaped
  and reordered; file SHA-256 identical to the host; an Arabic word copied from
  the screen pastes back byte-identical.
- Telegram (Arabic + emoji + blank lines) and Chrome text paste correctly.
- Left-edge long-press selection at x = 5, 15, 30, 45, 55 px.
- Fn latch (`ul` → `_|`) cleared by background/resume; Ctrl latch cleared by
  *Restart terminal*.
- `CSI 3 J` and *Clear scrollback*: scrollback gone, screen and shell kept.
- Menu zoom 36×63 → 30×54 → 25×46 → 30×54 → 36×63; real two-finger pinch in
  both directions.
- Real rotation portrait → landscape → portrait (PTY 7×123 in landscape with
  the keyboard up, compact Extra Keys).
- Multiple windows, switching through the drawer and Windows list, closing a
  window; the drawer opens from the toolbar and swipes shut.
- Window close with a foreground job, background + foreground jobs, `top`, an
  idle shell: gone within 250 ms; a SIGHUP-trapping foreground job gone within
  1.75 s; a `nohup` background job survives; other windows unchanged.
- Notification "ThothTerm / ThothTerm is running" visible 0.7 s after a cold
  start, one instance across background/resume, opens the terminal when tapped.
- Settings, Diagnostics log viewer and log-level picker under R8.
- `OPEN_NEW_WINDOW` and share-to-terminal (`cd "<dir>"`) work; `RUN_SCRIPT`
  without the permission is denied by the system; a forged `RUN_SHORTCUT` is
  rejected.
- Exit with three windows, running foreground/background/`nohup` jobs and
  `top`: no process of the app's uid left, no service, no notification, no
  Recents task; clean cold relaunch.
- Logcat over every app process: zero `UnsatisfiedLinkError`,
  `ClassNotFoundException`, `NoSuchMethodError`, `NoSuchFieldError`,
  `VerifyError`, JNI errors, `ForegroundServiceStartNotAllowedException`,
  `FATAL EXCEPTION`, ANR or tombstone.

## Known limitations

- The reverse-landscape rotation was exercised on the device only while another
  app was in front; ThothTerm was verified in portrait and landscape.
- mksh does not hang up running background jobs on its own; window close now
  does it for the whole session, but a job that ignores SIGHUP (`nohup`) or
  leaves the session (`setsid`) keeps running until Exit.
- Translated locales keep their previous wording for the notification text.
- The legacy `sharedUserId`, `RUN_SCRIPT` permission and exported integration
  entry points are unchanged for compatibility.

## Artifacts

Produced by a clean build of a fresh clone at the release commit (JetBrains 17,
toolchain auto-download and auto-detection disabled). Build outputs are not
committed.

| Artifact | Path | Size (bytes) | SHA-256 |
| --- | --- | --- | --- |
| Full debug APK | `term/build/outputs/apk/full/debug/term-full-debug.apk` | 6,785,091 | `df272502f7419bc716df0c760fa41a8d99a38564b3f52a740eaba9163745cfe1` |
| Play debug APK | `term/build/outputs/apk/play/debug/term-play-debug.apk` | 6,784,208 | `6bc1ef00175701dc58ad4bf4cf61b01e9323ba8959cdb048b456dc31ecb2e8f8` |
| Full release APK (unsigned) | `term/build/outputs/apk/full/release/term-full-release-unsigned.apk` | 2,987,775 | `b164edeee86ee8941c542e962f7224d2bde09a9c131404e21b097a73d0d4ad88` |
| Play release APK (unsigned) | `term/build/outputs/apk/play/release/term-play-release-unsigned.apk` | 2,987,399 | `729bcc521bfa90ad2ca0256aa97eb06627aa523fc53b36767a911c28cab09ea4` |
| Full release AAB (unsigned) | `term/build/outputs/bundle/fullRelease/term-full-release.aab` | 3,484,878 | `13df02775fe59ce6a3423fc78d861a9bdd6600db022190d1baad2be4d82ba64a` |
| Play release AAB (unsigned) | `term/build/outputs/bundle/playRelease/term-play-release.aab` | 3,484,260 | `4a85bc2172419c76e4c751aa2ecf12acc5d61f098a48a1a626f40e25ecab624a` |

## Signing status

Production signing is **pending**: no ThothTerm signing credentials are
configured in this repository. Debug APKs are signed with the Android debug key
(device testing only). Release APKs and AABs are **unsigned**.
