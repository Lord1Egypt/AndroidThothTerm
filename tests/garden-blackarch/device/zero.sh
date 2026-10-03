# The BlackArch "pacman zero-drama" gate (adapted from Rolling's) on a freshly provisioned
# candidate rootfs, as root: both keyrings, both signing identities, the real
# repositories including BlackArch's aarch64 one. This is Rolling's gate as root
# (A-K), then the guest checks. No manual repair between steps: any trust or
# keyring complaint anywhere is a FAIL. Prints PASS/FAIL per check.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2 ${3:-}"; fi; }
BAD='unknown trust|marginal trust|invalid or corrupted package|required key missing|not writable|could not be looked up|keyserver|failed to|error:|warning: .*(key|sign|trust)|archlinuxarm.gpg.*(missing|not found)|trustdb.*(error|fail|broken|corrupt)|permission'
clean() { ! grep -Eiq "$BAD" "$1"; }
# Under PRoot without patch 0006 libarchive's lchmod of a symlink failed with
# an errno pacman reports as "Can't set permissions to 0777". With 0006 the
# kernel's own EOPNOTSUPP reaches libarchive, which ignores it as on any Linux,
# so the warning should not appear at all. If it does, it is still checked,
# never filtered blindly: every named path must be a resolving symlink, and
# every package that owns one must pass pacman -Qkk (files and checksums).
clean_symlink_chmod() {
  log=$1
  sed -n "s/^warning: warning given when extracting \(\/.*\) (Can't set permissions to 0777)$/\1/p" "$log" > "$log.symlinks"
  while IFS= read -r path; do
    [ -L "$path" ] && [ -e "$path" ] || return 1
  done < "$log.symlinks"
  if [ -s "$log.symlinks" ]; then
    # shellcheck disable=SC2046
    owners=$(pacman -Qqo $(cat "$log.symlinks") 2>/dev/null | sort -u)
    [ -n "$owners" ] || return 1
    # shellcheck disable=SC2086
    pacman -Qkk $owners > "$log.qkk" 2>&1 || return 1
  fi
  sed "/^warning: warning given when extracting \/.* (Can't set permissions to 0777)$/d" "$log" > "$log.checked"
  clean "$log.checked"
}
K=/etc/pacman.d/gnupg

pacman --version > /tmp/a 2>&1; grep -q 'Pacman v7' /tmp/a; ok $? "A pacman --version ($(sed -n 's/.*\(Pacman v[^ ]*\).*/\1/p' /tmp/a))"

pacman-key --populate archlinuxarm blackarch > /tmp/b 2>&1; r=$?
[ $r = 0 ] && clean /tmp/b; ok $? "B pacman-key --populate archlinuxarm blackarch, no trust errors" "$(grep -Ei "$BAD" /tmp/b | head -2)"
for k in 68B3537F39A313B3E574D06777193F152BDBE6A6 F9A6E68A711354D84A9B91637533BAFE69A25079 8F9A9793CB8591147C2EC70566E0CDBD1E01F333 A0917C4147A37007CB54C1CFD295AA940EFDDF62 4345771566D76038C7FEB43863EC0ADBEA87E4E3; do
  gpg --homedir $K --no-permission-warning --batch --with-colons --list-keys $k | grep -q '^pub:[fu]:'; ok $? "B2 key $k is fully valid"
done
for k in 5E210889BBB5C48500E0C4F9C75E985FF8B993B4 CBA3C7D4798912702DCF568E67D8BDF42AD93F4E F6DA3545F655964DD9177918C65009B64EB7BB3C; do
  ! gpg --homedir $K --no-permission-warning --batch --with-colons --list-keys $k | grep -q '^pub:[fu]:'; ok $? "B3 key $k (revoked or untrusted) is NOT valid in the installation keyring"
done

# C: straight from the mirror, independent of pacman's databases: the newest
# 'which' in core, its detached signature, verified with the new keyring.
M=http://mirror.archlinuxarm.org/aarch64/core
rm -rf /tmp/c && mkdir -p /tmp/c/db && cd /tmp/c
f=
curl -fsSL -o core.db $M/core.db && bsdtar -xf core.db -C db && f=$(sed -n '/^%FILENAME%$/{n;p}' db/which-[0-9]*/desc)
[ -n "$f" ] && curl -fsSL -o "$f" "$M/$f" && curl -fsSL -o "$f.sig" "$M/$f.sig" \
  && gpg --homedir $K --no-permission-warning --batch --status-fd 1 --verify "$f.sig" "$f" > /tmp/c/v 2>/dev/null
