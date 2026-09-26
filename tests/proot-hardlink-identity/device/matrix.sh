# Clean-rootfs recovery/upgrade matrix. Each check prints PASS/FAIL.
export DEBIAN_FRONTEND=noninteractive
APT="apt-get -y -o Dpkg::Options::=--force-confold"
ok() { if [ "$2" = 0 ]; then echo "PASS $1"; else echo "FAIL $1 ($3)"; fi; }
utils() {
  for u in basename dirname dircolors chown chmod cp mv ln mkdir rm ls install stat; do
    "$u" --version >/dev/null 2>&1 || { echo "$u"; return 1; }
  done
  d=$(mktemp -d) && cd "$d" && mkdir a && cp /etc/hostname a/f && chmod 600 a/f && chown "$(id -u):$(id -g)" a/f && ln -s f a/l \
    && mv a/f a/g && install -m 644 a/g a/h && stat -c %a a/h | grep -q 644 && ls a >/dev/null && rm -r a \
    && [ "$(basename /x/y)" = y ] && [ "$(dirname /x/y)" = /x ] && dircolors -b >/dev/null && cd / && rm -rf "$d"
}
$APT update >/dev/null 2>&1; ok "apt update" $?
out=$($APT full-upgrade 2>&1); ok "apt full-upgrade" $? "$(printf '%s' "$out" | grep -E 'Security violation|error' | head -2)"
echo "     rust-coreutils $(dpkg-query -W -f='${Version}' rust-coreutils), sudo $(dpkg-query -W -f='${Status} ${Version}' sudo)"
a=$(dpkg --audit 2>&1); [ -z "$a" ]; ok "dpkg --audit clean" $? "$a"
$APT --fix-broken install >/dev/null 2>&1; ok "apt --fix-broken install" $?
[ "$(su -s /bin/sh thoth -c 'sudo -n id -un' 2>&1)" = root ]; ok "sudo -n id -un (as thoth) = root" $?
bad=$(utils); ok "13 utilities (root)" $? "$bad"
bad=$(su -s /bin/bash thoth -c "$(declare -f utils); utils"); ok "13 utilities (thoth)" $? "$bad"
$APT install tree >/dev/null 2>&1 && tree --version >/dev/null 2>&1; ok "apt install tree" $?
$APT remove tree >/dev/null 2>&1 && [ ! -e /usr/bin/tree ] && ! dpkg-query -W -f='${Status}' tree 2>/dev/null | grep -q "ok installed"; ok "apt remove tree" $?
# The dpkg hard-link lifecycle again, as any future rust-coreutils update would run it.
$APT install --reinstall rust-coreutils >/dev/null 2>&1; ok "reinstall rust-coreutils (future-upgrade path)" $?
bad=$(utils); ok "13 utilities after reinstall" $? "$bad"
[ "$(ls -l /proc/self/exe | sed 's/.*-> //')" = /usr/lib/cargo/bin/coreutils/ls ]; ok "ls /proc/self/exe = its hard link" $?
a=$(dpkg --audit 2>&1); [ -z "$a" ]; ok "dpkg --audit clean at end" $? "$a"
