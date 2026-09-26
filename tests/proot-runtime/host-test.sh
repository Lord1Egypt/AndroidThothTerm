#!/bin/sh
# Regression tests for the PRoot runtime ThothTerm ships, built natively:
#  - /proc/self/exe must name the hard link a program was started through, as
#    Linux does, never link2symlink's hidden ".l2s.*" backing file. Ubuntu's
#    rust-coreutils refuses to run otherwise (docs/garden/HARDLINK_EXECUTABLES.md).
#  - --hangup-on-exit must hang up the session like a terminal (nohup and
#    setsid survive), and a SIGKILLed proot must take its tracees with it
#    (docs/garden/SESSION_LIFECYCLE.md).
#
# Builds PRoot natively for this Linux host from third_party/proot plus
# term-ubuntu/patches/*.patch -- the same sources the app ships -- and runs
# executables under --link2symlink. Needs a Linux host with cc, make and ar.
#
#   tests/proot-runtime/host-test.sh [WORK_DIR]
#
# Exit status 0 when every case holds, 1 on a failed case, 2 when it cannot run.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
WORK="${1:-$(mktemp -d)}"
mkdir -p "$WORK"
WORK="$(cd "$WORK" && pwd)"

for tool in cc make ar patch; do
    command -v "$tool" >/dev/null 2>&1 || { echo "SKIP: $tool not found"; exit 2; }
done
[ -f "$REPO/third_party/proot/src/GNUmakefile" ] || { echo "SKIP: third_party/proot not checked out"; exit 2; }

# ------------------------------------------------------------------ build
# Rebuild only when the sources or patches change.
STAMP="$(cat "$REPO"/term-ubuntu/patches/*.patch "$REPO/third_party/talloc/talloc.c" \
         | cksum | cut -d' ' -f1)-$(git -C "$REPO/third_party/proot" rev-parse HEAD 2>/dev/null)"
