# ThothTerm BlackArch — design record

Status: **development**: first slice (module skeleton, identity, protected ids,
multi-keyring support) and rootfs slice (a host-validated candidate, NOT PUBLISHED:
`ROOTFS_PROVENANCE.md`). Nothing is released, tagged or submitted, and nothing has
run on a phone. Facts below were measured on 2026-10-03; re-measure before release.

## Identity (locked for development)

| | |
|---|---|
| Display name | ThothTerm BlackArch (development name; naming is a release gate, see Trademark) |
| Module | `garden-blackarch` |
| Package id | `com.thothterm.blackarch` (protected like every production id) |
| Version | 0.1.0 / versionCode 100, literal in `garden-blackarch/build.gradle` so F-Droid's `checkupdates` reads it statically |
| LAN port | 7684 (`docs/garden/PORTS.md`) |
| Tags (later) | source `blackarch-v*`, rootfs `blackarch-rootfs-aarch64-*` |

### Port 7684

Checked 2026-10-03 before locking: no reference in this repository other than
the registry's "next edition takes 7684" line and one historical hand-off note
about the occupied-port fallback; no listener on the build host (`ss -ltn`);
no socket in any state on the Samsung SM-A165F (`/proc/net/tcp`, `/proc/net/tcp6`
read over adb, nothing installed or changed). The same read showed 7683
listening: Rolling's LAN Mode was on. 7684 is not in `/etc/services`, which is
not an authority. A port in use on a user's network is handled by LAN Mode's
fallback to the next free port (`LanServer.start`), so the registry value is
a preference, not a guarantee.

## Upstream facts (measured 2026-10-03)

- BlackArch publishes no ARM image. Its download page says it is compatible with
  Arch Linux ARM and that the repository is added to an installed system.
- An AArch64 repository exists at
  `https://blackarch.org/blackarch/blackarch/os/aarch64` (the repository name
  appears twice in the path). It lists 4702 packages (1084 `aarch64`, 3618
  `any`) against 5050 in the x86_64 repository; `armv7h` is a 404.
- Trust: `blackarch-keyring` 20251011-2 (SHA-256
  `d40c1301f84195164a2843f2447f5b3833bd9ea30e5cfdaf4271ad9c9771ee07`) is signed
  by key `F9A6E68A711354D84A9B91637533BAFE69A25079`; its `blackarch-trusted`
  lists four signing keys, the same four `strap.sh` hard-codes. A package whose
  signature is not embedded in the database also verified against its detached
  `.sig`.
- `strap.sh` is interactive, edits `pacman.conf` with `sed`, derives the path
  from `uname -m` and is authenticated only by a published SHA-1. The app does
  not run it; the rootfs slice installs the keyring from pinned bytes.

### What the aarch64 catalog can honestly be said to offer

On the Rolling rootfs in an aarch64 container (qemu, `--disable-sandbox`, not
PRoot, not a phone) with the signature level unchanged: the keyring populated
offline; `pacman -Sy` synced all 4702 packages; one real install of three
packages (125 with dependencies) and its removal succeeded, `pacman -Dk` clean;
59 of 59 randomly sampled packages resolved their dependency closure.

A name-level dependency analysis over the whole aarch64 repository (version
constraints ignored, cycles treated optimistically) found 4413 of 4702 packages
whose dependencies are present in the Arch Linux ARM and BlackArch databases,
and 289 whose dependencies are not. `blackarch-officials` itself cannot be
installed (`kcptun`, `rathole` are missing).

> Approximately 94% of the current aarch64 catalog passed the present
> dependency-resolution analysis; this is not equivalent to successful
> installation/runtime validation.

No application text may promise a number of tools, the x86_64 catalog,
`blackarch-officials`, or that any named tool runs under PRoot. Many popular
tools (`nmap`, `hydra`, `sqlmap`, `hashcat`) are in Arch Linux ARM's own
repositories, not BlackArch's; `metasploit` and `john` are absent.

## Architecture

Rolling's, with a second signing identity:

- Rootfs: Arch Linux ARM base built like `garden-arch/rootfs`, plus
  `blackarch-keyring`, `blackarch-mirrorlist` and a `[blackarch]` section that
  keeps the global `SigLevel` (never `Never`, `Optional` or `TrustAll`). The
  keyring is verified against pinned fingerprints and SHA-256, not a SHA-1.
- Terminal readiness: the terminal needs only the rootfs and a runnable shell.
  Keyring creation (`pacman-key --init`, `--populate archlinuxarm blackarch`)
  and the signature proofs are optional setup: background, single-flight,
  retryable, never on the main thread. `blackarch` repository synchronisation
  stays the user's `pacman -Syu`.
- Shared code: everything in `garden-common` is reused unchanged except one
  additive change, below.

### Multiple keyrings and signing identities (this slice)

`pacmanKeyring` and `packageSigningKey` in `distro.properties` accept
white-space separated lists (at most 8 entries, no duplicates, each validated
with the same patterns as before). `DistroInfo.pacmanKeyring()` and
`packageSigningKey()` still return the first entry, so a single-value edition
(Rolling) reads exactly what it always read; `pacmanKeyrings()` and
`packageSigningKeys()` return the lists. The keyring script
(`RootfsManager.pacmanKeyringScript`) is byte-for-byte the previous one for a
single signing key (`PacmanKeyringScriptTest` compares it with the text derived
from the previous revision). With several keys it populates every keyring,
requires every key to be fully valid, requires every file in the signature-check
directory to be signed by one of the keys with full trust, and requires every key
to have signed at least one of them.

## Trademark

Release gate. The product name "ThothTerm BlackArch" is the development name.
No BlackArch trademark or logo policy was found on 2026-10-03 (its `LICENSE` page
returned 404; the FAQ and downloads page say nothing on the subject), so the
position is UNVERIFIED. Before any tag, release or F-Droid submission: contact
the BlackArch project, record the answer, and decide between this name and a
neutral one, as Rolling did (`docs/branding/arch/TRADEMARK.md`). Until then
every user-visible text carries a non-affiliation notice, and the app uses none
of BlackArch's or Arch's artwork. The supplied artwork
(`logos/ChatGPT Image Sep 26, 2026, 07_39_07 AM.png`) is the Garden cup and
petals in black and ice blue with a "BLACKARCH EDITION" wordmark; its wording
falls under the same gate.

## Not done yet

Publication of the rootfs (release asset, immutable URL, `rootfsPublication=published`);
the real launcher icon and palette; the package-manager gate and every physical-device
check; F-Droid metadata; tags and releases; the licence of `blackarch-keyring` and the
trademark review (both release gates).
