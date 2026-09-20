# Distro porting checklist and acceptance matrix

ThothTerm Ubuntu is the reference implementation. This document is what a new
variant — Kali, Arch, AlmaLinux or any other — has to satisfy before it can be
called working.

Read `ANDROID_PROOT_QUIRKS.md` first. Its **ENGINE** entries are already settled
and must not be re-derived per distro; only its **DISTRO** entries are open
choices.

---

## 1. Shared engine vs. distro policy

The split matters, because getting it wrong is how a distro port turns into an
engine fork.

| Concern | Owner | Notes |
|---|---|---|
| PRoot invocation, `--rootfs` canonicalisation, `--link2symlink` | **Engine** | Identical for every distro. |
| Rootfs extraction (hard links, symlinks, modes, setuid) | **Engine** | Format-agnostic. |
| Mount namespace and path-spelling rules | **Engine** | See quirks §1–§6. |
| Terminal geometry chain, zoom, PTY resize | **Engine** | Never distro-specific. |
| Foreground service, notification, lifecycle | **Engine** | |
| Managed shell block: idempotent, delimited, additive | **Engine** | Mechanism is fixed. |
| PATH: existence-gated, `$HOME`-relative, prepend-only | **Engine** | |
| Which user-local bin directories to probe | **Distro** | Ubuntu probes `.local/bin`, `bin`, `.cargo/bin`, `.foundry/bin`, `go/bin`, `.bun/bin`, `.deno/bin`, `.npm-global/bin`. |
| Image source, version, checksum | **Distro** | |
| Package manager and its acceptance commands | **Distro** | `apt`/`dpkg`, `pacman`, `dnf`, … |
| Privilege escalation tool and its configuration | **Distro** | `sudo`, `doas`, … |
| Default user name, UID/GID, home path | **Distro** | Ubuntu uses `thoth`, `/home/thoth`. |
| Shell startup file layout | **Distro** | `.bashrc` vs `.zshrc` vs `.profile`. |
| Welcome banner content | **Distro** | The adaptive mechanism is engine; the text is not. |

**Rule:** if a fix would have to be repeated in every distro, it belongs in the
engine. If it would be wrong in another distro, it belongs in distro policy.

---

## 2. Porting checklist

### 2.1 Image

- [ ] Official upstream URL for a **base/minimal** image, arm64.
- [ ] Pin the exact version and SHA-256; staging script **fails closed**.
- [ ] Record compressed and uncompressed size.
- [ ] Confirm the image is redistributable, and record per-package license
      handling in `THIRD_PARTY_NOTICES.md`.
- [ ] Asset filename must not end in `.gz` (Android expands `*.gz` assets).

### 2.2 Extraction

- [ ] Hard links preserved.
- [ ] Symlinks preserved.
- [ ] Permission bits, including setuid, preserved.
- [ ] Extraction is resumable/idempotent and never half-writes a usable-looking
      rootfs.
- [ ] Path-length and traversal safety on every entry.

### 2.3 Guest identity

- [ ] Unprivileged user exists with a stable UID/GID and a real home.
- [ ] Android supplementary GIDs are named in the guest group database.
- [ ] `/etc/hosts` managed.
- [ ] `/etc/resolv.conf` bind-mounted from the Android side.
- [ ] Locale set so tools do not emit locale warnings.

### 2.4 Privileges

- [ ] The distro's **genuine** escalation tool, not a wrapper.
- [ ] Provisioned offline from pinned packages.
- [ ] Configuration valid per the distro's own validator (`visudo -c`).
- [ ] No permissive PAM bypass.

### 2.5 Shell integration

- [ ] Managed block inserted exactly once, between begin/end markers.
- [ ] Idempotent across upgrades.
- [ ] Never truncates or reorders user content.
- [ ] PATH additions existence-gated, `$HOME`-relative, prepend-only, exported.
- [ ] Non-interactive children inherit the environment.

### 2.6 Lifecycle and terminal

These are engine behaviours; a port only has to confirm nothing regressed.

- [ ] `START_NOT_STICKY` intact.
- [ ] Notification permission requested once; refusal does not crash.
- [ ] Geometry chain intact; `stty size` matches the view at several font sizes.

---

## 3. Distro acceptance matrix

A variant is **not** shippable until every row passes on a physical device. Fill
in one column per distro; "n/a" needs a written reason.

| # | Check | How it is proven | Ubuntu 26.04 | Kali | Arch | AlmaLinux |
|---|---|---|---|---|---|---|
| 1 | App launches, rootfs extracts | first run completes | PASS | | | |
| 2 | Existing rootfs preserved on upgrade | `adb install -r`, home unchanged | PASS | | | |
| 3 | Welcome banner adapts to width | narrow and wide render | PASS | | | |
| 4 | Primary shell | `bash -c 'echo ok'` | PASS | | | |
| 5 | POSIX shell | `sh -c 'echo ok'` | PASS | | | |
| 6 | Secondary shell | `dash`/`ash` equivalent | PASS | | | |
| 7 | Identity | `whoami` = unprivileged user | PASS (`thoth`) | | | |
| 8 | HOME correct | `$HOME` matches the account | PASS | | | |
| 9 | Non-login PATH sane | `echo $PATH` has system dirs | PASS | | | |
| 10 | User-local PATH | CLI in `~/.local/bin` resolves | PASS | | | |
| 11 | PATH survives restart | close, reopen, resolve again | PASS | | | |
| 12 | PATH inherited | `sh -c 'command -v <cli>'` | PASS | | | |
| 13 | Escalation, non-interactive | `sudo -n true` exit 0 | PASS | | | |
| 14 | Escalation identity | `sudo whoami` = root | PASS | | | |
| 15 | Package index update | `apt-get update` | PASS | | | |
| 16 | Package install | install a small package | PASS | | | |
| 17 | Package remove | remove it again | PASS | | | |
| 18 | Package DB consistent | `dpkg --audit` clean | PASS | | | |
| 19 | DNS | resolve a public name | PASS | | | |
| 20 | TLS | HTTPS fetch returns 200, verify OK | PASS | | | |
| 21 | Hard-link semantics | create→link→read→exec→restart→read | PASS | | | |
| 22 | l2s census | 0 broken links | PASS (11/0) | | | |
| 23 | PTY geometry | `stty size` matches the view | PASS | | | |
| 24 | Zoom | several sizes, PTY tracks each | PASS | | | |
| 25 | Rotation | portrait ↔ landscape round-trip | PASS | | | |
| 26 | IME | shown/hidden both correct | PASS | | | |
| 27 | Multi-session | two sessions, switch, both live | PASS | | | |
| 28 | Notification | one ongoing, stable id, clears on stop | PASS | | | |
| 29 | No sticky restart | no Android 16 restart crash | PASS | | | |
| 30 | No crash / ANR | logcat clean across the run | PASS | | | |
| 31 | Upgrade persistence | home, rootfs, prefs intact | PASS | | | |
| 32 | Provenance recorded | every binary hashed and licensed | PASS | | | |

---

## 4. Anti-goals

Things a port must **not** do, each learned the hard way:

- Do not fake the escalation tool.
- Do not inject every plausible directory into PATH.
- Do not rewrite user dotfiles.
- Do not "repair" `/etc/ssl/certs` hash links.
- Do not change PTY geometry to make one program's output fit.
- Do not replace, alias or patch an upstream binary to fix its presentation;
  add an optional helper beside it.
- Do not revert `START_NOT_STICKY` to keep a notification alive.
- Do not reset or re-extract a rootfs, or delete a home directory, without a
  proven invariant that requires it.
