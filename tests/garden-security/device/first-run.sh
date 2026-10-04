#!/bin/sh
# First run of an isolated Security QA build through the real UI: the consent
# screen, the pinned download, the terminal at TERMINAL_READY, then the optional
# setup in the background. Taps only while the QA app is in front.
#
#   tests/garden-security/device/first-run.sh QA_PACKAGE EVIDENCE_DIR
#
# Prints PASS/FAIL lines; exits 1 on any FAIL, 2 on a refused package.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/../../garden-common/extractor/apk-identity.sh"
PKG=${1:?usage: first-run.sh QA_PACKAGE EVIDENCE_DIR}; E=${2:?}
case "$PKG" in com.thothterm.security.qa.*) ;; *) echo "REFUSED: $PKG"; exit 2 ;; esac
gate_is_qa_id "$PKG" && ! gate_is_protected "$PKG" || { echo "REFUSED: $PKG"; exit 2; }
adb shell pm path "$PKG" | tr -d '\r' | grep -q '^package:' || { echo "REFUSED: $PKG is not installed"; exit 2; }
mkdir -p "$E"; FAIL=0
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2 ${3:-}"; FAIL=1; fi; }
top() { adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity' | head -1; }
adb shell dumpsys deviceidle whitelist +"$PKG" >/dev/null
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
i=0; while [ $i -lt 30 ]; do top | grep -q "$PKG/.*GardenSetupActivity" && break; sleep 1; i=$((i + 1)); done
top | grep -q "$PKG/.*GardenSetupActivity"; ok $? "the consent screen opens first"
sleep 3
adb shell dumpsys activity activities | grep -q "$PKG/" && adb shell input swipe 540 1800 540 700 300; sleep 1
adb shell uiautomator dump /data/local/tmp/qa-ui.xml >/dev/null 2>&1; adb shell cat /data/local/tmp/qa-ui.xml > "$E/first-run-consent.xml"; adb shell rm -f /data/local/tmp/qa-ui.xml
grep -q 'Welcome to ThothTerm Security' "$E/first-run-consent.xml"; ok $? "the welcome names ThothTerm Security"
grep -qi 'blackarch' "$E/first-run-consent.xml"; [ $? != 0 ]; ok $? "the first consent screen names no third-party repository as the product"
grep -q 'No third-party security repository is included' "$E/first-run-consent.xml"; ok $? "the first consent screen says no third-party repository is included"
xy=$(python3 -c '
import re, sys
x = open(sys.argv[1], encoding="utf-8").read()
for m in re.finditer(r"<node([^>]*)>", x):
    a = dict(re.findall(r"([\w-]+)=\"([^\"]*)\"", m.group(1)))
    if a.get("text", "").upper() == "DOWNLOAD":
        b = re.findall(r"\d+", a["bounds"]); print((int(b[0]) + int(b[2])) // 2, (int(b[1]) + int(b[3])) // 2)
' "$E/first-run-consent.xml")
[ -n "$xy" ]; ok $? "the Download button is on screen"
top | grep -q "$PKG/" && [ -n "$xy" ] && adb shell input tap $xy
i=0; reached=1; while [ $i -lt 300 ]; do top | grep -q "$PKG/.*TermActivity" && { reached=0; break; }; sleep 3; i=$((i + 3)); done
ok $reached "download, extraction, then the terminal (about $i s)"
i=0; while [ $i -lt 120 ]; do adb logcat -d -s ThothTerm | grep -qE 'OPTIONAL_SETUP_(FINISHED|FAILED)' && break; sleep 3; i=$((i + 3)); done
adb logcat -d -s ThothTerm | tr -d '\r' | grep -E 'TERMINAL_READY|OPTIONAL_SETUP' | cut -c1-200 > "$E/first-run-log.txt"
t1=$(grep -n 'Setup stage TERMINAL_READY' "$E/first-run-log.txt" | head -1 | cut -d: -f1); t2=$(grep -n 'OPTIONAL_SETUP_STARTED' "$E/first-run-log.txt" | head -1 | cut -d: -f1)
[ -n "$t1" ] && [ -n "$t2" ] && [ "$t1" -lt "$t2" ]; ok $? "TERMINAL_READY is reached before the optional setup starts"
grep -q 'OPTIONAL_SETUP_FINISHED.*sudo=INSTALLED' "$E/first-run-log.txt"; ok $? "the optional setup then finishes (sudo installed, keyring created)"
! adb logcat -b events -d | tr -d '\r' | grep am_anr | grep -q "$PKG"; ok $? "no ANR during the first run"
adb shell dumpsys deviceidle whitelist -"$PKG" >/dev/null
[ "$FAIL" = 0 ]
