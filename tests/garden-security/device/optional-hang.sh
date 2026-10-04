#!/bin/sh
# The Security equivalent of the Ubuntu reviewer regression gate: optional setup (the
# two-keyring script) hangs; the terminal must be usable at once, never ANR, setup must run
# once at a time, time out and fail cleanly, and a retry must succeed.
#
#   optional-hang.sh QA_PACKAGE EVIDENCE_DIR
#
# QA_PACKAGE: com.thothterm.security.qa.<name>, healthy and installed. The hang is made in
# THAT app's own rootfs only: pacman-key is replaced by a sleeping script and the keyring
# trust database removed (so setup is needed again); the real pacman-key is put back before
# the retry. UI taps are guarded on the QA app being in front.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/../../garden-common/extractor/apk-identity.sh"
PKG=${1:?usage: optional-hang.sh QA_PACKAGE EVIDENCE_DIR}; E=${2:?}
case "$PKG" in com.thothterm.security.qa.*) ;; *) echo "REFUSED: $PKG"; exit 2 ;; esac
gate_is_qa_id "$PKG" && ! gate_is_protected "$PKG" || { echo "REFUSED: $PKG"; exit 2; }
R=files/linux/security-aarch64/rootfs
MORE="More options|مزيد من الخيارات"   # the owner's phone runs in Arabic
mkdir -p "$E"; PASS=0; FAIL=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; PASS=$((PASS + 1)); else echo "FAIL $2 ${3:-}"; FAIL=$((FAIL + 1)); fi; }
app() { printf '%s\n' "$1" > "$E/app.sh"; adb push "$E/app.sh" /data/local/tmp/qa-app.sh >/dev/null; adb shell "run-as $PKG sh /data/local/tmp/qa-app.sh" | tr -d '\r'; }
log() { adb logcat -d -s ThothTerm | tr -d '\r'; }
top() { adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity' | head -1; }
now() { cut -d' ' -f1 /proc/uptime; }
guest_procs() { adb shell "run-as $PKG ps -A -o PID,ARGS" 2>/dev/null | tr -d '\r' | grep -c 'libproot'; }
no_anr() { ! adb logcat -d -b events | tr -d '\r' | grep am_anr | grep -q "$PKG"; }
screen_has() { adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1; adb shell cat /data/local/tmp/qa-ui.xml | grep -qiF "$1"; }
close_ui() { # close an open overflow menu, then the soft keyboard, so the whole menu is on screen
    screen_has "Restart terminal" && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
    adb shell dumpsys input_method | grep -q 'mInputShown=true' && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
    return 0
}
ui_tap() { # ui_tap TEXT-or-desc (case-insensitive), only while the QA app is in front
    top | grep -q "$PKG/" || { echo "REFUSED to tap '$1': $(top)"; return 1; }
    adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1
    adb shell cat /data/local/tmp/qa-ui.xml > "$E/ui-last.xml"
    xy=$(python3 - "$E/ui-last.xml" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for m in re.finditer(r'<node([^>]*)>', xml):
    a = dict(re.findall(r'([\w-]+)="([^"]*)"', m.group(1)))
    if any(t.casefold() in (a.get("text", "").casefold(), a.get("content-desc", "").casefold()) for t in sys.argv[2].split("|")):
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
log | grep -q 'Optional setup queued in the background (session start)'; ok $? "optional setup was queued in the background, not in front of the terminal"
log | grep -q 'OPTIONAL_SETUP_STARTED'; ok $? "optional setup started in the background"
sleep 10
# the terminal stays usable: type and read back
# (the terminal view is a canvas, not in the accessibility tree: the proof is a file the typed command makes)
adb shell "input text 'echo%sresponsive%s>%s/tmp/qa-resp-$$'"; adb shell input keyevent KEYCODE_ENTER; sleep 3
adb exec-out screencap -p > "$E/hang-terminal-responsive.png"
app "cat $R/tmp/qa-resp-$$" | grep -q responsive; ok $? "the terminal answers while setup hangs (a typed command ran and wrote its file)"
no_anr; ok $? "no ANR while setup hangs"
# single-flight: ask for setup again while one is running
S1=$(log | grep -c 'OPTIONAL_SETUP_STARTED'); P1=$(guest_procs)
adb shell am start -n "$PKG/com.thothterm.TermActivity" >/dev/null 2>&1; sleep 4
close_ui; ui_tap "$MORE" && sleep 1; screen_has "Retry administrator tools setup" && ui_tap "Retry administrator tools setup"; sleep 6
close_ui
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
adb shell "input text 'echo%safter%s>%s/tmp/qa-after-$$'"; adb shell input keyevent KEYCODE_ENTER; sleep 3
app "cat $R/tmp/qa-after-$$" | grep -q after; ok $? "the terminal still answers after the timeout"
adb exec-out screencap -p > "$E/hang-after-timeout.png"
no_anr; ok $? "no ANR through the whole hang and timeout"
# 2. retry succeeds once the real pacman-key is back
app "cp files/qa-pacman-key.orig $R/usr/bin/pacman-key && chmod 755 $R/usr/bin/pacman-key && rm -f files/qa-pacman-key.orig"
adb logcat -c
close_ui; ui_tap "$MORE" && sleep 1
screen_has "Retry administrator tools setup"; ok $? "the menu offers 'Retry administrator tools setup'"
ui_tap "Retry administrator tools setup"
i=0; while [ $i -lt 180 ]; do log | grep -qE 'OPTIONAL_SETUP_(FAILED|FINISHED)' && break; sleep 3; i=$((i + 3)); done
log | grep 'OPTIONAL_SETUP_FINISHED' | tail -1 | cut -c1-200 | tee "$E/retry-finished-line.txt"
grep -q 'sudo=INSTALLED state=HEALTHY' "$E/retry-finished-line.txt"; ok $? "the retry succeeds: keyring recreated, state HEALTHY"
app "test -s $R/etc/pacman.d/gnupg/trustdb.gpg && ! grep -q 'sleep 1000' $R/usr/bin/pacman-key && head -c 2 $R/usr/bin/pacman-key | grep -q . && echo trust-and-real-pacman-key"| grep -q trust-and-real-pacman-key; ok $? "trust database exists again and pacman-key is the real one"
no_anr; ok $? "no ANR during the retry"
app "rm -f $R/tmp/qa-resp-* $R/tmp/qa-after-*" >/dev/null 2>&1
adb shell rm -f /data/local/tmp/qa-app.sh /data/local/tmp/qa-ui.xml
echo "SUMMARY: $PASS PASS, $FAIL FAIL"; [ "$FAIL" = 0 ]
