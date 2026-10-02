#!/bin/sh
# Device extractor gate: the APK's own extractor (RootfsArchive +
# TarballExtractor + AndroidFileOps) on a real Android device, against every
# adversarial case and, given an archive, the real pinned rootfs.
#
#   tests/garden-common/extractor/device-gate.sh MODULE [ARCHIVE.tar.gz SHA256]
#
# MODULE is garden-arch, garden-debian or term-ubuntu. Builds the module's full
# debug APK and its androidTest APK, so it needs the Android SDK/NDK and one
# device on adb, and installs both with "adb install --no-incremental" (an
# incremental install is how a primary app's data was lost before). It
# extracts into files/extractor-gate/ of the app under test and never touches
# its Linux environment.
#
# The app under test is the module's application id. If that package is
# already installed -- for example the phone's primary ThothTerm -- the script
# stops, unless GATE_ALLOW_REPLACE=1 says replacing it is intended. Prefer a
# build with an isolated application id and set GATE_PACKAGE to it.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
MODULE=$1; shift
case "$MODULE" in
    garden-arch) PKG=com.thothterm.arch ;;
    garden-debian) PKG=com.thothterm.debian ;;
    term-ubuntu) PKG=com.thothterm.ubuntu ;;
    *) echo "unknown module $MODULE"; exit 2 ;;
esac
PKG=${GATE_PACKAGE:-$PKG}
cd "$REPO"
if adb shell pm path "$PKG" 2>/dev/null | grep -q '^package:' && [ "${GATE_ALLOW_REPLACE:-0}" != 1 ]; then
    echo "STOP: $PKG is installed on this device. Use an isolated application id"
    echo "(GATE_PACKAGE=...) or set GATE_ALLOW_REPLACE=1 if replacing it is intended."
    exit 3
fi
./gradlew ":$MODULE:assembleFullDebug" ":$MODULE:assembleFullDebugAndroidTest"
APK=$(ls "$MODULE"/build/outputs/apk/full/debug/*.apk | head -1)
TEST_APK=$(ls "$MODULE"/build/outputs/apk/androidTest/full/debug/*.apk | head -1)
adb install --no-incremental -r "$APK"
adb install --no-incremental -r -t "$TEST_APK"
EXTRA=""
if [ $# -ge 2 ]; then
    actual=$(sha256sum "$1" | cut -d' ' -f1)
    [ "$actual" = "$2" ] || { echo "FAIL archive sha256 $actual, expected $2"; exit 1; }
    TMP=$(mktemp -d)
    LANG=C.UTF-8 python3 "$HERE/manifest.py" "$1" > "$TMP/expected.manifest"
    adb push "$1" /data/local/tmp/gate-rootfs.tgz >/dev/null
    adb push "$TMP/expected.manifest" /data/local/tmp/gate.manifest >/dev/null
    adb shell "run-as $PKG sh -c 'mkdir -p files/extractor-gate && cp /data/local/tmp/gate-rootfs.tgz files/extractor-gate/rootfs.tgz && cp /data/local/tmp/gate.manifest files/extractor-gate/expected.manifest'"
    adb shell rm -f /data/local/tmp/gate-rootfs.tgz /data/local/tmp/gate.manifest
    EXTRA="-e gateArchive extractor-gate/rootfs.tgz -e gateSha256 $2 -e gateManifest extractor-gate/expected.manifest"
fi
# shellcheck disable=SC2086
adb shell am instrument -w $EXTRA -e class com.thothterm.linux.ExtractorDeviceGateTest \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee /tmp/extractor-device-gate.txt
adb shell "run-as $PKG sh -c 'cat files/extractor-gate/cases.txt files/extractor-gate/real.txt 2>/dev/null; rm -f files/extractor-gate/rootfs.tgz'"
grep -q '^OK (' /tmp/extractor-device-gate.txt
