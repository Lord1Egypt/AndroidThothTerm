#!/bin/sh
#
# Build the ThothTerm Rolling aarch64 root filesystem: an Arch Linux ARM
# userland, fully upgraded as of the moment its package inputs were captured.
#
#   build-rootfs.sh capture   network: authenticate the upstream image, sync the
#                             repositories, fetch every package, pin it all
#   build-rootfs.sh build     offline: rebuild the rootfs from the pinned inputs
#   build-rootfs.sh check     network: compare the pinned inputs with the live
#                             repositories (the release-day freshness gate)
#
# Arch Linux ARM is rolling, so the release is defined as "the latest
# internally consistent Arch Linux ARM AArch64 package state at the capture
# timestamp". That moment is recorded in inputs/capture.txt and the manifest.
#
# How the rootfs is made, and why:
#   1. The official ArchLinuxARM-aarch64-latest.tar.gz is downloaded with its
#      detached signature and verified with gpgv against the Arch Linux ARM
#      Build System key (68B3537F39A313B3E574D06777193F152BDBE6A6), taken from
#      the pinned archlinuxarm.gpg beside this script, before anything else
#      touches it. It is the build environment and the root of trust, not the
#      product: it carries a kernel, firmware, sshd and board configuration a
#      PRoot guest does not need, and it predates the repositories.
#   2. That image runs as an arm64 container (qemu-user through binfmt_misc).
#      A throwaway pacman keyring is created in it with pacman-key --init and
#      --populate archlinuxarm, the repositories are synced, and the image
#      itself is brought current with a complete pacman -Syu.
#   3. The now-current pacman installs packages.txt into an empty root
#      (pacstrap's method), with signature checking at the image's own
#      SigLevel, from the same synced databases, so the target is current by
#      construction; a second -Su must then find nothing to do. Every file in
#      the result comes from a pacman transaction and its hooks.
#   4. Capture keeps the synced databases and every package the image and the
#      target needed. Build repeats steps 2-3 offline (--network none) from
#      exactly those files, so two builds give identical bytes.
#
# The published archive ships no pacman keyring: the builder's keyring holds a
# private master key that must never be shared, so each installation creates
# its own at first run (the app runs pacman-key --init and --populate
# archlinuxarm). The trusted key DATA ships as usual in the keyring packages
# under /usr/share/pacman/keyrings. No sync databases, package cache, lock or
# log ship either.
#
# Requirements: docker and an aarch64 binfmt_misc handler (qemu-user, with the
# F flag) on the host.
#
# Output in $OUT_DIR (default garden-arch/rootfs/out, not tracked):
#   thothterm-arch-aarch64-rootfs-<sha12>.tar.gz          the rootfs
#   ...tar.gz.sha256                                      its checksum
#   ...packages.tsv                                       installed packages
#   ...manifest.txt                                       how it was built
#   ...scan.txt                                           identity/secret scan
# <sha12> is the first 12 hex digits of the archive's SHA-256.
set -eu

# ---- pins -------------------------------------------------------------------
UPSTREAM_URL="http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
SIGNING_KEY="68B3537F39A313B3E574D06777193F152BDBE6A6"
# archlinuxarm.gpg from github.com/archlinuxarm/archlinuxarm-keyring at
# 91e6b11698f8df66042d56aaa56fbe9c9263847d (tag 20140119, its latest).
KEYRING_SHA256="50a08f82ce3cd524a552da4bfa37f3e04b2c9da468e2fce5783474b58a34c518"
# Packages whose versions the manifest names, and that must be installed.
KEY_PACKAGES="pacman archlinuxarm-keyring archlinux-keyring gnupg gpgme ca-certificates openssl curl pacman-mirrorlist glibc sudo"
# ------------------------------------------------------------------------------

HERE="$(cd "$(dirname "$0")" && pwd)"
INPUTS="${INPUTS:-$HERE/inputs}"
OUT_DIR="${OUT_DIR:-$HERE/out}"
PACKAGES="$(grep -v '^#' "$HERE/packages.txt" | grep -v '^[[:space:]]*$' | tr '\n' ' ')"

