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

# The native runtime inside the app APK must be built for the QA id itself
# (QA VARIANT RUNTIME ISOLATION). Fake APKs here are real zips whose native
# files carry the strings build-proot.sh compiles in.
REPO=$(cd "$HERE/../../.." && pwd)
export REPO
native_apk() { # native_apk NAME PROOT_ID [SHMEM_ID] [EXEC_LIB_ID] [DROP]
    python3 - "$WORK/$1.apk" "$2" "${3:-$2}" "${4:-}" "${5:-}" <<'PY'
import sys, zipfile
out, proot_id, shmem_id, exec_id, drop = sys.argv[1:6]
rt = lambda i: "/data/data/%s/files/linux/runtime" % i
files = {
    "lib/arm64-v8a/libproot.so": b"\x7fELF\0" + (rt(proot_id) + "/loader\0").encode()
        + b"It seems that termux-exec is active and is prepending /data/data/com.termux/...\0",
    "lib/arm64-v8a/libproot_loader.so": b"\x7fELF\0loader\0",
    "assets/runtime/arm64-v8a/libandroid-shmem.so": b"\x7fELF\0" + (rt(shmem_id) + "/tmp/ashv_key_%d\0").encode(),
    "assets/runtime/arm64-v8a/libtalloc.so.2": b"\x7fELF\0talloc\0",
    "AndroidManifest.xml": b"binary",
}
if exec_id:
    files["lib/arm64-v8a/libexec-t1plus.so"] = b"\x7fELF\0" + exec_id.encode() + b"\0%s-app_info-%s\0"
files.pop(drop, None)
with zipfile.ZipFile(out, "w") as z:
    for name, data in files.items():
        z.writestr(name, data)
print(out)
PY
}
runtime_result() { # runtime_result EXPECTED APK -> ok|abort
    if (. "$HERE/apk-identity.sh"; gate_check_runtime_ids "$1" "$2") >/dev/null 2>&1; then
        echo ok
    else
        echo abort
    fi
}
expect_runtime() { # expect_runtime RESULT EXPECTED APK WHAT
    got=$(runtime_result "$2" "$3")
    [ "$got" = "$1" ] || fail "runtime: expected $1 for $4, got $got"
}
for base in com.thothterm.arch com.thothterm.debian com.thothterm.ubuntu; do
    qa=$base$Q
    expect_runtime ok "$qa" "$(native_apk qa "$qa")" "$qa with its own runtime"
    # The blocker: a QA app whose runtime points at the production package.
    expect_runtime abort "$qa" "$(native_apk prodrt "$base")" "$qa with the $base runtime"
    expect_runtime abort "$qa" "$(native_apk mixed "$qa" "$base")" "$qa with the $base libandroid-shmem"
    expect_runtime abort "$qa" "$(native_apk otherqa "$base.qa.other")" "$qa with another QA id's runtime"
    expect_runtime abort "$qa" "$(native_apk noproot "$qa" "$qa" "" lib/arm64-v8a/libproot.so)" "$qa without libproot.so"
    expect_runtime abort "$qa" "$(native_apk noshmem "$qa" "$qa" "" assets/runtime/arm64-v8a/libandroid-shmem.so)" \
        "$qa without libandroid-shmem.so"
    # A production build is still accepted for its own id, and only for it.
    expect_runtime ok "$base" "$(native_apk prod "$base")" "$base with its own runtime"
    expect_runtime abort "$base" "$(native_apk qa2 "$qa")" "$base with the $qa runtime"
done
U=com.thothterm.ubuntu$Q
expect_runtime ok "$U" "$(native_apk uexec "$U" "$U" "$U")" "Ubuntu QA libexec-t1plus with its id"
expect_runtime abort "$U" "$(native_apk uexecprod "$U" "$U" com.thothterm.ubuntu)" \
    "Ubuntu QA libexec-t1plus with the production id"
expect_runtime abort "com.thothterm.arch$Q" "$WORK/missing.apk" "a missing APK"
printf 'not a zip' > "$WORK/broken.apk"
expect_runtime abort "com.thothterm.arch$Q" "$WORK/broken.apk" "an unreadable APK"
[ ! -s "$ADB_LOG" ] || fail "adb was called by a runtime check: $(cat "$ADB_LOG")"

# install-qa-app.sh: the complete QA app for lifecycle QA, same guards.
qa_id() { if (. "$HERE/apk-identity.sh"; gate_is_qa_id "$1"); then echo yes; else echo no; fi; }
for id in com.thothterm.arch.qa.lifecycle com.thothterm.ubuntu.qa.x1 com.thothterm.debian.qa.extractorgate; do
    [ "$(qa_id "$id")" = yes ] || fail "$id must be a QA id"
done
for id in com.thothterm.arch com.thothterm com.thothterm.arch.qa. com.thothterm.arch.qa.Up \
        com.thothterm.arch.qa.1x com.example.qa.x com.thothterm.arch.qa.x.qa.y com.pocketclaw.qa.x \
        com.thothterm.arch.qa.x-y ""; do
    [ "$(qa_id "$id")" = no ] || fail "'$id' must not be a QA id"
