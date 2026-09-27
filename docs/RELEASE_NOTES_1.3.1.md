# ThothTerm Terminal Emulator 1.3.1

A narrow bug-fix release on top of 1.3.0 (`terminal-v1.3.0`). Nothing else
changed.

## Fixed

- **Keep screen awake** now does what it says. It sets Android's foreground
  display flag (`FLAG_KEEP_SCREEN_ON`) on the terminal window, so the display
  no longer turns off at the system screen timeout while the terminal is in
  front. It no longer depends on a CPU wake lock or a battery-optimization
  exemption, and it no longer sends you to battery settings.

  Up to 1.3.0 the action took a `PARTIAL_WAKE_LOCK`, which keeps the CPU
  running but lets the display sleep, and then offered battery-optimization
  settings, which have nothing to do with the screen.

## How it behaves

- *Keep screen awake* in the menu keeps the display on while the terminal is
  the app in front. The menu then offers *Allow screen to sleep*, which turns
  it off again straight away.
- When another app is in front, or the Home screen, the display sleeps at the
  normal timeout. Coming back to the terminal restores the choice.
- The choice lasts while the terminal is open, including rotation and other
  changes that recreate the screen. *Exit*, or opening the app fresh, starts
  with it off.
- No permission is needed and nothing asks for one.
- The labels are "Keep screen awake" / "Allow screen to sleep" in English and
  in the translations that describe the screen; translations that named a
  "WakeLock" now show the English label.

## Unchanged

- *Keep Wi-Fi on* / *Allow Wi-Fi to sleep* is a separate Wi-Fi lock and works
  exactly as before.
- Everything in 1.3.0: see `docs/RELEASE_NOTES_1.3.md`. The regular terminal
  still has no LAN Mode, no Linux distribution, no PRoot and no rootfs.
