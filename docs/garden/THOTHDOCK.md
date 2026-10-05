# ThothDock in ThothTerm Trixie

ThothTerm Trixie 0.3.0 and later bundles ThothDock: a Docker Engine API 1.41
daemon (Apache-2.0, [Lord1Egypt/ThothDock](https://github.com/Lord1Egypt/ThothDock))
that runs containers through the PRoot this app already ships. It starts no
`dockerd`, `containerd` or `runc`, and none is packaged.

## What is in the app

| Piece | Where | Built from |
|---|---|---|
| ThothDock daemon | `lib/arm64-v8a/libthothdock.so` | `third_party/thothdock` at the pin in `garden-common/thothdock/thothdock.properties`, Go, `-trimpath`, vendored modules |
| Docker CLI (client only) | `lib/arm64-v8a/libdocker.so` | `third_party/docker-cli` at tag v29.8.1, unmodified, its own `vendor/` tree |
| Engine Guard packages | `assets/thothdock/engine-guard/*.deb` | `engine-guard/build.sh` in ThothDock |
| Supervisor, Containers screen, branding | `garden-common/.../dock/`, `garden-debian/src/thothdock/` | this repository |

`stage-thothdock.sh` downloads nothing. It needs a Go toolchain of at least the
version in the pins on `PATH`; both binaries are byte-identical across build
directories. `-PthothtermThothDock=off` builds the plain Trixie edition and
removes anything staged earlier.

## How it runs

The daemon runs in the app's own context, supervised by `ThothDock.java`:
single-flight start on a supervisor thread, readiness by `/_ping`, restart with
backoff, SIGTERM then SIGKILL on stop, a pid file with the process start time
to recognise a daemon left by a restarted app, and `--exit-with-parent`. Its
API socket is `files/thothdock/sock/thothdock.sock` (mode 0600 in an owner-only
directory), bound into the guest at `/run/thothdock`; `DOCKER_HOST` is set for
the terminal. Closing the last terminal window is Exit: the daemon and every
container stop.

## Engine Guard

Placeholder packages at epoch 9999 for `docker.io`, `docker-ce`,
`docker-engine`, `moby-engine`, `containerd`, `containerd.io` and `runc`, an apt
pin and a dpkg hook keep `apt` from installing a real engine over ThothDock. The
Docker CLI packages are not guarded and stay updatable.

## Network and downloads

Nothing is downloaded in the background. Container images are fetched from a
registry over HTTPS only when the user runs `docker pull` or `docker run`.

## Limits

See ThothDock's `docs/KNOWN_LIMITATIONS.md` and `docs/SECURITY_MODEL.md`.
Containers are userspace environments, not a security boundary; a published
port is a device-wide loopback listener; UDP is refused; there are no CPU or
memory metrics.

## Release candidates and application ids

`-PthothtermQaApplicationIdSuffix=.rc.thothdock` builds an isolated candidate
(`com.thothterm.debian.rc.thothdock`) beside the installed production apps, with
its own private storage and a PRoot runtime built for that id.

## Reproducibility

The unsigned production-id `fdroid` release APK is byte-identical across
builds (evidence: `docs/garden/thothdock-rc1/reproducibility-rc1.txt`): two clean
builds in one work tree, a build from a fresh clone at another path, and the
GitHub Actions build all give the same SHA-256, entries, order, CRCs and
payload bytes. Two things made that true: the Go programs are built with
`-trimpath`, vendored modules and an empty build id, and the release build type
sets `vcsInfo.include = false` (AGP otherwise stamps the git revision, or
`NO_VALID_GIT_FOUND`, into `META-INF/version-control-info.textproto`). Debug
builds are **not** reproducible: the Android Gradle plugin orders the
`classesN.dex` entries differently from run to run, which is why the Golden QA
APK was not bit-identical between builds. That is a debug-build property; the
claim above is limited to the release APK, built with JDK 21, Go 1.26.8 and the
NDK pinned in `ndkVersion.gradle`. Signing is separate: an APK signed with a
given key matches a rebuild only after the signature is compared apart from the
signing block (as F-Droid's `Binaries` verification does).
