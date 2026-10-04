#!/bin/bash
# Runs INSIDE an arm64 container (qemu-user) whose filesystem is the Security
# base system under test, as root, with the log directory at /w (the generated
# optional-repository script and the pinned upstream artifact, base64) and
# this directory at /h. Started by validate-rootfs.sh; never on a phone.
#
# MODE=online (default): the whole suite. MODE=offline (container started with
# --network none): the enable script gets as far as the network and no further.
# Package-management checks only: nothing from the third-party repository is
# run or pointed at any target; the one probe package is a small library.
#
# qemu-user has no seccomp support, so pacman's downloader needs
# --disable-sandbox here (the shipped pacman.conf is not changed by it): an
# emulation limit, reported as such, not a property of the image.
set -u
export LANG=C.UTF-8
MODE=${MODE:-online}
PASS=0; FAIL=0
ok() { echo "PASS $*"; PASS=$((PASS + 1)); }
no() { echo "FAIL $*"; FAIL=$((FAIL + 1)); }
tailout() { sed 's/^/    | /' "$1" | tail -${2:-6}; }
check() { local d=$1; shift; if "$@" > /tmp/c.out 2>&1; then ok "$d"; else no "$d"; tailout /tmp/c.out; fi; }
# refuse DESC REGEX CMD...: the command must fail, and say why.
refuse() {
    local d=$1 re=$2; shift 2
    if "$@" > /tmp/c.out 2>&1; then no "$d: ACCEPTED"; tailout /tmp/c.out
    elif grep -Eqi "$re" /tmp/c.out; then ok "$d: refused ($(grep -Eio "$re" /tmp/c.out | head -1))"
    else no "$d: failed for another reason"; tailout /tmp/c.out 8; fi
}
D=/var/tmp/thothterm-extrarepo
BAD_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
read -r -a TRUSTED_ARR <<< "$TRUSTED"
FIRST=${TRUSTED_ARR[0]}
SIGNER=F9A6E68A711354D84A9B91637533BAFE69A25079
G="gpg --homedir /etc/pacman.d/gnupg --no-permission-warning --batch"
# The script as generated, with the emulation flag for the one network step.
sed 's/^pacman -Sy --noconfirm$/pacman -Sy --noconfirm --disable-sandbox/' /w/extra-repo-script.sh > /tmp/enable.sh
grep -q -- '--disable-sandbox' /tmp/enable.sh || { echo "FAIL could not patch the sync step"; exit 1; }
stage() { rm -rf $D; mkdir -p -m 0755 $D; cp /w/pkg.b64 /w/sig.b64 $D/; }
run() { bash "$1" 2>&1 | tee /tmp/run.out; return "${PIPESTATUS[0]}"; }
conf_sum() { sha256sum /etc/pacman.conf | cut -d' ' -f1; }
markers() { grep -c '^# >>> ThothTerm optional repository' /etc/pacman.conf; }

echo "== D. the image as shipped ($MODE)"
check "no pacman keyring ships" test ! -e /etc/pacman.d/gnupg
check "no sync databases, cache or lock" bash -c 'test -z "$(ls -A /var/lib/pacman/sync)" && test -z "$(ls -A /var/cache/pacman/pkg)" && test ! -e /var/lib/pacman/db.lck'
check "repositories: core extra alarm aur, and no third-party one" test "$(pacman-conf --repo-list | tr '\n' ' ')" = "core extra alarm aur "
check "pacman.conf names no third-party repository" bash -c '! grep -qi blackarch /etc/pacman.conf'
check "no package, keyring or file of the third-party repository ships" bash -c '! pacman -Qq | grep -qi blackarch && test -z "$(find / -xdev \( -iname "*blackarch*" \) 2>/dev/null | head -1)"'
check "only the Arch keyrings ship" bash -c 'ls /usr/share/pacman/keyrings | sed "s/-.*//; s/\.gpg//" | sort -u | tr "\n" " " | grep -Eqx "(archlinux|archlinuxarm| )+"'
check "global SigLevel requires signatures and none is relaxed" bash -c 'pacman-conf SigLevel | tr " " "\n" | grep -Eqx "Required|PackageRequired" && ! pacman-conf SigLevel | tr " " "\n" | grep -Eqx "Never|Optional|TrustAll|PackageNever|PackageOptional|PackageTrustAll"'
check "Architecture = aarch64" test "$(pacman-conf Architecture)" = aarch64
echo "INFO installed packages: $(pacman -Q | wc -l)"
check "pacman -Dk: no database errors" bash -c 'pacman -Dk 2>/dev/null | grep -q "No database errors have been found"'
check "pacman -Qk: no package has missing files" bash -c 'pacman -Qk 2>/dev/null | tee /tmp/qk.out | grep -vc " 0 missing files" | grep -qx 0 && test "$(wc -l < /tmp/qk.out)" -ge 100'
CONF0=$(conf_sum)

