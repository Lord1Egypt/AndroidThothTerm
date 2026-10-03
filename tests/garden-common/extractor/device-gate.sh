#!/bin/sh
# Device extractor gate: the APK's own extractor (RootfsArchive +
# TarballExtractor + AndroidFileOps) on a real Android device, against every
# adversarial case and, given an archive, the real pinned rootfs.
#
#   tests/garden-common/extractor/device-gate.sh MODULE [ARCHIVE.tar.gz SHA256]
#
# MODULE is garden-arch, garden-debian or term-ubuntu. Needs the Android
# SDK/NDK (ANDROID_HOME, for aapt2 too) and exactly one device on adb (or
# ANDROID_SERIAL).
#
# The app under test is never a ThothTerm a person uses. The module's full
# debug APK and its androidTest APK are built with
# -PthothtermQaApplicationIdSuffix=.qa.extractorgate, so the app is, for
# example, com.thothterm.arch.qa.extractorgate. Before adb installs anything,
# the package of each APK file is read back with aapt2 and the gate aborts
# unless it is exactly that isolated id (apk-identity.sh): never one of the
# protected packages, never PocketClaw, never an id it cannot prove. Installs
# are always --no-incremental. The gate never uninstalls or clears any app.
#
# The QA suffix sets the module's one application id (applicationId.gradle), so
# the QA app's PRoot runtime and native code are built for the QA id too and
# the QA app is a complete Garden app in its own storage; the gate checks that
# from the APK (gate_check_runtime_ids). The gate itself only extracts, into
# files/extractor-gate/ of the QA app.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
. "$HERE/apk-identity.sh"
MODULE=$1; shift
case "$MODULE" in
    garden-arch) BASE=com.thothterm.arch ;;
    garden-debian) BASE=com.thothterm.debian ;;
    term-ubuntu) BASE=com.thothterm.ubuntu ;;
    *) echo "unknown module $MODULE"; exit 2 ;;
esac
[ -z "${GATE_PACKAGE:-}" ] || gate_die "GATE_PACKAGE is gone: the package is the APK's own, read back with aapt2"
[ -z "${GATE_ALLOW_REPLACE:-}" ] || gate_die "GATE_ALLOW_REPLACE is gone: the gate never replaces a protected app"
PKG=$BASE$GATE_QA_SUFFIX
gate_is_protected "$PKG" && gate_die "$PKG is protected"
cd "$REPO"

if [ -z "${ANDROID_SERIAL:-}" ]; then
    devices=$(adb devices | sed -n 's/^\([^[:space:]]*\)[[:space:]]*device$/\1/p' | wc -l)
    [ "$devices" -eq 1 ] || gate_die "$devices devices on adb; set ANDROID_SERIAL"
fi

APK_DIR=$MODULE/build/outputs/apk/full/debug
TEST_DIR=$MODULE/build/outputs/apk/androidTest/full/debug
rm -f "$APK_DIR"/*.apk "$TEST_DIR"/*.apk
./gradlew -PthothtermQaApplicationIdSuffix="$GATE_QA_SUFFIX" \
    ":$MODULE:assembleFullDebug" ":$MODULE:assembleFullDebugAndroidTest"
[ "$(ls "$APK_DIR"/*.apk | wc -l)" -eq 1 ] || gate_die "expected one APK in $APK_DIR"
[ "$(ls "$TEST_DIR"/*.apk | wc -l)" -eq 1 ] || gate_die "expected one APK in $TEST_DIR"
APK=$(ls "$APK_DIR"/*.apk)
TEST_APK=$(ls "$TEST_DIR"/*.apk)

# The proof: the packages inside the APK files, and the native runtime built
# for exactly that package, before adb touches the device.
gate_check_identity "$PKG" "$APK" "$TEST_APK"
gate_check_runtime_ids "$PKG" "$APK"

# Replacing an earlier build of the QA app itself is fine; it holds nothing.
gate_install "$APK" -r
gate_install "$TEST_APK" -r -t

OUT=$(mktemp -d)
EXTRA=""
if [ $# -ge 2 ]; then
    actual=$(sha256sum "$1" | cut -d' ' -f1)
    [ "$actual" = "$2" ] || { echo "FAIL archive sha256 $actual, expected $2"; exit 1; }
    LANG=C.UTF-8 python3 "$HERE/manifest.py" "$1" > "$OUT/expected.manifest"
    adb push "$1" /data/local/tmp/gate-rootfs.tgz >/dev/null
    adb push "$OUT/expected.manifest" /data/local/tmp/gate.manifest >/dev/null
    adb shell "run-as $PKG sh -c 'mkdir -p files/extractor-gate && cp /data/local/tmp/gate-rootfs.tgz files/extractor-gate/rootfs.tgz && cp /data/local/tmp/gate.manifest files/extractor-gate/expected.manifest'"
    adb shell rm -f /data/local/tmp/gate-rootfs.tgz /data/local/tmp/gate.manifest
    EXTRA="-e gateArchive extractor-gate/rootfs.tgz -e gateSha256 $2 -e gateManifest extractor-gate/expected.manifest"
fi
# shellcheck disable=SC2086
adb shell am instrument -w $EXTRA \
    -e class com.thothterm.linux.ExtractorDeviceGateTest,com.thothterm.linux.AndroidFileOpsSetuidTest \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/extractor-device-gate.txt"
adb shell "run-as $PKG sh -c 'cat files/extractor-gate/cases.txt files/extractor-gate/real.txt 2>/dev/null; rm -f files/extractor-gate/rootfs.tgz'"
echo "results: $OUT/extractor-device-gate.txt"
grep -q '^OK (' "$OUT/extractor-device-gate.txt"
