# ThothTerm Phase 0/1 handoff

## Current state

- Audit starting commit: `b48c119ec6ce6928c9c3f83750492715b2b7e392`
- Branch: `feature/thotterm-visual-foundation`
- Baseline branch: `master`
- Phase 1 is intentionally limited to visual identity, theme selection, and UI resource cleanup.
- The implementation is kept as a reviewable worktree on the feature branch; run `git status --short` for the exact file list.
- No terminal engine, PTY, JNI, process, session lifecycle, runtime, Ubuntu/PRoot, extra-key toolbar, or web-terminal behavior was changed.

See `docs/BASELINE.md` for the untouched build record, exact baseline APK hash, and toolchain setup.

## Architecture map

```text
Android launcher
  -> com.thothterm.Application
       -> preference defaults/migration
       -> TypefaceSetting
       -> Installer (private dirs, skeleton ~/.shrc, generated mkshrc)
  -> com.thothterm.TermActivity
       -> legacy jackpal.androidterm.Term controller
          -> ServiceManager starts + binds foreground TermService
             -> SessionsService owns SessionList<TermSession>
             -> CommandService owns authenticated-by-UID local Unix socket
          -> TermActionBar + DrawerLayout + live session Spinner
          -> TermViewFlipper
             -> TermView
                -> emulatorview.EmulatorView
                   -> emulatorview.TermSession
                      -> TerminalEmulator + TranscriptScreen + I/O threads

New window
  -> Term.doCreateNewWindow()
  -> ShellTermSession opens /dev/ptmx
  -> com.thothterm.Process.createSubprocess()
  -> libtermexec JNI libterm-system.so
  -> fork/session/controlling-PTY/exec native path
  -> session added to service-owned SessionList
  -> TermView added to TermViewFlipper

Resize / encoding
  -> EmulatorView or configuration size change
  -> GenericTermSession.updateSize()/initializeEmulator()
  -> TermIO JNI
  -> termio.c ioctl/PTY configuration
```

The `TermService` is already a `START_STICKY` foreground service and owns sessions independently of activity views. `Term.onStop()` removes views and unbinds; reconnect repopulates views from the service list. This is a useful starting point for future persistent sessions, but it is not yet the future Linux-runtime/session-engine design.

## Modules and responsibilities

- `term/`: Android application, activities, foreground service, shell sessions, settings, layouts/resources, app-local command bridge, and three native helper binaries/library.
- `emulatorview/`: legacy Java terminal emulator/view library. It owns parsing, screen/transcript, rendering, key translation, mouse tracking, terminal I/O queues, and resize callbacks.
- `libtermexec/`: Java/JNI PTY process and terminal-control library. It builds `libterm-system.so` and also exposes legacy AIDL `ITerminal` integration.
- `samples/`: excluded from `settings.gradle`; historical integrations only.
- `tests/`: manual terminal/control-sequence fixtures and an old standalone test project; not current Gradle tests.

There is no Kotlin application code. `enableKotlin = false` is set in every active module.

## Entry points and startup

- `com.thothterm.Application`: initializes paths, preferences, typeface, private directories, skeleton startup files, and executable fallback copies.
- `com.thothterm.TermActivity`: launcher activity and thin project-specific subclass of `jackpal.androidterm.Term`.
- `jackpal.androidterm.Term`: main UI/session controller. It creates/binds the service, creates terminal views, routes menus and keyboard actions, and implements New/Close Window.
- `jackpal.androidterm.TermService`: foreground `SessionsService`, sticky lifecycle, ongoing notification, exported legacy terminal binder, and local command service.
- Other manifest activities: `RemoteInterface`, `RunScript`, `RunShortcut`, `RemoteSession`, `WindowListActivity`, `TermPreferencesActivity`, shortcut/file/color configuration activities.

Startup ordering is Application initialization -> activity theme/content -> foreground-service start -> service bind -> information collection -> reuse existing sessions or create one -> create emulator views -> initialize emulator when sized -> start shell watcher and initial command.

## PTY/session/native boundary

- `ShellTermSession` opens `/dev/ptmx`, validates the configured shell with a failsafe shell fallback, and constructs `TERM`, `PATH`, `HOME`, `TMPDIR`, and `ENV` without modification in Phase 1.
- `Process.createSubprocess()` loads `libterm-system.so` and calls registered JNI methods implemented in `libtermexec/src/main/cpp/process.c`/`registration.c`.
- `GenericTermSession` wraps the PTY file descriptor as input/output streams, applies user terminal color/UTF-8 preferences, and delegates resize/UTF-8 PTY operations to `TermIO` JNI.
- A watcher thread calls native `waitExit`; session finish kills the process group/children before closing the PTY.
- `emulatorview.TermSession` owns byte queues and emulator I/O. `EmulatorView`/`TermViewFlipper` handle display, scrolling, input, sizing, and view switching.

Native outputs:

- `libterm-system.so`: PTY subprocess/JNI bridge from `libtermexec`.
- `libappwrap.so`: app command wrapper JNI/helper library from `term`.
- `libexec-t1plus.so` and `libcmd-t1plus.so`: packaged PIE executables used by startup aliases/app command integration.

Do not casually modify any of these paths. Changes require dedicated device/PTTY regression coverage.

## Menus, windows, settings, and special keys

- Toolbar menu `res/menu/main.xml`: working New Window, Close Window, keyboard toggle, reset, WakeLock, and WifiLock actions.
- Navigation drawer `res/menu/menu_term.xml`: Windows, Settings, Special keys documentation, Help, and transcript email.
- Session context menu `res/menu/menu_session.xml`: select/copy/paste/script paste and one-shot send-control/send-Fn actions.
- New Window creates another `ShellTermSession` in the same service list; it does not create a separate environment.
- The toolbar spinner and `WindowListActivity` both switch existing sessions. The window list can create and close sessions using existing service behavior.
- Preferences use `PreferenceFragmentCompat` with `res/xml/preferences.xml`; `TermSettings` reads renderer/session preferences while `com.thothterm.Settings` owns project-specific app preferences.
- Current special keys are not a toolbar. `Term.doDocumentKeys()` displays a dialog describing the configured hardware control/Fn mappings; context-menu actions invoke `EmulatorView.sendControlKey()`/`sendFnKey()`. The future extra-key row must be a separate phase.

## Installer/assets/bootstrap

- `Application` computes app-private root, `etc`, native-library executable path, cache, and default HOME.
- `Installer` creates directories, writes `etc/mkshrc`, copies executables on old Android versions, and copies missing skeleton assets.
- `assets/skel/shrc` becomes `~/.shrc` only when absent.
- `assets/font/DejaVuSansMono.ttf` is the embedded font; its license is included.
- This is Android-shell bootstrap only. No Ubuntu, PRoot, rootfs, package-manager, or download/extraction system exists or was added.

## Background behavior and future constraints

