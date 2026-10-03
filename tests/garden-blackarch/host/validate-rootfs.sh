#!/bin/sh
#
# validate-rootfs.sh ARCHIVE SHA256 [LOG_DIR]
#
# Host validation of a ThothTerm BlackArch rootfs candidate, before its pin is
# adopted. Needs docker with an aarch64 binfmt_misc handler (qemu-user), a JDK,
# python3 and the built garden-common classes (gradlew :garden-common:compileDebugJavaWithJavac).
#
#   A  the archive's SHA-256 is the expected one
#   B  the archive's paths are safe (independent Python check)
#   C  the Garden host extractor gate on the real archive (the app's extraction
#      policy, JVM FileOps; not GNU tar, and not proof of AndroidFileOps)
#   D-H  in an arm64 container that is this rootfs: structure, pacman -Dk/-Qk,
#      both keyrings through the app's own keyring script, a real repository
#      sync, signed installs and removals, and refusals (container-checks.sh)
#
# Exit 0 only when everything passes. Nothing is installed on a phone, and no
# BlackArch tool is run.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
ARCHIVE=${1:?usage: validate-rootfs.sh ARCHIVE SHA256 [LOG_DIR]}
SHA=${2:?usage: validate-rootfs.sh ARCHIVE SHA256 [LOG_DIR]}
LOG=${3:-$(mktemp -d)}
mkdir -p "$LOG"
fail() { echo "FAIL $*"; exit 1; }

echo "== A. SHA-256"
actual=$(sha256sum "$ARCHIVE" | cut -d' ' -f1)
[ "$actual" = "$SHA" ] || fail "archive sha256 $actual, expected $SHA"
echo "PASS archive sha256 $actual, $(stat -c %s "$ARCHIVE") bytes"

echo "== B. archive paths"
python3 - "$ARCHIVE" <<'PY' | tee "$LOG/archive-paths.txt"
import sys, tarfile, collections
names = collections.Counter(); kinds = collections.Counter(); bad = []
files = {}
with tarfile.open(sys.argv[1], "r:gz") as t:
    for m in t:
        n = m.name
        if n.startswith("./"): n = n[2:]
        parts = n.replace("\\", "/").split("/") if n not in (".", "") else []
        if n.startswith("/") or ".." in parts or "\0" in n or "//" in n: bad.append("unsafe path " + repr(m.name))
        try: n.encode("utf-8")
        except UnicodeError: bad.append("not UTF-8 " + repr(m.name))
        names[n] += 1
        kind = "dir" if m.isdir() else "symlink" if m.issym() else "hardlink" if m.islnk() else "file" if m.isfile() else "other"
        kinds[kind] += 1
        if kind == "other": bad.append("special file " + repr(m.name))
        if m.islnk():
            tgt = m.linkname[2:] if m.linkname.startswith("./") else m.linkname
            if tgt.startswith("/") or ".." in tgt.split("/"): bad.append("hardlink target escapes: " + repr(m.name))
            elif tgt not in files: bad.append("hardlink target not seen before the link: " + repr(m.name) + " -> " + tgt)
        if m.isfile(): files[n] = 1
        if m.issym() and m.linkname == "": bad.append("empty symlink target " + repr(m.name))
dups = [n for n, c in names.items() if c > 1]
print("entries:", sum(kinds.values()), dict(kinds))
print("distinct paths:", len(names))
print("duplicate names:", len(dups), dups[:5])
for b in bad[:20]: print("PROBLEM", b)
sys.exit(1 if bad or dups else 0)
PY
echo "PASS archive paths are safe and unambiguous"

echo "== C. Garden host extractor gate (the app's extraction policy)"
sh "$REPO/tests/garden-common/extractor/host-gate.sh" "$ARCHIVE" "$SHA" > "$LOG/host-gate.txt" 2>&1 || { tail -20 "$LOG/host-gate.txt"; fail "extractor gate"; }
tail -6 "$LOG/host-gate.txt"
grep -q 'GATE PASS' "$LOG/host-gate.txt" || fail "no GATE PASS"

echo "== D-H. the rootfs in an arm64 container"
command -v docker >/dev/null || fail "no docker"
CLASSES=$(find "$REPO/garden-common/build/intermediates/javac" -path '*debug*' -name RootfsManager.class | head -1 | sed 's#/com/thothterm/linux/RootfsManager.class##')
[ -n "$CLASSES" ] || fail "garden-common is not compiled (gradlew :garden-common:compileDebugJavaWithJavac)"
ANDROID_JAR=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/platforms/android-*/android.jar | sort -V | tail -1)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
javac -nowarn -d "$T/cls" -cp "$CLASSES:$ANDROID_JAR" "$HERE/DumpKeyringScript.java"
java -cp "$T/cls:$CLASSES:$ANDROID_JAR" com.thothterm.linux.DumpKeyringScript \
    "$REPO/garden-blackarch/src/main/assets/garden/distro.properties" > "$LOG/keyring-script.sh"
grep -q 'pacman-key --populate archlinuxarm blackarch' "$LOG/keyring-script.sh" || fail "the keyring script does not populate both keyrings"
IMAGE=thothterm-blackarch-validate:$(printf %.12s "$SHA")
docker rmi -f "$IMAGE" > /dev/null 2>&1 || true
docker import --platform linux/arm64 "$ARCHIVE" "$IMAGE" > /dev/null
set +e
docker run --rm --platform linux/arm64 --privileged \
    -v "$REPO/garden-blackarch/rootfs:/builder:ro" -v "$LOG:/w:ro" -v "$HERE:/h:ro" \
    "$IMAGE" /bin/bash /h/container-checks.sh > "$LOG/container-checks.txt" 2>&1
rc=$?
cat "$LOG/container-checks.txt"
set -e
docker rmi -f "$IMAGE" > /dev/null 2>&1 || true
[ "$rc" -eq 0 ] || fail "container checks (see $LOG/container-checks.txt)"
echo "VALIDATION PASS ($LOG)"
