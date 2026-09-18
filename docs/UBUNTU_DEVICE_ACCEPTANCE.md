# ThothTerm Ubuntu physical-device acceptance

Use an ARM64 Android device. Capture diagnostics only after reproducing a
failure; never include terminal contents, command history, credentials, or
private keys.

## Clean install and first run

1. Uninstall `com.thothterm.ubuntu`, then install the current debug APK.
2. Disable Wi-Fi and mobile data. Launch from the new Garden flower icon.
3. Confirm setup says **Preparing your Linux environment** and extraction
   advances monotonically through real percentages to 100%; rotate once and
   confirm extraction neither restarts nor moves backward.
4. Confirm the terminal opens offline. The small colored ASCII flower appears
   once, followed by one clean prompt. No `cd ~`, `clear`, `echo`, or `source`
   line should look user-typed.
5. Confirm the default canvas is deep midnight, text is neutral and readable,
   the prompt uses restrained amber/teal accents, and AMOLED/custom color
   choices still work.

## Runtime identity and environment

Run:

```sh
id
id -u
whoami
groups
printf '%s\n' "$USER" "$HOME" "$LOGNAME" "$SHELL" "$PATH" "$TERM"
pwd
locale
stty size
cat /etc/os-release
uname -m
uname -a
ls -ld /tmp /dev /proc /sys
cat /etc/hostname
cat /etc/hosts
cat /etc/resolv.conf
```

Expected V1 identity is explicit PRoot fake-root: `id -u` and `whoami` report
`0`/`root`, `USER` and `LOGNAME` are `root`, `HOME` and `pwd` are
`/home/thoth`, and the prompt ends in `#`. Android itself remains unprivileged.
`sudo` is intentionally absent and unnecessary in this model.

## Networking and packages

1. Re-enable the desired Android network and open a **new window**.
2. Run `getent hosts archive.ubuntu.com`; it must return an address.
3. Run `apt --version`, then `apt update`; repository metadata refresh must
   complete without a DNS-resolution error.
4. Optionally install and remove one tiny package to validate dpkg writes and
   `--link2symlink`; do not install a toolchain.
5. Switch between Wi-Fi and mobile data, wait a moment, and repeat the lookup
   in the existing window to check live resolver refresh.

## Terminal semantics and accessibility

1. Check ASCII, digits, punctuation, `_`, `|`, long paths, ANSI colors,
   backspace, command history, wrapping, and cursor position at several sizes.
2. Type wide CJK characters, combining marks, Arabic, and mixed Arabic/English;
   verify shaping, BiDi placement, selection, cursor geometry, and deletion.
3. Repeat in portrait and landscape with the keyboard shown and hidden.
4. Verify both Extra Keys pages, Ctrl+C on a running command, and Ctrl+D in a
   disposable shell/window.
5. Change font source and size, then return to the built-in default and confirm
   the compact cell geometry remains aligned.

## Sessions, persistence, and lifecycle

1. Run `touch ~/testfile`; open two New Windows and confirm all see it.
2. Close one window and confirm the others continue running.
3. Background/foreground the app, rotate it, switch theme, and confirm no
   duplicate or orphan shell appears.
4. Close and relaunch the app; `~/testfile` and command history must remain and
   extraction must not run again.
5. Force-stop and relaunch; confirm the rootfs remains and normal TermService
   ownership/exit behavior is preserved.
6. Disable **Settings → Linux → Show welcome banner**, open a new window, and
   confirm no automatic banner appears; `thothfetch` must still work manually.

## Privacy and recovery

1. In Diagnostics, confirm useful ROOTFS/PROOT/NETWORK/SESSION state appears,
   but no typed commands, terminal output, DNS addresses, environment values,
   clipboard, tokens, keys, or private file contents appear.
2. Test an offline launch: Linux must still open; network failure must not be
   described as rootfs corruption.
3. If practical on a disposable install, constrain free space and verify setup
   fails cleanly with Retry/View Logs and leaves no accepted partial rootfs.
4. Launch the regular ThothTerm Terminal Emulator and repeat a short font,
   cursor, wrapping, Arabic/RTL, Extra Keys, portrait, and landscape smoke test.

