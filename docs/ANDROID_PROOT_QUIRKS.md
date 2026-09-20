# Android + PRoot quirks — verified lessons

Every entry here was demonstrated on a physical device (Samsung SM-A165F,
Android 16 / API 36, arm64) during ThothTerm Ubuntu development. Nothing is
included on reasoning alone; where a belief was later disproved, the correction
is recorded rather than the original claim.

Each lesson is tagged:

- **ENGINE** — a property of Android, PRoot or the terminal engine. It applies to
  every distro variant and must not be re-litigated per distro.
- **DISTRO** — a policy choice that a specific distro image may legitimately make
  differently.

---

## Filesystem and mount namespace

### 1. The app's mount namespace is not `adb run-as`'s — ENGINE

The same rootfs path resolves differently depending on who looks. `run-as`
enters the app's UID but **not** the app process's mount namespace. Paths, and
especially symlink targets written by the running app, can therefore read
differently from a `run-as` shell than they do inside a live session.

**Consequence:** a `run-as` reading is evidence about the `run-as` view only. Any
claim about what the *app* sees must be measured from a process launched the way
the app launches it.

### 2. `/data/user/0` and `/data/data` are the same directory, spelled differently — ENGINE

`/data/data/<pkg>` and `/data/user/0/<pkg>` are two representations of one
location. Android hands the app one spelling in some APIs and the other
elsewhere, and PRoot records whichever it is given.

**Consequence:** never compare these paths as strings. Canonicalise first.

### 3. PRoot must be given the canonical rootfs path — ENGINE

`--rootfs` must receive the canonicalised path. Passing a non-canonical spelling
makes PRoot's internal bookkeeping disagree with the paths it later resolves.

### 4. `--link2symlink` encodes an absolute prefix — ENGINE

With `--link2symlink`, PRoot emulates hard links as symlinks carrying an
**absolute** path prefix. If the rootfs is later reached by a different absolute
spelling (see §2), previously written `.l2s` links point at the old prefix and
appear broken.

**Consequence:** the prefix is part of on-disk state. Changing how the rootfs
path is spelled is a **migration**, not a refactor.

### 5. `readlink`/`stat` alone do not prove link health — ENGINE

A `.l2s` link can `readlink` cleanly and still be semantically dead, and can look
dead from one namespace while being alive in another (§1). Existence checks
measure representation, not behaviour.

**The only trustworthy check is the semantic one:**

```
create → link → read back → exec → restart the session → read again
```

If each step succeeds after a restart, the links are healthy. Anything less is
inference.

### 6. Never probe the production rootfs casually from `run-as` — ENGINE

Reads are safe, but writing or "repairing" from a `run-as` shell writes state
into the wrong namespace view and can manufacture exactly the corruption it was
meant to fix. Diagnose from a real session.

### 7. SSL hash links look broken from the host and are not — ENGINE

`/etc/ssl/certs` holds many hash links. From outside they can appear
unresolvable. Measured **inside the guest**: all 243 entries readable, 0 broken,
`openssl s_client` reports `Verification: OK` over TLSv1.3, and `curl` returns
200 with `ssl_verify=0`. This is another instance of §1 — a representation
artifact with no functional effect. **Do not "repair" it.**

### 8. Rootfs extraction must preserve hard links and modes — ENGINE

The extractor must reproduce hard links, symlinks and permission bits, including
setuid, or `dpkg` and `sudo` break in ways that surface much later.

---

## Shell and environment

### 9. The session shell is a non-login shell — ENGINE

ThothTerm enters the guest with `su -m`, so `shopt login_shell` reports **off**.
Neither `/etc/profile` nor `~/.profile` runs.

**Consequence:** anything a distro normally does in `~/.profile` — including
Ubuntu's own `~/.local/bin` and `~/bin` PATH lines — simply never happens.

### 10. PAM's `pam_env` replaces PATH wholesale — ENGINE

`/etc/pam.d/su` contains `session required pam_env.so readenv=1`, which sets PATH
from `/etc/environment`. Demonstrated by putting a sentinel in the PATH handed to
PRoot: it survives a direct `bash`, and is **gone** through `su`.

**Consequence:** setting PATH in the Android-side runtime environment cannot fix
guest PATH. It is discarded before the user's shell starts. The fix must live in
something the shell itself sources.

### 11. User-local PATH integration must be generic — ENGINE / DISTRO policy

The managed `/etc/profile.d` block re-establishes the user-local bin directories
a login shell would have added: each candidate is `$HOME`-relative, added only if
it exists, and only if not already present. PATH is prepended to, never replaced.

Never special-case one tool. The bug that exposed this looked like "Foundry
disappears", but the real defect was that *no* user-local bin directory was ever
on PATH.

Which directories are worth probing is **DISTRO** policy; that they must be
existence-gated and additive is **ENGINE**.

### 12. The managed shell block must be idempotent and delimited — ENGINE

The block is inserted exactly once, between explicit begin/end markers, appended
rather than rewritten. Verified across two `adb install -r` upgrades: exactly one
block, and `.bashrc`/`.profile`/`.bash_logout` byte-identical before and after.

**Never rewrite arbitrary user dotfiles.**

### 13. Non-interactive shells inherit, they do not re-derive — ENGINE

Because the managed block **exports** PATH, child processes — `sh -c`, `bash -c`,
scripts — inherit it, exactly as on a real Ubuntu login session. A non-interactive
shell started as the session root does not read `.bashrc`, which is correct POSIX
behaviour and not a defect.

---

## Privileges, packages and networking

### 14. `sudo` must be genuine — ENGINE

