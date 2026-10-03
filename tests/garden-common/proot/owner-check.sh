#!/bin/sh
# Host check of patch 0008: the ownership a guest sees on link2symlink "hard
# links". Builds the pinned PRoot with 0001-0007 and with every patch, makes
# faked hard links, symlinks and ordinary files in a guest whose files are owned
# by the real (non-root) user, and reads their owner through every stat flavour
# (tests/garden-common/proot/owner-probe.c) and find(1) -- as root-id and as a
# fake non-root id. Every flavour must agree on every object. Needs gcc, make,
# patch, find; exits 2 when the host cannot run it, 1 on any FAIL.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
W=${WORK:-$(mktemp -d)}
fail=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2"; fail=1; fi; }
for tool in gcc make patch find; do
    command -v "$tool" >/dev/null 2>&1 || { echo "ENVIRONMENT BLOCKED (not a PRoot result): missing $tool"; exit 2; }
done
[ -f "$REPO/third_party/proot/src/GNUmakefile" ] || { echo "run: git submodule update --init third_party/proot"; exit 2; }
[ "$(id -u)" != 0 ] || { echo "ENVIRONMENT BLOCKED: run as an ordinary user (files must not be owned by root)"; exit 2; }

mkdir -p "$W/include" "$W/lib"
sed -n '/^cat > "\$BUILD_DIR\/include\/replace.h" <<.REPLACE_H./,/^REPLACE_H$/p' \
    "$REPO/garden-common/tools/build-proot.sh" | sed '1d;$d' > "$W/include/replace.h"
gcc -O2 -fPIC -c "$REPO/third_party/talloc/talloc.c" -I"$W/include" -I"$REPO/third_party/talloc" \
    -DHAVE_VA_COPY -DHAVE_STDBOOL_H -DHAVE_STDINT_H -DHAVE_CONSTRUCTOR_ATTRIBUTE \
    -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=4 -DTALLOC_BUILD_VERSION_RELEASE=3 \
    -o "$W/talloc.o" || exit 2
ar rcs "$W/lib/libtalloc.a" "$W/talloc.o"
build() { # build NAME MAX_PATCH
    rm -rf "$W/$1"; mkdir -p "$W/$1"
    cp -R "$REPO/third_party/proot" "$W/$1/proot"; rm -rf "$W/$1/proot/.git"
    for p in "$REPO"/garden-common/patches/*.patch; do
        n=$(basename "$p" | cut -c1-4); [ "$n" -le "$2" ] || continue
        (cd "$W/$1/proot" && patch -p1 --batch --silent < "$p") || return 1
    done
    make -s -C "$W/$1/proot/src" CFLAGS="-I$REPO/third_party/talloc -O2" \
        LDFLAGS="-L$W/lib -ltalloc -lpthread" proot >"$W/$1.build.log" 2>&1
}
build before 7 || { echo "ENVIRONMENT BLOCKED: build failed"; exit 2; }
build after 9999 || { echo "ENVIRONMENT BLOCKED: build failed"; exit 2; }
G="$W/guest"; rm -rf "$G"; mkdir -p "$G/work"
gcc -O2 -o "$G/probe" "$HERE/owner-probe.c" || exit 2
ME=$(id -u)

run() { # run PROOT IDOPT CMD...
    p=$1; idopt=$2; shift 2
    (cd / && "$p" -r "$G" $idopt --link2symlink -b /usr -b /lib -b /lib64 -b /bin -b /etc -b /dev -b /proc "$@" 2>&1) \
        | { grep -v '^proot \(warning\|info\)' || true; }
}
prepare="cd /work && mkdir \$T && cd \$T && echo data > file && ln file hl1 && ln file hl2 && ln -s file sym && echo other > plain"
N=0
# owners of every object through every flavour, as numbers; one distinct set expected
survey() { # survey PROOT IDOPT
    N=$((N + 1)); T=t$N
    run "$1" "$2" env T=$T sh -c "$prepare; for f in file hl1 hl2 sym plain; do /probe /work/$T/\$f; done; mv hl1 hl3; ln -f plain hl2; ln hl3 hl4; for f in hl3 hl2 hl4 .l2s.*; do /probe /work/$T/\$f; done; find /work/$T -printf '  find %p %U %G\n'; stat -c '  stat %n %u %g' /work/$T/*" 
}
uids() { # "UID GID" of every observation, counted
  awk '/uid=[0-9]+ gid=[0-9]+/ { match($0, /uid=[0-9]+/); u = substr($0, RSTART + 4, RLENGTH - 4); match($0, /gid=[0-9]+/); g = substr($0, RSTART + 4, RLENGTH - 4); print u, g }
       /^  (find|stat) / { print $(NF-1), $NF }' | sort | uniq -c; }

for idopt in "-0" "-i 1234:1234"; do
    exp=${idopt#* }; [ "$idopt" = "-0" ] && exp="0:0"; e="${exp%:*} ${exp#*:}"
    survey "$W/before/proot/src/proot" "$idopt" > "$W/survey-before.txt"
    survey "$W/after/proot/src/proot" "$idopt" > "$W/survey-after.txt"
    echo "--- id option '$idopt' (expect every object $e): owners seen, before / after 0008"
    uids < "$W/survey-before.txt" | sed 's/^/  before /'
    uids < "$W/survey-after.txt" | sed 's/^/  after  /'
    [ "$(uids < "$W/survey-after.txt" | wc -l)" = 1 ] && uids < "$W/survey-after.txt" | grep -q " $e\$"
    ok $? "with 0008 every flavour sees $e on every object ($idopt)"
done
echo "--- before 0008 (information)"
uids < "$W/survey-before.txt" | sed 's/^/  /'
exit $fail
