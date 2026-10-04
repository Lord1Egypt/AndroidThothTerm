#!/bin/sh
# ThothTerm Security package-manager device gate: Rolling's base cases on the
# shared archive, then the optional third-party repository (extra-repo.sh), on
# disposable rootfs copies.
# Runs as the app itself (run-as, so in the app's own SELinux domain: gpg-agent
# needs a unix socket, which Android refuses the adb shell user) with the
# installed app's PRoot and the app's PRoot argv, under files/ga -- never the
# app's real environment in files/linux.
#
#   gate.sh QA_PACKAGE ROOTFS_TARBALL
#
# QA_PACKAGE: an installed, isolated QA build, com.thothterm.security.qa.<name>
# (tests/garden-common/qa/install-qa-app.sh garden-security .qa.<name>). The gate
# writes into that app's private files/ga, so a production or any other
# protected package is refused before adb is used.
# ROOTFS_TARBALL: the base rootfs (Rolling's pinned archive). Needs garden-security
# built (its PRoot runtime in build/garden) and the garden-common classes compiled
# (the optional-repository script the app generates is dumped and run unchanged by
# extra-repo.sh). Exits 1 on any FAIL, 2 on a refused package.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/../../garden-common/extractor/apk-identity.sh"
[ $# -ge 2 ] || { echo "usage: gate.sh QA_PACKAGE ROOTFS_TARBALL"; exit 2; }
PKG=$1
shift
case "$PKG" in
    com.thothterm.security.qa.*) ;;
    *) echo "REFUSED: $PKG is not a com.thothterm.security QA build"; exit 2 ;;
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
    # at the end, deepest first. Sticky directories (/var/spool/mail is 1777)
    # are recorded too: toybox tar does not keep the sticky bit, the app's
    # extractor does.
    python3 -c 'import sys, tarfile
src = tarfile.open(sys.argv[1], "r:gz")
modes = []
with tarfile.open(sys.argv[2], "w:gz", format=tarfile.GNU_FORMAT, compresslevel=1) as out:
    for m in src:
        if m.isdir() and (not m.mode & 0o200 or m.mode & 0o1000):
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
}

adb shell "chmod -R u+w $S 2>/dev/null; rm -rf $S && mkdir -p $S"
adb push "$HERE/mkroot.sh" "$HERE/run.sh" $S/ >/dev/null
# The runtime libraries the APK stages into files/linux/runtime/lib.
G="$HERE/../../../garden-security/build/garden/assets/runtime/arm64-v8a"
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

# The optional third-party repository's guest script exactly as RootfsManager
# generates it for this edition, and the pinned upstream artifact it is given
# (fetched from the upstream site and compared with the pins first).
REPO="$HERE/../../.."
DISTRO="$REPO/garden-security/src/main/assets/garden/distro.properties"
prop() { sed -n "s/^$1=//p" "$DISTRO"; }
CLASSES=$(find "$REPO/garden-common/build/intermediates/javac" -path '*debug*' -name RootfsManager.class | head -1 | sed 's#/com/thothterm/linux/RootfsManager.class##')
[ -n "$CLASSES" ] || { echo "FAIL garden-common is not compiled"; exit 1; }
ANDROID_JAR=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/platforms/android-*/android.jar | sort -V | tail -1)
mkdir -p "$TMP/sec-dump" && javac -nowarn -d "$TMP/sec-dump" -cp "$CLASSES:$ANDROID_JAR" "$HERE/../host/DumpExtraRepoScript.java" || exit 1
java -cp "$TMP/sec-dump:$CLASSES:$ANDROID_JAR" com.thothterm.linux.DumpExtraRepoScript "$DISTRO" > "$TMP/sec-extra-script.sh" || exit 1
curl -fsSL --proto '=https' -o "$TMP/sec-pkg" "$(prop extraRepoKeyringUrl)" && curl -fsSL --proto '=https' -o "$TMP/sec-sig" "$(prop extraRepoKeyringSigUrl)" \
    || { echo "FAIL cannot fetch the pinned upstream keyring"; exit 1; }
[ "$(sha256sum "$TMP/sec-pkg" | cut -d' ' -f1)" = "$(prop extraRepoKeyringSha256)" ] && [ "$(stat -c %s "$TMP/sec-pkg")" = "$(prop extraRepoKeyringSize)" ] \
    && [ "$(sha256sum "$TMP/sec-sig" | cut -d' ' -f1)" = "$(prop extraRepoKeyringSigSha256)" ] && [ "$(stat -c %s "$TMP/sec-sig")" = "$(prop extraRepoKeyringSigSize)" ] \
    || { echo "FAIL the upstream keyring is no longer the pinned artifact (upstream released a new one: review the pins)"; exit 1; }
base64 -w0 "$TMP/sec-pkg" > "$TMP/sec-pkg.b64"; base64 -w0 "$TMP/sec-sig" > "$TMP/sec-sig.b64"
# The emulation-only flag does not apply on a phone: the script runs exactly as generated.
adb push "$TMP/sec-extra-script.sh" $S/enable-extra-repo.sh >/dev/null
adb push "$TMP/sec-pkg.b64" $S/pkg.b64 >/dev/null
adb push "$TMP/sec-sig.b64" $S/sig.b64 >/dev/null
printf 'TRUSTED="%s"\nREVOKED="%s"\nSIGNER=%s\nSERVER=%s\n' "$(prop extraRepoTrusted)" "$(prop extraRepoRevoked)" "$(prop extraRepoSigner)" \
    'https://blackarch.org/blackarch/blackarch/os/aarch64' > "$TMP/sec-pins"
adb push "$TMP/sec-pins" $S/pins >/dev/null

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
# The optional repository, as the app runs it, on a copy of its own.
mkroot extra $S/final.tar.gz
cmds extra provision.sh extra-repo.sh qkk.sh
for f in enable-extra-repo.sh pkg.b64 sig.b64 pins; do app cp $S/$f $T/extra/cmds/; done
app sh $T/run.sh extra root provision.sh > /dev/null 2>&1
out="$out$(app sh $T/run.sh extra root extra-repo.sh)
"
out="$out$(app sh $T/run.sh extra root qkk.sh)
"
out="${out}UNVERIFIED stale-image upgrade: this is the first Security release; the base is Rolling's image, whose update story is Rolling's (tests/garden-arch/device/stale.sh)
"
printf '%s\n' "$out"
echo "PASS: $(printf '%s\n' "$out" | grep -c '^PASS')  FAIL: $(printf '%s\n' "$out" | grep -c '^FAIL')"
case "$out" in *FAIL*) exit 1 ;; esac
