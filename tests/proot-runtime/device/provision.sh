# Like the app: real sudo from the pinned debs, the thoth account, passwordless sudo.
set -e
export DEBIAN_FRONTEND=noninteractive
# Same order and flags as RootfsManager's real-sudo provisioning.
dpkg --force-confold -i /cmds/libapparmor1_*.deb >/dev/null
dpkg --force-confold -i /cmds/sudo-common_*.deb >/dev/null
dpkg --force-confold -i /cmds/sudo_*.deb >/dev/null
dpkg --configure -a
id thoth >/dev/null 2>&1 || useradd -m -u 1000 -s /bin/bash -G sudo thoth
printf 'thoth ALL=(ALL:ALL) NOPASSWD: ALL\n' > /etc/sudoers.d/thoth
chmod 0440 /etc/sudoers.d/thoth
chmod 4755 /usr/bin/sudo
echo "provisioned: $(dpkg-query -W -f='${Version}' rust-coreutils) coreutils, sudo $(dpkg-query -W -f='${Version}' sudo)"
