#!/bin/sh
# Host check of patches 0011-0017 with the pinned PRoot and every
# garden-common patch, as fake root (--root-id):
#   - setpriv's sequence for the Redis 8 entrypoint (PR_SET_KEEPCAPS,
#     setresuid, capset, setgroups, bounding-set drop, ambient raise)
#     succeeds, and without KEEPCAPS setgroups and the bounding-set drop
#     still fail with EPERM, as on Linux;
#   - SIGQUIT sent to PRoot's process group (docker stop of nginx) reaches
#     the guest's handler instead of PRoot killing the guest;
#   - patch 0013: --hostname makes uname report the container's host name,
#     also with --kernel-release after it (ThothDock's order);
#   - patch 0016: chroot(2) by a fake-root process takes effect (also nested)
#     instead of aborting PRoot (sshd privilege separation);
#   - patch 0014: a Unix socket whose host path is longer than sun_path,
#     bound in one PRoot, is a real socket at its path that another PRoot
#     connects to; both see the guest path; it is gone after unlink.
# Exit 2 when the host cannot run it.
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
gcc -O2 -static -o "$W/probe" "$HERE/setpriv-probe.c" || { echo "ENVIRONMENT BLOCKED (not a PRoot result): no static libc"; exit 2; }
export PROOT_TMP_DIR="$W"
"$W/proot/src/proot" -r / --root-id "$W/probe" keep; ok $? "KEEPCAPS: capset, setgroups, bounding drop and ambient raise succeed (setpriv)"
"$W/proot/src/proot" -r / --root-id "$W/probe" drop; ok $? "without KEEPCAPS: setgroups and the bounding drop fail with EPERM"
# setsid gives PRoot its own process group, as ThothDock does; -w makes it
# return PRoot's exit status, which is the guest's.
setsid -w "$W/proot/src/proot" -r / --root-id "$W/probe" quit > "$W/quit.log" 2>&1 &
sp=$!
for _ in $(seq 50); do grep -q waiting "$W/quit.log" && break; sleep 0.1; done
pg=$(sed -n 's/^waiting pgid=\([0-9]*\)$/\1/p' "$W/quit.log")
[ -n "$pg" ] && kill -QUIT "-$pg"  # dash: no "--"
wait $sp
code=$?
ok "$([ "$code" = 3 ]; echo $?)" "SIGQUIT to the process group reaches the guest's handler (exit $code, 3 expected)"
P="$W/proot/src/proot"
[ "$("$P" -r / --hostname=compat-node uname -n)" = compat-node ]
ok $? "--hostname: uname -n reports the container's host name"
[ "$("$P" -r / --hostname=compat-node --kernel-release=6.1.0-thoth uname -nr)" = "compat-node 6.1.0-thoth" ]
ok $? "--hostname before --kernel-release: both apply"
[ "$("$P" -r / uname -n)" = "$(uname -n)" ]
ok $? "without --hostname the node name is the kernel's"
"$P" -r / --hostname=a/b true 2>/dev/null; [ $? != 0 ]
ok $? "an invalid host name is refused"
# A guest directory whose host path makes the socket path ~150 bytes.
DEEP="$W/deep/$(printf '%0100d' 0)"
mkdir -p "$DEEP"
gcc -O2 -static -o "$W/unix" "$HERE/unix-probe.c" || exit 2
mkfifo "$W/quit"
"$P" -r / -b "$DEEP:/deep" "$W/unix" serve /deep/s.sock < "$W/quit" > "$W/serve.log" 2>&1 &
sp=$!
exec 9> "$W/quit"
for _ in $(seq 50); do grep -q listening "$W/serve.log" && break; sleep 0.1; done
[ -S "$DEEP/s.sock" ]
ok $? "the socket is a real socket at its host path"
grep -q "^listening /deep/s.sock$" "$W/serve.log"
ok $? "getsockname in the server reports the guest path"
out=$("$P" -r / -b "$DEEP:/deep" "$W/unix" connect /deep/s.sock)
[ "$out" = "pong peer=/deep/s.sock" ]
ok $? "another PRoot connects and sees the guest path ($out)"
echo quit >&9; exec 9>&-; wait $sp
"$P" -r / -b "$DEEP:/deep" "$W/unix" connect /deep/s.sock 2>/dev/null; [ $? != 0 ] && [ ! -e "$DEEP/s.sock" ]
ok $? "after unlink the address is gone"
# Security of the aliases (patch 0014): names are random, a connect leaves
# nothing behind, and a dead socket's alias is swept at the next bind.
for n in 1 2; do
    "$P" -r / -b "$DEEP:/deep" "$W/unix" serve /deep/n$n.sock < "$W/quit" > "$W/n$n.log" 2>&1 &
    eval "sp$n=\$!"
