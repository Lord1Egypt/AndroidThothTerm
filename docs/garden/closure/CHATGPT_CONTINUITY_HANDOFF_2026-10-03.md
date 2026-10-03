# ChatGPT / Garden Continuity Handoff — 2026-10-03

> **Read this file first in any new ChatGPT/Claude/Codex session before touching the Garden code.**
>
> This document exists so a new chat can continue the project without re-discovering the history, rules, safety constraints, QA discipline, ADB workflow, F-Droid context, or the exact remaining release blockers.
>
> Repository: https://github.com/Lord1Egypt/AndroidThothTerm  
> Working branch: **claude/garden-master-closure-from-9677a88**  
> Code baseline immediately before this handoff-document commit: **d2cb5745e939997fc426f3d3dcb3bb265b1d90cc**  
> Current release verdict at that code baseline: **RELEASE BLOCKED**  
> Reason: one real Rolling/Arch PRoot ownership-semantics defect remains. No Golden tag/release/F-Droid update is allowed until it is resolved and the final gates are rerun.
>
> This file is intentionally redundant. Prefer redundancy over losing context.

---

# 1. How to work with Mohamed / chat operating style

The user is Mohamed and prefers to be called **حماده** in casual Arabic conversation. He calls ChatGPT **ميدو**.

For project work:

- Speak in Egyptian Arabic in the chat unless a technical prompt/report is being written in English.
- Keep the tone warm, lively, technical and senior-engineering level.
- Jokes are welcome, especially around:
  - "الصنايعي" = Claude implementation agent
  - "مفتش كرومبو" = Codex QA
  - "الشبشب للbugs"
  - "المشتل / الورد" = Garden product family
  - "الديناصورات" = full history / huge master prompt
- Do not let jokes weaken technical precision.
- Mohamed likes **copy-paste prompts** with:
  - exact branch/SHA
  - explicit scope
  - exact stop conditions
  - exact PASS/FAIL/UNVERIFIED semantics
  - specific tests and evidence required
- Do not habitually end every message with a question.
- When the user says **"هات حالة المشتل"**, give a clean project-state snapshot.
- Accuracy beats reassurance. Never claim a gate passed if it did not run.
- If a physical test cannot run, mark it **UNVERIFIED/BLOCKED**, not PASS and not product FAIL.

The user explicitly asked that a new chat continue "على نفس المنوال". Preserve this collaboration style.

---

# 2. Agent responsibility model — do not change casually

The project deliberately separates implementation from independent QA.

## Claude / Claude Cloud
**Sole source-code implementation owner.**

Claude may:
- change source
- change build scripts
- change tests when the test itself is wrong
- do architecture/security/runtime fixes
- prepare releases
- create tags/releases only after all gates are genuinely satisfied
- update F-Droid metadata/MRs only after Golden criteria are met

## Codex local
**Independent QA only.**

Codex should:
- inspect diffs
- run real Gradle/SDK/NDK builds
- run host security/adversarial tests
- run ADB/device gates
- inspect package IDs
- inspect R8/JNI/16 KB alignment
- report defects with exact evidence

Codex should **not modify source** during independent QA. If it finds a source defect, return evidence to Claude.

This separation exists to stop an implementation agent from self-certifying its own work.

## Model choice rule used in this project

Use the stronger broad-reasoning model for wide forensic closure work:
- **Opus 5.5** for full-repo architecture/security/PRoot/release/F-Droid judgment.
- **Sonnet 5.5** for a narrow, already-understood continuation phase.

We used Opus for the master closure pass. Once the remaining scope became small and concrete, we switched to **Sonnet 5.5**.

Do not restart the entire "history of humanity and dinosaurs" audit for a narrow remaining bug unless the new evidence points back into a closed area.

---

# 3. Prompting philosophy that worked well

There are two prompt shapes.

## A. Master closure prompt
Use when the repo needs a complete forensic pass across architecture/security/build/device/release/F-Droid.

Characteristics:
- includes historical SHAs
- includes every known review/QA finding
- requires reading reports before changes
- forces root-cause fixes
- requires clean build + physical device evidence
- forbids warning suppression
- forbids fake PASS
- defines exactly when tags/releases/F-Droid may happen
- final verdict must be closure complete or one concrete remaining blocker

This was already done from 9677a88 and produced the current closure branch.

## B. Continuation prompt
Use now.

Start with wording like:

~~~text
Continue from commit d2cb5745e939997fc426f3d3dcb3bb265b1d90cc on
claude/garden-master-closure-from-9677a88.

Do not re-audit already closed areas unless the remaining PRoot ownership defect
directly points back to them.

Do not weaken or waive the pacman -Qkk gate.

Fix the remaining fake-root + link2symlink ownership semantics correctly,
rerun the unchanged Rolling physical gate, then finish the final release closure
only if every mandatory gate is green.
~~~

For a narrow continuation, do not burn context rereading unrelated ancient history unless necessary.

---

# 4. Product family and immutable historical releases

## Regular Terminal
Package:
- com.thothterm

Historical Golden:
- terminal-v1.4.0
- version 1.4.0 / 10400

Rules:
- local-only forever
- Upload files/folder to cwd
- **no Garden LAN**
- no new Regular Terminal release unless impact analysis proves its source changed

## Ubuntu Garden
Package:
- com.thothterm.ubuntu

Historical Golden:
- ubuntu-v0.3.0
- version 0.3.0 / 300

Active F-Droid MR:
- !49556

Current intended next release after closure:
- ubuntu-v0.3.1
- version 0.3.1 / 301

LAN port:
- 7681

## Trixie Garden
Package:
- com.thothterm.debian

Historical Golden:
- trixie-v0.2.0
- version 0.2.0 / 200

Active F-Droid MR:
- !50342

Current intended next release after closure:
- trixie-v0.2.1
- version 0.2.1 / 201

LAN port:
- 7682

## Rolling Garden
Visible product:
- **ThothTerm Rolling**

Underlying userspace:
- Arch Linux ARM AArch64

