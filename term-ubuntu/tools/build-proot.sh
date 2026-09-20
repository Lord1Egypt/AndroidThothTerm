#!/bin/sh
#
# Build the PRoot runtime from source for arm64-v8a, using only sources that
# live in this repository: two pinned git submodules and two vendored talloc
# files. Nothing is downloaded and no prebuilt binary is copied in, so an
# F-Droid build produces the runtime it ships.
#
# Inputs (see THIRD_PARTY_NOTICES.md for licenses and provenance):
#   third_party/proot              termux/proot @ 7266fb3e (tag v5.1.107.92), GPL-2.0
#   third_party/libandroid-shmem   termux/libandroid-shmem @ 7f0bd7e2 (tag v0.7), BSD-3-Clause
#   third_party/talloc             talloc 2.4.3 talloc.c/talloc.h, verbatim, LGPL-3.0
#   term-ubuntu/patches/*.patch    applied to third_party/proot, in listed order
#
# Outputs:
#   src/main/jniLibs/arm64-v8a/libproot.so         (the proot executable)
#   src/main/jniLibs/arm64-v8a/libproot_loader.so  (the PRoot loader)
#   src/main/assets/runtime/arm64-v8a/libtalloc.so.2
#   src/main/assets/runtime/arm64-v8a/libandroid-shmem.so
#
# Run from the term-ubuntu module directory. Requires ANDROID_NDK_HOME (or
# ANDROID_SDK_ROOT/ANDROID_HOME with an ndk/<version> directory).
set -eu

ABI="arm64-v8a"
API=26
# Android 15 introduced 16 KB page devices; every arm64 ELF we ship must have
# LOAD segments aligned to 16 KB or the system reports the app as incompatible.
# Stating it here keeps the result the same whichever NDK the build runs with,
# rather than relying on a default (NDK r28+ does this, r23 does not).
PAGE_ALIGN_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
TALLOC_VERSION_MAJOR=2
TALLOC_VERSION_MINOR=4
TALLOC_VERSION_RELEASE=3

# Pinned so the build fails loudly if a submodule is moved.
PROOT_COMMIT="7266fb3e8516535682f5a9c8f3a7e70f6506eddb"
SHMEM_COMMIT="7f0bd7e25dbdd146265aff7c6a890029e374622d"

# Where the guest runtime keeps its scratch space. libandroid-shmem compiles
# this in, and PRoot is told where to find its unbundled loader.
APP_ID="${THOTHTERM_APPLICATION_ID:-com.thothterm.ubuntu}"
RUNTIME_DIR="/data/data/${APP_ID}/files/linux/runtime"

REPO_ROOT="$(cd .. && pwd)"
MODULE_DIR="$(pwd)"
PROOT_SRC="$REPO_ROOT/third_party/proot"
SHMEM_SRC="$REPO_ROOT/third_party/libandroid-shmem"
TALLOC_SRC="$REPO_ROOT/third_party/talloc"
PATCH_DIR="$MODULE_DIR/patches"

BUILD_DIR="${THOTHTERM_PROOT_BUILD_DIR:-$MODULE_DIR/build/proot-src}"
JNI_DIR="$MODULE_DIR/src/main/jniLibs/$ABI"
RUNTIME_ASSETS="$MODULE_DIR/src/main/assets/runtime/$ABI"

log() { echo "build-proot: $*"; }
die() { echo "build-proot: ERROR: $*" >&2; exit 1; }

# ---------------------------------------------------------------- toolchain
ndk_root() {
    if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
        echo "$ANDROID_NDK_HOME"; return 0
    fi
    if [ -n "${ANDROID_NDK_ROOT:-}" ] && [ -d "$ANDROID_NDK_ROOT" ]; then
        echo "$ANDROID_NDK_ROOT"; return 0
    fi
    _sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    [ -n "$_sdk" ] || return 1
    # Prefer the version the Gradle build is pinned to, so the PRoot runtime and
    # the CMake targets come from one toolchain.
    if [ -n "${THOTHTERM_NDK_VERSION:-}" ] \
        && [ -d "$_sdk/ndk/$THOTHTERM_NDK_VERSION" ]; then
        echo "$_sdk/ndk/$THOTHTERM_NDK_VERSION"; return 0
    fi
    _found="$(ls -1d "$_sdk"/ndk/* 2>/dev/null | sort -V | tail -1)"
    [ -n "$_found" ] || return 1
    echo "$_found"
}

NDK="$(ndk_root)" || die "no NDK found; set ANDROID_NDK_HOME"
[ -d "$NDK" ] || die "NDK directory does not exist: $NDK"

