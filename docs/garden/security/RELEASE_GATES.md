# ThothTerm Security — release gates (resolved 2026-10-04)

The edition was prepared as "ThothTerm BlackArch" and stopped on 2026-10-03 at
two gates. Both are closed by changing the product, not by arguing the facts.

## Gate 1 — third-party keyring licence: CLOSED by not redistributing it

The earlier rootfs carried `blackarch-keyring 20251011-2`, whose payload (the
exported public keys and trust lists) has no licence grant anywhere:
`.PKGINFO` says `license = custom:unknown`, `BlackArch/blackarch-keyring` has
no `LICENSE`, and blackarch.org states no licence. Redistribution was never
established, so that candidate rootfs (`thothterm-blackarch-aarch64-rootfs-
45a791115213`, SHA-256 `45a79111…4ac9`) is retired and is **not published**.

Now: no BlackArch file is in the rootfs, the APK, a release asset or the source
tree (`SecurityEditionTest.noThirdPartyKeyringOrRepositoryPayloadIsShipped`;
`container-checks.sh` section D on the real archive). The app fetches the
keyring from BlackArch's own site at run time, after consent, and verifies it
(`DESIGN.md`). The git history of the old branch still contains the keyring's
public key file (`blackarch.gpg`) from the earlier work; that branch is kept for
history, is not merged into master, and is flagged in the report for the owner
to decide whether to remove its remote ref.

## Gate 2 — naming: CLOSED by the neutral name

BlackArch publishes no trademark policy; its BSD-3-Clause COPYING forbids using
the group's name to promote derived products, and "BlackArch" contains "Arch"
(`docs/branding/arch/TRADEMARK.md`). The product is **ThothTerm Security**
(`com.thothterm.security`, `garden-security`, `security-v0.1.0`). "BlackArch"
remains only as the factual name of the optional repository
(`DESIGN.md`, "Naming"; `docs/security/THORNS.md`, rule 10). The unreleased
`com.thothterm.blackarch` identifier was dropped with no compatibility step.

## Evidence for 0.1.0 (Samsung SM-A165F, Android 16, kernel 6.12.38, arm64)

Isolated QA builds only (`com.thothterm.security.qa.*`, installed with
`--no-incremental`), source at the release candidate. Scripts in
`tests/garden-security/`; evidence files in the round's directory outside git.

| Category | Result |
|---|---|
| First run (F-Droid flavour): consent, pinned download, extraction, terminal | PASS; TERMINAL_READY ~55 s, optional setup afterwards, 0 ANR |
| First run (Full flavour, embedded rootfs) | PASS; TERMINAL_READY ~28 s |
| Package manager (Rolling base cases + 21 optional-repository cases, unmodified app script) | 104 PASS / 0 FAIL |
| Optional repository through the app UI: consent text, declining, no network, hung command → SIGKILL, retry, repeated, restart | 49 PASS / 0 FAIL |
| Optional setup hang/timeout/retry (keyring) | 18 PASS / 0 FAIL |
| Lifecycle and HOME (restart, missing library, lost state, damaged rootfs, interrupted reinstall) | 14 PASS / 0 FAIL |
| PRoot 0006 / 0007 / 0008 | 14 PASS / 0 FAIL |
| LAN Mode on 7684: bind, PIN pairing, protocol terminal, exact-byte upload, foreign Origin/Host, cancel, sign-out, stop, no staging leftovers, listener removed | all PASS |
| Minified (R8) release build, QA id: first run and the repository UI gate | PASS |
| Protected apps (`com.thothterm*`) and PocketClaw | install records unchanged |

Host: container validation of the real archive and of the script, online and
offline (`validate-rootfs.sh`): the base ships nothing third-party; every
refusal (changed package, changed signature, truncation, wrong pinned trust
list, wrong signer) leaves the guest untouched; success, idempotence and offline
failure behave as specified.

Not verified: kernel 4.14 and PRoot 0007's withheld-pid path (this kernel reports
fork pids, the recovery was not exercised); a real browser rendering of the LAN
page (the protocol contract is tested; the page is shared with the other
editions). The F-Droid review itself and the maintainers' merge are not ours.
