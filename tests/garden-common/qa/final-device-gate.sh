#!/bin/sh
# The final physical gate for the Garden release candidates, in one run, on
# isolated QA builds only. Nothing here may touch a production app: every
# install is a QA id proven from the APK (install-qa-app.sh, device-gate.sh),
# always --no-incremental, and nothing is ever uninstalled or cleared. The
# protected apps' install records are compared before and after; any change
# is a FAIL.
#
#   tests/garden-common/qa/final-device-gate.sh EVIDENCE_DIR ARCHIVE_DIR [STALE_ARCH_USERLAND]
#
# ARCHIVE_DIR holds the three pinned archives under their pinned names:
#   ubuntu-base-26.04.1-base-arm64.tar.gz
#   thothterm-debian-trixie-arm64-rootfs-f6520ff1c6ee.tar.gz
#   thothterm-arch-aarch64-rootfs-03a4c669ed6f.tar.gz
# STALE_ARCH_USERLAND (optional) is an older official Arch Linux ARM userland
# for the Arch gate's update story.
#
# Needs ANDROID_HOME (aapt2), ANDROID_NDK_HOME, adb with exactly one device
# (or ANDROID_SERIAL), python3, unzip. A few steps need a person at the phone
# (consent, the reinstall dialog, LAN in a browser): the script says exactly
# what to do and records the answer. Every line of the result is PASS, FAIL
# or UNVERIFIED in EVIDENCE_DIR/summary.txt; the exit status is 0 only when
# there is no FAIL and no UNVERIFIED.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../.." && pwd)
. "$REPO/tests/garden-common/extractor/apk-identity.sh"
[ $# -ge 2 ] || { echo "usage: final-device-gate.sh EVIDENCE_DIR ARCHIVE_DIR [STALE_ARCH_USERLAND]"; exit 2; }
E=$1
A=$2
STALE=${3:-}
mkdir -p "$E"
E=$(cd "$E" && pwd)
SUMMARY=$E/summary.txt
: > "$SUMMARY"
cd "$REPO"

UBUNTU_ARCHIVE=$A/ubuntu-base-26.04.1-base-arm64.tar.gz
UBUNTU_SHA=5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd
TRIXIE_ARCHIVE=$A/thothterm-debian-trixie-arm64-rootfs-f6520ff1c6ee.tar.gz
TRIXIE_SHA=f6520ff1c6eeb28ead2d175e6733da4c970459a291b1bd60f992f1655c5d0677
ARCH_ARCHIVE=$A/thothterm-arch-aarch64-rootfs-03a4c669ed6f.tar.gz
ARCH_SHA=03a4c669ed6f89d044a9e83dce54c554f04885fd1bb7d1793f5b95b7cc0f48d1

result() { # result PASS|FAIL|UNVERIFIED TEXT
    echo "$1 $2" | tee -a "$SUMMARY"
}
check() { # check STATUS TEXT: 0 = PASS, anything else = FAIL
    if [ "$1" = 0 ]; then result PASS "$2"; else result FAIL "$2"; fi
}
ask() { # ask QUESTION: the operator's verdict, PASS/FAIL/UNVERIFIED
    printf '\n>>> %s\n    Answer p (pass), f (fail) or s (skip = UNVERIFIED): ' "$1"
    read -r answer < /dev/tty
    case "$answer" in
        p|P) result PASS "$1" ;;
        f|F) result FAIL "$1" ;;
        *) result UNVERIFIED "$1" ;;
    esac
}
action() { # action TEXT: a step only a person can do; waits for Enter
    printf '\n>>> ACTION: %s\n    Press Enter when done. ' "$1"
    read -r _ < /dev/tty
}
shq() { # single-quote for the device shell
    printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}

# ---- preflight -------------------------------------------------------------
if [ -z "${ANDROID_SERIAL:-}" ]; then
    n=$(adb devices | sed -n 's/^\([^[:space:]]*\)[[:space:]]*device$/\1/p' | wc -l)
    [ "$n" -eq 1 ] || { echo "ABORT: $n devices on adb; connect one or set ANDROID_SERIAL"; exit 2; }
fi
for f in "$UBUNTU_ARCHIVE" "$TRIXIE_ARCHIVE" "$ARCH_ARCHIVE"; do
    [ -s "$f" ] || { echo "ABORT: missing $f"; exit 2; }