HOST_TAG="linux-x86_64"
[ -d "$NDK/toolchains/llvm/prebuilt/$HOST_TAG" ] || HOST_TAG="darwin-x86_64"
TOOLS="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
[ -d "$TOOLS" ] || die "NDK toolchain not found under $NDK"

CC="$TOOLS/aarch64-linux-android$API-clang"
[ -x "$CC" ] || die "no clang for API $API in $TOOLS"
OBJCOPY="$TOOLS/llvm-objcopy"
OBJDUMP="$TOOLS/llvm-objdump"
STRIP="$TOOLS/llvm-strip"
READELF="$TOOLS/llvm-readelf"

log "NDK      : $NDK"
log "compiler : $(basename "$CC")"

# ------------------------------------------------------------------ sources
[ -f "$PROOT_SRC/src/GNUmakefile" ] \
    || die "third_party/proot is empty; run: git submodule update --init --recursive"
[ -f "$SHMEM_SRC/shmem.c" ] \
    || die "third_party/libandroid-shmem is empty; run: git submodule update --init --recursive"
[ -f "$TALLOC_SRC/talloc.c" ] || die "third_party/talloc/talloc.c is missing"

check_commit() {
    _dir="$1"; _want="$2"; _name="$3"
    _have="$(cd "$_dir" && git rev-parse HEAD 2>/dev/null || echo unknown)"
    [ "$_have" = "$_want" ] \
        || die "$_name is at $_have, expected $_want"
}
check_commit "$PROOT_SRC" "$PROOT_COMMIT" "third_party/proot"
check_commit "$SHMEM_SRC" "$SHMEM_COMMIT" "third_party/libandroid-shmem"

# Build from a copy: PRoot's makefile builds in-tree, and the submodule must
# stay pristine so the pinned commit check keeps meaning something.
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
cp -R "$PROOT_SRC" "$BUILD_DIR/proot"
rm -rf "$BUILD_DIR/proot/.git"

if [ -d "$PATCH_DIR" ]; then
    for _patch in "$PATCH_DIR"/*.patch; do
        [ -e "$_patch" ] || continue
        log "patch    : $(basename "$_patch")"
        ( cd "$BUILD_DIR/proot" && patch -p1 --batch --silent < "$_patch" ) \
            || die "failed to apply $(basename "$_patch")"
    done
fi

mkdir -p "$BUILD_DIR/include/sys" "$BUILD_DIR/lib"
cp "$SHMEM_SRC/shm.h" "$BUILD_DIR/include/sys/shm.h"

# talloc expects a portability header; on bionic the standard headers suffice.
cat > "$BUILD_DIR/include/replace.h" <<'REPLACE_H'
/* Minimal replace.h for building talloc against Android's bionic libc.
   Upstream talloc ships lib/replace for platforms missing C99/POSIX pieces;
   bionic provides all of them, so this only supplies the few macros
   talloc.c expects from that header. */
#ifndef _THOTHTERM_TALLOC_REPLACE_H_
#define _THOTHTERM_TALLOC_REPLACE_H_
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <unistd.h>
#include <errno.h>
#include <limits.h>
#include <sys/types.h>
#ifndef MIN
#define MIN(a,b) ((a) < (b) ? (a) : (b))
#endif
#ifndef MAX
#define MAX(a,b) ((a) > (b) ? (a) : (b))
#endif
#ifndef PRINTF_ATTRIBUTE
#define PRINTF_ATTRIBUTE(a,b) __attribute__((format(__printf__, a, b)))
#endif
#ifndef _PUBLIC_
#define _PUBLIC_ __attribute__((visibility("default")))
#endif
#ifndef _DEPRECATED_
#define _DEPRECATED_ __attribute__((deprecated))
#endif
#ifndef HAVE_VA_COPY
#define HAVE_VA_COPY 1
#endif
#endif
REPLACE_H

# -------------------------------------------------------------------- build
log "building libtalloc.so.2"
"$CC" -c "$TALLOC_SRC/talloc.c" -o "$BUILD_DIR/talloc.o" \
    -I"$BUILD_DIR/include" -I"$TALLOC_SRC" \
    -O2 -fPIC -Wall \
    -DHAVE_VA_COPY -DHAVE_STDBOOL_H -DHAVE_STDINT_H \
    -DHAVE_CONSTRUCTOR_ATTRIBUTE \
    -DTALLOC_BUILD_VERSION_MAJOR=$TALLOC_VERSION_MAJOR \
    -DTALLOC_BUILD_VERSION_MINOR=$TALLOC_VERSION_MINOR \
    -DTALLOC_BUILD_VERSION_RELEASE=$TALLOC_VERSION_RELEASE
