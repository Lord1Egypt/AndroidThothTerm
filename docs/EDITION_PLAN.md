# ThothTerm edition split and coexistence plan

ThothTerm is split into two independently installable products that share one
terminal engine. Only the terminal emulator edition exists today.

## Editions

| | ThothTerm Terminal Emulator | ThothTerm Linux (future) |
| --- | --- | --- |
| Application ID | `com.thothterm` | `com.thothterm.linux` |
| Launcher label | ThothTerm | ThothTerm Linux |
| Purpose | Standalone Android terminal emulator | Linux development workspace for Android |
| Runtime | Android shell / PTY only | Embedded Ubuntu ARM64 runtime (planned) |
| Status | Released 1.0.0 golden baseline | Not implemented |

Both application IDs are distinct so the editions can be installed side by
side without clobbering each other's data or signature.

## Shared engine, no duplicated code

The terminal engine lives in the library modules that the current app already
depends on:

- `emulatorview/` — terminal parser, screen/transcript, rendering, key handling.
- `libtermexec/` — PTY process and terminal-control JNI library.

`term/` is the Android application layer (activities, service, resources,
manifest). The future Linux edition must reuse `emulatorview` and
`libtermexec` unchanged and add only its own application/runtime layer.

## Recommended Gradle strategy

To avoid copying terminal-engine code, the Linux edition should be added as a
second application module in the **same** Gradle build, for example:

```text
settings.gradle
  include ':term'          # com.thothterm  (terminal emulator)
  include ':term-linux'    # com.thothterm.linux (future)
  include ':emulatorview'
  include ':libtermexec'
```

`term-linux` would `implementation project(':emulatorview')` and
`implementation project(':libtermexec')`, exactly like `term`, and declare
`applicationId "com.thothterm.linux"`. Shared UI/resources can move into a
common library module later if duplication appears; do not pre-emptively
abstract today.

An alternative single-module product-flavor approach is **not** recommended:
flavors cannot change `applicationId` in a way that yields two independently
installable, differently-branded apps without also duplicating launcher
resources, and the Linux edition will carry a large rootfs/runtime payload that
should not be built into the terminal APK.

## Rules

- Do not rename or move `app_HOME`, HOME, or the PTY/session architecture for
  the Linux edition; it should layer on top.
- Do not begin the Linux edition until the shared Diagnostics/Logging phase and
  an explicit design exist.
- `com.thothterm.linux` is reserved conceptually only; nothing is published
  under it yet.
