# Draft reply to @visheh10 on fdroiddata!49556 — NOT POSTED

Post only after ThothTerm Ubuntu 0.3.1 is tagged, the MR's Builds entry
points at it, and its CI pipeline is green. Replace the bracketed parts then.

---

Thank you for the second logcat and the process list; they changed our
understanding of the problem.

What your log showed:

- Extraction itself completed. The 115 hard-link fallbacks worked as
  intended, and "Extraction complete" came about 7 seconds after the download
  was verified. The archive and the hard-link fallback were not the failing
  stage.
- The screen kept saying "Extracting Ubuntu… 100%" while the app ran a
  *different* step: installing sudo (apt) before opening the terminal. That
  step hung until its 3-minute timeout.
- After that, opening the terminal started the same provisioning again and
  waited for it on the main thread, which is what caused the ANRs.
- The stuck processes were a shell in state D and its child in state t,
  next to `proot warning: ptrace(GETEVENTMSG): Invalid argument`.

What 0.3.1 changes:

1. **The terminal no longer waits for sudo.** It opens as soon as the system
   is extracted, verified and configured. Installing sudo runs afterwards in
   the background, at most once at a time, and never on the main thread. If
   it fails, the terminal still works and the menu offers a retry. Each stage
   is logged on its own ("Setup stage …" lines with timings), so the screen
   no longer shows 100% during an unrelated step.
2. **PRoot child tracking (separate fix).** When the kernel does not report a
   new child's pid at a fork event, PRoot used to leave that child stopped
   forever: the "t" process, with its vfork parent in "D". It now finds the
   child from /proc (traced by this PRoot, not yet set up, with the expected
   parent). If several children could match, it stops the session with an
   error instead of guessing. We reproduced exactly your process states on a
   desktop kernel by forcing the pid to be withheld, and with the fix the same
   workloads complete. We do **not** know why your kernel withholds the pid.
3. `/etc/mtab` is now bound from `/proc/self/mounts`, which avoids the
   "can't sanitize binding /proc/mounts" warning path. We could not reproduce
   that warning here.

What we tested: [fill in from the final device gate: device, Android
version, kernel]. We have **no** device with a 4.14 kernel or crDroid, so the
fix is not proven on your setup.

If you have time, could you try the updated CI build
([link to the 0.3.1 pipeline artifact]) on the same Redmi Note 10 Pro, and
send `adb logcat -s ThothTerm` from first launch until the terminal opens? We
would especially like to know whether the terminal opens without waiting,
and whether `sudo` becomes available afterwards.
