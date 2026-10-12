#!/bin/sh
# Host check of patch 0010 (System V IPC kept inside proot, for ThothDock's
# --sysvipc). Builds the pinned PRoot with every garden-common patch and runs
# ipc-probe.c (a static binary, the host's / as the guest) in an IPC
# namespace whose limits are zero, so any call that slipped past PRoot to the
# kernel would fail, as it does on Android (no CONFIG_SYSVIPC):
#
#   - the probe passes 20 times in a row (semantics, errno values, 16
#     simultaneous attaches, attach from a thread, semaphore wake-ups);
#   - no "proot --shm-helper" process exists while a segment is held;
#   - nothing is left in PROOT_TMP_DIR afterwards;
#   - without --sysvipc the calls still reach the kernel (unchanged).
#
# Needs gcc (with static libc), make, patch and unprivileged user + IPC
# namespaces. Prints PASS/FAIL lines; exits non-zero on any FAIL, 2 when the
# host cannot run it.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
W=${WORK:-$(mktemp -d)}
fail=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2"; fail=1; fi; }

for tool in gcc make patch unshare; do
    command -v "$tool" >/dev/null 2>&1 || { echo "ENVIRONMENT BLOCKED (not a PRoot result): missing $tool"; exit 2; }
done
unshare -ri true 2>/dev/null || { echo "ENVIRONMENT BLOCKED (not a PRoot result): no unprivileged IPC namespaces"; exit 2; }
[ -f "$REPO/third_party/proot/src/GNUmakefile" ] || { echo "run: git submodule update --init third_party/proot"; exit 2; }

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
PROOT="$W/proot/src/proot"
gcc -O2 -static -pthread -o "$W/probe" "$HERE/ipc-probe.c" 2>"$W/probe.log" \
    || { cat "$W/probe.log"; echo "ENVIRONMENT BLOCKED (not a PRoot result): no static libc"; exit 2; }
T="$W/tmp"; mkdir -p "$T"
export PROOT_TMP_DIR="$T" PROOT_DONT_SHARE_LIBANDROID_SHMEM=1
# ipcns COMMAND...: COMMAND in a new IPC namespace with zero limits.
ipcns() {
    unshare -ri sh -c 'echo 0 > /proc/sys/kernel/shmmni && echo "0 0 0 0" > /proc/sys/kernel/sem &&
        echo 0 > /proc/sys/kernel/msgmni && exec "$@"' ipcns "$@"
}

bad=0
for i in $(seq 20); do
    ipcns "$PROOT" -r / --kill-on-exit --sysvipc "$W/probe" all > "$W/run.log" 2>&1 \
        || { bad=$((bad + 1)); grep -v '^PASS' "$W/run.log" | sed 's/^/    /'; }
done
ok $bad "ipc-probe passes 20/20 runs under --sysvipc ($bad failed)"

ipcns "$PROOT" -r / --kill-on-exit --sysvipc "$W/probe" hold 3 > "$W/hold.log" 2>&1 &
holder=$!
sleep 1.5
helpers=$(pgrep -f '^proot --shm-helper$' | wc -l)
ok "$( [ "$helpers" = 0 ]; echo $?)" "no proot --shm-helper while a segment is held ($helpers)"
wait $holder
ok "$( grep -q '^PASS hold' "$W/hold.log"; echo $?)" "segment and semaphore held"
left=$(ls -A "$T" | grep -c '^prootshm')
ok "$( [ "$left" = 0 ]; echo $?)" "nothing left in PROOT_TMP_DIR ($left)"

ipcns "$PROOT" -r / --kill-on-exit "$W/probe" postgres > "$W/off.log" 2>&1
grep -q 'errno=ENOSPC' "$W/off.log"
ok $? "without --sysvipc the call reaches the kernel (ENOSPC in the zero-limit namespace)"
exit $fail
