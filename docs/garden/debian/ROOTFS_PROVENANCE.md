# ThothTerm Debian — root filesystem provenance

## Pinned release

| Field | Value |
|---|---|
| Distribution | Debian GNU/Linux |
| Version | **13.7** (point release of Debian 13) |
| Codename | **trixie** |
| Released | 2026-09-12 (`Date: Sat, 12 Sep 2026 07:55:41 UTC` in the signed `InRelease`) |
| Architecture | arm64 |
| Archive snapshot | `snapshot.debian.org` timestamp **`20260926T000000Z`** |
| Effective epoch | 2026-09-25T22:03:11Z (newest signed `Release` file used: `trixie-security`) |

### How "current stable" was established

1. `https://deb.debian.org/debian/dists/stable/Release` (fetched 2026-09-26):
   `Suite: stable`, `Version: 13.7`, `Codename: trixie`,
   `Description: Debian 13.7 Released 12 September 2026`.
2. `https://www.debian.org/releases/`: "The current stable distribution of
   Debian is version 13, codenamed trixie."
3. `dists/trixie/InRelease` verified with `gpgv` against
   `debian-archive-keyring` 2025.1: good signatures from the
   *Debian Archive Automatic Signing Key (13/trixie)* (signing subkey
   `B8E5F13176D2A7A75220028078DBA3BC47EF2265` of primary
   `04B5 4C3C DCA7 9751 B16B C6B5 2256 29DF 75B1 88BD`) and the
   *Debian Stable Release Key (13/trixie)* `4158 7F7D B8C7 74BC CF13 1416 762F 67A0 B2C3 9DE4`.
4. Both primary fingerprints are listed on `https://ftp-master.debian.org/keys.html`
   as "the Debian 13/trixie archive signing key" and "the Debian 13/trixie
   release key", an independent Debian source for the keyring.
5. The snapshot `20260926T000000Z` serves the same signed `InRelease`
   (`Version: 13.7`, three good signatures), and its `trixie-security`
   `InRelease` (dated 2026-09-25 22:03:11 UTC) verifies with the
   *Debian Security Archive Automatic Signing Key (13/trixie)*.

## Choice of source

Debian publishes no minimal root-filesystem tarball of its own (Ubuntu does:
Ubuntu Base on cdimage.ubuntu.com, which the Ubuntu edition uses). Options
considered:

| Option | Verdict |
|---|---|
| Official Debian container image artifacts (`debuerreotype/docker-debian-artifacts`) | Built by Debian developers with `debuerreotype`, but distributed from GitHub. Reproducible, yet consuming them means trusting a GitHub-hosted binary. |
| Debian cloud images (`cloud.debian.org`) | Official infrastructure, but whole-disk images with a kernel, bootloader and systemd services — far more than a PRoot userland needs. |
| **Build it from the Debian archive with `debuerreotype`** | **Chosen.** Every package comes from Debian's archive via `snapshot.debian.org` and is verified against `debian-archive-keyring`; the tool is Debian's own (packaged in Debian, and what the official images use); the result is reproducible, so the published digest can be re-derived by anyone. |

## Construction

`garden-debian/rootfs/build-rootfs.sh` (committed) runs, in a container:

- **Build environment:** the official image
  `debian:trixie-slim@sha256:a99cfc517144bc59b1978475ec53b46ecabec7e43635402ee5b77cc54cd1b20a`
  (created 2026-09-18), whose apt sources are replaced by the pinned snapshot
  before anything is installed. Tools installed from that snapshot:
  `debuerreotype 0.15-1.1`, `debootstrap 1.0.141`.
- **Steps:** `debuerreotype-init --check-gpg --keyring debian-archive-keyring.gpg
  --arch arm64 rootfs trixie 2026-09-26T00:00:00Z` (debootstrap `minbase`,
  merged-usr, second stage under qemu-user via binfmt_misc) →
  `trixie-updates`/`trixie-security` from the same snapshot → `dist-upgrade` →
  install `garden-debian/rootfs/packages.txt` with `--no-install-recommends`
  (`sudo ca-certificates curl netbase procps less nano`) →
  `debuerreotype-recalculate-epoch` → user-facing sources set to the live
  `deb.debian.org` mirrors (the file records the snapshot it was built from) →
  apt lists and caches removed → `debuerreotype-tar` (clamped timestamps,
  sorted, numeric owners) → `gzip -9 -n`.
- **Not customized at build time.** The `thoth` user, sudoers entry, hostname
  and `/etc/hosts` are applied on the device after extraction by Garden's
  guest-configuration step, as in the Ubuntu edition, so the tarball is a plain
  Debian userland.

## Result

| Field | Value |
|---|---|
| File | `debian-13.7-trixie-arm64-rootfs.tar.gz` (packaged in the APK as `.tgz`) |
| SHA-256 | **`d88047a51be68dbf95062a2371ad2a7cb82b20e2681e91e2c31dde6337d971d1`** |
| Compressed size | 63,775,639 bytes |
| Uncompressed size | 179,732,480 bytes |
| Archive entries | 7,204 (no device nodes; one hardlink, `usr/bin/perl5.40.1`) |
| Packages | 111 — full list with versions in `garden-debian/rootfs/debian-13.7-trixie-arm64-rootfs.packages.tsv` |
| `/etc/os-release` | `PRETTY_NAME="Debian GNU/Linux 13 (trixie)"`, `DEBIAN_VERSION_FULL=13.7` |
| `/etc/debian_version` | `13.7` |
| Key packages | `base-files 13.8+deb13u7`, `libc6 2.41-12+deb13u4`, `bash 5.2.37-2+b10`, `apt 3.0.3`, `dpkg 1.22.22`, `sudo 1.9.16p2-3+deb13u2` (setuid), `curl 8.14.1-2+deb13u5`, `openssl 3.5.7-1~deb13u2`, `ca-certificates 20250419` |

**Reproducibility:** two independent runs of `build-rootfs.sh` (2026-09-26,
separate output directories, concurrent) produced byte-identical tarballs with
the digest above and identical package lists.

## Licensing

The rootfs is an aggregate of Debian packages under their own licenses. Each
package's `/usr/share/doc/<package>/copyright` is preserved inside the image and
is the authoritative statement of its terms. Sources for every binary package
are available from the Debian archive and, for these exact versions, from
`snapshot.debian.org` at the timestamp above.
