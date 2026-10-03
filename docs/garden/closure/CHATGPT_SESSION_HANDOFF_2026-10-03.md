# ChatGPT Session Handoff — ThothTerm Garden / Rolling / F-Droid

> **Purpose**
>
> This file is a durable handoff of the long ChatGPT supervision session for
> `Lord1Egypt/AndroidThothTerm`. It exists so a new ChatGPT conversation can
> reconstruct the technical state without depending on chat memory.
>
> **Snapshot branch:** `handoff/chatgpt-garden-session-2026-10-03`
>
> **Snapshot base commit:** `cb59e6c1fa8d5d03fffd85bd68a189fa478162a7`
>
> **Live implementation branch:** `claude/garden-master-closure-from-9677a88`
>
> **Important:** the live implementation branch may advance after this snapshot.
> In a new chat, read this file first, then immediately read the current
> `docs/garden/closure/CLOSURE_REPORT.md` and the current head of
> `claude/garden-master-closure-from-9677a88`. The live branch wins if newer
> evidence conflicts with this snapshot.

---

## 0. New-chat bootstrap — do this first

When resuming in a new ChatGPT conversation, the user should say something like:

> **Continue my ThothTerm Garden project from the GitHub handoff.**
> Use the GitHub connector and read, in this order:
>
> 1. `Lord1Egypt/AndroidThothTerm` branch
>    `handoff/chatgpt-garden-session-2026-10-03`,
>    file `docs/garden/closure/CHATGPT_SESSION_HANDOFF_2026-10-03.md`.
> 2. The **current** head of
>    `claude/garden-master-closure-from-9677a88`.
> 3. The **current**
>    `docs/garden/closure/CLOSURE_REPORT.md` from that live branch.
> 4. Any newer evidence/QA files referenced by the closure report.
>
> Treat the handoff as historical context and the live branch as the source of
> truth for current status. Do not guess. Preserve all architecture, security,
> release, F-Droid, HOME-safety and QA decisions. Call me **Hamada**.
>
> Then tell me: current HEAD, current release verdict, what is already proven,
> what is still blocked, and the next safest action.

That is the preferred recovery phrase. A shorter acceptable phrase is:

> **هات حالة المشتل من ملف GitHub handoff وكمل من آخر HEAD حي.**

---

## 1. Human / workflow conventions

- User preferred casual name: **Hamada**.
- ChatGPT is acting as supervising architect/reviewer/prompt writer.
- Claude is colloquially called **“الصنايعي”** in the chat.
- The project uses a deliberate **implementation vs independent QA split**:
  - **Claude (Cloud/CLI)** = source implementation owner, architecture fixes,
    release preparation, final audit, release/F-Droid work when gates are green.
  - **Codex local** = independent QA only:
    `git diff`, real Gradle/SDK builds, ADB, physical phone, adversarial tests,
    report findings; **must not silently fix source findings itself**.
  - ChatGPT = supervisor, reconciles reports, creates prompts, guards invariants.
- Earlier in the closure, Opus 5.5 was used for the broad forensic/security pass.
  Sonnet 5.5 is now preferred for the narrow completion phase unless a new deep
  security/architecture issue appears.
- Never optimize for a reassuring answer. Optimize for evidence that an
  independent expert can audit.

---

## 2. Repository and product family

Repository:

`https://github.com/Lord1Egypt/AndroidThothTerm`

### 2.1 Regular ThothTerm Terminal

- Package: `com.thothterm`
- Current Golden: `terminal-v1.4.0`
- Version: `1.4.0 / 10400`
- Regular Terminal is **local-only**.
- It must stay **LAN-free**.
- It has Upload files / Upload folder into the terminal's current directory.
- It has Keep screen awake while charging.
- Garden LAN changes must not accidentally leak into this product.

### 2.2 ThothTerm Ubuntu Garden

- Package: `com.thothterm.ubuntu`
- Historical Golden: `ubuntu-v0.3.0`
- Historical released version: `0.3.0 / 300`
- Intended next patch after closure: `0.3.1 / 301`
- LAN port: **7681**
- Active F-Droid MR: **!49556**

### 2.3 ThothTerm Trixie Garden

- Package: `com.thothterm.debian`
- Historical Golden: `trixie-v0.2.0`
- Historical released version: `0.2.0 / 200`
- Intended next patch after closure: `0.2.1 / 201`
- LAN port: **7682**
- Active F-Droid MR: **!50342**

### 2.4 ThothTerm Rolling

- Package: `com.thothterm.arch`
- Module: `garden-arch`
- Visible product name: **ThothTerm Rolling**
- Userspace: **Arch Linux ARM AArch64**
- Intended first Golden: `arch-v0.1.0`
- Intended version: `0.1.0 / 100`
- LAN port: **7683**
- Do not use official Arch branding/artwork or imply official affiliation.

### 2.5 Future distros

Planned only after Garden closure:

- BlackArch
- Kali
- Alpine
- AlmaLinux
- CentOS Stream
- possibly BusyBox

**Do not start BlackArch before Rolling/Ubuntu/Trixie closure and Goldens.**

---

## 3. Critical global invariants

