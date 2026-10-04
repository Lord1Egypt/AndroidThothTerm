# Garden Thorns

The security architecture contract for every ThothTerm app: the four Garden
editions (Resolute, Trixie, Rolling, Security) and the regular Terminal. The
Garden is the flower; the Thorns are the hardening that protects it.
A change that breaks a rule here needs a written reason in this file, in the
same commit, and a test that pins the new behaviour.

Report a vulnerability privately through GitHub's "Report a vulnerability"
(Security tab), not in a public issue.

## The rules

### 1. Export as little as possible

An Android component that is exported can be started or bound by every app on
the device, with whatever Intent that app builds. Each one is an entry point
for untrusted code.

- A Garden edition and ThothTerm Ubuntu export **exactly one** component of
  their own: the launcher activity. The terminal activity, the terminal
  service, LAN Mode, settings, logs and the window list are all
  `android:exported="false"`. The only other exported component in the merged
  manifest is androidx's profile-installer receiver, which requires
  `android.permission.DUMP` (held by the shell and the system only).
- No custom permission, no `activity-alias`, no share target, no shortcut
  activity, no bindable service, no `<queries>` entry naming another app's
  package as a trusted add-on.
- The regular Terminal keeps its documented legacy integrations (window
  opening, "Term here", `RUN_SCRIPT`, encrypted shortcuts, the `ITerminal`
  binding) for compatibility. That list is pinned; adding to it is a design
  change under this file, not a manifest edit.
- Every manifest, of every flavour, is checked against
  `tests/security/policy/<package>.json` from the built APK
  (`tests/security/exported_components.py`, run by
  `tests/garden-common/release/verify-apks.sh`), and from source by each
  module's `ExportedSurfaceTest` / `IntegrationSurfaceTest`.

### 2. External Intent data is untrusted

Extras, data URIs, `ClipData`, file paths, MIME types, package names and
labels in an Intent come from whichever app sent it. Treat them like network
input:

- decide from the data's *type and structure*, never by matching text;
- a file path from an Intent may name anything and contain any byte except
  `NUL` and `/` in a component;
- a package name proves nothing about who wrote the app: anyone can install an
  app under any unused name. Trust another app only by its signing
  certificate (`PackageManager.checkSignatures(...) == SIGNATURE_MATCH`), and
  only if the integration needs trust at all.

### 3. Never type untrusted data into an interactive terminal

Anything written to a PTY master is keyboard input. The line discipline, the
shell's line editor and any program in the foreground act on it, including
characters that are not printable.

- Nothing that came from outside the app is ever written to a PTY as a
  command, a path, an argument or a "convenience" prefix.
- Where outside data must influence a session, use a **structural** channel:
  the process's working directory (`createSubprocess(..., cwd)`), its argv or
  its environment — set before the shell starts, never typed into it.
- ThothTerm Ubuntu and the Garden editions have no "initial command" at all:
  `ShellTermSession` cannot be given text to type (pinned by
  `ExportedSurfaceTest.sessionsTakeNoInitialInput`).
- What the *user* types, and the user's own startup settings in the regular
  Terminal, are the user's input and are fine.

### 4. Shell escaping is not terminal sanitization

Quoting a string for `sh`/`bash` protects it from the shell's *parser*. It
does nothing about the terminal driver or the line editor, which see the
characters first. Escaping is therefore never a defence for rule 3.
Rejecting control characters is welcome as defence in depth, but it is never
the only protection when the input can simply not be typed at all.

### 5. Filesystem and path boundaries

- Resolve, then check: a path an outsider supplies (browser, document
  provider, Intent) is checked component by component (`UploadNames`), never
  normalized into something else, and must stay inside the intended root.
- Never follow a link that already exists in a target directory; never open
  an existing name for writing; publish with `renameat2(RENAME_NOREPLACE)`.
- Operate on a held descriptor (`O_PATH | O_NOFOLLOW`, `fchmod`), not on a
  path that can be swapped between the check and the use.
- The rootfs, the PRoot runtime and the app's private files are app-private;
  there is no shared-storage bridge in a Garden edition.

### 6. LAN Mode boundaries

LAN Mode is off by default and binds only a private (RFC 1918) Wi-Fi or
Ethernet address.

- Pairing: a one-time 6-digit PIN shown on the phone, valid 2 minutes, 5
  attempts, then lockout.
- After pairing the browser holds a 256-bit random token. It travels in
  `Sec-WebSocket-Protocol` or an `Authorization` header, never in a URL, and is
  compared in constant time.
- Every request must carry the exact expected `Host` and, for browser
  requests, the exact expected `Origin`; anything else is refused before the
  body is read.
- A browser acts only on its own terminals and uploads. Each browser terminal
  is its own PTY, never a window of the phone.
- It is plain HTTP on a local network. The page and the phone say so;
  authentication decides who may act, it does not hide the traffic.

### 7. Upload staging and exact bytes