die() { echo "build-rootfs: $*" >&2; exit 1; }

[ -r /proc/sys/fs/binfmt_misc/aarch64 ] || [ -r /proc/sys/fs/binfmt_misc/qemu-aarch64 ] \
    || die "no aarch64 binfmt_misc handler on this host"
[ "$(sha256sum "$HERE/archlinuxarm.gpg" | cut -d' ' -f1)" = "$KEYRING_SHA256" ] \
    || die "archlinuxarm.gpg does not match its pin"

# Verifies the upstream image against the Build System key alone, fail closed.
verify_upstream() {
    gnupg="$(mktemp -d)"
    GNUPGHOME="$gnupg" gpg -q --batch --import "$HERE/archlinuxarm.gpg" 2>/dev/null
    GNUPGHOME="$gnupg" gpg -q --batch --export "$SIGNING_KEY" > "$gnupg/signer.gpg"
    [ -s "$gnupg/signer.gpg" ] || die "signing key $SIGNING_KEY is not in archlinuxarm.gpg"
    gpgv --keyring "$gnupg/signer.gpg" --status-fd 1 \
        "$INPUTS/upstream/ArchLinuxARM-aarch64-latest.tar.gz.sig" \
        "$INPUTS/upstream/ArchLinuxARM-aarch64-latest.tar.gz" > "$gnupg/status" 2>&1 \
        || { cat "$gnupg/status" >&2; rm -rf "$gnupg"; die "upstream signature does NOT verify"; }
    grep -q "^\[GNUPG:\] VALIDSIG $SIGNING_KEY " "$gnupg/status" \
        || { rm -rf "$gnupg"; die "upstream signature is not by $SIGNING_KEY"; }
    grep "^\[GNUPG:\] VALIDSIG" "$gnupg/status" | cut -d' ' -f3-5
    rm -rf "$gnupg"
}

# The verified upstream image as an arm64 container image, named by its hash.
builder_image() {
    sha="$(sha256sum "$INPUTS/upstream/ArchLinuxARM-aarch64-latest.tar.gz" | cut -d' ' -f1)"
    image="thothterm-alarm-upstream:$(printf %.12s "$sha")"
    if ! docker image inspect "$image" >/dev/null 2>&1; then
        docker import --platform linux/arm64 \
            "$INPUTS/upstream/ArchLinuxARM-aarch64-latest.tar.gz" "$image" >/dev/null
    fi
    echo "$image"
}

# Shared by capture and build, inside the container: a throwaway keyring for
# the builder, then the builder brought current.
BUILDER_PREP='
set -eu
export LANG=C.UTF-8
rm -rf /etc/pacman.d/gnupg
pacman-key --init >/dev/null 2>&1
pacman-key --populate archlinuxarm >/dev/null 2>&1
pacman-key --list-keys 68B3537F39A313B3E574D06777193F152BDBE6A6 >/dev/null
grep -E "^[[:space:]]*SigLevel" /etc/pacman.conf > /tmp/siglevels
if grep -qi never /tmp/siglevels; then echo "SigLevel Never in the image" >&2; exit 1; fi
# The builder is a container: its kernel, firmware and initramfs generator
# only slow the upgrade (mkinitcpio would run under emulation). Removed with
# pacman, so the builder stays a consistent system.
pacman -Rns --noconfirm linux-aarch64 $(pacman -Qq | grep "^linux-firmware") mkinitcpio mkinitcpio-busybox >/dev/null
'