These were repeatedly agreed in the chat and must not regress.

### 3.1 HOME is sacred

- Never silently delete `/home/thoth`.
- A damaged runtime is **not** equivalent to a never-installed runtime.
- Missing `bash`, a runtime library, an image pin mismatch, failed optional
  provisioning, etc. must not trigger silent destructive extraction.
- Explicit repair/reinstall must preserve HOME transactionally.
- Crash/interruption at each meaningful step must leave recoverable state.

### 3.2 Terminal core must not be held hostage

As soon as the minimum runnable environment exists:

- rootfs extracted/promoted,
- PRoot/runtime usable,
- shell usable,
- minimum local config valid,

the product should reach a terminal-usable state.

The terminal must **not** wait for:

- `apt update`
- `apt install`
- `pacman`
- `sudo` provisioning
- network
- optional admin tools
- optional package setup

Optional setup runs in the background, single-flight, retryable, and never on
the Android main thread.

### 3.3 Extraction is a security boundary

Must defend against:

- `../` traversal
- absolute paths
- symlink ancestors/escapes
- dangling symlink replacement
- hardlink escape
- malformed UTF-8
- valid non-ASCII names
- literal backslashes
- duplicate-entry confusion
- rename/delete aliasing
- final-object replacement
- TOCTOU races
- unsupported special files
- chmod/chown through symlink targets

Do not “check path, then later perform a following operation by path” where
an attacker can swap the object.

### 3.4 QA packages must be truly isolated

- QA app IDs must be real Gradle/application IDs, not shell-variable fiction.
- Native PRoot/runtime paths must use the same QA package ID.
- Production packages and PocketClaw are protected.
- Every QA install: `adb install --no-incremental`.
- Never `pm clear`, uninstall or overwrite a protected production app as a
  test shortcut.
- Device gates fail closed if actual APK identity cannot be proven with aapt2.

### 3.5 No fake PASS

- If no phone: physical checks are **UNVERIFIED**, not PASS.
- If a kernel 4.14 environment has not been tested: say so.
- Do not mark a historical reviewer issue fixed only because another device
  works.
- Do not weaken tests to hide real package-manager integrity warnings.

---

## 4. Physical test device

Primary current test phone used during closure:

- Samsung Galaxy A16
- Model: **SM-A165F**
- Android 16 / API 36
- arm64-v8a
- physical kernel during closure testing: **6.12.38**
- Wireless debugging IP often: `192.168.1.103:<changing-port>`

Device rules:

- only isolated `.qa.*` apps for destructive/lifecycle/security testing;
- all installs `--no-incremental`;
- record/restore screen-awake settings;
- after the last completed test session,
  `stay_on_while_plugged_in` was restored to **0/off**.

There was an older Android package-manager incident where a primary Rolling
installation/data disappeared after an incremental-style update path. Exact
Android root cause was not proven. This is why all current tooling refuses
incremental installs and protects production IDs.

---

## 5. Important historical checkpoints

### 5.1 Trusted pre-cloud Arch checkpoint

- branch: `feature/arch-v0.1.0`
- commit: **`c49bb03`**

A later Cloud branch reached `5ae7c2b`, but `c49bb03` was deliberately
treated as the safer baseline for the independent Codex line.

### 5.2 Codex Arch line

Branch:

`codex/arch-v0.1.0-from-c49bb03`

Important checkpoint:

**`3585730`**

Codex found and fixed a literal-backslash extraction bug around:

**`3be6ce5`**

Historical corrected Full test APK:
`ThothTerm-Rolling-0.1.0-full-debug-3be6ce5.apk`

SHA-256:
`18a009c95fd33ce61783090fb847bfab2609373b4721baa7d2817aa6456de199`

A prior physical gate on this older line reached approximately:

- **78 PASS**
- **0 FAIL**

That is historical evidence only, not proof for current code.

### 5.3 Sonnet 5.5 forensic review

Review branch:

`review/sonnet55-arch-codex`

Review commit:

**`ea23b31`**

Main important findings:

1. Installed rootfs could be silently re-extracted and wipe HOME.
2. Extractor could write outside staging via dangling symlink + later file.
3. Old release gate tested with system tar rather than necessarily the app's
   real extractor.
4. Interrupted pacman test could misclassify integrity/signature failures.

### 5.4 Claude hardening line

Implementation branch:

`claude/arch-security-hardening-from-3585730`

Important progression:

- `d977d13` — major lifecycle/extractor/PRoot/LAN remediation.
- `d3fd1bd` — post-Codex fixes.
- `839f935` — real QA package/runtime ID isolation.
- `9677a88` — attempted Round-2 remediation; became the base of the master
  closure branch.

### 5.5 Master closure branch

Live branch:

`claude/garden-master-closure-from-9677a88`

Created from:

**`9677a88`**

Important closure commits before the current tail include:

- `c2f648a` — final-component chmod through stable no-follow descriptor;
  TOCTOU regression tests.
- `9ec42d3` — hardlink copies / unreadable target handling.
- `c6eaf8e` — final-object verifier and hardlink object tracking.
- `ab40535` — PRoot patch 0007 child/fork recovery.
- `93e41c0` — `/proc/self/mounts` bind improvement.
- `32baddb` — no guest command before terminal; safe setuid; provisioning
  stdin `/dev/null`; main-thread guard.