- Sessions survive activity stop/recreation while `TermService` remains alive; service destruction and Android 15+ foreground-service timeout clear sessions.
- There is no durable process restoration after service/process death.
- Future Linux sessions can reuse the concept of one service-owned list, but must define one shared rootfs/runtime and lifecycle explicitly.
- `CommandService` is a local Android `LocalServerSocket` used for app command metadata. It accepts only the same UID or root and is not a TCP/WebSocket server.
- Do not adapt that socket directly into Web Terminal. LAN mode needs an independently threat-modeled, disabled-by-default authenticated HTTP/WebSocket layer, explicit bind address, temporary token, client management, and foreground-service controls.

## Flavors and builds

- `play`: does not request/manage Android 11 all-files access; uses a no-op flavor implementation.
- `full`: adds `MANAGE_EXTERNAL_STORAGE`, enables the request flow, and appends `/X` to version name.
- Debug adds application ID suffix `.devel` and passes it into native CMake definitions.
- No ABI filters are declared, so AGP builds arm64-v8a, armeabi-v7a, x86, and x86_64.

Environment used:

```bash
export JAVA_HOME=/home/lordegypt/PocketCLaw/.tooling/jdk-17
export ANDROID_HOME=/home/lordegypt/Android/Sdk
export ANDROID_SDK_ROOT=/home/lordegypt/Android/Sdk
export GRADLE_USER_HOME=/tmp/thothterm-gradle-user
export PATH="$JAVA_HOME/bin:$PATH"
```

Build commands:

```bash
./gradlew :term:assembleFullDebug -x :term:elfcleaner
./gradlew :term:assemblePlayDebug -x :term:elfcleaner
./gradlew :emulatorview:testDebugUnitTest :libtermexec:testDebugUnitTest :term:testFullDebugUnitTest
./gradlew :term:lintFullDebug
```

The exclusion is required only because host `elf-cleaner` is absent. Installing that declared external tool should allow the normal command without `-x`; do not silently remove the task without deciding release expectations.

Toolchain: Gradle 9.7.1, AGP 9.4.0, Java 17.0.20.1, compile/target/min SDK 36/36/16, build-tools 37.0.0, NDK 23.2.8568313, SDK CMake 3.22.1. Project version is 5.7.0/570.

## Phase 1 visual changes

- Replaced legacy launcher artwork with an original midnight/teal terminal mark.
- Added adaptive vector foreground and Android monochrome icon; retained density-specific legacy and round launchers.
- Updated the foreground-service notification mark and kept source SVGs under `docs/`.
- Added Android 12+ system splash styling without fake Linux/Ubuntu progress.
- Established semantic brand colors and coherent Dark, Light, System, and pure-black AMOLED app chrome.
- Preserved terminal ANSI color schemes as an independent preference and did not force Material colors into the renderer.
- Added a compact `ThothTerm` toolbar wordmark while retaining the live session spinner and working New/Close actions.
- Removed one-pixel terminal margins to keep the emulator as the visual focus.
- Reworked the drawer header with product positioning and a restrained solid surface.
- Polished the window list row/close target, FAB accessibility label, and empty state.
- Renamed English-facing Preferences to Settings and clarified existing settings category/theme labels without adding settings for future features.

Primary files changed are theme/color/string/array resources, terminal/window-list/drawer layouts, adaptive/legacy/notification icon resources, `ThemeManager.java`, and brand SVG sources. No dependency versions or Gradle files changed.

### Files changed

- Documentation: `docs/BASELINE.md`, `docs/THOTHTERM_HANDOFF.md`, and five `docs/thothterm-*.svg` source assets.
- Theme selection: `term/src/main/java/com/thothterm/utils/ThemeManager.java`.
- Theme resources: `values/styles.xml`, `values/colors.xml`, `values/ic_launcher_background.xml`, `values-v31/styles.xml`.
- Labels/preferences: default `values/strings.xml`, `values/arrays.xml`, `values/arraysNoLocalize.xml`, and the five localized theme-entry arrays that override the default list.
- Main/window UI: `activity_term.xml`, `appbar_term.xml`, `header_term.xml`, `activity_windowlist.xml`, `content_windowlist.xml`, `fragment_windowlist.xml`, and both header background drawables.
- Code-native icons: new `drawable/ic_brand_mark.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`; adaptive icon XML points to these drawables.
- Raster compatibility assets: launcher/round launcher PNGs at ldpi through xxxhdpi and notification PNGs at default/ldpi through xxhdpi were replaced. Obsolete adaptive foreground/monochrome mipmap PNG duplicates were removed because adaptive icons now use density-independent vectors.

## Phase 1.5 UI/UX polish (first device-feedback pass)

Trigger: the first real-device test of the Phase 1 build. The engine/session behavior
was accepted; the chrome was crowded, the session label wrapped, the icon was weak, and
several labels were developer-oriented. This phase is polish only.

### What changed

1. Top app bar redesigned (`appbar_term.xml`, `TermActionBar.java`, `Term.java`):
   - Removed the custom `ThothTerm` TextView + live `Spinner` cluster that overlapped
     the menu and wrapped long session titles. It is replaced by the native Toolbar
     `title` ("ThothTerm") and the current session as the second-line `subtitle`.
     AppCompat constrains and ellipsizes both against the menu, so long shell titles
     can never wrap or push the actions away.
   - The right side now exposes exactly two actions: New window (always) and the
     overflow menu.
   - Close window moved into the overflow (no longer an `ifRoom` icon action), removing
     the duplicate close metaphor in the bar.
   - Tapping the title/subtitle area opens the Windows list for quick session switching;
     swipe left/right and drawer -> Windows still work.
   - An explicit dark toolbar theme overlay keeps title, subtitle and menu icons legible
     on the dark primary bar in the Light app theme.
2. Drawer cleanup (`menu_term.xml`, `header_term.xml`, `TermActionBar.java`, `Term.java`):
   - Removed "Email to" and the transcript-email flow entirely.
   - "Special keys" renamed to "Control & Fn keys" to match what it actually does
     (a key-mapping reference dialog, still not yet a toolbar).
   - "Help" replaced by "About", which opens a small About dialog (positioning +
     version + Project site link) instead of jumping straight to a URL.
   - Drawer is now: Windows, Settings, then Control & Fn keys and About.
   - Removed the hidden/empty email row from the drawer header.
3. User-facing overflow wording (`strings.xml`):
   - "Toggle soft keyboard" -> "Show keyboard"
   - "Reset terminal" -> "Restart terminal" (reset toast reworded too)
   - "Take WakeLock" / "Drop WakeLock" -> "Keep screen awake" / "Allow screen to sleep"
   - "Take WifiLock" / "Drop WifiLock" -> "Keep Wi-Fi on" / "Allow Wi-Fi to sleep"
4. Settings information architecture (`xml/preferences.xml`, `strings.xml`):
   - Four groups: Appearance, Terminal, Keyboard, Advanced.
   - Appearance: App theme, Terminal colors, Font size, Font source.
   - Terminal: Terminal type, HOME folder, Command line, Initial command, Shell
     startup, Source system mkshrc, Close window on exit.
   - Keyboard: Back button behavior, Control key, Fn key, Input method, Alt key sends
     ESC, Keyboard shortcuts, Send mouse events.
   - Advanced: Status bar, Action bar, Screen orientation, Size calculation,
     Default to UTF-8.
   - Shortened/softened summaries. All preference keys are unchanged, so persistence is
     preserved and no working setting was removed.