# pacstrap's chroot setup for a target root, so install scriptlets and hooks
# see /proc, /dev and /sys.
TARGET_PREP='
T=/target
mkdir -m 0755 -p $T/var/cache/pacman/pkg $T/var/lib/pacman/sync $T/var/log $T/dev $T/run $T/etc/pacman.d
mkdir -m 1777 -p $T/tmp
mkdir -m 0555 -p $T/sys $T/proc
mount -t proc proc $T/proc
mount --rbind /sys $T/sys
mount --rbind /dev $T/dev
mount -t tmpfs -o mode=0755 run $T/run
mount -t tmpfs -o mode=1777 tmp $T/tmp
cp /var/lib/pacman/sync/*.db $T/var/lib/pacman/sync/
'

cmd_capture() {
    mkdir -p "$INPUTS/upstream" "$INPUTS/pkg" "$INPUTS/sync"
    up="$INPUTS/upstream/ArchLinuxARM-aarch64-latest.tar.gz"
    if [ ! -s "$up" ]; then
        for suffix in .sig .md5 ""; do
            curl -fsSL -D "$up$suffix.headers" -o "$up$suffix.part" "$UPSTREAM_URL$suffix"
            mv "$up$suffix.part" "$up$suffix"
        done
    fi
    echo "upstream signature: $(verify_upstream)"
    image="$(builder_image)"
    rm -f "$INPUTS/pkg/"* "$INPUTS/sync/"*
    docker run --rm --privileged --platform linux/arm64 \
        -v "$INPUTS:/inputs" -e PACKAGES="$PACKAGES" "$image" /bin/bash -c "$BUILDER_PREP"'
# pacman 7 confines its downloader with Landlock and seccomp. Under qemu-user
# neither exists (no Landlock in the host kernel; qemu rejects seccomp filters
# with EINVAL), so the builder downloads without them -- still as the
# unprivileged DownloadUser, and every package is still signature-checked.
# The shipped pacman.conf is not changed by this.
# The mirror is one geo-redirector and a transfer can stall, so downloads
# are retried; each retry keeps what already arrived. -Syuw fetches the whole
# upgrade, then -Su applies it from the cache against the same databases:
# one complete upgrade.
retry() { for n in 1 2 3 4 5; do "$@" && return 0; echo "retrying ($n): $*" >&2; sleep 10; done; return 1; }
date -u +%Y-%m-%dT%H:%M:%SZ > /inputs/sync-started.txt
pacman -Sy --noconfirm --disable-sandbox
retry pacman -Suw --noconfirm --disable-sandbox --cachedir /inputs/pkg
date -u +%Y-%m-%dT%H:%M:%SZ > /inputs/sync-finished.txt
pacman -Su --noconfirm --cachedir /inputs/pkg
cp /var/lib/pacman/sync/*.db /inputs/sync/
grep -m1 "^Server" /etc/pacman.d/mirrorlist > /inputs/mirror.txt
pacman -Q pacman archlinuxarm-keyring > /inputs/builder-tools.txt
# Every package the target needs, from the same databases: an empty local
# database makes -Sw fetch the whole dependency closure.
mkdir -p /tmp/closure/db /tmp/closure/root
ln -s /var/lib/pacman/sync /tmp/closure/db/sync
retry pacman --root /tmp/closure/root --dbpath /tmp/closure/db --cachedir /inputs/pkg \
    -Sw --noconfirm --disable-sandbox $PACKAGES
# The unprivileged downloader'"'"'s scratch directories, empty once it is done.
rmdir /inputs/pkg/download-* 2>/dev/null || true
'
    {
        echo "# ThothTerm Rolling rootfs inputs"
        echo "captured_by: build-rootfs.sh capture"
        echo "sync_started: $(cat "$INPUTS/sync-started.txt")"
        echo "sync_finished: $(cat "$INPUTS/sync-finished.txt")"
        echo "mirror: $(cat "$INPUTS/mirror.txt")"
        echo "upstream_url: $UPSTREAM_URL"
        echo "upstream_last_modified: $(grep -i '^last-modified:' "$up.headers" | tail -1 | cut -d' ' -f2- | tr -d '\r')"
        echo "upstream_size: $(stat -c %s "$up")"
        echo "upstream_sha256: $(sha256sum "$up" | cut -d' ' -f1)"
        echo "upstream_md5_published: $(cut -d' ' -f1 "$up.md5")"
        echo "upstream_signature: $(verify_upstream)"
        echo "packages_captured: $(ls "$INPUTS/pkg" | grep -c '\.pkg\.tar\.[a-z]*$')"
    } > "$INPUTS/capture.txt"
    rm -f "$INPUTS/sync-started.txt" "$INPUTS/sync-finished.txt" "$INPUTS/mirror.txt"
    (cd "$INPUTS" && find upstream sync pkg builder-tools.txt capture.txt -type f ! -name '*.headers' \
        | LC_ALL=C sort | xargs sha256sum > inputs.sha256)
    cat "$INPUTS/capture.txt"
}

cmd_build() {
    (cd "$INPUTS" && sha256sum --quiet -c inputs.sha256) || die "inputs do not match inputs.sha256"
    echo "upstream signature: $(verify_upstream)"
    finished="$(sed -n 's/^sync_finished: //p' "$INPUTS/capture.txt")"
    epoch="$(date -u -d "$finished" +%s)"
    image="$(builder_image)"
    mkdir -p "$OUT_DIR"
    OUT_DIR="$(cd "$OUT_DIR" && pwd)"
    WORK="$(mktemp -d "$OUT_DIR/.work.XXXXXX")"
    trap 'rm -rf "$WORK"' EXIT

    docker run --rm --privileged --platform linux/arm64 --network none \
        -v "$INPUTS:/inputs:ro" -v "$WORK:/out" \
        -e PACKAGES="$PACKAGES" -e KEY_PACKAGES="$KEY_PACKAGES" -e EPOCH="$epoch" \
        -e SOURCE_DATE_EPOCH="$epoch" "$image" /bin/bash -c "$BUILDER_PREP"'
cp -a /inputs/pkg /pkg
cp /inputs/sync/*.db /var/lib/pacman/sync/
# The builder, brought to the captured state: a complete upgrade, offline.
pacman -Su --noconfirm --cachedir /pkg > /out/builder-upgrade.log
pacman -Q pacman archlinuxarm-keyring > /out/builder-tools.txt
[ "$(cat /out/builder-tools.txt)" = "$(cat /inputs/builder-tools.txt)" ]
'"$TARGET_PREP"'
P="pacman --root $T --dbpath $T/var/lib/pacman --cachedir /pkg --gpgdir /etc/pacman.d/gnupg --noconfirm"
$P -S $PACKAGES > /out/install.log
# Nothing may be left to upgrade against the same databases.
$P -Su > /out/second-upgrade.log
grep -q "there is nothing to do" /out/second-upgrade.log
if $P -Qu > /out/pending.txt; then echo "updates still pending:" >&2; cat /out/pending.txt >&2; exit 1; fi
$P -Dk > /out/dbcheck.txt 2>&1
$P -Qk > /out/filecheck.txt 2>&1 || { grep -v " 0 missing files" /out/filecheck.txt >&2; exit 1; }
for p in $KEY_PACKAGES; do $P -Q "$p" > /dev/null || { echo "missing $p" >&2; exit 1; }; done
$P -Q $KEY_PACKAGES > /out/key-packages.txt
$P -Q | wc -l > /out/count.txt
$P -Qn --info | awk -F": " "/^Name/{n=\$2} /^Version/{v=\$2} /^Architecture/{a=\$2} /^Install Reason/{print n\"\t\"v\"\t\"a\"\t\"\$2}" > /out/packages.tsv
$P -Qm > /out/foreign.txt || true
test ! -s /out/foreign.txt
for f in $T/usr/lib/libalpm.so* $T/usr/bin/pacman $T/usr/bin/bash $T/usr/bin/sudo $T/usr/bin/curl $T/usr/bin/gpg $T/usr/bin/tar $T/usr/bin/zstd $T/usr/bin/xz $T/usr/bin/ls; do
    chroot $T /usr/bin/ldd "${f#$T}" | grep "not found" && { echo "missing library for $f" >&2; exit 1; }
done
chroot $T /usr/bin/pacman --version | sed -n "s/.*\(Pacman v[^ ]*\).*/\1/p" > /out/pacman-version.txt
# A genuine signed repository package, kept so that the app can prove at
# first run, offline, that the keyring it has just created verifies the
# distribution signing key: the installed archlinuxarm-keyring and its .sig.
V=$T/usr/share/thothterm/signature-check
mkdir -p $V
kv=$($P -Q archlinuxarm-keyring | cut -d" " -f2)
kf=$(ls /pkg/archlinuxarm-keyring-$kv-*.pkg.tar.* | grep -v "\.sig$")
cp "$kf" "$kf.sig" $V/
gpg --homedir /etc/pacman.d/gnupg --batch --status-fd 1 --verify "$kf.sig" "$kf" 2>/dev/null \
    | grep -q "^\[GNUPG:\] VALIDSIG 68B3537F39A313B3E574D06777193F152BDBE6A6 "
