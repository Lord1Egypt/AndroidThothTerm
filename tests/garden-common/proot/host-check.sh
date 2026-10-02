#!/bin/sh
# Host reproduction of the "Can't set permissions to 0777" pacman warning and
# of patch 0006 (fchmodat2). Builds the pinned PRoot for this host twice --
# with garden-common/patches 0001-0005 and with every patch -- and runs the
# probes natively and in a guest whose paths do not exist on the host.
#
# Needs: gcc, make, libarchive headers (libarchive-dev), bsdtar, a Linux
# kernel >= 6.6 and glibc >= 2.39 (so lchmod uses fchmodat2), as on the phone.
# Prints PASS/FAIL lines; exits non-zero on any FAIL.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
W=${WORK:-$(mktemp -d)}
fail=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2"; fail=1; fi; }

[ -f "$REPO/third_party/proot/src/GNUmakefile" ] || { echo "run: git submodule update --init third_party/proot"; exit 2; }

# talloc, as tools/build-proot.sh builds it, but for this host.
mkdir -p "$W/include" "$W/lib"
sed -n '/^cat > "\$BUILD_DIR\/include\/replace.h" <<.REPLACE_H./,/^REPLACE_H$/p' \
    "$REPO/garden-common/tools/build-proot.sh" | sed '1d;$d' > "$W/include/replace.h"
gcc -O2 -fPIC -c "$REPO/third_party/talloc/talloc.c" -I"$W/include" -I"$REPO/third_party/talloc" \
    -DHAVE_VA_COPY -DHAVE_STDBOOL_H -DHAVE_STDINT_H -DHAVE_CONSTRUCTOR_ATTRIBUTE \
    -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=4 -DTALLOC_BUILD_VERSION_RELEASE=3 \
    -o "$W/talloc.o"
ar rcs "$W/lib/libtalloc.a" "$W/talloc.o"

build() { # build NAME MAX_PATCH_NUMBER
    rm -rf "$W/$1"; mkdir -p "$W/$1"
    cp -R "$REPO/third_party/proot" "$W/$1/proot"; rm -rf "$W/$1/proot/.git"
    for p in "$REPO"/garden-common/patches/*.patch; do
        n=$(basename "$p" | cut -c1-4)
        [ "$n" -le "$2" ] || continue
        (cd "$W/$1/proot" && patch -p1 --batch --silent < "$p")
    done
    make -s -C "$W/$1/proot/src" CFLAGS="-I$REPO/third_party/talloc -O2" \
        LDFLAGS="-L$W/lib -ltalloc" proot >/dev/null 2>&1
}
build before 5
build after 9999

G="$W/guest"
mkdir -p "$G/work" "$G/src"
gcc -O2 -o "$G/probe" "$HERE/fchmodat2-probe.c"
gcc -O2 -o "$G/alpm-extract" "$HERE/alpm-extract.c" -larchive
(cd "$G/src" && echo lib > libdemo.so.1 && ln -sf libdemo.so.1 libdemo.so && bsdtar -cf "$G/demo.tar" libdemo.so.1 libdemo.so)
reset_tree() { rm -rf "$1"; mkdir -p "$1"; echo x > "$1/file"; chmod 644 "$1/file"; ln -s file "$1/link"; }
run() { # run PROOT CMD...
    p=$1; shift
    (cd / && "$p" -0 -r "$G" -b /usr -b /lib -b /lib64 -b /bin -b /etc -b /dev -b /proc "$@" 2>&1) \
        | { grep -v '^proot \(warning\|info\)' || true; }
}

reset_tree "$W/native"; "$G/probe" "$W/native" > "$W/native.txt"
reset_tree "$G/work"; run "$W/before/proot/src/proot" /probe > "$W/before.txt"
reset_tree "$G/work"; run "$W/after/proot/src/proot" /probe > "$W/after.txt"
echo "--- native";        cat "$W/native.txt"
echo "--- PRoot 0001-0005"; cat "$W/before.txt"
echo "--- PRoot with 0006"; cat "$W/after.txt"
! cmp -s "$W/native.txt" "$W/before.txt"; ok $? "the bug reproduces: PRoot without 0006 differs from the kernel"
cmp -s "$W/native.txt" "$W/after.txt"; ok $? "with 0006 every probe result equals the native kernel's"

run "$W/before/proot/src/proot" sh -c 'rm -rf /x; /alpm-extract /demo.tar /x' > "$W/alpm-before.txt"
run "$W/after/proot/src/proot" sh -c 'rm -rf /x; /alpm-extract /demo.tar /x' > "$W/alpm-after.txt"
echo "--- libalpm-style extraction without 0006"; cat "$W/alpm-before.txt"
echo "--- libalpm-style extraction with 0006";    cat "$W/alpm-after.txt"
grep -q "Can't set permissions to 0777" "$W/alpm-before.txt"; ok $? "without 0006: pacman's warning reproduces"
[ ! -s "$W/alpm-after.txt" ]; ok $? "with 0006: no warning"
exit $fail
