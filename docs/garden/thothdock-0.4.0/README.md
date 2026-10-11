# ThothTerm • ThothDock 0.4.0 (ThothDock engine 0.2.0, NextGen) — release candidate QA

The full record is in the ThothDock repository: `docs/nextgen/QA-CLOSURE-2026-10-10.md`
(final hardening round, sections 1-8), `docs/nextgen/RELEASE_NOTES-0.2.0.md` and
`docs/nextgen/evidence/final-release/`. Summary of what this app commit changes and how it was checked:

| Change | Check |
|---|---|
| Web Panel browser sessions end with the panel process (P1); Disconnect all devices button; the Android Stop/Start/New code/Disconnect all devices are distinct | `tests/panel/device-lifecycle.sh` 24/24 on the SM-A165F, driven through this UI; desktop-browser screenshots |
| Process Guard warning in the Containers screen (`/thothdock/process-guard`) | seen on the phone at 24 processes; older engines answer 404 and show nothing |
| The engine's exit code is written to `thothdock/last-exit.json` when the supervisor sees it die, for the next daemon's recovery report | unit-free Java; exercised by `am force-stop` (no code, since the app itself is dead) — the code path with an exit code is not exercised on the phone |
| versionCode 400 / versionName 0.4.0, ThothDock 0.2.0 pinned (`garden-common/thothdock/thothdock.properties`) | in-place `adb install -r` of a QA build over 0.3.1-based builds kept `/home/thoth`, the engine data root and restored the four running containers |

Not exercised on the release candidate: a clean first install (needs the Debian download and a
second engine; running two engines at once pushed the phone over Android's process cap before),
the production-signed upgrade over 0.3.1 (needs the owner's key), and LAN Mode / Engine Guard
hardening re-runs (no change in those files).
