# What the app's guest configuration does: the thoth account, passwordless sudo
# through Debian's own sudo, and the setuid bit PRoot's fake root needs.
set -e
id thoth >/dev/null 2>&1 || useradd -m -u 1000 -U -s /bin/bash -G sudo thoth
printf 'thoth ALL=(ALL:ALL) NOPASSWD: ALL\n' > /etc/sudoers.d/thoth
chmod 0440 /etc/sudoers.d/thoth
chmod 4755 /usr/bin/sudo
echo "provisioned: $(. /etc/os-release; echo "$PRETTY_NAME"), sudo $(dpkg-query -W -f='${Version}' sudo), coreutils $(dpkg-query -W -f='${Version}' coreutils)"
