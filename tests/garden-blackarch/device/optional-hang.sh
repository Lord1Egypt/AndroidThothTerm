#!/bin/sh
# The BlackArch equivalent of the Ubuntu reviewer regression gate: optional setup (the
# two-keyring script) hangs; the terminal must be usable at once, never ANR, setup must run
# once at a time, time out and fail cleanly, and a retry must succeed.
#
#   optional-hang.sh QA_PACKAGE EVIDENCE_DIR
#
# QA_PACKAGE: com.thothterm.blackarch.qa.<name>, healthy and installed. The hang is made in
# THAT app's own rootfs only: pacman-key is replaced by a sleeping script and the keyring
# trust database removed (so setup is needed again); the real pacman-key is put back before
# the retry. UI taps are guarded on the QA app being in front.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/../../garden-common/extractor/apk-identity.sh"
PKG=${1:?usage: optional-hang.sh QA_PACKAGE EVIDENCE_DIR}; E=${2:?}
case "$PKG" in com.thothterm.blackarch.qa.*) ;; *) echo "REFUSED: $PKG"; exit 2 ;; esac
gate_is_qa_id "$PKG" && ! gate_is_protected "$PKG" || { echo "REFUSED: $PKG"; exit 2; }
R=files/linux/blackarch-aarch64/rootfs
mkdir -p "$E"; PASS=0; FAIL=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; PASS=$((PASS + 1)); else echo "FAIL $2 ${3:-}"; FAIL=$((FAIL + 1)); fi; }
app() { adb shell "run-as $PKG sh -c $(printf "'%s'" "$1")" | tr -d '\r'; }
log() { adb logcat -d -s ThothTerm | tr -d '\r'; }
top() { adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity' | head -1; }
now() { cut -d' ' -f1 /proc/uptime; }
guest_procs() { adb shell "run-as $PKG ps -A -o PID,ARGS" 2>/dev/null | tr -d '\r' | grep -c 'libproot'; }
no_anr() { ! adb logcat -d -b events | tr -d '\r' | grep am_anr | grep -q "$PKG"; }
screen_has() { adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1; adb shell cat /data/local/tmp/qa-ui.xml | grep -qiF "$1"; }
ui_tap() { # ui_tap TEXT-or-desc (case-insensitive), only while the QA app is in front
    top | grep -q "$PKG/" || { echo "REFUSED to tap '$1': $(top)"; return 1; }
    adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1
    adb shell cat /data/local/tmp/qa-ui.xml > "$E/ui-last.xml"
    xy=$(python3 - "$E/ui-last.xml" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for m in re.finditer(r'<node([^>]*)>', xml):
    a = dict(re.findall(r'([\w-]+)="([^"]*)"', m.group(1)))
    if sys.argv[2].casefold() in (a.get("text", "").casefold(), a.get("content-desc", "").casefold()):
        b = re.findall(r'\d+', a["bounds"]); print((int(b[0]) + int(b[2])) // 2, (int(b[1]) + int(b[3])) // 2); break
PY
)
    [ -n "$xy" ] || { echo "no '$1' on screen"; return 1; }
    top | grep -q "$PKG/" || return 1
    adb shell input tap $xy
}
# 1. make the hang (this app's rootfs only)
app "cp $R/usr/bin/pacman-key files/qa-pacman-key.orig && printf '#!/bin/bash\nsleep 1000\n' > $R/usr/bin/pacman-key && chmod 755 $R/usr/bin/pacman-key && rm -f $R/etc/pacman.d/gnupg/trustdb.gpg"
app "head -c 1 $R/usr/bin/pacman-key; test ! -e $R/etc/pacman.d/gnupg/trustdb.gpg && echo setup-needed" | grep -q setup-needed; ok $? "setup is needed again and pacman-key now hangs"
adb shell am force-stop "$PKG"; adb logcat -c
T0=$(now); adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
i=0; while [ $i -lt 60 ]; do top | grep -q "$PKG/.*TermActivity" && break; sleep 1; i=$((i + 1)); done
T1=$(now); echo "terminal in front after about $(python3 -c "print(round($T1-$T0,1))") s (wall clock incl. adb)"
top | grep -q "$PKG/.*TermActivity"; ok $? "the terminal opens while optional setup hangs (TermActivity in front)"
log | grep -q 'Setup stage TERMINAL_READY'; ok $? "TERMINAL_READY was reached before optional setup finished"
log | grep -q 'OPTIONAL_SETUP_STARTED'; ok $? "optional setup started in the background"
sleep 10
# the terminal stays usable: type and read back
adb shell input text "echo%sstill-responsive-$$"; adb shell input keyevent KEYCODE_ENTER; sleep 2
adb exec-out screencap -p > "$E/hang-terminal-responsive.png"
adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1; adb shell cat /data/local/tmp/qa-ui.xml | grep -q "still-responsive-$$"; ok $? "the terminal answers while setup hangs (typed command echoed back)"
no_anr; ok $? "no ANR while setup hangs"
# single-flight: ask for setup again while one is running
S1=$(log | grep -c 'OPTIONAL_SETUP_STARTED'); P1=$(guest_procs)
adb shell am start -n "$PKG/com.thothterm.TermActivity" >/dev/null 2>&1; sleep 4
ui_tap "More options" && sleep 1; screen_has "Retry administrator tools setup" && ui_tap "Retry administrator tools setup"; sleep 6
S2=$(log | grep -c 'OPTIONAL_SETUP_STARTED'); P2=$(guest_procs)
[ "$S1" = 1 ] && [ "$S2" = 1 ]; ok $? "single-flight: asking again while it runs starts no second setup (started: $S1 then $S2)"
[ "$P2" -le "$P1" ] || [ "$P2" -le 3 ]; ok $? "no duplicate setup process (guest processes $P1 then $P2)"
# wait for the timeout (180 s)
i=0; while [ $i -lt 240 ]; do log | grep -qE 'OPTIONAL_SETUP_(FAILED|FINISHED)' && break; sleep 5; i=$((i + 5)); done
log | grep -E 'OPTIONAL_SETUP_FAILED' | tail -1 | cut -c1-260 | tee "$E/hang-failed-line.txt"
grep -q 'OPTIONAL_SETUP_FAILED' "$E/hang-failed-line.txt"; ok $? "the hung setup fails cleanly (OPTIONAL_SETUP_FAILED after ~$i s)"
log | grep -qiE 'timed out'; ok $? "the failure names the timeout"
sleep 3; [ "$(guest_procs)" -le 1 ]; ok $? "the hung guest process was killed (guest processes: $(guest_procs))"
top | grep -q "$PKG/"; ok $? "the app is still in front and alive after the timeout"
adb shell input text "echo%safter-timeout"; adb shell input keyevent KEYCODE_ENTER; sleep 2
adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1; adb shell cat /data/local/tmp/qa-ui.xml | grep -q "after-timeout"; ok $? "the terminal still answers after the timeout"
adb exec-out screencap -p > "$E/hang-after-timeout.png"
no_anr; ok $? "no ANR through the whole hang and timeout"
# 2. retry succeeds once the real pacman-key is back
app "cp files/qa-pacman-key.orig $R/usr/bin/pacman-key && chmod 755 $R/usr/bin/pacman-key && rm -f files/qa-pacman-key.orig"
adb logcat -c
ui_tap "More options" && sleep 1
screen_has "Retry administrator tools setup"; ok $? "the menu offers 'Retry administrator tools setup'"
ui_tap "Retry administrator tools setup"
i=0; while [ $i -lt 180 ]; do log | grep -qE 'OPTIONAL_SETUP_(FAILED|FINISHED)' && break; sleep 3; i=$((i + 3)); done
log | grep 'OPTIONAL_SETUP_FINISHED' | tail -1 | cut -c1-200 | tee "$E/retry-finished-line.txt"
grep -q 'sudo=INSTALLED state=HEALTHY' "$E/retry-finished-line.txt"; ok $? "the retry succeeds: keyring recreated, state HEALTHY"
app "test -s $R/etc/pacman.d/gnupg/trustdb.gpg && test \"\$(head -c 11 $R/usr/bin/pacman-key)\" = '#!/usr/bin/bash' -o -s $R/usr/bin/pacman-key" ; ok $? "trust database exists again and pacman-key is the real one"
no_anr; ok $? "no ANR during the retry"
echo "SUMMARY: $PASS PASS, $FAIL FAIL"; [ "$FAIL" = 0 ]