done
[ -n "${ANDROID_NDK_HOME:-}" ] || { echo "ABORT: set ANDROID_NDK_HOME"; exit 2; }
{
    for p in ro.product.model ro.product.device ro.build.version.release ro.build.version.sdk \
             ro.build.version.security_patch ro.product.cpu.abi ro.build.fingerprint; do
        echo "$p=$(adb shell getprop $p | tr -d '\r')"
    done
    echo "kernel=$(adb shell uname -r | tr -d '\r')"
    echo "page_size=$(adb shell getconf PAGESIZE | tr -d '\r')"
    echo "repo=$(git rev-parse HEAD) $(git status --porcelain | wc -l) uncommitted"
} > "$E/device.txt"
cat "$E/device.txt"
KERNEL=$(sed -n 's/^kernel=//p' "$E/device.txt")

PROTECTED_RE='^package:\(com\.thothterm\(\.devel\|\.ubuntu\|\.debian\|\.arch\)\?\|.*[Pp][Oo][Cc][Kk][Ee][Tt][-_]\?[Cc][Ll][Aa][Ww].*\)$'
inventory() { # inventory FILE: install records of every protected app present
    adb shell pm list packages | tr -d '\r' | grep "$PROTECTED_RE" | sed 's/^package://' | sort |
    while read -r p; do
        adb shell dumpsys package "$p" | tr -d '\r' \
            | grep -E '^ *(versionCode|firstInstallTime|lastUpdateTime|dataDir)=' \
            | sed "s/^ */$p /" | sort -u
    done > "$1"
}
inventory "$E/protected-before.txt"
echo "protected apps present: $(cut -d' ' -f1 "$E/protected-before.txt" | sort -u | tr '\n' ' ')"

# ---- 1. the APK's own extractor, AndroidFileOps, on every pinned archive ----
for x in "garden-arch $ARCH_ARCHIVE $ARCH_SHA" "garden-debian $TRIXIE_ARCHIVE $TRIXIE_SHA" \
         "term-ubuntu $UBUNTU_ARCHIVE $UBUNTU_SHA"; do
    set -- $x
    tests/garden-common/extractor/device-gate.sh "$1" "$2" "$3" > "$E/extractor-$1.txt" 2>&1
    check $? "extractor device gate $1: adversarial cases, FileOps contract (incl. chmod swap race), setuid helper, real archive vs manifest"
done

# ---- 2. isolated QA apps: full and F-Droid flavour of each edition -----------
QA_APPS=""
for m in garden-arch garden-debian term-ubuntu; do
    for fl in full fdroid; do
        suffix=.qa.final; [ "$fl" = fdroid ] && suffix=.qa.finalfd
        tests/garden-common/qa/install-qa-app.sh "$m" "$suffix" "$fl" > "$E/install-$m-$fl.txt" 2>&1
        status=$?
        check $status "install $m $fl as a QA id (aapt2 identity, runtime ids, --no-incremental)"
        [ $status = 0 ] || continue
        case $m in
            garden-arch) base=com.thothterm.arch; distro=arch-aarch64 ;;
            garden-debian) base=com.thothterm.debian; distro=debian-trixie ;;
            term-ubuntu) base=com.thothterm.ubuntu; distro=ubuntu-26.04 ;;
        esac
        QA_APPS="$QA_APPS $m:$fl:$base$suffix:$distro"
    done
done

app() { adb shell "run-as $PKG sh -c $(shq "$1")"; }
stages() { adb logcat -d -v time -s ThothTerm | tr -d '\r' | grep -E 'ROOTFS: (Setup stage|Setup timeline|Environment condition|Preparing condition|Hardlink fallback|Completed an interrupted reset)'; }
wait_for() { # wait_for PATTERN SECONDS: in this run's ThothTerm log
    i=0
    while [ $i -lt "$2" ]; do
        adb logcat -d -s ThothTerm | tr -d '\r' | grep -q "$1" && return 0
        sleep 2; i=$((i + 2))
    done
    return 1
}
launch() { adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }
relaunch() { adb shell am force-stop "$PKG"; adb logcat -c; launch; }
no_anr() { ! adb logcat -d -b events | tr -d '\r' | grep "am_anr" | grep -q "$PKG"; }
terminal_in_front() { # terminal_in_front SECONDS: the terminal activity (not setup) is resumed
    i=0
    while [ $i -lt "$1" ]; do
        adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity|mResumedActivity' \
            | grep -q "$PKG/.*TermActivity\|$PKG/jackpal.androidterm.Term" && return 0
        sleep 2; i=$((i + 2))
    done
    return 1
}