5. Launcher/app identity (vectors, rasters, `docs/*.svg`):
   - New simpler, bolder mark: a white chevron plus a teal command bar (`>_`) on
     midnight navy. The previous outlined rounded rectangle read as coin-like.
   - Redrew `ic_launcher_foreground`, `ic_launcher_monochrome`, `ic_brand_mark` and the
     legacy/round raster PNGs (ldpi-xxxhdpi) from the same geometry; adaptive and
     monochrome themed-icon entries retained.
   - Updated the source SVGs under `docs/`; left the notification icon as-is.
6. Terminal chrome:
   - Removed the leftover 1dp margins around the terminal in the floating-bar layout to
     maximise usable terminal area.
   - Removed resources that only served the deleted spinner/email UI:
     `actionbar_windowlist.xml`, `ic_email_white_24dp.xml`.

### Design decisions

- The bar deliberately contains no custom child view. AppCompat measures and ellipsizes
  the native title/subtitle against the menu, but does not collision-manage custom
  children; that is what produced the broken wrapping. Using native title/subtitle makes
  long session labels safe at any width by construction.
- The session indicator is therefore the subtitle, and tapping the bar opens the Windows
  list (a condensed dropdown) instead of an inline spinner. This trades a one-tap inline
  spinner for guaranteed layout stability and a simpler bar. A true always-visible chip
  would require a custom, weight-based bar and is deferred.
- Brand remains ThothTerm; no Ubuntu/Linux wording was introduced.
- No PTY/session/JNI/emulator code was touched.

### Files changed (Phase 1.5)

- Java: `term/src/main/java/com/thothterm/TermActionBar.java` (spinner removed, subtitle
  + title-click added, email removed), `term/src/main/java/jackpal/androidterm/Term.java`
  (menu/drawer wiring, About dialog, session-subtitle refresh, removed the spinner
  adapter and the transcript-email code).
- Layouts: `appbar_term.xml`, `header_term.xml`, `activity_term_floatbar.xml`;
  deleted `actionbar_windowlist.xml`.
- Menus: `menu/main.xml`, `menu/menu_term.xml`.
- Settings: `xml/preferences.xml`.
- Strings: `values/strings.xml` plus orphan cleanup in 27 `values-*/strings.xml`
  (removed unused `send_email`, `email_transcript_*`, `help`).
- Styles: `values/styles.xml` (toolbar theme overlay + compact title/subtitle styles).
- Icons: `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`,
  `ic_brand_mark.xml`; all `mipmap-*/ic_launcher(.png/_round)` rasters;
  `docs/thothterm-*.svg`.
- Deleted: `drawable/ic_email_white_24dp.xml`.

### Phase 1.5 build and lint results

Built with the documented environment and the `-x :term:elfcleaner` workaround, after a
`:term:clean` to avoid a packaging artifact (see below):

- Full: `term/build/outputs/apk/full/debug/term-full-debug.apk`, 6,742,676 bytes,
  SHA-256 `bd2be8e2d62e10bb0be18677e639203111f05ab58d674ba2ce3718a4eb801ec9`.
- Play: `term/build/outputs/apk/play/debug/term-play-debug.apk`, 6,742,480 bytes,
  SHA-256 `941b3709ccaeb140552b98b589fd0cd8b3f9d0faff217278f0f514792f107d2e`.
- Both APKs contain arm64-v8a, armeabi-v7a, x86, and x86_64 native code.
- `:emulatorview:testDebugUnitTest`, `:libtermexec:testDebugUnitTest`,
  `:term:testFullDebugUnitTest` are still `NO-SOURCE`.
- `:term:lintFullDebug`: 1 error and 68 warnings, identical to the Phase 1 post-change
  count. The sole error is the same pre-existing `GestureBackNavigation` at
  `Term.java:638` (line moved from 670 with the edits). Phase 1.5 introduced no new
  lint issues; the unrelated `UnusedResources`, `RtlHardcoded`, `TypographyEllipsis`,
  etc. warnings are pre-existing.

Packaging gotcha: in the warmed incremental tree, `assemblePlayDebug` produced an 11.1 MB
play APK containing ~4.4 MB of non-entry data (the compressed dex duplicated in the
signing/aligned region). A `:term:clean` rebuild produced the correct 6.74 MB APK.
If a play APK looks unexpectedly large, clean the module before recording hashes.

### Remaining UX issues

- Predictive-back is still not migrated; lint keeps failing on legacy `KEYCODE_BACK`.
- The extra-keys toolbar does not exist yet. The context menu still offers one-shot
  Send control key / Send Fn key, and the drawer documents mappings.
- The session indicator is a subtitle, not a persistent inline chip/dropdown control.
- Settings still use the legacy `PreferenceFragmentCompat` look with no Material 3
  section headers; the grouping is clearer but not yet "premium".
- The window list screen still uses a `ListView` + FAB and can be modernised later.
- Dark/Light/System/AMOLED chrome, the new icon under OEM masks and themed icons, and
  the toolbar subtitle ellipsis behaviour all still need on-device confirmation.

## Verification results

Verified automatically:

- Full debug build succeeds with `-x :term:elfcleaner`.
- Play debug build succeeds with `-x :term:elfcleaner`.
- Java, AIDL, resources, dex, and JNI/CMake compile for all four ABIs.
- Gradle unit-test tasks complete but report `NO-SOURCE`.
- Post-change lint reports 1 error and 68 warnings. The sole error is the same pre-existing `GestureBackNavigation` issue at `Term.java:670`; Phase 1 introduced no lint errors.
- App package metadata remains `com.thothterm.devel` for debug, version code 570, target 36, min 16, label ThothTerm.

Final clean-build artifacts (ignored build outputs, not committed):

- Full: `term/build/outputs/apk/full/debug/term-full-debug.apk`, 6,802,764 bytes, SHA-256 `f94bf7682f0d92a778791df9290a24635dd9a48ee32791aba9bf6fb4b3936b4c`.
- Play: `term/build/outputs/apk/play/debug/term-play-debug.apk`, 6,801,565 bytes, SHA-256 `020c69b69b3a1f2d0ea246d0a09b6f908a54216e72988c5dc688777605a5afd1`.
- Both APKs contain arm64-v8a, armeabi-v7a, x86, and x86_64 native code.

Requires a physical device/emulator:

- Launch and Android 12+ splash appearance.
- Shell prompt/command execution, keyboard input, ANSI rendering, transcript scrolling, PTY resize, and orientation.
- New Window, switcher/spinner, Close Window, background/resume behavior.
- Settings persistence and all four theme modes, especially System day/night and AMOLED.
- Notification appearance across OEMs and adaptive/monochrome launcher masks.

No emulator/device was available. `adb devices -l` outside the restricted sandbox returned an empty list; inside the sandbox ADB cannot install its smart-socket listener.

## Phase 1.6 terminal input and extra-keys polish

Scope: the second device-feedback pass. This phase fixes terminal cursor/text alignment,
settings insets, and replaces the drawer's Control/Fn documentation dialog with a real
extra-keys toolbar. It does not change PTY/JNI/process/session ownership, the terminal
parser, Ubuntu/PRoot, or Web Terminal behavior.

