#!/bin/sh
#
# Fetch, verify, and stage the pinned Ubuntu payload for the FULL flavour of the
# ThothTerm Ubuntu edition. Fails closed on any checksum mismatch and reuses a
# verified local cache so subsequent builds need no network access.
#
# The payload lands in src/full/, so only the full flavour packages it. The
# fdroid flavour ships no distribution binaries and downloads the same pinned
# archive on first run after explicit consent.
#
# The PRoot runtime is NOT handled here: tools/build-proot.sh builds it from the
# sources in third_party/, for every flavour.
#
# Cache: ${THOTHTERM_CACHE_DIR:-$HOME/.cache/thothterm}
#
set -eu

CACHE_DIR="${THOTHTERM_CACHE_DIR:-$HOME/.cache/thothterm}"

# Shared, binary-free metadata: both flavours need to know which image is
# pinned, so it stays in main.
META_DIR="src/main/assets/ubuntu"
# The archive itself is full-flavour only.
ASSETS_DIR="src/full/assets/ubuntu"

# Official Ubuntu base root filesystem (cdimage.ubuntu.com).
UBUNTU_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz"
UBUNTU_SHA="5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd"
UBUNTU_SIZE="35092106"
UBUNTU_UNCOMPRESSED_SIZE="122122240"
UBUNTU_FILE="ubuntu-base-26.04.1-base-arm64.tar.gz"
# The packaged asset must not end in .gz: Android's asset packager transparently
# expands *.gz assets, which would break the runtime asset lookup.
UBUNTU_ASSET="ubuntu-base-26.04.1-base-arm64.tgz"

# Genuine Ubuntu sudo admin stack, provisioned offline on first run. The
# dependency closure was resolved against the pinned Ubuntu Base image: only
# libapparmor1, sudo-common and sudo are missing (libc6, libpam0g,
# libpam-modules, libselinux1, libssl3t64, zlib1g and libaudit1 are present).
# Fields: filename|pool path|sha256|size.
SUDO_PORTS_BASE="http://ports.ubuntu.com/ubuntu-ports"
SUDO_ASSETS_DIR="src/full/assets/sudo"
SUDO_PACKAGES="\
libapparmor1_5.0.0~beta1-0ubuntu7_arm64.deb|pool/main/a/apparmor/libapparmor1_5.0.0~beta1-0ubuntu7_arm64.deb|97bc3adba874fdda34afba6a206d3fcd5b531bb8681ba27c4442ee1379834cfa|50608
sudo-common_1.2ubuntu_all.deb|pool/main/s/sudo-common/sudo-common_1.2ubuntu_all.deb|ba909e8e796115f442d0915ed3baa1b752e8809ec4d8e723d2bbd0d177750d2c|4034
sudo_1.9.17p2-1ubuntu3_arm64.deb|pool/main/s/sudo/sudo_1.9.17p2-1ubuntu3_arm64.deb|a1f04e24343ad3b73123ca2dc2b43684ef1562ea3ed43788dc8ec79da574a873|923956"

log() {
    echo "prepare-assets: $*"
}

die() {
    echo "prepare-assets: ERROR: $*" >&2
    exit 1
}

sha256_of() {
    sha256sum "$1" | awk '{print $1}'
}

# fetch_verified <url> <sha256> <cache-path>
fetch_verified() {
    _url="$1"
    _sha="$2"
    _dest="$3"

    if [ -f "$_dest" ]; then
        _actual="$(sha256_of "$_dest")"
        if [ "$_actual" = "$_sha" ]; then
            log "cache hit: $(basename "$_dest")"
            return 0
        fi
        log "cache mismatch, re-fetching: $(basename "$_dest")"
        rm -f "$_dest"
    fi

    log "downloading: $_url"
    _tmp="$_dest.part"
    rm -f "$_tmp"
    curl -fL --retry 3 --connect-timeout 30 -o "$_tmp" "$_url" \
        || die "download failed: $_url"

    _actual="$(sha256_of "$_tmp")"
    if [ "$_actual" != "$_sha" ]; then
        rm -f "$_tmp"
        die "checksum mismatch for $_url (expected $_sha, got $_actual)"
    fi
    mv "$_tmp" "$_dest"
}

mkdir -p "$CACHE_DIR"

UBUNTU_CACHE="$CACHE_DIR/$UBUNTU_FILE"
fetch_verified "$UBUNTU_URL" "$UBUNTU_SHA" "$UBUNTU_CACHE"

_actual_size="$(wc -c < "$UBUNTU_CACHE" | tr -d ' ')"
[ "$_actual_size" = "$UBUNTU_SIZE" ] \
    || die "unexpected rootfs size (expected $UBUNTU_SIZE, got $_actual_size)"

mkdir -p "$META_DIR" "$ASSETS_DIR"

# Embed the rootfs exactly as downloaded from Ubuntu (packaged == upstream).
cp "$UBUNTU_CACHE" "$ASSETS_DIR/$UBUNTU_ASSET"

# Stage the verified Ubuntu admin packages for offline first-run provisioning.
mkdir -p "$SUDO_ASSETS_DIR"
while IFS='|' read -r _name _path _sha _size; do
    [ -n "$_name" ] || continue
    _cache="$CACHE_DIR/$_name"
    fetch_verified "$SUDO_PORTS_BASE/$_path" "$_sha" "$_cache"
    _actual_size="$(wc -c < "$_cache" | tr -d ' ')"
    [ "$_actual_size" = "$_size" ] \
        || die "unexpected size for $_name (expected $_size, got $_actual_size)"
    cp "$_cache" "$SUDO_ASSETS_DIR/$_name"
    chmod 644 "$SUDO_ASSETS_DIR/$_name"
done <<EOF
$SUDO_PACKAGES
EOF

# Runtime metadata used to validate the embedded image at first-run extraction.
cat > "$META_DIR/image.properties" <<EOF
imageId=ubuntu-26.04.1-base-arm64
ubuntuVersion=26.04.1
architecture=aarch64
sourceUrl=$UBUNTU_URL
filename=$UBUNTU_FILE
assetName=$UBUNTU_ASSET
upstreamSha256=$UBUNTU_SHA
compressedSize=$UBUNTU_SIZE
uncompressedSize=$UBUNTU_UNCOMPRESSED_SIZE
schemaVersion=1
EOF

log "ready (full flavour payload)"
log "  rootfs : $ASSETS_DIR/$UBUNTU_ASSET"
log "  meta   : $META_DIR/image.properties"
log "  sudo   : $SUDO_ASSETS_DIR (offline admin packages)"
