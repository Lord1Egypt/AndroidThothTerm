#!/bin/sh
# ThothTerm BlackArch package-manager device gate (adapted from Rolling's), on
# disposable rootfs copies.
# Runs as the app itself (run-as, so in the app's own SELinux domain: gpg-agent
# needs a unix socket, which Android refuses the adb shell user) with the
# installed app's PRoot and the app's PRoot argv, under files/ga -- never the
# app's real environment in files/linux.
#
#   gate.sh QA_PACKAGE ROOTFS_TARBALL
#
# QA_PACKAGE: an installed, isolated QA build, com.thothterm.blackarch.qa.<name>
# (tests/garden-common/qa/install-qa-app.sh garden-blackarch .qa.<name>). The gate
# writes into that app's private files/ga, so a production or any other
# protected package is refused before adb is used.
# ROOTFS_TARBALL: the candidate rootfs. Needs garden-blackarch built (its PRoot
# runtime in build/garden) and the garden-common classes compiled (the keyring
# script the app generates is dumped and run unchanged). Exits 1 on any FAIL, 2 on
# a refused package.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/../../garden-common/extractor/apk-identity.sh"
[ $# -ge 2 ] || { echo "usage: gate.sh QA_PACKAGE ROOTFS_TARBALL"; exit 2; }
PKG=$1
shift
case "$PKG" in
    com.thothterm.blackarch.qa.*) ;;
    *) echo "REFUSED: $PKG is not a com.thothterm.blackarch QA build"; exit 2 ;;
esac
if ! gate_is_qa_id "$PKG" || gate_is_protected "$PKG"; then
    echo "REFUSED: $PKG is not an isolated QA package"; exit 2
fi
adb shell pm path "$PKG" | tr -d '\r' | grep -q '^package:' \
    || { echo "REFUSED: $PKG is not installed"; exit 2; }
# Guest processes started with adb run-as have no foreground component, and Android
# denies the network to such an app (measured 2026-10-03: DNS fails in the guest of
# the very rootfs that synced fine while the app was in front; it works the moment the
# package is on the power-save allowlist and fails again when it is removed). The QA
# package alone is exempted for the length of this run and always restored.
adb shell dumpsys deviceidle whitelist +"$PKG" > /dev/null
trap 'adb shell dumpsys deviceidle whitelist -"$PKG" > /dev/null 2>&1' EXIT
S=/data/local/tmp/ga
T=/data/user/0/$PKG/files/ga
TMP="${TMPDIR:-/tmp}"
NLD="$(adb shell dumpsys package $PKG | tr -d '\r' | sed -n 's/.*legacyNativeLibraryDir=//p' | head -1)/arm64"
app() { adb shell "run-as $PKG sh -c '$*'"; }

push_rootfs() { # push_rootfs TARBALL NAME
    [ -s "$1" ] || { echo "FAIL missing rootfs archive: $1"; exit 1; }
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
' "$1" "$TMP/ga-$2.tar.gz" "$TMP/ga-$2.dirmodes" || exit 1
    adb push "$TMP/ga-$2.dirmodes" $S/$2.tar.gz.dirmodes >/dev/null
    adb push "$TMP/ga-$2.tar.gz" $S/$2.tar.gz >/dev/null
    # Hard links, which the app (and this test) materialize as copies.
    tar tvzf "$1" > "$TMP/ga-$2.list" || exit 1
    awk '$1 ~ /^h/ {print $6, $9}' "$TMP/ga-$2.list" > "$TMP/ga-$2.hardlinks" || exit 1
    adb push "$TMP/ga-$2.hardlinks" $S/$2.tar.gz.hardlinks >/dev/null
    echo "hard links in $2: $(wc -l < "$TMP/ga-$2.hardlinks")"
}
cmds() { # cmds CASE files...
    c=$1; shift
    for f in "$@"; do adb push "$HERE/$f" $S/$f >/dev/null; app cp $S/$f $T/$c/cmds/; done
    for f in keyring-script.sh check-pacman-conf.sh keyring.pins; do app cp $S/$f $T/$c/cmds/; done
}