### Cursor root cause and fix

`PaintRenderer` calculated the terminal grid and cursor from one fixed cell width, but
used `Canvas.drawText()` to shape each complete style run. Android then advanced the
glyphs using the typeface's fractional per-glyph metrics. Those cumulative advances are
not guaranteed to equal `column * cellWidth`, even for a nominally monospace system or
embedded font. The text therefore drifted away from the fixed grid while the cursor
remained at the correct terminal column, producing the visible multi-cell gap on longer
input. Layout margins, PTY cursor state, and resize were not the cause.

`PaintRenderer.drawTextInCells()` now places every base character plus its combining
marks at an explicit terminal-cell boundary. Wide and surrogate characters still use
`UnicodeTranscript.charWidth()`, cursor/selection styling is unchanged, and text and
cursor now share the same coordinate system. The fix is renderer-local and therefore
survives rotation, IME resize, font-size changes, and font-source changes without a
spacing offset or shell-input workaround.

### Extra keys architecture

- `ExtraKeysView` is a compact pair of horizontally scrollable rows in both terminal
  activity layouts. It remains part of the resized app content, so `adjustResize` keeps
  it immediately above the Android keyboard without implementing a custom IME.
- Primary row: ESC, CTRL, ALT, TAB, HOME, END, Left, Down, Up, Right.
- Optional symbols row: PGUP, PGDN, INS, DEL, `|`, `/`, `~`, `_`, `-`, `=`, `+`.
- Navigation/editing keys call `EmulatorView.sendExtraKey()` and enter the existing
  `TermKeyListener` key-code mapping. This preserves terminal/application cursor modes
  for bash, vim, nano, tmux, less, top, and similar programs; the UI does not inject
  guessed escape strings or shell commands.
- Symbol buttons use the same code-point/control mapping used by committed IME text.
- CTRL and ALT state lives at the active `EmulatorView` boundary. Tap arms one key,
  long-press locks, and tapping an armed/locked modifier clears it. In addition to
  surface/stroke changes, labels show `[1]` for one-shot and `[L]` for lock, so state is
  not conveyed only by color. IME commits and toolbar/hardware key events consume
  one-shot state and notify the toolbar.
- A session identity check in `Term` clears modifiers across views whenever the current
  session changes. Buttons are non-focusable and return focus to the current terminal.
- Keyboard settings now include `Show extra keys toolbar` and dependent `Show symbols
  row` switches. Both default on and persist in the existing default preferences.

The old drawer entry, dialog code/layout, icon, dialog strings/translations, and dialog-
only short-name arrays were removed. The session context menu's existing direct
Control/Fn actions remain independent terminal actions and were not part of the removed
help UX.

### Settings layout

The preference list now applies display-cutout/system-bar insets plus 12dp breathing
room to its top and bottom padding and allows content to scroll through that padding.
This keeps the Appearance header clear of the top chrome and the last Advanced items
clear of gesture/navigation controls while preserving all existing groups, keys, and
persistence.

### Phase 1.6 files changed

- Renderer/input integration: `emulatorview/.../PaintRenderer.java`,
  `EmulatorView.java`, and `TermKeyListener.java`.
- Toolbar/controller: new `term/.../widget/ExtraKeysView.java` and updated
  `term/.../jackpal/androidterm/Term.java`.
- Terminal layouts: `activity_term.xml` and `activity_term_floatbar.xml`.
- Settings/insets: `TermPreferencesActivity.java`, `preferences.xml`, `defaults.xml`,
  and default `strings.xml`.
- Legacy help removal: `menu_term.xml`; deleted `dialog_special_keys.xml` and
  `ic_vpn_key_white_24dp.xml`; removed the obsolete help strings and short-name arrays
  from the default and localized `values*` resources.

All other dirty worktree changes listed by Git belong to the preserved Phase 1/1.5
visual foundation.

### Phase 1.6 build and lint results

Validated using the documented Java/Android environment and `-x :term:elfcleaner`.
Final artifacts come from a `:term:clean` rebuild to avoid the known warmed-tree APK
packaging anomaly.

- Full: `term/build/outputs/apk/full/debug/term-full-debug.apk`, 6,725,766 bytes,
  SHA-256 `22b2b428f77cb1a8989cfa901fc6b271cbf63f8dda4575c9c0ab42bcc8a0a7bd`.
- Play: `term/build/outputs/apk/play/debug/term-play-debug.apk`, 6,724,971 bytes,
  SHA-256 `403c0a4fc16a992b551c9422538991f2acaeb4d656987f1588a3d2e5302ae712`.
- Both clean builds succeeded and contain all four ABIs with `libappwrap.so`,
  `libcmd-t1plus.so`, `libexec-t1plus.so`, and `libterm-system.so`.
- `:emulatorview:testDebugUnitTest`, `:libtermexec:testDebugUnitTest`, and
  `:term:testFullDebugUnitTest` completed as `NO-SOURCE`.
- `:term:lintFullDebug` reports 1 error and 67 warnings. This is two fewer warnings
  than the first Phase 1.6 run and one fewer than the Phase 1.5 baseline (68); no new
  findings remain. The only error is the pre-existing `GestureBackNavigation` issue,
  now at `Term.java:644` after line movement.

### Phase 1.6 device acceptance still required

No device/emulator was connected, so verify cursor alignment with both fonts and several
sizes; IME show/hide and rotation; settings top/bottom visibility; both toolbar settings;
ESC, Ctrl+C/D/Z, Tab completion, arrows/history, Home/End, PgUp/PgDn, Insert/Delete;
one-shot/cancel/lock modifier states; session switching; and vim/nano/tmux/less/top where
available. Also confirm the compact rows are comfortable on the narrowest supported
phone and under gesture and three-button navigation.

Known issue outside this phase: predictive-back migration remains outstanding and is
the single lint error. No PTY/native/session/parser changes were needed.

### Exact next recommended phase

Phase 1.7: a physical-device acceptance and regression-hardening pass for the completed
visual/input foundation, limited to defects found in the checklist above. Do not begin
Ubuntu/PRoot or Web Terminal work until this UI/input baseline is accepted.

## Phase 1.7 fit-to-screen, drawer, and Arabic/RTL polish

Scope: the final visual/input polish requested after Phase 1.6. This work makes every
extra-keys page fit without horizontal scrolling, balances the navigation-drawer header,
and adds visual-only Arabic shaping and Unicode bidirectional layout. It does not change
PTY bytes, the logical terminal buffer, JNI/native code, process/session ownership, or
the terminal parser.

### Fit-to-screen extra keys

`ExtraKeysView` no longer uses `HorizontalScrollView`. It presents two fixed-width pages,
each containing two compact rows whose buttons share the available width with equal
layout weights:

- NAV/default: ESC, CTRL, ALT, TAB, HOME / END, Left, Down, Up, Right, SYM.
- SYM: PGUP, PGDN, INS, DEL, `|`, `/` / `~`, `_`, `-`, `=`, `+`, NAV.