echo "== E. the script before the base keyring exists"
stage
refuse "no pacman keyring yet" "keyring is not ready" run /tmp/enable.sh
check "nothing changed" test "$(conf_sum)" = "$CONF0"
check "the script cleaned up what it staged" bash -c "test -z \"\$(ls $D | grep -E 'pkg|sig|b64' || true)\""

echo "== F. the base keyring, as the app creates it at first run"
check "pacman-key --init" pacman-key --init
check "pacman-key --populate archlinuxarm" pacman-key --populate archlinuxarm
CONF1=$(conf_sum)
no_key() { ! $G --with-colons --list-keys "$FIRST" >/dev/null 2>&1; }

echo "== G. refusals leave everything as it was"
tamper() { # tamper FILE: flip the first byte of the decoded file and re-encode
    base64 -d "$D/$1" > /tmp/t.bin; printf '\xff' | dd of=/tmp/t.bin bs=1 seek=7 conv=notrunc 2>/dev/null; base64 -w0 /tmp/t.bin > "$D/$1"
}
stage; tamper pkg.b64
refuse "a changed keyring package" "digest differs from the pin" run /tmp/enable.sh
stage; tamper sig.b64
refuse "a changed signature" "digest differs from the pin" run /tmp/enable.sh
stage; base64 -d "$D/pkg.b64" | head -c 18000 | base64 -w0 > "$D/pkg.b64"
refuse "a truncated keyring package" "size differs from the pin" run /tmp/enable.sh
stage; base64 -d "$D/sig.b64" | head -c 100 | base64 -w0 > "$D/sig.b64"
refuse "a truncated signature" "size differs from the pin" run /tmp/enable.sh
stage; : > "$D/pkg.b64"
refuse "an empty keyring package" "size differs from the pin" run /tmp/enable.sh
stage; rm -f "$D/sig.b64"
refuse "a missing signature file" "No such file|cannot|failed" run /tmp/enable.sh
stage; sed "s/$FIRST/$BAD_KEY/g" /tmp/enable.sh > /tmp/enable-badtrust.sh
refuse "a trusted key that is not the pinned one" "trusted keys differ from the pin" run /tmp/enable-badtrust.sh
stage; sed "s/$REVOKED/$BAD_KEY/g" /tmp/enable.sh > /tmp/enable-badrevoked.sh
refuse "a revoked key that is not the pinned one" "revoked keys differ from the pin" run /tmp/enable-badrevoked.sh
check "no key was imported by any refusal" no_key
check "pacman.conf is byte-identical after every refusal" test "$(conf_sum)" = "$CONF1"
check "no third-party keyring package got installed" bash -c '! pacman -Q blackarch-keyring'

echo "== H. enabling the optional repository ($MODE)"
stage
if [ "$MODE" = online ]; then
    check "the script succeeds" run /tmp/enable.sh
    tailout /tmp/run.out 4
    check "it reports the keys proven and the databases refreshed" bash -c 'grep -q "^EXTRA_REPO_KEYS_VERIFIED trusted=4 revoked=1$" /tmp/run.out && grep -qx EXTRA_REPO_SYNCED /tmp/run.out'
else
    refuse "offline, it stops at the network step" "could not resolve|failed retrieving|failed to synchronize|Could not|error" run /tmp/enable.sh
    check "the keys were proven before the network step" bash -c 'grep -q "^EXTRA_REPO_KEYS_VERIFIED trusted=4 revoked=1$" /tmp/run.out'
