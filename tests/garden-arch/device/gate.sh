#!/bin/sh
# ThothTerm Rolling package-manager golden gate, on disposable rootfs copies.
# Runs as the app itself (run-as, so in the app's own SELinux domain: gpg-agent
# needs a unix socket, which Android refuses the adb shell user) with the
# installed app's PRoot and the app's PRoot argv, under files/ga -- never the
# app's real environment in files/linux.
#
#   gate.sh ROOTFS_TARBALL [STALE_TARBALL]
#
# ROOTFS_TARBALL: the candidate/final rootfs. STALE_TARBALL (optional): an old
# but valid Arch Linux ARM userland for the update-story test (stale.sh).
# Needs: adb connected, and a debuggable com.thothterm.arch installed.
# Exits 1 on any FAIL.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
PKG=com.thothterm.arch
S=/data/local/tmp/ga
T=/data/data/$PKG/files/ga
TMP="${TMPDIR:-/tmp}"
NLD="$(adb shell dumpsys package $PKG | tr -d '\r' | sed -n 's/.*legacyNativeLibraryDir=//p' | head -1)/arm64"
app() { adb shell "run-as $PKG $*"; }

push_rootfs() { # push_rootfs TARBALL NAME
    # toybox tar applies each directory's mode as it goes, and some archive
    # directories are read-only (the root is 0555 by systemd's tmpfiles
    # root.conf, so is ca-certificates' cadir), which would stop it creating
    # their contents. The app applies directory modes last; this copy gives
    # every directory owner write, and mkroot.sh restores the recorded modes
    # at the end, deepest first.
    python3 -c 'import sys, tarfile
src = tarfile.open(sys.argv[1], "r:gz")
modes = []
with tarfile.open(sys.argv[2], "w:gz", format=tarfile.GNU_FORMAT, compresslevel=1) as out:
    for m in src:
        if m.isdir() and not m.mode & 0o200:
            modes.append("%o %s" % (m.mode, m.name))
            m.mode |= 0o700
        if m.name in (".", "./"):
            continue
        out.addfile(m, src.extractfile(m) if m.isreg() else None)
open(sys.argv[3], "w").write("\n".join(sorted(modes, key=lambda l: -l.count("/"))) + "\n")
' "$1" "$TMP/ga-$2.tar.gz" "$TMP/ga-$2.dirmodes"
    adb push "$TMP/ga-$2.dirmodes" $S/$2.tar.gz.dirmodes >/dev/null
    adb push "$TMP/ga-$2.tar.gz" $S/$2.tar.gz >/dev/null
    # Hard links, which the app (and this test) materialize as copies.
    tar tvzf "$1" | awk '$1 ~ /^h/ {print $6, $9}' > "$TMP/ga-$2.hardlinks"
    adb push "$TMP/ga-$2.hardlinks" $S/$2.tar.gz.hardlinks >/dev/null
    echo "hard links in $2: $(wc -l < "$TMP/ga-$2.hardlinks")"
}
cmds() { # cmds CASE files...
    c=$1; shift
    for f in "$@"; do adb push "$HERE/$f" $S/$f >/dev/null; app cp $S/$f $T/$c/cmds/; done
}

adb shell "chmod -R u+w $S 2>/dev/null; rm -rf $S && mkdir -p $S"
adb push "$HERE/mkroot.sh" "$HERE/run.sh" $S/ >/dev/null
# The runtime libraries the APK stages into files/linux/runtime/lib.
G="$HERE/../../../garden-arch/build/garden/assets/runtime/arm64-v8a"
adb push "$G/libtalloc.so.2" "$G/libandroid-shmem.so" $S/ >/dev/null
printf 'T=%s\nNLD=%s\n' "$T" "$NLD" > "$TMP/ga-env"
adb push "$TMP/ga-env" $S/env >/dev/null
app "chmod -R u+w $T 2>/dev/null; rm -rf $T && mkdir -p $T/lib && cp $S/mkroot.sh $S/run.sh $S/env $T/ && cp $S/libtalloc.so.2 $S/libandroid-shmem.so $T/lib/"
push_rootfs "$1" final

out=""
# The clean install: provision as the app does, the zero-drama gate, a restart.
mkroot() { # mkroot NAME TARBALL: stops the gate unless the rootfs is complete
    r="$(app sh $T/mkroot.sh "$1" "$2" 2>&1)"
    printf '%s\n' "$r" | tail -3
    case "$r" in *"rootfs ready"*) ;; *) echo "FAIL mkroot $1"; exit 1 ;; esac
}
mkroot fresh $S/final.tar.gz
cmds fresh provision.sh zero.sh restart.sh
out="$out$(app sh $T/run.sh fresh root provision.sh 2>&1 | tail -2)
"
out="$out$(app sh $T/run.sh fresh root zero.sh)
"
out="$out$(app sh $T/run.sh fresh root restart.sh)
"
# Interrupted transactions, on a copy of their own.
mkroot broken $S/final.tar.gz
cmds broken provision.sh zero.sh interrupt.sh
app sh $T/run.sh broken root provision.sh > /dev/null 2>&1
app sh $T/run.sh broken root zero.sh > /dev/null 2>&1
out="$out$(app sh $T/run.sh broken root interrupt.sh)
"
if [ $# -ge 2 ]; then
    push_rootfs "$2" stale
    mkroot old $S/stale.tar.gz
    cmds old provision.sh stale.sh
    out="$out$(app sh $T/run.sh old root provision.sh 2>&1 | tail -1)
"
    out="$out$(app sh $T/run.sh old root stale.sh)
"
fi
printf '%s\n' "$out"
echo "PASS: $(printf '%s\n' "$out" | grep -c '^PASS')  FAIL: $(printf '%s\n' "$out" | grep -c '^FAIL')"
case "$out" in *FAIL*) exit 1 ;; esac
