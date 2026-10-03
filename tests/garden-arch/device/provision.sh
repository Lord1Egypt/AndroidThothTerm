# What the app does to a freshly extracted rootfs before it declares setup
# complete (RootfsManager: GuestConfig, then pacmanKeyringScript), written out
# for a disposable copy. The account lines are appended the way GuestConfig
# appends them, blank separator line included.
set -e
mode=${1:-}
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
# --- the app's pacmanKeyringScript; the harness also refuses an image keyring ---
export LANG=C.UTF-8
cd /
K=/etc/pacman.d/gnupg
if [ -e "$K" ]; then echo "FAIL the image carries a pacman keyring"; exit 1; fi
SECONDS=0
pacman-key --init
pacman-key --populate archlinuxarm
gpg --homedir "$K" --no-permission-warning --batch --with-colons --list-keys 68B3537F39A313B3E574D06777193F152BDBE6A6 | grep -q '^pub:[fu]:' || { echo 'signing key is not fully valid' >&2; exit 1; }
set -- /usr/share/thothterm/signature-check/*.sig
if [ ! -f "$1" ]; then
    [ "$mode" = upstream-stale ] || { echo 'no signature-check package' >&2; exit 1; }
    # The official upstream image predates ThothTerm's signed fixture. The
    # stale-image gate checks this trusted keyring and then verifies package
    # signatures in its real pacman -Syu transaction.
    echo 'upstream image has no ThothTerm signature fixture'
else
    gpg --homedir "$K" --no-permission-warning --batch --status-fd 1 --verify "$1" "${1%.sig}" > /tmp/.thothterm-verify 2>/dev/null || true
    grep -q '^\[GNUPG:\] VALIDSIG 68B3537F39A313B3E574D06777193F152BDBE6A6 ' /tmp/.thothterm-verify && grep -qE '^\[GNUPG:\] TRUST_(FULLY|ULTIMATE)' /tmp/.thothterm-verify || { cat /tmp/.thothterm-verify >&2; rm -f /tmp/.thothterm-verify; exit 1; }
    rm -f /tmp/.thothterm-verify
fi
pacman-conf SigLevel | grep -q Required || { echo 'package signatures are not required' >&2; exit 1; }
echo keyring-verified
echo "provisioned: $(. /etc/os-release; echo "$PRETTY_NAME"), pacman $(ls /var/lib/pacman/local | sed -n "s/^pacman-\([0-9]\)/\1/p"), keyring in ${SECONDS} s"