# A guest command with the app's own PRoot, run as the app (its runtime is app-private).
guest_helper() { # guest_helper DISTRO_DIR
    nld=$(adb shell dumpsys package "$PKG" | tr -d '\r' | sed -n 's/.*legacyNativeLibraryDir=//p' | head -1)/arm64
    app "cat > files/qa-guest.sh <<'EOF'
F=/data/user/0/$PKG/files
RT=\$F/linux/runtime
export PROOT_LOADER=$nld/libproot_loader.so LD_LIBRARY_PATH=\$RT/lib PROOT_TMP_DIR=\$RT/tmp
export HOME=/home/thoth LANG=C.UTF-8 PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin SYSTEMD_IN_CHROOT=1
exec $nld/libproot.so --rootfs=\$F/linux/$1/rootfs --root-id --link2symlink --kill-on-exit \\
  --kernel-release=6.1.0-thothterm --bind=/dev --bind=/proc --bind=/sys \\
  --bind=/proc/self/mounts:/etc/mtab \${QA_BIND:-} /bin/sh -c \"\$1\"
EOF"
}
guest() { app "sh files/qa-guest.sh $(shq "$1")"; }

HOME_SENTINEL=/home/thoth/.qa-final-sentinel
sentinel() { # sentinel DISTRO_DIR: sha256 and inode of the HOME sentinel, or "missing"
    app "f=files/linux/$1/rootfs$HOME_SENTINEL; [ -f \$f ] && echo \$(sha256sum \$f | cut -d' ' -f1) \$(stat -c %i \$f) || echo missing" | tr -d '\r'
}

