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
