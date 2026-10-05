# ThothDock QA integration (branch qa/thothdock-integration)

QA-only: nothing here ships in a production edition, a tag or a release.

- Build: `./gradlew :garden-debian:assembleFullDebug -PthothtermQaApplicationIdSuffix=.qa.thothdock -PthothtermThothDockSource=<ThothDock checkout>`
  (application id `com.thothterm.debian.qa.thothdock`). Without the source
  property `stageThothDock` deletes anything staged earlier, and with it but
  without a QA suffix the build fails, so production builds cannot carry the
  daemon or the Docker CLI.
- `garden-common/tools/stage-thothdock.sh` builds ThothDock from the pinned
  commit with `git archive` (reproducible: two builds are byte-identical),
  and extracts only `docker/docker` from the official static Docker 29.8.1
  tarball, verifying tarball and binary SHA-256. They are packaged as
  `libthothdock.so` and `libdocker.so` in `nativeLibraryDir`, beside `libproot.so`.
  Pins: `garden-common/thothdock/thothdock.properties`.
- `com.thothterm.dock.ThothDock` supervises the daemon outside the guest:
  single-flight start on a supervisor thread (never the main thread), readiness
  by `/_ping` on the socket, restart with backoff (3 per minute), SIGTERM then
  SIGKILL on stop, a pid file to find a daemon left by a restarted app, and
  `--exit-with-parent`.
- The guest sees `files/thothdock/sock` at `/run/thothdock` and the CLI at
  `/usr/local/bin/docker`; `DOCKER_HOST=unix:///run/thothdock/thothdock.sock`.
- The notification shows "ThothDock: running/starting/stopped/unavailable".
  The terminal never waits for the daemon.

## Look (branding round)

`garden-debian/src/thothdock/` is an overlay on the **debug** source set, added
by `garden-debian/build.gradle` only when `-PthothtermThothDockSource` is given:

- `res/mipmap-*`, `res/drawable-nodpi/ic_splash_mark.webp`: launcher and in-app
  mark, generated from the approved icon (`docs/branding/thothdock/source/`).
- `res/values/thothdock_tokens.xml`: the design tokens, the single source of
  hex values for Android. `colors.xml` maps Garden's eleven `brand_*` roles onto
  them. `strings.xml`: title "ThothTerm • ThothDock".
- `assets/garden/palette.properties`: the terminal palette (background,
  default text, cursor, prompt); `check_thothdock_palette.py` keeps it in step
  with the tokens and checks 4.5:1 contrast. `distro.properties`: the same as
  Trixie's with `editionName=ThothDock`.
- `thothfetch` (shared) prints the ThothDock layout only when the Docker CLI is
  bound at `/usr/local/bin/docker`; it shows `Engine ThothDock` when the socket
  exists and `Engine Offline` when it does not. Other builds print exactly what
  they did before.

## Golden candidate additions (branch feature/thothdock-golden)

Still QA-only: the overlay is compiled in only with
`-PthothtermThothDockSource` plus a QA application id suffix, so no production
build, package id, tag or F-Droid metadata changes.

- **Engine Guard** (`dock/EngineGuard.java`, `RootfsManager.runGuestAdmin`):
  after the guest exists, installs the `thothdock-engine-guard` and placeholder
  packages (`docker.io`, `docker-ce`, `docker-engine`, `moby-engine`,
  `containerd`, `containerd.io`, `runc`) with a dpkg install in the guest, so
  `apt full-upgrade` and `apt install docker.io` cannot replace ThothDock. The
  Docker CLI package stays upgradable. `thothdock doctor --guard` reports it.
- **Containers screen** (Settings, Containers; `TermActionBar`, `menu_term.xml`,
  overlay `ContainersActivity`): a pure client of the ThothDock socket via
  `ApiClient` (one write per request, one retry for GET, visible errors).
  Start, Stop, Restart, Logs (bounded), Delete with confirmation, Shell
  (types `docker exec -it NAME sh` into the terminal). No CPU or memory
  figures, no Compose.
- **Branding polish**: selection and ANSI blue palette roles
  (`GardenPalette`, `ColorScheme`, renderers), graphite popups and dialogs,
  wordmark in the Containers header.
- **Lifecycle**: identity-checked stale socket and pid cleanup, atomic socket
  creation, single instance, daemon dies with the app. Closing the last
  window or choosing Exit stops the daemon and containers.
- Tests: `garden-common` 357, `garden-debian` 28 (including `ApiClientTest`
  and `PageAlignmentTest`, which now expects the ThothDock ELF pair), 0
  failures. Evidence: ThothDock `docs/evidence/golden/`.
- Golden APK: `ThothTerm-ThothDock-Golden-QA.apk`, applicationId
  `com.thothterm.debian.qa.thothdock`, versionName `0.2.2-thothdock-golden.1`,
  versionCode `202901`, debug-signed with the Android Debug key.
