# Third-party notices — ThothTerm BlackArch

**Development skeleton.** The rootfs, its provenance and its per-package
notices do not exist yet; this file lists what is already settled and says what
is still to come. Nothing here is released.

ThothTerm BlackArch is independent and is not affiliated with or endorsed by
the BlackArch, Arch Linux or Arch Linux ARM projects. Arch Linux is a trademark
of Levente Polyák and Judd Vinet, on behalf of Arch Linux. Those names are used
only to describe what the app installs, and no BlackArch, Arch Linux or Arch
Linux ARM logo or artwork is used.

## 1. Environment (to be written with the rootfs)

The planned environment is an Arch Linux ARM aarch64 root filesystem like
ThothTerm Rolling's, plus the `blackarch-keyring` and `blackarch-mirrorlist`
packages from the BlackArch aarch64 repository and a `[blackarch]` section in
`pacman.conf` that keeps the global signature level. Its pins, inputs and
corresponding sources will be recorded in `docs/garden/blackarch/` when it is
built. The `blackarch-keyring` package declares its licence as
`custom:unknown`; the licence of anything redistributed must be read from the
artifact before release (release gate).

## 2. Shared Garden components

As in `garden-common/THIRD_PARTY_NOTICES.md`: the PRoot runtime (GPL-2.0,
talloc LGPL-3.0, libandroid-shmem BSD-3-Clause), the terminal font, and the LAN
Mode web terminal.

## 3. Artwork

The launcher icon in this tree is a plain placeholder. The edition's artwork
will be ThothTerm's own Garden artwork (`logos/`), not BlackArch's.
