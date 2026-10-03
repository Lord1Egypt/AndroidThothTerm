# Posted on fdroiddata!49556 (note 3949190509, 2026-10-03)

@visheh10 thank you for the second logcat and the process list; they changed our understanding of the problem.

**What your log showed**

- Extraction itself completed. The 115 hard-link fallbacks worked as intended and "Extraction complete" came about 7 seconds after the download was verified. The archive and the hard-link fallback were not the failing stage.
- The screen kept saying "Extracting Ubuntu… 100%" while the app ran a *different* step: installing sudo (apt) before opening the terminal. That step hung until its 3-minute timeout.
- After that, opening the terminal started the same provisioning again and waited for it on the main thread, which is what caused the ANRs.
- The stuck processes were a shell in state `D` and its child in state `t`, next to `proot warning: ptrace(GETEVENTMSG): Invalid argument`.

**What 0.3.1 (versionCode 301, tag `ubuntu-v0.3.1`, commit `54a965f`) changes**

1. **The terminal no longer waits for sudo.** It opens as soon as the system is extracted, verified and configured. Installing sudo runs afterwards in the background, one run at a time, never on the main thread; if it fails the terminal still works and the menu offers a retry. Each stage is logged separately (`Setup stage …` with timings), so the screen no longer shows 100% during an unrelated step.
2. **PRoot child tracking (a separate fix).** When the kernel does not report a new child's pid at a fork event, PRoot used to leave that child stopped forever, which matches your `t` child and `D` parent. It now looks for the child in /proc (traced by this PRoot, not yet set up, with the expected parent). If more than one child could match, it stops the session with an error instead of guessing.
3. `/etc/mtab` is now bound from `/proc/self/mounts`, which avoids the "can't sanitize binding /proc/mounts" path. We could not reproduce that warning ourselves.

**What we tested, and what we did not**

- On a desktop Linux kernel we forced the fork-event pid to be withheld in a test build. With the previous patches that reproduces your process states (parent `D`, child `t`, the `GETEVENTMSG` warning); with the fix, fork, vfork, posix_spawn, CLONE_PARENT and thread workloads complete. Concurrent thread creation inside one process cannot be matched to a parent in that situation, so there the session stops with an error rather than hanging.
- On a Samsung SM-A165F (Android 16, kernel 6.12.38) we ran Ubuntu 0.3.1 QA builds of this commit (isolated application id): first run to a usable terminal in about 14 s with sudo installed in the background afterwards (no ANR); the F-Droid flavour with the apt mirror made unreachable, so sudo installation fails: the terminal still opened within about 2 s and took input, setup reported the failure, and the menu retry installed sudo once the mirror was restored; /home preservation checks through damage, repair and an interrupted-then-continued reinstall. The same PRoot runtime (built into a Rolling QA build of this commit) ran seven fork/thread workloads there. That phone reports the fork pid normally, so it did not exercise the recovery path itself.
- We tested the QA build of this commit, not the CI artifact from this pipeline.
- **We have not verified kernel 4.14 or crDroid.** The fork-child fix is implemented and covered by our host and device validation above, but your exact environment (Redmi Note 10 Pro, crDroid 12.11, kernel 4.14.357) remains unverified, and we do not know why that kernel withholds the pid. It needs your retest.

**Could you retest?** The updated CI build for this MR is the `fdroid build` job of pipeline [2909137062](https://gitlab.com/Lord1Egypt/fdroiddata/-/pipelines/2909137062) (commit f28fe532; the job is https://gitlab.com/Lord1Egypt/fdroiddata/-/jobs/16912420061, artifact `fdroiddata_build_com.thothterm.ubuntu-0.1.2_f28fe532…zip`; all 9 jobs are green). If you can, please send `adb logcat -s ThothTerm` from first launch until the terminal opens, and tell us whether the terminal opens without waiting and whether `sudo` becomes available afterwards. If it still hangs, the `ps` state of the stuck processes and the `proot warning` lines would tell us whether the new recovery path ran.

Separately: the earlier checkupdates failure on this MR was a metadata issue (the version detector could not read the app id in the new build file); it is fixed in the last commit and does not change the release source.
