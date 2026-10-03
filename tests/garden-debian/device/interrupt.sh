# An interrupted dpkg run leaves sudo unpacked but not configured. The app then
# runs `dpkg --configure -a` (RootfsManager.ensureRealSudo); nothing may be
# downgraded. Reproduced deterministically with dpkg --unpack.
export DEBIAN_FRONTEND=noninteractive
ok() { if [ "$2" = 0 ]; then echo "PASS $1"; else echo "FAIL $1 ($3)"; fi; }
v=$(dpkg-query -W -f='${Version}' sudo)
cd /tmp && apt-get download sudo >/dev/null 2>&1 && dpkg --unpack /tmp/sudo_*.deb >/dev/null 2>&1
s=$(dpkg-query -W -f='${Status}' sudo); [ "$s" = "install ok unpacked" ]; ok "sudo left unpacked, not configured" $? "$s"
dpkg --audit | grep -q sudo; ok "dpkg --audit reports it" $?
dpkg --configure -a >/dev/null 2>&1; ok "dpkg --configure -a" $?
s=$(dpkg-query -W -f='${Status} ${Version}' sudo); [ "$s" = "install ok installed $v" ]; ok "sudo configured, same version ($v), no downgrade" $? "$s"
chmod 4755 /usr/bin/sudo
[ "$(su -s /bin/sh thoth -c 'sudo -n id -un' 2>&1)" = root ]; ok "sudo works after recovery" $?
# A real kill: SIGKILL apt mid-install, then recover the same way.
(apt-get -y install tree >/dev/null 2>&1 &) ; sleep 2; pkill -9 -f 'apt-get -y install tree'; pkill -9 dpkg; sleep 1
rm -f /var/lib/dpkg/lock* /var/cache/apt/archives/lock /var/lib/apt/lists/lock
dpkg --configure -a >/dev/null 2>&1; apt-get -y --fix-broken install >/dev/null 2>&1
a=$(dpkg --audit 2>&1); [ -z "$a" ]; ok "recovered after SIGKILL during apt install" $? "$a"
apt-get -y purge tree >/dev/null 2>&1; rm -f /tmp/sudo_*.deb
