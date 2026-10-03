# What the app does to a freshly extracted BlackArch rootfs before it is usable
# (RootfsManager: GuestConfig, then the generated pacmanKeyringScript), written
# out for a disposable copy. The keyring script is NOT re-implemented here: it is
# the exact text RootfsManager.pacmanKeyringScript generates for this edition
# (tests/garden-blackarch/host/DumpKeyringScript.java), pushed beside this file
# as keyring-script.sh and run unchanged. The account lines are appended the way
# GuestConfig appends them, blank separator line included.
set -e
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
grep -q '^thoth:' /etc/passwd || printf '\nthoth:x:1000:1000:Thoth User:/home/thoth:/bin/bash\n' >> /etc/passwd
grep -q '^thoth:' /etc/group || printf '\nthoth:x:1000:\n' >> /etc/group
grep -q '^wheel:.*thoth' /etc/group || sed -i 's/^\(wheel:[^:]*:[^:]*:\)\(.*\)$/\1\2,thoth/; s/^\(wheel:[^:]*:[^:]*:\),thoth$/\1thoth/' /etc/group
grep -q '^thoth:' /etc/shadow || printf '\nthoth:!:19000:0:99999:7:::\n' >> /etc/shadow
mkdir -p /home/thoth /tmp && chmod 1777 /tmp
mkdir -p /etc/sudoers.d
printf 'thoth ALL=(ALL:ALL) NOPASSWD: ALL\n' > /etc/sudoers.d/thoth
chmod 0440 /etc/sudoers.d/thoth
if [ -f /usr/bin/sudo ]; then chmod 4755 /usr/bin/sudo; fi
grep -q localhost /etc/hosts || printf '127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n' >> /etc/hosts
[ ! -e /etc/pacman.d/gnupg ] || { echo "FAIL the image carries a pacman keyring"; exit 1; }
SECONDS=0
bash /cmds/keyring-script.sh > /tmp/keyring.out 2>&1 || { cat /tmp/keyring.out; echo "FAIL the app's keyring script"; exit 1; }
cat /tmp/keyring.out
pacman-conf SigLevel | grep -q Required || { echo 'package signatures are not required' >&2; exit 1; }
echo "provisioned: $(. /etc/os-release; echo "$PRETTY_NAME"), BlackArch repo $(pacman-conf --repo blackarch Server), keyring in ${SECONDS} s"
