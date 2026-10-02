#!/bin/sh
# Self-test of the device gate's package identity guard, without a device or
# the SDK: a stub aapt2 reports the packages written into fake APK files and a
# stub adb records every call. Run by DeviceGateIdentityTest; standalone:
#
#   tests/garden-common/extractor/device-gate-selftest.sh
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
FAILS=0
fail() {
    echo "FAIL: $*"
    FAILS=$((FAILS + 1))
}

mkdir -p "$WORK/bin"
# Fake APK: line 1 "app=<package>", line 2 "target=<instrumented package>".
cat > "$WORK/bin/aapt2" <<'EOF'
#!/bin/sh
for f; do :; done
app=$(sed -n 's/^app=//p' "$f")
target=$(sed -n 's/^target=//p' "$f")
case "$1 $2" in
    "dump badging")
        [ -n "$app" ] || exit 1
        echo "package: name='$app' versionCode='100' versionName='0.1.0'"
        echo "application-label:'ThothTerm'" ;;
    "dump xmltree")
        echo "N: android=http://schemas.android.com/apk/res/android (line=2)"
        echo "  E: manifest (line=2)"
        echo "    A: package=\"$app\" (Raw: \"$app\")"
        if [ -n "$target" ]; then
            echo "    E: instrumentation (line=9)"
            echo "      A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"androidx.test.runner.AndroidJUnitRunner\""
            echo "      A: http://schemas.android.com/apk/res/android:targetPackage(0x01010021)=\"$target\" (Raw: \"$target\")"
        fi ;;
    *) exit 2 ;;
esac
EOF
cat > "$WORK/bin/adb" <<'EOF'
#!/bin/sh
echo "adb $*" >> "$ADB_LOG"
EOF
chmod +x "$WORK/bin/aapt2" "$WORK/bin/adb"
export AAPT2="$WORK/bin/aapt2" ADB_LOG="$WORK/adb.log"
PATH="$WORK/bin:$PATH"
: > "$ADB_LOG"

apk() { # apk NAME APP [TARGET]
    printf 'app=%s\ntarget=%s\n' "$2" "${3:-}" > "$WORK/$1"
    echo "$WORK/$1"
}

# check EXPECTED APP_PKG TEST_PKG TARGET -> "ok" or "abort"
check() {
    a=$(apk app.apk "$2")
    t=$(apk test.apk "$3" "$4")
    if (. "$HERE/apk-identity.sh"; gate_check_identity "$1" "$a" "$t") >/dev/null 2>&1; then
        echo ok
    else
        echo abort
    fi
}

expect() { # expect RESULT EXPECTED APP TEST TARGET
    got=$(check "$2" "$3" "$4" "$5")
    [ "$got" = "$1" ] || fail "expected $1 for expected=$2 app=$3 test=$4 target=$5, got $got"
}

Q=.qa.extractorgate
for base in com.thothterm.arch com.thothterm.debian com.thothterm.ubuntu; do
    expect ok "$base$Q" "$base$Q" "$base$Q.test" "$base$Q"
    # The bug Codex found: the APK is still the primary package.
    expect abort "$base$Q" "$base" "$base.test" "$base"
    expect abort "$base$Q" "$base$Q" "$base$Q.test" "$base"
    expect abort "$base$Q" "$base$Q" "$base.test" "$base$Q"
    # Asking for a protected package directly.
    expect abort "$base" "$base" "$base.test" "$base"
done
for p in com.thothterm com.thothterm.devel com.thothterm.ubuntu com.thothterm.debian com.thothterm.arch \
        com.pocketclaw com.example.PocketClaw io.pocket_claw.app; do
    expect abort "$p" "$p" "$p.test" "$p"
    expect abort "com.thothterm.arch$Q" "$p" "com.thothterm.arch$Q.test" "com.thothterm.arch$Q"
    expect abort "com.thothterm.arch$Q" "com.thothterm.arch$Q" "com.thothterm.arch$Q.test" "$p"
done
expect abort "com.thothterm.arch.pocketclaw$Q" "com.thothterm.arch.pocketclaw$Q" \
    "com.thothterm.arch.pocketclaw$Q.test" "com.thothterm.arch.pocketclaw$Q"