The SYM and NAV buttons switch pages in place, so all primary keys are immediately
visible and all secondary keys remain reachable without a swipe. Row height, visual
states, non-focusable behavior, modifier lifecycle, active-session routing, and the
existing `EmulatorView`/`TermKeyListener` injection path are preserved. Disabling the
existing symbols preference hides SYM and returns the toolbar to the NAV page.

### Drawer header alignment

The header retains the app icon, ThothTerm title, positioning subtitle, and version.
Its outer vertical padding is now 24dp above and 8dp below, shifting the content group
down without increasing the header height. The icon/title row is explicitly centered
vertically so the text block and mark remain balanced.

### Arabic and RTL rendering architecture

The terminal buffer remains logical Unicode and all shell input/output bytes remain
unchanged. `PaintRenderer` now builds a visual-only `java.text.Bidi` plan for lines that
require bidi processing. Logical UTF-16 positions are mapped to terminal cells with
`UnicodeTranscript.charWidth()`, visual runs are reordered by bidi level, and each run
is assigned an exact fixed-cell box.

- On API 23+, Arabic directional runs are contextually shaped with
  `Canvas.drawTextRun()` and measured with `Paint.getRunAdvance()`.
- On API 16-22, a `StaticLayout`/`TextPaint` fallback performs platform shaping.
- The complete directional run is shaped before ANSI-style, selection, or cursor clips
  are applied. This preserves Arabic joins across visual paint boundaries.
- Shaped output is horizontally fitted to its allocated terminal cells. Latin-only
  lines continue through Phase 1.6's explicit per-cell renderer, preserving the cursor
  alignment fix.
- The block cursor maps its logical column into the reordered visual run. Selection
  touches map visual cells back to logical buffer columns, while copied text remains in
  logical Unicode order.

This is renderer and selection-coordinate work only: strings are never reversed,
presentation-form characters are not stored, and no line or terminal is globally set
to RTL.

### Phase 1.7 files changed

- `term/src/main/java/com/thothterm/widget/ExtraKeysView.java`
- `term/src/main/res/layout/header_term.xml`
- `term/src/main/res/values/strings.xml`
- `emulatorview/src/main/java/jackpal/androidterm/emulatorview/PaintRenderer.java`
- `emulatorview/src/main/java/jackpal/androidterm/emulatorview/TranscriptScreen.java`
- `emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java`
- `docs/THOTHTERM_HANDOFF.md`

All other dirty paths remain the preserved Phase 1, Phase 1.5, and Phase 1.6 work.

### Phase 1.7 build, test, and lint results

Validated with the documented JDK/SDK environment and `-x :term:elfcleaner`. The APKs
below are from one final `:term:clean` invocation that built both variants:

- Full: `term/build/outputs/apk/full/debug/term-full-debug.apk`, 6,730,175 bytes,
  SHA-256 `29b19317883040f4d7b41f1afe9a258f3312f6c0cefee300a882902d6097a04d`.
- Play: `term/build/outputs/apk/play/debug/term-play-debug.apk`, 6,729,027 bytes,
  SHA-256 `955a73a0920ccfd3a37a760a422ee187f1e4eb55d0fb6248df7c80f5e37ba143`.
- Both variants built successfully and the Full APK contains arm64-v8a, armeabi-v7a,
  x86, and x86_64 native libraries.
- `:emulatorview:testDebugUnitTest`, `:libtermexec:testDebugUnitTest`, and
  `:term:testFullDebugUnitTest` remain `NO-SOURCE`.
- `:term:lintFullDebug` remains at 1 error and 67 warnings: no delta from Phase 1.6.
  The only error is the existing `GestureBackNavigation` finding at `Term.java:644`.
- `git diff --check` passes.

### Phase 1.7 device acceptance still required

No device/emulator was connected. On a real device, verify:

1. NAV shows all primary keys without horizontal swiping in portrait and landscape.
2. Keys remain comfortable to tap; SYM/NAV exposes every secondary key.
3. CTRL/ALT one-shot and lock states, Android keyboard coexistence, focus, and session
   switching still work; confirm ESC, Tab, arrows, Home/End, PgUp/PgDn, and shell input.
4. Drawer icon, title, subtitle, and version appear vertically balanced.
5. Render `السلام عليكم`, `مرحبا بالعالم`, `Hello مرحبا World`, `123 العربية 456`, and
   `echo "السلام عليكم"`; confirm joining, visual order, Latin text, and numbers.
6. Create and `cat` a UTF-8 Arabic file, confirming its bytes/text are unchanged.
7. Check cursor-at-end, Left/Right movement, Backspace, Delete, selection/copy, and mixed
   RTL/LTR lines for correct logical-to-visual mapping and no drift.
8. Repeat Arabic and Latin checks after changing font source/size, IME show/hide,
   rotation, and inside vim/nano/tmux where available; confirm ANSI styles still render.

Known remaining issues: predictive-back migration is still the sole lint error, automated
tests are absent, and platform Arabic shaping/cursor geometry—especially the API 16-22
fallback and OEM fonts—requires the physical-device matrix above.

### Exact next recommended phase

Phase 1.8: physical-device acceptance and regression hardening of this completed
visual/input foundation, using the Phase 1.6 and Phase 1.7 checklists and fixing only
reproduced defects. After acceptance, scope predictive-back migration as a separate
focused maintenance task. Do not begin Ubuntu/PRoot, Web Terminal, or Diagnostics.

## Phase 1.8 release-blocking renderer fix (history redraw and Arabic tracking)

Trigger: real-device testing of the terminal-only 1.0 build. Two rendering
defects were reported: recalled shell history drew incorrectly when a long
prompt wrapped, and Arabic script looked unnaturally loose. Both are renderer
defects with no PTY/parser/session changes.

### BUG 1 - history recall / wrapped command redraw

Root cause: `PaintRenderer.BidiLayout.create()` built the paragraph with
`Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT`, which lets the **first strong
character** pick the paragraph base direction. A row whose first strong
character is Arabic (an Arabic-first prompt or working directory) therefore
became an RTL paragraph. `Bidi.reorderVisually()` then reordered the whole row,
so a trailing ASCII command was moved to the opposite side of the prompt and,
once the logical line wrapped, parts of the recalled command landed on the
wrong physical row. A terminal row is always written in logical shell order, so
this is never correct: only genuine RTL runs may be reversed, and they must be
reversed in place. Measured with the JDK: `"<arabic>$ mkdir"` rendered as
`mkdir $<reversed-arabic>` under the default base, and as
`<reversed-arabic>$ mkdir` under a forced LTR base.

Fix: force `Bidi.DIRECTION_LEFT_TO_RIGHT` as the paragraph base in
`BidiLayout.create()`. Pure ASCII/LTR rows still bypass bidi entirely
(`Bidi.requiresBidi()` is false), ASCII runs in mixed rows keep their logical
position, and only minimal RTL runs are reversed. The per-row cell map already
keeps the wrap boundary correct.

### BUG 2 - Arabic tracking too loose

