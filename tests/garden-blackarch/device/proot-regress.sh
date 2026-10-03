#!/bin/sh
# Shared-PRoot regression checks on the phone, under the QA app's own PRoot and rootfs:
#   0006 fchmodat2: the probe's output under PRoot equals the same probe run natively
#   0007 fork/thread workloads (fork, vfork, posix_spawn, CLONE_PARENT, threads, concurrent)
#   0008 link2symlink: the guest owner on fake hard links, symlinks and plain files, through
#        every stat flavour, as root and as the unprivileged thoth account
#
#   proot-regress.sh QA_PACKAGE EVIDENCE_DIR
#
# Needs ANDROID_NDK_HOME and the garden-common probes. Nothing leaves the QA app's data.
# Patch 0007's withheld-pid recovery is only exercised if this kernel withholds the fork
# event pid; the script reports whether it did and never claims otherwise.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
. "$REPO/tests/garden-common/extractor/apk-identity.sh"
PKG=${1:?usage: proot-regress.sh QA_PACKAGE EVIDENCE_DIR}; E=${2:?}
case "$PKG" in com.thothterm.blackarch.qa.*) ;; *) echo "REFUSED: $PKG"; exit 2 ;; esac
gate_is_qa_id "$PKG" && ! gate_is_protected "$PKG" || { echo "REFUSED: $PKG"; exit 2; }
CC=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang
[ -x "$CC" ] || { echo "ENVIRONMENT BLOCKED: no NDK clang"; exit 2; }
mkdir -p "$E"; PASS=0; FAIL=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; PASS=$((PASS + 1)); else echo "FAIL $2 ${3:-}"; FAIL=$((FAIL + 1)); fi; }
KERNEL=$(adb shell uname -r | tr -d '\r'); echo "kernel $KERNEL"
for p in fork-probe fchmodat2-probe owner-probe; do
    "$CC" -O2 -static -include "$HERE/bionic-shim.h" -w -o "$E/$p" "$REPO/tests/garden-common/proot/$p.c" 2> "$E/build-$p.log" || { echo "ENVIRONMENT BLOCKED: cannot build $p"; exit 2; }
    adb push "$E/$p" /data/local/tmp/qa-$p >/dev/null
    adb shell "run-as $PKG sh -c 'mkdir -p files/qa-probes && cp /data/local/tmp/qa-$p files/qa-probes/$p && chmod 700 files/qa-probes/$p'"
done
guest() { # guest SCRIPT-TEXT [USER]: run in the QA guest as root (or thoth)
    adb shell "run-as $PKG env QA_BIND=--bind=/data/data/$PKG/files/qa-probes:/probes sh files/qa-guest.sh $(printf "'%s'" "$1")" 2>&1 | tr -d '\r' | grep -v '^proot '
}

echo "== 0006 fchmodat2: native vs PRoot"
N=/data/local/tmp/qa-0006; adb shell "rm -rf $N; mkdir -p $N && echo x > $N/file && chmod 644 $N/file && ln -s file $N/link"
adb shell "/data/local/tmp/qa-fchmodat2-probe $N" 2>&1 | tr -d '\r' > "$E/p0006-native.txt"
guest 'rm -rf /tmp/w6; mkdir -p /tmp/w6 && echo x > /tmp/w6/file && chmod 644 /tmp/w6/file && ln -s file /tmp/w6/link && /probes/fchmodat2-probe /tmp/w6' > "$E/p0006-proot.txt"
sed 's#/data/local/tmp/qa-0006#/work#g' "$E/p0006-native.txt" > "$E/p0006-native.norm"
diff "$E/p0006-native.norm" "$E/p0006-proot.txt" > "$E/p0006.diff"; ok $? "0006: fchmodat2 / lchmod / O_PATH behaviour under PRoot equals the kernel's (native)" "$(head -3 "$E/p0006.diff")"
grep -q 'raw fchmodat2(O_PATH fd, "", EMPTY_PATH) = 0' "$E/p0006-proot.txt"; ok $? "0006: fchmodat2 on an O_PATH descriptor (systemd-tmpfiles) succeeds, no EBADF"
grep -q 'raw fchmodat2(/work/link, NOFOLLOW) *= -1 errno=EOPNOTSUPP' "$E/p0006-proot.txt"; ok $? "0006: a no-follow fchmodat2 of a symlink returns EOPNOTSUPP like Linux"

