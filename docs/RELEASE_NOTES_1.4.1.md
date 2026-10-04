# ThothTerm Terminal Emulator 1.4.1

A security patch on top of 1.4.0 (`terminal-v1.4.0`). Update recommended.

## Security

- Text that another installed app supplied through the share target or the
  "Term here" / window-opening integration could be typed into a terminal
  session as a command. It is no longer typed: the directory is now passed as a
  structural working-directory value, and nothing from outside the app is
  written to the terminal as input.
- Add-on applications are trusted only when they are signed with the same
  certificate as the terminal, not because of their package name.
- The local socket server now refuses a connection from another app and keeps
  serving instead of stopping for everyone.
- Permanent regression tests pin the exported components and the integration
  surface (`docs/security/THORNS.md`).

Details of the original report stay in the private advisory until users have
had time to update.

## Unchanged

Features, permissions, package id (`com.thothterm`), data and settings are the
same as 1.4.0; upgrades install in place.