Root cause: `drawRtlText23()` always scaled the shaped RTL run horizontally to
exactly `run.width() * mCharWidth` (`canvas.scale(targetWidth / advance, 1)`).
When the natural shaped advance was smaller than the Latin-cell budget, the run
was stretched, spreading joined Arabic glyphs apart and adding artificial
tracking.

Fix: draw the shaped run at its natural advance and only compress it when it
would overflow its cells (`scale = min(1, targetWidth / advance)`), right
aligned inside its cell box. The cursor for an RTL run is now placed with the
same glyph advances (`Paint.getRunAdvance` for the run and for the prefix)
instead of the cell grid, so it stays on the actual glyph. The API 16-22
`StaticLayout` fallback applies the same no-stretch rule. LTR runs and pure
ASCII rows keep the fixed-cell renderer (cursor alignment fix preserved), and
selection/copy still use the logical buffer.

### Phase 1.8 files changed

- `emulatorview/src/main/java/jackpal/androidterm/emulatorview/PaintRenderer.java`
  (forced LTR paragraph base, natural-advance RTL run drawing, advance-based RTL
  cursor, `runForLogicalCell`).

No other module, the terminal parser, JNI, PTY, session lifecycle, or the
Phase 1/1.5/1.6/1.7 UI work were changed.

### Phase 1.8 build and lint results

Validated with the documented JDK/SDK environment and `-x :term:elfcleaner`.
Artifacts are from a `:term:clean` rebuild (the warmed tree otherwise produces
the known oversized APK).

- Full: `term/build/outputs/apk/full/debug/term-full-debug.apk`, 6,730,635
  bytes, SHA-256 `9b95b3797bf6ca5ef2378440b1a6523a8f6a5da0f39fe6549e3c23ffacdc434e`.
- Play: `term/build/outputs/apk/play/debug/term-play-debug.apk`, 6,729,488
  bytes, SHA-256 `54f73a653489e8e127bfa08d14d8bcf92795eed28e5d36c4a8f69da9e811993b`.
- `:term:lintFullDebug`: 1 error and 67 warnings, unchanged from Phase 1.7. The
  only error is the pre-existing `GestureBackNavigation` at `Term.java:644`.
- `git diff --check` passes.

### Phase 1.8 device acceptance still required

No device/emulator was connected in this environment. On a real device verify:

1. Set a long Arabic-first prompt/path; recall history with UP/UP/UP/DOWN/DOWN
   (`pwd`, `mkdir`, `cd G`, `echo hello`, `ls -la`): each command renders in
   typed order, no stray glyph, no character jump across the wrap, and editing
   the recalled command with LEFT/BACKSPACE/RIGHT works.
2. Render `مرحبا`, `السلام عليكم`, `كيف الحال`, `مرحبا بالعالم`,
   `Hello مرحبا World`, `123 العربية 456`, and `echo "السلام عليكم"`: letters
   are joined and compact, no artificial tracking, mixed LTR/RTL order and the
   cursor remain correct.
3. Repeat 1-2 with ASCII-only, Arabic-only, and mixed lines, in portrait and
   landscape, with the keyboard shown and hidden, and after a font-size change.
4. Re-check Ctrl+C, Home/End, Left/Right, Backspace/Delete, selection/copy, and
   ANSI colors (no regression of the fixed-cell cursor alignment or UTF-8).

### Exact next recommended step

Run the Phase 1.8 device acceptance matrix above, then proceed with the 1.0
release baseline. Do not begin Ubuntu/PRoot, Web Terminal, or Diagnostics.

## Phase 1.9 right-edge cell clipping and friendly HOME prompt

Trigger: real-device history recall. A recalled ASCII command lost its first
character and a stray marker appeared at the right edge.

### Reproduction

With a long prompt whose length places the command's first character in the last
terminal column, recalling `echo hello`, `cd ~`, or `hi` displayed `cho hello`,
`d ~`, and `i`; the missing first character (and a partial glyph) appeared near
the right edge. The shell content itself was correct, so history and key
translation are not involved.

### Exact root cause

`EmulatorView.updateTermSize()` centered the cell grid with
`mLeftPadding = (w - mVisibleColumns * (int) mCharacterWidth) / 2`. It truncated
the fractional cell advance to an `int` before multiplying. Because
`mColumns = (int)(w / mCharacterWidth)`, the real grid width
`mColumns * mCharacterWidth` is larger than `mColumns * (int)mCharacterWidth`, so
the over-estimated padding shifted every column to the right and pushed the last
column (and its cursor) to or past the view's right edge. Measured against the
bundled DejaVu Sans Mono with fractional metrics, the grid can overshoot the view
by up to a full cell (about 20 px at typical sizes). Any character landing in the
last column — exactly where a long prompt pushes the command's first character —
is drawn off-screen, and the partially clipped neighbour is the stray marker.

This is not a bidi or logical-to-visual mapping fault: pure-ASCII rows never
enter the bidi path (`Bidi.requiresBidi` is false). The Phase 1.8 forced-LTR
base fix and Arabic natural-advance rendering are unaffected.

### Exact fix

`EmulatorView.updateTermSize()` now centers using the same fractional width used
to lay out text:
`mLeftPadding = (int)((w - mVisibleColumns * mCharacterWidth) / 2f)`, clamped to
`>= 0`. Because `mVisibleColumns = (int)(w / mCharacterWidth)`, the grid now
always fits inside the view, so no column or cursor is clipped.

### Friendly HOME prompt (presentation only)

The prompt exposed the full private path
`/data/user/0/com.thothterm.devel/app_HOME $`.
`Installer.installAppScriptFile()` now writes a `__thothterm_prompt_path` shell
function and `PS1` into the generated `etc/mkshrc`, placed before `~/.shrc` is
sourced so a user prompt still wins:

- cwd == `$HOME` -> `app_HOME`
- cwd under `$HOME` -> `app_HOME/<relative>`
- otherwise -> the real `$PWD`

The short name is derived from `$HOME` at runtime (`${HOME##*/}`), so Debug
(`com.thothterm.devel`) and Release (`com.thothterm`) both work, and a path
outside HOME never falsely claims `app_HOME`. `pwd` still prints the real
filesystem path; HOME, working-directory behavior, PTY, session, and JNI are
unchanged.

### Phase 1.9 files changed

- `emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java`
  (fractional cell-width centering).
- `term/src/main/java/com/thothterm/Installer.java` (generated friendly prompt).

No other module, and no PTY/parser/JNI/session/term-key code.

### Phase 1.9 build, lint, and test results

Validated with the documented JDK/SDK environment and `-x :term:elfcleaner`.
Artifacts are from a `:term:clean` rebuild.

- Full: `term/build/outputs/apk/full/debug/term-full-debug.apk`, 6,730,700
  bytes, SHA-256 `e1cf1074a888a9aaac740fc7db012d404ee4dd48ba4ed661f70c7d02f1871e53`.
- Play: `term/build/outputs/apk/play/debug/term-play-debug.apk`, 6,730,038
  bytes, SHA-256 `ccef6d61a496b54a58fe9a537c0d30cf8c77088516143d05d02d33a9cd135cf4`.
- `:term:lintFullDebug`: 1 error and 67 warnings, unchanged pre-existing
  baseline (`GestureBackNavigation` at `Term.java:644`).
