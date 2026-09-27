#!/bin/sh
#
# Build the ThothTerm Debian arm64 root filesystem from Debian's own archive.
#
# Debian publishes no minimal rootfs tarball of its own, so the userland is
# constructed here with debuerreotype -- Debian's reproducible-rootfs tool, the
# one behind the official Debian container images -- from a pinned
# snapshot.debian.org timestamp. debuerreotype is used rather than mmdebstrap
# because reproducibility is its purpose: it clamps every timestamp to the
# snapshot's Release date and writes the tarball in a canonical order, so two
# runs give identical bytes without any post-processing of ours.
#
# Every package is fetched from snapshot.debian.org and verified against
# debian-archive-keyring (--check-gpg). Both archives are read at the same
# moment: debian (trixie, trixie-updates) and debian-security (trixie-security).
#
# Requirements: docker, and an aarch64 binfmt_misc handler (qemu-user) on the
# host, because the second debootstrap stage runs arm64 maintainer scripts.
#
# Output in $OUT_DIR (default garden-debian/rootfs/out, not tracked):
#   thothterm-debian-trixie-arm64-rootfs-<sha12>.tar.gz   the rootfs
#   ...tar.gz.sha256                                      its checksum
#   ...packages.tsv                                       installed packages
#   ...manifest.txt                                       how it was built
#   ...scan.txt                                           identity/secret scan
# <sha12> is the first 12 hex digits of the archive's SHA-256, so a name can
# never refer to two different archives.
set -eu

# ---- pins -------------------------------------------------------------------
SUITE="trixie"
ARCH="arm64"
# snapshot.debian.org moment for both archives: Debian 13.7 (2026-09-12) plus
# every trixie-updates and trixie-security upload before it. The latest
# trixie-security Release before it is dated 2026-09-26 17:43:41 UTC.
SNAPSHOT="20260927T000000Z"
EPOCH_TIMESTAMP="2026-09-27T00:00:00Z"
# Official Debian container image, used only as the build environment and
# pinned by digest. The tools it runs are installed from the same snapshot.
BUILDER="debian:trixie-slim@sha256:a99cfc517144bc59b1978475ec53b46ecabec7e43635402ee5b77cc54cd1b20a"
# ------------------------------------------------------------------------------

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${OUT_DIR:-$HERE/out}"
PACKAGES="$(grep -v '^#' "$HERE/packages.txt" | grep -v '^[[:space:]]*$' | tr '\n' ' ')"
SCRIPT_SHA="$(sha256sum "$0" | cut -d' ' -f1)"
SCRIPT_REV="$(git -C "$HERE" log -1 --format=%H -- build-rootfs.sh packages.txt 2>/dev/null || true)"

[ -r /proc/sys/fs/binfmt_misc/aarch64 ] || [ -r /proc/sys/fs/binfmt_misc/qemu-aarch64 ] \
    || { echo "build-rootfs: no aarch64 binfmt_misc handler on this host" >&2; exit 1; }

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
WORK="$(mktemp -d "$OUT_DIR/.work.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

docker run --rm --privileged --platform linux/amd64 -e DEBIAN_FRONTEND=noninteractive \
    -v "$WORK:/out" \
    -e SNAPSHOT="$SNAPSHOT" -e EPOCH_TIMESTAMP="$EPOCH_TIMESTAMP" \
    -e SUITE="$SUITE" -e ARCH="$ARCH" -e PACKAGES="$PACKAGES" \
    "$BUILDER" sh -eu -c '
rm -f /etc/apt/sources.list.d/debian.sources
echo "deb [check-valid-until=no] http://snapshot.debian.org/archive/debian/$SNAPSHOT $SUITE main" \
    > /etc/apt/sources.list
apt-get -qq update
apt-get -qq install -y --no-install-recommends debuerreotype debootstrap >/dev/null
dpkg-query -W -f="\${Package} \${Version}\n" debuerreotype debootstrap apt > /out/builder-tools.txt

