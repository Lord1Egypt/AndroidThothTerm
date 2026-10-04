# The optional third-party repository, exactly as the app enables it, on a
# freshly provisioned disposable rootfs, as root. /cmds holds the guest script
# the app generates (enable-extra-repo.sh, unchanged), the pinned upstream
# keyring package and signature as the app stages them (pkg.b64, sig.b64) and the
# pins (pins). Prints PASS/FAIL per check; nothing from the repository is run.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
. /cmds/pins
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2 ${3:-}"; fi; }
D=/var/tmp/thothterm-extrarepo
K=/etc/pacman.d/gnupg
G="gpg --homedir $K --no-permission-warning --batch"
stage() { rm -rf $D; mkdir -p -m 0755 $D; cp /cmds/pkg.b64 /cmds/sig.b64 $D/; }
conf_sum() { sha256sum /etc/pacman.conf | cut -d' ' -f1; }
markers() { grep -c '^# >>> ThothTerm optional repository' /etc/pacman.conf; }
siglevels() { { pacman-conf SigLevel; for r in $(pacman-conf --repo-list); do pacman-conf --repo "$r" SigLevel; done; } | tr ' ' '\n'; }
first=${TRUSTED%% *}

! pacman -Q blackarch-keyring >/dev/null 2>&1 && ! grep -qi blackarch /etc/pacman.conf && [ "$(pacman-conf --repo-list | tr '\n' ' ')" = "core extra alarm aur " ]
ok $? "X1 before: no third-party keyring, repository or configuration in the base"
C0=$(conf_sum)

stage; base64 -d $D/pkg.b64 > /tmp/p.bin; printf '\377' | dd of=/tmp/p.bin bs=1 seek=7 conv=notrunc 2>/dev/null; base64 -w0 /tmp/p.bin > $D/pkg.b64
bash /cmds/enable-extra-repo.sh > /tmp/r1 2>&1; r=$?
[ $r != 0 ] && grep -q 'digest differs from the pin' /tmp/r1 && [ "$(conf_sum)" = "$C0" ] && ! $G --with-colons --list-keys "$first" >/dev/null 2>&1
ok $? "X2 a changed keyring package is refused, nothing imported, pacman.conf untouched" "$(tail -2 /tmp/r1)"

stage
bash /cmds/enable-extra-repo.sh > /tmp/r2 2>&1; r=$?
ok $r "X3 the app's script succeeds" "$(tail -4 /tmp/r2)"
grep -q "^EXTRA_REPO_KEYS_VERIFIED trusted=4 revoked=1$" /tmp/r2 && grep -qx EXTRA_REPO_SYNCED /tmp/r2
ok $? "X3b it reports the keys proven and the databases refreshed"
[ -z "$(ls $D 2>/dev/null | grep -E 'pkg|sig|b64|conf' || true)" ]
ok $? "X4 nothing of the staged files remains"
pacman -Q blackarch-keyring >/dev/null 2>&1; ok $? "X5 the keyring package is installed through pacman"
[ "$(markers)" = 1 ] && [ "$(pacman-conf --repo-list | tr '\n' ' ')" = "core extra alarm aur blackarch " ] && [ "$(pacman-conf --repo blackarch Server)" = "$SERVER" ] && [ -z "$(pacman-conf --repo blackarch SigLevel)" ]
ok $? "X6 one marked [blackarch] section, the pinned official aarch64 Server, no SigLevel of its own"
siglevels | grep -Eqx 'Required|PackageRequired' && ! siglevels | grep -Eqx 'Never|Optional|TrustAll|PackageNever|PackageOptional|PackageTrustAll'
ok $? "X7 signatures are required everywhere and none is relaxed"
for k in $TRUSTED; do
  $G --with-colons --list-keys $k | grep -q '^pub:[fu]:' && $G --export-ownertrust | grep -qx "$k:4:"; ok $? "X8 key $k is imported and fully trusted"
done
for k in $REVOKED; do ! $G --export-ownertrust | grep -Eq "^$k:[4-6]:"; ok $? "X9 revoked key $k is not trusted"; done
[ "$(pacman -Sl blackarch | wc -l)" -gt 1000 ]; ok $? "X10 the repository's package list is synced ($(pacman -Sl blackarch | wc -l) packages)"
$G --status-fd 1 --verify /var/lib/pacman/sync/blackarch.db.sig /var/lib/pacman/sync/blackarch.db 2>/dev/null | grep -q "^\[GNUPG:\] VALIDSIG $SIGNER "
ok $? "X11 the synchronised blackarch.db.sig verifies against the pinned signer"
pacman -Si perl-color-output 2>/dev/null | grep -q '^Repository *: blackarch'; ok $? "X12 pacman -Si resolves a package from the repository (perl-color-output)"
pacman -S --noconfirm perl-color-output > /tmp/i1 2>&1; r=$?
[ $r = 0 ] && ! grep -Eiq 'unknown trust|invalid or corrupted|required key missing|keyserver|error:' /tmp/i1 && pacman -Qk perl-color-output 2>&1 | grep -q ' 0 missing files'
ok $? "X13 a package signed by the repository installs, with its signature checked" "$(tail -3 /tmp/i1)"
pacman -Rns --noconfirm perl-color-output > /tmp/i2 2>&1; ok $? "X14 and removes cleanly" "$(tail -2 /tmp/i2)"
stage
bash /cmds/enable-extra-repo.sh > /tmp/r3 2>&1; r=$?
[ $r = 0 ] && [ "$(markers)" = 1 ] && [ "$(pacman -Qq | grep -c '^blackarch-keyring$')" = 1 ]
ok $? "X15 a second run succeeds and changes nothing (one section, one package)" "$(tail -3 /tmp/r3)"
stage; sed "s/VALIDSIG $SIGNER/VALIDSIG AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" /cmds/enable-extra-repo.sh > /tmp/bad.sh
bash /tmp/bad.sh > /tmp/r4 2>&1; r=$?
[ $r != 0 ] && grep -q 'was not signed by the pinned key' /tmp/r4 && [ "$(markers)" = 1 ]
ok $? "X16 a package whose signer is not the pinned one is refused"
pacman -Dk 2>/dev/null | grep -q 'No database errors have been found'; ok $? "X17 pacman -Dk: no database errors"