cat > $V/README <<README
$(basename "$kf") and its detached signature, exactly as the Arch Linux ARM
repository serves them. ThothTerm verifies this signature against the new
pacman keyring at first run, to prove offline that the keyring trusts the
Arch Linux ARM Build System key. It is not a package cache, and pacman does
not own these files.
README
umount -R $T/proc $T/sys $T/dev $T/run $T/tmp

# ---- one pacman.conf setting for a PRoot guest on Android ----------------
# pacman 7 confines its downloader three ways: Landlock, a seccomp filter
# and the unprivileged DownloadUser. Android kernels have no Landlock (on the
# SM-A165F: "restricting filesystem access failed because Landlock is not
# supported by the kernel!"), which makes every download fail. Only that
# layer is turned off; the seccomp filter and DownloadUser stay, and so does
# every signature check.
grep -qx "#DisableSandboxFilesystem" $T/etc/pacman.conf
sed -i "s/^#DisableSandboxFilesystem$/# ThothTerm: Android kernels provide no Landlock; the seccomp filter and\n# DownloadUser stay on. docs\/garden\/arch\/PACKAGE_MANAGER.md\nDisableSandboxFilesystem/" $T/etc/pacman.conf
grep -qx "DisableSandboxFilesystem" $T/etc/pacman.conf
grep -qx "#DisableSandboxSyscalls" $T/etc/pacman.conf
grep -q "^DownloadUser = alpm" $T/etc/pacman.conf