done
app_identity() { # app_identity EXPECTED APP_PKG -> ok|abort
    a=$(apk app.apk "$2")
    if (. "$HERE/apk-identity.sh"; gate_check_app_identity "$1" "$a") >/dev/null 2>&1; then echo ok; else echo abort; fi
}
L=.qa.lifecycle
for base in com.thothterm.arch com.thothterm.debian com.thothterm.ubuntu; do
    [ "$(app_identity "$base$L" "$base$L")" = ok ] || fail "$base$L must install"
    [ "$(app_identity "$base$L" "$base")" = abort ] || fail "a $base APK must not install as $base$L"
    [ "$(app_identity "$base" "$base")" = abort ] || fail "$base must never be installed"
done
[ "$(app_identity "com.thothterm.arch$L" "com.example.PocketClaw")" = abort ] || fail "PocketClaw must abort"
[ "$(app_identity "com.thothterm.arch$L" "")" = abort ] || fail "an unreadable APK must abort"
I="$REPO/tests/garden-common/qa/install-qa-app.sh"
grep -v '^[[:space:]]*#' "$I" | grep -nE 'uninstall|pm clear|adb[^|]*install' >/dev/null \
    && fail "install-qa-app.sh installs directly, uninstalls or clears"
i_app=$(grep -n '^gate_check_app_identity "\$PKG" "\$APK"' "$I" | cut -d: -f1)
i_rt=$(grep -n '^gate_check_runtime_ids "\$PKG" "\$APK"' "$I" | cut -d: -f1)
i_inst=$(grep -n '^gate_install ' "$I" | cut -d: -f1)
[ -n "$i_app" ] && [ -n "$i_rt" ] && [ -n "$i_inst" ] && [ "$i_app" -lt "$i_inst" ] && [ "$i_rt" -lt "$i_inst" ] \
    || fail "install-qa-app.sh must check identity and runtime before installing"
mkdir -p "$WORK/repo/tests/garden-common/qa" "$WORK/repo/tests/garden-common/extractor"
cp "$I" "$WORK/repo/tests/garden-common/qa/"
cp "$HERE/apk-identity.sh" "$WORK/repo/tests/garden-common/extractor/"
printf '#!/bin/sh\necho "gradlew $*" >> "$ADB_LOG"\nexit 1\n' > "$WORK/repo/gradlew"
chmod +x "$WORK/repo/gradlew"
: > "$ADB_LOG"
for bad in "" .qa .qa.Up -qa.x .devel .qa.x/y; do
    out=$(sh "$WORK/repo/tests/garden-common/qa/install-qa-app.sh" garden-arch "$bad" 2>&1) \
        && fail "install-qa-app.sh accepted suffix '$bad'"
    case "$out" in *ABORT*|*usage*) ;; *) fail "suffix '$bad' did not abort: $out" ;; esac
done
[ ! -s "$ADB_LOG" ] || fail "a refused install-qa-app.sh went on: $(cat "$ADB_LOG")"

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
runtime_line=$(grep -n '^gate_check_runtime_ids "\$PKG" "\$APK"' "$G" | head -1 | cut -d: -f1)
runtime_line=${runtime_line:-999999}
[ "$runtime_line" -lt "$install_line" ] && [ "$runtime_line" -lt "$adb_line" ] \
    || fail "device-gate.sh must check the APK's native runtime before installing"
grep -q 'thothtermQaApplicationIdSuffix="\$GATE_QA_SUFFIX"' "$G" \
    || fail "device-gate.sh must build with the QA application id suffix"
# One application id per module, from applicationId.gradle, for every variant
# and for the PRoot runtime: no build type or flavour may change it.
grep -q "rootProject.findProperty('thothtermQaApplicationIdSuffix')" "$REPO/applicationId.gradle" \
    || fail "applicationId.gradle does not read thothtermQaApplicationIdSuffix"
grep -q "apply from: 'applicationId.gradle'" "$REPO/build.gradle" \
    || fail "the root build does not apply applicationId.gradle"
for m in garden-arch:com.thothterm.arch garden-debian:com.thothterm.debian term-ubuntu:com.thothterm.ubuntu; do
    f="$REPO/${m%%:*}/build.gradle"
    grep -q "thothtermApplicationId('${m#*:}')" "$f" \
        || fail "${m%%:*}/build.gradle does not take its id from thothtermApplicationId"
    if grep -v '^[[:space:]]*//' "$f" | grep -qE '^[[:space:]]*applicationId[[:space:]]+"|^[[:space:]]*applicationIdSuffix'; then
        fail "${m%%:*}/build.gradle sets a literal id or a variant suffix"
    fi
    grep -q "environment 'THOTHTERM_APPLICATION_ID', android.defaultConfig.applicationId" "$f" \
        || fail "${m%%:*}/build.gradle does not build PRoot for its application id"
    grep -q "inputs.property 'applicationId', android.defaultConfig.applicationId" "$f" \
        || fail "${m%%:*}/build.gradle does not rebuild PRoot when the id changes"
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