Package:
- com.thothterm.arch

Module:
- garden-arch

Intended first Golden:
- arch-v0.1.0
- version 0.1.0 / 100

LAN port:
- 7683

Branding rule:
- visible name stays Rolling
- it is fine to factually say Arch Linux ARM AArch64 environment
- do not imply official Arch affiliation
- do not use official Arch artwork

## Future products
Planned:
- BlackArch
- Kali
- Alpine
- AlmaLinux
- CentOS Stream
- possible separate BusyBox package

**Do not start BlackArch until Rolling is genuinely released and stable.**

---

# 5. Historical Rolling / Arch development checkpoints

Trusted pre-cloud feature checkpoint:
- branch: feature/arch-v0.1.0
- commit: c49bb03

Later Claude Cloud checkpoint:
- 5ae7c2b
- not the trusted baseline merely because it is newer

Codex implementation line from c49bb03:
- branch: codex/arch-v0.1.0-from-c49bb03
- final HEAD: 3585730d3c4c6380302ee22e143480a566d711cf

Important Codex fix:
- 3be6ce5
- fixed literal-backslash filename extraction bug

Real Rolling rootfs:
- SHA-256: 03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1
- approximate size: 199,826,020 bytes
- approx package count: 138

Historical corrected Full APK:
- ThothTerm-Rolling-0.1.0-full-debug-3be6ce5.apk
- SHA-256: 18a009c95fd33ce61783090fb847bfab2609373b4721baa7d2817aa6456de199

Old physical gate once reached:
- 78 PASS / 0 FAIL

That historical PASS is evidence only. It does not certify the current tree.

---

# 6. Important Android package-manager data-loss incident

A prior command used:

~~~sh
adb install -r ...
~~~

Android selected an incremental-install path. It reported success, then the production Rolling package was fully removed and old private data, including /home/thoth, was lost.

No explicit pm clear or uninstall had been requested.

The exact Android initiating cause was not proven.

Permanent rules:

- **Never use incremental installation for Garden QA.**
- Use:
  - adb install --no-incremental ...
- QA builds must have a real isolated application ID.
- Verify package identity before install.
- Never let a QA gate write to production package private data.
- Never run pm clear/uninstall against production during QA.
- Treat /home/thoth as user data that must be preserved.

---

# 7. Physical Android test phone

Primary test phone:
- Samsung Galaxy A16
- model SM-A165F
- Android 16 / API 36
- arm64-v8a
- page size 4096
- current observed kernel during closure QA: 6.12.38

Wireless debugging:
- IP/port changes
- do not hardcode an old port
- user provides current value when reconnecting

Examples seen during this chat:
- 192.168.1.103:41973
- 192.168.1.103:35191

Unknown device:
- an ADB/mDNS device at 192.168.1.100 was seen
- **do not touch it**

Use ANDROID_SERIAL for every device command once the target is known.

Example:

~~~sh
export ANDROID_SERIAL=192.168.1.103:<current-port>
adb connect "$ANDROID_SERIAL"
adb devices -l
~~~

---

# 8. Stay-awake / charger handling

During long physical tests the user may ask to keep the screen on while the phone is charging.

Rule:
1. Read and remember the original setting first.
2. Enable stay-awake only while needed.
3. Keep the phone connected to charger.
4. Wake the screen periodically if needed.
5. Restore the exact original setting at the end.

Current phone's original setting at the end of the latest test session:
- stay_on_while_plugged_in = 0

Do not assume future devices have the same original value.

Claude correctly restored it after testing.

---

# 9. Garden screen-awake product rule

Separate from temporary ADB testing:

In the app itself, screen-awake behavior must remain:
- manual request, OR
- setting enabled AND charging
- foreground only

Do not:
- mutate global Android timeout
- introduce uncontrolled WakeLock behavior

---

# 10. Shared Garden architecture requirements

## Lifecycle / HOME
A damaged environment is not the same thing as never installed.

Conceptual states:
- NOT_INSTALLED
- CORE_SETUP_RUNNING
- TERMINAL_READY
- OPTIONAL_SETUP_PENDING
- HEALTHY
- REPAIR_REQUIRED

Exact names may differ, semantics must not.

Rules:
- never silently delete /home/thoth
- damaged runtime must not trigger destructive auto-reextract
- explicit repair/reinstall must preserve HOME transactionally
- destructive reset must be explicit and user-authorized
- crash at every important lifecycle stage must leave recoverable state

## Terminal readiness
Permanent rule:

As soon as:
- rootfs exists
- PRoot runtime is valid
- shell is runnable
- minimum configuration is complete

the app reaches TERMINAL_READY and opens the terminal.

Do not block terminal startup on:
- sudo
- apt
- pacman
- dnf
- apk
- keyring setup
- network
- optional packages
- optional admin tools

Optional provisioning:
- background only
- single-flight
- no Process.waitFor on main thread
- retryable
- timeout-safe
- failure must not freeze UI
- failure must not cause destructive re-extraction
- stage-specific UI/logging

## Shared extractor security model
The extractor is a security boundary.

Must defend against:
- ../ traversal
- absolute paths
- malformed UTF-8
- path alias confusion
- literal-backslash mishandling
- symlink escape
- dangling symlink replacement
- hardlink escape
- rename/delete ordering attacks
- duplicate entries
- TOCTOU
- unsupported special files
- parent/final component substitution where relevant

Use no-follow / stable object identity where races matter.

## Metadata semantics
Correctly handle:
- regular files
- directories
- symlinks
- hardlinks
- owner-unreadable files
- executable-only files
- mode 0000
- sticky bit
- /tmp 01777
- final ownership semantics visible inside PRoot

## Verifier
The final object on disk must be proven.

Do not:
- trust only the write-time hash if the final object could have changed
- chmod an unreadable file just so the verifier can read it

Current closure design pins unreadable file descriptors before the mode becomes unreadable and later proves final path/inode correspondence.

## systemd
Use:
- SYSTEMD_IN_CHROOT=1 where correct

