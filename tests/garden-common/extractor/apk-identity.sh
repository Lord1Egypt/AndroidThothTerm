# Package identity guard for the device extractor gate. Sourced by
# device-gate.sh and by device-gate-selftest.sh; POSIX sh.
#
# The gate installs only an APK whose package, read from the APK file itself
# with aapt2, is the isolated QA id it built. Anything else -- a primary
# ThothTerm, PocketClaw, a package that cannot be read -- stops it before adb
# is touched.

# The application id suffix the gate builds with
# (-PthothtermQaApplicationIdSuffix, see the modules' build.gradle).
GATE_QA_SUFFIX=.qa.extractorgate

# Installed apps the gate must never install over, uninstall or clear.
GATE_PROTECTED_PACKAGES="com.thothterm com.thothterm.devel com.thothterm.ubuntu com.thothterm.debian com.thothterm.arch"

gate_die() {
    echo "ABORT: $*" >&2
    exit 4
}

# gate_is_protected PACKAGE: true for a protected package or anything that
# looks like PocketClaw, in any case.
gate_is_protected() {
    for p in $GATE_PROTECTED_PACKAGES; do
        [ "$1" = "$p" ] && return 0
    done
    case $(printf '%s' "$1" | tr '[:upper:]' '[:lower:]') in
        *pocketclaw*|*pocket-claw*|*pocket_claw*) return 0 ;;
    esac
    return 1
}

# gate_aapt2: the aapt2 to read APKs with, or abort. AAPT2 wins; otherwise the
# SDK's build-tools (the version the build pins, else the newest).
gate_aapt2() {
    if [ -n "${AAPT2:-}" ]; then
        [ -x "$AAPT2" ] || gate_die "AAPT2=$AAPT2 is not executable"
        echo "$AAPT2"
        return
    fi
    sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
    [ -n "$sdk" ] || gate_die "cannot read the APK's package: set ANDROID_HOME or AAPT2"
    found=$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)
    [ -n "$found" ] && [ -x "$found" ] || gate_die "no aapt2 under $sdk/build-tools"
    echo "$found"
}

# gate_apk_package APK: the package attribute of the APK's manifest.
gate_apk_package() {
    [ -f "$1" ] || gate_die "no APK at $1"
    tool=$(gate_aapt2) || exit 4
    out=$("$tool" dump badging "$1" 2>/dev/null) || gate_die "aapt2 cannot read $1"
    pkg=$(printf '%s\n' "$out" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)
    [ -n "$pkg" ] || gate_die "no package name in $1"
    echo "$pkg"
}

# gate_apk_instrumentation_target APK: the targetPackage of the test APK's
# <instrumentation>, the app `am instrument` runs against.
gate_apk_instrumentation_target() {
    [ -f "$1" ] || gate_die "no APK at $1"
    tool=$(gate_aapt2) || exit 4
    out=$("$tool" dump xmltree --file AndroidManifest.xml "$1" 2>/dev/null) \
        || gate_die "aapt2 cannot read the manifest of $1"
    targets=$(printf '%s\n' "$out" | sed -n 's/.*targetPackage[^=]*="\([^"]*\)".*/\1/p' | sort -u)
    [ -n "$targets" ] || gate_die "no instrumentation targetPackage in $1"
    [ "$(printf '%s\n' "$targets" | wc -l)" -eq 1 ] || gate_die "several instrumentation targets in $1"
    echo "$targets"
}

# gate_check_identity EXPECTED_PACKAGE APK TEST_APK: abort unless the APK is
# exactly EXPECTED_PACKAGE, an isolated QA id, and the test APK is
# EXPECTED_PACKAGE.test instrumenting EXPECTED_PACKAGE.
gate_check_identity() {
    expected=$1
    case "$expected" in
        *"$GATE_QA_SUFFIX") ;;
        *) gate_die "expected package $expected is not an isolated QA id (*$GATE_QA_SUFFIX)" ;;
    esac
    gate_is_protected "$expected" && gate_die "$expected is a protected package"
    app=$(gate_apk_package "$2") || exit 4
    test=$(gate_apk_package "$3") || exit 4
    target=$(gate_apk_instrumentation_target "$3") || exit 4
    for p in "$app" "$test" "$target"; do
        gate_is_protected "$p" && gate_die "the APKs name the protected package $p"
    done
    [ "$app" = "$expected" ] || gate_die "the app APK is $app, not $expected"
    [ "$test" = "$expected.test" ] || gate_die "the test APK is $test, not $expected.test"
    [ "$target" = "$expected" ] || gate_die "the test APK instruments $target, not $expected"
    echo "identity: app=$app test=$test instruments=$target"
}