A wrapper that fakes `sudo` breaks as soon as anything inspects it. ThothTerm
provisions the distro's real `sudo` offline from pinned packages. Acceptance is
`sudo -n true` (exit 0) and `sudo whoami` → `root`.

Permissive PAM bypasses were removed; `visudo`-valid configuration is required.

### 15. DNS and `/etc/hosts` are managed, not inherited — ENGINE

`/etc/resolv.conf` is bind-mounted from a file the Android side regenerates, and
`/etc/hosts` is managed. The guest cannot see Android's resolver configuration by
itself.

### 16. Android supplementary GIDs need names in the guest — ENGINE

The app's supplementary Android group IDs must exist in the guest group database,
or tools that resolve the current process's groups emit errors or behave oddly.

### 17. The package manager is the real acceptance test — ENGINE

A distro is only "working" when its native package manager completes a full
cycle: update, install, configure, query, remove, and an audit that reports
nothing outstanding. Shell prompts prove far less.

---

## Android lifecycle

### 18. `START_NOT_STICKY` is required on modern Android — ENGINE

Android 16 forbids the implicit restart of a foreground service after process
death. A sticky service produces a crash on restart. `START_NOT_STICKY` is
mandatory, and **must not** be reverted to keep a notification alive.

### 19. A declared notification permission is not a granted one — ENGINE

`POST_NOTIFICATIONS` was declared and never requested. The platform accepted the
foreground-service notification and then suppressed it:
`numEnqueuedByApp=1, numPostedByApp=0`, with `granted=false` and
`importance=NONE userSet=false`.

**Consequences:** ask once at runtime; record the ask *before* showing the dialog,
because the result callback does not fire if the activity is recreated; and treat
refusal as a normal outcome — the service keeps running, only its notification is
absent.

With `allowBackup="true"`, SharedPreferences survive a reinstall, so a
"this install has asked" flag must not live there. ThothTerm Ubuntu sets
`allowBackup="false"`, which is why prefs are safe here.

### 20. API 36 execution constraints — ENGINE

Guest binaries are mapped by PRoot rather than `execve`'d by Android, so they may
live in app-private storage. PRoot and its loader must instead sit in
`nativeLibraryDir` with the executable bit, which requires `useLegacyPackaging`
and `lib*.so` naming.

---

## Terminal geometry

### 21. The geometry chain has one direction — ENGINE

```
font size → Paint text size → measured cell width/height
          → viewport rows/columns → emulator resize → PTY → TIOCSWINSZ → stty
```

Every size change must traverse the whole chain. A change that looks right but
leaves the PTY stale is a defect: verify with `stty size` **inside** the guest,
never by eye.

Measured at density 2.8125, portrait viewport 1080×1201:
`10pt → 28px → cell 17.0×33 → 63 cols, 36 rows → stty "36 63"`.

### 22. Zoom must change font metrics, never view scale — ENGINE

Scaling the View transform leaves rows and columns untouched, so the guest never
learns the terminal changed size. Zoom changes the font size and lets the chain
in §21 do the rest.

### 23. Hidden sessions hold a stale size, and that is correct — ENGINE

A non-displayed child is `GONE`, so it is never measured and never learns about a
geometry change. It converges when displayed.

An earlier reading suggested it failed to converge; instrumentation disproved
that. The flipper height oscillates (1201 ↔ 1733) as the extra-keys bar toggles,
and the session that looked stale was correct for the live layout. **Do not
"fix" this without evidence that a displayed session disagrees with the live
flipper geometry.**

### 24. Rotation and `configChanges` — ENGINE

The activity honours an orientation preference and may be pinned to portrait or
landscape. `TermViewFlipper.onSizeChanged` is **not** always called — full-screen
and `resize_on_measure` modes route through `onMeasure` instead — so both paths
must end in the same `doSizeChanged`.

A device lying flat will not rotate under `SCREEN_ORIENTATION_FULL_SENSOR`; drive
the app's own orientation preference when testing.

### 25. The Extra Keys bar is height-qualified — ENGINE

The bar must not crowd out the terminal in landscape, where height is scarce.
Measured: at 10pt landscape the terminal is 20 rows with the bar hidden and 7
with it shown.

### 26. Programs may ignore terminal width entirely — DISTRO

`fastfetch` emits **byte-identical output at every terminal width**, positioning
its info block with a fixed `ESC[47C` and ending at column 106, with autowrap
disabled. It overflows any terminal narrower than 106 columns — including the 63
of default portrait — and the excess is clipped, not wrapped.

**Two measurement lessons:**

- Stripping escape sequences and measuring the longest remaining text run gives
  59 here, and is **wrong**. It discards cursor-positioning jumps.
- The honest measurement replays the byte stream like a terminal, tracking `CUF`
  (`ESC[nC`), `CHA`, `CR`/`LF` and character widths, and reports the furthest
  column reached.

The response is presentation-level and optional (`fastfetch-fit`), never a change
to the renderer, the PTY or the terminal's geometry.

---

## Process and release discipline

### 27. Clean build, from the committed state — ENGINE

Release artifacts come from `./gradlew clean` followed by a build of a clean
worktree. Every downloaded input is SHA-256 pinned and the staging script fails
closed on mismatch.

### 28. Provenance is a release requirement — ENGINE

Every bundled binary needs a recorded upstream, version, license and hash, with
the license read from the artifact rather than inherited. See
`THIRD_PARTY_NOTICES.md`.

### 29. No destructive migration without a proven invariant — ENGINE

Never reset or re-extract a rootfs, or delete a user's home, to resolve a defect
that has not been proven to require it. Existing user state is the highest-value
thing the product holds.