for spec in $QA_APPS; do
    m=${spec%%:*}; rest=${spec#*:}; fl=${rest%%:*}; rest=${rest#*:}; PKG=${rest%%:*}; distro=${rest#*:}
    gate_is_qa_id "$PKG" && ! gate_is_protected "$PKG" || { echo "ABORT: $PKG"; exit 4; }
    tag="$m $fl ($PKG)"
    echo; echo "==== $tag"

    # -- first run: consent (F-Droid), download/extract, TERMINAL_READY, optional setup after
    adb logcat -c
    launch
    if [ "$fl" = fdroid ]; then
        action "On the phone, in $PKG: read the download consent screen (size, source URL, SHA-256 check) and tap the download button."
    fi
    if wait_for "Setup stage TERMINAL_READY" 900; then
        stages > "$E/first-run-$m-$fl.txt"
        r=$(grep -n "TERMINAL_READY" "$E/first-run-$m-$fl.txt" | head -1 | cut -d: -f1)
        h=$(grep -n "TERMINAL_HANDOFF" "$E/first-run-$m-$fl.txt" | head -1 | cut -d: -f1)
        wait_for "Setup stage OPTIONAL_SETUP_\(FINISHED\|FAILED\)" 600
        stages > "$E/first-run-$m-$fl.txt"
        o=$(grep -n "OPTIONAL_SETUP_STARTED" "$E/first-run-$m-$fl.txt" | head -1 | cut -d: -f1)
        [ -n "$r" ] && [ -n "$h" ] && [ -n "$o" ] && [ "$r" -lt "$h" ] && [ "$h" -lt "$o" ]
        check $? "$tag: TERMINAL_READY, then TERMINAL_HANDOFF, then optional setup (timings in first-run-$m-$fl.txt)"
        grep -q "OPTIONAL_SETUP_FINISHED" "$E/first-run-$m-$fl.txt"
        check $? "$tag: optional setup (sudo$( [ $m = garden-arch ] && echo ', pacman keyring')) finished"
    else
        stages > "$E/first-run-$m-$fl.txt"
        result FAIL "$tag: no TERMINAL_READY within 15 min (first-run-$m-$fl.txt)"
        continue
    fi
    no_anr; check $? "$tag: no ANR during first run"
    ask "$tag: the terminal opened by itself after 'Finalizing'; type 'echo hi' -- it answers; rotate the phone, background and reopen the app, close and reopen a window: the shell still answers and the title is right"

    guest_helper "$distro"
    out=$(guest 'echo guest-ok; id -u; test -x /usr/bin/sudo && echo sudo-present; systemd-detect-virt --chroot && echo chroot-yes' | tr -d '\r')
    echo "$out" > "$E/guest-$m-$fl.txt"
    echo "$out" | grep -q '^guest-ok$'; check $? "$tag: a guest shell runs under the app's PRoot"
    echo "$out" | grep -q '^sudo-present$'; check $? "$tag: sudo installed by optional setup"
    echo "$out" | grep -q '^chroot-yes$'; check $? "$tag: systemd-detect-virt --chroot says yes (SYSTEMD_IN_CHROOT=1)"

    # -- PRoot child tracking (patch 0007) on this kernel
    if [ "$fl" = full ]; then
        $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang \
            -O2 -static -o "$E/fork-probe-arm64" tests/garden-common/proot/fork-probe.c || exit 2
        adb push "$E/fork-probe-arm64" /data/local/tmp/qa-fork-probe >/dev/null
        app "cp /data/local/tmp/qa-fork-probe files/qa-fork-probe && chmod 700 files/qa-fork-probe"
        : > "$E/fork-probe-$m.txt"
        for mode in fork vfork spawn clone-parent thread forks-concurrent threads-concurrent; do
            o=$(app "QA_BIND=--bind=/data/user/0/$PKG/files/qa-fork-probe:/qa-fork-probe timeout 120 sh files/qa-guest.sh '/qa-fork-probe $mode'; echo rc=\$?" | tr -d '\r')
            echo "$mode: $o" >> "$E/fork-probe-$m.txt"
            case "$o" in *"ok $mode"*) result PASS "$tag: PRoot runs fork-probe $mode" ;;
                *"cannot be identified uniquely"*) result PASS "$tag: fork-probe $mode stopped cleanly (ambiguous child refused; see fork-probe-$m.txt)" ;;
                *) result FAIL "$tag: fork-probe $mode (fork-probe-$m.txt)" ;; esac
        done
        grep -q "GETEVENTMSG" "$E/fork-probe-$m.txt" \
            && echo "NOTE: this kernel withheld a fork event pid ($KERNEL)" | tee -a "$SUMMARY"
    fi

    # -- package manager on the real installation
    case $m in
        term-ubuntu|garden-debian)
            out=$(guest 'export DEBIAN_FRONTEND=noninteractive; apt-get update >/dev/null && apt-get install -y --no-install-recommends tree >/dev/null && tree --version && apt-get remove -y tree >/dev/null && dpkg --audit && echo apt-ok' 2>&1 | tr -d '\r')
            echo "$out" > "$E/apt-$m-$fl.txt"
            echo "$out" | grep -q '^apt-ok$'; check $? "$tag: apt update/install/remove and dpkg --audit clean" ;;
        garden-arch)
            out=$(guest 'pacman -Syu --noconfirm 2>&1; echo first=$?; pacman -Syu --noconfirm 2>&1; echo second=$?; pacman -Dk 2>&1; echo dk=$?; pacman -Qk 2>&1 | tail -3; echo qk=$?' | tr -d '\r')
            echo "$out" > "$E/pacman-$fl.txt"
            echo "$out" | grep -q '^first=0$' && echo "$out" | grep -q '^second=0$' \
                && echo "$out" | grep -q '^dk=0$' && ! echo "$out" | grep -q "Can't set permissions\|ptrace(GETEVENTMSG)\|Failed to check for chroot"
            check $? "$tag: pacman -Syu twice, -Dk clean, no permission/GETEVENTMSG/chroot warnings" ;;
    esac

    # -- HOME lifecycle (full flavour of each edition)
    [ "$fl" = full ] || continue
    app "head -c 4096 /dev/urandom > files/linux/$distro/rootfs$HOME_SENTINEL"
    before=$(sentinel "$distro"); echo "sentinel $before" >> "$E/home-$m.txt"

    app "rm files/linux/runtime/lib/libtalloc.so.2"; relaunch
    terminal_in_front 120
    app "test -f files/linux/runtime/lib/libtalloc.so.2"; s=$?
    [ $s = 0 ] && [ "$(sentinel "$distro")" = "$before" ]; check $? "$tag: a missing runtime library is re-staged; HOME sentinel same sha256 and inode"

    app "mv files/linux/$distro/state.properties files/qa-state.properties"; relaunch
    wait_for "repair-in-place" 300; s=$?
    [ $s = 0 ] && [ "$(sentinel "$distro")" = "$before" ]; check $? "$tag: a lost state record is finished in place, no extraction; HOME kept"
    app "rm -f files/qa-state.properties"

    app "mv files/linux/$distro/rootfs/usr/bin/bash files/qa-bash"; relaunch
    wait_for "condition=INSTALLED_DAMAGED" 120; s=$?
    [ $s = 0 ] && [ "$(sentinel "$distro")" = "$before" ]; check $? "$tag: missing /usr/bin/bash = needs attention, nothing deleted"
    ask "$tag: the phone shows the 'needs attention' screen (nothing was reinstalled by itself)"
    app "mv files/qa-bash files/linux/$distro/rootfs/usr/bin/bash"; relaunch
    terminal_in_front 120; check $? "$tag: bash restored -> the terminal opens"

    app "mv files/linux/$distro/rootfs/usr/bin/bash files/qa-bash"; relaunch
    wait_for "condition=INSTALLED_DAMAGED" 120
    adb logcat -c
    action "$tag: on the 'needs attention' screen tap 'Reinstall system files', confirm, and as soon as the bar shows Extracting, run in another shell: adb shell am force-stop $PKG"
    launch
    action "$tag: the app shows 'reinstall not finished'; tap Continue"
    wait_for "Setup stage TERMINAL_READY" 900; s=$?
    after=$(sentinel "$distro"); echo "after reinstall $after" >> "$E/home-$m.txt"
    [ $s = 0 ] && [ "$after" = "$before" ]; check $? "$tag: interrupted then continued reinstall keeps HOME (same sha256 and inode)"
    app "rm -f files/qa-bash files/qa-guest.sh files/qa-fork-probe"
