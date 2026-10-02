# Package management in ThothTerm Rolling

ThothTerm Rolling runs an Arch Linux ARM AArch64 userland under PRoot. Use the
normal Arch package manager inside a terminal:

```sh
sudo pacman -Syu
sudo pacman -S tree
sudo pacman -Rns tree
```

The rootfs ships without pacman's sync databases, package cache, lock, logs,
or a populated `/etc/pacman.d/gnupg` directory. On first installation the app
creates a keyring for that installation with `pacman-key --init` and
`pacman-key --populate archlinuxarm`. Before setup completes it requires the
Arch Linux ARM Build System key to be fully valid and verifies the signature
of a genuine repository package shipped as an offline check. Normal startup
does not need a public keyserver. The rootfs never contains the builder's
private key. Package signature checking remains required.

Pacman 7's filesystem sandbox uses Landlock, which the tested Android kernel
does not support. The rootfs sets only `DisableSandboxFilesystem` in
`/etc/pacman.conf`. Its syscall sandbox and unprivileged `DownloadUser = alpm`
remain enabled. The build checks the shipped `pacman.conf` with `pacman-conf`:
the global `SigLevel` must include `Required`, and neither it nor any
repository may contain `Never`, `Optional`, `TrustAll` or their `Package*`
forms (`DatabaseOptional`, Arch's default for databases, is allowed). It also
checks that the two other sandbox settings have not been disabled. The
September 30 image (`03a4c669...`) was built before the `SigLevel` part of
this check existed; `ROOTFS_PROVENANCE.md` records its
`SigLevel = Required DatabaseOptional`, and the last check of
`tests/garden-arch/device/interrupt.sh` verifies the levels on the device.

## Interrupted transactions

Pacman leaves `/var/lib/pacman/db.lck` when it is killed during a transaction.
Only remove that file after confirming **no pacman process is running**; the
app removes it on a new window only when no PRoot process of the app runs (and
therefore no guest process at all). Removing the lock does **not** complete or
repair the interrupted transaction, and the next `pacman -Syu` does not
necessarily do so either. An interrupted extraction can leave a package's
files or its local database entry incomplete:

1. `pacman -Dk` and `pacman -Qk` show what is affected.
2. Reinstall the affected package at the **same version** from a package file
   whose signature you can verify (`pacman -S <pkg>` normally does this).
3. If pacman refuses because the package's own files "exist in filesystem",
   overwrite only that package's paths (`--overwrite '/its/path/*'`).
4. If its local database entry has no `desc` ("could not fully load
   metadata"), recreate the entry with `pacman -S --dbonly <pkg>` from the
   verified package, then reinstall it normally.
5. Finish with `pacman -Qkk <pkg>`, `pacman -Dk`, `pacman -Qk` and a full
   `pacman -Syu`.

A signature, key or "invalid or corrupted package" error is never a symptom of
an interruption; it means the bytes or the trust are wrong. The device gate
(`tests/garden-arch/device/interrupt.sh`) exercises each case on disposable
rootfs copies, verifies every cached package's signature itself before using
it, and fails on any such error; it never damages the phone's primary
environment. If the shell itself is missing after an interruption, the app
does not reinstall anything on its own: it reports the damaged environment and
offers a reinstall that keeps `/home` (`docs/garden/ROOTFS_LIFECYCLE.md`).

## systemd

`systemd` is installed as a package dependency, but PRoot does not boot it as
PID 1. systemd decides whether it runs in a chroot by comparing `/proc/1/root`
with `/`; Android hides other users' processes, PID 1 included, so that lookup
fails and systemd itself answers ENOSYS ("Failed to check for chroot()
environment: Function not implemented"). The app therefore sets
`SYSTEMD_IN_CHROOT=1` (honoured since systemd 257) in every guest environment,
and the sudoers entry keeps it across `sudo`. That is true -- the guest's root
is not PID 1's -- and it makes `systemctl` in package hooks say "Running in
chroot, ignoring command" as in any chroot, instead of failing. No hook is
removed, `systemctl` is not replaced and nothing pretends to be PID 1. The
stale-image gate no longer excuses any hook error.

## Symbolic link permissions

Earlier builds printed `warning given when extracting ... (Can't set
permissions to 0777)` for every symlink in a package. Root cause: glibc 2.39+
implements `lchmod` with the `fchmodat2` system call (Linux 6.6), which PRoot
did not know, so the call reached the kernel with the untranslated guest path
and failed with ENOENT; libarchive reports any error other than "not
supported" as a warning. PRoot patch 0006 translates `fchmodat2` with the
kernel's semantics: a no-follow chmod of a symlink returns EOPNOTSUPP, which
libarchive ignores as on any Linux, and a regular file gets its mode (before,
a no-follow chmod of a regular file silently did nothing). The warning is gone
because the operation behaves like Linux, not because it is filtered.
`tests/garden-common/proot/host-check.sh` reproduces it on a Linux host; the
device gate checks it with `bsdtar` (P6 in `zero.sh`) and, should the warning
ever appear again, runs `pacman -Qkk` on every package owning a named link.

## Release gate

The package gate is `tests/garden-arch/device/gate.sh`, run with the current
rootfs archive and an older official Arch Linux ARM userland. It runs in
disposable app-owned copies, verifies a full `pacman -Syu`, a second no-op
`pacman -Syu`, install/remove/reinstall, DNS, TLS, dynamic libraries, signing,
restart, stale-image upgrade, and interrupted-transaction recovery. Its rootfs
copies are unpacked with tar, so it says nothing about the app's own
extractor: that proof is `tests/garden-common/extractor/device-gate.sh
garden-arch ARCHIVE SHA256`, which must pass on the same archive.