- `e108de9` — Arch device gate requires isolated QA package.
- `475d314` — final device gate orchestration.
- `752278b` — Ubuntu 0.3.1 / Trixie 0.2.1 release candidates.
- `37c8c2e` — APK verification/reviewer draft evidence.
- `3ed4b96` — docs/keyring optional setup.

Device-found fixes later:

- `ceb5c85` — Ubuntu sudo/sudoers-related permission problem.
- `59d2f8b` — LAN test ordering/timing assumption fixed.
- `737d308` — PRoot patch 0006 corrected for
  `fchmodat2(fd, "", mode, AT_EMPTY_PATH)` on O_PATH descriptors.
- `9c4fee3` — log “Extraction started” only when extraction actually starts.
- `6e81d66` — Arch SigLevel test/build check accepts pacman-conf's expanded
  `PackageRequired ...` form without weakening signature requirements.
- `a49ec449` — P6 test sets its own umask; interrupted local metadata
  recognised narrowly by cause.
- `d2cb574` — documented Rolling 88/1 and ownership root cause.
- `cba22e22` — **PRoot patch 0008**: preserve guest ownership semantics for
  link2symlink fake hard links.
- `cb59e6c` — hardlink materialized copy preserves archive target mtime.

At the moment this handoff branch was created, the live branch head was:

**`cb59e6c1fa8d5d03fffd85bd68a189fa478162a7`**

The live branch may already be newer when this file is read.

---

## 6. Pinned rootfs artifacts

### Ubuntu

Pinned Ubuntu 26.04.1 base SHA-256:

`5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd`

Host manifest/extractor count observed:

- 6564 paths

### Trixie

Pinned Trixie SHA-256:

`f6520ff1c6eeb28ead2d175e6733da4c970459a291b1bd60f992f1655c5d0677`

Observed:

- 7050 archive entries
- 7049 extracted paths

Important valid Unicode filename that must be preserved exactly:

`NetLock_Arany_=Class_Gold=_Főtanúsítvány.crt`

### Rolling / Arch Linux ARM

Pinned Rolling rootfs SHA-256:

`03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1`

Observed:

- 33640 archive entries
- 33639 extracted paths

Important restrictive-mode file:

`usr/lib/dbus-daemon-launch-helper`

Expected final mode:

`0110`

Rolling's F-Droid/download flavor currently requires this rootfs to be
published at the expected GitHub release asset URL. During testing that asset
returned **HTTP 404**, and the app correctly showed the download/retry error.
Do not call the F-Droid path complete until the rootfs asset is published and
the exact path is re-tested.

---

## 7. Security findings that drove the closure

### 7.1 HOME deletion lifecycle

Old readiness logic used conditions such as:

- setup state marker
- `/usr/bin/bash`
- runtime library presence
- image pin/version

When these failed, setup could re-extract and delete the rootfs, including
`/home/thoth`.

Permanent fix direction:

- explicit lifecycle/state model;
- damaged != never installed;
- repair required, not silent overwrite;
- explicit reset/reinstall preserves HOME;
- crash-safe lifecycle tests.

### 7.2 Extractor symlink escape

A real PoC showed that an archive could create a dangling symlink, then a
regular file at the same pathname, and cause an outside write.

The extractor/file layer was redesigned around no-follow semantics and stable
object identity where needed.

### 7.3 chmod TOCTOU — Codex Round 3 critical finding

At `9677a88`, a fallback did:

1. inspect path type,
2. then plain `chmod(path)`.

Codex deterministically swapped the object to a symlink between the check and
chmod.

Outside sentinel changed:

`0600 -> 0640`

This was a real security regression.

The closure solution uses a stable descriptor/no-follow operation for the
final object, with permanent regression tests.

### 7.4 Verifier gaps

Codex Round 3 also found:

- owner-unreadable file content could rely only on write-time digest, so a
  later same-size rewrite could be missed;
- renaming one hardlink name could make inode relationship verification
  incomplete.

Closure redesign pins/proves final objects rather than trusting stale path
records.

### 7.5 PRoot patch 0006 — fchmodat2

Arch/pacman emitted:

`Can't set permissions to 0777`

A root cause was modern `fchmodat2` semantics not handled correctly by PRoot.

Patch 0006 was introduced, then **device testing found a regression**:
systemd uses:

`fchmodat2(fd, "", mode, AT_EMPTY_PATH)`

on O_PATH descriptors.

Old patch 0006 rewrote this to `fchmod(fd)`, which failed with EBADF.

Commit `737d308` fixed it by allowing the empty-path form to reach the kernel
unchanged because no pathname translation is required.

### 7.6 PRoot patch 0007 — GETEVENTMSG/fork recovery

The F-Droid reviewer exposed a likely PRoot child-tracking problem on an
Android 16 custom ROM / kernel 4.14 environment.

Patch 0007 provides a carefully bounded child-recovery path when the ptrace
event PID is absent/unusable.