done
exec 8> "$W/quit"
for _ in $(seq 50); do grep -q listening "$W/n1.log" && grep -q listening "$W/n2.log" && break; sleep 0.1; done
set -- $(ls "$W" | grep '^prootsock-b-[0-9a-f]\{16\}$')
[ "$#" -ge 2 ] && [ "$1" != "$2" ]
ok $? "bound sockets get distinct unguessable names (prootsock-b-<16 hex>, $# found)"
"$P" -r / -b "$DEEP:/deep" "$W/unix" connect /deep/n1.sock >/dev/null
"$P" -r / -b "$DEEP:/deep" "$W/unix" connect /deep/n2.sock >/dev/null
[ -z "$(ls "$W" | grep '^prootsock-c-')" ]
ok $? "a connect leaves no alias behind"
echo quit >&8; exec 8>&-; wait $sp1 $sp2
before=$(ls "$W" | grep -c '^prootsock-b-')
timeout 2 "$P" -r / -b "$DEEP:/deep" "$W/unix" serve /deep/n3.sock < /dev/null > "$W/n3.log" 2>&1
after=$(ls "$W" | grep -c '^prootsock-b-')
[ "$after" -lt "$before" ] || [ "$after" -le 1 ]
ok $? "aliases of dead sockets are swept at the next bind ($before -> $after)"
"$P" -r / -b "$DEEP:/deep" sh -c ': > /deep/s.sock'
"$P" -r / -b "$DEEP:/deep" "$W/unix" serve /deep/s.sock < /dev/null > "$W/inuse.log" 2>&1
grep -q "Address already in use" "$W/inuse.log"
ok $? "binding over an existing file fails with EADDRINUSE"
# Patch 0016: guest chroot.
gcc -O2 -static -o "$W/chroot-probe" "$HERE/chroot-probe.c" || exit 2
mkdir -p "$W/croot/sub"; : > "$W/croot/marker"; : > "$W/croot/sub/inner"
"$P" -r / -0 "$W/chroot-probe" "$W/croot" marker > "$W/chroot.log" 2>&1
ok $? "chroot as fake root takes effect, also nested ($(tail -1 "$W/chroot.log"))"
# With an extra bind mount present (the volume of a container): patch 0017.
mkdir -p "$W/xbind"
"$P" -r / -0 -b "$W/xbind:/extra" "$W/chroot-probe" "$W/croot" marker > "$W/chroot2.log" 2>&1
ok $? "chroot with another bind mount in the container"
"$P" -r / -0 "$W/chroot-probe" "$W/does-not-exist" marker >/dev/null 2>&1; [ $? != 0 ]
ok $? "chroot into a missing directory fails cleanly (no abort)"
# Patch 0015: the host's resolver stub address stays reachable from a --net-ip guest.
if command -v python3 >/dev/null 2>&1 && python3 -c 'import socket; s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.bind(("127.0.0.53", 0))' 2>/dev/null; then
    cat > "$W/udp-echo.py" <<'PY'
import socket, sys
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("127.0.0.53", 25353))
s.settimeout(10)
print("up", flush=True)
data, peer = s.recvfrom(64)
s.sendto(b"stub:" + data, peer)
PY
    python3 "$W/udp-echo.py" > "$W/udp.log" 2>&1 &
    up=$!
    for _ in $(seq 50); do grep -q '^up' "$W/udp.log" && break; sleep 0.1; done
    reply=$("$P" -r / --net-ip=127.77.0.9 python3 -c '
import socket
c = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
c.settimeout(5)
c.sendto(b"q", ("127.0.0.53", 25353))
print(c.recvfrom(64)[0].decode())' 2>&1)
    kill $up 2>/dev/null; wait $up 2>/dev/null
    [ "$reply" = "stub:q" ]
    ok $? "--net-ip: a query to 127.0.0.53 reaches the host's resolver stub address ($reply)"
else
    echo "SKIP 127.0.0.53 is not usable on this host"
fi
exit $fail