Do not:
- fake PID 1
- pretend systemctl is operating a real booted systemd instance

## LAN
Garden only.

Requirements:
- session ownership correct
- foreign origin refused
- sign-out cancels active upload
- no orphan stream/fd/staging file
- LAN off closes connections

---

# 11. Sonnet 5.5 forensic review

Review branch:
- review/sonnet55-arch-codex

Review commit:
- ea23b31

Report:
- docs/garden/arch/SONNET55_REVIEW.md

Review base:
- 3585730

Verdict:
- CHANGES REQUIRED

Critical historical findings:

## HOME deletion design bug
Missing readiness components could trigger re-extraction and delete the rootfs including /home/thoth.

## Extractor symlink escape
Crafted archive:
1. create dangling symlink
2. later create regular file at same name
3. write outside staging

PoC produced a real outside write.

## Release gate weakness
Old proof used system/GNU tar rather than the app's real extractor.

## Interrupted pacman test weakness
An interruption test could false-pass on signature/corruption errors.

All of these informed the master remediation.

---

# 12. Claude remediation history before final closure

Implementation branch:
- claude/arch-security-hardening-from-3585730

Major remediation:
- d977d13

Claims included:
- shared lifecycle
- HOME preservation
- shared extractor
- UTF-8
- symlink containment
- PRoot fchmodat2 patch 0006
- SYSTEMD_IN_CHROOT=1
- background Ubuntu provisioning
- stronger interrupted pacman checks
- signature verification
- real extractor gates
- .bashrc integrity
- /tmp sticky bit
- fd cleanup
- LAN race fixes

Codex QA Round 1 found:
1. SDK36 compile failure from hidden Os.unlink(String)
2. LAN sign-out could leave an upload open
3. QA gate could pretend a package override while APK still used production ID

Claude then produced:
- d3fd1bd
- 839f935

Important QA isolation changes at 839f935:
- single application ID source via applicationId.gradle
- QA suffix produces real package IDs
- PRoot/native paths are package-ID aware
- runtime ID checker
- safe QA install script
- QA variant is a real runnable app

---

# 13. Codex QA Round 2

Report:
- docs/garden/arch/CODEX_QA_839F935.md

Report-only commit historically:
- 421b09426768155a1f9ee6adce50d41797751189

Important results:
- real SDK36 compile PASS
- debug/release build matrix PASS
- R8/JNI PASS
- 16 KB PASS
- QA package isolation PASS
- one JVM FileOps chmod unreadable-file failure
- Ubuntu predictive-back lint failure
- verifier could not safely re-read mode-0110 dbus helper
- physical device checks unavailable at that time

Real rootfs references:

Ubuntu:
- SHA-256 5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd
- 6564 paths

Trixie:
- SHA-256 f6520ff1c6eeb28ead2d175e6733da4c970459a291b1bd60f992f1655c5d0677
- 7050 archive entries
- 7049 paths
- exact non-ASCII file:
  NetLock_Arany_=Class_Gold=_Főtanúsítvány.crt

Rolling:
- SHA above
- 33640 archive entries
- 33639 expected paths
- usr/lib/dbus-daemon-launch-helper is mode 0110

---

# 14. Claude Round 3 and Codex QA Round 3

Claude Round 3 HEAD:
- 9677a88

It attempted to fix:
- JVM chmod on unreadable files
- verifier design
- predictive back
- PRoot host-check preflight

Codex QA Round 3 verdict:
- RETURN TO CLAUDE

Report:
- docs/garden/arch/CODEX_QA_9677A88.md

Critical defect:
- JvmFileOps.chmodNoFollow fallback had a real TOCTOU
- deterministic swap replaced target path with a symlink
- subsequent plain chmod followed it
- outside sentinel mode changed:
  - 0600 -> 0640

Two more verifier findings:
1. owner-unreadable file could be rewritten with same size after write-time digest and escape detection
2. hardlink-group inode verification could be lost after rename

The fail-fast rule correctly stopped later QA gates.

---

# 15. Master closure branch

Current closure branch:
- claude/garden-master-closure-from-9677a88

Start:
- 9677a88

The master closure pass was done with Opus 5.5.

Read:
- docs/garden/closure/CLOSURE_REPORT.md

Important code-fix commits from the closure report:

- c2f648a — safe chmod through stable descriptor; TOCTOU regression coverage
- 9ec42d3 — owner-unreadable hardlink copy and target-mode behavior
- c6eaf8e — verifier final-object proof / hardlink tracking
- ab40535 — PRoot patch 0007 child recovery
- 93e41c0 — /proc/self/mounts -> /etc/mtab binding
- 32baddb — terminal-ready before guest provisioning; setuid descriptor path; stdin /dev/null; main-thread guard
- e108de9 — Rolling gate requires isolated QA package
- 475d314 — final device gate script
- 752278b — Ubuntu 0.3.1 / Trixie 0.2.1 candidates/changelogs
- 37c8c2e — APK verification / evidence / reviewer draft
- 3ed4b96 — documentation around keyring optional setup

Device-found fixes after that:
- ceb5c85 — Ubuntu sudo check / sudoers permissions under PRoot
- 737d308 — patch 0006 regression on a mode-change call systemd uses
- 9c4fee3 — misleading "Extraction started" before download
- 59d2f8b — LAN test timing assumption
- 6e81d66 — Rolling signature-settings check wording
- later gate/test fixes are included up to d2cb574

Code baseline before this handoff document:
- **d2cb5745e939997fc426f3d3dcb3bb265b1d90cc**

---

# 16. PRoot patches 0006 and 0007

## Patch 0006
Purpose:
- correct modern fchmodat/fchmodat2 behavior used by glibc/system packages
- eliminate symlink permission-warning flood by fixing semantics, not hiding warnings

A device regression was later found:
- old 0006 broke a mode-change call used by systemd-tmpfiles/pacman hook
- fixed at 737d308
- reverified on phone

