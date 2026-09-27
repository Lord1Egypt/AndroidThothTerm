# ThothTerm Debian — package manager

apt and dpkg are Debian's own, unmodified, and so is sudo. This page records
what was verified before the 0.1.0 release and the one PRoot limitation users
can notice.

## Golden gate

`tests/garden-debian/device/gate.sh` runs on a disposable copy of the pinned
rootfs under `/data/local/tmp` on the phone, with the edition's own PRoot build
and the app's PRoot argv. It never touches the app or its data. The archive's
hard link is materialized as a copy, as the app's extractor does.

Result on the SM-A165F (Android 16), rootfs `f6520ff1c6ee`, 2026-09-27:
**26 of 26 PASS**.

| Check | Result |
|---|---|
| apt sources: trixie, trixie-updates, trixie-security, main only | PASS |
| `apt update`; `apt full-upgrade` (nothing newer than the snapshot yet) | PASS |
| `dpkg --audit` clean; `apt --fix-broken install` is a no-op | PASS |
| `sudo -n id -un` as thoth is `root`, no password prompt | PASS |
| coreutils, including `readlink` and making a hard link, as root and as thoth | PASS |
| install and purge `tree` and `jq` | PASS |
| `/proc/self/exe` names a guest-made hard link it ran through (patch 0003) | PASS |
| after a PRoot restart: install and purge `less`, `/proc/self/exe`, sudo, audit | PASS |
| sudo left `unpacked` by an interrupted run: `dpkg --configure -a` finishes it at the same version | PASS |
| apt SIGKILLed mid-install: recovered, `dpkg --audit` clean | PASS |

The same was then checked inside the installed app: `apt-get update`,
`full-upgrade`, install and purge of two packages, `dpkg --audit` clean; and
with sudo left `install ok unpacked` by `dpkg --unpack`, opening a new window
made the app run `dpkg --configure -a`, after which sudo was
`install ok installed 1.9.16p2-3+deb13u2` — the same version, not a
downgrade — and `sudo -n id -un` printed `root`.

## PRoot limitation: ownership is not stored

PRoot's fake root (`--root-id`) makes `chown` succeed without recording
anything: an app cannot change file ownership on Android. Every file therefore
appears to belong to whoever looks — `root` to root, `thoth` to thoth.
`chown 1000:1000 f; stat -c %u:%g f` as root prints `0:0`.

Practical effect: package installation, sudo and ordinary work are unaffected,
because every program sees files it owns. Software that insists on a
particular owner different from the caller (for example OpenSSH's
`StrictModes` checks on another user's files) can refuse to work. This is a
property of running rootless inside an Android app, shared with every PRoot
distribution, and the same in ThothTerm Ubuntu.