# gate_is_qa_id PACKAGE: true for <protected production id>.qa.<name>, the only
# shape applicationId.gradle produces for a QA build.
gate_is_qa_id() {
    base=${1%.qa.*}
    name=${1##*.qa.}
    [ "$base" != "$1" ] || return 1
    case "$name" in ''|*[!a-z0-9]*) return 1 ;; esac
    case "$name" in [a-z]*) ;; *) return 1 ;; esac
    [ "$base.qa.$name" = "$1" ] || return 1
    for p in $GATE_PROTECTED_PACKAGES; do
        [ "$base" = "$p" ] && return 0
    done
    return 1
}

# gate_check_app_identity EXPECTED_PACKAGE APK: abort unless EXPECTED_PACKAGE is
# a QA id (gate_is_qa_id), not protected, and the APK's own package is exactly
# it. For installing the QA app by itself (install-qa-app.sh).
gate_check_app_identity() {
    expected=$1
    gate_is_qa_id "$expected" || gate_die "$expected is not an isolated QA id (<production id>.qa.<name>)"
    gate_is_protected "$expected" && gate_die "$expected is a protected package"
    app=$(gate_apk_package "$2") || exit 4
    gate_is_protected "$app" && gate_die "the APK is the protected package $app"
    [ "$app" = "$expected" ] || gate_die "the APK is $app, not $expected"
    echo "identity: app=$app"
}

# gate_check_runtime_ids EXPECTED_PACKAGE APK: abort unless the native runtime
# inside the app APK was built for EXPECTED_PACKAGE and for no production
# package: PRoot's loader path and libandroid-shmem's tmp path are
# /data/data/EXPECTED_PACKAGE/files/linux/runtime/..., and no shipped native
# file names another protected id (garden-common/tools/check-runtime-ids.sh).
# A QA app built with a production runtime would run against
# /data/data/com.thothterm.<edition>; this refuses it before it is installed.
gate_check_runtime_ids() {
    expected=$1
    apk=$2
    check=${GATE_RUNTIME_CHECK:-${REPO:?}/garden-common/tools/check-runtime-ids.sh}
    [ -f "$check" ] || gate_die "no runtime id checker at $check"
    command -v unzip >/dev/null 2>&1 || gate_die "unzip is needed to read the APK's native runtime"
    [ -f "$apk" ] || gate_die "no APK at $apk"
    dir=$(mktemp -d)
    if ! unzip -q -o "$apk" 'lib/arm64-v8a/*' 'assets/runtime/arm64-v8a/*' -d "$dir" >/dev/null 2>&1; then
        rm -rf "$dir"
        gate_die "cannot read the native runtime of $apk"
    fi
    runtime="/data/data/$expected/files/linux/runtime"
    proot="$dir/lib/arm64-v8a/libproot.so"
    shmem="$dir/assets/runtime/arm64-v8a/libandroid-shmem.so"
    for f in "$proot" "$dir/lib/arm64-v8a/libproot_loader.so" "$shmem" \
            "$dir/assets/runtime/arm64-v8a/libtalloc.so.2"; do
        [ -f "$f" ] || { rm -rf "$dir"; gate_die "$apk has no ${f#"$dir"/}"; }
    done
    sh "$check" "$expected" "$proot" "$runtime/loader" \
        || { rm -rf "$dir"; gate_die "PRoot in $apk is not built for $expected"; }
    sh "$check" "$expected" "$shmem" "$runtime/tmp/" \
        || { rm -rf "$dir"; gate_die "libandroid-shmem in $apk is not built for $expected"; }
    # ThothTerm Ubuntu's own JNI library names its socket after the package
    # (CMake's PACKAGE_NAME, the same application id).
    exec_lib="$dir/lib/arm64-v8a/libexec-t1plus.so"
    if [ -f "$exec_lib" ]; then
        sh "$check" "$expected" "$exec_lib" "$expected" \
            || { rm -rf "$dir"; gate_die "libexec-t1plus in $apk is not built for $expected"; }
    fi
    for f in "$dir"/lib/arm64-v8a/* "$dir"/assets/runtime/arm64-v8a/*; do
        sh "$check" "$expected" "$f" \
            || { rm -rf "$dir"; gate_die "${f#"$dir"/} in $apk names another ThothTerm package"; }
    done
    rm -rf "$dir"
    echo "runtime: native files of $apk built for /data/data/$expected/ only"
}

# gate_install APK [ADB INSTALL FLAGS...]: the only way the gate installs
# anything. Always --no-incremental: an incremental install is how a primary
# app's data was lost before.
gate_install() {
    apk=$1; shift
    adb install --no-incremental "$@" "$apk"
}