grep -q '^\[GNUPG:\] VALIDSIG 68B3537F39A313B3E574D06777193F152BDBE6A6 ' /tmp/c/v 2>/dev/null && grep -qE '^\[GNUPG:\] TRUST_(FULLY|ULTIMATE)' /tmp/c/v
ok $? "C current repository package $f downloaded and signature-verified (trusted)"
cd / && rm -rf /tmp/c
# C2: BlackArch's database and one of its packages straight from the origin, verified with the same keyring.
BA=https://blackarch.org/blackarch/blackarch/os/aarch64
rm -rf /tmp/c && mkdir -p /tmp/c/db && cd /tmp/c
curl -fsSL -o blackarch.db $BA/blackarch.db && curl -fsSL -o blackarch.db.sig $BA/blackarch.db.sig \
  && gpg --homedir $K --no-permission-warning --batch --status-fd 1 --verify blackarch.db.sig blackarch.db > /tmp/c/vdb 2>/dev/null
grep -q '^\[GNUPG:\] VALIDSIG F9A6E68A711354D84A9B91637533BAFE69A25079 ' /tmp/c/vdb 2>/dev/null && grep -qE '^\[GNUPG:\] TRUST_(FULLY|ULTIMATE)' /tmp/c/vdb
ok $? "C2 BlackArch database signature verified from the origin (trusted, F9A6E68A)"
bsdtar -xf blackarch.db -C db && f=$(sed -n '/^%FILENAME%$/{n;p}' db/perl-color-output-[0-9]*/desc)
[ -n "$f" ] && curl -fsSL -o "$f" "$BA/$f" && curl -fsSL -o "$f.sig" "$BA/$f.sig" \
  && gpg --homedir $K --no-permission-warning --batch --status-fd 1 --verify "$f.sig" "$f" > /tmp/c/vp 2>/dev/null
grep -q '^\[GNUPG:\] VALIDSIG F9A6E68A711354D84A9B91637533BAFE69A25079 ' /tmp/c/vp 2>/dev/null && grep -qE '^\[GNUPG:\] TRUST_(FULLY|ULTIMATE)' /tmp/c/vp
ok $? "C2 BlackArch package $f downloaded and signature-verified (trusted)"
cd / && rm -rf /tmp/c

# Patch 0006: no-follow chmod of a symlink with an absolute guest path, the
# way libalpm extracts. Without 0006 fchmodat2 reaches the kernel untranslated
# and bsdtar fails with "Can't set permissions to 0777: No such file or
# directory"; with it the kernel's own EOPNOTSUPP reaches libarchive, which
# ignores it as on any Linux.
(umask 022; rm -rf /tmp/p6 && mkdir -p /tmp/p6/src && cd /tmp/p6/src && echo x > target) && cd /tmp/p6/src && ln -s target link \
  && bsdtar -cPf ../abs.tar -s ',^,/tmp/p6/out/,' target link && cd / \
  && bsdtar -xpPf /tmp/p6/abs.tar > /tmp/p6.log 2>&1 && [ ! -s /tmp/p6.log ] \
  && [ -L /tmp/p6/out/link ] && [ "$(stat -c %a /tmp/p6/out/target)" = 644 ]
ok $? "P6 symlink extraction with absolute names: no permission warning (patch 0006)" "$(head -1 /tmp/p6.log 2>/dev/null)"
cd / && rm -rf /tmp/p6 /tmp/p6.log
pacman -Syu --noconfirm > /tmp/d 2>&1; r=$?
[ $r = 0 ] && clean_symlink_chmod /tmp/d; ok $? "D pacman -Syu ($(grep -E '^Packages \(' /tmp/d | cut -d')' -f1 | tr -d 'Packages (' || true) upgraded; $(wc -l < /tmp/d.symlinks) symlink chmod warnings, their owners pass pacman -Qkk)" "$(grep -Ei "$BAD" /tmp/d.checked 2>/dev/null | head -3) $(grep -v ' 0 altered files' /tmp/d.qkk 2>/dev/null | head -2)"
[ -s /var/lib/pacman/sync/blackarch.db ] && [ "$(pacman -Sl blackarch | wc -l)" -ge 4000 ]; ok $? "D2 the blackarch repository is synchronised ($(pacman -Sl blackarch | wc -l) packages)"
gpg --homedir $K --no-permission-warning --batch --status-fd 1 --verify /var/lib/pacman/sync/blackarch.db.sig /var/lib/pacman/sync/blackarch.db 2>/dev/null | grep -q '^\[GNUPG:\] VALIDSIG F9A6E68A711354D84A9B91637533BAFE69A25079 '; ok $? "D3 the synchronised blackarch.db.sig verifies (F9A6E68A)"
pacman -Syu --noconfirm > /tmp/e 2>&1; r=$?
[ $r = 0 ] && grep -q 'there is nothing to do' /tmp/e && clean /tmp/e; ok $? "E second pacman -Syu: nothing to do"

