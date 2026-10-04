#!/bin/sh
# Host check of the QA runtime isolation, on real compiled code: PRoot (with
# every garden-common patch) and libandroid-shmem's shmem.c are compiled for
# this host twice -- once for a production id, once for its QA id -- with the
# same compiled-in paths build-proot.sh uses. garden-common/tools/
# check-runtime-ids.sh must accept each for its own id and refuse it for the
# other. Needs gcc, make, patch and the proot and libandroid-shmem submodules.
# (The device build is arm64 with the NDK; the paths are the same strings.)
#
#   tests/garden-common/qa/runtime-ids-host-check.sh
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
CHECK="$REPO/garden-common/tools/check-runtime-ids.sh"
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
fail=0
expect() { # expect accept|refuse DESCRIPTION CHECK-ARGS...
    want=$1; what=$2; shift 2
    if sh "$CHECK" "$@" 2>"$W/check.err"; then got=accept; else got=refuse; fi
    if [ "$got" = "$want" ]; then
        echo "PASS $what${want:+ ($want)}"
    else
        echo "FAIL $what: expected $want, got $got: $(cat "$W/check.err")"
        fail=1
    fi
}

[ -f "$REPO/third_party/proot/src/GNUmakefile" ] || { echo "run: git submodule update --init third_party/proot"; exit 2; }
[ -f "$REPO/third_party/libandroid-shmem/shmem.c" ] || { echo "run: git submodule update --init third_party/libandroid-shmem"; exit 2; }

mkdir -p "$W/include/android" "$W/include/linux" "$W/lib"
sed -n '/^cat > "\$BUILD_DIR\/include\/replace.h" <<.REPLACE_H./,/^REPLACE_H$/p' \
    "$REPO/garden-common/tools/build-proot.sh" | sed '1d;$d' > "$W/include/replace.h"
gcc -O2 -fPIC -c "$REPO/third_party/talloc/talloc.c" -I"$W/include" -I"$REPO/third_party/talloc" \
    -DHAVE_VA_COPY -DHAVE_STDBOOL_H -DHAVE_STDINT_H -DHAVE_CONSTRUCTOR_ATTRIBUTE \
    -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=4 -DTALLOC_BUILD_VERSION_RELEASE=3 \
    -o "$W/talloc.o"
ar rcs "$W/lib/libtalloc.a" "$W/talloc.o"
# Just enough of the Android headers for shmem.c to compile to an object.
# bionic's <paths.h> defines no _PATH_TMP (AOSP bionic libc/include/paths.h),
# so on Android the -D_PATH_TMP of build-proot.sh is what is compiled in;
# glibc's would silently replace it with "/tmp/", so use a bionic-like one.
printf '/* bionic: no _PATH_TMP */\n' > "$W/include/paths.h"
printf '#define ANDROID_LOG_INFO 4\nint __android_log_print(int, const char *, const char *, ...);\n' \
    > "$W/include/android/log.h"
printf '#include <fcntl.h>\n#include <linux/ioctl.h>\n#define ASHMEM_SET_SIZE _IOW(0x77, 3, unsigned long)\n#define ASHMEM_GET_SIZE _IO(0x77, 4)\n#define ASHMEM_NAME_LEN 256\n#define ASHMEM_SET_NAME _IOW(0x77, 1, char[ASHMEM_NAME_LEN])\n' \
    > "$W/include/linux/ashmem.h"

build() { # build APPLICATION_ID -> $W/<id>/{proot,shmem.o}
    d="$W/$1"
    runtime="/data/data/$1/files/linux/runtime"
    mkdir -p "$d"
    cp -R "$REPO/third_party/proot" "$d/proot"; rm -rf "$d/proot/.git"
    for p in "$REPO"/garden-common/patches/*.patch; do
        (cd "$d/proot" && patch -p1 --batch --silent < "$p")
    done
    # From the environment, as build-proot.sh does: the makefile appends to
    # these (a command-line CFLAGS would drop its -DPROOT_UNBUNDLE_LOADER).
    CPPFLAGS="-I$REPO/third_party/talloc" LDFLAGS="-L$W/lib" \
        PROOT_UNBUNDLE_LOADER="$runtime/loader" \
        make -s -C "$d/proot/src" proot > "$d/make.log" 2>&1 \
        || { tail -20 "$d/make.log"; exit 1; }
    cp "$d/proot/src/proot" "$d/proot.bin"
    gcc -O2 -fPIC -std=gnu11 -I"$W/include" -I"$REPO/third_party/libandroid-shmem" \
        -D_PATH_TMP="\"$runtime/tmp/\"" -c "$REPO/third_party/libandroid-shmem/shmem.c" -o "$d/shmem.o" 2>"$d/shmem.log" \
        || { cat "$d/shmem.log"; exit 1; }
}

for base in com.thothterm.arch com.thothterm.debian com.thothterm.ubuntu com.thothterm.security; do
    qa=$base.qa.lifecycle
    build "$base"
    build "$qa"
    for id in "$base" "$qa"; do
        other=$base; [ "$id" = "$base" ] && other=$qa
        expect accept "$id: proot carries its own loader path only" \
            "$id" "$W/$id/proot.bin" "/data/data/$id/files/linux/runtime/loader"
        expect accept "$id: libandroid-shmem carries its own tmp path only" \
            "$id" "$W/$id/shmem.o" "/data/data/$id/files/linux/runtime/tmp/"
        expect refuse "$id proot is refused as $other" \
            "$other" "$W/$id/proot.bin" "/data/data/$other/files/linux/runtime/loader"
        expect refuse "$id libandroid-shmem is refused as $other" \
            "$other" "$W/$id/shmem.o" "/data/data/$other/files/linux/runtime/tmp/"
    done
done
[ "$fail" = 0 ] && echo "RUNTIME IDS HOST CHECK PASS" || { echo "RUNTIME IDS HOST CHECK FAIL"; exit 1; }