"$CC" -shared -o "$BUILD_DIR/lib/libtalloc.so.2" "$BUILD_DIR/talloc.o" \
    -Wl,-soname,libtalloc.so.2 -Wl,-z,noexecstack $PAGE_ALIGN_LDFLAGS
# ld.lld resolves -ltalloc through libtalloc.so; the SONAME above is what the
# runtime actually loads.
cp "$BUILD_DIR/lib/libtalloc.so.2" "$BUILD_DIR/lib/libtalloc.so"

log "building libandroid-shmem.so"
"$CC" -c "$SHMEM_SRC/shmem.c" -o "$BUILD_DIR/shmem.o" \
    -O2 -fPIC -std=c11 -Wall \
    -D_PATH_TMP="\"$RUNTIME_DIR/tmp/\""
"$CC" -shared -o "$BUILD_DIR/lib/libandroid-shmem.so" "$BUILD_DIR/shmem.o" \
    -llog -landroid \
    -Wl,--version-script="$SHMEM_SRC/exports.txt" \
    -Wl,-soname,libandroid-shmem.so -Wl,-z,noexecstack $PAGE_ALIGN_LDFLAGS

log "building proot and loader"
(
    cd "$BUILD_DIR/proot/src"
    CC="$CC" LD="$CC" OBJCOPY="$OBJCOPY" OBJDUMP="$OBJDUMP" STRIP="$STRIP" \
    CPPFLAGS="-I$TALLOC_SRC -I$BUILD_DIR/include" \
    LDFLAGS="-L$BUILD_DIR/lib -Wl,-z,noexecstack $PAGE_ALIGN_LDFLAGS" \
    LOADER_LDFLAGS="$PAGE_ALIGN_LDFLAGS" \
    PROOT_WITH_LIBANDROID_SHMEM=1 \
    PROOT_UNBUNDLE_LOADER="$RUNTIME_DIR/loader" \
    make -s
)

PROOT_BIN="$BUILD_DIR/proot/src/proot"
LOADER_BIN="$BUILD_DIR/proot/src/loader/loader"
[ -x "$PROOT_BIN" ] || die "proot was not produced"
[ -x "$LOADER_BIN" ] || die "loader was not produced"

# The runtime contract the app relies on: proot must resolve talloc and the
# shmem shim from the runtime library directory, or guest execve fails later.
for _need in libtalloc.so.2 libandroid-shmem.so; do
    "$READELF" -d "$PROOT_BIN" 2>/dev/null | grep -q "\[$_need\]" \
        || die "proot is not linked against $_need"
done

# 16 KB alignment is a device-compatibility requirement, not a nicety: verify
# every artifact we ship rather than assume a flag took effect. The loader is
# linked through the makefile's own LOADER_LDFLAGS, so it needs its own check --
# it was the one binary that slipped through when only proot was verified.
check_alignment() {
    _file="$1"
    _align="$("$READELF" -l "$_file" 2>/dev/null | awk '/LOAD/ {print $NF; exit}')"
    [ "$_align" = "0x4000" ] \
        || die "$(basename "$_file") LOAD alignment is $_align, expected 0x4000 (16 KB)"
    log "alignment      : $(basename "$_file") $_align"
}
check_alignment "$PROOT_BIN"
check_alignment "$LOADER_BIN"
check_alignment "$BUILD_DIR/lib/libtalloc.so.2"
check_alignment "$BUILD_DIR/lib/libandroid-shmem.so"

# ------------------------------------------------------------------ install
mkdir -p "$JNI_DIR" "$RUNTIME_ASSETS"
# Executables ship under lib*.so names so Android extracts them into
# nativeLibraryDir with the executable bit; see packaging in build.gradle.
install -m 755 "$PROOT_BIN" "$JNI_DIR/libproot.so"
install -m 755 "$LOADER_BIN" "$JNI_DIR/libproot_loader.so"
install -m 644 "$BUILD_DIR/lib/libtalloc.so.2" "$RUNTIME_ASSETS/libtalloc.so.2"
install -m 644 "$BUILD_DIR/lib/libandroid-shmem.so" "$RUNTIME_ASSETS/libandroid-shmem.so"

log "ready"
log "  proot          : $JNI_DIR/libproot.so"
log "  loader         : $JNI_DIR/libproot_loader.so"
log "  libtalloc.so.2 : $RUNTIME_ASSETS/libtalloc.so.2"
log "  libandroid-shmem.so : $RUNTIME_ASSETS/libandroid-shmem.so"