PK="perl-color-output libtirpc-compat mbelib"
for p in $PK; do pacman -Q $p >/dev/null 2>&1; ok $(( $? == 0 )) "F0 $p not installed before the test"; done
# libtirpc-compat 0.1-4's own archive records usr/include/rpc/ as a 0644 directory, so
# pacman warns that the existing 0755 directory differs. That one line is set aside
# only when the cached package itself proves it; anything else still fails.
upstream_dirmode() { # upstream_dirmode LOG
  grep -vx 'warning: directory permissions differ on /usr/include/rpc/' "$1" | grep -vx 'filesystem: 755  package: 644' > "$1.checked"
  if grep -qx 'warning: directory permissions differ on /usr/include/rpc/' "$1"; then
    f=$(ls /var/cache/pacman/pkg/libtirpc-compat-*.pkg.tar.* | grep -v '\.sig$' | head -1)
    bsdtar -tvf "$f" | grep -Eq '^drw-r--r-- .* usr/include/rpc/?$' || return 1
    echo "INFO libtirpc-compat's archive records usr/include/rpc/ as 0644 (upstream packaging); pacman's directory-mode warning is that"
  fi
  clean "$1.checked"
}
pacman -S --noconfirm $PK > /tmp/f 2>&1; r=$?
[ $r = 0 ] && upstream_dirmode /tmp/f; ok $? "F install $PK (BlackArch repository; mbelib is signed by the Arch Linux ARM key)" "$(tail -2 /tmp/f)"
for p in $PK; do pacman -Qi $p | grep -E '^Validated By' | grep -q Signature; ok $? "F2 $p Validated By: Signature"; done
pacman -Qk $PK > /tmp/f3 2>&1; ok $? "F3 pacman -Qk of the probe packages" "$(grep -v ' 0 missing files' /tmp/f3 | head -2)"
f=$(pacman -Qlq perl-color-output | grep '\.pm$' | head -1); [ -n "$f" ] && [ -s "$f" ]; ok $? "F4 an installed file of perl-color-output is on disk ($f); nothing from BlackArch is run"
pacman -Rns --noconfirm $PK > /tmp/g 2>&1; r=$?
hash -r; [ $r = 0 ]; for p in $PK; do ! pacman -Q $p >/dev/null 2>&1 || r=1; done; ok $r "G remove $PK"
pacman -S --noconfirm $PK > /tmp/h 2>&1; r=$?
[ $r = 0 ] && upstream_dirmode /tmp/h; ok $? "H reinstall $PK"
for p in $PK; do pacman -Qi $p | grep -E '^Validated By' | grep -q Signature; ok $? "H2 $p Validated By: Signature (reinstall)"; done
pacman -Rns --noconfirm $PK > /tmp/i 2>&1; ok $? "I remove $PK (baseline has none)"
pacman -Syu --noconfirm > /tmp/j 2>&1; r=$?
[ $r = 0 ] && clean /tmp/j; ok $? "J pacman -Syu after the transactions"
pacman -Dk > /tmp/k1 2>&1; r1=$?
pacman -Qk > /tmp/k2 2>&1; r2=$?
missing=$(grep -vc ' 0 missing files' /tmp/k2)
[ $r1 = 0 ] && [ $r2 = 0 ] && [ "$missing" = 0 ] && [ ! -e /var/lib/pacman/db.lck ]
ok $? "K pacman -Dk clean, pacman -Qk: $(wc -l < /tmp/k2) packages, $missing with missing files, no db.lck"
[ -s /var/lib/pacman/sync/core.db ] && [ -s /var/lib/pacman/sync/alarm.db ] && [ -s /var/lib/pacman/sync/blackarch.db ]; ok $? "K sync databases present after -Syu"
sh /cmds/check-pacman-conf.sh /etc/pacman.conf /cmds/keyring.pins > /tmp/pc 2>&1; ok $? "K2 the shipped pacman.conf still passes the policy (no signature relaxation)" "$(tail -1 /tmp/pc)"

