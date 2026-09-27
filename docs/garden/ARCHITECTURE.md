# ThothTerm Garden — architecture

Garden is the family of ThothTerm distribution editions. Each edition is a
separate app that runs a real Linux userland under PRoot inside its own
app-private storage. ThothTerm Debian (`com.thothterm.debian`) is the first
edition built on it.

```
emulatorview/   libtermexec/        terminal engine, shared with every app
        │             │
        └──────┬──────┘
         garden-common/             Garden layer (Android library)
               │                      terminal app layer, PRoot runtime,
               │                      first-run setup, LAN Mode
         garden-debian/             ThothTerm Debian (application)
                                      distro.properties, strings, branding,
                                      rootfs builder, PRoot build for its id
```

The Regular Terminal (`term/`, `com.thothterm`) contains none of this: no
PRoot, rootfs, LAN Mode, xterm.js or WebSocket code.

## Where garden-common comes from

`garden-common` is ThothTerm Ubuntu 0.2.0 (`ubuntu-v0.2.0`, 2ae7732) — its
runtime, setup, LAN Mode and terminal app layer — copied verbatim in one commit
(b209582) and then made distro-neutral in reviewable diffs. Every fix that
release carries is therefore inherited, not re-implemented:

| Ubuntu 0.2.0 fix | Where it lives now |
|---|---|
| PRoot patches 0001–0004: string.h, loader info without host tools, `/proc/self/exe` names the hard link, `--hangup-on-exit` + `PTRACE_O_EXITKILL` | `garden-common/patches/`, byte-identical to `term-ubuntu/patches/` (`ProotSourceBuildTest`) |
| Windows hang up like a terminal; `nohup` survives; nothing outlives PRoot | `GardenRuntime` argv, `ShellTermSession` (exit on EOF), `SessionHangup`, `libtermexec` SIGHUP reset |
| Busy-window close, Exit cleanup, notification lifecycle | `Term`, `TermService`, `SessionProcesses` |
| Safe extraction: traversal, symlink and hard-link checks, crash-safe staging, hard-link copy fallback | `TarballExtractor`, `SafeFileTree`, `RootfsManager` |
| Private resolver, Android supplementary groups, `thoth` account | `AndroidNetworkResolver`, `GuestConfig` |
| Interrupted dpkg is finished in place, never downgraded | `RootfsManager.ensureRealSudo`, `GuestConfig.packageState` |
| PTY/JNI, resize, Arabic/BiDi phone rendering | `emulatorview`, `libtermexec` (unchanged) |
| LAN Mode: PIN pairing, 256-bit token, rate limit and lockout, Host/Origin checks, dedicated PTYs, 30 s grace, xterm.js 6.0.0 + fit 0.11.0 + bidi-js 1.0.3 + Cascadia Mono, no CDN | `com.thothterm.lan`, `assets/lan`, `lan-web/` |
| R8 with two JNI keep rules, 16 KB alignment, full/fdroid tracks | `consumer-rules.pro`, `build-proot.sh`, the edition's `build.gradle` |

`term-ubuntu` is not moved onto `garden-common` in this release; it keeps
building exactly what `ubuntu-v0.2.0` shipped. Moving it is a separate change.

## What changed on the way to distro-neutral

Each item below is a change from Ubuntu 0.2.0's behaviour, with the reason.
None is a Debian-specific workaround.

- **Identity is data.** `ImageInfo` became `DistroInfo`, read from the
  edition's `assets/garden/distro.properties`: edition and distro names, the
  rootfs directory, the pinned archive (URL, asset name, SHA-256, sizes), the
  LAN port and the sudo binary. Loading fails closed on a missing digest or
  size, a non-HTTPS URL, a name with a path separator, a control character,
  or a privileged port.
- **App id and version come from the package.** A library's `BuildConfig`
  describes the library, not the app that ships it.
- **The embedded rootfs is detected by its asset**, instead of a
  `BuildConfig.EMBEDDED_ROOTFS` flag the library cannot see.
- **No bundled sudo packages.** Ubuntu Base ships without sudo, so Ubuntu's
  full flavour carries three `.deb`s. A Garden rootfs includes the
  distribution's own sudo; if a user removes it, it is reinstalled from the
  distribution's archive with apt's signature checks, as the F-Droid Ubuntu
  build already did.
- **Only the launcher is exported.** Removed: `RemoteInterface`/`TermHere`,
  `RunScript` and its `RUN_SCRIPT` permission, `RunShortcut`/`AddShortcut`/
  `FileSelection`, the command collector and its trusted-app bindings, and the
  exported `ITerminal` binder. The terminal service is not exported.
  `usesCleartextTraffic` is false; LAN Mode is a server socket, which that
  setting does not govern.
- **The host-shell settings are gone**: home path, shell, initial command,
  `shrc` and "source system shrc". A Garden window always runs the guest's
  bash through PRoot, and `ShellTermSession` no longer falls back to Android's
  `/system/bin/sh` when PRoot is missing — it fails instead.
- **Neutral defaults, edition overrides.** Every string that names a product
  or distribution is a neutral default in `garden-common` and is overridden by
  the edition (`DebianEditionTest` lists them). The translations inherited from
  the terminal no longer carry `application_terminal` or `service_notify_text`,
  so no locale falls back to a neutral name.
- **Branding lives in the edition.** `garden-common` has no launcher icons and
  only a neutral vector placeholder for the in-app mark, which every edition
  replaces under the same name and qualifier.
- **`sharedUserId` is not declared.** Ubuntu keeps it for upgrade
  compatibility; a new package has nothing to be compatible with.
- **The welcome banner reads the guest.** `thothfetch` prints `PRETTY_NAME`
  from `/etc/os-release` and the edition name from `/etc/thothterm/edition`,
  which the app writes. Both are read as data and stripped of control
  characters, so a point-release upgrade shows up without an app update.
- **LAN port per edition**: Debian 7682, so it runs beside Ubuntu's 7681. On a
  collision LAN Mode moves up through ten ports and shows the port it bound.

## Runtime

- PRoot, its loader, talloc and libandroid-shmem are built from the pinned
  sources in `third_party/` by `garden-common/tools/build-proot.sh`, once per
  edition, because the loader path is compiled in
  (`/data/data/<application id>/files/linux/runtime/loader/loader`). The
  output goes to the edition's `build/garden/` and is checked for 16 KB LOAD
  alignment.
- A window runs `proot --rootfs=<canonical path> --root-id --link2symlink
  --hangup-on-exit --bind=/dev --bind=/proc --bind=/sys
  --bind=<private resolv.conf>:/etc/resolv.conf /usr/bin/su -m -s /bin/bash
  thoth`. One-shot provisioning uses `--kill-on-exit` instead.
- First run extracts into `rootfs.staging` while hashing the archive, checks
  the SHA-256, applies the guest configuration, renames the directory to
  `rootfs`, and only then writes the state file.
- Root inside the guest is PRoot's user-space emulation. It grants no Android
  privilege and there is no isolation beyond the app sandbox.

## Two tracks

| | full | fdroid |
|---|---|---|
| Rootfs | embedded (`assets/garden/rootfs/*.tgz`, stored uncompressed) | downloaded on first run after consent |
| Verification | build refuses an archive whose size or SHA-256 differ from `distro.properties` | app refuses a download whose size or SHA-256 differ from `distro.properties` |
| Distribution | GitHub release | F-Droid, built by F-Droid from source |

Both extract the same bytes; everything after extraction is the same code.

## minSdk 26, arm64 only

PRoot and its runtime are compiled for `aarch64-linux-android26`, and
libandroid-shmem uses `ASharedMemory` (API 26).
