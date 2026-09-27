# A new PRoot process (app restart): another dpkg transaction, identity still right.
export DEBIAN_FRONTEND=noninteractive
ok() { if [ "$2" = 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fi; }
apt-get -y install less >/dev/null 2>&1 && less --version >/dev/null; ok "after restart: apt install less" $?
apt-get -y purge less >/dev/null 2>&1; ok "after restart: apt purge less" $?
[ "$(readlink /proc/self/exe)" = /usr/bin/readlink ]; ok "after restart: readlink /proc/self/exe" $?
[ "$(su -s /bin/sh thoth -c 'sudo -n id -un' 2>&1)" = root ]; ok "after restart: sudo -n id -un = root" $?
a=$(dpkg --audit 2>&1); [ -z "$a" ]; ok "after restart: dpkg --audit clean" $?
