#!/bin/sh
# Static release checks on built Garden APKs, one report line per check:
#   identity     aapt2: package, versionCode, versionName, min/target SDK
#   16 KB        zipalign -c -P 16 (uncompressed .so on 16 KB boundaries)
#   ELF          every arm64 ELF (jniLibs and the runtime assets) has LOAD
#                segments aligned to >= 0x4000
#   JNI          the 7 methods libtermexec registers (RegisterNatives) exist
#                in the dex with exactly those classes, names and signatures,
#                i.e. R8 kept them
#   rootfs       a full flavour embeds the pinned rootfs, an F-Droid flavour
#                embeds none
#
#   tests/garden-common/release/verify-apks.sh APK...
#
# Needs ANDROID_HOME (build-tools aapt2/zipalign, cmdline-tools apkanalyzer)
# and ANDROID_NDK_HOME (llvm-readelf). Exits 1 on any FAIL.
set -u
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
AAPT2=$BT/aapt2
ZIPALIGN=$BT/zipalign
APKANALYZER=$SDK/cmdline-tools/latest/bin/apkanalyzer
READELF=${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf
fail=0
line() { echo "$1 $2"; [ "$1" = PASS ] || fail=1; }

for apk in "$@"; do
    name=$(basename "$apk")
    badging=$("$AAPT2" dump badging "$apk" 2>/dev/null)
    pkg=$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
    code=$(printf '%s\n' "$badging" | sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p")
    ver=$(printf '%s\n' "$badging" | sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p")
    min=$(printf '%s\n' "$badging" | sed -n "s/^minSdkVersion:'\([^']*\)'/\1/p")
    target=$(printf '%s\n' "$badging" | sed -n "s/^targetSdkVersion:'\([^']*\)'/\1/p")
    echo "== $name: $pkg $ver ($code) minSdk $min targetSdk $target sha256 $(sha256sum "$apk" | cut -d' ' -f1) bytes $(stat -c %s "$apk")"
    [ -n "$pkg" ] && [ -n "$code" ]; line $([ $? = 0 ] && echo PASS || echo FAIL) "$name identity read by aapt2"

    "$ZIPALIGN" -c -P 16 4 "$apk" >/dev/null 2>&1
    line $([ $? = 0 ] && echo PASS || echo FAIL) "$name zipalign -c -P 16 4"

    W=$(mktemp -d)
    unzip -q -o "$apk" 'lib/*' 'assets/runtime/*' -d "$W" 2>/dev/null
    elves=0; bad=0
    for so in $(find "$W" -type f); do
        "$READELF" -h "$so" >/dev/null 2>&1 || continue
        elves=$((elves + 1))
        for a in $("$READELF" -lW "$so" | awk '$1 == "LOAD" {print $NF}'); do
            [ $((a)) -ge $((0x4000)) ] || { bad=$((bad + 1)); echo "   LOAD align $a: ${so#$W/}"; }
        done
    done
    rm -rf "$W"
    [ "$elves" -gt 0 ] && [ "$bad" = 0 ]
    line $([ $? = 0 ] && echo PASS || echo FAIL) "$name ELF LOAD >= 0x4000 ($elves ELF files)"

    dex=$("$APKANALYZER" dex packages --defined-only "$apk" 2>/dev/null)
    jni=0
    for m in 'com.thothterm.TermIO$Native void setUTF8Input(int,boolean)' \
             'com.thothterm.TermIO$Native void setWindowSize(int,int,int,int,int)' \
             'com.thothterm.Process$Native int createSubprocess(int,byte[],byte[][],byte[][],byte[])' \
             'com.thothterm.Process$Native int waitExit(int)' \
             'com.thothterm.Process$Native void finishChilds(int)' \
             'com.thothterm.Process$Native void killChilds(int)' \
             'com.thothterm.Process$Native int renameNoReplace(byte[],byte[])'; do
        if printf '%s\n' "$dex" | grep -F -q "$m"; then jni=$((jni + 1)); else echo "   missing: $m"; fi
    done
    [ "$jni" = 7 ]; line $([ $? = 0 ] && echo PASS || echo FAIL) "$name JNI RegisterNatives targets $jni/7"

    rootfs=$(unzip -l "$apk" | awk '{print $4}' | grep -E '^assets/.*\.(tgz|tar\.gz|tar\.xz)$' || true)
    case "$name" in
        *fdroid*) [ -z "$rootfs" ]; line $([ $? = 0 ] && echo PASS || echo FAIL) "$name F-Droid flavour embeds no rootfs" ;;
        *) [ -n "$rootfs" ]; line $([ $? = 0 ] && echo PASS || echo FAIL) "$name full flavour embeds $(echo $rootfs)" ;;
    esac
done
exit $fail
