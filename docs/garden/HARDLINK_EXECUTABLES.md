# Hard-linked executables under PRoot — the Garden rule

**Rule.** Under PRoot's `--link2symlink`, a program started through a hard
link must see that hard link's own path in `/proc/self/exe`, exactly as on
Linux. It must never see link2symlink's hidden `.l2s.*` backing file. Every
Garden edition inherits this from the shared PRoot runtime (patch
`term-ubuntu/patches/0003-link2symlink-name-proc-self-exe-after-the-faked-hard-link.patch`).

## Why it matters

Distributions increasingly ship **multicall binaries installed as hard links**,
one link per command. Programs also inspect their own identity. Ubuntu 26.04's
`rust-coreutils` carries `require-utility-to-be-invoked-at-matching-path.patch`
(an AppArmor hardening). It refuses to run unless the file name of
`readlink(/proc/self/exe)` equals the utility requested:

    Security violation: Requested utility `chown` does not match executable name:
      /usr/bin/.l2s.coreutils.dpkg-new0001.0116

## What happened

1. Android forbids apps from creating hard links. The app's extractor therefore
   materializes the base tarball's hard links as copies (115 in
   ubuntu-base 26.04.1). Every copy has its own name, so the shipped rootfs
   works.
2. Inside the guest, `dpkg` installs the next rust-coreutils
   (`0.10.0-1ubuntu2~26.04.1`) by `link(2)`-ing the new binary to every utility
   name. link2symlink fakes each hard link as a chain:
   `coreutils/chown` → `.l2s.coreutils.dpkg-new0001` →
   `.l2s.coreutils.dpkg-new0001.0116` (the file; `.0116` is the link count).
3. PRoot reported the executed file's canonical path, the backing file, as
   `/proc/self/exe`. Every coreutils command failed. sudo's postinst failed on
   `chown`, and dpkg was left with sudo half-configured.

## The fix

At `execve`, when the executed file lives in the l2s directory, link2symlink
walks the path the program passed. It follows ordinary symlinks as the kernel
does, and stops at the faked hard link that leads to the executed file. That
link becomes the new `/proc/self/exe`.

Two details matter:

- Only the directory part of each step is translated.
  link2symlink's own path hook swaps a faked hard link for its backing file,
  which would hide the very link being looked for.
- Nothing changes when no faked hard link is involved.

The fix is generic: nothing names a package. Perl (`perl` / `perl5.40.1`) is
fixed by the same code.

## Regression tests

- `tests/proot-hardlink-identity/host-test.sh` builds PRoot natively from the
  pinned sources plus the patches and checks the kernel semantics: absolute
  path, the linked original, relative and absolute symlinks to a hard link, a
  relative path, and no hard link at all. Without the patch, five of its seven
  checks fail. `ProotHardlinkIdentityTest` runs it in the JVM unit tests on
  Linux build hosts.
- `tests/proot-hardlink-identity/device/device-test.sh` runs the full
  upgrade matrix on the phone as the adb shell user in `/data/local/tmp`. It
  never touches app data. Starting from the pinned baseline rootfs it runs
  `apt update`, `full-upgrade`, `dpkg --audit`, `--fix-broken install`,
  `sudo -n id -un`, the core utilities as root and as thoth, and a package
  install and remove. It then reinstalls rust-coreutils (the future-upgrade
  path) and repeats a transaction in a new PRoot process.

## For future distros

Before shipping any Garden edition, run its package manager's full upgrade on
a clean baseline under the edition's PRoot (see quirk 17 in
`docs/ANDROID_PROOT_QUIRKS.md`). Pay attention to multicall packages installed
as hard links: coreutils, busybox, perl, git and similar.
