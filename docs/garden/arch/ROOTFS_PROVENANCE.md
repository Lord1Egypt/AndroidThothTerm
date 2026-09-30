# ThothTerm Rolling AArch64 rootfs provenance

This is the September 30, 2026 release candidate for ThothTerm Rolling
0.1.0/100. It has not been published. If publication is more than roughly
24 hours after this capture, generate, verify, and pin a new rootfs before
release.

## Captured inputs

| Item | Exact value |
| --- | --- |
| Official upstream image | `http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz` |
| Last-Modified | `Wed, 05 Aug 2026 12:41:36 GMT` |
| Upstream compressed size | `829367415` bytes |
| Upstream SHA-256 | `42a4eeaa038994ffd31fa173256ef2f0ef511358eeb41b9ea1f8626391b9b319` |
| Published upstream MD5 | `23eec86365b24f7913c403e8f4e8719b` |
| Detached signature | Valid signature from Arch Linux ARM Build System key `68B3537F39A313B3E574D06777193F152BDBE6A6`, made 2026-08-05 |
| Pinned verifier keyring SHA-256 | `50a08f82ce3cd524a552da4bfa37f3e04b2c9da468e2fce5783474b58a34c518` |
| Repository sync start | `2026-09-30T07:50:50Z` |
| Repository sync finish | `2026-09-30T07:52:09Z` |
| Mirror | `http://mirror.archlinuxarm.org/$arch/$repo` |
| Signed packages captured | `145` |
| Captured-input checksums file SHA-256 | `08b69ce5465578e350f16cf503cf661b1d7bab2a650e7b0ec086cf1646253060` |
| Builder image | `thothterm-alarm-upstream:42a4eeaa0389`, derived from the signed upstream image |
| Source-collector image | `archlinux:base-devel@sha256:8745817f349ed24373341ddb92776209eeec3f0364ea48f7f645ac5800d30a50` |

`garden-arch/rootfs/build-rootfs.sh capture` recorded the URL, HTTP metadata,
image digest, detached signature, pinned repository databases, signed package
files, and per-input SHA-256 values in
`arch-rootfs-work/inputs-codex-final-20260930/`. The transport URL is HTTP;
the detached signature and pinned key provide image authenticity. Pacman
signature checking remained enabled during the package capture and build.

## Build and normalization

The builder script revision is
`1d5d1473b783d2b4cda59e0bbcd72b3b34d7749b`; the exact script SHA-256
is `3e29336c520a8db069bc50716a916f0f0e81751394dfa4f8557170aa640173e5`.
The offline build used `--network none` and capture epoch `1790754729`.
Builder tools were pacman `7.1.0.r9.g54d9411-2` and
archlinuxarm-keyring `20240419-2`.

The builder upgraded itself from the signed capture and installed a fresh
`base archlinuxarm-keyring sudo ca-certificates curl` target. The target has
138 installed packages. Its second `pacman -Su` reported “there is nothing
to do”; `pacman -Dk` found no database errors and `pacman -Qk` found no
missing files. The installed package list is in the local
`thothterm-arch-aarch64-rootfs-03a4c669ed6f.packages.tsv` build artifact.

For Android's PRoot environment the image enables only
`DisableSandboxFilesystem`, because the phone kernel lacks Landlock.
`DisableSandboxSyscalls`, `DownloadUser = alpm`, and
`SigLevel = Required DatabaseOptional` remain in effect. The archive keeps
a signed archlinuxarm-keyring package for first-run trust verification. It
ships no pacman private key, pacman keyring home, sync databases, package
cache, lock, log, machine ID, SSH host keys, or populated user HOME.
Passwords are locked. The build clamps generated timestamps and pacman
install dates to the capture epoch, sorts tar entries, uses numeric owners,
and compresses with `gzip -9 -n`.

## Output and reproducibility

| Item | Exact value |
| --- | --- |
| Archive | `thothterm-arch-aarch64-rootfs-03a4c669ed6f.tar.gz` |
| Archive SHA-256 | `03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1` |
| Compressed size | `199826020` bytes |
| Uncompressed tar size | `674723840` bytes |
| Architecture | `aarch64` |
| Package state | Internally consistent as of `2026-09-30T07:52:09Z` |

Two separate offline builds from the same captured inputs, in
`arch-rootfs-work/out-codex-final-20260930-a/` and `-b/`, produced archives
that `cmp` found byte identical. Their package TSV, manifest, and scan files
are also identical. The fresh archive passed the complete physical disposable
rootfs gate on the Galaxy A16: **78 PASS, 0 FAIL**, including signed
keyring provisioning, two no-op final updates, install/remove/reinstall,
interrupted transaction repair, DNS/TLS, library loading, and upgrading an
old image. Log: `/tmp/codex-arch-gate-fresh-final.log` on the local workstation.

## Corresponding sources

The local `arch-rootfs-work/sources-codex-final/` collection contains one
verified source row for each of the 138 installed binaries, representing
120 unique source archives. `SOURCES.tsv` SHA-256 is
`0b55a1198a6bd0881f6badfd2fca3104ae1ddada0463913e59254bd6e811efee`.
All archive sizes and SHA-256 values were checked when the collection was
assembled. The 135 unchanged binary versions reuse source archives from a
clean recollection using the corrected AArch64 selector; coreutils 9.12-2
and pcre2 10.49-1 were collected from the final captured binaries. The
remaining binary is `ca-certificates-mozilla`, built from NSS 3.130-1:
the pinned Arch Linux ARM recipe lists three local-file BLAKE2 checksums in
the wrong order. Each source file matched a published checksum exactly, and
`makepkg --verifysource` passed using a temporary corrected order. Its source
archive includes both the unmodified upstream recipe and the exact verified
checksum-order correction. No checksum value was weakened or skipped.

The corresponding rootfs archive and source archives are local review
artifacts. Do not publish them, create the rootfs tag, or use them for an
app release until the remaining final APK and phone gates pass and the
capture is fresh at publication time.
