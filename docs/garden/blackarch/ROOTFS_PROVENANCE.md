# ThothTerm BlackArch AArch64 rootfs provenance

**This is not an official BlackArch image.** BlackArch publishes no ARM image.
This archive is the ThothTerm Rolling userland (Arch Linux ARM, unmodified
packages) with the official BlackArch aarch64 repository configured and its
signing keys pinned. It is not built, endorsed or supported by BlackArch, Arch
Linux or Arch Linux ARM. No BlackArch tool is preinstalled: the user installs
whatever the aarch64 repository can provide with pacman.

**Publication state: NOT PUBLISHED.** The archive exists only in the local build
output. No GitHub release, tag or F-Droid request exists. `distro.properties`
says so (`rootfsPublication=not-published`) and no F-Droid release can be
packaged while it does (`requirePublishedRootfs`).

Built 2026-10-03 on the build host; inputs captured 2026-09-30 (Arch Linux ARM)
and 2026-10-03 (BlackArch keyring). Candidate status: **host-validated only**;
nothing here has been run on a phone.

## Result

| Item | Exact value |
| --- | --- |
| Archive | `thothterm-blackarch-aarch64-rootfs-45a791115213.tar.gz` |
| SHA-256 | `45a79111521382fc4162d5642da360b7a78a1cc251a1e2efcf138bb41bc94ac9` |
| Compressed size | `199874013` bytes |
| Uncompressed tar size | `674775040` bytes |
| Architecture | `aarch64` |
| Installed packages | `139` (Rolling's 138, plus `blackarch-keyring`) |
| Archive entries | `33656`: 1365 directories, 8188 symlinks, 22282 files, 1821 hard links |
| Distinct paths | `33656` (no duplicate names) |
| Extracted paths | `33655` by the Garden extractor policy and by the independent manifest (the archive's `.` root is the one entry that is not a path), 0 rejected, 0 hard-link copies on the host |
| Package state | Internally consistent as of `2026-09-30T07:52:09Z` (Arch Linux ARM) |

Difference from ThothTerm Rolling's rootfs (`03a4c669ed6f`, 33640 entries),
measured entry by entry: **16 entries added, 2 changed, 0 removed.**

- Added: `/etc/pacman.d/hooks/` and `blackarch-key.hook`; the three files under
  `/usr/share/pacman/keyrings/` (`blackarch.gpg`, `blackarch-trusted`,
  `blackarch-revoked`); `/usr/share/thothterm/blackarch/` (README,
  `keyring.pins`, `blackarch-repo.conf`); the BlackArch keyring package and its
  signature under `/usr/share/thothterm/signature-check/`; and the four files of
  the `blackarch-keyring` entry in pacman's local database.
- Changed: `/etc/pacman.conf` (the `[blackarch]` section) and
  `/usr/share/thothterm/signature-check/README`.

## Inputs

| Input | Exact value |
| --- | --- |
| Arch Linux ARM base image | `http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz` |
| Last-Modified | `Wed, 05 Aug 2026 12:41:36 GMT` |
| Size / SHA-256 | `829367415` bytes / `42a4eeaa038994ffd31fa173256ef2f0ef511358eeb41b9ea1f8626391b9b319` |
| Detached signature | valid, by Arch Linux ARM Build System key `68B3537F39A313B3E574D06777193F152BDBE6A6`, made 2026-08-05 |
| Repository sync | 2026-09-30T07:50:50Z to 07:52:09Z, `http://mirror.archlinuxarm.org/$arch/$repo`; 145 signed packages captured |
| Base inputs (`inputs.sha256`) | `08b69ce5465578e350f16cf503cf661b1d7bab2a650e7b0ec086cf1646253060`: **byte for byte the capture ThothTerm Rolling was built from**, re-verified (299 files) before this build |
| BlackArch inputs (`blackarch/inputs.sha256`) | `0663d7a3c2da7be24b670ad76d9b7772d236fceab14109ab44cbb515f42ca096` |
| Builder image | `thothterm-alarm-upstream:42a4eeaa0389`, derived from the signed upstream image (`sha256:6ba8ea32...`) |

The Arch Linux ARM side is Rolling's 2026-09-30 capture on purpose: the 138
Arch Linux ARM binaries are the very ones whose corresponding source was
already collected and verified for Rolling, so this edition adds one package
to the source obligations, not 138. The consequence is that this image is as
old as that capture; the first `pacman -Syu` brings a device current.

### The BlackArch keyring package

| | |
| --- | --- |
| Package | `blackarch-keyring` `20251011-2` (`any`) |
| URL | `https://blackarch.org/blackarch/blackarch/os/aarch64/blackarch-keyring-20251011-2-any.pkg.tar.zst` (Last-Modified `Mon, 10 Nov 2025 00:21:13 GMT`) |
| Size / SHA-256 | `18445` bytes / `d40c1301f84195164a2843f2447f5b3833bd9ea30e5cfdaf4271ad9c9771ee07` |
| Detached signature | `...pkg.tar.zst.sig`, 566 bytes, SHA-256 `c2e21fcd878e26ed0b91ebc53f5fbb3d9a5d28278f0eb218ea866b7a2c04499d` |
| Signed by | `F9A6E68A711354D84A9B91637533BAFE69A25079`, Levon 'noptrix' Kayan (BlackArch Developer) |
| Recipe | `BlackArch/blackarch` `packages/blackarch-keyring` at `eb863fdeb5cc41204d226812d4408d182f1ada46` (PKGBUILD SHA-256 `9867b2a2...8be13cb`, equal to the `pkgbuild_sha256sum` in the package's own `.BUILDINFO`) |

The repository keeps only the latest version of a package: this URL stops
working when BlackArch releases the next keyring. The SHA-256 is the pin; the
captured file is kept with the build inputs and must be kept with the rootfs.

### Trust anchors (`garden-blackarch/rootfs/keyring.pins`)

| Role | Fingerprint | Where it is established |
| --- | --- | --- |
| Signs the keyring package, the repository database and 1434 of the 1437 aarch64 package signatures embedded in it | `F9A6E68A711354D84A9B91637533BAFE69A25079` | in `blackarch.gpg`; one of the four in `blackarch-trusted` and in BlackArch's `strap.sh` `KEYRING_SIGNERS`; verified by `gpgv` against this key alone |
| Trusted (`blackarch-trusted`, each `:4:`) | `4345771566D76038C7FEB43863EC0ADBEA87E4E3` `8F9A9793CB8591147C2EC70566E0CDBD1E01F333` `A0917C4147A37007CB54C1CFD295AA940EFDDF62` `F9A6E68A711354D84A9B91637533BAFE69A25079` | the keyring package; the same four `strap.sh` hard-codes (measured 2026-10-03) |
| Revoked (`blackarch-revoked`) | `5E210889BBB5C48500E0C4F9C75E985FF8B993B4` | the keyring package |
| In the keyring, neither trusted nor revoked | `CBA3C7D4798912702DCF568E67D8BDF42AD93F4E` (noptrix, BlackArch Master), `F6DA3545F655964DD9177918C65009B64EB7BB3C` (Evan Teitelman, BlackArch Developer) | reported by the builder as NOTICE and pinned; pacman does not trust them |
| Signs 3 of the 1437 embedded signatures (e.g. `mbelib`) | `68B3537F39A313B3E574D06777193F152BDBE6A6` (Arch Linux ARM) | already trusted through the Arch Linux ARM keyring |

`blackarch.gpg` (SHA-256 `aafde833...78369f8`) is committed beside the pins; it is
byte-identical to the file in the package and in BlackArch's `blackarch-keyring`
git repository. The trust root of this build is this reviewed pin file, not a
download.

**Upstream drift, found while pinning.** The upstream git repository
(`BlackArch/blackarch-keyring`, HEAD `e5bb520`, 2025-10-11) lists `CBA3C7D4...` as
trusted too (five keys); the released package, the one the repository serves,
and `strap.sh` list four. This image pins the released package. If BlackArch
starts signing with `CBA3C7D4...` or ships a new keyring, the builder fails on
the pin until the change is reviewed and `keyring.pins` updated deliberately.

### Repository configuration: no mirror list

The `[blackarch]` section (`blackarch-repo.conf`, SHA-256
`35ffd3cd255c1bd04085a4a84a83c46daf84e2ed175ddebc1eee05245ccb7b19`) is:

```
[blackarch]
Server = https://blackarch.org/blackarch/$repo/os/$arch
```

It is BlackArch's own origin, over HTTPS, and with `Architecture = aarch64` it
resolves to the verified aarch64 repository. It sets no `SigLevel`. The
`blackarch-mirrorlist` package (`20260403-1`, SHA-256 `6b675524...79f0e32`, licence
`custom:unknown`) is deliberately **not** installed: it is not needed for trust,
its only active server is a third-party university mirror, and shipping it would
add redistributed material of unclear licence. A user who wants mirrors installs
it and adds its `Include`.

## Build

`garden-blackarch/rootfs/build-rootfs.sh` is Rolling's builder with these
differences, each checked by a test:

1. `capture-blackarch`, and a host-side `verify-keyring.sh`, before anything is
   built: package and signature match the pinned size and SHA-256; the signature
   verifies with `gpgv` against the single pinned signer key and no other; the
   package carries exactly the pinned files (keys, trusted and revoked lists, hook,
   install script, nothing else); the keys in `blackarch.gpg` are exactly the
   pinned seven, the trusted list exactly the pinned four; a trusted key may not
   be revoked. Any difference fails the build.
2. In the builder container, the keyring is populated into the builder's own
   keyring (`pacman-key --populate blackarch`) and installed with `pacman -U`
   under `LocalFileSigLevel = Required`, so pacman checks the signature as well.
   `--noscriptlet` skips its install script (the app creates the installation's
   keyring at first run).
3. `blackarch-repo.conf` is appended to `pacman.conf`; the final file is checked
   by `check-pacman-conf.sh` (exact repository list and order, `Architecture`,
   the global `SigLevel` requires signatures and contains none of
   `Never`/`Optional`/`TrustAll` or their `Package*`/`Database*` forms other than
   `DatabaseOptional`, no `SigLevel` on any repository, `[blackarch]` is exactly
   the pinned `Server`, no `XferCommand`) and by pacman itself (`pacman-conf`).
4. The BlackArch keyring package and its signature are kept under
   `/usr/share/thothterm/signature-check/` beside Arch Linux ARM's, so the app
   proves both keys offline at first run; `/usr/share/thothterm/blackarch/`
   records the pins.
5. The package inventory uses `pacman -Qi` (the previous `-Qn` would skip the one
   foreign package).

Commands (offline `build`, `--network none`):

```sh
cd garden-blackarch/rootfs
INPUTS=<dir> ./build-rootfs.sh capture-blackarch   # on top of the existing base capture
INPUTS=<dir> OUT_DIR=<out> ./build-rootfs.sh build
```

Builder script SHA-256 `80b2e0dd92d77b2240fbd8fbb5e586cc06cb3a09aca94d3dbc547bf62766657d`;
host tools GnuPG 2.4.4, GNU tar 1.35; pacman v7.1.0 in the builder.

What the image does and does not contain: no pacman keyring (each installation
creates its own, in the background, after the terminal opens), no sync
databases, package cache, lock or log, no machine id, SSH host keys, private
keys or populated home; all passwords locked; timestamps clamped to the capture
epoch `1790754729`; numeric owners; `gzip -9 -n`. Nothing from the build host's
paths or time is recorded (`scan.txt`: 0 build paths, 0 private keys).

## Reproducibility: REPRODUCIBLE

Three offline builds (`out-a`, `out-b`, `out-c`) from the same pinned inputs on
the same host gave the same archive: SHA-256
`45a79111521382fc4162d5642da360b7a78a1cc251a1e2efcf138bb41bc94ac9`. The file lists
(name, size, mode, mtime, owner, type; 33656 entries), the generated
`pacman.conf` and the scan are identical as well. The only differences between
builds are in the manifest and inventory, from two builder edits made between
them (the manifest records the builder script hash; the inventory gained the
foreign keyring row); the archive did not change.

Limits of that claim: one host, one Docker, one qemu-user. It has not been
repeated on another machine. It depends on the captured inputs being retained
(upstream removes old packages).

## Host validation (not a device)

`tests/garden-blackarch/host/validate-rootfs.sh` on the archive, with the arm64
container run under qemu-user. **61 PASS, 0 FAIL** (container checks) plus:

- **SHA-256** matches the pin; **archive paths**: no absolute path, no `..`, no
  special file, no duplicate name, every hard-link target seen before its link.
- **Garden extractor gate** (`tests/garden-common/extractor/host-gate.sh`, the
  app's extraction policy with the JVM `FileOps`; not GNU tar, not proof of the
  APK's `AndroidFileOps`): 47 PASS, 0 FAIL, 33656 entries, 0 rejected, 33655
  paths equal to the independent manifest, 24103 regular files' contents verified,
  1027 hard-link groups (2848 names).
- **pacman -Dk**: "No database errors have been found". **pacman -Qk**: no package
  with missing files (139 packages). Without sync databases pacman warns that
  they do not exist; that is the shipped state.
- **Both keyrings through the app's own script**: the script `RootfsManager`
  generates for this edition (two keyrings, two signing keys) was run as shipped
  with real `pacman-key` and `gpg`: it ends `keyring-verified`, all five signing
  and trusted keys are fully valid, the revoked key is not valid.
- **Real repository sync**: `pacman -Sy` synchronised core, extra, alarm, aur and
  blackarch (4702 BlackArch packages); `blackarch.db.sig` verifies with the
  installation keyring against `F9A6E68A...`.
- **Signature-positive**: three libraries and a perl module (`perl-color-output`,
  `libtirpc-compat`, `mbelib`; `mbelib` is signed by the Arch Linux ARM key) were
  installed, each reported `Validated By : Signature`, removed, reinstalled and
  removed; `pacman -Dk` and `-Qk` clean after each step. None was run.
- **Signature-negative**, each compared with an untampered control that installs:
  a package altered after signing, a package signed by an untrusted key, a
  tampered database under the genuine signature, a repository package altered after
  signing, and an unsigned package in a repository were all refused.
- The shipped `pacman.conf` was unchanged by all of it and still passes the policy.

Limits: qemu-user cannot run pacman's seccomp download sandbox, so downloads ran with
`--disable-sandbox` (the image's `pacman.conf` is not changed by it); this is an
emulation limit. A full `pacman -Syu` and interrupted-transaction tests were not
part of this slice. `LocalFileSigLevel = Optional` is Arch's default and unchanged:
a local `pacman -U` of a file with **no** signature is accepted; a present, invalid
or untrusted signature is refused (tested). Repository installs always require one.

## Licences and corresponding source

Measured from the packages' own metadata in the archive: 139 packages; 95 carry a
copyleft licence (GPL, LGPL, MPL and similar); 7 declare a custom licence:
`blackarch-keyring` (`custom:unknown`), `iana-etc`, `krb5`, `libldap`, `lmdb`, `popt`,
`sudo`. The licence texts of the Arch Linux ARM packages ship inside the rootfs
under `/usr/share/licenses/`.

Corresponding source is collected for **every** package, not only the copyleft
ones, by `garden-blackarch/rootfs/collect-sources.py`:

- the 138 Arch Linux ARM rows are Rolling's already verified collection, reused after
  each archive's size and SHA-256 are re-checked against its row (a package or version
  Rolling did not collect would be UNRESOLVED, and the script would stop);
- `blackarch-keyring` is collected from the recipe pinned above: the PKGBUILD hash
  must equal the one in the package's `.BUILDINFO`, the source tarball
  (`https://www.blackarch.org/keyring/blackarch-keyring-20251011.tar.gz`) must match
  the recipe's sha512, and its detached signature must verify with a pinned BlackArch
  key.

Result: 139 rows, 0 unresolved, 122 files, `SOURCES.tsv` SHA-256
`1af7dc950867107335733198705319ad1d572432a8268e79605e55509db0b4d3`. Not published
and not uploaded anywhere. Not checked: that the keyring source archive is
byte-identical when collected twice.

**Unresolved licensing.** `blackarch-keyring` declares `custom:unknown`; its upstream
repository has no licence file and GitHub reports none; the recipe's header refers
to BlackArch's BSD-3-Clause `COPYING`, which is not stated to cover the keyring data.
It is public key material plus a hook and an install script. Whether it may be
redistributed in this archive is unconfirmed and is a **release gate**, together with the
trademark and naming review (`docs/garden/blackarch/DESIGN.md`). Redistribution of the
Arch Linux ARM packages follows Rolling's.

## Not verified

- Anything on a phone: extraction with `AndroidFileOps`, first run, `TERMINAL_READY`,
  background keyring setup, pacman under PRoot, HOME preservation, LAN on 7684.
- Kernel 4.14 (carried over from Garden).
- A full `pacman -Syu`, a second clean `-Syu`, interrupted transactions and stale locks
  on this image; `pacman -Qkk` classification.
- Reproducibility on another host.
- Licence of `blackarch-keyring`; BlackArch's position on the product name.
- Publication: no release asset exists, no immutable URL, no F-Droid metadata.
