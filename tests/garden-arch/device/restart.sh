# L: after a complete PRoot restart, a real package transaction.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2 ${3:-}"; fi; }
pacman -S --noconfirm jq > /tmp/l 2>&1 && echo '{"a":1}' | jq -e .a >/dev/null; ok $? "L after restart: install jq and use it" "$(tail -2 /tmp/l)"
pacman -Rns --noconfirm jq >/dev/null 2>&1; ok $? "L remove jq"
pacman -Dk >/dev/null 2>&1 && [ ! -e /var/lib/pacman/db.lck ]; ok $? "L database consistent, no lock"
v=$(su -s /bin/sh thoth -c 'sudo -n id -un' 2>&1); [ "$v" = root ]; ok $? "L sudo after restart"
