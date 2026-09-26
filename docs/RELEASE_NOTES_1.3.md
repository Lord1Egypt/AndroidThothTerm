# ThothTerm Terminal Emulator 1.3.0 — Golden Baseline

ThothTerm is a terminal emulator for Android. Version 1.3.0 is the new
**Regular ThothTerm Terminal Golden Baseline**: it brings the terminal fixes
proven in the Ubuntu edition to the regular app, fixes the notification and
window-close lifecycle, makes the build portable, and ships the release build
minified with R8. It remains a local Android terminal — nothing listens on the
network.

## What's new in 1.3.0

- **Paste from any app**: text copied in Telegram, Chrome and other apps that
  publish `text/html` or other `text/*` clips now pastes. Clips that are only a
  URI or an Intent are refused rather than opened, and clipboard contents are
  never logged.
- **Selection at the start of a line**: a long-press on the first columns no
  longer gets swallowed by the navigation drawer's edge gesture. The drawer
  still opens from the toolbar button and can still be swiped shut.
- **No more stuck Fn/Ctrl**: a latched Volume Up (Fn) or Volume Down (Ctrl)
  modifier is cleared when the app goes to the background, when it comes back,
  and by *Restart terminal*. Latching itself still works.
- **Clear scrollback**: the terminal now supports `CSI 3 J` (erase saved
  lines), and a *Clear scrollback* menu item does the same for the current
  window. The visible screen, the cursor and the running shell are kept.
- **Zoom**: pinch with two fingers, or use *Zoom in*, *Zoom out* and *Reset
  font size* in the menu. Zoom moves the existing font-size setting
  (6–32 pt) and resizes the terminal's rows and columns to match.
- **Compact Extra Keys in landscape**: the Extra Keys bar uses a compact size
  in short windows, so the terminal keeps more rows. Portrait is unchanged.
- **Notification**: ThothTerm asks once for notification permission (Android
  13+). While the terminal service runs, the notification "ThothTerm — ThothTerm
  is running" is shown straight away, including right after permission is
  granted; tapping it opens the terminal, and it is removed when the service
  stops.
- **Closing a window cleans up**: closing a window that is still running a
  command now ends that window's shell, its foreground command and its
  background jobs. Other windows are untouched; a job started with `nohup`
  keeps running, as `nohup` intends.
- **Exit**: a new *Exit* menu item, after confirmation, hangs up every session,
  kills what is left of them, stops the service, removes the notification and
  the Recents card, and ends the app process. Nothing on disk is touched.
- **Smaller release APK**: the release build is minified with R8 (about
  5.6 MB → 3.0 MB).
- **Portable build**: the build no longer demands a JetBrains Java 17; it
  builds with the JDK the environment provides (verified with JDK 17 and 21).

## Existing capabilities (retained)

- Modern text selection with handles and the floating Copy/Paste/Select all
  toolbar, embedded DejaVu Sans Mono, HOME startup and the friendly prompt
  (1.2.0).
- Diagnostics and structured logging (1.1.0).
- Arabic and Unicode bidirectional rendering with logical copy.
- Multiple terminal windows, themes, Extra Keys, and the legacy integration
  entry points (RemoteInterface, RunScript, RunShortcut, shortcuts).

## Notes

- Production signing credentials are not configured in this repository. Debug
  APKs are signed with the Android debug key and are for testing only. Release
  APKs and AABs are unsigned.
- 16 KB page size: the 64-bit native libraries are 16 KB aligned and the APKs
  pass `zipalign -P 16`. The 32-bit libraries keep 4 KB alignment, which
  Android does not require to change.
- Translated locales keep their existing wording for the notification text.
- The single pre-existing lint error (`GestureBackNavigation`) is unchanged.
- This release is the regular terminal emulator only. It contains no Ubuntu or
  other Linux distribution, no PRoot, no rootfs, and no network or web terminal.
