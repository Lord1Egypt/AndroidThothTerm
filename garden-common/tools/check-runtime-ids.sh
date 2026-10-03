#!/bin/sh
#
# check-runtime-ids.sh APPLICATION_ID FILE [REQUIRED_STRING]
#
# Proves a native runtime file was built for APPLICATION_ID and for no other
# ThothTerm package:
#   - every compiled-in /data/data/com.thothterm*/ path names APPLICATION_ID;
#   - no other protected ThothTerm application id appears in it at all (a QA
#     build, com.thothterm.arch.qa.<name>, must not carry com.thothterm.arch);
#   - REQUIRED_STRING, when given, is present (for example the PRoot loader
#     path /data/data/<APPLICATION_ID>/files/linux/runtime/loader).
#
# Run by build-proot.sh (garden-common and term-ubuntu) on every runtime it
# builds, and by tests/garden-common/extractor/device-gate.sh on the files
# inside a built APK. Exit 0 on success, 1 with a reason otherwise. POSIX sh,
# grep -a and sort only.
set -eu

PROTECTED="com.thothterm com.thothterm.devel com.thothterm.ubuntu com.thothterm.debian com.thothterm.arch com.thothterm.blackarch"

fail() {
    echo "check-runtime-ids: $*" >&2
    exit 1
}

[ $# -ge 2 ] || fail "usage: check-runtime-ids.sh APPLICATION_ID FILE [REQUIRED_STRING]"
APP_ID=$1
FILE=$2
REQUIRED=${3:-}
case "$APP_ID" in
    ''|*[!A-Za-z0-9._]*|.*|*.|*..*) fail "not an application id: '$APP_ID'" ;;
esac
[ -f "$FILE" ] || fail "no file $FILE"

# Compiled-in runtime paths of any ThothTerm package. (PRoot's own help text
# mentions /data/data/com.termux/, which is not ours and not checked.)
paths=$(LC_ALL=C grep -a -o '/data/data/com\.thothterm[A-Za-z0-9._]*/' "$FILE" | sort -u || true)
for p in $paths; do
    [ "$p" = "/data/data/$APP_ID/" ] || fail "$FILE: compiled-in path $p is not /data/data/$APP_ID/"
done

# Any ThothTerm id as a whole token (ids end at a character that cannot be in
# one: '/', '-', NUL...). Only APPLICATION_ID itself may be a protected id.
ids=$(LC_ALL=C grep -a -o 'com\.thothterm[A-Za-z0-9._]*' "$FILE" | sed 's/\.*$//' | sort -u || true)
for id in $ids; do
    [ "$id" = "$APP_ID" ] && continue
    for p in $PROTECTED; do
        [ "$id" = "$p" ] && fail "$FILE: carries the protected application id $id, but it was built for $APP_ID"
    done
done

if [ -n "$REQUIRED" ]; then
    LC_ALL=C grep -a -q -F "$REQUIRED" "$FILE" || fail "$FILE: does not contain $REQUIRED"
fi
exit 0
