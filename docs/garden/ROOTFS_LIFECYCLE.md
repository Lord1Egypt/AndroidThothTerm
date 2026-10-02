# Rootfs lifecycle, extraction and setup stages

Applies to every Garden edition (Arch, Trixie) and to ThothTerm Ubuntu. The
code is shared: `garden-common/src/shared/java/com/thothterm/linux/` is
compiled into garden-common and into term-ubuntu (see their `sourceSets`).

## The rule

**Only a genuinely never-installed environment is set up automatically.**
An installed environment that becomes damaged is classified and, at most,
repaired without touching guest data. Replacing the system takes an explicit,
dialog-confirmed reset, and even that moves `/home` into the new system with
`rename(2)`; nothing in the app deletes an installed rootfs.

## Conditions (`RootfsLifecycle.Condition`)

| Condition | Observed | What the app does |
|---|---|---|
| `NOT_INSTALLED` | no `rootfs/`, no state record, no reset in progress | first install (F-Droid flavour: after consent) |
| `INSTALLED_HEALTHY` | rootfs, state record, guest entry points (`/bin/bash`, `/usr/bin/su`, resolved inside the guest), app runtime files | opens the terminal |
| `APP_RUNTIME_DAMAGED` | as healthy, but app-owned `runtime/lib` files missing | re-stages them from the APK; the guest is not touched |
| `REPAIR_REQUIRED` | rootfs with its entry points, but no readable state record (crash right after a first install, lost state file) | finishes in place: managed configuration, keyring (pacman), state record. No extraction |
| `INSTALLED_DAMAGED` | rootfs whose entry points are missing (interrupted package transaction, removed shell), or a state record without a rootfs | nothing; the setup screen explains it and offers a reinstall that keeps `/home`, behind a confirmation dialog |
| `EXPLICIT_RESET_REQUESTED` | the user confirmed a reset (`reset.requested` marker), or one was interrupted (`rootfs.previous` exists) | runs the reset, or after a restart asks to continue or cancel it |