## Patch 0007
Purpose:
- deal with kernels/environments where PTRACE_GETEVENTMSG does not provide usable child PID

Host reproduction:
- old patches 0001–0006 + forced missing event PID reproduced reviewer-like state:
  - parent D
  - child t
  - GETEVENTMSG warning

0007 behavior:
- recovers sequential fork/vfork/spawn/clone cases when child can be uniquely identified
- ambiguous concurrent thread attribution fails closed instead of guessing
- never resume an untracked ambiguous child

Physical Samsung kernel 6.12:
- all 7 fork/thread workloads passed
- however this kernel normally provides child PIDs
- therefore 0007 recovery path is still not proven on a real problematic kernel

Kernel 4.14:
- still physically UNVERIFIED

Do not claim otherwise.

---

# 17. F-Droid Ubuntu reviewer case

Ubuntu F-Droid MR:
- !49556

Reviewer:
- Vishnu Prakash
- @visheh10

Original test:
- ThothTerm Ubuntu 0.3.0 / 300
- Xiaomi Redmi Note 10 Pro
- Android 16
- custom ROM: crDroid 12.11, LineageOS-based
- August 2026 security patch
- kernel 4.14.357

Original symptom:
- UI stayed at "Extracting Ubuntu… 100%"
- terminal never appeared for minutes
- LAN unreachable
- SELinux hardlink denials were visible

We tested the exact current signed F-Droid CI artifact on our physical Android 16 device:
- extraction ~6 sec
- terminal opened
- LAN worked
- could not reproduce initially

Reviewer then retested signed CI APK without resigning and supplied excellent logcat.

The new log proved:

- download + verification ~6 sec
- extraction ~7 sec
- hardlink fallback used 115 times successfully
- extraction completed
- then "Provisioning command start"
- sudo provisioning timed out after 3 minutes

Therefore:
- extraction was not the blocking step
- the UI label was misleading
- post-extraction provisioning held terminal readiness hostage

Process state:
- PRoot parent
- child shell in D
- forked child stopped under ptrace (t)

Warning:
- ptrace(GETEVENTMSG): Invalid argument

Second independent confirmed bug:
- provisioning restarted from TermActivity/service connect
- Process.waitFor ran on main thread
- ANR
- later terminal appeared but shell was unresponsive
- third provisioning attempt caused another ANR

Permanent response:
- terminal opens at TERMINAL_READY
- sudo/apt/admin provisioning is background
- no main-thread wait
- single-flight
- retry from menu
- failure does not block terminal

Physical Samsung test later intentionally simulated hung sudo provisioning:
- terminal still opened in ~2 sec
- terminal answered input
- provisioning failed cleanly
- menu retry installed sudo

This is very strong evidence the lifecycle fix works on our phone.

Reviewer response draft:
- docs/garden/closure/FDROID_REVIEWER_REPLY_49556.md

Do not post "fixed on kernel 4.14".
The reply must:
- credit the reviewer
- distinguish provisioning fix from PRoot fix
- state what our physical device proved
- state kernel 4.14 remains unverified
- request reviewer retest of updated CI build

---

# 18. Horus reference project

Public repo:
- https://github.com/scarif-labs/horus

Relevant file:
- native/patches/proot-android.patch

Horus also runs a PRoot-based Linux environment on Android and includes child-recovery logic for GETEVENTMSG failure by inspecting /proc relationships such as:
- Tgid
- PPid
- TracerPid

This was studied as a **public reference implementation**.

Rules:
- do not blindly copy
- understand upstream termux/proot first
- preserve license obligations
- our patch 0007 is a separate implementation
- fail closed on ambiguous child identity

Interesting public identity evidence connected reviewer account @visheh10 with Horus development metadata, but do **not** frame the reviewer as adversarial or accuse them of conflict. Their logcat was accurate and extremely useful.

Horus F-Droid submission seen:
- !50351

---

# 19. QA package isolation / launcher clutter

During physical QA there were many isolated ThothTerm QA apps visible in the launcher and notification shade.

This is expected because:
- each QA build uses a distinct package ID
- Android treats them as separate apps
- foreground services produce separate notifications

This visual clutter is not a product bug.

After release closure, clean up isolated QA apps if desired, but do not uninstall production packages accidentally.

Production package IDs:
- com.thothterm
- com.thothterm.ubuntu
- com.thothterm.debian
- com.thothterm.arch

QA package IDs use .qa.* suffixes, examples:
- com.thothterm.arch.qa.lifecycle
- com.thothterm.arch.qa.final3
- similar isolated Ubuntu/Trixie IDs

Always verify actual APK package identity before install.

---

# 20. ADB/device QA rules and useful commands

## Always
Use:
~~~sh
adb install --no-incremental ...
~~~

Never rely on plain:
~~~sh
adb install -r ...
~~~

for Garden QA.

## Establish target
~~~sh
export ANDROID_SERIAL=192.168.1.103:<current-port>
adb connect "$ANDROID_SERIAL"
adb devices -l
~~~

## Toolchain environment used by closure
~~~sh
export ANDROID_HOME=~/Android/Sdk
export ANDROID_NDK_HOME=~/Android/Sdk/ndk/23.2.8568313
export JAVA_HOME=~/.gradle/jdks/jetbrains_s_r_o_-17-amd64-linux.2
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
~~~

## Final combined device gate
~~~sh
tests/garden-common/qa/final-device-gate.sh \
  ~/AndroidThothTerm/qa-final \
  ~/AndroidThothTerm/archives \
  ~/AndroidThothTerm/arch-rootfs-work/stale/alarm-upstream-20260805-userland.tar.gz
~~~

## Rolling gate
The closure work used:
~~~sh
tests/garden-arch/device/gate.sh \
  com.thothterm.arch.qa.final3 \
  /home/lordegypt/AndroidThothTerm/archives/thothterm-arch-aarch64-rootfs-03a4c669ed6f.tar.gz \
  /home/lordegypt/AndroidThothTerm/arch-rootfs-work/stale/alarm-upstream-20260805-userland.tar.gz
