# Garden LAN port registry

Each Garden edition's LAN Mode tries its own default port first, so the
editions can run side by side on one phone. The default is data:
`lanPort` in the edition's `assets/garden/distro.properties`
(`term-ubuntu` still keeps its own constant).

| Port | Edition | Package | Since |
|---|---|---|---|
| 7681 | ThothTerm Ubuntu | `com.thothterm.ubuntu` | ubuntu-v0.2.0 |
| 7682 | ThothTerm Trixie | `com.thothterm.debian` | trixie-v0.1.0 |
| 7683 | ThothTerm Rolling | `com.thothterm.arch` | arch-v0.1.0 |
| 7684 | ThothTerm BlackArch | `com.thothterm.blackarch` | not released (provisional; `docs/garden/blackarch/DESIGN.md`) |

The next edition takes **7685**. A port, once assigned, never changes: users
bookmark `http://<phone>:<port>/`.

When the default is taken — by another edition's LAN Mode or anything else —
LAN Mode binds the next free port above it (up to nine further ports,
`LanServer.start`) and shows the port it actually bound. It never takes over a
socket something else holds: a bind that fails simply moves on. So a
collision costs a different URL, never a broken session.

Every port is bound only on the phone's private IPv4 address on Wi-Fi, or
failing that Ethernet (RFC 1918: 10/8, 172.16/12, 192.168/16;
`LanAddresses.choose`), never on mobile data, never through a relay or UPnP.