Host tests reproduced the stuck-parent/stopped-child shape with a forced
missing-event-pid shim and validated fork/vfork/spawn/thread behavior.

On the Samsung kernel 6.12, all 7 fork/thread workloads passed, but that
kernel normally reports child PIDs, so the actual fallback path is **not
physically proven on kernel 4.14**.

Do not claim kernel-4.14 compatibility is proven until the reviewer or another
equivalent device retests.

### 7.7 PRoot patch 0008 — link2symlink ownership

Rolling gate after interrupted tzdata recovery reached:

- **88 PASS / 1 FAIL**

Remaining failure:

`pacman -Qkk tzdata`

reported UID/GID mismatch for faked hard links such as:

`/usr/share/zoneinfo/CET`

Root cause established physically:

- PRoot link2symlink represents hardlinks with hidden `.l2s.*` backing
  objects;
- the real host owner is the Android app UID;
- fake-root ownership emulation had already mapped ownership to guest root;
- link2symlink then overwrote that ownership in stat results with the backing
  object's real Android UID.

Affected counts observed:

- tzdata: **1093**
- coreutils: **46**
- pacman: **14**

Contents and modes were intact.
`pacman -Qk` had 0 missing files and `pacman -Dk` was clean.
The gate was intentionally **not weakened**.

Commit `cba22e22` added patch 0008 to keep the already-emulated guest owner
when link2symlink substitutes backing-file metadata.

Host owner regression:

- before: many observations leaked real UID;
- after: **239/239** ownership observations correct;
- tested create/rename/replace/new link, fake IDs, multiple stat forms,
  `find`, `stat`;
- patch 0006 and 0007 host checks remained green.

### 7.8 Hardlink-copy mtime

Because Android materializes archive hardlinks as copies, a copied hardlink
received creation time rather than the target inode's archived time.
`pacman -Qkk` then reported Modification time mismatch.

Commit:

`cb59e6c`

makes the materialized copy retain the archive/target time.

At snapshot creation this was the newest observed live commit and still needs
the live Rolling gate result checked.

---

## 8. F-Droid Ubuntu reviewer case — full context

Active MR:

**fdroid/fdroiddata !49556**

Reviewer:

- Vishnu Prakash
- GitLab: `@visheh10`

### 8.1 Original report

Tested ThothTerm Ubuntu 0.3.0 / versionCode 300 on:

- Xiaomi Redmi Note 10 Pro
- Android 16

Observed:

- setup reached `Extracting Ubuntu… 100%`;
- terminal never appeared during observation;
- LAN/uploads were therefore unreachable;
- logcat showed SELinux hard-link denials.

The project initially could not reproduce it.

We downloaded the exact current signed F-Droid CI artifact and tested on our
own Android 16 physical device:

- extraction ~6 s;
- terminal opened;
- LAN worked.

We did **not** tell the reviewer he was wrong.
We asked for a rerun/logcat and ROM/kernel details.

### 8.2 Reviewer's second run — decisive evidence

Reviewer environment:

- Xiaomi Redmi Note 10 Pro
- crDroid 12.11
- LineageOS-based Android 16
- August 2026 security patch
- kernel **4.14.357**
- signed CI APK used as-is

Important timings:

- Ubuntu archive downloaded/verified around `15:46:44.936`
- hardlink fallback count: **115**
- extraction complete around `15:46:51.895`
- provisioning command started around `15:46:51.899`
- sudo provisioning timed out around `15:49:51.905`

Conclusion:

**Extraction itself completed normally.**

The visible “Extracting 100%” stall was misleading; the app was actually
waiting on post-extraction provisioning.

The hardlink fallback worked. Do not blame it for the hang.

Process state during the hang included approximately:

- PRoot parent
- shell in `D`
- child shell in stopped/traced `t`

Later output contained:

`proot warning: ptrace(GETEVENTMSG): Invalid argument`

This strongly suggested PRoot lost track of a forked child, but the project
must distinguish supported hypothesis from proven kernel mechanism.

### 8.3 Second separate confirmed Ubuntu bug

After the first provisioning timeout, provisioning was started again from the
terminal/service path.

A `Process.waitFor()` occurred on the Android **main thread**.

Android raised ANR.

After another timeout the terminal could appear but the shell was
unresponsive; later another provisioning attempt caused another ANR.

This proved two separate design problems existed:

1. provisioning incorrectly gated/blocked terminal/UI flow;
2. PRoot child tracking could break on the reviewer's kernel/ROM.

### 8.4 Closure behavior now required

Ubuntu should:

- finish core rootfs setup;
- reach `TERMINAL_READY`;
- hand off/open terminal;
- then start optional sudo/admin provisioning in background.

Provisioning:

- single-flight;
- background only;
- stdin `/dev/null`;
- timeout-safe;
- no duplicate launch on service reconnect;
- retryable from UI/menu;
- failure leaves terminal usable;
- never re-extracts or touches HOME.

Device test on Samsung deliberately made Ubuntu provisioning hang like the
reviewer case:

- terminal opened in about **2 seconds**;
- terminal accepted input;
- provisioning failed cleanly;
- retry later installed sudo.

