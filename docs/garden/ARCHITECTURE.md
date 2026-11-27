# ThothTerm Garden — architecture

Garden is the family of ThothTerm distribution editions. Each edition is a
separate app that runs a real Linux userland under PRoot inside its own
app-private storage. ThothTerm Trixie (`com.thothterm.debian`) is the first
edition built on it.

```
emulatorview/   libtermexec/        terminal engine, shared with every app
        │             │
        └──────┬──────┘
         garden-common/             Garden layer (Android library)
               │                      terminal app layer, PRoot runtime,
               │                      first-run setup, LAN Mode
         garden-debian/             ThothTerm Trixie (application)
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
| PRoot patches 0001–0004: string.h, loader info without host tools, `/proc/self/exe` names the hard link, `--hangup-on-exit` + `PTRACE_O_EXITKILL` (0005, the kernel working directory in step with the guest's, came with 0.2.0; 0006 translates `fchmodat2` with the kernel's semantics) | `garden-common/patches/`, byte-identical to `term-ubuntu/patches/` (`ProotSourceBuildTest`) |
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
- **Colours per edition.** Ubuntu's banner, prompt, terminal scheme, app
  chrome and web page hardcoded its amber/teal/navy and Ubuntu orange. They
  now come from the edition: `assets/garden/palette.properties` (banner,
  prompt, terminal scheme, LAN page via `/edition.css`) and
  `res/values/colors.xml` (Android chrome). garden-common's defaults are
  neutral grey. See `docs/branding/debian/PALETTE.md`.
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
  thoth`, plus the `/proc` stand-ins and
  `--bind=<files>/linux/audio:/run/thothterm/audio`, the directory of the
  audio pipe (below). One-shot provisioning uses `--kill-on-exit` instead.
- `prepareSession()`, on the UI thread for every new window, refreshes the
  managed files, the `/proc` stand-ins and the audio pipe
  (`<files>/linux/audio/pulse.fifo`, `mkfifo` by the app), and runs no guest
  command.
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

## Keep screen awake

**Screen awake is not a CPU wake lock.** It is `FLAG_KEEP_SCREEN_ON` on the
terminal's window (`com.thothterm.utils.ScreenAwake`), for two reasons:

- **manual** — the menu's *Keep screen awake*. It lives as long as the
  activity: saved across recreation; a new launch, including the one after
  Exit, starts with it off.
- **charging** — *Keep screen awake while charging* (Settings, on by default)
  while the device is on AC, USB, wireless or dock power.

The flag is `manual || (setting && pluggedIn)`, computed from those three
values and applied to the window whenever one changes; the window is never
the record. Power is followed by a receiver for `ACTION_BATTERY_CHANGED`,
registered at run time in `onStart` (never exported, never in the manifest)
and dropped in `onStop`, when the charging reason is dropped too; `onStart`
reads the current state from the sticky broadcast. Android honours the flag
only while the window is visible, so a terminal behind another app never
holds the display, and coming back while still charging keeps it on again.

- The menu never contradicts the flag: *Keep screen awake* when nothing keeps
  the screen on, *Allow screen to sleep* while the manual choice does, and
  *Screen awake while charging* — which opens the setting — while only
  charging does.
- No permission, no `PowerManager` wake lock, no battery-optimization
  exemption, and the system screen timeout is never touched. Up to
  ubuntu-v0.2.0 and trixie-v0.1.0 the action took a `PARTIAL_WAKE_LOCK` and
  offered battery settings; that kept the CPU running while the display still
  slept at the normal timeout.
- Keep Wi-Fi on is a separate `WifiLock` and keeps the `WAKE_LOCK` permission it
  needs.

Every edition inherits this from garden-common's `Term` and runs
`garden-common/src/editionAndroidTest/java/com/thothterm/KeepScreenAwakeTest.java`
on the device, which checks the real window flag and WindowManager's display
hold in `dumpsys power`, simulating power with `dumpsys battery`.
`GardenScreenAwakeTest` fails the build of any edition that brings its own
implementation, a wake lock or a battery flow, or leaves the device test out.

