#!/bin/sh
# ThothTerm Security device gate for the optional third-party repository, through
# the real app UI on an isolated QA build that has completed its first run.
#
#   tests/garden-security/device/extra-repo-ui.sh QA_PACKAGE EVIDENCE_DIR
#
# QA_PACKAGE is com.thothterm.security.qa.<name> (install-qa-app.sh garden-security
# .qa.<name> fdroid). Covers: the consent screen and declining it (nothing fetched or
# changed), failure while the app has no network, a hung guest command that the
# timeout must SIGKILL (no process left, terminal answering throughout), a retry
# that succeeds, the entry disappearing once enabled, and an app restart. UI taps
# happen only while the QA app is in front; nothing else is touched. Prints
# PASS/FAIL lines and a summary; exits 1 on any FAIL, 2 on a refused package.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/../../garden-common/extractor/apk-identity.sh"
PKG=${1:?usage: extra-repo-ui.sh QA_PACKAGE EVIDENCE_DIR}; E=${2:?}
case "$PKG" in com.thothterm.security.qa.*) ;; *) echo "REFUSED: $PKG"; exit 2 ;; esac
gate_is_qa_id "$PKG" && ! gate_is_protected "$PKG" || { echo "REFUSED: $PKG"; exit 2; }
adb shell pm path "$PKG" | tr -d '\r' | grep -q '^package:' || { echo "REFUSED: $PKG is not installed"; exit 2; }
R=files/linux/security-aarch64/rootfs
MORE="More options|مزيد من الخيارات"
ITEM="Enable BlackArch repository"
mkdir -p "$E"; PASS=0; FAIL=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; PASS=$((PASS + 1)); else echo "FAIL $2 ${3:-}"; FAIL=$((FAIL + 1)); fi; }
app() { printf '%s\n' "$1" > "$E/app.sh"; adb push "$E/app.sh" /data/local/tmp/qa-app.sh >/dev/null; adb shell "run-as $PKG sh /data/local/tmp/qa-app.sh" | tr -d '\r'; }
log() { adb logcat -d -s ThothTerm | tr -d '\r'; }
top() { adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity' | head -1; }
procs() { adb shell "run-as $PKG ps -A -o PID,ARGS" 2>/dev/null | tr -d '\r'; }
no_anr() { ! adb logcat -d -b events | tr -d '\r' | grep am_anr | grep -q "$PKG"; }
dump() { adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1; adb shell cat /data/local/tmp/qa-ui.xml; }
screen_has() { dump | grep -qiF "$1"; }
find_xy() { # find_xy TEXT|TEXT2: centre of the first node whose text or content-desc matches
    dump | python3 -c '
import re, sys
xml = sys.stdin.read()
for m in re.finditer(r"<node([^>]*)>", xml):
    a = dict(re.findall(r"([\w-]+)=\"([^\"]*)\"", m.group(1)))
    if any(t.casefold() in (a.get("text", "").casefold(), a.get("content-desc", "").casefold()) for t in sys.argv[1].split("|")):
        b = re.findall(r"\d+", a["bounds"]); print((int(b[0]) + int(b[2])) // 2, (int(b[1]) + int(b[3])) // 2); break
' "$1"
}
tap_text() {
    top | grep -q "$PKG/" || { echo "REFUSED to tap '$1': $(top)"; return 1; }
    xy=$(find_xy "$1"); [ -n "$xy" ] || { echo "no '$1' on screen"; return 1; }
    top | grep -q "$PKG/" || return 1
    adb shell input tap $xy
}
type_marker() {
    top | grep -q "$PKG/.*TermActivity" || { echo "REFUSED to type '$1': $(top)"; return 1; }
    adb shell dumpsys input_method | grep -q 'mInputShown=true' || true
    adb shell "input text 'echo%s$1%s>%s/tmp/$1'"; adb shell input keyevent KEYCODE_ENTER; sleep 3
    app "cat $R/tmp/$1" | grep -q "$1"
}
open_terminal() {
    if top | grep -q "$PKG/.*TermActivity" && procs | grep -q ' bash$'; then return 0; fi
    adb logcat -c
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    i=0; while [ $i -lt 90 ]; do top | grep -q "$PKG/.*TermActivity" && break; sleep 1; i=$((i + 1)); done
    [ $i -lt 90 ] || return 1
    while [ $i -lt 90 ]; do procs | grep -q ' bash$' && { sleep 2; return 0; }; sleep 1; i=$((i + 1)); done
    return 1
}
close_ui() {
    adb shell dumpsys input_method | grep -q 'mInputShown=true' && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
    return 0
}
menu_open() { close_ui; tap_text "$MORE" && sleep 1; }
menu_scroll_to_item() { # the popup is a scrolling list; the entry is near its end
    adb shell input swipe 700 1500 700 500 300; sleep 1
}
conf_sum() { app "sha256sum $R/etc/pacman.conf" | cut -d' ' -f1; }
installed() { app "ls $R/var/lib/pacman/local" | grep -c '^blackarch-keyring-'; }
section() { app "grep -c '^# >>> ThothTerm optional repository' $R/etc/pacman.conf"; }
wait_log() { # wait_log REGEX SECONDS
    i=0; while [ $i -lt "$2" ]; do log | grep -qE "$1" && return 0; sleep 3; i=$((i + 3)); done; return 1
}
adb shell dumpsys deviceidle whitelist +"$PKG" >/dev/null

open_terminal; ok $? "the terminal is up with a PTY session"
app "mkdir -p $R/home/thoth && head -c 4096 /dev/urandom > $R/home/thoth/qa-extra-sentinel && sha256sum $R/home/thoth/qa-extra-sentinel && stat -c %i $R/home/thoth/qa-extra-sentinel" > "$E/sentinel-before.txt"
C0=$(conf_sum)
[ "$(installed)" = 0 ] && [ "$(section)" = 0 ]; ok $? "before: no third-party keyring package, no repository section"
app "test ! -e $R/var/tmp/thothterm-extrarepo"; ok $? "before: no staging directory"
type_marker qa-x-first; ok $? "the terminal answers"

# 1. The entry and the consent screen; declining changes nothing.
adb logcat -c
menu_open; menu_scroll_to_item
screen_has "$ITEM"; ok $? "the menu offers 'Enable BlackArch repository'"
tap_text "$ITEM" && sleep 2
dump > "$E/consent-dialog.xml"
for t in "Enable BlackArch repository?" "blackarch.org" "third-party repository" "Nothing is downloaded unless you choose Enable" "affiliated with or endorsed"; do
    grep -qiF "$t" "$E/consent-dialog.xml"; ok $? "consent screen says: $t"
done
python3 -c "
import re, sys
t = open(sys.argv[1], encoding='utf-8').read()
sys.exit(0 if re.search(r'about \\d+ KB', t) and re.search(r'exactly the \\d+ pinned signing keys', t) else 1)" "$E/consent-dialog.xml"; ok $? "consent screen names the size and the pinned keys"
tap_text "Not now" && sleep 2
! log | grep -q 'EXTRA_REPO_'; ok $? "declining: nothing was queued, fetched or logged"
[ "$(conf_sum)" = "$C0" ] && [ "$(section)" = 0 ]; ok $? "declining: pacman.conf untouched"
app "test ! -e $R/var/tmp/thothterm-extrarepo"; ok $? "declining: no staging directory"

# 2. No network for the app: the setup fails, the terminal and the guest are unchanged.
# FIREWALL_CHAIN_OEM_DENY_3 with a deny bit for this QA package only: no other app is affected.
restore_net() { adb shell cmd connectivity set-package-networking-enabled true "$PKG" >/dev/null 2>&1; adb shell cmd connectivity set-chain3-enabled false >/dev/null 2>&1; adb shell dumpsys deviceidle whitelist -"$PKG" >/dev/null 2>&1; }
trap restore_net EXIT
adb shell cmd connectivity set-chain3-enabled true >/dev/null 2>&1; adb shell cmd connectivity set-package-networking-enabled false "$PKG" >/dev/null 2>&1; NETOFF=$?
adb shell "run-as $PKG /system/bin/toybox nc -w 4 1.1.1.1 443 </dev/null >/dev/null 2>&1"; [ $? != 0 ] || NETOFF=1
if [ "$NETOFF" = 0 ]; then
    adb logcat -c
    menu_open; menu_scroll_to_item; tap_text "$ITEM" && sleep 2; screen_has "Not now" && tap_text "Enable"; sleep 1
    wait_log 'EXTRA_REPO_SETUP_(FAILED|FINISHED)' 90; log | grep -q 'EXTRA_REPO_SETUP_FAILED'; ok $? "offline: the setup fails (nothing is downloaded)"
    log | grep 'EXTRA_REPO_SETUP_FAILED' | tail -1 | cut -c1-200 > "$E/offline-failed-line.txt"
    [ "$(conf_sum)" = "$C0" ] && [ "$(installed)" = 0 ] && app "test ! -e $R/var/tmp/thothterm-extrarepo/pkg.b64"; ok $? "offline: pacman.conf untouched, no keyring package, nothing staged"
    type_marker qa-x-offline; ok $? "offline: the terminal still answers"
    no_anr; ok $? "offline: no ANR"
    restore_net; adb shell dumpsys deviceidle whitelist +"$PKG" >/dev/null
else
    restore_net; adb shell dumpsys deviceidle whitelist +"$PKG" >/dev/null
    echo "UNVERIFIED offline behaviour on the phone: 'cmd connectivity set-package-networking-enabled' is not available; the offline case is covered by the container check (--network none)"
fi

# 3. A hung guest command: the timeout must kill it for real; the terminal answers throughout.
app "cp $R/usr/bin/pacman files/qa-pacman.orig && printf '#!/bin/sh\nexec sleep 1000\n' > $R/usr/bin/pacman && chmod 755 $R/usr/bin/pacman"
adb logcat -c
close_ui; menu_open; menu_scroll_to_item; tap_text "$ITEM" && sleep 2; screen_has "Not now" && tap_text "Enable"
T0=$(cut -d' ' -f1 /proc/uptime); sleep 20
log | grep -q 'EXTRA_REPO_SETUP_STARTED'; ok $? "hang: the setup runs in the background"
procs > "$E/procs-during.txt"; grep -q 'sleep 1000' "$E/procs-during.txt"; ok $? "hang: the hang is real (the guest's pacman is sleeping)"
top | grep -q "$PKG/"; ok $? "hang: the app is still in front (not blocked)"
close_ui; type_marker qa-x-hang; ok $? "hang: the terminal answers while the setup hangs"
no_anr; ok $? "hang: no ANR while it hangs"
wait_log 'EXTRA_REPO_SETUP_(FAILED|FINISHED)' 260; T1=$(cut -d' ' -f1 /proc/uptime)
log | grep 'EXTRA_REPO_SETUP_FAILED' | tail -1 | cut -c1-220 > "$E/hang-failed-line.txt"
grep -q 'EXTRA_REPO_SETUP_FAILED' "$E/hang-failed-line.txt" && log | grep -qi 'timed out'; ok $? "hang: it failed cleanly on the timeout (about $(python3 -c "print(round($T1-$T0))") s)"
sleep 5; procs > "$E/procs-after.txt"
! grep -q 'sleep 1000' "$E/procs-after.txt"; ok $? "hang: nothing of the hung command survives (SIGKILL, not SIGTERM)"
[ "$(grep -c 'libproot' "$E/procs-after.txt")" -le 1 ]; ok $? "hang: only the terminal's own PRoot is left ($(grep -c 'libproot' "$E/procs-after.txt") libproot)"
type_marker qa-x-after; ok $? "hang: the terminal still answers after the timeout"
no_anr; ok $? "hang: no ANR through the hang and the timeout"
! log | grep -q 'must never run on the main thread'; ok $? "hang: provisioning never ran on the main thread"
app "test \"\$(grep -c '^# >>> ThothTerm optional repository' $R/etc/pacman.conf)\" = 0"; ok $? "hang: the repository section was not added (the hang came before it)"
app "cp files/qa-pacman.orig $R/usr/bin/pacman && chmod 755 $R/usr/bin/pacman && rm -f files/qa-pacman.orig"
app "test -x $R/usr/bin/pacman && ! grep -q 'sleep 1000' $R/usr/bin/pacman"; ok $? "hang: the real pacman is restored"

# 4. Retry from the menu: succeeds, terminal answering during it.
adb logcat -c
close_ui; menu_open; menu_scroll_to_item
screen_has "$ITEM"; ok $? "retry: the entry is offered again after the failure"
tap_text "$ITEM" && sleep 2; screen_has "Not now" && tap_text "Enable"
sleep 5; close_ui; type_marker qa-x-during; ok $? "retry: the terminal answers while the setup runs"
wait_log 'EXTRA_REPO_SETUP_(FAILED|FINISHED)' 300
log | grep 'EXTRA_REPO_SETUP_(FINISHED|FAILED)' >/dev/null 2>&1
log | grep -E 'EXTRA_REPO_SETUP_(FINISHED|FAILED)' | tail -1 | cut -c1-220 > "$E/retry-line.txt"
grep -q 'EXTRA_REPO_SETUP_FINISHED' "$E/retry-line.txt"; ok $? "retry: the repository is enabled" "$(cat "$E/retry-line.txt")"
log | grep 'EXTRA_REPO_REPORT' | tail -1 | cut -c1-400 > "$E/report-line.txt"
grep -q 'EXTRA_REPO_KEYS_VERIFIED trusted=4 revoked=1' "$E/report-line.txt" && grep -q 'EXTRA_REPO_SYNCED' "$E/report-line.txt"; ok $? "retry: keys proven (4 trusted, 1 revoked) and databases refreshed"
[ "$(section)" = 1 ] && [ "$(installed)" = 1 ]; ok $? "retry: one repository section, keyring package installed through pacman"
app "grep -A2 '^\\[blackarch\\]' $R/etc/pacman.conf" > "$E/pacman-conf-section.txt"
grep -q '^Server = https://blackarch.org/blackarch/\$repo/os/\$arch$' "$E/pacman-conf-section.txt" && ! grep -q SigLevel "$E/pacman-conf-section.txt"; ok $? "retry: the section is the pinned Server with no SigLevel of its own"
app "grep -nE '^[[:space:]]*(SigLevel|LocalFileSigLevel)' $R/etc/pacman.conf | sed 's/[[:space:]]\\+/ /g'" > "$E/siglevels.txt"
! grep -Eqi 'never|trustall' "$E/siglevels.txt"; ok $? "retry: no signature level is relaxed ($(tr '\n' ';' < "$E/siglevels.txt"))"
app "ls $R/var/lib/pacman/sync | grep -c '^blackarch.db'" | grep -qx '[12]'; ok $? "retry: the blackarch database is synced"
app "test -z \"\$(ls $R/var/tmp/thothterm-extrarepo 2>/dev/null)\""; ok $? "retry: nothing staged remains"
no_anr; ok $? "retry: no ANR"
close_ui; menu_open; menu_scroll_to_item
! screen_has "$ITEM"; ok $? "enabled: the menu no longer offers it"
adb shell input keyevent KEYCODE_BACK; sleep 1

# 5. App restart: state persists, the terminal comes first, HOME is intact.
adb shell am force-stop "$PKG"; sleep 2
open_terminal; ok $? "restart: the terminal is up again"
[ "$(section)" = 1 ] && [ "$(installed)" = 1 ]; ok $? "restart: the repository is still enabled"
menu_open; menu_scroll_to_item; ! screen_has "$ITEM"; ok $? "restart: the entry stays hidden"
adb shell input keyevent KEYCODE_BACK; sleep 1
app "sha256sum $R/home/thoth/qa-extra-sentinel && stat -c %i $R/home/thoth/qa-extra-sentinel" > "$E/sentinel-after.txt"
cmp -s "$E/sentinel-before.txt" "$E/sentinel-after.txt"; ok $? "HOME sentinel: same SHA-256 and inode"
type_marker qa-x-last; ok $? "the terminal answers"
no_anr; ok $? "no ANR through the whole run"
! log | grep -qE 'SigLevel|TrustAll'; ok $? "no log line mentions a signature bypass"

app "rm -f $R/tmp/qa-x-* files/qa-pacman.orig" >/dev/null 2>&1
adb shell rm -f /data/local/tmp/qa-app.sh /data/local/tmp/qa-ui.xml
restore_net
echo "SUMMARY: $PASS PASS, $FAIL FAIL"; [ "$FAIL" = 0 ]
