# ThothTerm Trixie — rootfs provenance

The Debian userland ThothTerm Trixie installs is built by
`garden-debian/rootfs/build-rootfs.sh` from Debian's own archive. Debian
publishes no minimal root-filesystem tarball, so the project assembles one
with **debuerreotype**, the Debian tool behind the official Debian container
images. It is chosen over mmdebstrap because reproducibility is its purpose:
it clamps every timestamp to the snapshot's Release date and writes the tar in
a canonical order, so no post-processing of ours is needed for identical
output.

Nothing in the archive is built or modified by ThothTerm beyond what is listed
under "Changes from a plain debootstrap". Every package is an unmodified
Debian binary package, verified against `debian-archive-keyring`
(`debuerreotype-init --check-gpg`).

## The pinned archive

| | |
|---|---|
| File | `thothterm-debian-trixie-arm64-rootfs-f6520ff1c6ee.tar.gz` |
| SHA-256 | `f6520ff1c6eeb28ead2d175e6733da4c970459a291b1bd60f992f1655c5d0677` |
| Size | 62445701 bytes (uncompressed tar 176332800 bytes) |
| Published at | GitHub release `debian-rootfs-trixie-arm64-f6520ff1c6ee`, with its package list and build manifest |
| Pinned in | `garden-debian/src/main/assets/garden/distro.properties` |

The name carries the first 12 hex digits of the SHA-256, so a name can never
stand for two different archives. The release tag does not match
`^trixie-v[0-9.]+$`, so F-Droid's update check never mistakes it for an app
release.

## How it was built

```
architecture: arm64
suite: trixie (trixie, trixie-updates, trixie-security; component main)
debootstrap_variant: minbase
snapshot_requested: 20260927T000000Z
archive_snapshot: snapshot.debian.org/archive/debian/20260926T200824Z
security_snapshot: snapshot.debian.org/archive/debian-security/20260926T200824Z
epoch_timestamp: 2026-09-27T00:00:00Z
builder_image: debian:trixie-slim@sha256:a99cfc517144bc59b1978475ec53b46ecabec7e43635402ee5b77cc54cd1b20a
builder_tool: apt 3.0.3
builder_tool: debootstrap 1.0.141
builder_tool: debuerreotype 0.15-1.1
keyring: debian-archive-keyring (debuerreotype-init --check-gpg)
extra_packages: sudo ca-certificates curl netbase procps 
installed_packages: 109
build_script_sha256: 62ed330ec4aed93c9863f529cc2dc6ac032fe2ce94a8ac8090704468ec68cac3
build_script_revision: 793ce99e72b30854a8bf457e4d649fbe6b3529b8
dpkg_audit: clean
```

Debian 13 "trixie" was verified as Debian Stable on 2026-09-27: the
`stable` Release file reads `Version: 13.7`, `Codename: trixie`, dated
2026-09-12. The newest trixie-security Release inside the snapshot is dated
2026-09-26 17:43:41 UTC.

Package set: debootstrap's **minbase** variant (apt, dpkg, bash, coreutils,
util-linux including `su`, passwd, glibc with its built-in `C.UTF-8`
locale) plus `sudo ca-certificates curl netbase procps`
(`garden-debian/rootfs/packages.txt`). No compilers, desktop, ssh or web
servers. 109 packages in total; the exact list and versions are the
`.packages.tsv` published beside the archive.

## Reproducibility

The script was run twice on 2026-09-27, from a clean output directory each
time. Both runs produced the same archive, byte for byte
(`cmp` equal, SHA-256 above), and the same package list and manifest.
Anyone can re-derive the SHA-256 with:

    garden-debian/rootfs/build-rootfs.sh     # docker + aarch64 binfmt (qemu-user)

## Package sources inside the guest

The archive ships the live Debian archives, pinned to the codename, never the
snapshot and never `stable`:

```
Types: deb
# http://snapshot.debian.org/archive/debian/20260926T200824Z
URIs: http://deb.debian.org/debian
Suites: trixie trixie-updates
Components: main
Signed-By: /usr/share/keyrings/debian-archive-keyring.gpg

Types: deb
# http://snapshot.debian.org/archive/debian-security/20260926T200824Z
URIs: http://deb.debian.org/debian-security
Suites: trixie-security
Components: main
Signed-By: /usr/share/keyrings/debian-archive-keyring.gpg
```

`http://` is Debian's own default; apt verifies the signed Release files
against `debian-archive-keyring`. The comment lines record the snapshot the
archive was built from.

## Changes from a plain debootstrap

- `/etc/hostname` is `thothterm` (debuerreotype names the host after itself).
- `/etc/resolv.conf` is a one-line comment; the app bind-mounts the resolver
  Android is using over it at runtime (debuerreotype points it at a public DNS
  service).
- `/var/log` holds no files (they recorded the build); `/var/lib/apt/lists`
  and the apt caches are empty.

The app adds the rest at first run, idempotently: the `thoth` account
(1000:1000, `/home/thoth`, bash) in the `sudo` group, a `NOPASSWD` sudoers
entry, the Android supplementary groups, `/etc/hosts` entries, the debconf
frontend, the welcome banner and the edition file it reads.

## Hygiene scan of the archive

```
machine-id: [absent]
dbus machine-id: absent
hostname: [thothterm]
resolv.conf: [# ThothTerm bind-mounts the resolver Android is using over this file. ]
sources.list (one-line): absent
shadow password fields:  18 *;
ssh host keys: 0
private keys: 0
shell histories: 0
home entries: 0
root entries: .bashrc .profile 
apt lists: 0
apt archives (.deb): 0
apt pkgcache: 0
non-empty logs: 
tmp entries: 0
setuid files: /usr/bin/mount /usr/bin/passwd /usr/bin/umount /usr/bin/gpasswd /usr/bin/newgrp /usr/bin/chfn /usr/bin/chsh /usr/bin/sudo /usr/bin/su 
```

`shadow`: every account is locked (`*`). The setuid bits are Debian's; the
app's extractor drops setuid on extraction and restores it only on
`/usr/bin/sudo`, which PRoot's fake root needs.

The archive holds one hard link (`usr/bin/perl5.40.1` → `usr/bin/perl`).
Android does not let an app create hard links, so the extractor writes a copy;
links the guest creates later are handled by PRoot's link2symlink, and patch
0003 keeps `/proc/self/exe` correct for them
(`docs/garden/HARDLINK_EXECUTABLES.md`).

## Corresponding source

Every binary package in the archive is Debian's. The source package for each
exact version is kept by snapshot.debian.org at the same moment
(`https://snapshot.debian.org/archive/debian/20260926T200824Z/` and
`.../debian-security/20260926T200824Z/`), which never removes files, and the
`.packages.tsv` names every package and version. This is how the official
Debian container images meet the same obligation. This is a practical
description, not legal advice.
