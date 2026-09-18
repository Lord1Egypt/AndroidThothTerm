# ThothTerm Terminal Emulator 1.2.0 — Selection & Typography Release

## Identity

| Item | Value |
| --- | --- |
| Product | ThothTerm Terminal Emulator |
| Launcher label | ThothTerm |
| Positioning | Terminal emulator for Android |
| Production applicationId | `com.thothterm` |
| Debug applicationId | `com.thothterm.devel` |
| versionName | `1.2.0` |
| versionCode | `10200` |
| Tag | `terminal-v1.2.0` (annotated) |
| Release title | ThothTerm Terminal Emulator 1.2.0 — Selection & Typography Release |
| Repository | https://github.com/Lord1Egypt/AndroidThothTerm |

Terminal 1.2.0 = **ThothTerm Terminal Emulator 1.1.0 (Diagnostics) + modern
selection, DejaVu Sans Mono typography, native HOME startup, and the friendly
dynamic prompt**. It contains no Ubuntu, PRoot, rootfs, or Linux-edition code.

## Relationship to prior releases

`terminal-v1.0.0` (`7a3c88e52075a0a7abaa9dd61150c122c119718f`) remains the
immutable Golden Baseline, and `terminal-v1.1.0`
(`7ef55ec7f896a99492f7fd59dfa29b6ac9371d73`) remains the Diagnostics release.
Neither has been modified, moved, deleted, or retagged. Terminal 1.2.0 builds
on top of them.

Base chain (terminal release branch):

```
terminal-v1.0.0   7a3c88e  release: ThothTerm Terminal Emulator 1.0 golden baseline
55b4ba9 / 37a37ad          docs
5308000                    feat: add ThothTerm diagnostics and structured logging
acef247                    release: prepare ThothTerm Terminal Emulator 1.1.0
7ef55ec  terminal-v1.1.0   release: ThothTerm Terminal Emulator 1.1.0
ea63364                    feat: modernize terminal text selection
2ff9b10                    fix: start regular terminal sessions in HOME
4d64c4f                    style: improve default terminal typography
<ebfec92/c7bf039 terminal portions>  prompt export + dynamic app_HOME prompt
<release commit>           release: prepare ThothTerm Terminal Emulator 1.2.0
```

The terminal changes were authored on `feature/thotterm-linux` and applied to
the release branch as **terminal/shared-file overlays only**
(`term/`, `emulatorview/`, `libtermexec/`). No `term-ubuntu/` file, Ubuntu
document, rootfs, PRoot binary, or Ubuntu DNS/runtime code was taken.
`settings.gradle` on the release branch does not include `:term-ubuntu`.

## What's new in 1.2.0

- Modern text selection in `emulatorview`: `EmulatorView`, `PaintRenderer`,
  `TranscriptScreen`, new `strings.xml`, and `TranscriptSelectionTest`.
- `term/TermActivity.java`: the terminal long-press no longer opens the legacy
  full-screen "Edit text" context menu; selection is handled in the view with
  the Android floating toolbar. `menu_session.xml` keeps Copy all / Paste script.
- Selection highlight and floating-toolbar content-rect boundaries use the same
  inclusive terminal-cell range as the copied text.
- Typography: default font source is the embedded DejaVu Sans Mono ("Built-in").
- `libtermexec`: the native child process now performs `chdir(cwd)` before
  `execve`, so the session starts in HOME.
- `term`: the `cd ~` default startup command is removed and `prepareInitialCommand`
  no longer prepends an empty command.
- Friendly prompt derived dynamically from `$HOME` (no hard-coded package path).

## Verification

Built from a clean tree (`:term:clean`) to avoid the known warm-tree APK padding.
Validated on the release branch:

- `git diff --check`: clean.
- Lint `:term:lintFullDebug`: **1 error / 67 warnings**, identical to the 1.1.0
  baseline. The sole error is the pre-existing `GestureBackNavigation` finding.
- `:emulatorview:testDebugUnitTest` (selection): pass.
- APK badging: applicationId `com.thothterm` (debug `com.thothterm.devel`),
  versionCode `10200`, versionName `1.2.0`, ABIs `arm64-v8a`, `armeabi-v7a`,
  `x86`, `x86_64`.
- APK content scan: no `assets/ubuntu`, no `libproot*`, no `thothfetch`, no
  `com/thothterm/linux/` classes.
- Debug prompt behavior was validated with shell tests (bash and dash) against
  the generated helper; on-device mksh confirmation is recommended.

## Artifacts

Produced by one clean build on the release branch. Build outputs are not
committed.

| Artifact | Path | Size (bytes) | SHA-256 |
| --- | --- | --- | --- |
| Full debug APK | `term/build/outputs/apk/full/debug/term-full-debug.apk` | 6,768,836 | `547846ef9e623279af55a28daec1393cbdd9ab8e7b20c22af57182c202e77f5c` |
| Play debug APK | `term/build/outputs/apk/play/debug/term-play-debug.apk` | 6,767,734 | `5dcd3ee6d767a3cfc24b06d53442cccaf851495f808339f37aed12cfbd356cb8` |
| Full release APK (unsigned) | `term/build/outputs/apk/full/release/term-full-release-unsigned.apk` | 5,581,816 | `8609420060ee59c86f75666710811cfb1913f8b2e84163c39328b4294899fc11` |
| Play release APK (unsigned) | `term/build/outputs/apk/play/release/term-play-release-unsigned.apk` | 5,581,076 | `2b06a65226f3baaedd92d8d5b08865f85c51661f23eb02e51f864a58e9fd6bb9` |
| Full release AAB (unsigned) | `term/build/outputs/bundle/fullRelease/term-full-release.aab` | 4,863,577 | `7c09b57dacf7484cf8a905ce4aeaa88738778c036b458409d597c3503957cd2c` |
| Play release AAB (unsigned) | `term/build/outputs/bundle/playRelease/term-play-release.aab` | 4,862,831 | `a53e3219f8bc24d93cab9bb52790f0f3c88ab2f3720a1620086449f2b1700e5a` |

## Signing status

Production signing is **pending**: no ThothTerm signing credentials are
configured in this repository. Debug APKs are signed with the Android debug key
(device testing only). Release APKs and AABs are **unsigned**.