cp $T/etc/pacman.conf /out/pacman.conf
cp $T/etc/pacman.d/mirrorlist /out/mirrorlist

# ---- publication state ---------------------------------------------------
# No repository view, cache, lock or log from the build: the first pacman
# -Syu on the phone fetches current databases.
rm -f $T/var/lib/pacman/sync/* $T/var/cache/pacman/pkg/* $T/var/log/pacman.log
test ! -e $T/var/lib/pacman/db.lck
# Never the builder keyring (pacman -r uses --gpgdir, so none should exist).
test ! -e $T/etc/pacman.d/gnupg
test -s $T/usr/share/pacman/keyrings/archlinuxarm.gpg
# Written by systemd at install; a machine identity must not be shared.
: > $T/etc/machine-id
rm -f $T/var/lib/dbus/machine-id
echo thothterm > $T/etc/hostname
echo "# ThothTerm bind-mounts the resolver Android is using over this file." > $T/etc/resolv.conf
echo "LANG=C.UTF-8" > $T/etc/locale.conf
# Every account locked; no default root (or any) password.
awk -F: -v OFS=: "{ if (\$2 == \"\" || \$2 !~ /^[!*]/) \$2 = \"*\"; print }" $T/etc/shadow > /tmp/shadow
cat /tmp/shadow > $T/etc/shadow
find $T/var/log -type f -delete
rm -rf $T/var/cache/ldconfig/aux-cache $T/root/.gnupg $T/root/.cache
# pacman records the wall-clock install time; the capture moment instead.
sed -i "/^%INSTALLDATE%$/{n;s/.*/$EPOCH/}" $T/var/lib/pacman/local/*/desc
# Files a hook or this script wrote carry the build time; clamp them.
find $T -xdev -newermt "@$EPOCH" -exec touch -h -d "@$EPOCH" {} +

