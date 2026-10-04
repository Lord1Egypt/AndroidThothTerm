# Garden editions, releases and F-Droid status

The apps the Garden produces, one module each, one application id each so they
install side by side. Updated whenever a release is tagged or an F-Droid merge
request changes state; the tags themselves are immutable (never moved, always
superseded).

## Supported editions

| Product | Package | Module | Base | LAN port | Tag prefix |
|---|---|---|---|---|---|
| ThothTerm Terminal Emulator | `com.thothterm` | `term` | none (Android shell) | — | `terminal-v*` |
| ThothTerm Resolute | `com.thothterm.ubuntu` | `term-ubuntu` | Ubuntu 26.04.1 base (downloaded from cdimage.ubuntu.com, SHA-256 pinned) | 7681 | `ubuntu-v*` |
| ThothTerm Trixie | `com.thothterm.debian` | `garden-debian` | Debian GNU/Linux 13 (trixie) rootfs, `debian-rootfs-trixie-arm64-f6520ff1c6ee` | 7682 | `trixie-v*` |
| ThothTerm Rolling | `com.thothterm.arch` | `garden-arch` | Arch Linux ARM rootfs, `arch-rootfs-aarch64-03a4c669ed6f` | 7683 | `arch-v*` |
| ThothTerm Security | `com.thothterm.security` | `garden-security` | Rolling's Arch Linux ARM rootfs (same archive) | 7684 | `security-v*` |

`ThothTerm Resolute` is the former ThothTerm Ubuntu (Canonical's policy bars
the trademark in a software title; the package id did not change).

### ThothTerm Security

The neutral, security-focused edition. It is a plain, minimal Arch Linux ARM
environment (Rolling's userland, byte for byte) with **no security tool
installed or bundled**. It can optionally enable the third-party BlackArch
repository, only after a consent screen; the repository's keyring is *not*
redistributed: the app downloads exactly one pinned artifact from the project's
own site over HTTPS at run time, compares its trust lists with pinned ones,
proves the keys from the installation's own keyring and keeps signature
checking required everywhere. The terminal opens first, long before and
independently of that setup. Details: `docs/garden/security/DESIGN.md`; rules:
`docs/security/THORNS.md`, rule 10.

## Release matrix

| Product | Version | Tag | Commit | State |
|---|---|---|---|---|
| Terminal | 1.4.1 / 10401 | `terminal-v1.4.1` | `760d54e` | released |
| Resolute | 0.3.2 / 302 | `ubuntu-v0.3.2` | `760d54e` | released |
| Trixie | 0.2.2 / 202 | `trixie-v0.2.2` | `760d54e` | released |
| Rolling | 0.1.1 / 101 | `arch-v0.1.1` | `760d54e` | released |
| Security | 0.1.0 / 100 | `security-v0.1.0` | `4cbf7a8` | released |

## F-Droid (fdroiddata merge requests)

All are Squash on, auto-merge off, latest `Builds` entry only; merging is the
F-Droid maintainers' decision.

| Product | MR | State |
|---|---|---|
| Resolute 0.3.2 | !49556 | open, ready, pipeline green |
| Trixie 0.2.2 | !50342 | open, ready, pipeline green |
| Rolling 0.1.1 | !51045 | open, ready, pipeline green; upstream signing (`Binaries`, `AllowedAPKSigningKeys` 3f85da4f…f679; `docs/RELEASE_SIGNING.md`) |
| Security 0.1.0 | !51185 | open, ready, pipeline green; upstream signing (`Binaries`, `AllowedAPKSigningKeys` 24dd0902…e3c6) |
