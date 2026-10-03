#!/bin/sh
# HOME-preservation lifecycle gate on an isolated BlackArch QA app (the real app flows,
# not a harness): a sentinel in /home/thoth must keep its SHA-256 and inode through a
# normal restart, a missing runtime library, a lost state record, a damaged rootfs
# (nothing is deleted), and an interrupted-then-continued reinstall.
#
#   lifecycle.sh QA_PACKAGE EVIDENCE_DIR [CASE...]
#
# CASE: 1-5 (default: all). The HOME sentinel is always created and read back first.
# UI text is matched case-insensitively: Material buttons render their labels in
# capitals ("CONTINUE REINSTALL"), which a case-sensitive match never finds (the
# stall of 2026-10-03).
#
# QA_PACKAGE must be com.thothterm.blackarch.qa.<name> and installed. The reinstall
# steps drive the app's own screens with guarded taps: a tap is sent only while the QA
# app is the top resumed activity and the expected text is on the screen.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/../../garden-common/extractor/apk-identity.sh"
PKG=${1:?usage: lifecycle.sh QA_PACKAGE EVIDENCE_DIR}
E=${2:?usage: lifecycle.sh QA_PACKAGE EVIDENCE_DIR}
shift 2
CASES=${*:-1 2 3 4 5}
want() { case " $CASES " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }
case "$PKG" in com.thothterm.blackarch.qa.*) ;; *) echo "REFUSED: $PKG"; exit 2 ;; esac
gate_is_qa_id "$PKG" && ! gate_is_protected "$PKG" || { echo "REFUSED: $PKG"; exit 2; }
adb shell pm path "$PKG" | tr -d '\r' | grep -q '^package:' || { echo "REFUSED: $PKG is not installed"; exit 2; }
D=blackarch-aarch64
R=files/linux/$D
mkdir -p "$E"
PASS=0; FAIL=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; PASS=$((PASS + 1)); else echo "FAIL $2 ${3:-}"; FAIL=$((FAIL + 1)); fi; }
app() { adb shell "run-as $PKG sh -c $(printf "'%s'" "$1")" | tr -d '\r'; }
sentinel() { # "<sha256> <inode>" of the HOME sentinel, or "missing" (never an empty answer)
    h=$(adb shell "run-as $PKG sha256sum $R/rootfs/home/thoth/.qa-sentinel 2>/dev/null" | tr -d '\r' | cut -d' ' -f1)
    i=$(adb shell "run-as $PKG stat -c %i $R/rootfs/home/thoth/.qa-sentinel 2>/dev/null" | tr -d '\r')
    if printf '%s' "$h" | grep -Eq '^[0-9a-f]{64}$' && printf '%s' "$i" | grep -Eq '^[0-9]+$'; then echo "$h $i"; else echo missing; fi
}
log() { adb logcat -d -s ThothTerm | tr -d '\r'; }
wait_for() { i=0; while [ $i -lt "$2" ]; do log | grep -q "$1" && return 0; sleep 2; i=$((i + 2)); done; return 1; }
launch() { adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }
relaunch() { adb shell am force-stop "$PKG"; adb logcat -c; launch; }
top() { adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity' | head -1; }
terminal_in_front() { i=0; while [ $i -lt "$1" ]; do top | grep -q "$PKG/.*TermActivity" && return 0; sleep 2; i=$((i + 2)); done; return 1; }
no_anr() { ! adb logcat -d -b events | tr -d '\r' | grep am_anr | grep -q "$PKG"; }
# ui_tap TEXT: tap the node whose text equals TEXT, only while the QA app is in front.
ui_tap() {
    top | grep -q "$PKG/" || { echo "REFUSED to tap '$1': $PKG is not in front ($(top))"; return 1; }
    adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1
    adb shell cat /data/local/tmp/qa-ui.xml > "$E/ui-last.xml"
    xy=$(python3 - "$E/ui-last.xml" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for m in re.finditer(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if m.group(1).casefold() == sys.argv[2].casefold():
        print((int(m.group(2)) + int(m.group(4))) // 2, (int(m.group(3)) + int(m.group(5))) // 2)
        break
PY
)
    [ -n "$xy" ] || { echo "no '$1' on screen"; return 1; }
    top | grep -q "$PKG/" || return 1
    adb shell input tap $xy
}
screen_has() { adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1; adb shell cat /data/local/tmp/qa-ui.xml | grep -qiF "$1"; }
wait_screen() { i=0; while [ $i -lt "$2" ]; do screen_has "$1" && return 0; sleep 2; i=$((i + 2)); done; return 1; }

app "head -c 4096 /dev/urandom > $R/rootfs/home/thoth/.qa-sentinel"
BEFORE=$(sentinel); echo "sentinel $BEFORE" | tee "$E/home-sentinel.txt"
printf '%s' "$BEFORE" | grep -Eq '^[0-9a-f]{64} [0-9]+$'; ok $? "HOME sentinel created (sha256 and inode recorded: $BEFORE)"
[ "$(sentinel)" = "$BEFORE" ] && [ "$BEFORE" != missing ] || { echo "ABORT: the sentinel cannot be read back; every later comparison would be vacuous"; exit 3; }

if want 1; then
relaunch; terminal_in_front 120; s=$?
[ $s = 0 ] && [ "$(sentinel)" = "$BEFORE" ] && no_anr; ok $? "1 normal restart: the terminal opens, HOME sentinel same sha256 and inode, no ANR"

fi
if want 2; then
app "rm $R/../runtime/lib/libtalloc.so.2"; relaunch; terminal_in_front 120
app "test -f files/linux/runtime/lib/libtalloc.so.2"; s=$?
[ $s = 0 ] && [ "$(sentinel)" = "$BEFORE" ] && ! log | grep -q 'Extraction started'; ok $? "2 a missing runtime library is re-staged from the APK; no extraction; HOME same"

fi
if want 3; then
app "mv $R/state.properties files/qa-state.properties"; relaunch
wait_for "repair-in-place" 300; s=$?
[ $s = 0 ] && [ "$(sentinel)" = "$BEFORE" ] && ! log | grep -q 'Extraction started'; ok $? "3 a lost state record is finished in place, no extraction; HOME same"
app "rm -f files/qa-state.properties"
terminal_in_front 60

fi
if want 4; then
app "mv $R/rootfs/usr/bin/bash files/qa-bash"; relaunch
wait_for "condition=INSTALLED_DAMAGED" 120; s=$?
[ $s = 0 ] && [ "$(sentinel)" = "$BEFORE" ] && ! log | grep -q 'Extraction started'; ok $? "4 missing /usr/bin/bash = INSTALLED_DAMAGED, nothing deleted or reinstalled by itself, HOME same"
wait_screen "Your Linux environment needs attention." 30; ok $? "4 the needs-attention screen is shown"
app "mv files/qa-bash $R/rootfs/usr/bin/bash"; relaunch
terminal_in_front 120; ok $? "4 bash restored: the terminal opens again"

fi
if want 5; then
# interrupted reinstall, with the app's own dialogs
app "mv $R/rootfs/usr/bin/bash files/qa-bash"; relaunch
wait_for "condition=INSTALLED_DAMAGED" 120
wait_screen "Reinstall system files" 30; ok $? "5 the reinstall action is offered"
ui_tap "Reinstall system files"; sleep 2
wait_screen "Reinstall, keep /home" 20; ok $? "5 the confirmation dialog says /home is kept"
ui_tap "Reinstall, keep /home"
wait_for "Extraction started" 120; ok $? "5 the reinstall extracts a new system next to the old one"
sleep 6
adb shell am force-stop "$PKG"; echo "killed the app during the reinstall at $(date +%T)"
app "ls -d $R/rootfs $R/rootfs.previous $R/rootfs.staging $R/reset.requested 2>&1" > "$E/home-after-kill.txt"; cat "$E/home-after-kill.txt"
adb logcat -c; launch
wait_screen "A system reinstall was started but not finished." 90; ok $? "5 after the kill the app says the reinstall was not finished"
ui_tap "Continue reinstall"
wait_for "Setup stage TERMINAL_READY" 600; s=$?
AFTER=$(sentinel); echo "after reinstall $AFTER" >> "$E/home-sentinel.txt"
[ $s = 0 ] && [ "$AFTER" = "$BEFORE" ]; ok $? "5 interrupted then continued reinstall keeps HOME (same sha256 and inode)"
app "test -x $R/rootfs/usr/bin/bash && test ! -e $R/rootfs.previous && test ! -e $R/rootfs.staging"; ok $? "5 the new system is complete and no previous/staging directory is left"
fi
no_anr; ok $? "no ANR in the final launch"
echo "SUMMARY: $PASS PASS, $FAIL FAIL"
[ "$FAIL" = 0 ]
