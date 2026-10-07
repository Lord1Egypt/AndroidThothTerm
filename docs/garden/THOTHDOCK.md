# ThothTerm • ThothDock

**ThothTerm • ThothDock** is the product name of package `com.thothterm.debian` (from 0.3.1; before that the
listing said "ThothTerm Trixie"). The package id and the `trixie-v*` tags are unchanged, so updates keep working.
The guest it runs is still Debian GNU/Linux 13 (trixie), and says so wherever it names the distribution.

Version 0.3.0 and later bundles ThothDock: a Docker Engine API 1.41
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

Two parts, with different timing (0.3.1):

* **Enforcement** (the hook, its apt configuration and the pin) is part of the
  guest being ready. `RootfsManager.setupEngineGuard` writes the three files out
  of the SHA-256-verified guard package, hook first, each by atomic rename, in
  every step that makes the guest usable: a fresh install, a repair and the
  start of every terminal window, before any guest process can run apt. It is
  three small file writes, needs neither the daemon, dpkg nor the network, and a
  guest that cannot be guarded is an error, not an unguarded terminal.
* **Registration** (installing the placeholders and the guard package with
  dpkg, so apt lists them as installed) runs on a background thread after the
  daemon is ready, because it needs the dpkg lock and a guest process. Protection
  does not wait for it: without placeholders the candidate for `docker.io` is the
  stock package, which the hook refuses. Registration is retried at the next
  daemon start.

The hook reads apt protocol-3 records (`name oldver oldarch oldmulti cmp newver
newarch newmulti action`), allows only the ThothDock placeholder version for a
protected name, and fails closed on an unreadable protected record or any other
protocol (ThothDock issue #3). The guard package is versioned separately from the
placeholders (`1.0+thothdock.2`), so dpkg replaces an older hook.
## Network and downloads

Nothing is downloaded in the background. Container images are fetched from a
registry over HTTPS only when the user runs `docker pull` or `docker run`.

## Pull ceilings

A pull is bounded by explicit ceilings (layers, compressed and decompressed bytes, entries, free
storage), with clear errors and cleanup; see ThothDock's `docs/SECURITY_MODEL.md`.

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
independent builds: a local build, GitHub Actions, and the F-Droid CI job in
the `buildserver-trixie` image (Go built from source) all give the same
SHA-256, entries, order, CRCs and payload bytes (evidence and the three fixes
that were needed: `docs/garden/thothdock-rc1/reproducibility-rc1.txt`). The Go
programs are built with `-trimpath`, vendored modules and an empty build id; the
release build type has `vcsInfo.include = false`; the Engine Guard packages are
uncompressed and built under a fixed umask.

Debug builds are **not** reproducible: the Android Gradle plugin orders the
`classesN.dex` entries differently from run to run, which is why the Golden QA
APK was not bit-identical between builds. The claim above is limited to the
release APK built with JDK 21, Go 1.26.8 and the NDK pinned in `ndkVersion.gradle`.
A signed APK matches a rebuild only after the signature is compared apart from
the signing block (as F-Droid's `Binaries` verification does).
