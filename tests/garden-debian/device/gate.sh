#!/bin/sh
# ThothTerm Trixie package-manager golden gate, on disposable rootfs copies.
# Runs as the adb shell user under /data/local/tmp/gd with the edition's own
# PRoot build; the app and its data are never touched.
#
#   gate.sh ROOTFS_TARBALL
#
# Needs: adb connected, and garden-debian built (build/garden holds its PRoot
# runtime). Prints PASS/FAIL per check and exits 1 on any FAIL.
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
G="$REPO/garden-debian/build/garden"
T=/data/local/tmp/gd
TARBALL="$1"

adb shell "rm -rf $T && mkdir -p $T/bin $T/lib"
adb push "$G/jniLibs/arm64-v8a/libproot.so" $T/bin/proot >/dev/null
adb push "$G/jniLibs/arm64-v8a/libproot_loader.so" $T/bin/loader >/dev/null
adb push "$G/assets/runtime/arm64-v8a/libtalloc.so.2" "$G/assets/runtime/arm64-v8a/libandroid-shmem.so" $T/lib/ >/dev/null
adb push "$TARBALL" $T/rootfs.tar.gz >/dev/null
# The archive's hard links, which the app (and this test) materialize as copies.
tar tvzf "$TARBALL" | awk '$1 ~ /^h/ {print $6, $9}' > "${TMPDIR:-/tmp}/gd-hardlinks.txt"
echo "hard links in the archive: $(wc -l < "${TMPDIR:-/tmp}/gd-hardlinks.txt")"
adb push "${TMPDIR:-/tmp}/gd-hardlinks.txt" $T/hardlinks.txt >/dev/null
for f in mkroot.sh run.sh; do adb push "$HERE/$f" $T/ >/dev/null; done
adb shell "chmod 755 $T/bin/*"

adb shell sh $T/mkroot.sh case | tail -2
for f in provision.sh matrix.sh restart.sh interrupt.sh; do adb push "$HERE/$f" $T/case/cmds/ >/dev/null; done
adb shell sh $T/run.sh case root provision.sh | tail -1
out="$(adb shell sh $T/run.sh case root matrix.sh; adb shell sh $T/run.sh case root restart.sh; adb shell sh $T/run.sh case root interrupt.sh)"
printf '%s\n' "$out"
case "$out" in *FAIL*) exit 1 ;; esac
