#!/bin/sh
# Self-test of garden-common/tools/check-runtime-ids.sh on synthetic files
# (no compiler needed). Run by QaRuntimeIsolationTest; the same checker on
# real compiled PRoot and libandroid-shmem is runtime-ids-host-check.sh.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
CHECK="$HERE/../../../garden-common/tools/check-runtime-ids.sh"
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
FAILS=0

file() { # file NAME CONTENT(printf format, \0 allowed)
    # shellcheck disable=SC2059
    printf "$2" > "$W/$1"
    echo "$W/$1"
}
expect() { # expect accept|refuse WHAT CHECK-ARGS...
    want=$1; what=$2; shift 2
    if sh "$CHECK" "$@" 2>/dev/null; then got=accept; else got=refuse; fi
    [ "$got" = "$want" ] || { echo "FAIL $what: expected $want, got $got"; FAILS=$((FAILS + 1)); }
}

A=com.thothterm.arch
QA=$A.qa.lifecycle
RT=files/linux/runtime
termux='It seems that termux-exec is active and is prepending /data/data/com.termux/...\0'

prod=$(file prod "\177ELF\0/data/data/$A/$RT/loader\0$termux")
qa=$(file qa "\177ELF\0/data/data/$QA/$RT/loader\0$termux")
expect accept "production proot for production" "$A" "$prod" "/data/data/$A/$RT/loader"
expect accept "QA proot for QA" "$QA" "$qa" "/data/data/$QA/$RT/loader"
expect refuse "production proot as QA" "$QA" "$prod" "/data/data/$QA/$RT/loader"
expect refuse "production proot as QA, without a required string" "$QA" "$prod"
expect refuse "QA proot as production" "$A" "$qa" "/data/data/$A/$RT/loader"
expect refuse "QA proot as production, without a required string" "$A" "$qa"

# A QA file naming a production id anywhere, path or not.
expect refuse "QA file with a production socket name" "$QA" \
    "$(file sock "\177ELF\0/data/data/$QA/$RT/loader\0$A\0%%s-app_info-%%s\0")"
expect refuse "QA file naming com.thothterm" "$QA" "$(file bare "\177ELF\0com.thothterm\0")"
expect refuse "QA file naming another edition" "$QA" "$(file other "\177ELF\0/data/data/com.thothterm.ubuntu/x\0")"
expect refuse "QA file with another QA id's path" "$QA" "$(file otherqa "\177ELF\0/data/data/$A.qa.other/$RT/tmp/\0")"
expect accept "QA file with no ThothTerm strings" "$QA" "$(file plain "\177ELF\0talloc\0")"
expect accept "a trailing dot ends an id" "$QA" "$(file dot "\177ELF\0$QA.\0")"
expect refuse "a missing required string" "$QA" "$(file noreq "\177ELF\0/data/data/$QA/$RT/tmp/\0")" \
    "/data/data/$QA/$RT/loader"
expect refuse "a missing file" "$QA" "$W/nothing"
for bad in "" ".qa" "com..thothterm" "com.thothterm/arch" "com.thothterm.arch." "a b"; do
    expect refuse "bad application id '$bad'" "$bad" "$prod"
done

[ "$FAILS" -eq 0 ] || { echo "runtime ids self-test: $FAILS failure(s)"; exit 1; }
echo "runtime ids self-test: OK"
