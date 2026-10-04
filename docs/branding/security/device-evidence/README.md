# ThothTerm Security branding — device evidence

Samsung SM-A165F (Android 16), isolated QA build `com.thothterm.security.qa.srel`
(minified release, debug-signed, installed with `--no-incremental`), 2026-10-04.

| File | Shows |
|---|---|
| `04-terminal-banner.png` | Terminal: the shared `thothfetch` ASCII mark unchanged, drawn in silver and gunmetal; the edition name "ThothTerm Security", "Arch Linux ARM", labels and values in the metallic roles; the prompt `thoth@thothterm:~$` with user, host and path in separate roles |
| `08-app-info-launcher-icon.png` | Android's app-info page: the real launcher icon beside "ThothTerm Security" (the phone runs in Arabic; the system text is the phone's) |

The consent screen, the LAN screen and the repository consent dialog were read
as text from the live UI by the device gate (`tests/garden-security/device/`),
not archived as images. The earlier screenshots of this edition showed another
project's name in the banner and were removed with the rename.
