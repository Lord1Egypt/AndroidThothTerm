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
remain enabled. The build refuses `SigLevel = Never` and checks that the two
other sandbox settings have not been disabled.

## Interrupted transactions

Pacman leaves `/var/lib/pacman/db.lck` when it is killed during a transaction.
Only remove that file after confirming **no pacman process is running**. The
app's first-run recovery uses the same condition. An interrupted extraction
can also leave files or local database metadata incomplete; removing a lock
does not repair those. Check `pacman -Dk` and `pacman -Qk`, then reinstall the
affected **same package version** from a verified package when needed. The
device gate exercises lock removal, a missing local `desc`, leftover files,
and a damaged library on disposable rootfs copies; it never damages the
phone's primary environment to create these cases.

`systemd` is installed as a package dependency, but PRoot does not boot it as
PID 1. A hook in the official stale upstream image can report that it cannot
restart `sshd.service` through systemd. The gate accepts only that exact hook
message when pacman itself succeeds and database and library checks pass.
Other package and scriptlet errors remain failures.

Android cannot chmod a symbolic link. The device gate tolerates pacman's
`Can't set permissions to 0777` warning only when each named link resolves and
the installed package's integrity check is clean. It does not ignore other
permission warnings.

The release gate is `tests/garden-arch/device/gate.sh`, run with the current
rootfs archive and an older official Arch Linux ARM userland. It runs in
disposable app-owned copies, verifies a full `pacman -Syu`, a second no-op
`pacman -Syu`, install/remove/reinstall, DNS, TLS, dynamic libraries, signing,
restart, stale-image upgrade, and interrupted-transaction recovery.
