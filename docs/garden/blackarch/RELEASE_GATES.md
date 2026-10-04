# BlackArch edition — release gates (2026-10-04)

**Status: publication STOPPED.** The device gate passed on QA builds
(SM-A165F, Android 16, kernel 6.12.38; scripts in `tests/garden-blackarch/`):
package manager 116/0, lifecycle and HOME 14/0, PRoot 0006/0007/0008 14/0,
optional setup hang/timeout/retry 18/0, LAN on 7684, final integrity and QA
cleanup. Kernel 4.14 (the 0007 withheld-pid path) is UNVERIFIED. But two
release gates do not close.
No rootfs release, app tag, GitHub release or F-Droid MR is made until both
do. The validated candidate rootfs stays unpublished and unchanged:
`thothterm-blackarch-aarch64-rootfs-45a791115213`, SHA-256
`45a79111521382fc4162d5642da360b7a78a1cc251a1e2efcf138bb41bc94ac9`,
199,874,013 bytes.

## Gate 1 — blackarch-keyring licence: BLOCKED

The rootfs ships one package from BlackArch: `blackarch-keyring
20251011-2` (signer `F9A6E68A…5079`). Every other package is Arch Linux ARM's,
each with its declared licence.

| Evidence | Finding |
|---|---|
| Package `.PKGINFO` | `license = custom:unknown` |
| Package contents | `usr/share/pacman/keyrings/blackarch.gpg` (21,158 B), `blackarch-trusted`, `blackarch-revoked`, `etc/pacman.d/hooks/blackarch-key.hook`, `.INSTALL` |
| Recipe (`PKGBUILD`, hook, install script), in `BlackArch/blackarch` | header "This file is part of BlackArch Linux … See COPYING"; that repository's COPYING is **BSD-3-Clause** |
| Payload source, `https://www.blackarch.org/keyring/blackarch-keyring-20251011.tar.gz` (from `BlackArch/blackarch-keyring`) | Makefile, `blackarch.gpg`, trusted/revoked lists, `packager-revoked/nrz.asc`; **no licence file**. GitHub API: `license: null`; repository root has no LICENSE/COPYING |
| blackarch.org (index, faq, downloads, guide, about) | no licence or trademark statement |

The recipe and hook are BSD-3-Clause. The keyring payload — the exported
public keys and the trust lists, which is what the rootfs actually carries —
has no licence grant anywhere we could find. Redistribution is therefore
**not established**, and `custom:unknown` is not treated as permission.

Ways forward (owner decision):

1. Ask the BlackArch maintainers to state a licence for
   `BlackArch/blackarch-keyring` (an issue on that repository). If they
   publish one that permits redistribution, this gate closes with that
   evidence and the already-validated rootfs can be published as is.
2. Stop shipping the keyring: the rootfs carries no BlackArch file, and on
   first run the app downloads `blackarch-keyring` from BlackArch's own
   mirror and verifies it against a signing-key fingerprint pinned in the
   source (BlackArch's own `strap.sh` bootstrap does the same). This needs a
   new rootfs and a new device gate; the current evidence would not carry
   over.

## Gate 2 — naming: the current name cannot be supported

- BlackArch publishes no trademark policy (blackarch.org, the BlackArch
  GitHub organisation). Nothing grants use of the name.
- The BSD-3-Clause COPYING it does publish says: "Neither the name of the
  BlackArch group nor the names of its contributors may be used to endorse or
  promote products derived from this software without specific prior written
  permission." A product titled "ThothTerm BlackArch" uses the name to promote
  a derived product.
- The name also contains "Arch"; the Arch Linux trademark policy covers
  combined marks (see `docs/branding/arch/TRADEMARK.md`).

Conclusion: before any public release the edition needs a neutral product
name (as with ThothTerm Trixie, Rolling and Resolute), with "BlackArch" used
only to describe what it runs and a non-affiliation notice. Package id
`com.thothterm.blackarch`, module and tags can keep the internal name.
The name itself is an owner decision.

## Shared fixes integrated after the device gate

Merged from master (security/garden-thorns): Garden Thorns tests (no
runtime change for Garden), `killHard()` also on the interrupted path and an
`isAlive()` check before SIGKILL (same behaviour the device gate measured),
store metadata per module, and reproducible native builds (talloc
`-ffile-prefix-map`, no build-id on CMake libraries, PRoot built with
`GIT=false`). The last changes bytes of `libtalloc.so.2`, `libproot.so` and
`libterm-system.so` but no code: the 116/0, lifecycle 14/0, PRoot 14/0,
optional setup 18/0 and LAN evidence stand. Because publication is stopped,
no device rerun was made for them.
