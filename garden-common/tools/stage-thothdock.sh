#!/bin/sh
#
# Stage ThothDock and the Docker CLI for a Garden build:
#   <jni dir>/arm64-v8a/libthothdock.so   ThothDock daemon, built from source
#   <jni dir>/arm64-v8a/libdocker.so      the upstream Docker CLI, built from source
#   <assets dir>/thothdock/components.properties   what was staged (commit, sizes, SHA-256)
#   <assets dir>/thothdock/engine-guard/*.deb + SHA256SUMS   the Engine Guard packages
#
# Both land in nativeLibraryDir with the executable bit, like libproot.so.
#
#   stage-thothdock.sh PINS_FILE THOTHDOCK_SRC DOCKER_CLI_SRC JNI_DIR ASSETS_DIR WORK_DIR
#
# Each program is built from `git archive <pinned commit>` of its checkout, so
# the working tree never leaks in, with the Go toolchain already on PATH
# (GOTOOLCHAIN=local unless the caller sets it) and flags that make the output
# reproducible: -trimpath, no VCS stamp, empty build id, vendored modules.
# Nothing is downloaded.
set -eu
PINS="$1"; SRC="$2"; CLI_SRC="$3"; JNI="$4/arm64-v8a"; ASSETS="$5/thothdock"; WORK="$6"

die() { echo "stage-thothdock: ERROR: $*" >&2; exit 1; }
pin() { v="$(sed -n "s/^$1=//p" "$PINS")"; [ -n "$v" ] || die "no $1 in $PINS"; echo "$v"; }
sha() { sha256sum "$1" | cut -d' ' -f1; }

COMMIT="$(pin thothdockCommit)"
GO_MIN="$(pin goMinVersion)"
CLI_VERSION="$(pin dockerCliVersion)"
CLI_COMMIT="$(pin dockerCliCommit)"

export GOTOOLCHAIN="${GOTOOLCHAIN:-local}"
command -v go >/dev/null || die "go not found (Go $GO_MIN or newer is needed on PATH)"
HAVE="$(go env GOVERSION | sed 's/^go//')"
[ "$(printf '%s\n%s\n' "$GO_MIN" "$HAVE" | sort -V | head -n1)" = "$GO_MIN" ] \
    || die "Go $HAVE is older than the required $GO_MIN"

mkdir -p "$JNI" "$ASSETS" "$WORK"

# ---- ThothDock ---------------------------------------------------------------
FULL="$(git -C "$SRC" rev-parse --verify "$COMMIT^{commit}")" || die "$COMMIT not in $SRC"
rm -rf "$WORK/thothdock-src"
mkdir -p "$WORK/thothdock-src"
git -C "$SRC" archive "$FULL" | tar -x -C "$WORK/thothdock-src"
SHORT="$(echo "$FULL" | cut -c1-7)"
( cd "$WORK/thothdock-src" && \
  CGO_ENABLED=0 GOOS=linux GOARCH=arm64 GOFLAGS=-mod=vendor \
  go build -trimpath -buildvcs=false \
    -ldflags "-s -w -buildid= -X github.com/Lord1Egypt/ThothDock/internal/version.GitCommit=$SHORT" \
    -o "$WORK/libthothdock.so" ./cmd/thothdock ) || die "ThothDock build failed"
cp "$WORK/libthothdock.so" "$JNI/libthothdock.so"
chmod 755 "$JNI/libthothdock.so"

# ---- Engine Guard packages (Debian-family guests), built from the same commit ----
command -v dpkg-deb >/dev/null || die "dpkg-deb not found (needed to build the Engine Guard packages)"
rm -rf "$ASSETS/engine-guard"
( cd "$WORK/thothdock-src" && SOURCE_DATE_EPOCH=1790000000 sh engine-guard/build.sh "$ASSETS/engine-guard" >/dev/null ) \
    || die "Engine Guard build failed"
GUARD_VERSION="$(dpkg-deb -f "$ASSETS"/engine-guard/docker.io_*_all.deb Version)"

# ---- Docker CLI, from the upstream source tag ---------------------------------
CLI_FULL="$(git -C "$CLI_SRC" rev-parse --verify "$CLI_COMMIT^{commit}")" || die "$CLI_COMMIT not in $CLI_SRC"
[ "$CLI_FULL" = "$CLI_COMMIT" ] || die "dockerCliCommit must be the full commit id"
rm -rf "$WORK/cli-src"
mkdir -p "$WORK/cli-src"
git -C "$CLI_SRC" archive "$CLI_FULL" | tar -x -C "$WORK/cli-src"
# docker/cli is CalVer and ships vendor.mod instead of go.mod; give the build
# directory the go.mod/go.sum it expects (scripts/with-go-mod.sh does the same).
( cd "$WORK/cli-src" && ln -s vendor.mod go.mod && ln -s vendor.sum go.sum && \
  CGO_ENABLED=0 GOOS=linux GOARCH=arm64 GOFLAGS=-mod=vendor \
  go build -trimpath -buildvcs=false -tags grpcnotrace \
    -ldflags "-s -w -buildid= -X github.com/docker/cli/cli/version.GitCommit=$(echo "$CLI_FULL" | cut -c1-7) -X github.com/docker/cli/cli/version.Version=$CLI_VERSION" \
    -o "$WORK/libdocker.so" ./cmd/docker ) || die "Docker CLI build failed"
cp "$WORK/libdocker.so" "$JNI/libdocker.so"
chmod 755 "$JNI/libdocker.so"

cat > "$ASSETS/components.properties" <<PROPS
thothdockCommit=$FULL
thothdockSha256=$(sha "$JNI/libthothdock.so")
thothdockSize=$(stat -c %s "$JNI/libthothdock.so")
dockerCliVersion=$CLI_VERSION
dockerCliCommit=$CLI_FULL
dockerCliSha256=$(sha "$JNI/libdocker.so")
dockerCliSize=$(stat -c %s "$JNI/libdocker.so")
goVersion=$HAVE
guardVersion=$GUARD_VERSION
PROPS
echo "stage-thothdock: ThothDock $SHORT sha256 $(sha "$JNI/libthothdock.so"), Docker CLI $CLI_VERSION sha256 $(sha "$JNI/libdocker.so"), Go $HAVE"
