# The "pacman zero-drama" gate on a freshly provisioned FINAL rootfs, as root
# (A-K), then the guest checks. No manual repair between steps: any trust or
# keyring complaint anywhere is a FAIL. Prints PASS/FAIL per check.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2 ${3:-}"; fi; }
BAD='unknown trust|marginal trust|invalid or corrupted package|required key missing|not writable|could not be looked up|keyserver|failed to|error:|warning: .*(key|sign|trust)|archlinuxarm.gpg.*(missing|not found)|trustdb.*(error|fail|broken|corrupt)|permission'
clean() { ! grep -Eiq "$BAD" "$1"; }
K=/etc/pacman.d/gnupg

pacman --version > /tmp/a 2>&1; grep -q 'Pacman v7' /tmp/a; ok $? "A pacman --version ($(sed -n 's/.*\(Pacman v[^ ]*\).*/\1/p' /tmp/a))"

pacman-key --populate archlinuxarm > /tmp/b 2>&1; r=$?
[ $r = 0 ] && clean /tmp/b; ok $? "B pacman-key --populate archlinuxarm, no trust errors" "$(grep -Ei "$BAD" /tmp/b | head -2)"

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

pacman -Syu --noconfirm > /tmp/d 2>&1; r=$?
[ $r = 0 ] && clean /tmp/d; ok $? "D pacman -Syu ($(grep -E '^Packages \(' /tmp/d | cut -d')' -f1 | tr -d 'Packages (' || true) upgraded)" "$(grep -Ei "$BAD" /tmp/d | head -3)"
pacman -Syu --noconfirm > /tmp/e 2>&1; r=$?
[ $r = 0 ] && grep -q 'there is nothing to do' /tmp/e && clean /tmp/e; ok $? "E second pacman -Syu: nothing to do"

pacman -Q tree >/dev/null 2>&1; ok $(( $? == 0 )) "F0 tree not installed before the test"
pacman -S --noconfirm tree > /tmp/f 2>&1; r=$?
[ $r = 0 ] && clean /tmp/f && tree -L 1 /etc/pacman.d >/dev/null; ok $? "F install tree and run it"
pacman -Rns --noconfirm tree > /tmp/g 2>&1; r=$?
hash -r; [ $r = 0 ] && ! command -v tree >/dev/null && ! pacman -Q tree >/dev/null 2>&1; ok $? "G remove tree"
pacman -S --noconfirm tree > /tmp/h 2>&1; r=$?
[ $r = 0 ] && clean /tmp/h && tree --version >/dev/null; ok $? "H reinstall tree"
pacman -Rns --noconfirm tree > /tmp/i 2>&1; ok $? "I remove tree (baseline has none)"
pacman -Syu --noconfirm > /tmp/j 2>&1; r=$?
[ $r = 0 ] && clean /tmp/j; ok $? "J pacman -Syu after the transactions"
pacman -Dk > /tmp/k1 2>&1; r1=$?
pacman -Qk > /tmp/k2 2>&1; r2=$?
missing=$(grep -vc ' 0 missing files' /tmp/k2)
[ $r1 = 0 ] && [ $r2 = 0 ] && [ "$missing" = 0 ] && [ ! -e /var/lib/pacman/db.lck ]
ok $? "K pacman -Dk clean, pacman -Qk: $(wc -l < /tmp/k2) packages, $missing with missing files, no db.lck"
[ -s /var/lib/pacman/sync/core.db ] && [ -s /var/lib/pacman/sync/alarm.db ]; ok $? "K sync databases present after -Syu"

# ---- guest checks --------------------------------------------------------
. /etc/os-release; [ "$ID" = archarm ]; ok $? "os-release: $PRETTY_NAME (ID=$ID)"
k=$(uname -r); [ "$k" = 6.1.0-thothterm ]; ok $? "uname -r reports PRoot's emulated release ($k), not an Arch kernel; $(uname -m)"
v=$(su -s /bin/sh thoth -c 'sudo -n id -un' 2>&1); [ "$v" = root ]; ok $? "sudo -n id -un as thoth: $v"
[ "$(su -s /bin/sh thoth -c 'id -u; id -g; echo $HOME' | tr '\n' ' ')" = "1000 1000 /home/thoth " ]; ok $? "thoth uid/gid/home"
[ "$(getent passwd thoth | cut -d: -f7)" = /bin/bash ]; ok $? "thoth shell /bin/bash"
getent hosts archlinuxarm.org >/dev/null; ok $? "DNS"
c=$(curl -sS -o /dev/null -w '%{http_code} %{ssl_verify_result}' https://archlinuxarm.org/); [ "$c" = "200 0" ]; ok $? "HTTPS/TLS (curl: $c)"
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
