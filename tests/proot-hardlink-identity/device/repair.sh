export DEBIAN_FRONTEND=noninteractive
echo "before: sudo $(dpkg-query -W -f='${Status}' sudo)"
dpkg --configure -a 2>&1 | grep -vE "^update-alternatives: warning" | tail -3
apt-get -y -o Dpkg::Options::=--force-confold --fix-broken install 2>&1 | tail -1
apt-get -y -o Dpkg::Options::=--force-confold full-upgrade 2>&1 | tail -1
echo "after: sudo $(dpkg-query -W -f='${Status} ${Version}' sudo), rust-coreutils $(dpkg-query -W -f='${Version}' rust-coreutils)"
a=$(dpkg --audit 2>&1); echo "dpkg --audit: [${a:-clean}]"
echo "sudo -n id -un as thoth: $(su -s /bin/sh thoth -c 'sudo -n id -un' 2>&1)"
