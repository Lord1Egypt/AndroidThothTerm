# The update story from a genuinely stale Arch Linux ARM state: the official
# upstream image's own userland (kernel and firmware removed with pacman),
# with its stale sync databases, provisioned as the app does, then brought
# forward only by a full pacman -Syu. Signature checking stays on.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2 ${3:-}"; fi; }
BAD='unknown trust|marginal trust|invalid or corrupted package|required key missing|not writable|could not be looked up|error:'
# Every ThothTerm image carries the one Android setting (no Landlock).
grep -qx DisableSandboxFilesystem /etc/pacman.conf || sed -i 's/^#DisableSandboxFilesystem$/DisableSandboxFilesystem/' /etc/pacman.conf
before="pacman $(pacman -Q pacman | cut -d' ' -f2), archlinux-keyring $(pacman -Q archlinux-keyring | cut -d' ' -f2), openssl $(pacman -Q openssl | cut -d' ' -f2), systemd $(pacman -Q systemd | cut -d' ' -f2)"
echo "INFO stale state: $before; sync db from $(stat -c %y /var/lib/pacman/sync/core.db | cut -d. -f1)"
pacman -Syu --noconfirm > /tmp/st1 2>&1; r=$?
# The official image has openssh: its pre-transaction hook asks systemd to
# restart sshd. Ignore only this exact four-line PID-1 failure. Any other
# hook error stays in the checked log and fails the transaction gate.
awk '
  $0 == "error: command failed to execute correctly" &&
  a == "(1/1) Marking sshd.service for restart..." &&
  b == "System has not been booted with systemd as init system (PID 1). Can\047t operate." &&
  c == "Failed to connect to system scope bus via local transport: Host is down" { ignored++; next }
  { print; a=b; b=c; c=$0 }
  END { if (ignored) print "INFO known sshd/systemd hook failure: " ignored > "/dev/stderr" }
' /tmp/st1 > /tmp/st1.checked
[ $r = 0 ] && ! grep -Eiq "$BAD" /tmp/st1.checked; ok $? "stale -> current by one pacman -Syu ($(grep -E '^Packages \(' /tmp/st1 | sed 's/^Packages (\([0-9]*\)).*/\1/') packages)" "$(grep -Ei "$BAD" /tmp/st1 | head -3)"
pacman -Syu --noconfirm > /tmp/st2 2>&1; grep -q 'there is nothing to do' /tmp/st2; ok $? "second -Syu: nothing to do"
pacman -Dk >/dev/null 2>&1; ok $? "pacman -Dk"
# PRoot cannot chmod symlinks. Check every warned link and its target, then
# reject any other upgrade warning except the two upstream tpm2-tss .pacnew
# files and the journal directory mode inherited from the official image.
sed -n "s/^warning: warning given when extracting \(\/.*\) (Can't set permissions to 0777)$/\1/p" /tmp/st1 > /tmp/st1.symlinks
broken=0
while IFS= read -r path; do
  [ -L "$path" ] && [ -e "$path" ] || broken=$((broken + 1))
done < /tmp/st1.symlinks
grep '^warning:' /tmp/st1 | sed \
  -e "/^warning: warning given when extracting \/.* (Can't set permissions to 0777)$/d" \
  -e '/^warning: \/etc\/tpm2-tss\/fapi-profiles\/P_ECCP384SHA384.json installed as \/etc\/tpm2-tss\/fapi-profiles\/P_ECCP384SHA384.json.pacnew$/d' \
  -e '/^warning: \/etc\/tpm2-tss\/fapi-profiles\/P_RSA3072SHA384.json installed as \/etc\/tpm2-tss\/fapi-profiles\/P_RSA3072SHA384.json.pacnew$/d' \
  -e '/^warning: directory permissions differ on \/var\/log\/journal\/$/d' > /tmp/st1.unexpected
[ "$broken" -eq 0 ] && [ ! -s /tmp/st1.unexpected ]; ok $? "stale upgrade warnings classified ($(wc -l < /tmp/st1.symlinks) working symlinks)" "$(head -1 /tmp/st1.unexpected)"
pacman -Qk > /tmp/st1.qk 2>&1; r=$?
[ "$r" -eq 0 ] && [ "$(grep -vc ' 0 missing files' /tmp/st1.qk)" -eq 0 ]; ok $? "pacman -Qk: no missing files"
for b in pacman bash curl gpg; do
  path=$(command -v "$b")
  [ -n "$path" ] && ldd "$path" > "/tmp/stale-ldd-$b" 2>&1
  r=$?
  [ "$r" = 0 ] && ! grep -q 'not found' "/tmp/stale-ldd-$b"
  ok $? "ldd $b" "$(head -1 "/tmp/stale-ldd-$b" 2>/dev/null)"
done
if command -v sudo >/dev/null 2>&1; then
  path=$(command -v sudo)
  ldd "$path" > /tmp/stale-ldd-sudo 2>&1
  r=$?
  [ "$r" = 0 ] && ! grep -q 'not found' /tmp/stale-ldd-sudo
  ok $? 'ldd sudo' "$(head -1 /tmp/stale-ldd-sudo)"
else
  echo 'INFO the official stale upstream image has no sudo package; the candidate image tests sudo separately'
fi
echo "INFO now: pacman $(pacman -Q pacman | cut -d' ' -f2), archlinux-keyring $(pacman -Q archlinux-keyring | cut -d' ' -f2), openssl $(pacman -Q openssl | cut -d' ' -f2), systemd $(pacman -Q systemd | cut -d' ' -f2)"