~~~

Evidence files seen:
- qa-final/arch-pacman-gate.txt
- qa-final/arch-pacman-gate2.txt
- device-evidence/arch-pacman-gate.txt

## Trixie gate
Used:
~~~sh
tests/garden-debian/device/gate.sh \
  /home/lordegypt/AndroidThothTerm/archives/thothterm-debian-trixie-arm64-rootfs-f6520ff1c6ee.tar.gz
~~~

Latest result:
- 26 PASS / 0 FAIL
- exit 0

## Stay awake while long gate runs
Do not invent a permanent setting.
Capture original, enable temporarily, restore exact original.

Current original after latest session:
- 0 / off

---

# 21. Host/build closure results already achieved

Before the latest device-only continuation, the master closure had a clean host/build result:

Unit:
- garden-common 335/335
- Ubuntu 604/604
- Arch 56/56
- Trixie 40/40

Lint:
- 0 errors in all 7 relevant reports

APK verification:
- 60/60 checks on 12 app APKs
- package identity
- 16 KB zip alignment
- ELF LOAD >= 0x4000
- R8 keeps all 7 JNI methods
- Full embeds rootfs
- F-Droid does not embed rootfs

Real host extractor gates:
- Ubuntu 6564 paths
- Trixie 7049 paths, exact non-ASCII cert name
- Rolling 33639 paths, mode-0110 dbus helper verified without chmod

These must be rerun after the final source commit before release, because final artifacts/hashes must correspond to the final commit.

---

# 22. Physical device results already achieved

On the SM-A165F Android 16 kernel 6.12.38, only isolated QA apps, every install --no-incremental:

## Extractor
All three editions:
- 46 PASS each
- app's own extractor
- AndroidFileOps chmod race regression included
- setuid helper included

## First run
Full:
- Ubuntu
- Trixie
- Rolling

F-Droid:
- Ubuntu
- Trixie

Results:
- terminal opened in ~11–25 sec
- optional setup happened after terminal readiness
- no ANR

## Reviewer provisioning simulation
Hung Ubuntu sudo provisioning intentionally:
- terminal opened in ~2 sec
- terminal responded
- provisioning failed cleanly
- menu retry installed sudo

## HOME
Damage/repair/reinstall/interrupted reinstall:
- sentinel file survived
- same SHA-256
- same inode
- preserved through continuation

## LAN
Ubuntu and Rolling:
- pair
- foreign origin refused
- terminal over LAN
- upload bytes matched on phone
- cancel mid-upload
- sign-out mid-upload
- no partial files left
- LAN off closes connection

## Package managers
Ubuntu:
- apt works

Trixie:
- apt works
- dedicated gate later: 26 PASS / 0 FAIL

Rolling:
- pacman upgraded twice
- pacman -Dk clean
- pacman -Qk clean
- final deep -Qkk ownership semantics still has one real defect; see current blocker

## PRoot child tracking
All 7 fork/thread workloads pass on Samsung kernel 6.12.

Again: this does not prove recovery behavior on reviewer kernel 4.14.

---

# 23. Device-found bugs already fixed

During the physical closure session:

## ceb5c85
Ubuntu sudo check failed after install because sudoers-related file mode under PRoot was wrong.

Fixed and reverified.

## 737d308
Previous PRoot patch 0006 broke a mode-change call systemd uses, causing a pacman hook/systemd-tmpfiles error.

Fixed and reverified on phone.

## 9c4fee3
Log said "Extraction started" before download actually began.

Fixed.

## 59d2f8b
LAN test had a timing assumption / flakiness.

Fixed as a test issue.

## 6e81d66
Rolling signature-settings check looked for incorrect wording even though system was correctly configured.

Fixed without weakening signature requirements.

## Post-6e81d66 gate test fixes
Rolling rerun improved from:
- 83 PASS / 6 FAIL

to:
- **88 PASS / 1 FAIL**

P6 symlink check failure:
- test bug
- inherited permissive umask under run-as
- expected 0644 but saw 0666
- extraction itself printed no warning
- test now sets its own umask

Interrupted vim-runtime "invalid or corrupted package":
- test interpretation issue
- libalpm uses this wording for incomplete local DB metadata after interrupted install
- gate only sets aside that exact line when accompanied by exact missing-local-desc / "could not fully load metadata" evidence
- other integrity/signature wording remains a failure

These test changes are committed through:
- d2cb574

---

# 24. CURRENT REAL RELEASE BLOCKER — READ CAREFULLY

Current Rolling device gate result:
- **88 PASS / 1 FAIL**

The one remaining failure is real.

After the tzdata recovery step:

~~~sh
pacman -Qkk tzdata
~~~

reports UID/GID mismatches, e.g.:
- UID mismatch on /usr/share/zoneinfo/CET

Phone root cause confirmed:

PRoot link2symlink hardlink emulation creates a visible symlink such as:

~~~text
CET -> .l2s.CET0001
~~~

The hidden backing file is owned on the Android host by the app UID.

PRoot fake-root/fake_id0 semantics do not report the expected guest root ownership correctly for these link2symlink-emulated hardlinks.

Therefore libalpm's mtree ownership comparison sees the wrong uid/gid.

Counts observed:
- tzdata: 1093 files
- coreutils: 46 files
- pacman: 14 files

Important:
- file contents are intact
- modes are intact
- pacman -Qk reports 0 missing files
- pacman -Dk is clean
- only -Qkk ownership comparison differs

This is **not** to be hidden as a verification-only limitation.

Decision already made in chat:
**FIX IT.**

Why:
- Rolling has not shipped yet
- noisy pacman -Qkk is a bad first-release semantic defect
- ownership semantics may matter to more than pacman
- future Garden distros may also inspect ownership

Do not weaken the gate.

---

# 25. Exact requirements for the remaining PRoot ownership fix

The next implementation agent should receive this instruction essentially unchanged:

~~~text
Fix it. Do not weaken or waive the pacman -Qkk gate.

