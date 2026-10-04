#!/bin/sh
#
# validate-rootfs.sh ARCHIVE SHA256 KEYRING_PKG KEYRING_SIG [LOG_DIR]
#
# Host validation of the ThothTerm Security base system and of its optional
# third-party repository setup. Needs docker with an aarch64 binfmt_misc
# handler (qemu-user), a JDK, python3 and the built garden-common classes
# (gradlew :garden-common:compileDebugJavaWithJavac).
#
#   A  the archive's SHA-256 is the pinned one, and Rolling's
#   B  the archive's paths are safe (independent Python check)
#   C  the Garden host extractor gate on the real archive
#   D-F in an arm64 container that is this rootfs: it ships no third-party
#      keyring or repository, then the optional-repository script generated
#      from distro.properties, run against the real pinned artifact: success,
#      idempotence, every refusal, offline failure and retry (container-checks.sh)
#
# KEYRING_PKG / KEYRING_SIG are the pinned upstream files, fetched from the
# upstream site beforehand; they are compared with the pins first.
# Exit 0 only when everything passes. Nothing is installed on a phone, and no
# tool of the third-party repository is run.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
ARCHIVE=${1:?usage: validate-rootfs.sh ARCHIVE SHA256 KEYRING_PKG KEYRING_SIG [LOG_DIR]}
SHA=${2:?}
PKG=${3:?}
SIG=${4:?}
LOG=${5:-$(mktemp -d)}
mkdir -p "$LOG"
LOG=$(cd "$LOG" && pwd)
fail() { echo "FAIL $*"; exit 1; }
prop() { sed -n "s/^$1=//p" "$REPO/garden-security/src/main/assets/garden/distro.properties"; }

if [ -z "${SKIP_HOST:-}" ]; then
echo "== A. SHA-256"
actual=$(sha256sum "$ARCHIVE" | cut -d' ' -f1)
[ "$actual" = "$SHA" ] || fail "archive sha256 $actual, expected $SHA"
[ "$SHA" = "$(prop sha256)" ] || fail "archive is not the one distro.properties pins"
[ "$SHA" = "$(sed -n 's/^sha256=//p' "$REPO/garden-arch/src/main/assets/garden/distro.properties")" ] \
    || fail "Security's base is not Rolling's pinned archive"
[ "$(stat -c %s "$ARCHIVE")" = "$(prop compressedSize)" ] || fail "archive size differs from the pin"
echo "PASS archive sha256 $actual, $(stat -c %s "$ARCHIVE") bytes (the pin, and Rolling's)"
[ "$(sha256sum "$PKG" | cut -d' ' -f1)" = "$(prop extraRepoKeyringSha256)" ] || fail "keyring package differs from the pin"
[ "$(stat -c %s "$PKG")" = "$(prop extraRepoKeyringSize)" ] || fail "keyring package size differs from the pin"
[ "$(sha256sum "$SIG" | cut -d' ' -f1)" = "$(prop extraRepoKeyringSigSha256)" ] || fail "keyring signature differs from the pin"
[ "$(stat -c %s "$SIG")" = "$(prop extraRepoKeyringSigSize)" ] || fail "keyring signature size differs from the pin"
echo "PASS the upstream keyring package and signature are exactly the pinned artifact"

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

fi
echo "== D-F. the rootfs in an arm64 container"
command -v docker >/dev/null || fail "no docker"
CLASSES=$(find "$REPO/garden-common/build/intermediates/javac" -path '*debug*' -name RootfsManager.class | head -1 | sed 's#/com/thothterm/linux/RootfsManager.class##')
[ -n "$CLASSES" ] || fail "garden-common is not compiled (gradlew :garden-common:compileDebugJavaWithJavac)"
ANDROID_JAR=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/platforms/android-*/android.jar | sort -V | tail -1)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
javac -nowarn -d "$T/cls" -cp "$CLASSES:$ANDROID_JAR" "$HERE/DumpExtraRepoScript.java"
java -cp "$T/cls:$CLASSES:$ANDROID_JAR" com.thothterm.linux.DumpExtraRepoScript \
    "$REPO/garden-security/src/main/assets/garden/distro.properties" > "$LOG/extra-repo-script.sh"
base64 -w0 "$PKG" > "$LOG/pkg.b64"
base64 -w0 "$SIG" > "$LOG/sig.b64"
IMAGE=thothterm-security-validate:$(printf %.12s "$SHA")
docker rmi -f "$IMAGE" > /dev/null 2>&1 || true
docker import --platform linux/arm64 "$ARCHIVE" "$IMAGE" > /dev/null
set +e
docker run --rm --platform linux/arm64 --privileged -v "$LOG:/w" -v "$HERE:/h:ro" \
    -e TRUSTED="$(prop extraRepoTrusted)" -e REVOKED="$(prop extraRepoRevoked)" \
    "$IMAGE" /bin/bash /h/container-checks.sh > "$LOG/container-checks.txt" 2>&1
rc=$?
cat "$LOG/container-checks.txt"
# Offline: the same script with no network reaches the network step and stops, keys proven.
docker run --rm --platform linux/arm64 --privileged --network none -v "$LOG:/w" -v "$HERE:/h:ro" -e MODE=offline \
    -e TRUSTED="$(prop extraRepoTrusted)" -e REVOKED="$(prop extraRepoRevoked)" \
    "$IMAGE" /bin/bash /h/container-checks.sh > "$LOG/container-checks-offline.txt" 2>&1
rc2=$?
cat "$LOG/container-checks-offline.txt"
[ "$rc" -eq 0 ] && [ "$rc2" -eq 0 ] && rc=0 || rc=1
set -e
docker rmi -f "$IMAGE" > /dev/null 2>&1 || true
[ "$rc" -eq 0 ] || fail "container checks (see $LOG/container-checks.txt)"
echo "VALIDATION PASS ($LOG)"
