# Ubuntu productization audit

This audit is scoped to the known-good embedded Ubuntu → PRoot → PTY → Bash
foundation. Physical-device items remain acceptance work, not inferred passes.

## FIXED NOW

- Extraction percentage used incompatible units (entries divided by compressed
  bytes). It now uses consumed compressed bytes, updates at most once per
  percentage point, reaches 100 only after archive verification, survives
  activity recreation, and remains off the UI thread.
- The inherited `cd ~` default was genuinely written into the PTY after 500 ms.
  Ubuntu now has no implicit startup command; `--cwd=/home/thoth` is sufficient.
- Android active-network DNS servers now produce an atomic app-private resolver
  file, with IPv4/IPv6 support, count-only diagnostics, launch-time refresh,
  network-change refresh, and a narrow PRoot bind to `/etc/resolv.conf`.
- PRoot fake-root identity is coherent and disclosed: `root`/`#`, no fake sudo,
  `/home/thoth` retained as the persistent working home, and no Android root.
- Dedicated managed profile integration is idempotent, removes only the exact
  legacy prompt line, preserves user `.bashrc` content, and does not regenerate
  deleted skeleton files on later launches.
- `thothfetch`, one-per-window welcome control, responsive ASCII layout,
  edition prompt, Garden palette, Garden icon family, and brand source guidance
  replace prototype black/white identity.
- Both editions default to their already-bundled DejaVu Sans Mono 2.37. The
  fixed fractional cell renderer, wide/combining handling, Arabic shaping, and
  BiDi mapping were intentionally left unchanged.
- Setup listener replacement is lifecycle-safe, retries reset stale progress,
  stale staging is cleaned before a storage preflight, and managed runtime
  configuration is refreshed without re-extracting or replacing user files.
- Ubuntu V1 no longer requests legacy external storage, superuser, or protected
  dump permissions and no longer prompts for an out-of-scope shared-storage
  feature.

## NEXT MILESTONE

- Complete the physical checklist, especially live DNS changes, `apt update`, a
  tiny dpkg install/remove cycle, signals, rotation/IME resize, multi-session
  isolation, and persistence after force-stop.
- Design a first-class non-root session plus explicit admin-window workflow if
  product research shows it is worth the added process/UI surface. Do not use a
  nested PRoot or pretend `sudo` elevates.
- Add explicit corrupted-rootfs diagnosis/rebuild UX and a versioned migration
  policy before changing the embedded image or schema.
- Exercise interrupted extraction and low-storage behavior with Android storage
  quotas/OEM cleaners; the current implementation fails closed, cleans staging,
  and retains Retry/View Logs.
- Resolve or baseline the inherited predictive-back lint error and review the
  legacy exported terminal-service/file-picker surfaces as a focused security
  milestone rather than changing compatibility contracts incidentally.
- Validate package-maintainer edge cases involving hardlinks, `/dev/shm`, and
  services that assume systemd; publish a concise compatibility note from real
  package tests.
- Define an app-data upgrade/export policy. Android backup is intentionally
  disabled, so uninstall remains destructive and must be communicated before
  a public release.

## OPTIONAL FUTURE

- A small in-terminal network status command and user-facing DNS hint for
  offline sessions, without exposing resolver addresses.
- Additional curated font choices only if physical testing finds a material
  gap in DejaVu coverage/density; no font marketplace or ligatures by default.
- Automated screenshot/golden tests for icon masks and narrow/wide welcome
  layouts once the Android UI test harness is established.

## NOT A PROBLEM

- Absence of systemd is expected for this PRoot product and is not a boot bug.
- Absence of sudo is intentional while the shell is already PRoot fake-root.
- `/tmp` cannot preserve a host sticky bit through the hardened extraction
  abstraction; it is writable inside one app-private UID boundary.
- Application-layer duplication between `term/` and `term-ubuntu/` remains
  deliberate. This pass shared only the existing renderer behavior and brand
  sources; a broad module refactor would add regression risk.
- The official Canonical tarball remains byte-for-byte pinned and unmodified.
