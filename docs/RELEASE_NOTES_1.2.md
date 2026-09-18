# ThothTerm Terminal Emulator 1.2.0 — Selection & Typography Release

ThothTerm is a terminal emulator for Android. Version 1.2.0 builds on the 1.1
Diagnostics release with modern text selection, embedded DejaVu Sans Mono
typography, and a correct startup directory and prompt.

## What's new in 1.2.0

- **Modern text selection**: long-press starts a selection directly in the
  terminal, with draggable start/end handles and the Android floating toolbar
  (Copy, Paste, Select all). The old full-screen "Edit text" dialog and its
  context-menu entries are gone.
- **Tight selection geometry**: the highlight and the floating-toolbar bounds
  align to the exact selected terminal cells, including Arabic/RTL, wide, and
  combining text. Logical copy is unchanged.
- **Embedded DejaVu Sans Mono by default**: the font source defaults to the
  bundled font, shown as "Built-in".
- **Correct startup directory**: regular terminal sessions start in HOME via the
  native child `chdir(HOME)`; the injected `cd ~` startup command is removed.
- **Friendly dynamic prompt**: `app_HOME $` when in HOME, `app_HOME/sub $` inside
  it, and the real path elsewhere. `pwd` still prints the real filesystem path,
  and no Android package path is hard-coded.

## Existing capabilities (retained)

- Diagnostics and structured logging (1.1.0): live viewer, filters, levels,
  rotating app-private logs, clearance/export, privacy-safe events.
- Extra Keys NAV/SYM toolbar with one-shot and locked CTRL/ALT.
- Arabic and Unicode bidirectional (RTL) rendering with logical cursor,
  selection, and clipboard mapping.
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