echo "== 0007 fork / thread workloads (this kernel: $KERNEL)"
: > "$E/p0007.txt"
for mode in fork vfork spawn clone-parent thread forks-concurrent threads-concurrent; do
    o=$(guest "timeout 120 /probes/fork-probe $mode; echo rc=\$?")
    echo "$mode: $o" >> "$E/p0007.txt"
    case "$o" in *"ok $mode"*) ok 0 "0007: $mode workload runs under PRoot" ;;
        *"cannot be identified uniquely"*) ok 0 "0007: $mode stopped cleanly (ambiguous child refused)" ;;
        *) ok 1 "0007: $mode ($o)" ;; esac
done
if grep -q 'GETEVENTMSG' "$E/p0007.txt"; then echo "NOTE this kernel WITHHELD a fork event pid: the 0007 recovery path was exercised"
else echo "NOTE this kernel reports fork child pids normally: patch 0007's withheld-pid recovery was NOT exercised here (host-proven only); kernel 4.14 remains UNVERIFIED"; fi

echo "== 0008 guest ownership on link2symlink objects"
cat > "$E/own.sh" <<'EOS'
rm -rf /tmp/o8; mkdir -p /tmp/o8 && cd /tmp/o8
echo data > a && ln a b && ln a c && ln -s a sym && echo more > plain && ln plain plain2
/probes/owner-probe /tmp/o8/a /tmp/o8/b /tmp/o8/c /tmp/o8/sym /tmp/o8/plain /tmp/o8/plain2
echo "find: $(find /tmp/o8 -printf '%u:%g ' | tr ' ' '\n' | sort | uniq -c | tr '\n' ' ')"
echo "stat: $(stat -c '%n %u:%g' /tmp/o8/a /tmp/o8/b /tmp/o8/plain2 | tr '\n' ';')"
EOS
adb shell "run-as $PKG sh -c 'cat > files/qa-probes/own.sh'" < "$E/own.sh"
guest 'bash /probes/own.sh' > "$E/p0008-root.txt"
for want in 0:0; do
    bad=$(grep -E 'uid=[0-9]+ gid=[0-9]+' "$E/p0008-root.txt" | grep -vc 'uid=0 gid=0')
    n=$(grep -cE 'uid=[0-9]+ gid=[0-9]+' "$E/p0008-root.txt")
    [ "$n" -ge 60 ] && [ "$bad" = 0 ]; ok $? "0008 as root: $n stat observations of 6 objects, every one uid=0 gid=0 ($bad differ)"
done
grep '^find:' "$E/p0008-root.txt" | grep -q ' root:root' && [ "$(grep '^find:' "$E/p0008-root.txt" | sed 's/^find://' | grep -o '[A-Za-z0-9_]*:[A-Za-z0-9_]*' | sort -u | tr '\n' ' ')" = "root:root " ]; ok $? "0008 as root: find(1) sees only root:root ($(grep '^find:' "$E/p0008-root.txt" | tr -s ' '))"
adb shell "run-as $PKG env QA_BIND=--bind=/data/data/$PKG/files/qa-probes:/probes sh files/qa-guest.sh 'su -s /bin/bash thoth -c \"bash /probes/own.sh\"'" 2>&1 | tr -d '\r' | grep -v '^proot ' > "$E/p0008-thoth.txt"
tuid=$(sed -n 's/^.*stat: .* \([0-9]*\):[0-9]*;.*/\1/p' "$E/p0008-thoth.txt" | head -1)
n=$(grep -cE 'uid=[0-9]+ gid=[0-9]+' "$E/p0008-thoth.txt"); u=$(grep -E 'uid=[0-9]+ gid=[0-9]+' "$E/p0008-thoth.txt" | grep -o 'uid=[0-9]*' | sort -u | tr '\n' ' ')
[ "$n" -ge 60 ] && [ "$(echo $u | wc -w)" = 1 ]; ok $? "0008 as thoth: $n observations of 6 objects, every flavour agrees on one owner ($u)"
grep '^find:' "$E/p0008-thoth.txt" | grep -q ' thoth:thoth' && [ "$(grep '^find:' "$E/p0008-thoth.txt" | sed 's/^find://' | grep -o '[A-Za-z0-9_]*:[A-Za-z0-9_]*' | sort -u | tr '\n' ' ')" = "thoth:thoth " ]; ok $? "0008 as thoth: find(1) sees only thoth:thoth ($(grep '^find:' "$E/p0008-thoth.txt" | tr -s ' '))"
echo "SUMMARY: $PASS PASS, $FAIL FAIL"; [ "$FAIL" = 0 ]
