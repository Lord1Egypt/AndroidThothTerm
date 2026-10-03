# Session lifecycle under PRoot — the Garden rule

**Rule.** A terminal window's PRoot session must behave like a real terminal:

- When the window's shell exits, the rest of its session is hung up.
- `nohup`'d and `setsid`'d processes survive, as they would on Linux.
- No guest process may ever outlive the PRoot that traces it.

## How it is achieved

The runtime is PRoot patch
`term-ubuntu/patches/0004-hang-up-the-session-on-command-exit-and-never-outlive-proot.patch`.

- **`--hangup-on-exit`** (interactive windows and LAN browser terminals). When
  the main command (`su` → `bash`) exits, PRoot does what the kernel does on a
  terminal hangup:
  - It sends SIGHUP, then SIGCONT, to every remaining tracee still in PRoot's
    session. PRoot is the session leader the terminal belongs to.
  - It points its own stdin, stdout and stderr at `/dev/null`.
  - Processes that ignore SIGHUP or left the session keep running, and PRoot
    keeps tracing them until they end.
- **`PTRACE_O_EXITKILL`** (always). If PRoot dies, for example when Exit
  SIGKILLs it, the kernel kills every tracee. Without it, a killed PRoot left
  its tracees running untraced, where paths and syscalls are no longer
  translated.
- **`--kill-on-exit`** (provisioning only). A setup script must leave nothing
  behind, because the app waits for its PRoot to exit.

## How the app closes things

- **A phone window closes when nothing holds its terminal any more**
  (`exitOnEOF`), not when PRoot exits. PRoot may run on for a nohup'd job
  after the shell is gone.
- **A LAN browser terminal ends on the same signal:** its output pump reaching
  end of file.
- **Closing a window** hangs up its session from Java (`SessionHangup`). A
  400 ms backstop SIGKILLs whatever survived without ignoring SIGHUP. PRoot
  itself is left alone so a nohup'd job survives.
- **Exit** hangs up every session, then SIGKILLs every PRoot. `EXITKILL`
  takes nohup'd jobs down with it. Exit means everything stops.

## History

- Up to ubuntu-v0.1.7, app processes ignored SIGHUP, so closing a window
  never hung anything up. The kernel only reaped the shell when its terminal
  read failed.
- With `--kill-on-exit`, the whole tree was killed with the shell, so a
  nohup'd job never survived a window close.
- 0.2.0 fixes both: the SIGHUP reset in libtermexec, and this patch.

## Tests

`tests/proot-runtime/host-test.sh`, run by `ProotRuntimeHostTest` on Linux
build hosts, checks against a native build:
- a background job ends;
- `nohup` and `setsid` jobs survive;
- PRoot releases the terminal and exits after the survivors;
- a SIGKILLed PRoot takes its tracee with it.

The on-device window and Exit behaviour is part of the release acceptance.
