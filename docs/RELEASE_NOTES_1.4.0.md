# ThothTerm Terminal Emulator 1.4.0

A feature release on top of 1.3.1 (`terminal-v1.3.1`).

## New

- **Upload files** (overflow menu) copies one or more documents you pick
  into the **current directory of the terminal window in front** — wherever
  its shell has `cd`'d to, not a fixed folder.
- **Upload folder** copies a whole folder, with its sub-folders, file names
  (Unicode included), hidden files and empty folders as the picker shows them.
- **Keep screen awake while charging** (Settings, on by default): while the
  phone is connected to power (USB, AC, wireless or dock) and the terminal is
  in front, the screen stays on.

## How uploads behave

- Android's own document picker is used. No storage permission is requested;
  only the documents you pick are read.
- Before anything is copied, the dialog shows where it will go:
  *Upload to: /data/…/app_HOME/project*. Changing directory during the
  transfer does not move it. Progress shows the file, bytes and percentage;
  **Cancel** stops at once.
- Nothing is ever overwritten. If `notes.txt` exists, the upload becomes
  `notes (1).txt`; a folder that exists is kept and the upload becomes
  `project (1)`. Uploads never merge into an existing folder.
- A file never appears under its name half-written: everything is written to
  a hidden staging folder in the same directory and moved into place when it
  is complete. Cancel, an error, running out of space or Exit remove it; if
  the app is killed mid-upload, it is removed at the next start.
- Existing links in the directory are never followed. Names from the picker
  are checked like any untrusted input (no `..`, separators, control
  characters or over-long names). Nothing uploaded is executed, made
  executable or unpacked.
- Files get the same permissions a `cp` in that shell would give them.
- Needs Android 5.0 or newer; on older versions the two actions are hidden.

## How the screen behaves

- The screen stays on when **Keep screen awake** was chosen *or* the phone is
  charging with the setting on. Unplugging drops the charging reason at once.
- While only charging keeps it on, the menu says **Screen awake while
  charging** and opens the setting; it never offers "Keep screen awake" while
  the screen is already kept on.
- With another app in front the terminal never keeps the display on. Coming
  back while still charging keeps it on again.
- Still only the window's `FLAG_KEEP_SCREEN_ON`: no wake lock, no permission,
  no battery-optimization exemption, and the system screen timeout is never
  changed.

## Unchanged

- The Regular Terminal remains **LAN-free**: no LAN Mode, no HTTP or
  WebSocket server, no browser terminal and no remote upload.
- Everything in 1.3.1 and 1.3.0.

## For developers

- `com.thothterm.upload` is shared byte for byte with the Garden editions;
  see `docs/garden/UPLOADS.md` on the Garden branches for the design.
- libtermexec gains `Process.renameNoReplace` (`renameat2` with
  `RENAME_NOREPLACE`), covered by the existing `-keep` rule for
  `com.thothterm.Process$Native`.
