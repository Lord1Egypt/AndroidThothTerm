#!/bin/sh
# Regression tests for the PRoot runtime ThothTerm ships, built natively:
#  - /proc/self/exe must name the hard link a program was started through, as
#    Linux does, never link2symlink's hidden ".l2s.*" backing file. Ubuntu's
#    rust-coreutils refuses to run otherwise (docs/garden/HARDLINK_EXECUTABLES.md).
#  - --hangup-on-exit must hang up the session like a terminal (nohup and
#    setsid survive), and a SIGKILLed proot must take its tracees with it
#    (docs/garden/SESSION_LIFECYCLE.md).
#  - A guest process's /proc/<pid>/cwd, read from the host, must name the host
#    directory behind its guest working directory after every chdir/fchdir,
#    and a failed chdir must change neither view (docs/garden/UPLOADS.md).
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
# ------------------------------------------------ working directory in step
# Seen from outside PRoot, as ThothTerm reads it: /proc/<pid>/cwd of a guest
# process must be the rootfs path followed by its guest working directory.
R="$WORK/cwd-rootfs"
rm -rf "$R"
mkdir -p "$R/home/thoth/with space/مرحبا" "$R/home/thoth/real" "$R/tmp"
ln -s real "$R/home/thoth/link"
BINDS=""
for dir in /bin /sbin /usr /lib /lib32 /lib64 /libx32 /etc /dev; do
    [ -e "$dir" ] && BINDS="$BINDS -b $dir"
done
cat > "$R/tmp/steps.sh" <<'STEPS'
report() { # report STEP PID
    echo "$2|$(pwd -P)" > "/tmp/step.$1.tmp" && mv "/tmp/step.$1.tmp" "/tmp/step.$1"
    n=0; while [ ! -e "/tmp/ack.$1" ] && [ $n -lt 200 ]; do sleep 0.05; n=$((n+1)); done
}
report 1 $$
cd "with space" && report 2 $$
cd "مرحبا" && report 3 $$
cd /home/thoth/link && report 4 $$
cd /does/not/exist 2>/dev/null; echo "$?" > /tmp/failed-cd; report 5 $$
sh -c 'cd /tmp && . /tmp/report.sh && report 6 $$'
report 7 $$
cd /usr && report 8 $$
cd - >/dev/null && report 9 $$
STEPS
sed -n '/^report()/,/^}/p' "$R/tmp/steps.sh" > "$R/tmp/report.sh"
PROOT_TMP_DIR="$WORK" "$PROOT" -r "$R" $BINDS -w /home/thoth sh /tmp/steps.sh </dev/null >"$WORK/cwd.out" 2>&1 &
CWD_PROOT=$!
step() { # step N GUEST_PHYSICAL HOST_EXPECTED NAME
    n=0; while [ ! -e "$R/tmp/step.$1" ] && [ $n -lt 100 ]; do sleep 0.05; n=$((n+1)); done
    line="$(cat "$R/tmp/step.$1" 2>/dev/null)"
    pid="${line%%|*}"; guest="${line#*|}"
    host="$(readlink "/proc/$pid/cwd" 2>/dev/null)"
    if [ -n "$pid" ] && [ "$guest" = "$2" ] && [ "$host" = "$3" ]; then
        echo "PASS cwd: $4"
    else
        echo "FAIL cwd: $4: guest '$guest' (want '$2'), /proc/$pid/cwd '$host' (want '$3')"
        fail=1
    fi
    touch "$R/tmp/ack.$1"
}
step 1 /home/thoth                    "$R/home/thoth"                    "the first tracee starts behind --cwd"
step 2 "/home/thoth/with space"       "$R/home/thoth/with space"         "cd into a name with a space"
step 3 "/home/thoth/with space/مرحبا" "$R/home/thoth/with space/مرحبا" "relative cd into a Unicode name"
step 4 /home/thoth/real               "$R/home/thoth/real"               "cd through a symlink lands on its target"
step 5 /home/thoth/real               "$R/home/thoth/real"               "a failed cd changes neither view"
step 6 /tmp                           "$R/tmp"                           "a child's cd is its own"
step 7 /home/thoth/real               "$R/home/thoth/real"               "the parent is unaffected by the child"
step 8 /usr                           /usr                               "cd into a binding names the binding's host path"
step 9 /home/thoth/real               "$R/home/thoth/real"               "cd - returns"
result "cwd: the failed cd reported an error to the guest" test "$(cat "$R/tmp/failed-cd" 2>/dev/null)" != 0
wait "$CWD_PROOT" 2>/dev/null
if command -v python3 >/dev/null 2>&1; then
    PROOT_TMP_DIR="$WORK" "$PROOT" -r "$R" $BINDS -w / python3 -c '
import os, time
fd = os.open("/home/thoth/with space", os.O_RDONLY | os.O_DIRECTORY)
os.fchdir(fd)
with open("/tmp/fchdir.tmp", "w") as f:
    f.write("%d|%s" % (os.getpid(), os.getcwd()))
os.rename("/tmp/fchdir.tmp", "/tmp/fchdir")
time.sleep(1.5)
' </dev/null >/dev/null 2>&1 &
    FPROOT=$!
    n=0; while [ ! -e "$R/tmp/fchdir" ] && [ $n -lt 100 ]; do sleep 0.05; n=$((n+1)); done
    line="$(cat "$R/tmp/fchdir" 2>/dev/null)"
    host="$(readlink "/proc/${line%%|*}/cwd" 2>/dev/null)"
    result "cwd: fchdir keeps both views in step" \
        test "${line#*|}" = "/home/thoth/with space" -a "$host" = "$R/home/thoth/with space"
    wait "$FPROOT" 2>/dev/null
else
    echo "SKIP cwd: fchdir (no python3 on this host)"
fi
exit $fail
