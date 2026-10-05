#!/bin/sh
#
# Stage ThothDock and the Docker CLI for a Garden QA integration build:
#   <jni dir>/arm64-v8a/libthothdock.so   ThothDock daemon, built from source
#   <jni dir>/arm64-v8a/libdocker.so      the stock Docker CLI, unmodified
#   <assets dir>/thothdock/components.properties   what was staged (commit, sizes, SHA-256)
#   <assets dir>/thothdock/engine-guard/*.deb + SHA256SUMS   the Engine Guard packages
#
# Both land in nativeLibraryDir with the executable bit, like libproot.so.
#
#   stage-thothdock.sh PINS_FILE THOTHDOCK_SOURCE JNI_DIR ASSETS_DIR WORK_DIR
#
# ThothDock is built from `git archive <pinned commit>` of THOTHDOCK_SOURCE,
# so the checkout's working tree never leaks in, with a fixed toolchain and
# flags that make the output reproducible (-trimpath, no VCS stamp, empty
# build id). The CLI is downloaded once over HTTPS and refused unless both
# the tarball and the extracted binary match their pins.
set -eu
PINS="$1"; SRC="$2"; JNI="$3/arm64-v8a"; ASSETS="$4/thothdock"; WORK="$5"

die() { echo "stage-thothdock: ERROR: $*" >&2; exit 1; }
pin() { v="$(sed -n "s/^$1=//p" "$PINS")"; [ -n "$v" ] || die "no $1 in $PINS"; echo "$v"; }
sha() { sha256sum "$1" | cut -d' ' -f1; }

COMMIT="$(pin thothdockCommit)"
TOOLCHAIN="$(pin thothdockGoToolchain)"
URL="$(pin dockerCliUrl)"
TAR_SHA="$(pin dockerCliTarSha256)"
CLI_SHA="$(pin dockerCliSha256)"
case "$URL" in https://*) ;; *) die "refusing non-HTTPS $URL" ;; esac
command -v go >/dev/null || die "go not found"

mkdir -p "$JNI" "$ASSETS" "$WORK"

# ---- ThothDock ---------------------------------------------------------------
FULL="$(git -C "$SRC" rev-parse --verify "$COMMIT^{commit}")" || die "$COMMIT not in $SRC"
rm -rf "$WORK/thothdock-src"
mkdir -p "$WORK/thothdock-src"
git -C "$SRC" archive "$FULL" | tar -x -C "$WORK/thothdock-src"
SHORT="$(echo "$FULL" | cut -c1-7)"
( cd "$WORK/thothdock-src" && \
  GOTOOLCHAIN="$TOOLCHAIN" CGO_ENABLED=0 GOOS=linux GOARCH=arm64 GOFLAGS=-mod=readonly \
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

# ---- Docker CLI -----------------------------------------------------------------
TGZ="$WORK/$(basename "$URL")"
if [ ! -f "$TGZ" ] || [ "$(sha "$TGZ")" != "$TAR_SHA" ]; then
    rm -f "$TGZ" "$TGZ.part"
    curl -fsSL --proto '=https' --tlsv1.2 -o "$TGZ.part" "$URL" || die "download failed: $URL"
    [ "$(sha "$TGZ.part")" = "$TAR_SHA" ] || { rm -f "$TGZ.part"; die "tarball SHA-256 mismatch"; }
    mv "$TGZ.part" "$TGZ"
fi
rm -rf "$WORK/cli" && mkdir -p "$WORK/cli"
tar -xzf "$TGZ" -C "$WORK/cli" docker/docker
[ "$(sha "$WORK/cli/docker/docker")" = "$CLI_SHA" ] || die "docker CLI SHA-256 mismatch"
cp "$WORK/cli/docker/docker" "$JNI/libdocker.so"
chmod 755 "$JNI/libdocker.so"

cat > "$ASSETS/components.properties" <<PROPS
thothdockCommit=$FULL
thothdockSha256=$(sha "$JNI/libthothdock.so")
thothdockSize=$(stat -c %s "$JNI/libthothdock.so")
dockerCliVersion=$(pin dockerCliVersion)
dockerCliSha256=$CLI_SHA
dockerCliSize=$(stat -c %s "$JNI/libdocker.so")
guardVersion=$GUARD_VERSION
PROPS
echo "stage-thothdock: ThothDock $SHORT sha256 $(sha "$JNI/libthothdock.so"), Docker CLI $(pin dockerCliVersion)"
