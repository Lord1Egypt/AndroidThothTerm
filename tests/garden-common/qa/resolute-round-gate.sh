#!/bin/sh
# Targeted device gate for ThothTerm Resolute (term-ubuntu) after the Garden
# Thorns round: the removed legacy surface, terminal-first start, HOME through
# an in-place update, and the optional-setup hang -> SIGKILL timeout -> retry.
# Runs on an isolated QA build only (install-qa-app.sh term-ubuntu .qa.<name>
# fdroid), already through its first run with sudo installed.
#
#   tests/garden-common/qa/resolute-round-gate.sh QA_PACKAGE APK EVIDENCE_DIR
#
# QA_PACKAGE is com.thothterm.ubuntu.qa.<name>; APK is that QA build (used for
# the in-place update). The hang is made in that app's own rootfs only:
# apt-get becomes a sleeping script and sudo's dpkg record is removed, so
# optional setup runs and hangs; both are restored before the retry. Network
# for run-as guest commands is allowed with a temporary deviceidle entry,
# removed at the end. UI taps happen only while the QA app is in front.
# Prints PASS/FAIL lines and a summary; exits 1 on any FAIL.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/../extractor/apk-identity.sh"
PKG=${1:?usage: resolute-round-gate.sh QA_PACKAGE APK EVIDENCE_DIR}; APK=${2:?}; E=${3:?}
case "$PKG" in com.thothterm.ubuntu.qa.*) ;; *) echo "REFUSED: $PKG"; exit 2 ;; esac
gate_is_qa_id "$PKG" && ! gate_is_protected "$PKG" || { echo "REFUSED: $PKG"; exit 2; }
gate_check_app_identity "$PKG" "$APK"
R=files/linux/ubuntu-26.04/rootfs
MORE="More options|مزيد من الخيارات"   # the owner's phone runs in Arabic
mkdir -p "$E"; PASS=0; FAIL=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; PASS=$((PASS + 1)); else echo "FAIL $2 ${3:-}"; FAIL=$((FAIL + 1)); fi; }
app() { printf '%s\n' "$1" > "$E/app.sh"; adb push "$E/app.sh" /data/local/tmp/qa-app.sh >/dev/null; adb shell "run-as $PKG sh /data/local/tmp/qa-app.sh" | tr -d '\r'; }
log() { adb logcat -d -s ThothTerm | tr -d '\r'; }
top() { adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity' | head -1; }
procs() { adb shell "run-as $PKG ps -A -o PID,ARGS" 2>/dev/null | tr -d '\r'; }
no_anr() { ! adb logcat -d -b events | tr -d '\r' | grep am_anr | grep -q "$PKG"; }
screen_has() { adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1; adb shell cat /data/local/tmp/qa-ui.xml | grep -qiF "$1"; }
close_ui() {
    screen_has "Restart terminal" && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
    adb shell dumpsys input_method | grep -q 'mInputShown=true' && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
    return 0
}
ui_tap() {
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
type_marker() { # type_marker NAME: a command that writes /tmp/NAME in the guest
    adb shell "input text 'echo%s$1%s>%s/tmp/$1'"; adb shell input keyevent KEYCODE_ENTER; sleep 3
    app "cat $R/tmp/$1" | grep -q "$1"
}
open_terminal() {
    adb logcat -c
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    i=0; while [ $i -lt 90 ]; do top | grep -q "$PKG/.*TermActivity" && return 0; sleep 1; i=$((i + 1)); done
    return 1
}
adb shell dumpsys deviceidle whitelist +"$PKG" >/dev/null

# 1. The legacy surface is gone (no payload is sent: the components must not exist).
adb shell cmd package query-activities --brief -a android.intent.action.SEND -t text/plain | tr -d '\r' > "$E/send-targets.txt"
! grep -q "^$PKG/" "$E/send-targets.txt"; ok $? "no share (ACTION_SEND) target in $PKG"
for c in com.thothterm.TermHere jackpal.androidterm.RemoteInterface jackpal.androidterm.RunScript \
         jackpal.androidterm.RunShortcut com.thothterm.shortcuts.AddShortcut com.thothterm.shortcuts.FileSelection; do
    out=$(adb shell am start -W -n "$PKG/$c" 2>&1 | tr -d '\r')
    printf '%s\n' "$out" | grep -qi "does not exist\|unable to resolve\|Error type 3"; ok $? "$c cannot be started from outside"
done
out=$(adb shell am start-foreground-service -n "$PKG/jackpal.androidterm.TermService" 2>&1 | tr -d '\r')
printf '%s\n' "$out" | grep -qi "not exported\|Requires permission\|SecurityException\|Error"; ok $? "TermService cannot be started from outside ($(printf '%s' "$out" | tail -1 | cut -c1-80))"
adb shell dumpsys package "$PKG" | tr -d '\r' > "$E/dumpsys-package.txt"
! grep -q "permission.RUN_SCRIPT" "$E/dumpsys-package.txt"; ok $? "no RUN_SCRIPT permission declared"

# 2. HOME survives an in-place update; the terminal opens first.
app "mkdir -p $R/home/thoth && head -c 4096 /dev/urandom > $R/home/thoth/qa-round-sentinel && sha256sum $R/home/thoth/qa-round-sentinel && stat -c %i $R/home/thoth/qa-round-sentinel" > "$E/sentinel-before.txt"
gate_install "$APK" -r >/dev/null
open_terminal; ok $? "the terminal opens after an in-place update"
app "sha256sum $R/home/thoth/qa-round-sentinel && stat -c %i $R/home/thoth/qa-round-sentinel" > "$E/sentinel-after.txt"
cmp -s "$E/sentinel-before.txt" "$E/sentinel-after.txt"; ok $? "HOME sentinel: same SHA-256 and inode after the update"
type_marker qa-round-first; ok $? "the terminal answers (a typed command wrote its file)"

# 3. Optional setup hangs, times out, is killed, and a retry succeeds.
app "cp $R/usr/bin/apt-get files/qa-apt-get.orig && cp $R/var/lib/dpkg/status files/qa-dpkg-status.orig && printf '#!/bin/sh\nexec sleep 1000\n' > $R/usr/bin/apt-get && chmod 755 $R/usr/bin/apt-get"
app "cat $R/var/lib/dpkg/status" > "$E/status.orig"
python3 - "$E/status.orig" "$E/status.nosudo" <<'PY'
import sys
stanzas = open(sys.argv[1], encoding="utf-8").read().split("\n\n")
kept = [s for s in stanzas if not s.startswith("Package: sudo\n")]
assert len(kept) == len(stanzas) - 1, "expected one sudo stanza"
open(sys.argv[2], "w", encoding="utf-8").write("\n\n".join(kept))
PY
adb push "$E/status.nosudo" /data/local/tmp/qa-status >/dev/null
app "cat /data/local/tmp/qa-status > $R/var/lib/dpkg/status"; adb shell rm -f /data/local/tmp/qa-status
adb shell am force-stop "$PKG"
T0=$(cut -d' ' -f1 /proc/uptime)
open_terminal; ok $? "the terminal opens while optional setup will hang"
T1=$(cut -d' ' -f1 /proc/uptime)
echo "terminal in front after about $(python3 -c "print(round($T1-$T0,1))") s (wall clock incl. adb)"
sleep 8
log > "$E/hang-start-log.txt"
grep -q 'OPTIONAL_SETUP_STARTED' "$E/hang-start-log.txt"; ok $? "optional setup started in the background"
procs > "$E/procs-during.txt"; grep -q 'sleep 1000' "$E/procs-during.txt"; ok $? "the hang is real (the guest's apt-get is sleeping)"
type_marker qa-round-hang; ok $? "the terminal answers while setup hangs"
no_anr; ok $? "no ANR while setup hangs"
i=0; while [ $i -lt 260 ]; do log | grep -qE 'OPTIONAL_SETUP_(FAILED|FINISHED)' && break; sleep 5; i=$((i + 5)); done
log | grep 'OPTIONAL_SETUP_FAILED' | tail -1 | cut -c1-260 > "$E/hang-failed-line.txt"
grep -q 'OPTIONAL_SETUP_FAILED' "$E/hang-failed-line.txt" && log | grep -qi 'timed out'; ok $? "setup failed cleanly on the timeout after ~$i s"
sleep 5; procs > "$E/procs-after.txt"
! grep -q 'sleep 1000' "$E/procs-after.txt"; ok $? "nothing of the hung setup survives (no sleeping apt-get)"
[ "$(grep -c 'libproot' "$E/procs-after.txt")" -le 1 ]; ok $? "only the terminal's own PRoot is left ($(grep -c 'libproot' "$E/procs-after.txt") libproot)"
type_marker qa-round-after; ok $? "the terminal still answers after the timeout"
no_anr; ok $? "no ANR through the hang and the timeout"
log | grep -q 'must never run on the main thread'; [ $? != 0 ]; ok $? "provisioning never ran on the main thread"
app "cp files/qa-apt-get.orig $R/usr/bin/apt-get && chmod 755 $R/usr/bin/apt-get && rm -f files/qa-apt-get.orig"
adb logcat -c
close_ui; ui_tap "$MORE" && sleep 1
screen_has "Retry administrator tools setup"; ok $? "the menu offers 'Retry administrator tools setup'"
ui_tap "Retry administrator tools setup"
i=0; while [ $i -lt 300 ]; do log | grep -qE 'OPTIONAL_SETUP_(FAILED|FINISHED)' && break; sleep 3; i=$((i + 3)); done
log | grep -E 'OPTIONAL_SETUP_(FAILED|FINISHED)' | tail -1 | cut -c1-220 > "$E/retry-line.txt"
grep -q 'OPTIONAL_SETUP_FINISHED' "$E/retry-line.txt"; ok $? "the retry installs sudo from the archive"
app "grep -A1 '^Package: sudo$' $R/var/lib/dpkg/status" | grep -q 'install ok installed'; ok $? "dpkg records sudo as installed again"
no_anr; ok $? "no ANR during the retry"

# 4. Runtime identity: every PRoot process runs this QA app's own runtime.
procs > "$E/procs-final.txt"
grep 'libproot' "$E/procs-final.txt" | grep -v "/data/app/.*$PKG\|/data/data/$PKG\|/data/user/0/$PKG" | grep -q . ; [ $? != 0 ]; ok $? "every PRoot process belongs to $PKG"

app "rm -f $R/tmp/qa-round-* files/qa-dpkg-status.orig" >/dev/null 2>&1
adb shell rm -f /data/local/tmp/qa-app.sh /data/local/tmp/qa-ui.xml
adb shell dumpsys deviceidle whitelist -"$PKG" >/dev/null
echo "SUMMARY: $PASS PASS, $FAIL FAIL"; [ "$FAIL" = 0 ]