Treat the remaining UID/GID mismatch as a real PRoot metadata-semantics defect
in the interaction between fake-root ownership emulation and link2symlink
hardlink emulation.

Requirements:

- Preserve correct guest-visible ownership for link2symlink backing objects and
  their exposed symlink/hardlink representation.
- Do not chown Android app-private files to host uid 0 or require privileged
  host ownership.
- The fix must be guest-semantic emulation inside PRoot, not a host-filesystem
  ownership hack.
- Audit both creation and later stat/lstat/fstat/statx ownership reporting paths.
- Cover hardlink creation, rename, replacement, extraction, package upgrade,
  and package reinstall.
- Ensure no regression to patch 0006 or 0007.
- Add deterministic regression tests for:
  - normal file ownership
  - faked hardlink ownership
  - symlink ownership
  - link2symlink backing .l2s.* objects
  - root-owned package files viewed from guest
  - non-root guest ownership if supported
- Then rerun the full Rolling device gate unchanged.

Acceptance criterion:

pacman -Qkk tzdata
pacman -Qkk coreutils
pacman -Qkk pacman

must no longer report ownership mismatches caused by PRoot emulation.

Do not mask those lines in the gate.

If the correct implementation requires changes to PRoot ownership metadata
bookkeeping, implement them in the shared Garden PRoot patch set and document
the semantics clearly.
~~~

After that:
- rerun Rolling gate unchanged
- target = 0 FAIL

---

# 26. What remains after the ownership fix

Do not tag anything before all of these are complete.

## 1. Rolling gate
Rerun:
- tests/garden-arch/device/gate.sh ...

Requirement:
- 0 FAIL
- no gate weakening
- verify -Qkk ownership noise is gone

## 2. Trixie
Already:
- 26 PASS / 0 FAIL

Only rerun if final shared runtime/PRoot change affects Trixie.

Because the new ownership fix is in shared PRoot, likely perform an appropriate regression check on Ubuntu/Trixie too.

## 3. Publish Rolling rootfs
Rolling F-Droid/download build currently gets HTTP 404 because the rootfs release asset has not been published.

Observed UI:
- "Linux environment could not be prepared"
- logs showed rootfs=download
- rootfs archive request failed with HTTP 404

This is known infrastructure/release work, not a new extractor failure.

Publish the exact pinned Rolling rootfs file where the app/F-Droid flavor expects it.

Then test:
- fresh F-Droid/download flow
- consent
- download
- SHA verification
- retry path
- extraction
- terminal readiness

## 4. Final clean rebuild
Current older RC hashes are obsolete because later commits changed source/tests.

Rebuild at final commit:
- all release candidates
- all clean unit/lint/build/security checks
- regenerate artifact hashes
- verify package IDs
- R8/JNI
- 16 KB alignment
- Full vs F-Droid embedded-rootfs split

## 5. Goldens
Only after final green evidence:

Create immutable tags:
- arch-v0.1.0
- ubuntu-v0.3.1
- trixie-v0.2.1

Do not mutate:
- terminal-v1.4.0
- ubuntu-v0.3.0
- trixie-v0.2.0

Regular Terminal gets no new release unless impact analysis changes.

## 6. F-Droid
Ubuntu:
- MR !49556

Trixie:
- MR !50342

Rolling:
- create/update submission only after genuine Golden

Reviewer rule:
**keep only the latest Build entry**

Do not stack historical Build entries in the active New App MR metadata.

Workflow:
- squash ON
- auto-merge OFF
- never merge fdroiddata upstream ourselves

## 7. Reviewer reply
Keep draft until updated CI build exists.

Then post evidence-based reply.
No kernel-4.14 success claim.

---

# 27. F-Droid policy / metadata discipline

For active New App review MRs, reviewer asked:
- only latest Builds entry

Meaning:
- if Ubuntu becomes 0.3.1, active metadata has only the current required Build entry
- do not keep 0.3.0 and 0.3.1 together if reviewer instructed latest-only for this New App MR

Same principle for Trixie/Rolling.

Do not confuse this with deleting GitHub historical releases. Historical GitHub tags/releases remain immutable.

---

# 28. Rolling package-security rules

Fresh install must not ship:
- builder private signing keys
- stale sync DB
- unsafe package cache

Keyring:
- fresh per install
- optional/background after TERMINAL_READY

pacman 7 / Android:
- only disable filesystem sandbox part that is actually incompatible:
  DisableSandboxFilesystem
- keep other available sandbox/security behavior
- DownloadUser = alpm
- package signatures stay required

Do not "fix" package-manager tests by weakening signature settings.

Interrupted transaction test:
- use disposable QA
- prove extraction/state mutation began
- kill target once
- confirm process dead
- remove stale lock only after no package manager remains
- recover/reinstall
- then -Syu / -Dk / -Qk / -Qkk as appropriate
- signature/corrupt download is not a valid interruption success

---

# 29. Ubuntu specific release rules

Terminal must open before optional sudo/apt.

No duplicate provisioning from:
- setup
- TermActivity
- service reconnect

Optional setup:
- single-flight
- background
- stdin /dev/null
- timeout safe
- failure visible but non-blocking
- menu retry allowed

Do not keep UI at "Extracting 100%" while doing sudo provisioning.

---

# 30. Trixie specific release rules

Historical:
- trixie-v0.2.0 immutable

New intended release:
- 0.2.1 / 201

Preserve exact Unicode certificate:
- NetLock_Arany_=Class_Gold=_Főtanúsítvány.crt

Physical apt/dpkg gate:
- 26 PASS / 0 FAIL

If shared PRoot ownership patch changes guest stat semantics, run a regression on Trixie before Golden.

---

# 31. Regular Terminal separation

Regular Terminal is not Garden.

Do not accidentally add:
- Garden LAN
- distro setup
- package manager logic

to com.thothterm.

Current closure impact analysis said:
- no Regular Terminal source changed
- no release needed

Keep that unless final PRoot/shared changes prove otherwise.