- Uploads only add. No download, delete, rename, extract, execute or chmod +x.
- Every upload is staged in `.thothterm-upload-<random>` inside the target,
  journalled first, `fsync`ed, then moved into place in one step. Cancel,
  failure, disconnect, sign-out, LAN off, Exit and the next start after a crash
  all remove staging.
- The bytes written are exactly the bytes received: no transcoding, no newline
  conversion, no normalization of names. Device gates compare SHA-256.
- Storage is checked before and during an upload; `ENOSPC` fails cleanly.

### 8. Fail closed

- A failed check refuses; it never falls through to the permissive path.
- A server refuses a bad peer and keeps serving the good ones: refusing one
  connection must not stop the service for everyone.
- A timeout must actually stop the work it timed out. On Android,
  `Process.destroyForcibly()` sends only SIGTERM, which PRoot survives while a
  traced command hangs; provisioning timeouts escalate to SIGKILL
  (`RootfsManager.killHard`).
- Logs never carry tokens, PINs, file names, paths or file contents.

### 9. Every vulnerability fix gets a permanent regression test

A fix is not done until a test fails on the vulnerable code and passes on the
fixed code, and that test runs in the normal unit-test or release-check path.
Tests are never weakened to make a release pass.

### 10. Third-party trust material and names

- **Do not redistribute a third-party trust or keyring bundle without
  established redistribution rights.** A keyring package with no licence file
  (`custom:unknown`) is not ours to ship in a rootfs, an APK, a release asset or
  a source tree, however convenient. Neither is a copy of its key file.
- **Prefer explicit, verified runtime acquisition** when redistribution rights
  are unclear and fetching is technically and policy-wise appropriate: ask the
  user first, fetch over HTTPS from the project's own site, accept exactly one
  pinned artifact (size and SHA-256, plus its signature's), compare the trust
  lists it carries with pinned ones *before* importing anything, prove the
  result from the installation's own keyring, and fail closed. Never lower a
  signature level, never run a script from the network.
- **Compatibility with a project is not a right to its name or look.** A
  product is not named after the project it interoperates with, and uses none of
  its logos, wordmarks or endorsement language. Interoperability may be named
  factually ("Enable BlackArch repository", pinned URLs, a non-affiliation
  notice); that naming stays distinct from the product identity
  (`docs/garden/DISTRO_BRANDING_CHECKLIST.md`, section 0).
- When a package manager's repository keeps only the latest keyring, a pinned
  release goes stale by design: the download then fails closed until a release
  reviews and updates the pins.

## Current inventory

| App | Package | Exported components |
|---|---|---|
| ThothTerm Resolute | `com.thothterm.ubuntu` | launcher `UbuntuSetupActivity`; androidx profile installer (DUMP) |
| ThothTerm Trixie | `com.thothterm.debian` | launcher `GardenSetupActivity`; androidx profile installer (DUMP) |
| ThothTerm Rolling | `com.thothterm.arch` | launcher `GardenSetupActivity`; androidx profile installer (DUMP) |
| ThothTerm Security | `com.thothterm.security` | launcher `GardenSetupActivity`; androidx profile installer (DUMP) |
| ThothTerm (regular) | `com.thothterm` | `TermActivity` (launcher), `RemoteInterface` (open a new empty window), `TermHere` (share target: opens a window *in* the shared directory), `RunScript` (`RUN_SCRIPT`, a dangerous permission the user grants), `RunShortcut` (only commands encrypted and authenticated with this install's own keys), `AddShortcut`, `FileSelection`, `TermService` (`ITerminal`: shows a terminal for the *caller's* own process); androidx profile installer (DUMP) |

The exact JSON is in `tests/security/policy/`.

### Accepted risks (regular Terminal only)

- `RUN_SCRIPT` runs commands for an app the user granted that permission to.
  That is its purpose.
- An app bound through `ITerminal` can show a terminal window inside ThothTerm
  for its own process; what the user types there goes to that app. The window
  is labelled with the calling app's name.
- Command add-ons (`TrustedApplications`) are used only when signed with the
  same certificate as ThothTerm; their command names must be plain words.

## History

| Date | Finding | Affected | Fixed |
|---|---|---|---|
| 2026-10 | An exported legacy integration accepted untrusted external input. Reported privately; credited in the advisory. | ThothTerm Ubuntu ≤ 0.3.1; regular Terminal ≤ 1.4.0. Not the Garden editions (Trixie, Rolling, Security), which never exported it. | Ubuntu: the legacy external-integration surface was removed. Regular: the share target now uses a structural working directory; add-ons require a matching signature. |
| 2026-10 | The unreleased security edition was developed under another project's name and bundled that project's keyring, whose licence is not established. Found before any release. | Nothing released. | Renamed ThothTerm Security (`security-v0.1.0`); no third-party keyring is shipped; the repository is an optional, consent-gated, pinned and verified runtime setup (rule 10). |
