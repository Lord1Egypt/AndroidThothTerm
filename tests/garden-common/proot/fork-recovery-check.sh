#!/bin/sh
# Host check of patch 0007 (PRoot child tracking when PTRACE_GETEVENTMSG gives
# no pid), the F-Droid reviewer's hang on a Redmi Note 10 Pro (crDroid,
# Android 16, kernel 4.14): a child stopped in ptrace ("t"), its vfork parent
# in "D", "proot warning: ptrace(GETEVENTMSG): Invalid argument".
#
# No host kernel withholds the pid, so the condition is forced at compile time
# in test builds only (THOTHTERM_TEST_NO_EVENTMSG; build-proot.sh never sets
# it). This proves PRoot's handling of that condition, not the reviewer's
# kernel: that stays a device check.
#
#   stock   0001-0006                           normal kernel behaviour
#   stuck   0001-0006, pid forced to 0           the reviewer's condition, before
#   fixed   every patch                          normal kernel behaviour, after
#   forced  every patch, pid forced to 0         the reviewer's condition, after
#
# Needs gcc, make, patch, timeout, ps. Prints PASS/FAIL lines; exits non-zero
# on any FAIL, 2 when the host cannot run it.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
W=${WORK:-$(mktemp -d)}
fail=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2"; fail=1; fi; }

missing=""
for tool in gcc make patch timeout ps; do
    command -v "$tool" >/dev/null 2>&1 || missing="$missing $tool"
done
if [ -n "$missing" ]; then
    echo "ENVIRONMENT BLOCKED (not a PRoot result): missing$missing"
    exit 2
fi
[ -f "$REPO/third_party/proot/src/GNUmakefile" ] || { echo "run: git submodule update --init third_party/proot"; exit 2; }

mkdir -p "$W/include" "$W/lib"
sed -n '/^cat > "\$BUILD_DIR\/include\/replace.h" <<.REPLACE_H./,/^REPLACE_H$/p' \
    "$REPO/garden-common/tools/build-proot.sh" | sed '1d;$d' > "$W/include/replace.h"
gcc -O2 -fPIC -c "$REPO/third_party/talloc/talloc.c" -I"$W/include" -I"$REPO/third_party/talloc" \
    -DHAVE_VA_COPY -DHAVE_STDBOOL_H -DHAVE_STDINT_H -DHAVE_CONSTRUCTOR_ATTRIBUTE \
    -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=4 -DTALLOC_BUILD_VERSION_RELEASE=3 \
    -o "$W/talloc.o" || exit 2
ar rcs "$W/lib/libtalloc.a" "$W/talloc.o"

