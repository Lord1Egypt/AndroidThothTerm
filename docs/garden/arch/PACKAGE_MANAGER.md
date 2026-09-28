# ThothTerm Rolling — package manager

pacman, its keyrings and sudo are Arch Linux ARM's own, unmodified. This page
records how pacman is set up in a PRoot guest on Android, what the device gate
verifies, how an interrupted transaction is recovered, and the PRoot
limitations users can notice.

> **Status: the device-gate result below is PENDING.** The last run (candidate
> rootfs `ffb1fe11e9bc`) was 55 PASS / 14 FAIL, every FAIL a harness bug or the
> interrupt design, fixed in 978389e and not yet re-run. Replace the pending
> section with the output of `tests/garden-arch/device/gate.sh` on the final
> rootfs before the release (`docs/garden/arch/RELEASE_CHECKLIST.md`).

## First use

The rootfs ships no sync databases, like Arch's own container images, so the
first command is:

    sudo pacman -Syu

Until then pacman prints `database file for 'core' does not exist (use '-Sy'
to download)`. Never `pacman -Sy <package>` on its own: refreshing the
databases without upgrading is a partial upgrade, which Arch does not support.
The app itself never runs `-Sy`; if it has to reinstall sudo it uses
`pacman -S --needed sudo` against the databases the guest already has.

## The keyring

Each installation creates its own pacman keyring at first run, as Arch Linux
ARM documents it: `pacman-key --init` (a fresh local master key) and
`pacman-key --populate archlinuxarm`. Before setup is marked complete the app
proves, offline, that

1. the Arch Linux ARM Build System key
   (`68B3537F39A313B3E574D06777193F152BDBE6A6`) is fully valid in it, and
2. a genuine signed repository package kept in the image for this purpose
   (`/usr/share/thothterm/signature-check/`) verifies with `VALIDSIG` for that
   key and full trust.

Any other result fails setup, and nothing is marked installed. A keyring in
the image itself is refused: it would carry a private master key every
installation shared. If a user deletes `/etc/pacman.d/gnupg`, the app
recreates it the same way when the next terminal window opens.

`SigLevel` is Arch Linux ARM's default; nothing weakens signature checking.

## One pacman.conf setting: no Landlock on Android

pacman 7 confines its downloader three ways: Landlock, a seccomp filter and
the unprivileged `DownloadUser = alpm`. Android kernels provide no Landlock;
on the SM-A165F (Android 16, kernel 6.12) every download failed with
"restricting filesystem access failed because Landlock is not supported by the
kernel!". Each option was tested on the phone: only `DisableSandboxFilesystem`
is needed. The seccomp filter and `DownloadUser` work and stay on. The image
sets exactly that one line (`ArchEditionTest` checks the builder).

## Golden gate

`tests/garden-arch/device/gate.sh ROOTFS [STALE]` runs as the app (`run-as`,
in the app's SELinux domain, because gpg-agent needs a unix socket the adb
shell user may not create) with the installed app's PRoot and the app's PRoot
argv, on disposable copies under `files/ga` — never the app's own
environment. A terminal window must be open while it runs: Android 16 blocks
network for an app with no foreground process.

It checks:

| Case | Script |
|---|---|
| first-run keyring provisioning as the app does it, no warnings | `provision.sh` |
| A–K: pacman 7; `--populate` without trust errors; a current repository package fetched straight from the mirror and signature-verified with the new keyring; `-Syu`; a second `-Syu` has nothing to do; install, remove, reinstall and remove `tree`; `-Syu` again; `-Dk` clean, `-Qk` with no missing files, no `db.lck` | `zero.sh` |
| guest: `os-release`, PRoot's `uname -r`, `sudo -n id -un` as thoth is `root`, thoth's ids, home and shell, DNS, HTTPS, gzip/xz/zstd/bzip2/tar round trips, hard and symbolic links, `/proc/self/exe` through a guest-made hard link (patch 0003), UTF-8, `ldd` of the core tools | `zero.sh` |
| L: after a full PRoot restart, install and use `jq`, remove it, database consistent, sudo | `restart.sh` |
| interrupted transactions, three cases (below) | `interrupt.sh` |
| the update story: the official upstream image's own stale userland, brought current by one `pacman -Syu` | `stale.sh` |

### Result on the final rootfs — PENDING

To be filled from the gate's output on the SM-A165F with the final rootfs:
the rootfs SHA-256, the date, the PASS/FAIL count (the release requires
0 FAIL) and any INFO lines.

## Interrupted transactions

pacman marks a running transaction with `/var/lib/pacman/db.lck`. When
Android kills the app mid-transaction, PRoot takes pacman down with it
(`PTRACE_O_EXITKILL`) and the lock stays. The app removes it only when it is
proven stale: when a terminal window opens and no PRoot process of the app is
running at all, since every guest process — pacman included — runs under one.
Otherwise the lock is left alone. The transaction itself is completed by the
user:

1. **An ordinary package** was being replaced: `sudo pacman -Syu` (or
   reinstalling the package) completes it at the same version.
2. **A new package** was being installed, so its files can be on disk with no
   database entry, and a plain retry reports `exists in filesystem`. Retry
   with `--overwrite` limited to that package's own paths, for example
   `sudo pacman -S --overwrite '/usr/share/vim/*' vim-runtime`.
3. **A library pacman itself needs** (for example `icu`, through libarchive)
   was being replaced, and pacman cannot start. GNU tar does not link it:
   restore the files from the verified package pacman already downloaded,
   then let pacman reinstall it properly:

       sudo tar -xf /var/cache/pacman/pkg/icu-<version>-*.pkg.tar.xz -C / \
           --exclude=.PKGINFO --exclude=.BUILDINFO --exclude=.MTREE --exclude=.INSTALL
       sudo pacman -S icu && sudo pacman -Syu

After any of them, `pacman -Dk` and `pacman -Qk` report no problems.

## PRoot limitations

- **No systemd as PID 1.** `systemctl` does not work. A package hook that asks
  systemd to restart a service (openssh's, for example) prints "System has not
  been booted with systemd" and "command failed to execute correctly"; the
  package itself is installed correctly.
- **Ownership is not stored.** PRoot's fake root (`--root-id`) makes `chown`
  succeed without recording anything, since an app cannot change file
  ownership on Android. Every file appears to belong to whoever looks. Package
  installation, sudo and ordinary work are unaffected; software that insists
  on another user's ownership (OpenSSH's `StrictModes`) can refuse to work.
  The same holds in every Garden edition.
- **`uname -r`** reports PRoot's emulated release (`6.1.0-thothterm`), not an
  Arch Linux ARM kernel; no kernel, firmware or initramfs is installed.
- **Size.** `base` is about 674 MB installed; each `pacman -Syu` adds package
  cache under `/var/cache/pacman/pkg`, which `sudo pacman -Sc` clears.
