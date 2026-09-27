# Clean-rootfs package-manager matrix. Each check prints PASS/FAIL.
export DEBIAN_FRONTEND=noninteractive
APT="apt-get -y -o Dpkg::Options::=--force-confold"
ok() { if [ "$2" = 0 ]; then echo "PASS $1"; else echo "FAIL $1 ($3)"; fi; }
utils() {
  for u in basename dirname dircolors chown chmod cp mv ln mkdir rm ls install stat readlink; do
    "$u" --version >/dev/null 2>&1 || { echo "$u"; return 1; }
  done
  d=$(mktemp -d) && cd "$d" && mkdir a && cp /etc/hostname a/f && chmod 600 a/f && chown "$(id -u):$(id -g)" a/f && ln -s f a/l \
    && ln a/f a/hard && [ "$(stat -c %h a/f)" = 2 ] && [ "$(readlink a/l)" = f ] \
    && mv a/f a/g && install -m 644 a/g a/h && stat -c %a a/h | grep -q 644 && ls a >/dev/null && rm -r a \
    && [ "$(basename /x/y)" = y ] && [ "$(dirname /x/y)" = /x ] && dircolors -b >/dev/null && cd / && rm -rf "$d"
}
grep -q '^Suites: trixie trixie-updates$' /etc/apt/sources.list.d/debian.sources \
  && grep -q '^Suites: trixie-security$' /etc/apt/sources.list.d/debian.sources \
  && ! grep -v '^#' /etc/apt/sources.list.d/debian.sources | grep -q 'snapshot\|stable\|contrib\|non-free' \
  && [ "$(grep -c '^Components: main$' /etc/apt/sources.list.d/debian.sources)" = 2 ]
ok "apt sources: trixie, trixie-updates, trixie-security, main only" $?
$APT update >/dev/null 2>&1; ok "apt update" $?
before=$(dpkg-query -W -f='${Package} ${Version}\n' | sort)
out=$($APT full-upgrade 2>&1); ok "apt full-upgrade" $? "$(printf '%s' "$out" | grep -E 'error|Errors' | head -2)"
echo "     upgraded: $(printf '%s\n' "$before" | diff - <(dpkg-query -W -f='${Package} ${Version}\n' | sort) | grep -c '^>')"
a=$(dpkg --audit 2>&1); [ -z "$a" ]; ok "dpkg --audit clean" $? "$a"
out=$($APT --fix-broken install 2>&1); printf '%s' "$out" | grep -q '0 upgraded, 0 newly installed, 0 to remove'; ok "apt --fix-broken install is a no-op" $?
[ "$(su -s /bin/sh thoth -c 'sudo -n id -un' 2>&1)" = root ]; ok "sudo -n id -un (as thoth) = root" $?
bad=$(utils); ok "coreutils incl. readlink and hard links (root)" $? "$bad"
bad=$(su -s /bin/bash thoth -c "$(declare -f utils); utils"); ok "coreutils incl. readlink and hard links (thoth)" $? "$bad"
for p in tree jq; do
  $APT install $p >/dev/null 2>&1 && $p --version >/dev/null 2>&1; ok "apt install $p" $?
done
for p in tree jq; do
  $APT purge $p >/dev/null 2>&1 && hash -r && ! command -v $p >/dev/null && ! dpkg-query -W -f='${Status}' $p 2>/dev/null | grep -q "ok installed"; ok "apt purge $p" $?
done
# /proc/self/exe through a hard link the guest makes, and through a plain binary.
ln /usr/bin/readlink /usr/local/bin/rl-hard
[ "$(/usr/local/bin/rl-hard /proc/self/exe)" = /usr/local/bin/rl-hard ]; ok "/proc/self/exe names the hard link it ran through" $? "$(/usr/local/bin/rl-hard /proc/self/exe)"
rm /usr/local/bin/rl-hard
[ "$(readlink /proc/self/exe)" = /usr/bin/readlink ]; ok "readlink /proc/self/exe" $?
# chown under PRoot is emulated: fake root records nothing on disk.
touch /tmp/own && chown 1000:1000 /tmp/own && echo "     chown to 1000:1000 then stat: $(stat -c %u:%g /tmp/own) (PRoot limitation, see docs)"; rm -f /tmp/own
a=$(dpkg --audit 2>&1); [ -z "$a" ]; ok "dpkg --audit clean at end" $? "$a"