{
    echo "machine-id: [$(cat $T/etc/machine-id)]"
    echo "dbus machine-id: $(test -e $T/var/lib/dbus/machine-id && echo present || echo absent)"
    echo "hostname: [$(cat $T/etc/hostname)]"
    echo "resolv.conf: [$(tr "\n" " " < $T/etc/resolv.conf)]"
    echo "shadow password fields: $(cut -d: -f2 $T/etc/shadow | sort | uniq -c | tr -s " " | tr "\n" ";")"
    echo "accounts with a login shell: $(awk -F: "\$7 !~ /(nologin|false)\$/ {print \$1}" $T/etc/passwd | tr "\n" " ")"
    echo "pacman gnupg home: $(test -e $T/etc/pacman.d/gnupg && echo PRESENT || echo absent)"
    echo "private keys: $(grep -rlE "BEGIN [A-Z ]*PRIVATE KEY" $T/etc $T/root $T/home $T/var 2>/dev/null | wc -l)"
    echo "gnupg secret keyrings: $(find $T -xdev \( -name "private-keys-v1.d" -o -name "secring.gpg" \) | wc -l)"
    echo "ssh host keys: $(find $T/etc -name "ssh_host_*" | wc -l)"
    echo "shell histories: $(find $T -xdev \( -name ".*_history" -o -name ".lesshst" -o -name ".wget-hsts" \) | wc -l)"
    echo "home entries: $(ls -A $T/home | wc -l)"
    echo "root entries: $(ls -A $T/root | tr "\n" " ")(files under /root/.ssh: $(find $T/root/.ssh -type f 2>/dev/null | wc -l))"
    echo "sync databases: $(ls -A $T/var/lib/pacman/sync | wc -l)"
    echo "package cache: $(ls -A $T/var/cache/pacman/pkg | wc -l)"
    echo "db.lck: $(test -e $T/var/lib/pacman/db.lck && echo PRESENT || echo absent)"
    echo "non-empty logs: $(find $T/var/log -type f -size +0 | tr "\n" " ")"
    echo "tmp entries: $(ls -A $T/tmp $T/var/tmp | grep -v ":$" | grep -c . || true)"
    echo "kernel/boot files: $(ls -A $T/boot | wc -l) in /boot; modules: $(ls -A $T/usr/lib/modules 2>/dev/null | wc -l)"
    echo "setuid files: $(find $T -xdev -perm -4000 -type f | sed "s#^$T##" | LC_ALL=C sort | tr "\n" " ")"
    echo "build paths: $(grep -rlsE -e "(^|[^a-z_])/(inputs|out|pkg|target)/" -e "\.work\." $T/etc $T/var/lib/pacman/local $T/usr/share/thothterm 2>/dev/null | grep -v "/files\$" | wc -l)"
} > /out/scan.txt

cd $T
LC_ALL=C tar --sort=name --format=gnu --numeric-owner --one-file-system \
    -cf /out/rootfs.tar .
