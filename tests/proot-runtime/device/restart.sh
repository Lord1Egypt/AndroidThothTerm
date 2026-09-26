# A new PRoot process (app restart): another dpkg transaction, identity still right.
export DEBIAN_FRONTEND=noninteractive
ok() { if [ "$2" = 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fi; }
apt-get -y install jq >/dev/null 2>&1 && jq --version >/dev/null; ok "after restart: apt install jq" $?
apt-get -y remove jq >/dev/null 2>&1; ok "after restart: apt remove jq" $?
[ "$(readlink /proc/self/exe)" = /usr/lib/cargo/bin/coreutils/readlink ]; ok "after restart: readlink /proc/self/exe = its hard link" $?
a=$(dpkg --audit 2>&1); [ -z "$a" ]; ok "after restart: dpkg --audit clean" $?
n=0; for f in /usr/lib/cargo/bin/coreutils/*; do "$f" --version >/dev/null 2>&1 || n=$((n+1)); done
[ $n = 0 ]; ok "after restart: all $(ls /usr/lib/cargo/bin/coreutils | wc -l) applets run" $?
