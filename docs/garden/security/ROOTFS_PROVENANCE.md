# ThothTerm Security — base system provenance

Status: **published** (as ThothTerm Rolling's archive). Re-verified 2026-10-04.

ThothTerm Security's base system is not a separate image. It is ThothTerm
Rolling's Arch Linux ARM userland, byte for byte: the same immutable GitHub
release asset, the same pins. That is deliberate. The earlier candidate for this
edition added a third-party keyring package and a repository section to that
userland; the keyring's redistribution rights are not established, so the
addition was dropped (`DESIGN.md`, "Why no keyring is bundled") and what is left
is Rolling's image, already published with its corresponding source.

| | |
|---|---|
| File | `thothterm-arch-aarch64-rootfs-03a4c669ed6f.tar.gz` |
| SHA-256 | `03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1` |
| Compressed size | 199 826 020 bytes |
| Uncompressed size | 674 723 840 bytes |
| Packages | 138 installed (explicit: `base archlinuxarm-keyring sudo ca-certificates curl`), unmodified Arch Linux ARM binary packages |
| State | the latest internally consistent Arch Linux ARM aarch64 package state at 2026-09-30T07:52:09Z (`SOURCE_DATE_EPOCH` 1790754729); every file's timestamp is clamped to it |
| Published at | `https://github.com/Lord1Egypt/AndroidThothTerm/releases/download/arch-rootfs-aarch64-03a4c669ed6f/` (immutable release; the corresponding source of every package is attached to it) |
| Pinned in | `garden-security/src/main/assets/garden/distro.properties` (identical to `garden-arch`'s, checked by `SecurityEditionTest`) |

## What it does not contain

No BlackArch package, key, keyring, repository section, mirror list or
configuration; no pacman keyring (each installation creates its own at first
run); no sync database, package cache, lock or log; no security tool. This is
checked on the real archive in an arm64 container
(`tests/garden-security/host/container-checks.sh`, section D): no file named
after the third-party project anywhere in the tree, repositories exactly
`core extra alarm aur`, only the Arch and Arch Linux ARM keyrings under
`/usr/share/pacman/keyrings`, no relaxed signature level.

## Reproducibility, measured for this edition

Built twice, each in a fresh container, offline, with the pinned builder
(`garden-arch/rootfs/build-rootfs.sh`, SHA-256
`bc67cc4f880ebf9d8c89983f96c135e8bca65122a178915f975b58cdc0fda47e`, repository
commit `d1d8e69`) from the same hash-pinned inputs (145 captured packages,
synced databases and the signature-verified upstream
`ArchLinuxARM-aarch64-latest.tar.gz`, SHA-256 `42a4eeaa…b319`, Build System key
`68B3537F39A313B3E574D06777193F152BDBE6A6`), docker 29.8.1 on an x86-64 host with
qemu-user binfmt for aarch64:

| Build | SHA-256 | Size |
|---|---|---|
| A | `03a4c669…8d1` | 199 826 020 |
| B | `03a4c669…8d1` | 199 826 020 |
| Downloaded from the GitHub release | `03a4c669…8d1` | 199 826 020 |

All three are byte-identical. The inputs that `capture` fetched (as opposed to
`build`, which is offline) are the ones recorded in Rolling's provenance
(`docs/garden/arch/ROOTFS_PROVENANCE.md`); a different capture gives a different
archive by design (the state is "as of the capture"), which is why the pins name
the content (`03a4c669ed6f`).

The build script's own manifest records: second `pacman -Su` finds nothing to
do, `pacman -Dk` reports no database errors, `pacman -Qk` reports 0 packages
with missing files, the identity scan finds no private key, shell history, SSH
host key or machine id, and every account is locked.

## Licences and source

Each package is under its own licence; the texts ship in the rootfs under
`/usr/share/licenses/`, and 120 `*.source.tar.gz` archives (recipe and upstream
sources; split packages share one) with `SOURCES.tsv` are attached to the same
release. Nothing in the archive was written by
ThothTerm except the managed files `build-rootfs.sh` creates (`/etc/hostname`,
an empty `/etc/machine-id`, `/etc/locale.conf`, the resolver placeholder, one
`pacman.conf` setting for PRoot) and the signature-check package kept for the
first-run keyring proof (an unmodified Arch Linux ARM package with its detached
signature).

## The optional repository is separate

The BlackArch repository setup does not alter this archive; it acts on the
installed copy on the phone, only after consent (`DESIGN.md`). Its keyring is
acquired from the project's own site at run time and verified against pins; it
is not part of any build input, release asset or source file of this repository.
