#!/bin/sh
# Isolated on-device upgrade matrix for ThothTerm Ubuntu's PRoot, run as the
# adb shell user under /data/local/tmp/l2s -- the app's data is never touched.
#
#   device-test.sh NEW_PROOT_DIR [OLD_PROOT_DIR]
#
# NEW_PROOT_DIR/OLD_PROOT_DIR hold libproot.so and libproot_loader.so. With an
# OLD directory the failure is first reproduced with it on a clean rootfs.
# Needs: adb connected, and in ~/.cache/thothterm the pinned
# ubuntu-base-26.04.1-base-arm64.tar.gz and the three sudo .debs
# (term-ubuntu/tools/prepare-assets.sh downloads them).
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
CACHE="${THOTHTERM_CACHE_DIR:-$HOME/.cache/thothterm}"
RUNTIME="$REPO/term-ubuntu/build/intermediates/assets/fullRelease/mergeFullReleaseAssets/runtime/arm64-v8a"
T=/data/local/tmp/l2s
TARBALL=ubuntu-base-26.04.1-base-arm64.tar.gz

adb shell "mkdir -p $T/new $T/old $T/lib $T/debs"
adb push "$1/libproot.so" $T/new/proot >/dev/null
adb push "$1/libproot_loader.so" $T/new/loader >/dev/null
if [ $# -ge 2 ]; then
    adb push "$2/libproot.so" $T/old/proot >/dev/null
    adb push "$2/libproot_loader.so" $T/old/loader >/dev/null
fi
adb push "$RUNTIME/libtalloc.so.2" "$RUNTIME/libandroid-shmem.so" $T/lib/ >/dev/null
adb push "$CACHE/$TARBALL" $T/ >/dev/null
for deb in "$CACHE"/libapparmor1_*.deb "$CACHE"/sudo-common_*.deb "$CACHE"/sudo_*.deb; do
    adb push "$deb" $T/debs/ >/dev/null
done
# The tarball's hard links, which the app (and this test) materialize as copies.
tar tvzf "$CACHE/$TARBALL" | awk '$1 ~ /^h/ {print $6, $9}' > "${TMPDIR:-/tmp}/hardlinks.txt"
adb push "${TMPDIR:-/tmp}/hardlinks.txt" $T/ >/dev/null
for f in mkroot.sh run.sh; do adb push "$HERE/$f" $T/ >/dev/null; done
adb shell "chmod 755 $T/new/* $T/old/* 2>/dev/null; true"

stage() { # stage NAME: a clean, provisioned baseline rootfs
    adb shell sh $T/mkroot.sh "$1" | tail -1
    for f in provision.sh upgrade.sh matrix.sh restart.sh repair.sh; do
        adb push "$HERE/$f" $T/"$1"/cmds/ >/dev/null
    done
}
if [ $# -ge 2 ]; then
    echo "== reproduce with the old PRoot"
    stage old-case
    adb shell sh $T/run.sh old old-case root provision.sh | tail -1
    adb shell sh $T/run.sh old old-case root upgrade.sh | grep -v "cannot find name"
fi
echo "== clean-rootfs matrix with the new PRoot"
stage new-case
adb shell sh $T/run.sh new new-case root provision.sh | tail -1
out="$(adb shell sh $T/run.sh new new-case root matrix.sh; adb shell sh $T/run.sh new new-case root restart.sh)"
printf '%s\n' "$out" | grep -v "cannot find name"
case "$out" in *FAIL*) exit 1 ;; esac