build() { # build NAME MAX_PATCH_NUMBER FORCE_NO_PID(0|1)
    rm -rf "$W/$1"; mkdir -p "$W/$1"
    cp -R "$REPO/third_party/proot" "$W/$1/proot"; rm -rf "$W/$1/proot/.git"
    for p in "$REPO"/garden-common/patches/*.patch; do
        n=$(basename "$p" | cut -c1-4)
        [ "$n" -le "$2" ] || continue
        (cd "$W/$1/proot" && patch -p1 --batch --silent < "$p") || return 1
    done
    extra=""
    if [ "$3" = 1 ]; then
        if [ "$2" -ge 7 ]; then
            extra="-DTHOTHTERM_TEST_NO_EVENTMSG"
        else
            # Before 0007 there is no hook: zero the pid right after the call.
            sed -i 's|^\(\tstatus = ptrace(PTRACE_GETEVENTMSG, parent->pid, NULL, &pid);\)$|\1\n\tpid = 0;|' \
                "$W/$1/proot/src/tracee/tracee.c"
            grep -q '^	pid = 0;$' "$W/$1/proot/src/tracee/tracee.c" || return 1
        fi
    fi
    make -s -C "$W/$1/proot/src" CFLAGS="-I$REPO/third_party/talloc -O2 $extra" \
        LDFLAGS="-L$W/lib -ltalloc -lpthread" proot >"$W/$1.build.log" 2>&1
}
for b in "stock 6 0" "stuck 6 1" "fixed 9999 0" "forced 9999 1"; do
    set -- $b
    build "$1" "$2" "$3" || { echo "ENVIRONMENT BLOCKED: building $1 failed"; cat "$W/$1.build.log"; exit 2; }
done
gcc -O2 -pthread -o "$W/fork-probe" "$HERE/fork-probe.c" || exit 2

# run BUILD MODE -> sets RC and OUT; RC 124 = still running after 60 s (a hang)
run() {
    OUT="$W/out-$1-$2.txt"
    (cd / && timeout -k 5 60 "$W/$1/proot/src/proot" -r / "$W/fork-probe" "$2") > "$OUT" 2>&1
    RC=$?
    leftover=$(ps -eo pid=,args= | grep -F "$W/fork-probe $2" | grep -v grep || true)
    if [ -n "$leftover" ]; then
        echo "  leftover processes after $1 $2:"; echo "$leftover" | sed 's/^/    /'
        echo "$leftover" | awk '{print $1}' | xargs -r kill -9 2>/dev/null
        RC=99
    fi
}

echo "--- the reviewer's condition reproduced without the fix (stuck)"
OUT="$W/out-stuck-hang.txt"
(cd / && "$W/stuck/proot/src/proot" -r / "$W/fork-probe" vfork) > "$OUT" 2>&1 &
STUCK=$!
sleep 5
states=$(ps -eo pid=,ppid=,stat=,args= | grep -F "$W/fork-probe vfork" | grep -v grep)
echo "$states" | sed 's/^/    /'
kill -9 "$STUCK" 2>/dev/null; wait "$STUCK" 2>/dev/null
sleep 1
ps -eo pid=,args= | grep -F "$W/fork-probe vfork" | grep -v grep | awk '{print $1}' | xargs -r kill -9 2>/dev/null
grep -q "GETEVENTMSG" "$OUT"; ok $? "stuck: PRoot warns about ptrace(GETEVENTMSG), as on the device"
echo "$states" | awk '{print $3}' | grep -q '^t'; ok $? "stuck: a child is left stopped under ptrace (t)"
echo "$states" | awk '{print $3}' | grep -q '^D'; ok $? "stuck: its vfork parent waits in D"

for mode in fork vfork spawn clone-parent thread threads-concurrent forks-concurrent; do
    run stock "$mode"; [ "$RC" = 0 ] && grep -q "^ok $mode" "$OUT"; ok $? "stock  $mode"
    run fixed "$mode"; [ "$RC" = 0 ] && grep -q "^ok $mode" "$OUT"; ok $? "fixed  $mode (0007, normal kernel)"
done

# Sequential workloads have exactly one new child per event: always recovered.
for mode in fork vfork spawn clone-parent thread; do
    run forced "$mode"
    [ "$RC" = 0 ] && grep -q "^ok $mode" "$OUT"; ok $? "forced $mode: every child recovered, no hang"
done
# Concurrent creation can leave two unidentified children at one event. Either
# outcome is correct; a hang, a wrong result or a leftover process is not.
for mode in threads-concurrent forks-concurrent; do
    recovered=0; refused=0; bad=0
    for i in 1 2 3 4 5; do
        run forced "$mode"
        if [ "$RC" = 0 ] && grep -q "^ok $mode" "$OUT"; then
            recovered=$((recovered + 1))
        elif [ "$RC" != 124 ] && [ "$RC" != 99 ] && grep -q "cannot be identified uniquely" "$OUT"; then
            refused=$((refused + 1))
        else
            bad=$((bad + 1)); echo "  run $i: rc=$RC"; sed 's/^/    /' "$OUT" | tail -5
        fi
    done
    echo "  forced $mode: recovered=$recovered refused-and-stopped=$refused other=$bad"
    [ "$bad" = 0 ]; ok $? "forced $mode: each run completes or stops cleanly, never hangs"
done
exit $fail
