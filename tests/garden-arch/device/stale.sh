# The update story from a genuinely stale Arch Linux ARM state: the official
# upstream image's own userland (kernel and firmware removed with pacman),
# with its stale sync databases, provisioned as the app does, then brought
# forward only by a full pacman -Syu. Signature checking stays on.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
ok() { if [ "$2" = 0 ]; then echo "PASS $1"; else echo "FAIL $1 ${3:-}"; fi; }
BAD='unknown trust|marginal trust|invalid or corrupted package|required key missing|not writable|could not be looked up|error:'
before="pacman $(pacman -Q pacman | cut -d' ' -f2), archlinux-keyring $(pacman -Q archlinux-keyring | cut -d' ' -f2), openssl $(pacman -Q openssl | cut -d' ' -f2), systemd $(pacman -Q systemd | cut -d' ' -f2)"
echo "INFO stale state: $before; sync db from $(stat -c %y /var/lib/pacman/sync/core.db | cut -d. -f1)"
pacman -Syu --noconfirm > /tmp/st1 2>&1; r=$?
[ $r = 0 ] && ! grep -Eiq "$BAD" /tmp/st1; ok "stale -> current by one pacman -Syu ($(grep -E '^Packages \(' /tmp/st1 | sed 's/^Packages (\([0-9]*\)).*/\1/') packages)" $? "$(grep -Ei "$BAD" /tmp/st1 | head -3)"
pacman -Syu --noconfirm > /tmp/st2 2>&1; grep -q 'there is nothing to do' /tmp/st2; ok "second -Syu: nothing to do" $?
pacman -Dk >/dev/null 2>&1; ok "pacman -Dk" $?
for b in pacman bash sudo curl gpg; do ldd "$(command -v $b)" | grep -q 'not found'; ok "ldd $b" $(( $? == 0 )); done
echo "INFO now: pacman $(pacman -Q pacman | cut -d' ' -f2), archlinux-keyring $(pacman -Q archlinux-keyring | cut -d' ' -f2), openssl $(pacman -Q openssl | cut -d' ' -f2), systemd $(pacman -Q systemd | cut -d' ' -f2)"
