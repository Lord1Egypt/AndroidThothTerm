# ThothTerm • ThothDock (Debian 13 trixie) — LAN Mode acceptance on a phone

Playwright (Chromium) scripts run from a computer on the phone's network
against the installed app with LAN Mode on. `PHONE` is the phone's address
(default 192.168.1.103), `OUT` a directory for screenshots, `TAP` a guarded
tap helper (`tap.sh ACTIVITY X Y`, refusing unless that activity is in front).
Read each PIN from the phone's LAN Mode screen.

| Script | What it proves | Result 2026-09-27, SM-A165F |
|---|---|---|
| `debian_e2e.py PIN [PORT]` | pairing, edition branding, Debian on a real PTY, sudo, the required Arabic strings, ANSI Arabic, logical copy/paste, resize, Ctrl-C, scrollback, reconnect, separate PTYs, sign-out revocation | 35/35 PASS |
| `lan_neg.py HOST PORT PIN` | Host and Origin checks, WebSocket refused before upgrade, forged and URL tokens refused, path traversal, rate limit, lockout | 13/13 PASS |
| `deb_pair.py PIN OUT.json` then `lan_multi_off.py` | two browsers, isolated PTYs; LAN Mode OFF on the phone ends both at once and closes the port | 4/4 PASS |
