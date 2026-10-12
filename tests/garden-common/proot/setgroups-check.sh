#!/bin/sh
# Host check of patch 0011: as fake root (--root-id), setpriv's sequence
# (PR_SET_KEEPCAPS, setresuid, capset, setgroups) succeeds, and without
# KEEPCAPS setgroups still fails with EPERM, as on Linux. Builds the pinned
# PRoot with every garden-common patch. Exit 2 when the host cannot run it.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
W=${WORK:-$(mktemp -d)}
fail=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2"; fail=1; fi; }
for tool in gcc make patch; do
    command -v "$tool" >/dev/null 2>&1 || { echo "ENVIRONMENT BLOCKED (not a PRoot result): missing $tool"; exit 2; }
done
[ "$(id -u)" != 0 ] || { echo "ENVIRONMENT BLOCKED (not a PRoot result): run as an unprivileged user"; exit 2; }
mkdir -p "$W/include" "$W/lib"
sed -n '/^cat > "\$BUILD_DIR\/include\/replace.h" <<.REPLACE_H./,/^REPLACE_H$/p' \
    "$REPO/garden-common/tools/build-proot.sh" | sed '1d;$d' > "$W/include/replace.h"
gcc -O2 -fPIC -c "$REPO/third_party/talloc/talloc.c" -I"$W/include" -I"$REPO/third_party/talloc" \
    -DHAVE_VA_COPY -DHAVE_STDBOOL_H -DHAVE_STDINT_H -DHAVE_CONSTRUCTOR_ATTRIBUTE \
    -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=4 -DTALLOC_BUILD_VERSION_RELEASE=3 \
    -o "$W/talloc.o"
ar rcs "$W/lib/libtalloc.a" "$W/talloc.o"
rm -rf "$W/proot"
cp -R "$REPO/third_party/proot" "$W/proot"; rm -rf "$W/proot/.git"
for p in "$REPO"/garden-common/patches/*.patch; do
    (cd "$W/proot" && patch -p1 --batch --silent < "$p") || { echo "FAIL patch $(basename "$p")"; exit 1; }
done
make -s -C "$W/proot/src" CFLAGS="-I$REPO/third_party/talloc -O2" \
    LDFLAGS="-L$W/lib -ltalloc" GIT=false proot >"$W/build.log" 2>&1 || { tail -20 "$W/build.log"; echo "FAIL build"; exit 1; }
gcc -O2 -static -o "$W/probe" "$HERE/setgroups-probe.c" || { echo "ENVIRONMENT BLOCKED (not a PRoot result): no static libc"; exit 2; }
export PROOT_TMP_DIR="$W"
"$W/proot/src/proot" -r / --root-id "$W/probe" keep; ok $? "KEEPCAPS + setresuid + capset: setgroups succeeds (setpriv --clear-groups)"
"$W/proot/src/proot" -r / --root-id "$W/probe" drop; ok $? "without KEEPCAPS: setgroups after the UID drop fails with EPERM"
exit $fail
