#!/bin/sh
# Host check of patch 0009 (--net-ip, ThothDock user-defined networks). Builds
# the pinned PRoot with every garden-common patch, then runs a static probe
# (as Go and Rust workloads are) with the host's / as the guest:
#
#   - one container's view: bind/connect/sendto rules, low-port shift,
#     V6ONLY and nginx-style listeners, the application buffer untouched;
#   - two containers binding the same port at once, reaching each other by
#     address, the host reaching both, the gateway reaching the host;
#   - nothing listens on 0.0.0.0, so nothing is exposed beyond loopback;
#   - bad --net-ip values are refused, and without the option nothing changes.
#
# Needs gcc (with static libc), make, patch, python3, ss. Prints PASS/FAIL
# lines; exits non-zero on any FAIL, 2 when the host cannot run it.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
W=${WORK:-$(mktemp -d)}
fail=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2"; fail=1; fi; }

missing=""
for tool in gcc make patch python3 ss timeout; do
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
    -o "$W/talloc.o"
ar rcs "$W/lib/libtalloc.a" "$W/talloc.o"

rm -rf "$W/proot"
cp -R "$REPO/third_party/proot" "$W/proot"; rm -rf "$W/proot/.git"
for p in "$REPO"/garden-common/patches/*.patch; do
    (cd "$W/proot" && patch -p1 --batch --silent < "$p") || { echo "FAIL patch $(basename "$p")"; exit 1; }
done
make -s -C "$W/proot/src" CFLAGS="-I$REPO/third_party/talloc -O2" \
    LDFLAGS="-L$W/lib -ltalloc" proot >"$W/build.log" 2>&1 || { tail -20 "$W/build.log"; echo "FAIL build"; exit 1; }
PROOT="$W/proot/src/proot"
gcc -O2 -static -o "$W/probe" "$HERE/net-ip-probe.c" 2>"$W/probe.log" \
    || { cat "$W/probe.log"; echo "ENVIRONMENT BLOCKED (not a PRoot result): no static libc"; exit 2; }
export PROOT_TMP_DIR="$W/tmp"; mkdir -p "$PROOT_TMP_DIR"
in_proot() { ip=$1; shift; "$PROOT" -r / --kill-on-exit --net-ip="$ip" "$@"; }

$PROOT --help | grep -q -- '--net-ip' ; ok $? "proot --help lists --net-ip"

# 1. One container's view of its own network.
in_proot 127.77.0.5 "$W/probe" self 127.77.0.5 > "$W/self.log" 2>&1
st=$?
sed 's/^/    /' "$W/self.log"
ok $st "single-container rules (static binary)"

# 2. Two containers, one port. Only listeners that appear now count: the host
# may run its own services on these ports.
ss -Hltn | awk '{print $4}' | sort > "$W/listeners.before"
PIDS=""
# PRoot ignores SIGTERM while its tracee runs: SIGKILL ends it and the tracee.
cleanup() { for p in $PIDS; do kill -9 "$p" 2>/dev/null; done; wait 2>/dev/null; }
trap cleanup EXIT
in_proot 127.77.0.5 "$W/probe" serve 24000 > "$W/a.log" 2>&1 & PIDS="$PIDS $!"
in_proot 127.77.0.6 "$W/probe" serve 24000 > "$W/b.log" 2>&1 & PIDS="$PIDS $!"
in_proot 127.77.0.7 "$W/probe" serve 80 > "$W/c.log" 2>&1 & PIDS="$PIDS $!"
for _ in $(seq 50); do
    [ "$(cat "$W/a.log" "$W/b.log" "$W/c.log" | grep -c serving)" = 3 ] && break
    sleep 0.1
done
ok "$( [ "$(cat "$W/a.log" "$W/b.log" "$W/c.log" | grep -c serving)" = 3 ]; echo $?)" \
    "two containers bind 0.0.0.0:24000 at the same time (and one binds :80)"
ss -Hltn | awk '{print $4}' | sort > "$W/listeners.after"
listeners=$(comm -13 "$W/listeners.before" "$W/listeners.after")
echo "$listeners" | grep -qx '127.77.0.5:24000' && echo "$listeners" | grep -qx '127.77.0.6:24000' && echo "$listeners" | grep -qx '127.77.0.7:30080'
ok $? "listeners are 127.77.0.5:24000, 127.77.0.6:24000, 127.77.0.7:30080"
echo "$listeners" | grep -Eq '^(0\.0\.0\.0|\*|\[::\]):(24000|80|30080)$'
[ $? = 1 ]; ok $? "no workload listener on a wildcard address (not exposed to the LAN)"
[ "$(in_proot 127.77.0.6 "$W/probe" ask 127.77.0.5 24000)" = pong ]
ok $? "container .6 reaches container .5 by address"
[ "$(in_proot 127.77.0.5 "$W/probe" ask 127.77.0.7 80)" = pong ]
ok $? "container .5 reaches container .7 on port 80 (shifted both sides)"
[ "$(python3 -I -c 'import socket; print(socket.create_connection(("127.77.0.6", 24000)).recv(16).decode().strip())')" = pong ]
ok $? "the host reaches a container address (the -p forwarder's path)"

# 3. The gateway is the host's 127.0.0.1.
python3 -I -c '
import socket
s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", 24100)); s.listen()
print("ready", flush=True)
while True:
    c, _ = s.accept(); c.sendall(b"host\n"); c.close()
' > "$W/host.log" 2>&1 & PIDS="$PIDS $!"
for _ in $(seq 50); do grep -q ready "$W/host.log" && break; sleep 0.1; done
[ "$(in_proot 127.77.0.5 "$W/probe" ask 127.77.0.1 24100)" = host ]
ok $? "127.77.0.1 (host.docker.internal) reaches the host's 127.0.0.1"
in_proot 127.77.0.5 "$W/probe" ask 127.0.0.1 24100 > /dev/null 2>&1
[ $? != 0 ]; ok $? "the host's 127.0.0.1 services are not the container's localhost"

# 4. Refused values, and no change without the option.
for bad in 10.0.0.1 127.77.0.1 127.77.0.0 127.77.255.255 127.78.0.2 nonsense; do
    "$PROOT" -r / --net-ip="$bad" /bin/true > /dev/null 2>&1
    [ $? != 0 ]; ok $? "--net-ip=$bad is refused"
done
"$PROOT" -r / --kill-on-exit "$W/probe" serve 24200 > "$W/plain.log" 2>&1 & PIDS="$PIDS $!"
for _ in $(seq 50); do grep -q serving "$W/plain.log" && break; sleep 0.1; done
ss -Hltn | awk '{print $4}' | grep -qx '0.0.0.0:24200'
ok $? "without --net-ip, bind 0.0.0.0 is unchanged"

exit $fail