adb shell "chmod -R u+w $S 2>/dev/null; rm -rf $S && mkdir -p $S"
adb push "$HERE/mkroot.sh" "$HERE/run.sh" $S/ >/dev/null
# The keyring script exactly as RootfsManager generates it for this edition.
REPO="$HERE/../../.."
CLASSES=$(find "$REPO/garden-common/build/intermediates/javac" -path '*debug*' -name RootfsManager.class | head -1 | sed 's#/com/thothterm/linux/RootfsManager.class##')
[ -n "$CLASSES" ] || { echo "FAIL garden-common is not compiled"; exit 1; }
ANDROID_JAR=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/platforms/android-*/android.jar | sort -V | tail -1)
mkdir -p "$TMP/ba-dump" && javac -nowarn -d "$TMP/ba-dump" -cp "$CLASSES:$ANDROID_JAR" "$HERE/../host/DumpKeyringScript.java" || exit 1
java -cp "$TMP/ba-dump:$CLASSES:$ANDROID_JAR" com.thothterm.linux.DumpKeyringScript \
    "$REPO/garden-blackarch/src/main/assets/garden/distro.properties" > "$TMP/ba-keyring-script.sh" || exit 1
grep -q 'pacman-key --populate archlinuxarm blackarch' "$TMP/ba-keyring-script.sh" || { echo "FAIL the keyring script does not populate both keyrings"; exit 1; }
adb push "$TMP/ba-keyring-script.sh" $S/keyring-script.sh >/dev/null
adb push "$REPO/garden-blackarch/rootfs/check-pacman-conf.sh" "$REPO/garden-blackarch/rootfs/keyring.pins" $S/ >/dev/null
# The runtime libraries the APK stages into files/linux/runtime/lib.
G="$HERE/../../../garden-blackarch/build/garden/assets/runtime/arm64-v8a"
adb push "$G/libtalloc.so.2" "$G/libandroid-shmem.so" $S/ >/dev/null
# The resolvers Android is using, as the app's AndroidNetworkResolver writes them.
adb shell dumpsys connectivity | tr -d '\r' | grep -oE 'DnsAddresses: \[[^]]*\]' | head -1 \
    | sed 's/^DnsAddresses: \[//; s/\]$//' | tr ',' '\n' | sed 's#^ */##; s# *$##' | grep . \
    | sed 's/^/nameserver /' > "$TMP/ga-resolv.conf"
grep -q nameserver "$TMP/ga-resolv.conf" || { echo "FAIL no Android DNS servers"; exit 1; }
adb push "$TMP/ga-resolv.conf" $S/resolv.conf >/dev/null
printf 'T=%s\nNLD=%s\nS=%s\n' "$T" "$NLD" "$S" > "$TMP/ga-env"
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
cmds fresh provision.sh zero.sh restart.sh qkk.sh netcheck.sh
net="$(app sh $T/run.sh fresh root netcheck.sh 2>&1 | tail -2)"
case "$net" in *network-ok*) echo "INFO guest network: ok" ;; *) printf '%s\n' "$net"; echo "ENVIRONMENT BLOCKED: the disposable guest has no network; no repository case can mean anything"; exit 3 ;; esac
prov="$(app sh $T/run.sh fresh root provision.sh 2>&1)"
case "$prov" in *keyring-verified*) out="${out}PASS first-run keyring: $(printf '%s\n' "$prov" | tail -1)
" ;; *) printf '%s\n' "$prov" | tail -20; echo "FAIL first-run keyring provisioning"; exit 1 ;; esac
case "$prov" in *[Ww]arning*|*WARNING*|*rror*) out="${out}FAIL provisioning printed warnings: $(printf '%s\n' "$prov" | grep -iE 'warning|error' | head -3 | tr '\n' ' ')
" ;; esac
out="$out$(app sh $T/run.sh fresh root zero.sh)
"
out="$out$(app sh $T/run.sh fresh root qkk.sh)
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
out="${out}UNVERIFIED stale-image upgrade: no older BlackArch userland exists (this is the first BlackArch rootfs; the first pacman -Syu on a 2026-09-30 image is the update story)
"
printf '%s\n' "$out"
echo "PASS: $(printf '%s\n' "$out" | grep -c '^PASS')  FAIL: $(printf '%s\n' "$out" | grep -c '^FAIL')"
case "$out" in *FAIL*) exit 1 ;; esac
