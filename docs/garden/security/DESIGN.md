# ThothTerm Security — design record

Status: **release candidate** for 0.1.0 / 100. The edition was developed as
"ThothTerm BlackArch" and never released; it was migrated before any release
to the neutral name below, and its rootfs was redesigned so that no third-party
trust material is redistributed. The earlier history is preserved on the branch
`feature/blackarch-v0.1.0`; nothing under that name was tagged, published or
submitted.

## Identity

| | |
|---|---|
| Display name | ThothTerm Security |
| Module | `garden-security` |
| Package id | `com.thothterm.security` (protected like every production id) |
| Version | 0.1.0 / versionCode 100, literal in `garden-security/build.gradle` so F-Droid's `checkupdates` reads it statically |
| LAN port | 7684 (`docs/garden/PORTS.md`) |
| Tags | source `security-v0.1.0`; the rootfs is Rolling's (`arch-rootfs-aarch64-03a4c669ed6f`) |

## Naming

The product is not named after the third-party repository it can connect to.
Compatibility with a project does not give naming or branding rights
(`docs/security/THORNS.md`). Rules, enforced by `SecurityEditionTest`:

- "BlackArch" is never the app name, label, title, notification text, launcher
  text, Gradle identity, package id or tag, and never appears next to
  "Edition".
- It appears only as the factual name of the optional repository: in the menu
  entry "Enable BlackArch repository", the consent screen, the store
  description, the about text, the pinned URLs in `distro.properties`, and this
  documentation.
- Every user-visible text that mentions it carries the non-affiliation notice.
  No logo, wordmark or endorsement language of that project, of Arch Linux or of
  Arch Linux ARM is used. The artwork is ThothTerm's own Garden artwork; its
  sheet reads "SECURITY EDITION" (`docs/branding/security/source/README.md`).

BlackArch publishes no trademark policy that was found on 2026-10-03; the
position was UNVERIFIED, and the neutral name makes the question moot.

## Why no keyring is bundled

The earlier candidate installed `blackarch-keyring` into the rootfs. That
package carries no licence file and declares `custom:unknown`
(`BlackArch/blackarch-keyring` has no `LICENSE`), so there is no established
right to redistribute it, and a downloadable image or APK would be doing so.
Instead:

- the rootfs is Rolling's plain Arch Linux ARM userland, with no BlackArch
  package, key or configuration;
- the app fetches the keyring from BlackArch's own infrastructure at run time,
  only after explicit consent, and verifies it before use (below).

## Optional repository setup (runtime)

Menu: "Enable BlackArch repository" (visible only in this edition, while the
repository is not enabled, or after a failure). It opens a consent screen that
names the host, the size, what is checked and that skipping it costs nothing.
Nothing is downloaded before the positive button. Declining changes nothing.

On consent, `RootfsManager.enableExtraRepo()` queues one background run
(single-flight, never the main thread, `OptionalSetup` status, `EXTRA_REPO_*`
log markers). The terminal is never gated by it: it is `TERMINAL_READY` long
before, and works with no network, with the repository unreachable, or after
any failure. The run:

1. creates the installation's own pacman keyring if the first-run setup has not
   yet (the normal first-run step);
2. downloads the keyring package and its detached signature over HTTPS from
   `blackarch.org` (redirects followed manually, plain HTTP refused), accepting
   only the exact pinned size, and only if the SHA-256 equals the pin
   (`RootfsDownloader.fetchPinned`); nothing is written to the guest before
   both pass;
3. hands them to the guest, base64 in a staging directory, and runs
   `ExtraRepoScript`:
   - sizes and SHA-256 again in the guest;
   - unpacks only `blackarch.gpg`, `blackarch-trusted` and `blackarch-revoked`
     into scratch space; the trusted list must be exactly the four pinned
     fingerprints at full trust and the revoked list exactly the pinned one,
     or the script stops before importing anything;
   - `pacman-key --populate-from <scratch> blackarch`, then proves from the
     installation's own keyring that each trusted key is imported, valid and
     fully trusted, that the revoked key is not trusted, and that the package
     signature verifies against the pinned signer (`F9A6E68A…5079`) with full
     trust;
   - installs the package with pacman (`LocalFileSigLevel = Required`, no
     scriptlet), which checks the signature a second time;
   - appends one marked `[blackarch]` section (`Server = https://blackarch.org/
     blackarch/$repo/os/$arch`, no SigLevel of its own) and proves with
     `pacman-conf` that signatures are still required everywhere and none is
     relaxed;
   - `pacman -Sy` (the rootfs ships no databases) and requires a non-empty
     package list.

Never: `SigLevel = Never`, `TrustAll`, a global or per-repository downgrade,
`curl | sh`, `strap.sh`, any remote script, `--recv-keys` or a keyserver. The
configuration is touched only after the keys are proven. A timeout is a
`killHard()` (SIGKILL), as for every provisioning command. HOME is never
touched, and a failure never reinstalls the rootfs. Re-running is idempotent
(one section, one package) and is the retry. Offline, the run fails at the
network step with the keys proven and the section in place; the next run
completes it.

### Pins (this release)

All in `garden-security/src/main/assets/garden/distro.properties`; re-measured
on 2026-10-04 against the live repository: package `blackarch-keyring`
20251011-2, 18445 bytes, SHA-256 `d40c1301…ee07`; signature 566 bytes, SHA-256
`c2e21fcd…499d`; trusted `4345771566D76038C7FEB43863EC0ADBEA87E4E3`,
`8F9A9793CB8591147C2EC70566E0CDBD1E01F333`,
`A0917C4147A37007CB54C1CFD295AA940EFDDF62`,
`F9A6E68A711354D84A9B91637533BAFE69A25079` (signer); revoked
`5E210889BBB5C48500E0C4F9C75E985FF8B993B4`. Arch-style repositories keep only
the latest version, so a new upstream keyring makes the download fail closed
with the pin mismatch until a release reviews and updates the pins. That is the
intended behaviour, not a bug.

## Upstream facts (measured 2026-10-03, keyring facts re-measured 2026-10-04)

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
  not run it; the optional setup uses pinned bytes (below).

### What the aarch64 catalog can honestly be said to offer

With the keyring installed by the old BlackArch-bundled candidate, on the Rolling rootfs in an aarch64 container (qemu, `--disable-sandbox`, not
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

- Rootfs: Rolling's (`garden-arch/rootfs`, byte for byte; see
  `ROOTFS_PROVENANCE.md`). No security tool is installed.
- Shared code: `garden-common`, with one additive feature, the optional
  repository (`DistroInfo` `extraRepo*` data, `ExtraRepoScript`,
  `RootfsManager.enableExtraRepo`, the menu entry and its consent screen), which
  only an edition that declares `extraRepo` ever shows.
- Not offered anywhere: running a security tool automatically, bundling one, or
  promising that one works.

## Not verified

Kernel 4.14 and PRoot patch 0007's withheld-pid path remain UNVERIFIED (no such
device here; the shared runtime is identical to the other editions').