- `git diff --check` passes. Unit-test tasks remain `NO-SOURCE`.
- Prompt snippet validated under a POSIX shell (dash) for `$HOME`, `$HOME/sub`,
  deep subdirectories, `/`, and a sibling `app_HOME2`, plus preservation of a
  user `.shrc` PS1 override.

### Phase 1.9 device acceptance still required

No device/emulator was connected. On the real device verify:

1. Set a long prompt that puts the command's first character in the last column;
   recall `echo hello`, `cd ~`, `hi`, `pwd`, `mkdir`, `ls -la` with UP/UP/UP/
   DOWN/DOWN: the first character stays visible and no marker appears at the
   right edge; editing, Left/Right, and cursor position remain correct.
2. Repeat with a prompt that wraps, in portrait and landscape, with the keyboard
   shown and hidden, and after a font-size change.
3. `pwd` still prints the full real path.
4. `cd "$HOME"` shows `app_HOME $`; `mkdir -p test/project && cd test/project`
   shows `app_HOME/test/project $`; `cd /` no longer shows `app_HOME`.

### Exact next recommended step

Run the Phase 1.9 acceptance checks above, then resume the 1.0 release baseline.
Do not begin Ubuntu/PRoot, Web Terminal, or Diagnostics.

## TERMINAL EMULATOR 1.0 GOLDEN BASELINE

This working tree (Phases 1 through 1.9) is frozen as the ThothTerm Terminal
Emulator 1.0.0 golden baseline.

- Product: ThothTerm Terminal Emulator (launcher label `ThothTerm`)
- Positioning: Terminal emulator for Android
- Package ID: `com.thothterm` (debug: `com.thothterm.devel`)
- Version: `1.0.0`, versionCode `10000`
- Annotated tag: `terminal-v1.0.0`
- Baseline commit (tag target): `7a3c88e52075a0a7abaa9dd61150c122c119718f`
- Release (feature-branch) commit: `108d3e79bf68a6c51044e48519ec628da64cd2b4`
- GitHub Release: https://github.com/Lord1Egypt/AndroidThothTerm/releases/tag/terminal-v1.0.0
- Full manifest, artifact hashes, and restore steps: `docs/GOLDEN_BASELINE.md`
- Release notes: `docs/RELEASE_NOTES_1.0.md`
- Edition split / future `com.thothterm.linux`: `docs/EDITION_PLAN.md`

Accepted and frozen: terminal engine and PTY/session architecture, multiple
windows, cleaned drawer and settings, Dark/Light/System/AMOLED themes, Extra
Keys NAV/SYM toolbar with one-shot/locked CTRL/ALT, cursor/cell alignment,
right-edge clipping fix, friendly `app_HOME` prompt, UTF-8, Arabic shaping,
Unicode BiDi with logical/visual cursor and selection mapping, Arabic natural
glyph advance, and the forced-LTR paragraph base.

Artifacts (unsigned release uses the production ID; no signing key present):
Full/Play debug APKs `6,730,700`/`6,730,038` bytes, Full/Play release APKs
`5,548,608`/`5,547,588` bytes, Full/Play release AABs `4,838,346`/`4,837,316`
bytes. Exact SHA-256 values are in `docs/GOLDEN_BASELINE.md`.

Lint baseline unchanged: 1 error (`GestureBackNavigation`, `Term.java:644`),
67 warnings.

Next recommended phase (not started): shared Diagnostics/Logging, then the
future `com.thothterm.linux` edition. Do not begin Ubuntu/PRoot/Web Terminal
from the terminal baseline without a separate design.

## POST-1.0 PHASE: Diagnostics / structured logging foundation

Branch `feature/thotterm-linux`, starting from the accepted baseline commit
`55b4ba931371ac957dad163913f1050a38d0b739`. The immutable golden tag
`terminal-v1.0.0` (`7a3c88e5…`) is untouched. This phase adds diagnostics only;
no Ubuntu/PRoot/rootfs/runtime/Web Terminal/SSH/networking work was started.

### What was added

- A single logging facade, `com.thothterm.logging.ThothLog`, used by all
  application code. Raw `android.util.Log` calls were replaced in the app
  module (the pointer `jackpal.androidterm.compat.PRNGCFixes` utility and the
  `emulatorview` renderer intentionally keep their own behaviour; the renderer
  must stay independent of the logger).
- Structured levels `ERROR`/`WARN`/`INFO`/`DEBUG`/`VERBOSE` (default `INFO`)
  and categories `APP`, `UI`, `SESSION`, `PTY`, `SHELL`, `INSTALLER`,
  `STORAGE`, `NETWORK`, with reserved `RUNTIME`, `LINUX`, `ROOTFS`, `PROOT`,
  `WEB`, `SECURITY` for the Linux phase.
- `Settings → Diagnostics` with View logs, Log level, Developer logging,
  Clear logs, and Export logs. Log level and developer logging apply
  immediately, with no reinstall.
- A live Logs screen (`com.thothterm.LogsActivity`) with timestamp, level,
  category and message; search; level/category filters; pause/resume;
  auto-scroll; clear; and export.
- Bounded rotating persistence in `<app files dir>/logs/`:
  `thotterm.log` … `thotterm.3.log`, 4 MB × 4 files ≈ 16 MB maximum, oldest
  removed automatically. Writes are asynchronous on a single low-priority
  daemon thread; logging failures are swallowed and can never crash the app.
- Privacy rules: terminal input, command text, terminal output, environment
  values, clipboard data, and credentials are never logged; every message is
  normalized to one line and capped. Future Linux code must log through the
  same API with the reserved categories.

See `docs/DIAGNOSTICS.md` for the full architecture, API, rotation, privacy
rules, and the "how future Linux code must log" section.

### Files changed

- New logging core: `term/src/main/java/com/thothterm/logging/{ThothLog,
  LogLevel, LogCategory, LogEntry, LogBuffer, LogFileStore, LogExporter,
  Formats}.java`.
- New UI: `term/src/main/java/com/thothterm/LogsActivity.java`,
  `term/src/main/java/com/thothterm/LogsAdapter.java`,
  `term/src/main/res/layout/activity_logs.xml`,
  `term/src/main/res/layout/item_log.xml`, `term/src/main/res/menu/logs.xml`.
- Settings/manifest/resources: `TermPreferencesActivity.java`,
  `xml/preferences.xml`, `values/strings.xml`, `values/arrays.xml`,
  `values/defaults.xml`, `values/colors.xml`, `AndroidManifest.xml`.
- Instrumentation: `Application.java`, `Installer.java`,
  `jackpal/androidterm/{Term,TermService,ShellTermSession,GenericTermSession,
  RunScript,RunShortcut}.java`, `services/SessionsService.java`,
  `WindowListActivity.java`, `RemoteActionActivity.java`,
  `shortcuts/AddShortcut.java`, `utils/ThemeManager` consumers.
- Docs: this section and `docs/DIAGNOSTICS.md`.

### Build, lint, and verification