K=/usr/share/keyrings/debian-archive-keyring.gpg
rootfs=/tmp/rootfs
debuerreotype-init --check-gpg --keyring "$K" --arch "$ARCH" "$rootfs" "$SUITE" "$EPOCH_TIMESTAMP"
# trixie, trixie-updates and trixie-security, all as of the snapshot moment.
debuerreotype-debian-sources-list --snapshot --deb822 "$rootfs" "$SUITE"
cp "$rootfs/etc/apt/sources.list.d/debian.sources" /out/build-sources.txt
debuerreotype-apt-get "$rootfs" update -qq
debuerreotype-apt-get "$rootfs" dist-upgrade -yqq
debuerreotype-apt-get "$rootfs" install -yqq --no-install-recommends $PACKAGES
# Clamp every timestamp to the newest signed Release file just used; needs the
# apt lists, so it runs before they are removed.
debuerreotype-recalculate-epoch "$rootfs"
debuerreotype-chroot "$rootfs" dpkg-query -W -f="\${Package}\t\${Version}\n" > /out/packages.tsv
debuerreotype-chroot "$rootfs" dpkg --audit > /out/dpkg-audit.txt 2>&1 || true
# What the user gets: the live Debian archives, pinned to the codename.
debuerreotype-debian-sources-list --deb822 "$rootfs" "$SUITE"
cp "$rootfs/etc/apt/sources.list.d/debian.sources" /out/sources.txt
debuerreotype-apt-get "$rootfs" clean
rm -rf "$rootfs"/var/lib/apt/lists/*
# Build-host state that must not ship: debuerreotype names the host after
# itself and points the resolver at a public DNS service, and the logs record
# the build. The app bind-mounts the Android resolver over /etc/resolv.conf at
# runtime; "thothterm" is the host name the app itself would set.
echo thothterm > "$rootfs/etc/hostname"
echo "# ThothTerm bind-mounts the resolver Android is using over this file." > "$rootfs/etc/resolv.conf"
find "$rootfs/var/log" -type f -delete

# Anything that would identify a machine or a person, or leak build state.
{
    echo "machine-id: [$(cat "$rootfs/etc/machine-id" 2>/dev/null || echo absent)]"
    echo "dbus machine-id: $(test -e "$rootfs/var/lib/dbus/machine-id" && echo present || echo absent)"
    echo "hostname: [$(cat "$rootfs/etc/hostname" 2>/dev/null || echo absent)]"
    echo "resolv.conf: [$(cat "$rootfs/etc/resolv.conf" 2>/dev/null | tr "\n" " " || echo absent)]"
    echo "sources.list (one-line): $(test -e "$rootfs/etc/apt/sources.list" && echo present || echo absent)"
    echo "shadow password fields: $(cut -d: -f2 "$rootfs/etc/shadow" | sort | uniq -c | tr -s " " | tr "\n" ";")"
    echo "ssh host keys: $(find "$rootfs/etc" -name "ssh_host_*" | wc -l)"
    echo "private keys: $(grep -rlE "BEGIN [A-Z ]*PRIVATE KEY" "$rootfs/etc" "$rootfs/root" "$rootfs/home" 2>/dev/null | wc -l)"
    echo "shell histories: $(find "$rootfs" -xdev \( -name ".*_history" -o -name ".lesshst" -o -name ".wget-hsts" \) | wc -l)"
    echo "home entries: $(ls -A "$rootfs/home" | wc -l)"
    echo "root entries: $(ls -A "$rootfs/root" | tr "\n" " ")"
    echo "apt lists: $(ls -A "$rootfs/var/lib/apt/lists" | wc -l)"
    echo "apt archives (.deb): $(find "$rootfs/var/cache/apt" -name "*.deb" | wc -l)"
    echo "apt pkgcache: $(find "$rootfs/var/cache/apt" -name "*.bin" | wc -l)"
    echo "non-empty logs: $(find "$rootfs/var/log" -type f -size +0 | sed "s#^$rootfs##" | tr "\n" " ")"
    echo "tmp entries: $(ls -A "$rootfs/tmp" "$rootfs/var/tmp" | grep -v ":$" | grep -c . || true)"
    echo "setuid files: $(find "$rootfs" -xdev -perm -4000 -type f | sed "s#^$rootfs##" | tr "\n" " ")"
} > /out/scan.txt

debuerreotype-tar "$rootfs" /tmp/rootfs.tar
stat -c %s /tmp/rootfs.tar > /out/uncompressed-size.txt
gzip -9 -n < /tmp/rootfs.tar > /out/rootfs.tar.gz
'

SHA="$(sha256sum "$WORK/rootfs.tar.gz" | cut -d' ' -f1)"
NAME="thothterm-debian-${SUITE}-${ARCH}-rootfs-$(printf %.12s "$SHA")"
SIZE="$(stat -c %s "$WORK/rootfs.tar.gz")"

mv "$WORK/rootfs.tar.gz" "$OUT_DIR/$NAME.tar.gz"
(cd "$OUT_DIR" && sha256sum "$NAME.tar.gz" > "$NAME.tar.gz.sha256")
mv "$WORK/packages.tsv" "$OUT_DIR/$NAME.packages.tsv"
mv "$WORK/scan.txt" "$OUT_DIR/$NAME.scan.txt"
{
    echo "# ThothTerm Debian rootfs build manifest"
    echo "file: $NAME.tar.gz"
    echo "sha256: $SHA"
    echo "size: $SIZE"
    echo "uncompressed_size: $(cat "$WORK/uncompressed-size.txt")"
    echo "architecture: $ARCH"
    echo "suite: $SUITE (trixie, trixie-updates, trixie-security; component main)"
    echo "debootstrap_variant: minbase"
    echo "snapshot_requested: $SNAPSHOT"
    echo "archive_snapshot: $(grep -m1 -o 'snapshot.debian.org/archive/debian/[0-9TZ]*' "$WORK/sources.txt")"
    echo "security_snapshot: $(grep -m1 -o 'snapshot.debian.org/archive/debian-security/[0-9TZ]*' "$WORK/sources.txt")"
    echo "epoch_timestamp: $EPOCH_TIMESTAMP"
    echo "builder_image: $BUILDER"
    sed 's/^/builder_tool: /' "$WORK/builder-tools.txt"
    echo "keyring: debian-archive-keyring (debuerreotype-init --check-gpg)"
    echo "extra_packages: $PACKAGES"
    echo "installed_packages: $(wc -l < "$OUT_DIR/$NAME.packages.tsv")"
    echo "build_script_sha256: $SCRIPT_SHA"
    echo "build_script_revision: ${SCRIPT_REV:-uncommitted}"
    echo "dpkg_audit: $(if [ -s "$WORK/dpkg-audit.txt" ]; then tr '\n' ' ' < "$WORK/dpkg-audit.txt"; else echo clean; fi)"
    echo "commands: debuerreotype-init; debuerreotype-debian-sources-list --snapshot --deb822;"
    echo "  apt-get update; apt-get dist-upgrade; apt-get install --no-install-recommends <extra_packages>;"
    echo "  debuerreotype-recalculate-epoch; debuerreotype-debian-sources-list --deb822; apt-get clean;"
    echo "  rm var/lib/apt/lists/*; debuerreotype-tar; gzip -9 -n"
    echo "--- apt sources while building (/etc/apt/sources.list.d/debian.sources)"
    cat "$WORK/build-sources.txt"
    echo "--- apt sources shipped (/etc/apt/sources.list.d/debian.sources)"
    cat "$WORK/sources.txt"
} > "$OUT_DIR/$NAME.manifest.txt"

cat "$OUT_DIR/$NAME.tar.gz.sha256"
echo "size $SIZE"