---

# 32. Known limitations that must remain honest

## Kernel 4.14
Reviewer kernel:
- 4.14.357

Still unverified on a real device after patch 0007.

Do not write:
- "fixed on kernel 4.14"

Write:
- host reproduction/recovery logic tested
- Samsung kernel 6.12 device workloads pass
- physical 4.14 retest requested

## PRoot 0007 ambiguous concurrent thread creation
On a kernel that withholds PID and presents ambiguous concurrent thread creation, the design fails closed rather than guessing.

This is preferable to tracking the wrong process.

## Extractor intermediate components
Closure report documents that intermediate directories are still checked by path while the final component is atomic/no-follow.

The report considers the risk bounded by app-private staging / no concurrent external writer during install.

Do not silently remove this limitation from documentation.

---

# 33. Current branch / commit state for the next chat

Repository:
- Lord1Egypt/AndroidThothTerm

Branch:
- claude/garden-master-closure-from-9677a88

Code baseline before this handoff file:
- d2cb5745e939997fc426f3d3dcb3bb265b1d90cc

Verdict:
- RELEASE BLOCKED

Single real product blocker:
- fake-root + link2symlink ownership semantics causing pacman -Qkk UID/GID mismatches

Known infrastructure/release blocker after code:
- Rolling rootfs release asset is not published, F-Droid/download flow returns HTTP 404

No:
- Golden tags
- new GitHub releases
- F-Droid MR updates
- reviewer reply post

yet.

---

# 34. Recommended exact next-session flow

1. Read this file.
2. Read:
   - docs/garden/closure/CLOSURE_REPORT.md
   - docs/garden/arch/SONNET55_REVIEW.md
   - docs/garden/arch/CODEX_QA_839F935.md
   - docs/garden/arch/CODEX_QA_9677A88.md
   - relevant handoff docs
3. Verify:
   - branch
   - HEAD
   - clean status
4. Continue from d2cb574 (plus this documentation-only commit).
5. Fix PRoot fake-root/link2symlink ownership semantics.
6. Add deterministic host/native tests.
7. Rebuild PRoot runtime as needed.
8. Rerun unchanged Rolling physical gate.
9. If shared PRoot changed, do targeted Ubuntu/Trixie regression too.
10. Require Rolling 0 FAIL.
11. Publish exact Rolling rootfs asset.
12. Test download/F-Droid path from fresh isolated QA app.
13. Clean rebuild all final RCs at final source commit.
14. Re-run unit/lint/security/APK/R8/JNI/16KB checks.
15. Final physical smoke where final PRoot/artifacts changed.
16. Only then:
    - tag arch-v0.1.0
    - tag ubuntu-v0.3.1
    - tag trixie-v0.2.1
17. Generate final hashes/release notes.
18. Update F-Droid MRs latest-Build-only.
19. Keep squash ON, auto-merge OFF.
20. Post reviewer reply with honest 4.14 wording.
21. Cleanup QA apps only after release work is safe.

---

# 35. Recommended Sonnet 5.5 continuation prompt

Use this for the current narrow phase:

~~~text
Continue the Garden release closure from the verified current branch
claude/garden-master-closure-from-9677a88.

The code baseline before the ChatGPT handoff documentation commit was:
d2cb5745e939997fc426f3d3dcb3bb265b1d90cc

Read first:
- docs/garden/closure/CHATGPT_CONTINUITY_HANDOFF_2026-10-03.md
- docs/garden/closure/CLOSURE_REPORT.md

Do not re-audit already closed areas unless the remaining defect directly points
back to them.

Current verdict: RELEASE BLOCKED.

The one real Rolling product defect left is PRoot ownership semantics for
link2symlink-emulated hardlinks under fake root. The unchanged Rolling device
gate is 88 PASS / 1 FAIL because pacman -Qkk sees UID/GID mismatches.

Root cause confirmed on the phone:
- /usr/share/zoneinfo/CET is represented as CET -> .l2s.CET0001
- backing objects are host-owned by the Android app UID
- fake_id0/link2symlink do not expose the expected guest root ownership
- mismatches: tzdata 1093, coreutils 46, pacman 14
- contents/modes intact
- pacman -Qk clean
- pacman -Dk clean

Fix it. Do not waive or mask the -Qkk failure.

Requirements:
- correct guest-visible ownership semantics
- no host chown-to-root hack
- no Android privileged ownership requirement
- audit stat/lstat/fstat/statx and link2symlink bookkeeping
- cover creation, rename, replacement, extraction, package upgrade/reinstall
- no regression to PRoot patches 0006/0007
- deterministic regression tests for normal files, faked hardlinks, symlinks,
  .l2s backing objects, guest-root ownership and non-root guest ownership if
  supported
- rerun the exact unchanged Rolling physical gate
- acceptance: pacman -Qkk tzdata/coreutils/pacman has no PRoot-caused ownership
  mismatches and Rolling gate reaches 0 FAIL

If shared PRoot behavior changes, run targeted Ubuntu/Trixie regression too.

After the code/device gate is green:
1. publish Rolling's exact pinned rootfs release asset
2. test the F-Droid/download flow (currently HTTP 404)
3. clean rebuild RCs from the final source commit
4. rerun unit/lint/security/APK/R8/JNI/16KB gates
5. run final physical smoke required by changed PRoot/artifacts
6. only then create immutable tags:
   arch-v0.1.0
   ubuntu-v0.3.1
   trixie-v0.2.1
7. update F-Droid MRs with latest Build entry only
8. squash ON, auto-merge OFF, never merge upstream ourselves
9. post the Ubuntu reviewer reply only after updated CI evidence exists
10. explicitly keep kernel 4.14 as physically unverified unless the reviewer or
    another real 4.14 device tests it

Do not tag or publish if any mandatory gate fails.
Return either:
GARDEN RELEASE CLOSURE COMPLETE
or:
RELEASE BLOCKED: <one concrete remaining gate>
~~~

---

# 36. If Codex is brought back for final independent QA

