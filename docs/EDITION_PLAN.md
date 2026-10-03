# ThothTerm Garden edition plan

ThothTerm Garden is the product family. Each edition is an independently
installable Android application that shares one terminal engine.

## Editions

| | ThothTerm Terminal Emulator | ThothTerm Ubuntu | Future distro editions |
| --- | --- | --- | --- |
| Application ID | `com.thothterm` | `com.thothterm.ubuntu` | `com.thothterm.<distro>` |
| Launcher label | ThothTerm | ThothTerm Ubuntu | ThothTerm <Distro> |
| Purpose | Standalone Android terminal emulator | Ubuntu 26.04 LTS workspace for Android | e.g. Kali/Arch/AlmaLinux workspaces |
| Runtime | Android shell / PTY only | Embedded Ubuntu ARM64 + packaged PRoot | Embedded distro ARM64 + packaged PRoot |
| Modules | `term/` | `term-ubuntu/` | future `term-<distro>/` |
| Status | Released 1.0.0 golden baseline | First milestone (0.1.0 dev) | Not implemented |

Reserved future application IDs (not created yet):

- `com.thothterm.kali`
- `com.thothterm.arch`
- `com.thothterm.almalinux`

The earlier plan reserved a single `com.thothterm.linux` package. That is
superseded: the Garden model is **distro-specific application IDs**, so each
edition can carry its own rootfs and install side by side.

Both application IDs are distinct so editions can be installed side by side
without clobbering each other's data or signature.

## Shared engine, no duplicated engine code

The terminal engine lives in the library modules the applications depend on:

- `emulatorview/` — terminal parser, screen/transcript, rendering, key handling.
- `libtermexec/` — PTY process and terminal-control JNI library.

Each application module (`term`, `term-ubuntu`, future editions) reuses
`emulatorview` and `libtermexec` unchanged and adds only its own
application/runtime layer. The raw PTY/session/parser/native boundary is never
duplicated or replaced.

For the first Ubuntu milestone, `term-ubuntu` **copies** the application layer
from `term` (activities, service, session, settings, theme, diagnostics UI) and
adapts it. This is deliberate and temporary: it preserves the stable terminal
edition untouched and avoids a premature shared-UI refactor. A later milestone
may extract the common application layer into a shared library module once two
editions demonstrably need it.

## Gradle strategy

The editions are separate application modules in the same Gradle build:

```text
settings.gradle
  include ':term'          # com.thothterm            (terminal emulator)
  include ':term-ubuntu'   # com.thothterm.ubuntu     (Ubuntu)
  include ':emulatorview'
  include ':libtermexec'
```

Each application module builds its embedded runtime as an APK asset/native
payload; the large rootfs is fetched, verified, and staged at build time rather
than committed to Git. See `docs/UBUNTU_RUNTIME.md` for the Ubuntu pipeline.

## Rules

- Do not rename or move `app_HOME`, HOME, or the PTY/session architecture; the
  Linux editions layer underneath the existing session model.
- Do not mutate `term` into a Linux edition.
- Do not duplicate `emulatorview` or `libtermexec`.
- Do not begin a new distro edition without an explicit milestone.
- Keep application IDs distinct per edition.
