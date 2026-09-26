# ThothTerm Garden — architecture

Garden is the family of ThothTerm distribution editions (Debian first; Kali,
Alpine, Arch, AlmaLinux, CentOS Stream and BlackArch later). Each edition is a
separate app that runs a real Linux userland under PRoot inside its own
app-private storage.

```
emulatorview/   libtermexec/          shared terminal engine — consumed unchanged
        │             │                (terminal-v1.3.0)
        └──────┬──────┘
         garden-common/                Garden layer (Android library)
               │                         terminal app layer, distro runtime,
               │                         first-run setup, LAN mode
         garden-debian/                ThothTerm Debian (application)
                                         distro config, branding, rootfs, PRoot build
```

## Why one Garden copy of the terminal app layer

The terminal *engine* (`emulatorview`, `libtermexec`) is a pair of libraries and
Garden uses them as they are. The terminal *app layer* — `Term`, `TermService`,
sessions, windows, settings, Extra Keys, zoom, diagnostics — lives in `term/`,
which is an application module. An app cannot depend on another app, and
turning `term/` into a library or adding extension hooks to it would be an
invasive change to the Regular Terminal Golden Baseline.

So `garden-common` holds **one** copy of that app layer, taken verbatim from
`terminal-v1.3.0` in its first commit so every Garden change is a reviewable
diff against the Golden code, then trimmed and extended. Every Garden edition
shares it; no edition carries its own terminal implementation. The Ubuntu
edition (`term-ubuntu`, released separately) is not migrated onto Garden.

Removed from the Garden copy, because they are integration surfaces a distro
edition does not need and each is an exported entry point:
`RemoteInterface`/`TermHere`, `RunScript` and its `RUN_SCRIPT` permission,
`RunShortcut`/`AddShortcut`/`FileSelection`, the `ICommand` command protocol and
its native helpers, the exported `ITerminal` binder, and external-storage
permission flows. Garden's remote-access feature is LAN mode instead.

## Distro data, not distro code

An edition supplies `assets/garden/distro.properties` (identity, pinned rootfs
asset, digest and sizes, guest policy) plus its branding resources and default
theme. `garden-common` reads it at runtime; it contains no distro name.

## Runtime (from the Ubuntu edition's proven design)

- PRoot, its loader, talloc and libandroid-shmem are **built from pinned source
  in this repository** (`third_party/`, same commits as the Ubuntu edition) by
  `garden-common/tools/build-proot.sh`, per edition because the runtime path is
  compiled in. Output is checked for 16 KB LOAD alignment.
- Launch: `proot --rootfs=<canonical path> --root-id --link2symlink
  --kill-on-exit --bind /dev /proc /sys --bind <private resolv.conf>:/etc/resolv.conf`,
  then the guest's own `su -m -s /bin/bash thoth`. The rootfs path is
  canonicalised because `--link2symlink` stores absolute targets.
- First run extracts into `rootfs.staging`, verifies the archive's SHA-256 as it
  streams, applies guest configuration, renames to `rootfs` and only then writes
  the state file, so a partial extraction is never mistaken for an install. The
  extractor rejects traversal and unsafe links and reproduces modes (including
  setuid, which PRoot's fake root relies on) and hard links.
- Guest configuration is idempotent text transforms: `thoth` (1000:1000,
  `/home/thoth`, bash) in the `sudo` group, a `NOPASSWD` sudoers entry, hosts,
  names for the Android supplementary groups, a noninteractive-safe debconf
  frontend.
- Root inside the guest is PRoot's user-space emulation. It grants no Android
  privilege; there is no VM or kernel isolation beyond the app sandbox.

## minSdk 26

Not copied: PRoot and its runtime are compiled for API 26
(`aarch64-linux-android26`), and libandroid-shmem uses `ASharedMemory` (API 26).
The runtime also relies on `StorageManager.getAllocatableBytes` and fraction
insets for the splash (both API 26). arm64-v8a only.

## F-Droid

v0.1.0 ships **only a direct (full) build**. The Ubuntu edition's F-Droid
flavour downloads the distribution's official base tarball after consent;
Debian has no such official artifact, so an F-Droid flavour would have to
download a tarball built by this project — not something F-Droid can verify — or
bootstrap Debian on the device from `deb.debian.org` with signature checks, which
is future work. No `fdroid` flavour or artifact exists rather than a nominal one.

## LAN mode (Garden only)

A browser on the same LAN gets a terminal attached to a **real PTY running the
distro shell under PRoot** — not a JavaScript emulation.

```
browser ──HTTP──> packaged web UI (xterm.js, no CDN)
        ──WebSocket (authenticated)──> LAN server ──> dedicated PTY session ──> bash (PRoot)
```

- Off by default; never restored after process death; no listener exists while
  off. Not present in the Regular Terminal (`term/`), which a release gate scans.
- Bound to the Wi-Fi/Ethernet IPv4 address only (not `0.0.0.0`); refuses to
  start without such an interface; additionally rejects peers outside the
  interface's private subnet.
- Pairing: a short-lived one-time PIN shown on the phone; a correct PIN yields a
  256-bit random session token (never the PIN) in an `HttpOnly`,
  `SameSite=Strict` cookie. Failed attempts are limited; tokens are compared in
  constant time; the WebSocket also checks `Origin`. Turning LAN mode off
  invalidates every token; turning it on generates new secrets.
- Plain HTTP on the LAN: traffic is authenticated but **not encrypted**. This is
  documented, not hidden. Self-signed TLS would give every user a certificate
  warning and train them to click through it, so it is not used for v1.
- Each browser gets its own PTY session; disconnecting keeps it for a short
  grace period for reconnection, then hangs up its whole process session. Phone
  sessions are never touched.