Validated with the documented JDK/SDK environment and `-x :term:elfcleaner`.
Artifacts, sizes, SHA-256 values, `git diff --check`, lint count, and the
existing `NO-SOURCE` unit-test tasks are recorded in the phase commit message
and in the owner-facing phase report. The known lint baseline must not silently
worsen; the only expected pre-existing error remains `GestureBackNavigation`.

Logging core logic (`LogLevel`, `LogCategory`, `LogBuffer`, `LogFileStore`,
`LogEntry`, `Formats`) is Android-free and was exercised by a standalone JVM
harness: level/category parsing, ring-buffer bounding and ordering, timestamp
and line formatting, rotation bounded to 4 files, and `deleteAll`.

### Device acceptance still required

1. Settings → Diagnostics exists. 2. Logs screen opens. 3. INFO logs appear at
startup. 4. New Window logs a SESSION event. 5. Closing a window logs an event.
6. Switching sessions logs an event. 7. PTY resize appears at DEBUG. 8. Level
filtering works. 9. Search works. 10. Pause/resume works. 11. Auto-scroll
works. 12. Clear logs works. 13. Export logs works. 14. Restart preserves
rotated/persistent logs as designed. 15. Logging does not noticeably affect
terminal responsiveness. 16. Typed terminal text never appears in logs.
17. Secrets/environment values never appear in logs. 18. Dark/Light/System/
AMOLED render correctly.

### Exact next recommended phase

Embedded Ubuntu 26.04 LTS ARM64 runtime (separate, explicitly authorized). The
Linux layer must use the reserved `RUNTIME`/`LINUX`/`ROOTFS`/`PROOT`/`WEB`/
`SECURITY` categories through `ThothLog`. No Ubuntu/PRoot work has begun.

## TERMINAL EMULATOR 1.1.0 RELEASE (Diagnostics)

The terminal edition was released as **1.1.0**: the 1.0 golden baseline plus the
completed Diagnostics / Structured Logging feature.

- Product: ThothTerm Terminal Emulator (`com.thothterm`; debug
  `com.thothterm.devel`).
- Version: `1.1.0`, versionCode `10100`.
- Annotated tag: `terminal-v1.1.0`.
- Release title: "ThothTerm Terminal Emulator 1.1.0 — Diagnostics Release".
- `terminal-v1.0.0` (`7a3c88e`) remains the immutable Golden Baseline and was
  not modified, moved, or retagged.

The Diagnostics implementation was authored on `feature/thotterm-linux` as
commit `7e716b7` and cherry-picked onto a `release/terminal-v1.1.0` branch off
`master` as `5308000`, so no Ubuntu branch content entered the terminal edition.
The cherry-picked commit was audited first: it contains only the terminal-app
Diagnostics work (plus the `docs/DIAGNOSTICS.md` documentation and reserved
log-category names); no `term-ubuntu`, rootfs, PRoot, or
`com.thothterm.ubuntu` code.

Added in 1.1.0: `Settings → Diagnostics` (View logs, Log level, Developer
logging, Clear logs, Export logs); live log viewer with search, level/category
filters, pause/resume, auto-scroll; levels ERROR/WARN/INFO/DEBUG/VERBOSE
(default INFO); bounded rotating app-private logs (~4 MB × 4 files); SAF export;
privacy-safe logging with no terminal input/output/commands/secrets. See
`docs/DIAGNOSTICS.md`, `docs/TERMINAL_RELEASE_1.1.md`, and
`docs/RELEASE_NOTES_1.1.md`.

Signing: no ThothTerm production signing credentials exist, so debug APKs use
the Android debug key and release APKs/AABs are unsigned. Lint is unchanged at
the baseline 1 error (`GestureBackNavigation`) and 67 warnings.

The Linux/Ubuntu edition remains a separate product on `feature/thotterm-linux`
(`com.thothterm.ubuntu`) and is not part of this release.

## Known risks and technical debt

- Target SDK 36 predictive back is not migrated; lint fails on legacy `KEYCODE_BACK` handling. Treat this as a focused behavior task because back can close sessions or send terminal characters.
- `elf-cleaner` is an undocumented external executable dependency for every debug symbol-strip task.
- Automated tests are effectively absent from the active Gradle build.
- The app retains deprecated/fragile compatibility surfaces including legacy package names, `sharedUserId` compatibility, exported legacy service/interface behavior, very old minSdk 16 support, and broad storage code.
- `TermService` calls `System.exit(0)` on API 35+ destruction after timeout; lifecycle behavior deserves isolated review.
- Theme mode changes recreate activities; verify session/view rebinding on device.
- The compact toolbar uses existing action/menu mechanics and must be checked on narrow screens and long localized session titles.
- The public app is `com.thothterm`, but many stable internals remain under `jackpal.androidterm`; do not mass-rename them.

## Deliberately not changed

- PTY opening, subprocess creation, environment construction, shell parsing,
  watcher/exit handling, process groups, JNI/C, terminal parser, scrolling, resize,
  service ownership, or New/Close Window behavior.
- Application ID, namespaces, version, SDK levels, dependencies, build flavors, release signing/minification, or ABI set.
- Ubuntu/rootfs, PRoot, apt tooling, embedded Linux, distro selection, extraction/download flows.
- New session architecture, background-service redesign, Web Terminal/LAN server,
  analytics, telemetry, ads, or Compose migration.

## Exact recommended next task

Phase 1.8 physical-device acceptance and regression hardening. Run the combined Phase
1.6/1.7 checklist on API 23/29/31/36 where available, including narrow-screen toolbar,
drawer alignment, both fonts/sizes, Arabic/mixed bidi rendering, cursor/selection, ANSI,
and vim/nano/tmux. Fix only reproduced defects. After acceptance, scope predictive-back
migration separately with explicit session-close/send-character tests. Do not begin
Ubuntu/PRoot, Web Terminal, or Diagnostics.

## Git status at handoff

- Branch: `feature/thotterm-visual-foundation`.
- `HEAD` remains the audited baseline commit `b48c119ec6ce6928c9c3f83750492715b2b7e392`.
- The Phase 1 and Phase 1.5 implementations plus both documentation files are uncommitted
  for owner review; modified/new/deleted files are enumerated by `git status --short` and
  summarized above.
- No unrelated pre-existing user changes were present in the fresh clone.
- Gradle caches were removed from the repository. APKs and intermediates remain ignored
  under module build directories and are not staged.
- Nothing was committed, pushed, rebased, reset, or history-rewritten.

## NEXT AGENT START HERE

1. Read `docs/BASELINE.md`, then this file.
2. Run `git status --short --branch` and preserve the Phase 1/Phase 1.5 worktree; do not
   reset it.
3. Export the documented JDK/SDK/Gradle variables.
4. Rebuild both debug flavors with `-x :term:elfcleaner` unless the host tool is installed.
   If a debug APK is unexpectedly large, run `:term:clean` first (see the Phase 1.5
   packaging note).
5. Start with the Phase 1.6/1.7 on-device validation matrices, especially narrow-screen
   extra keys and Arabic/mixed bidi cursor/selection behavior.
6. If any terminal behavior fails, stop and isolate it before editing PTY/session/JNI/
   emulator code.