done

# ---- 3. package-manager gates on disposable copies -------------------------
ARCH_QA=com.thothterm.arch.qa.final
if echo "$QA_APPS" | grep -q ":$ARCH_QA:"; then
    tests/garden-arch/device/gate.sh "$ARCH_QA" "$ARCH_ARCHIVE" $STALE > "$E/arch-pacman-gate.txt" 2>&1
    check $? "Rolling pacman gate (zero, interrupted transactions, restart$( [ -n "$STALE" ] && echo ', stale upgrade'))"
    [ -n "$STALE" ] || result UNVERIFIED "Rolling stale-userland upgrade (no STALE_ARCH_USERLAND given)"
fi
tests/garden-debian/device/gate.sh "$TRIXIE_ARCHIVE" > "$E/trixie-apt-gate.txt" 2>&1
check $? "Trixie apt/dpkg gate"

# ---- 4. what only a person with a browser can check ------------------------
for t in "Ubuntu (7681)" "Trixie (7682)" "Rolling (7683)"; do
    ask "LAN $t on the QA app: start LAN Mode, pair a browser, open a terminal, sign out and in, upload a file and a folder, cancel an upload mid-way, sign out during an upload: no partial file, no .thothterm-upload-* left in the directory, the upload stops; LAN Mode off closes everything"
done
ask "Keep screen awake: with the setting on, the screen stays on only while charging and the app is in front; the manual toggle keeps it on; global screen timeout unchanged (settings get system screen_off_timeout)"
ask "Regular ThothTerm (com.thothterm), if installed, still works and has no LAN Mode (it is not touched by this gate)"

# ---- 5. protected apps untouched -------------------------------------------
inventory "$E/protected-after.txt"
diff -u "$E/protected-before.txt" "$E/protected-after.txt" > "$E/protected-diff.txt"
check $? "protected apps' install records unchanged (protected-diff.txt)"

# Not a verdict of this gate: which kernels the PRoot child tracking was seen on.
case "$KERNEL" in
    4.14.*) echo "NOTE kernel $KERNEL: the fork-probe lines above are physical 4.14 evidence" | tee -a "$SUMMARY" ;;
    *) echo "NOTE kernel $KERNEL: the F-Droid reviewer's kernel 4.14 is not covered by this run" | tee -a "$SUMMARY" ;;
esac

echo
fails=$(grep -c '^FAIL' "$SUMMARY"); unver=$(grep -c '^UNVERIFIED' "$SUMMARY"); passes=$(grep -c '^PASS' "$SUMMARY")
echo "PASS=$passes FAIL=$fails UNVERIFIED=$unver -- $SUMMARY" | tee -a "$SUMMARY"
[ "$fails" = 0 ] && [ "$unver" = 0 ]