stat -c %s /out/rootfs.tar > /out/uncompressed-size.txt
gzip -9 -n < /out/rootfs.tar > /out/rootfs.tar.gz
rm /out/rootfs.tar
'

    SHA="$(sha256sum "$WORK/rootfs.tar.gz" | cut -d' ' -f1)"
    NAME="thothterm-arch-aarch64-rootfs-$(printf %.12s "$SHA")"
    SIZE="$(stat -c %s "$WORK/rootfs.tar.gz")"
    mv "$WORK/rootfs.tar.gz" "$OUT_DIR/$NAME.tar.gz"
    (cd "$OUT_DIR" && sha256sum "$NAME.tar.gz" > "$NAME.tar.gz.sha256")
    mv "$WORK/packages.tsv" "$OUT_DIR/$NAME.packages.tsv"
    mv "$WORK/scan.txt" "$OUT_DIR/$NAME.scan.txt"
    {
        echo "# ThothTerm Rolling rootfs build manifest"
        echo "file: $NAME.tar.gz"
        echo "sha256: $SHA"
        echo "size: $SIZE"
        echo "uncompressed_size: $(cat "$WORK/uncompressed-size.txt")"
        echo "architecture: aarch64"
        echo "state: latest internally consistent Arch Linux ARM aarch64 package state at sync_finished"
        sed -n 's/^\(sync_started\|sync_finished\|mirror\|upstream_[a-z_0-9]*\|packages_captured\): /\1: /p' "$INPUTS/capture.txt"
        echo "inputs_sha256: $(sha256sum "$INPUTS/inputs.sha256" | cut -d' ' -f1)"
        echo "epoch: $epoch"
        sed 's/^/builder_tool: /' "$WORK/builder-tools.txt"
        echo "pacman_version: $(cat "$WORK/pacman-version.txt")"
        sed 's/^/key_package: /' "$WORK/key-packages.txt"
        echo "explicit_packages: $PACKAGES"
        echo "installed_packages: $(cat "$WORK/count.txt")"
        echo "second_upgrade: $(tail -1 "$WORK/second-upgrade.log")"
        echo "pacman_Dk: $(tr '\n' ' ' < "$WORK/dbcheck.txt")"
        echo "pacman_Qk: $(grep -vc ' 0 missing files' "$WORK/filecheck.txt" || true) packages with missing files"
        echo "build_script_sha256: $(sha256sum "$0" | cut -d' ' -f1)"
        echo "build_script_revision: $(git -C "$HERE" log -1 --format=%H -- build-rootfs.sh packages.txt archlinuxarm.gpg 2>/dev/null || echo uncommitted)"
        echo "--- /etc/pacman.conf (active lines)"
        grep -vE '^[[:space:]]*(#|$)' "$WORK/pacman.conf"
        echo "--- /etc/pacman.d/mirrorlist (active lines)"
        grep -vE '^[[:space:]]*(#|$)' "$WORK/mirrorlist"
    } > "$OUT_DIR/$NAME.manifest.txt"
    cat "$OUT_DIR/$NAME.tar.gz.sha256"
    echo "size $SIZE"
}

# Release-day gate: which pinned packages the live repositories have moved.
cmd_check() {
    image="$(builder_image)"
    docker run --rm --privileged --platform linux/arm64 -v "$INPUTS:/inputs:ro" \
        -e KEY_PACKAGES="$KEY_PACKAGES" "$image" /bin/bash -c "$BUILDER_PREP"'
mkdir -p /tmp/pinned /tmp/live
for db in /inputs/sync/*.db; do bsdtar -xf "$db" -C /tmp/pinned; done
# A throwaway container only reads the live databases; nothing is installed.
pacman -Sy >/dev/null
for db in /var/lib/pacman/sync/*.db; do bsdtar -xf "$db" -C /tmp/live; done
echo "checked: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
changed=0
for p in $KEY_PACKAGES; do
    a=$(ls -d /tmp/pinned/$p-[0-9]* 2>/dev/null | sed "s#.*/##"); b=$(ls -d /tmp/live/$p-[0-9]* 2>/dev/null | sed "s#.*/##")
    if [ "$a" = "$b" ]; then echo "same    $a"; else echo "CHANGED $a -> $b"; changed=1; fi
done
total=$(ls /tmp/pinned | wc -l); moved=$(comm -3 <(ls /tmp/pinned) <(ls /tmp/live) | wc -l)
echo "repository entries: $total pinned, $moved differ from live"
exit $changed
'
}

case "${1:-}" in
    capture) cmd_capture ;;
    build) cmd_build ;;
    check) cmd_check ;;
    *) die "usage: build-rootfs.sh capture|build|check" ;;
esac
