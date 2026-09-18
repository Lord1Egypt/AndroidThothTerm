#!/bin/sh
#
# Fetch, verify, and stage the pinned Ubuntu rootfs and PRoot runtime for the
# ThothTerm Ubuntu edition. Fails closed on any checksum mismatch and reuses a
# verified local cache so subsequent builds need no network access.
#
# Cache: ${THOTHTERM_CACHE_DIR:-$HOME/.cache/thothterm}
#
set -eu

CACHE_DIR="${THOTHTERM_CACHE_DIR:-$HOME/.cache/thothterm}"

ASSETS_DIR="src/main/assets/ubuntu"
RUNTIME_ASSETS_DIR="src/main/assets/runtime/arm64-v8a"
JNI_DIR="src/main/jniLibs/arm64-v8a"

# Official Ubuntu base root filesystem (cdimage.ubuntu.com).
UBUNTU_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz"
UBUNTU_SHA="5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd"
UBUNTU_SIZE="35092106"
UBUNTU_UNCOMPRESSED_SIZE="122122240"
UBUNTU_FILE="ubuntu-base-26.04.1-base-arm64.tar.gz"
# The packaged asset must not end in .gz: Android's asset packager transparently
# expands *.gz assets, which would break the runtime asset lookup.
UBUNTU_ASSET="ubuntu-base-26.04.1-base-arm64.tgz"

# ProotX support bundle (PRoot + loader + host libraries), owner-maintained and
# already audited for modern Android (NDK 29, 16 KB aligned).
PROOT_URL="https://github.com/Lord1Egypt/ProotX-Assets-Support/releases/download/v1.2.0/arm64-v8a-assets.zip"
PROOT_SHA="42fd0042b18d8145ebb72aece000404bed8a0911c83505c1acf31e2da5033fe7"
PROOT_FILE="prootx-arm64-v8a-v1.2.0-assets.zip"

# Genuine Ubuntu sudo admin stack, provisioned offline on first run. The
# dependency closure was resolved against the pinned Ubuntu Base image: only
# libapparmor1, sudo-common and sudo are missing (libc6, libpam0g,
# libpam-modules, libselinux1, libssl3t64, zlib1g and libaudit1 are present).
# Fields: filename|pool path|sha256|size.
SUDO_PORTS_BASE="http://ports.ubuntu.com/ubuntu-ports"
SUDO_ASSETS_DIR="src/main/assets/sudo"
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

PROOT_CACHE="$CACHE_DIR/$PROOT_FILE"
fetch_verified "$PROOT_URL" "$PROOT_SHA" "$PROOT_CACHE"

mkdir -p "$ASSETS_DIR" "$RUNTIME_ASSETS_DIR" "$JNI_DIR"

# Embed the rootfs exactly as downloaded from Ubuntu (packaged == upstream).
cp "$UBUNTU_CACHE" "$ASSETS_DIR/$UBUNTU_ASSET"

# Stage the PRoot runtime. Executables are shipped as fake native libraries so
# Android extracts them to nativeLibraryDir with the executable bit.
_tmpdir="$(mktemp -d)"
trap 'rm -rf "$_tmpdir"' EXIT INT TERM
unzip -oq "$PROOT_CACHE" \
    'modern/proot' 'modern/loader' \
    'modern/libtalloc.so.2' 'modern/libandroid-shmem.so' 'modern/libandroid-selinux.so' \
    -d "$_tmpdir" || die "failed to unpack PRoot assets"

cp "$_tmpdir/modern/proot" "$JNI_DIR/libproot.so"
cp "$_tmpdir/modern/loader" "$JNI_DIR/libproot_loader.so"
chmod 755 "$JNI_DIR/libproot.so" "$JNI_DIR/libproot_loader.so"

cp "$_tmpdir/modern/libtalloc.so.2" "$RUNTIME_ASSETS_DIR/libtalloc.so.2"
cp "$_tmpdir/modern/libandroid-shmem.so" "$RUNTIME_ASSETS_DIR/libandroid-shmem.so"
cp "$_tmpdir/modern/libandroid-selinux.so" "$RUNTIME_ASSETS_DIR/libandroid-selinux.so"
chmod 644 "$RUNTIME_ASSETS_DIR"/*

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
cat > "$ASSETS_DIR/image.properties" <<EOF
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
prootSource=$PROOT_URL
prootVersion=ProotX-v1.2.0-modern
prootSha256=$PROOT_SHA
EOF

log "ready"
log "  rootfs : $ASSETS_DIR/$UBUNTU_ASSET"
log "  proot  : $JNI_DIR/libproot.so"
log "  loader : $JNI_DIR/libproot_loader.so"
log "  sudo   : $SUDO_ASSETS_DIR (offline admin packages)"