This verifies the architecture on kernel 6.12, **not** the PRoot kernel-4.14
fallback.

### 8.5 Reviewer reply policy

Keep the reply factual:

- thank reviewer for the diagnostic log;
- say extraction was proven complete;
- hardlink fallback was not the failing stage;
- explain that misleading 100% UI / synchronous duplicate provisioning was
  corrected;
- explain the separate PRoot child/fork fix;
- list exact devices/kernels tested;
- explicitly say kernel 4.14 remains unverified if true;
- ask reviewer to retest the new CI build once available.

Never say “fixed on your device” without that device's evidence.

---

## 9. Horus reference

Public project:

`https://github.com/scarif-labs/horus`

Relevant file:

`native/patches/proot-android.patch`

It contains a public approach around `PTRACE_GETEVENTMSG` failure/missing
event PID and attempts child recovery using `/proc` relationships such as:

- Tgid
- PPid
- TracerPid

This was treated as a **reference implementation only**.

Rules:

- study upstream termux/proot first;
- understand semantics, don't blindly copy;
- verify license compatibility before any reuse;
- handle concurrent forks/threads, PID reuse, CLONE_PARENT, ambiguity and
  event ordering;
- fail closed if child identity is ambiguous.

Patch 0007 in ThothTerm was implemented separately and tested independently.

---

## 10. Host/build closure status before later device fixes

At the broad Opus closure pass, from clean build around code commit
`3ed4b96`:

- garden-common unit: **335/335**
- term-ubuntu: **604/604**
- Arch: **56/56**
- Trixie: **40/40**
- lint: **0 errors** across 7 reports
- 6 release (R8) + 6 debug + 3 androidTest APKs built
- APK verification: **60/60**
- expected JNI RegisterNatives: **7/7**
- zip alignment / 16 KB checks: PASS
- ELF LOAD alignment >= `0x4000`: PASS
- Full flavors embed pinned rootfs
- F-Droid flavors embed no rootfs
- real host rootfs gates:
  - Ubuntu: PASS
  - Trixie: PASS
  - Rolling: PASS
- PRoot host fork recovery: PASS
- fchmodat2 host probes: PASS at that stage
- runtime-ID checks: PASS

**Important:** all RC hashes from `3ed4b96` are now stale because device
testing found and fixed later defects. Do not release those APKs.

Historical stale RC hashes from `3ed4b96` are preserved only as evidence:

- Arch Full:
  `d792d8d676de450460ce7e470c4d330ed380f75d4eb95fe39398eb6596529050`
- Arch F-Droid:
  `4de243cdbd3e5bcd4d591c804202ab1eb5751b595415eda6b206d707d63e5e26`
- Trixie Full:
  `a7deabf9329e3fd838b7fb571f99a509f2bc777e9b095f895003dc8290b326d2`
- Trixie F-Droid:
  `bb1b10d27f2a9691520356ae17fbf6110cfd15238e43544db9ac791bce4f04fe`
- Ubuntu Full:
  `815409ca2019206022f4cb3590f54b1eab3f160c87226ea494db58ee8545e1ea`
- Ubuntu F-Droid:
  `398ebfecf0c6b12e8fec7103db0b8d98d8715011981acc78447d06a962520815`

Do not use these as final release artifacts.

---

## 11. Physical-device results from the master closure

On the SM-A165F, using isolated `.qa.*` packages only and
`--no-incremental`:

### 11.1 Extractor

All three editions:

- **46 PASS each**
- real app extractor
- AndroidFileOps race cases
- chmod-race regression
- setuid helper checks

### 11.2 First-run terminal behavior

Full builds:

- Ubuntu
- Trixie
- Rolling

F-Droid builds tested:

- Ubuntu
- Trixie

Observed terminal readiness:

- roughly **11–25 seconds** under normal setup
- optional sudo / Rolling keyring occurs afterwards
- no ANR

Reviewer-style intentional provisioning hang:

- terminal opened in ~**2 s**
- terminal remained usable
- optional provisioning failed cleanly
- menu retry later succeeded

### 11.3 HOME

Tested damage/repair/reinstall/interruption.

Sentinel in `/home/thoth` preserved:

- same SHA-256
- same inode

through an interrupted and continued reinstall path.

### 11.4 LAN

Tested Ubuntu and Rolling:

- pairing
- foreign origin rejected
- browser terminal
- upload
- uploaded bytes/hash match phone
- cancellation
- sign-out during upload
- no partial leftovers
- LAN off closes appropriately

### 11.5 Package managers

Ubuntu/Trixie apt:

- working

Trixie dedicated device gate:

- **26 PASS / 0 FAIL**

Rolling pacman first closure run:

- **83 PASS / 6 FAIL**

After test-harness corrections:

- **88 PASS / 1 FAIL**

The last real failure was the link2symlink fake-root UID/GID problem described
above, leading to patch 0008.

Since then patch 0008 and the hardlink-copy mtime fix landed. The next reader
must inspect the **live branch** to see whether a newer device rerun has
reached 0 FAIL.

---

## 12. Rolling gate failures that were test bugs vs real bugs

The 83/6 result was reduced after careful diagnosis.