# Identity that cannot be proven fails closed.
expect abort "com.thothterm.arch$Q" "" "com.thothterm.arch$Q.test" "com.thothterm.arch$Q"
expect abort "com.thothterm.arch$Q" "com.thothterm.arch$Q" "com.thothterm.arch$Q.test" ""
expect abort "com.other.app" "com.other.app" "com.other.app.test" "com.other.app"
if (unset AAPT2; ANDROID_HOME=$WORK/no-sdk; . "$HERE/apk-identity.sh"; \
        gate_apk_package "$(apk app.apk com.thothterm.arch$Q)") >/dev/null 2>&1; then
    fail "no aapt2 must abort"
fi
if (. "$HERE/apk-identity.sh"; gate_apk_package "$WORK/missing.apk") >/dev/null 2>&1; then
    fail "a missing APK must abort"
fi
[ ! -s "$ADB_LOG" ] || fail "adb was called by an identity check: $(cat "$ADB_LOG")"

# Installs are always --no-incremental.
(. "$HERE/apk-identity.sh"; gate_install "$WORK/x.apk" -r -t)
grep -qx "adb install --no-incremental -r -t $WORK/x.apk" "$ADB_LOG" \
    || fail "gate_install did not use --no-incremental: $(cat "$ADB_LOG")"

# The gate script: every install goes through gate_install, after the identity
# check; nothing uninstalls or clears; the old overrides are refused.
G="$HERE/device-gate.sh"
if grep -v '^[[:space:]]*#' "$G" | grep -n 'adb[^|]*install' >/dev/null; then
    fail "device-gate.sh calls adb install directly"
fi
if grep -v '^[[:space:]]*#' "$G" | grep -nE 'uninstall|pm clear|pm uninstall|--user|rm -rf /data' >/dev/null; then
    fail "device-gate.sh uninstalls or clears something"
fi
check_line=$(grep -n '^gate_check_identity ' "$G" | head -1 | cut -d: -f1)
install_line=$(grep -n '^gate_install ' "$G" | head -1 | cut -d: -f1)
adb_line=$(grep -n '^[^#]*adb \(push\|shell\|install\)' "$G" | head -1 | cut -d: -f1)
check_line=${check_line:-999999}
[ -n "$install_line" ] && [ "$check_line" -lt "$install_line" ] \
    || fail "device-gate.sh must check identity before installing"
[ -n "$adb_line" ] && [ "$check_line" -lt "$adb_line" ] \
    || fail "device-gate.sh must check identity before touching the device"
grep -q 'thothtermQaApplicationIdSuffix="\$GATE_QA_SUFFIX"' "$G" \
    || fail "device-gate.sh must build with the QA application id suffix"
for m in garden-arch garden-debian term-ubuntu; do
    grep -q "applicationIdSuffix = qaSuffix" "$HERE/../../../$m/build.gradle" \
        || fail "$m/build.gradle does not apply thothtermQaApplicationIdSuffix"
done
# The old overrides abort before the build or the device. Run on a copy whose
# gradlew only records that it ran, never the real build.
SANDBOX=$WORK/repo/tests/garden-common/extractor
mkdir -p "$SANDBOX"
cp "$G" "$HERE/apk-identity.sh" "$SANDBOX/"
printf '#!/bin/sh\necho "gradlew $*" >> "$ADB_LOG"\nexit 1\n' > "$WORK/repo/gradlew"
chmod +x "$WORK/repo/gradlew"
: > "$ADB_LOG"
for v in GATE_PACKAGE GATE_ALLOW_REPLACE; do
    out=$(env "$v=com.thothterm.arch" sh "$SANDBOX/device-gate.sh" garden-arch 2>&1) && fail "$v was accepted"
    case "$out" in *ABORT*) ;; *) fail "$v did not abort: $out" ;; esac
done
[ ! -s "$ADB_LOG" ] || fail "the refused runs went on: $(cat "$ADB_LOG")"

if [ "$FAILS" -ne 0 ]; then
    echo "device gate self-test: $FAILS failure(s)"
    exit 1
fi
echo "device gate self-test: OK"
