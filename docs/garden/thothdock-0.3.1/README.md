# ThothTerm • ThothDock 0.3.1 — device QA (2026-10-07)

Samsung SM-A165F, Android 16, kernel 6.12.38, on AC power. Isolated QA package
`com.thothterm.debian.qa.v031` (debug build of commit 1664cca, ThothDock v0.1.1 at 814e193; the
release commit differs from it only in documentation). The installed production apps were not touched; the
QA package was uninstalled at the end and left no process behind.

| Check | Result |
|---|---|
| First launch shows the consent screen named "ThothTerm • ThothDock"; nothing downloaded or created under `files/linux` before consent | PASS |
| Download Debian after consent, setup to TERMINAL_READY | PASS (15.1 s, 8.0 s of it the download) |
| Drawer header, notification text, launcher label, About version | "ThothTerm • ThothDock", 0.3.1 |
| #6: Engine Guard enforcement independent of dpkg: the hook, apt configuration and pin were deleted from the guest, the app was relaunched and all three were back (hook first) before any dpkg run | PASS |
| `apt update`, `apt upgrade`, `apt full-upgrade` | PASS |
| #3: stock `docker.io` downgrade, `docker-ce`, `moby-engine`, `containerd`, `runc` refused / no-op; real apt hook error | PASS |
| #3: legitimate placeholder update `9999:1.0+thothdock.2` over `.1` installs; `full-upgrade` afterwards is clean | PASS |
| `thothdock doctor --guard`, no dockerd/containerd/runc | PASS |
| docker version/info/ps/pull/run/exec/logs/stop/start/restart/volumes/`-p` TCP/missing command 127 | 13/13 PASS |
| Containers screen agrees with the CLI (running count, image count, port, names); Stop from the UI is seen as `exited` by the CLI | PASS |
| #4: daemon SIGKILL: supervisor restarts it, containers recorded `Exited (137)`, no container process left | PASS |
| Background (20 s), foreground: daemon and published port keep working | PASS |
| Force-stop: every daemon and PRoot process gone; the socket file remains until the next launch but nothing listens; relaunch removes it | PASS (known, documented) |
| Exit (last window closed): no process, empty socket directory, pid file removed, published port refused | PASS |
| No staging directories left in the store; uninstall leaves no process | PASS |
| LAN Mode default | Off |

Not verified on the device: #5 (the pull ceilings are covered by the host tests and a host
regression with a real PRoot), network capture before consent (no capture tool is installed), a kernel
4.14 device.