The rootfs pin (`imageId`/`sha256` in the app) is not an input at all: a newer
app with a newer pin sees an existing installation as installed. (ThothTerm
Ubuntu's readiness still compared the pin until this change; any rootfs pin
bump would have re-extracted over every user's home.)

An I/O error while looking is never read as "not installed".

## Reset that keeps /home (`RootfsLifecycle.replaceSystemKeepingHome`)

The new system is extracted, verified and configured in `rootfs.staging`
first and marked complete. Then, each step one `rename(2)`:

1. `rootfs` -> `rootfs.previous`
2. the new image's skeleton `home` is removed; `rootfs.previous/home` ->
   `rootfs.staging/home`
3. `rootfs.staging` -> `rootfs`
4. the old system, which no longer contains `/home`, is deleted

`RootfsLifecycle.recover` runs before anything else on the next start and
completes or rolls back a crash at any step; `/home` is never copied and never
deleted. `RootfsLifecycleTest.crashAtAnyResetStepNeverLosesHome` crashes the
reset after every single filesystem operation and checks that exactly one
complete system holds the unchanged `/home`. Everything outside `/home`
(installed packages, `/etc`, `/root`, `/opt`, `/usr/local`) is replaced, which
the confirmation dialog says. A reset refuses to run while any PRoot process
of the app is alive.

## Extraction (`TarballExtractor`, `FileOps`, `RootfsArchive`)

- Names and pax paths are strict UTF-8; absolute names, `..` components (also
  spelled with backslashes) and NUL are rejected; a literal backslash inside a
  name is kept (systemd's `\x2d` unit names).
- No entry is ever extracted through a symlink: every intermediate component
  must be a real directory (libarchive's secure-symlinks model).
- The final component is never followed: an existing non-directory is
  unlinked and the file is created with `O_CREAT|O_EXCL|O_NOFOLLOW`; its mode
  is applied with `fchmod` on that descriptor. A dangling symlink planted by an
  earlier entry cannot redirect a later file, hardlink or directory.
- Hardlink targets are walked the same way and must be a regular file or
  symlink in the root. When Android SELinux refuses `link(2)`, the target is
  copied through `O_NOFOLLOW` (a symlink by its target text).
- setuid and setgid are never applied; a directory keeps its sticky bit.
- Symlink entries are created verbatim: their targets are guest paths that
  PRoot resolves inside the guest; the extractor never follows any of them.
- `RootfsArchive` is the one extract-and-verify path: the SHA-256 covers every
  byte of the file (the stream is drained after the tar end marker), and any
  rejected entry fails the install. Staging is deleted on any failure.

The app's other guest writes (`ManagedFiles`) resolve symlinks inside the
guest (`GuestPaths`), so a user's `~/.bashrc -> dotfiles/bashrc` keeps working
and an absolute guest link can never send a write to an Android path. Files are
decoded whole as strict UTF-8 (a file that is not UTF-8 is left exactly as it
is) and replaced atomically.

## Setup stages and timings

The percentage on the setup screen is the archive only: download progress is
shown separately ("Downloading ... N%"), and 100% means the archive is fully
extracted and its checksum verified. Then named stages follow:

1. "Finalizing <distro> environment..." -- managed configuration, promotion,
   the pacman keyring (Arch), the state record
2. "Preparing administrator tools..." -- only local, bounded work: the setuid
   bit on an installed sudo, finishing an interrupted dpkg configuration, or
   (Ubuntu full flavour) installing the bundled sudo packages

Anything that needs the network -- installing sudo from the distribution's
archive (Ubuntu F-Droid flavour, or a guest where it was removed), recreating
a missing pacman keyring -- runs on one background thread after the terminal
has opened and never blocks it; `prepareSession()`, which runs on the UI thread
for every new window, no longer runs guest provisioning at all. Until the
background work finishes, `sudo` may be unavailable; the log says when it is
done.

`SetupTimeline` logs (category ROOTFS) a line per stage with milliseconds
since setup started: `DOWNLOAD_COMPLETE`, `ARCHIVE_EXTRACTED` (entries,
rejected, hardlink fallbacks, bytes), `ARCHIVE_VERIFIED`,
`SETUP_ROOTFS_COMPLETE`, `ROOTFS_PROMOTED`, `KEYRING_PROVISIONED`,
`STATE_WRITTEN`, `ADMIN_TOOLS_READY`, `TERMINAL_HANDOFF`, and a summary line.
The background administrator tools log their own duration and result.

## Tests

- `garden-common/src/sharedTest`: extractor adversarial cases
  (`ExtractorSecurityCases`, every case checks an OUTSIDE sentinel tree is
  untouched), the `FileOps` no-follow contract, lifecycle transitions and the
  crash matrix, guest path resolution, managed files, archive digest.
- `LifecycleSourceGuardTest`: neither `RootfsManager` deletes or renames over
  the rootfs itself, both promote only through `RootfsLifecycle`, and
  `prepareSession()` runs no guest provisioning.
- `tests/garden-common/extractor/host-gate.sh ARCHIVE SHA256`: the shared
  policy on a real archive against an independent Python manifest (JVM
  `FileOps`; not proof of `AndroidFileOps`).
- `tests/garden-common/extractor/device-gate.sh MODULE ARCHIVE SHA256`: the
  same gate on a device with the APK's `AndroidFileOps`
  (`ExtractorDeviceGateTest`). This is the extractor proof for a release; a
  GNU/toybox tar extraction (the package gates) is not. It builds the module's
  debug APK under an isolated application id
  (`-PthothtermQaApplicationIdSuffix=.qa.extractorgate`), reads the package
  back out of both APK files with aapt2 and aborts unless it is exactly that
  id (never `com.thothterm`, `.devel`, `.ubuntu`, `.debian`, `.arch`, never
  PocketClaw), installs only with `--no-incremental`, and never uninstalls or
  clears an app. `device-gate-selftest.sh` (run by `DeviceGateIdentityTest`)
  checks all of that without a device.