### Test bug: P6 symlink extraction

The test expected mode 0644 but inherited a permissive umask under
`run-as`, producing 0666.

The extractor itself emitted no permission warning.

Fix:

- P6 sets its own umask explicitly.

### Test semantic mismatch: interrupted vim-runtime

A partially-created local pacman DB entry caused libalpm to print
“invalid or corrupted package”.

The gate had treated this wording as necessarily an integrity/signature
failure.

The gate was changed narrowly:

- only accept that one line when the same log proves missing local metadata
  and “could not fully load metadata”;
- all other signature/integrity corruption wording remains fatal.

### Real defect: tzdata `pacman -Qkk` ownership

This was not waived.

It led to patch 0008.

---

## 13. Current live tail at handoff snapshot

At snapshot creation the live branch had advanced beyond `d2cb574`:

### `cba22e22`

`fix(proot): 0008 keeps the guest owner on link2symlink hard links`

Key evidence from its commit message:

- 1093 tzdata files, 46 coreutils, 14 pacman affected before fix;
- fake hardlink symlink/backing metadata leaked Android app UID;
- after patch: **239/239** host ownership observations correct;
- 0006 check unchanged green: 4/4;
- 0007 check unchanged green: 24/24.

### `cb59e6c`

`fix(garden): a hard link's copy keeps the archive's time`

Reason:

Android materializes archive hardlinks as copies.
The copy previously got the time it was created rather than the archive
target's time, so `pacman -Qkk` saw Modification time mismatch.

This is the **snapshot HEAD**.

**Do not assume the Rolling gate is now green until the live branch/report
says so.**

---

## 14. F-Droid rules

### Existing MRs

Ubuntu:

- `!49556`

Trixie:

- `!50342`

Rolling:

- do not create/finalize submission until genuine Golden exists and rootfs
  asset/download path works.

### Metadata policy from reviewer

For these New App MRs, keep only the **latest Build entry**.

Do not stack historical Build stanzas.

### Workflow

- squash: **ON**
- auto-merge: **OFF**
- never merge upstream fdroiddata yourself
- maintainers decide merge
- historical GitHub release tags stay immutable even if F-Droid metadata only
  contains the latest Build

### F-Droid technical rules

- F-Droid flavor embeds no prohibited prebuilt rootfs.
- Runtime userland download requires user consent.
- SHA-256 pin is verified.
- wrong hash / partial archive / retry / cleanup must be correct.
- permissions must be justified.
- no analytics/tracking surprises.
- source/native-runtime provenance clear.
- rootfs release asset URL must be live before Rolling F-Droid closure.

---

## 15. Release policy

Historical tags are immutable:

- `terminal-v1.4.0`
- `ubuntu-v0.3.0`
- `trixie-v0.2.0`

Never force-update or retag them.

Expected new Goldens, only after all required gates are green:

- Rolling:
  - `arch-v0.1.0`
  - `0.1.0 / 100`
- Ubuntu:
  - `ubuntu-v0.3.1`
  - `0.3.1 / 301`
- Trixie:
  - `trixie-v0.2.1`
  - `0.2.1 / 201`

Regular Terminal:

- no new release unless final impact analysis shows a real change to it.

Every Golden needs:

- clean tested commit
- immutable tag
- artifact SHA-256
- package ID
- versionName/versionCode
- flavor
- build toolchain summary
- truthful release notes
- no claim of unperformed physical verification

---

## 16. Remaining closure sequence

Always inspect the live branch first; this list reflects the snapshot around
`cb59e6c`.

### Mandatory before tagging

1. **Rerun Rolling pacman gate unchanged** after patch 0008 + hardlink mtime fix.
   - Do not mask `pacman -Qkk` ownership/mtime output.
   - Target is 0 FAIL.
2. Because patch 0008 is shared PRoot code, rerun relevant Trixie/Ubuntu
   package-manager smoke/device checks if the live closure has not already
   done so.
3. Publish the exact Rolling rootfs asset on GitHub at the URL expected by
   the F-Droid/download flavor.
4. Test a fresh Rolling F-Droid QA install/download:
   - consent
   - live URL
   - correct SHA
   - extraction
   - terminal
   - cleanup/retry
5. Rebuild all release candidates from the **final** commit.
6. Rerun clean build/test/lint/R8/JNI/16KB/rootfs gates at the final commit.
7. Produce fresh final artifact hashes.
8. Only then tag:
   - `arch-v0.1.0`
   - `ubuntu-v0.3.1`
   - `trixie-v0.2.1`
9. Publish GitHub releases/assets.
10. Update F-Droid MRs using latest Build only.
11. Publish/post the reviewer reply after updated CI build exists.
12. Keep kernel 4.14 explicitly unverified until the reviewer or equivalent
    hardware runs the updated build.

### If a new real defect appears

- stop release;
- root-cause it;
- do not waive a package-manager integrity check just to make a gate green;
- add deterministic regression evidence;
- rerun only the impacted matrix plus required final clean matrix.

---

## 17. Recommended division of labor from here

### Claude Sonnet 5.5

Good default for the narrow remaining closure:

- diagnose remaining gate output;
- implement focused fixes;
- run final matrix;
- publish rootfs;
- prepare releases/F-Droid when green.

Do not re-audit the entire history unless a new finding points back into a
closed area.

Useful continuation instruction:

> Continue from the current head of
> `claude/garden-master-closure-from-9677a88`.
> Do not re-audit already closed areas unless a remaining failure points back
> to them. Your only goal is to eliminate the remaining release blockers with
> evidence, rerun the impacted gates, rebuild final RCs, and release/update
> F-Droid only if every mandatory gate is genuinely green.

### Claude Opus 5.5

Use if a new deep issue appears involving:

- PRoot architecture
- ptrace/fork semantics
- extractor security
- TOCTOU/object identity
- lifecycle/data safety
- verifier trust model

### Codex

Independent QA only.

If asked to validate a Claude closure:

- diff review
- real SDK/Gradle build
- ADB
- physical phone
- adversarial tests
- report evidence
- do not silently “fix it while here”

---

## 18. Master prompt principles that must survive across chats

The earlier master closure prompt was intentionally broad. A future prompt
does not need to repeat its full size if this file is read, but must preserve
these rules:

1. Read reports/handoffs before changing source.
2. Record branch/HEAD/clean state.
3. Verify live branch matches expectation.
4. Fix root causes, not symptoms.
5. Never weaken tests to fit implementation.
6. HOME cannot be silently destroyed.
7. Terminal readiness cannot depend on optional provisioning.
8. Extraction/file metadata is a security boundary.
9. Stable object identity/no-follow semantics where races matter.
10. QA package identity and native runtime ID must agree.
11. Production apps and PocketClaw are protected.
12. All ADB installs are `--no-incremental`.
13. PRoot patches 0006/0007/0008 must not regress one another.
14. Package signatures stay enforced.
15. No fake PID 1/systemd.
16. Physical claims require physical evidence.
17. Kernel 4.14 remains an explicit open compatibility matrix item.
18. Historical tags are immutable.
19. F-Droid latest-Build-only, squash ON, auto-merge OFF.
20. BlackArch waits until Garden Goldens are closed.

---

## 19. Useful repository paths

Primary closure report:

`docs/garden/closure/CLOSURE_REPORT.md`

Current evidence directory:

`docs/garden/closure/evidence/`

Physical evidence:

`docs/garden/closure/device-evidence/`

Arch remediation handoff:

`docs/garden/arch/CLAUDE_FIX_HANDOFF.md`

Codex QA reports historically referenced:

- `docs/garden/arch/CODEX_QA_839F935.md`
- `docs/garden/arch/CODEX_QA_9677A88.md` / report-only worktree/branch when present

Sonnet review historically:

`docs/garden/arch/SONNET55_REVIEW.md`

Final device gate:

`tests/garden-common/qa/final-device-gate.sh`

Rolling device gate:

`tests/garden-arch/device/gate.sh`

Trixie device gate:

`tests/garden-debian/device/gate.sh`

QA installer:

`tests/garden-common/qa/install-qa-app.sh`

Runtime-ID checker:

`garden-common/tools/check-runtime-ids.sh`

PRoot owner host check introduced with patch 0008:

`tests/garden-common/proot/owner-check.sh`

PRoot patch directories:

- `garden-common/patches/`
- `term-ubuntu/patches/`

---

## 20. Known PRoot patch intent

The exact patch files in the live branch are the source of truth, but the
functional history is:

- 0001–0005: earlier Android/PRoot compatibility/runtime work.
- **0006**: fchmodat2/no-follow permission semantics; later corrected for
  `AT_EMPTY_PATH`/O_PATH kernel behavior.
- **0007**: missing/unusable ptrace fork event PID recovery; fail closed on
  ambiguous concurrent child identity.
- **0008**: preserve fake guest ownership when link2symlink substitutes fake
  hardlink backing metadata.

Any future PRoot change must rerun regression coverage for all three newer
patches rather than validating only the newest one.

---

## 21. Product UX decisions that matter

### Uploads

Garden and Regular Terminal support:

- Upload files
- Upload folder

Target:

- the **current working directory of the terminal window in front**
- cwd resolved from the session/foreground process under `/proc`, not by
  parsing screen text or injecting `pwd`
- no blind overwrite; keep-both collision behavior
- no partial files after cancellation/failure

Garden additionally exposes upload through paired LAN browser.

### Keep screen awake

- manual Keep screen awake via window flag;
- “Keep screen awake while charging” setting;
- foreground only;
- do not mutate the device's global screen timeout as a product feature;
- testing may temporarily adjust stay-awake, but must restore it.

### LAN

Garden-only.

Must support:

- explicit enable
- pairing/auth
- origin checks
- terminal
- upload
- session ownership
- cancellation
- sign-out cleanup
- LAN-off cleanup

Regular Terminal remains LAN-free.

---

## 22. F-Droid reviewer answer draft — content requirements

When the updated Ubuntu CI build exists and evidence is final, the reply
should roughly convey:

- Thank you; the second log narrowed the problem substantially.
- It proved extraction completed, including hardlink fallback.
- The misleading 100% state was actually post-extraction provisioning.
- Provisioning has been moved out of terminal/UI blocking and made single-flight
  background work.