## Linux audio

The guest cannot reach Android's audio devices. *Linux audio* (menu,
Settings; off by default) plays what the guest's own PulseAudio writes:

- **Guest side**: the edition ships `garden/audio/thothterm-audio-setup`
  (optional; garden-common installs it as `/usr/local/bin/thothterm-audio-setup`
  and knows nothing else about it). Run once as the user, it installs
  `pulseaudio` with the edition's package manager, writes
  `~/.config/pulse/{default.pa,client.conf,daemon.conf}` -- a self-contained
  `default.pa` without udev or card probing, `module-pipe-sink` as the default
  sink writing s16le/48 kHz/stereo into `/run/thothterm/audio/pulse.fifo`,
  `module-suspend-on-idle timeout=5`, no shared memory, no idle exit -- and
  starts or restarts the daemon, stopping by name (`pgrep`/`pkill`) any
  daemon of the user its socket no longer reaches. Programs just use
  PulseAudio.
- **App side**: the pipe is the app's. `RootfsManager` creates
  `<files>/linux/audio/pulse.fifo` before every session and the mount point
  `/run/thothterm/audio` in the rootfs; `GardenRuntime` binds the directory.
  `AudioBridge` (owned by `TermService`, following the `audio_bridge`
  preference) opens the FIFO read-write -- so a PulseAudio restart never ends
  the stream -- and polls it with a self-pipe for `stop()`. It opens an
  `AudioTrack` (`USAGE_MEDIA`) on the first bytes and releases it after 1 s
  without any; `TermService` is `specialUse|mediaPlayback` so playback goes
  on in the background. While nothing plays the sink is suspended and the
  bridge waits in `poll`: zero bytes, zero mixing, no audio hardware powered.
  The notification shows "Linux audio: playing" or "idle".
- `/etc/machine-id` is managed: written once, a random systemd-shaped id,
  when the rootfs has none or an invalid one. PulseAudio names its per-user
  runtime directory after it and falls back to the host name, which would
  orphan a running daemon whenever the host name changed.

## Keep CPU awake

**Keep CPU awake is not Keep screen awake.** It is a `PARTIAL_WAKE_LOCK`
(`com.thothterm.utils.CpuWakeLock`, tag `ThothTerm:KeepAwake`) owned by
`TermService`, so guest processes keep running while the display sleeps:
without it the device suspends soon after the screen turns off and PRoot's
tracees only run for a moment every 30-60 s, which ends Linux audio and stalls
long jobs. The mode `keep_cpu_awake_mode` (menu dialog, Settings list,
notification button) is the record:

- **off** -- never held.
- **while sound plays** (default) -- held while the audio bridge is active
  and for 10 s after it turns idle; nothing held otherwise.
- **always** -- held as long as the service runs, for long jobs.

The service re-evaluates the lock when the mode changes, whenever the bridge
starts or stops playing and once more when the grace runs out; it releases
it on destroy, timeout and Exit and never leaves it behind. The notification
button goes between *always* and the default and the notification says why
the CPU is awake. No battery-optimization exemption is asked for, and nothing
here touches the display: `ScreenAwakeTest` allows the wake-lock tokens in
`CpuWakeLock.java` alone and checks the two features share no code.

## Uploads

*Upload files* and *Upload folder* — on the phone through the Storage Access
Framework, and in LAN Mode from a paired browser — copy into the **current
working directory of one terminal session**, read from `/proc` (the PTY's
foreground process group's working directory) and never from the browser.
PRoot patch 0005 keeps each guest process's kernel working directory in step
with its guest one, which makes that possible under PRoot. Uploads are staged,
never overwrite (keep-both names), never follow existing links and never leave
partial files. All of it — `com.thothterm.upload`, `LanUploads`, the
`/api/upload/*` routes and the page — is garden-common's; an edition brings
none. See `docs/garden/UPLOADS.md`.

## minSdk 26, arm64 only

PRoot and its runtime are compiled for `aarch64-linux-android26`, and
libandroid-shmem uses `ASharedMemory` (API 26).
