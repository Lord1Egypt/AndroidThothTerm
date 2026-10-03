#!/bin/sh
# Host extractor gate: the shared extractor policy (garden-common/src/shared)
# on this machine's JVM, with the JVM FileOps.
#
#   tests/garden-common/extractor/host-gate.sh                       # security cases only
#   tests/garden-common/extractor/host-gate.sh ARCHIVE.tar.gz SHA256 # + the real archive
#
# This proves the extraction POLICY and the archive contents. It is not proof of
# the APK's AndroidFileOps: run device-gate.sh for that. A GNU tar extraction
# (tests/garden-*/device/gate.sh) proves neither.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
WORK=${WORK:-$(mktemp -d)}
OUT="$WORK/classes"
mkdir -p "$OUT"
# File names are UTF-8 on Android; the JVM needs a UTF-8 locale to match.
export LANG=C.UTF-8 LC_ALL=C.UTF-8
find "$REPO/garden-common/src/shared/java" "$REPO/garden-common/src/sharedTestFixtures/java" \
    -name '*.java' ! -name 'AndroidFileOps.java' > "$WORK/sources.txt"
javac -nowarn -encoding UTF-8 -d "$OUT" @"$WORK/sources.txt"
if [ $# -ge 2 ]; then
    echo "INFO archive $1"
    actual=$(sha256sum "$1" | cut -d' ' -f1)
    [ "$actual" = "$2" ] || { echo "FAIL archive sha256 $actual, expected $2"; exit 1; }
    python3 "$HERE/manifest.py" "$1" > "$WORK/expected.manifest"
    echo "INFO independent manifest: $(wc -l < "$WORK/expected.manifest") paths (Python tarfile)"
    java --add-opens java.base/java.io=ALL-UNNAMED -cp "$OUT" com.thothterm.linux.ExtractorGate "$WORK/run" "$1" "$2" "$WORK/expected.manifest"
else
    java --add-opens java.base/java.io=ALL-UNNAMED -cp "$OUT" com.thothterm.linux.ExtractorGate "$WORK/run"
fi