- The PRoot child/fork behavior was addressed separately.
- State what devices/kernels were actually tested.
- State explicitly if 4.14 remains untested.
- Ask for a retest on the Redmi/crDroid device if it remains the only 4.14
  environment.
- Do not overclaim.

---

## 23. Things that must NOT be forgotten in the next chat

- The current snapshot is not necessarily the live HEAD.
- Query the live branch first.
- `cb59e6c` is only the handoff snapshot head.
- Patch 0008 ownership fix and hardlink mtime fix landed after the 88/1 report.
- The Rolling gate must be rerun; do not assume 0 FAIL.
- Trixie previously passed 26/26, but shared PRoot changes may justify another
  impacted smoke run.
- Rolling F-Droid rootfs URL was 404 and still needs the release asset unless
  the live branch has already published it.
- RC hashes from `3ed4b96` are stale.
- No Golden/tag should be created from stale RC hashes.
- Kernel 4.14 / crDroid physical proof is still separate from Samsung 6.12.
- The reviewer issue is not “just extraction”; it exposed both provisioning
  architecture and PRoot child tracking.
- Never loosen `pacman -Qkk` merely to make Rolling pass.
- Keep production app IDs protected.
- Do not start BlackArch yet.

---

## 24. Suggested status query for every resumed session

Before giving advice, retrieve:

```text
repo: Lord1Egypt/AndroidThothTerm
live branch: claude/garden-master-closure-from-9677a88
read:
  docs/garden/closure/CLOSURE_REPORT.md
latest commits on live branch
latest device evidence
```

Then report exactly:

```text
LIVE HEAD:
RELEASE VERDICT:
ROLLING GATE:
TRIXIE GATE:
UBUNTU DEVICE:
ROOTFS ASSET:
FINAL RC BUILD:
F-DROID:
KERNEL 4.14:
NEXT ACTION:
```

If any field is unknown, retrieve it rather than guessing.

---

## 25. Snapshot status summary

At handoff branch creation:

- Handoff snapshot based on live commit:
  **`cb59e6c1fa8d5d03fffd85bd68a189fa478162a7`**
- Live closure branch:
  **`claude/garden-master-closure-from-9677a88`**
- Release status:
  **not safe to assume complete**
- Trixie latest proven device gate before shared 0008:
  **26 PASS / 0 FAIL**
- Rolling proven before 0008:
  **88 PASS / 1 FAIL**
- Remaining 88/1 real defect:
  fake-hardlink UID/GID ownership presentation
- Ownership fix:
  **patch 0008 at `cba22e22`**
- New follow-on hardlink mtime fix:
  **`cb59e6c`**
- Required immediate next evidence:
  **rerun Rolling gate on the new live code and inspect the current closure
  report**
- Rootfs asset:
  historically still required for Rolling F-Droid download path
- Final release artifact hashes:
  must be regenerated from final tested commit
- Kernel 4.14:
  **unverified physically**
- Tags/releases/F-Droid:
  only after all mandatory gates genuinely pass.

---

## 26. Exact one-message prompt to restore ChatGPT supervision

The user can paste this into a completely new ChatGPT chat:

```text
We are continuing my AndroidThothTerm / ThothTerm Garden release-closure project.

Use the GitHub connector. Do not answer from memory alone.

First read:
Repository: Lord1Egypt/AndroidThothTerm
Branch: handoff/chatgpt-garden-session-2026-10-03
File: docs/garden/closure/CHATGPT_SESSION_HANDOFF_2026-10-03.md

Then inspect the CURRENT live branch:
claude/garden-master-closure-from-9677a88

Read its current:
docs/garden/closure/CLOSURE_REPORT.md
latest commits
referenced device/host evidence

The handoff file is historical context; the live branch is authoritative if it has advanced.

Preserve all established architecture and rules: HOME safety, terminal-before-optional-provisioning, extractor/no-follow security, PRoot patches 0006/0007/0008, isolated QA package IDs, --no-incremental installs, protected production apps, package signature enforcement, real physical evidence requirements, immutable historical tags, F-Droid latest-Build-only/squash-on/auto-merge-off, and no BlackArch until Garden closure.

Claude is the source implementation owner. Codex is independent QA only. You are supervising the work with me.

Call me Hamada.

After reading, give me only:
1. current live HEAD,
2. current release verdict,
3. what has been conclusively proven,
4. remaining blockers,
5. next safest action.

Do not guess missing status; retrieve it.
```

---

## 27. Final note to the next assistant

The most important pattern in this project is that several “small” warnings
turned into real architecture/security findings:

- an extraction 100% “hang” was actually synchronous provisioning;
- a chmod compatibility fallback became a real TOCTOU exploit;
- a test-only package override could still target production;
- fake-hardlink ownership looked cosmetic until `pacman -Qkk` proved the
  guest metadata semantics were wrong;
- physical testing caught a real 0006 regression that host-only reasoning had
  missed.

Therefore keep the current discipline:

**evidence first, no cosmetic suppression, no fake PASS, and no Golden until
the final live commit has passed the real required gates.**