fi
check "the keyring package is installed through pacman" pacman -Q blackarch-keyring
check "exactly one marked section was added" test "$(markers)" = 1
check "repositories: the extra one is last" test "$(pacman-conf --repo-list | tr '\n' ' ')" = "core extra alarm aur blackarch "
check "its Server is the pinned official aarch64 origin" test "$(pacman-conf --repo blackarch Server)" = 'https://blackarch.org/blackarch/blackarch/os/aarch64'
check "it overrides no SigLevel" test -z "$(pacman-conf --repo blackarch SigLevel)"
check "no signature level is relaxed anywhere" bash -c '{ pacman-conf SigLevel; for r in $(pacman-conf --repo-list); do pacman-conf --repo "$r" SigLevel; done; } | tr " " "\n" | grep -Eqx "Required|PackageRequired" && ! { pacman-conf SigLevel; for r in $(pacman-conf --repo-list); do pacman-conf --repo "$r" SigLevel; done; } | tr " " "\n" | grep -Eqx "Never|Optional|TrustAll|PackageNever|PackageOptional|PackageTrustAll"'
check "the global SigLevel lines are untouched" test "$(grep -E "^[[:space:]]*(SigLevel|LocalFileSigLevel|RemoteFileSigLevel)" /etc/pacman.conf | sed 's/[[:space:]]\+/ /g' | tr '\n' '|')" = "SigLevel = Required DatabaseOptional|LocalFileSigLevel = Optional|"
for k in "${TRUSTED_ARR[@]}"; do
    check "key $k is imported and fully valid" bash -c "$G --with-colons --list-keys $k | grep -q '^pub:[fu]:' && $G --export-ownertrust | grep -qx '$k:4:'"
done
check "the revoked key is not trusted" bash -c "! $G --export-ownertrust | grep -Eq \"^$REVOKED:[4-6]:\""
check "nothing of the staged files remains" bash -c "test -z \"\$(ls $D 2>/dev/null | grep -E 'pkg|sig|b64|conf' || true)\""
check "pacman -Dk: no database errors" bash -c 'pacman -Dk 2>/dev/null | grep -q "No database errors have been found"'

if [ "$MODE" = online ]; then
    echo "== I. the repository works, with signatures checked"
    check "its package list is synced" bash -c '[ "$(pacman -Sl blackarch | wc -l)" -gt 1000 ]'
    check "pacman -Si resolves a package from it" bash -c 'pacman -Si blackarch-keyring | grep -q "^Repository *: blackarch"'
    check "a signed install from it works (probe: mbelib, a small library)" pacman -S --noconfirm --disable-sandbox mbelib
    tailout /tmp/c.out 3
    check "…and the installed copy is intact" bash -c 'pacman -Qk mbelib 2>&1 | grep -q " 0 missing files"'
    check "…and removes cleanly" pacman -Rns --noconfirm mbelib
    check "-Qk over the whole system is clean" bash -c 'pacman -Qk 2>/dev/null | grep -vc " 0 missing files" | grep -qx 0'
    echo "== J. idempotent and retryable"
    stage
    check "a second run succeeds" run /tmp/enable.sh
    check "still exactly one marked section" test "$(markers)" = 1
    check "the keyring package is still installed once" bash -c 'test "$(pacman -Qq | grep -c "^blackarch-keyring$")" = 1'
    stage; sed "s/VALIDSIG $SIGNER/VALIDSIG $BAD_KEY/" /tmp/enable.sh > /tmp/enable-badsigner.sh
    refuse "a signer that is not the pinned one" "was not signed by the pinned key" run /tmp/enable-badsigner.sh
    check "that refusal changed nothing" test "$(markers)" = 1
else
    echo "== J. a retry while still offline changes nothing more"
    C=$(conf_sum)
    stage
    refuse "a retry fails the same way" "could not resolve|failed retrieving|failed to synchronize|Could not|error" run /tmp/enable.sh
    check "still exactly one marked section, same file" bash -c "test \"\$(grep -c '^# >>> ThothTerm optional repository' /etc/pacman.conf)\" = 1 && test \"\$(sha256sum /etc/pacman.conf | cut -d' ' -f1)\" = $C"
fi
echo "SUMMARY: $PASS PASS, $FAIL FAIL"
[ "$FAIL" = 0 ]
