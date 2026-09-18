# ThothTerm Ubuntu — genuine sudo provisioning and provenance

Status: implemented on `feature/thotterm-linux`. The embedded Canonical Ubuntu
rootfs is unchanged; sudo is a post-extraction provisioning layer installed
offline from packages bundled in the APK.

## Why the su-backed helper was removed

Runtime config v2 wrapped `su` in a managed `/usr/local/bin/sudo`. On device it
failed with `su: System error`. PRoot's `fake_id0` extension (`SETXID` in
`extension/fake_id0/fake_id0.c`) only allows `setuid(0)` while the fake euid is
already 0. After the session drops to `thoth` (fake uid 1000) the helper's
`su root` calls `setuid(0)`, which PRoot denies with `EPERM`; util-linux `su`
then prints `su: System error`. Raising privilege with `su` cannot work once the
fake identity has dropped.

Genuine `sudo` works because PRoot's `fake_id0` `handle_sysexit_start()` grants
fake euid 0 when the **executed binary carries the setuid bit** — a path that
does not depend on the caller's current fake uid. That is the PRoot-specific
adjustment: `/usr/bin/sudo.ws` must have mode `4755` on the real filesystem.

## Bundled packages

Repository: `http://ports.ubuntu.com/ubuntu-ports` (Ubuntu 26.04 "resolute",
`main`, `binary-arm64`). Versions pinned exactly as published at the time of
release; every package is verified by SHA-256 by `tools/prepare-assets.sh`
before staging.

| Package | Version | Arch | Filename | Size (bytes) | SHA-256 | License |
| --- | --- | --- | --- | ---: | --- | --- |
| `sudo` | `1.9.17p2-1ubuntu3` | arm64 | `sudo_1.9.17p2-1ubuntu3_arm64.deb` | 923,956 | `a1f04e24343ad3b73123ca2dc2b43684ef1562ea3ed43788dc8ec79da574a873` | ISC |
| `sudo-common` | `1.2ubuntu` | all | `sudo-common_1.2ubuntu_all.deb` | 4,034 | `ba909e8e796115f442d0915ed3baa1b752e8809ec4d8e723d2bbd0d177750d2c` | GPL-2+ |
| `libapparmor1` | `5.0.0~beta1-0ubuntu7` | arm64 | `libapparmor1_5.0.0~beta1-0ubuntu7_arm64.deb` | 50,608 | `97bc3adba874fdda34afba6a206d3fcd5b531bb8681ba27c4442ee1379834cfa` | GPL-2+ / BSD-3-clause (per packaged copyright) |

- Pool paths: `pool/main/s/sudo/...`, `pool/main/s/sudo-common/...`,
  `pool/main/a/apparmor/...`.
- `sudo` Pre-Depends `sudo-common`; Depends `libpam-modules`, `libapparmor1`,
  `libaudit1`, `libc6`, `libpam0g`, `libselinux1`, `libssl3t64`, `zlib1g`.
- `libapparmor1` Depends `libc6`; `sudo-common` has no dependencies.
- Package copyright/licence files are preserved inside the installed rootfs at
  `/usr/share/doc/<package>/copyright`.
- The embedded Canonical rootfs tarball is **not** modified and its pinned
  SHA-256 (`5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd`)
  is unchanged.

### Why only these three

Everything else in the closure is already in the pinned Ubuntu Base image:
`libc6` 2.43, `libpam0g`/`libpam-modules` 1.7.0, `libselinux1` 3.9, `libssl3t64`
3.5.5, `zlib1g` 1.3, `libaudit1` 4.1.2. Only `libapparmor1`, `sudo-common`, and
`sudo` are missing, so only those are bundled.

## Offline provisioning architecture

1. `tools/prepare-assets.sh` downloads the three `.deb`s from
   `ports.ubuntu.com`, verifies each SHA-256 and size against the pin, caches
   them under `$HOME/.cache/thothterm`, and stages them into the git-ignored
   `term-ubuntu/src/main/assets/sudo/`.
2. On first run, after extraction (and on later sessions if needed),
   `RootfsManager.ensureRealSudo()` copies the assets into
   `rootfs/var/cache/thothterm/packages/`.
3. A one-shot PRoot command runs as fake-root:
   `dpkg --force-confold -i libapparmor1_*.deb`, then `sudo-common_*.deb`, then
   `sudo_*.deb`, then `dpkg --configure -a`. Dependency order matches the
   `Depends`/`Pre-Depends` graph; `--force-confold` keeps re-runs from prompting.
4. `RootfsManager.forceSudoSetuid()` sets mode `04755` on the real
   `usr/bin/sudo.ws` from Java, bypassing PRoot's chmod shim, then verifies the
   bit with `Os.stat`. The `sudo` postinst creates `/usr/bin/sudo` through
   `update-alternatives` (→ `/etc/alternatives/sudo` → `/usr/bin/sudo.ws`).
5. A verification PRoot command runs `dpkg-query -W sudo`, checks `/usr/bin/sudo`
   is executable, and runs `visudo -c`.

If provisioning fails it is logged via `ThothLog` (never hidden) and retried on
the next session; the terminal still opens.

## Guest configuration

- `/etc/sudoers.d/thoth`: `thoth ALL=(ALL:ALL) NOPASSWD: ALL`, mode `0440`.
- `/etc/pam.d/su`: the v2 `auth sufficient pam_permit.so` line is removed, so
  `su` returns to the stock `auth sufficient pam_rootok.so` policy. `su root`
  from `thoth` no longer offers a passwordless route; `sudo` is the elevation
  path.
- `/usr/local/bin/sudo` (the v2 su helper) is deleted only when it carries the
  ThothTerm marker; an unrecognised user replacement is left in place and logged.
- `GuestConfig.RUNTIME_CONFIG_VERSION` is `3`; the migration is idempotent and
  does not re-extract the rootfs.

## Size contribution

- APK: the three `.deb`s total 978,598 bytes uncompressed and add ~0.98 MB to
  the Ubuntu debug APK (clean build 41,845,480 bytes).
- Installed rootfs: `sudo` 3,828 KiB + `libapparmor1` 165 KiB + `sudo-common`
  26 KiB ≈ 4.0 MB installed, plus dpkg metadata.

## Provenance statements

- Only official Ubuntu archive packages are used; no third-party or repackaged
  sudo binary is bundled.
- The rootfs is the byte-identical Canonical base; sudo is layered on top.
- Real `sudo` provides the guest admin transition. This is not Android/host
  root: PRoot fake-root is user-space emulation inside the app sandbox.
