#!/bin/bash
# JVM unit tests without the Android SDK, for cloud sessions whose network
# policy blocks dl.google.com (so neither the SDK nor the Android Gradle
# Plugin can be fetched). Locally, use Gradle; this is a stand-in, not a
# replacement, and reports only what it could run.
#
#   tools/garden/cloud-hosttest/run.sh [MODULE...]
#       (default: emulatorview garden-common garden-debian garden-arch)
#
# How it stands in for `gradlew testDebugUnitTest`:
# - android.jar: AGP's "mockable" jar is made from the SDK's android.jar; here
#   MockGen.java makes the same from Robolectric's android-all 16 (API 36,
#   Maven Central, pinned by SHA-256): every framework method throws "not
#   mocked" (or returns a default value where the module sets
#   unitTests.returnDefaultValues), no static initialiser runs, constants stay.
# - javac -sourcepath compiles only the main classes the tests reach; R is
#   generated from res/ (gen-r.py) and the few AndroidX members they reach are
#   stubs/ (throwing), since Google's Maven is unreachable too.
# - garden-arch and garden-debian also run garden-common/src/editionTest.
# - Tests run as an unprivileged user (a root process can write a read-only
#   directory) in a UTF-8 locale, from the module directory, as Gradle does.
#
# Not run: TermServiceTest (it reaches the whole AppCompat UI). Expected
# failures: PageAlignmentTest needs the NDK-built PRoot runtime; and
# ArchEditionTest.provenanceRecordsThePinnedArchive until ROOTFS_PROVENANCE.md
# exists.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$(cd "$HERE/../../.." && pwd)"
C="${HOSTTEST_CACHE:-/var/tmp/thothterm-hosttest}"
mkdir -p "$C/jars"
MC=https://repo1.maven.org/maven2

fetch() { # fetch PATH SHA256
    f="$C/jars/${1##*/}"
    [ -f "$f" ] && [ "$(sha256sum "$f" | cut -d' ' -f1)" = "$2" ] && return 0
    for wait in 2 4 8 16 0; do      # Maven Central rate-limits (429)
        curl -fsSL -o "$f.part" "$MC/$1" && break
        [ "$wait" = 0 ] && { echo "cannot download $1" >&2; exit 2; }
        sleep "$wait"
    done
    [ "$(sha256sum "$f.part" | cut -d' ' -f1)" = "$2" ] || { echo "$1 does not match its pin" >&2; exit 2; }
    mv "$f.part" "$f"
}
fetch org/robolectric/android-all/16-robolectric-13921718/android-all-16-robolectric-13921718.jar \
    8b74a0a137330658d2f33f0dc715d42734f74ba8b2d7014fc2e95aa40d3f682d
fetch junit/junit/4.13.2/junit-4.13.2.jar 8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3
fetch org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar 66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9
fetch org/ow2/asm/asm/9.8/asm-9.8.jar 876eab6a83daecad5ca67eb9fcabb063c97b5aeb8cf1fca7a989ecde17522051
J="$C/jars"

if [ ! -f "$C/mockable-android-36.jar" ] || [ ! -f "$C/mockable-android-36-default-values.jar" ]; then
    mkdir -p "$C/mockgen"
    javac -nowarn -d "$C/mockgen" -cp "$J/asm-9.8.jar" "$HERE/MockGen.java" || exit 2
    java -cp "$J/asm-9.8.jar:$C/mockgen" MockGen "$J/android-all-16-robolectric-13921718.jar" "$C/mockable-android-36.jar" throw || exit 2
    java -cp "$J/asm-9.8.jar:$C/mockgen" MockGen "$J/android-all-16-robolectric-13921718.jar" "$C/mockable-android-36-default-values.jar" defaults || exit 2
fi

STUBS="$C/stubs"
rm -rf "$STUBS"; mkdir -p "$STUBS"
cp -r "$HERE/stubs/." "$STUBS/"
python3 "$HERE/gen-r.py" com.thothterm "$STUBS/com/thothterm/R.java" \
    "$R/garden-common/src/main/res" "$R/emulatorview/src/main/res" || exit 2

RUNAS=()
if [ "$(id -u)" = 0 ]; then
    id hosttest >/dev/null 2>&1 || useradd -M -d /nonexistent -s /usr/sbin/nologin hosttest
    RUNAS=(runuser -u hosttest --)
fi

status=0
modules=("$@")
[ ${#modules[@]} -gt 0 ] || modules=(emulatorview garden-common garden-debian garden-arch)
for m in "${modules[@]}"; do
    out="$C/out/$m"; rm -rf "$out"; mkdir -p "$out"
    extra=()
    case $m in garden-arch|garden-debian) extra=("$R/garden-common/src/editionTest/java") ;; esac
    tests=$(find "$R/$m/src/test/java" "${extra[@]}" -name '*.java' | grep -v '/TermServiceTest.java$')
    aj="$C/mockable-android-36.jar"
    grep -q "returnDefaultValues = true" "$R/$m/build.gradle" && aj="$C/mockable-android-36-default-values.jar"
    cp="$aj:$J/junit-4.13.2.jar:$J/hamcrest-core-1.3.jar"
    sp="$R/$m/src/main/java:$R/garden-common/src/main/java:$R/emulatorview/src/main/java:$R/libtermexec/src/main/java:$STUBS:$R/$m/src/test/java"
    for e in "${extra[@]}"; do sp="$sp:$e"; done
    # shellcheck disable=SC2086  # one file per word
    if ! javac -nowarn -encoding UTF-8 -source 17 -target 17 -proc:none -d "$out" -cp "$cp" -sourcepath "$sp" $tests 2>&1 | grep -v '^Note:'; then :; fi
    classes=$(for f in $tests; do n=${f##*/java/}; n=${n%.java}; [ -f "$out/$n.class" ] && grep -q '@Test' "$f" && echo "${n//\//.}"; done)
    res=""; [ -d "$R/$m/src/test/resources" ] && res=":$R/$m/src/test/resources"
    chmod -R a+rX "$C"
    echo "=== $m"
    # shellcheck disable=SC2086
    (cd "$R/$m" && "${RUNAS[@]}" env LC_ALL=C.UTF-8 java -cp "$out$res:$cp" org.junit.runner.JUnitCore $classes) \
        > "$C/$m.log" 2>&1 || status=1
    grep -E '^(OK|Tests run)' "$C/$m.log"
    grep -E '^[0-9]+\) ' "$C/$m.log"
done
echo "logs: $C/*.log"
exit $status
