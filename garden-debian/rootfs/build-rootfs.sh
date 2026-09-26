#!/bin/sh
#
# Build the ThothTerm Debian arm64 root filesystem from Debian's own archive.
#
# Debian publishes no minimal rootfs tarball of its own, so the userland is
# constructed here with debuerreotype -- Debian's reproducible-rootfs tool, the
# one behind the official Debian container images -- from a pinned
# snapshot.debian.org timestamp. Every package is fetched from snapshot.debian.org
# and verified against debian-archive-keyring (--check-gpg). Two runs of this
# script produce the same tarball, which is how the recorded SHA-256 in
# ROOTFS_PROVENANCE.md can be re-derived by anyone.
#
# Requirements: docker, and an aarch64 binfmt_misc handler with the F flag
# (qemu-user) on the host, because the second debootstrap stage runs arm64
# maintainer scripts.
#
# Output: $OUT_DIR (default garden-debian/rootfs/out, not tracked) holds
#   debian-13.7-trixie-arm64-rootfs.tar.gz, its .sha256 and the package list.
set -eu

# ---- pins -------------------------------------------------------------------
DEBIAN_VERSION="13.7"
SUITE="trixie"
ARCH="arm64"
# snapshot.debian.org timestamp: Debian 13.7 (released 2026-09-12) plus every
# trixie-security and trixie-updates upload up to this moment.
SNAPSHOT="20260926T000000Z"
EPOCH_TIMESTAMP="2026-09-26T00:00:00Z"
# Official Debian container image used only as the build environment, pinned
# by digest. Its tools are replaced by packages from the same snapshot below.
BUILDER="debian:trixie-slim@sha256:a99cfc517144bc59b1978475ec53b46ecabec7e43635402ee5b77cc54cd1b20a"
# ------------------------------------------------------------------------------

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${OUT_DIR:-$HERE/out}"
NAME="debian-${DEBIAN_VERSION}-${SUITE}-${ARCH}-rootfs"
PACKAGES="$(grep -v '^#' "$HERE/packages.txt" | grep -v '^[[:space:]]*$' | tr '\n' ' ')"

[ -r /proc/sys/fs/binfmt_misc/aarch64 ] || [ -r /proc/sys/fs/binfmt_misc/qemu-aarch64 ] \
    || { echo "build-rootfs: no aarch64 binfmt_misc handler on this host" >&2; exit 1; }

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

docker run --rm --privileged --platform linux/amd64 -e DEBIAN_FRONTEND=noninteractive \
    -v "$OUT_DIR:/out" \
    -e SNAPSHOT="$SNAPSHOT" -e EPOCH_TIMESTAMP="$EPOCH_TIMESTAMP" \
    -e SUITE="$SUITE" -e ARCH="$ARCH" -e NAME="$NAME" -e PACKAGES="$PACKAGES" \
    "$BUILDER" sh -eu -c '
rm -f /etc/apt/sources.list.d/debian.sources
echo "deb [check-valid-until=no] http://snapshot.debian.org/archive/debian/$SNAPSHOT $SUITE main" \
    > /etc/apt/sources.list
apt-get -qq update
apt-get -qq install -y --no-install-recommends debuerreotype debootstrap >/dev/null
echo "builder: $(dpkg-query -W -f="\${Package} \${Version}, " debuerreotype debootstrap apt)"

K=/usr/share/keyrings/debian-archive-keyring.gpg
rootfs=/tmp/rootfs
debuerreotype-init --check-gpg --keyring "$K" --arch "$ARCH" "$rootfs" "$SUITE" "$EPOCH_TIMESTAMP"
# Pull trixie-updates and trixie-security as of the same snapshot moment.
debuerreotype-debian-sources-list --snapshot "$rootfs" "$SUITE"
debuerreotype-apt-get "$rootfs" update -qq
debuerreotype-apt-get "$rootfs" dist-upgrade -yqq
debuerreotype-apt-get "$rootfs" install -yqq --no-install-recommends $PACKAGES
# Clamp every timestamp to the newest signed Release file just used; needs the
# apt lists, so it runs before they are removed.
debuerreotype-recalculate-epoch "$rootfs"
debuerreotype-chroot "$rootfs" dpkg-query -W -f="\${Package}\t\${Version}\n" > "/out/$NAME.packages.tsv"
# What the user sees: the live Debian mirrors, not the snapshot.
debuerreotype-debian-sources-list --deb822 "$rootfs" "$SUITE"
debuerreotype-apt-get "$rootfs" clean
rm -rf "$rootfs"/var/lib/apt/lists/*
debuerreotype-tar "$rootfs" "/tmp/$NAME.tar"
gzip -9 -n < "/tmp/$NAME.tar" > "/out/$NAME.tar.gz"
cd /out && sha256sum "$NAME.tar.gz" > "$NAME.tar.gz.sha256"
cat "$NAME.tar.gz.sha256"
'