Codex must not edit source.

Suggested final QA scope after Claude/Sonnet says closure complete:

- inspect diff from d2cb574 to final source HEAD
- focus on new PRoot ownership semantics
- verify no gate weakening
- verify -Qkk ownership fix is real
- check link2symlink + fake_id0 stat paths
- run host/native regression
- real SDK36 build
- R8/JNI 7/7
- 16 KB alignment
- package IDs
- Full/F-Droid split
- final physical QA if device available
- check tags point exactly to tested commit
- check release hashes
- check F-Droid latest-Build-only metadata

Final Codex verdict should be either:
- QA PASS FOR RELEASE
or
- RETURN TO CLAUDE

No source edits.

---

# 37. Release naming / immutability checklist

Never move historical tags.

Historical:
- terminal-v1.4.0
- ubuntu-v0.3.0
- trixie-v0.2.0

Planned:
- arch-v0.1.0
- ubuntu-v0.3.1
- trixie-v0.2.1

Every Golden needs:
- clean tree
- exact source SHA
- tag
- package ID
- versionName/versionCode
- build flavor
- APK/artifact SHA-256
- build toolchain summary
- evidence references
- honest limitations
- no unperformed validation claim

RC != Golden.

---

# 38. Rootfs publication reminder

The Rolling F-Droid/download flavor currently fails before extraction with HTTP 404 because the rootfs asset is not published.

This was visible on the phone:

- rootfs=download
- NOT_INSTALLED
- request failed HTTP 404
- setup correctly showed failure/retry UI

Do not misdiagnose this as:
- PRoot failure
- extractor failure
- network resolver failure

Publish the exact pinned artifact to the URL/catalog location expected by the app.

Then test from a fresh isolated QA install.

---

# 39. Evidence locations worth preserving

Main closure:
- docs/garden/closure/CLOSURE_REPORT.md

Reviewer response draft:
- docs/garden/closure/FDROID_REVIEWER_REPLY_49556.md

Older reviews:
- docs/garden/arch/SONNET55_REVIEW.md
- docs/garden/arch/CODEX_QA_839F935.md
- docs/garden/arch/CODEX_QA_9677A88.md

Device evidence:
- device-evidence/
- qa-final/

Arch gate evidence:
- device-evidence/arch-pacman-gate.txt
- qa-final/arch-pacman-gate2.txt

Host evidence mentioned in closure:
- evidence/toctou-before-9677a88.txt
- evidence/toctou-after.txt
- evidence/proot-fork-recovery-host.txt
- evidence/final-host-gate-*.txt
- evidence/final-rc-sha256.txt

Do not delete evidence before release closure.

---

# 40. Things the next chat must NOT do

Do not:
- restart from c49bb03 unless investigating historical regression
- treat 5ae7c2b as trusted baseline
- use adb incremental install
- touch unknown 192.168.1.100 device
- test against production package data
- pm clear/uninstall production apps
- loosen pacman signature policy
- suppress -Qkk ownership mismatch
- hide PRoot warnings instead of fixing semantics
- block terminal on sudo/apt/pacman/keyring
- run long Process.waitFor on main thread
- silently delete /home/thoth
- mutate historical Golden tags
- tag an RC
- update F-Droid before Golden
- auto-merge F-Droid
- claim kernel 4.14 physical success without a real 4.14 device
- start BlackArch before Rolling closure
- treat Horus code as something to copy blindly

---

# 41. Current strategic decision

We explicitly chose:

**Fix the remaining PRoot ownership problem before Rolling 0.1.0.**

We rejected:
- documenting it away as a verification-only limitation
- weakening the gate
- accepting noisy pacman -Qkk on first release

The reasoning:
- this is the best time to fix ownership semantics before public Golden
- package managers and future distros may depend on accurate ownership
- contents/modes being correct is not enough if guest metadata semantics are wrong

This decision should remain unless new evidence shows the gate itself is wrong.

---

# 42. Quick "state of the Garden" answer for a new chat

If Mohamed asks "هات حالة المشتل", answer approximately:

- Branch: claude/garden-master-closure-from-9677a88
- Baseline before handoff-doc commit: d2cb574
- Verdict: RELEASE BLOCKED
- Host/build/security: previously green; must be rerun at final commit
- Samsung physical:
  - extractors PASS
  - Ubuntu/Trixie/Rolling startup PASS
  - provisioning non-blocking PASS
  - HOME PASS
  - LAN PASS
  - Ubuntu/Trixie apt PASS
  - Trixie dedicated gate 26/26
  - PRoot fork workloads PASS on kernel 6.12
- Rolling pacman gate: 88 PASS / 1 FAIL
- remaining real defect: fake-root + link2symlink ownership causes pacman -Qkk UID/GID mismatches
- Rolling rootfs download asset still unpublished -> F-Droid/download HTTP 404
- no Goldens yet
- no F-Droid changes yet
- reviewer reply still draft
- kernel 4.14 still physically unverified
- next: fix PRoot ownership -> rerun Rolling -> publish rootfs -> final clean rebuild -> Goldens -> F-Droid

---

# 43. Final note to future ChatGPT

The user is intentionally using ChatGPT as the project-state supervisor while Claude implements and Codex audits.

Your job in the new chat is not to make him repeat history.

Read this handoff and the closure report, keep the exact state, and continue from the remaining blocker.

When giving Claude prompts:
- make them copy-paste ready
- preserve exact SHAs/branches
- specify what must not be changed
- define evidence and stop conditions
- prefer one coherent closure prompt over many tiny ping-pong prompts
- once scope becomes narrow, keep the continuation prompt narrow

When reading agent output:
- separate product bug vs test bug vs environment block
- do not count UNVERIFIED as FAIL
- do not accept "works for me" as release proof
- preserve user-data safety
- protect production Android packages
- protect historical tags
- keep F-Droid reviewer wording factual and non-defensive

The release is close, but the last real PRoot metadata defect must be closed cleanly before the first Rolling Golden.
