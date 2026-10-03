#!/bin/sh
# Build and install a complete, isolated QA build of an edition, for physical
# lifecycle QA (terminal, PRoot, HOME, repair, reinstall, pacman...) that must
# never touch a production app.
#
#   tests/garden-common/qa/install-qa-app.sh MODULE .qa.<name> [full|fdroid]
#
# MODULE is garden-arch, garden-debian, garden-blackarch or term-ubuntu. The app is
# <production id>.qa.<name> (applicationId.gradle): every variant, its PRoot
# runtime and its native code are built for that id, so it runs in its own
# /data/data/<QA id>/ and cannot reach a production app's storage.
#
# Before adb installs anything, the APK file itself is checked
# (../extractor/apk-identity.sh): aapt2 must read exactly the QA id, never a
# protected package or PocketClaw, and the native runtime inside must be built
# for that id only. The install is always --no-incremental. Nothing is ever
# uninstalled or cleared; replacing an earlier build of the same QA id is the
# only "-r". Needs ANDROID_HOME (aapt2), unzip, and one device (or
# ANDROID_SERIAL).
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
. "$REPO/tests/garden-common/extractor/apk-identity.sh"
[ $# -ge 2 ] || { echo "usage: install-qa-app.sh MODULE .qa.<name> [full|fdroid]"; exit 2; }
MODULE=$1
SUFFIX=$2
FLAVOUR=${3:-full}
case "$MODULE" in
    garden-arch) BASE=com.thothterm.arch ;;
    garden-debian) BASE=com.thothterm.debian ;;
    garden-blackarch) BASE=com.thothterm.blackarch ;;
    term-ubuntu) BASE=com.thothterm.ubuntu ;;
    *) gate_die "unknown module $MODULE" ;;
esac
case "$FLAVOUR" in
    full) VARIANT=FullDebug ;;
    fdroid) VARIANT=FdroidDebug ;;
    *) gate_die "unknown flavour $FLAVOUR" ;;
esac
PKG=$BASE$SUFFIX
gate_is_qa_id "$PKG" || gate_die "$SUFFIX is not a QA suffix (.qa.<lower-case letters and digits>)"
gate_is_protected "$PKG" && gate_die "$PKG is protected"
cd "$REPO"

if [ -z "${ANDROID_SERIAL:-}" ]; then
    devices=$(adb devices | sed -n 's/^\([^[:space:]]*\)[[:space:]]*device$/\1/p' | wc -l)
    [ "$devices" -eq 1 ] || gate_die "$devices devices on adb; set ANDROID_SERIAL"
fi

APK_DIR=$MODULE/build/outputs/apk/$FLAVOUR/debug
rm -f "$APK_DIR"/*.apk
./gradlew -PthothtermQaApplicationIdSuffix="$SUFFIX" ":$MODULE:assemble$VARIANT"
[ "$(ls "$APK_DIR"/*.apk | wc -l)" -eq 1 ] || gate_die "expected one APK in $APK_DIR"
APK=$(ls "$APK_DIR"/*.apk)

gate_check_app_identity "$PKG" "$APK"
gate_check_runtime_ids "$PKG" "$APK"

gate_install "$APK" -r
echo "installed $PKG (data: /data/data/$PKG; nothing else was touched)"
echo "launch: adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1"