# ---- guest checks --------------------------------------------------------
# systemd's chroot detection: Android hides PID 1 (/proc hidepid), so without
# SYSTEMD_IN_CHROOT systemd answers ENOSYS. The app sets it (GardenRuntime);
# with it, detection answers "chroot" and systemctl skips PID-1 work cleanly.
[ -e /proc/1/root ]; echo "INFO /proc/1/root $( [ $? = 0 ] && echo visible || echo 'not visible (hidepid)' ); SYSTEMD_IN_CHROOT=${SYSTEMD_IN_CHROOT:-unset}"
env -u SYSTEMD_IN_CHROOT systemd-detect-virt --chroot > /tmp/dv0 2>&1; echo "INFO without SYSTEMD_IN_CHROOT: systemd-detect-virt --chroot exit $? $(head -1 /tmp/dv0)"
systemd-detect-virt --chroot > /tmp/dv1 2>&1; ok $? "systemd-detect-virt --chroot: yes (app environment)" "$(head -1 /tmp/dv1)"
systemctl daemon-reload > /tmp/dv2 2>&1; r=$?
[ $r = 0 ] && grep -q 'Running in chroot, ignoring command' /tmp/dv2; ok $? "systemctl daemon-reload is skipped as in a chroot, exit 0" "$(head -1 /tmp/dv2)"

. /etc/os-release; [ "$ID" = archarm ]; ok $? "os-release: $PRETTY_NAME (ID=$ID)"
k=$(uname -r); [ "$k" = 6.1.0-thothterm ]; ok $? "uname -r reports PRoot's emulated release ($k), not an Arch kernel; $(uname -m)"
v=$(su -s /bin/sh thoth -c 'sudo -n id -un' 2>&1); [ "$v" = root ]; ok $? "sudo -n id -un as thoth: $v"
[ "$(su -s /bin/sh thoth -c 'id -u; id -g; echo $HOME' | tr '\n' ' ')" = "1000 1000 /home/thoth " ]; ok $? "thoth uid/gid/home"
[ "$(getent passwd thoth | cut -d: -f7)" = /bin/bash ]; ok $? "thoth shell /bin/bash"
getent hosts archlinuxarm.org >/dev/null && getent hosts blackarch.org >/dev/null; ok $? "DNS (archlinuxarm.org, blackarch.org)"
c=$(curl -sS -o /dev/null -w '%{http_code} %{ssl_verify_result}' https://archlinuxarm.org/); [ "$c" = "200 0" ]; ok $? "HTTPS/TLS (curl: $c)"
c=$(curl -sS -o /dev/null -w '%{http_code} %{ssl_verify_result}' https://blackarch.org/); [ "${c#* }" = 0 ]; ok $? "HTTPS/TLS blackarch.org (curl: $c)"
cd /tmp && rm -rf z && mkdir z && cd z && head -c 200000 /dev/urandom > d && h=$(sha256sum < d)
for c in gzip xz zstd bzip2; do $c -c d > d.$c && $c -dc d.$c | sha256sum | grep -q "${h%% *}"; ok $? "$c round trip"; done
same() { [ "$(sha256sum < "$1")" = "$(sha256sum < "$2")" ]; }
tar -cf t.tar d && mkdir x && tar -xf t.tar -C x && same d x/d; ok $? "tar round trip"
ln d hard && [ "$(stat -c %h d)" = 2 ] && same d hard; ok $? "hard link"
ln -s d soft && [ "$(readlink soft)" = d ] && same d soft; ok $? "symlink"
cp /usr/bin/readlink ./rl && ln rl rl2 && [ "$(/tmp/z/rl2 /proc/self/exe)" = /tmp/z/rl2 ] && [ "$(PATH=/tmp/z rl2 /proc/self/exe)" = /tmp/z/rl2 ]
ok $? "/proc/self/exe names the hard link run, by path and by PATH (patch 0003)" "$(/tmp/z/rl2 /proc/self/exe)"
echo "INFO /proc/self/exe after a relative ./rl2: $(./rl2 /proc/self/exe) (PRoot keeps the ./; same file, same basename)"
cd / && rm -rf /tmp/z
[ -w /tmp ] && [ "$(stat -c %a /tmp)" = 1777 ]; ok $? "/tmp writable, 1777"
printf 'مرحبا\n' | grep -q 'مرحبا' && [ "$(printf 'é' | wc -m)" = 1 ]; ok $? "UTF-8 locale ($(locale 2>&1 | head -1))"
! locale 2>&1 | grep -qi cannot; ok $? "no locale warnings"
for b in pacman bash sudo curl gpg tar zstd xz ls; do
  p=$(command -v $b); ldd "$p" | grep -q 'not found'; ok $(( $? == 0 )) "ldd $b: all libraries found"
done
ls --version >/dev/null && sort --version >/dev/null && sha256sum /etc/os-release >/dev/null; ok $? "coreutils"
systemctl is-system-running > /tmp/s 2>&1; echo "INFO systemctl (expected unavailable under PRoot): $(head -1 /tmp/s)"
