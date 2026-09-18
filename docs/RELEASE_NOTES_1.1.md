# ThothTerm Terminal Emulator 1.1.0 — Diagnostics Release

ThothTerm is a terminal emulator for Android. Version 1.1.0 adds a built-in
Diagnostics and logging system on top of the 1.0 terminal baseline.

## What's new in 1.1.0

- **New Diagnostics section** in Settings.
- **Live log viewer** that shows what ThothTerm is doing internally.
- **Search and filtering** by level and category.
- **ERROR / WARN / INFO / DEBUG / VERBOSE levels**, with Developer logging off
  by default for normal use.
- **Persistent rotating diagnostic logs** kept in app-private storage, capped at
  about 16 MB so they can never grow without bound.
- **Clear and export logs**, with export through the standard Android file
  chooser.
- **Session, PTY, and shell diagnostic events** (for example session
  create/close, PTY resize, shell exit code).
- **Privacy-safe logging**: terminal input, terminal output, commands, and
  secrets are never written to the logs.

Changing the log level takes effect immediately — no reinstall or restart.

## Existing capabilities

These were already present and remain:

- Extra Keys NAV/SYM toolbar with one-shot and locked CTRL/ALT.
- Arabic and Unicode bidirectional (RTL) rendering with logical cursor and
  selection mapping.
- Multiple terminal windows.
- Dark, Light, System, and AMOLED themes.
- Foreground-service-owned sessions that survive the activity.

## Notes

- Production signing credentials are not configured in this repository. Debug
  APKs are signed with the Android debug key and are for device testing only.
  Release APKs and AABs are unsigned.
- The single pre-existing lint error (`GestureBackNavigation`) is unchanged;
  predictive-back migration is not part of this release.
- This release is the terminal emulator only. It contains no Ubuntu, Linux
  distribution, PRoot, or embedded rootfs.