PROOT="$WORK/proot/src/proot"
if [ ! -x "$PROOT" ] || [ "$(cat "$WORK/stamp" 2>/dev/null)" != "$STAMP" ]; then
    rm -rf "$WORK/proot" "$WORK/include" "$WORK/lib"
    mkdir -p "$WORK/include" "$WORK/lib"
    cp -r "$REPO/third_party/proot" "$WORK/proot"
    rm -rf "$WORK/proot/.git"
    for p in "$REPO"/term-ubuntu/patches/*.patch; do
        (cd "$WORK/proot" && patch -p1 --batch --silent < "$p") || { echo "FAIL: $(basename "$p") does not apply"; exit 1; }
    done
    # The same minimal replace.h build-proot.sh gives talloc.
    sed -n '/^cat > "\$BUILD_DIR\/include\/replace.h" <<.REPLACE_H.$/,/^REPLACE_H$/p' \
        "$REPO/term-ubuntu/tools/build-proot.sh" | sed '1d;$d' > "$WORK/include/replace.h"
    cc -c "$REPO/third_party/talloc/talloc.c" -o "$WORK/talloc.o" -O2 -fPIC \
        -I"$WORK/include" -I"$REPO/third_party/talloc" \
        -DHAVE_VA_COPY -DHAVE_STDBOOL_H -DHAVE_STDINT_H -DHAVE_CONSTRUCTOR_ATTRIBUTE \
        -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=4 -DTALLOC_BUILD_VERSION_RELEASE=3 \
        || { echo "SKIP: talloc does not build here"; exit 2; }
    ar rcs "$WORK/lib/libtalloc.a" "$WORK/talloc.o"
    (cd "$WORK/proot/src" \
        && CPPFLAGS="-I$REPO/third_party/talloc -I$WORK/include" LDFLAGS="-L$WORK/lib" \
           make -s proot >"$WORK/build.log" 2>&1) \
        || { echo "SKIP: proot does not build on this host (see $WORK/build.log)"; exit 2; }
    echo "$STAMP" > "$WORK/stamp"
fi

# ------------------------------------------------------------------- cases
D="$WORK/case"
rm -rf "$D"
mkdir -p "$D/bin" "$D/lib/multi"
READLINK="$(command -v readlink)"

run() {
    PROOT_TMP_DIR="$WORK" "$PROOT" -r / --link2symlink "$@"
}

# The layout Ubuntu's rust-coreutils uses: a binary hard-linked under a
# directory of utility names, and PATH entries that are relative symlinks.
run sh -c "
    cp '$READLINK' '$D/bin/multi' &&
    ln '$D/bin/multi' '$D/lib/multi/util' &&
    ln -s ../lib/multi/util '$D/bin/util' &&
    ln -s '$D/lib/multi/util' '$D/abs-util' &&
    cp '$READLINK' '$D/plain'
" || { echo "FAIL: could not set up the case under proot"; exit 1; }

fail=0
expect() { # expect NAME EXECUTED EXPECTED_EXE
    got="$(run sh -c "cd '$D' && $2 /proc/self/exe" 2>&1)"
    if [ "$got" = "$3" ]; then
        echo "PASS $1"
    else
        echo "FAIL $1: /proc/self/exe = $got, expected $3"
        fail=1
    fi
}
expect "hard link, absolute path"        "'$D/lib/multi/util'" "$D/lib/multi/util"
expect "the linked original"             "'$D/bin/multi'"      "$D/bin/multi"
expect "relative symlink to a hard link" "'$D/bin/util'"       "$D/lib/multi/util"
expect "absolute symlink to a hard link" "'$D/abs-util'"       "$D/lib/multi/util"
expect "hard link, relative path"        "./lib/multi/util"    "$D/lib/multi/util"
expect "no hard link involved"           "'$D/plain'"          "$D/plain"

case "$(ls -a "$D/bin")" in
    *.l2s.*) echo "PASS link2symlink faked the hard link (the case is meaningful)" ;;
    *) echo "FAIL link2symlink did not fake the hard link; the test proves nothing"; fail=1 ;;
esac

# ------------------------------------------------------ session lifecycle
alive() { kill -0 "$1" 2>/dev/null; }
result() { # result NAME CONDITION...
    name="$1"; shift
    if "$@"; then echo "PASS $name"; else echo "FAIL $name"; fail=1; fi
}
S="$WORK/session"
rm -rf "$S"; mkdir -p "$S"
PROOT_TMP_DIR="$WORK" "$PROOT" -r / --hangup-on-exit sh -c "
    sleep 600 & echo \$! > '$S/job'
    nohup sleep 601 >/dev/null 2>&1 & echo \$! > '$S/nohup'
    setsid sleep 602 & echo \$! > '$S/setsid'
    sleep 0.3
" </dev/null >"$S/proot.out" 2>&1 &
PR=$!
sleep 2
JOB=$(cat "$S/job" 2>/dev/null); NOHUP=$(cat "$S/nohup" 2>/dev/null); SETSID=$(cat "$S/setsid" 2>/dev/null)
result "hangup-on-exit: a background job gets SIGHUP and ends" test -n "$JOB" -a ! -d "/proc/$JOB"
result "hangup-on-exit: a nohup'd job keeps running" alive "$NOHUP"
result "hangup-on-exit: a setsid'd job keeps running" alive "$SETSID"
result "hangup-on-exit: proot stays for them" alive "$PR"
result "hangup-on-exit: proot lets go of the terminal" \
    test "$(readlink /proc/$PR/fd/0)$(readlink /proc/$PR/fd/1)$(readlink /proc/$PR/fd/2)" = /dev/null/dev/null/dev/null
kill "$NOHUP" "$SETSID" 2>/dev/null
n=0; while alive "$PR" && [ $n -lt 50 ]; do sleep 0.1; n=$((n+1)); done
result "hangup-on-exit: proot exits once they end" test ! -d "/proc/$PR"

PROOT_TMP_DIR="$WORK" "$PROOT" -r / sh -c "sleep 603 & echo \$! > '$S/traced'; wait" </dev/null >/dev/null 2>&1 &
PR=$!
sleep 1.5
TRACED=$(cat "$S/traced" 2>/dev/null)
kill -9 "$PR"
sleep 0.5
result "a SIGKILLed proot takes its tracees with it (EXITKILL)" test -n "$TRACED" -a ! -d "/proc/$TRACED"
kill "$TRACED" 2>/dev/null
exit $fail
