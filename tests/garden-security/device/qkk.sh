# pacman -Qkk on every installed package of a disposable Security rootfs, every
# finding classified. Nothing is allowlisted to make the gate green: a finding is
# KNOWN only when it belongs to one of the two classes documented in
# docs/garden/closure/CLOSURE_REPORT.md (Addendum 3) and the test proves the class
# for that very finding. Anything else is UNKNOWN and fails the gate.
#
#   K1 GID mismatch on a path whose package records a non-root group (the mtree
#      says gid != 0): the non-USERLAND PRoot cannot emulate chown to a group.
#   K2 Modification time mismatch on a symbolic link: the app's extractor has no
#      no-follow utimes (Android's public Os API has none).
#   K3 Permissions mismatch where the on-disk mode is exactly the package's mode
#      without its setuid/setgid bits: the extractor never applies them
#      (docs/garden/ROOTFS_LIFECYCLE.md); the app sets sudo's own bit separately.
#      A lost sticky bit is NOT this class (the extractor keeps it).
#   K5 Permissions mismatch on a file whose package mode lacks owner read, where the
#      guest sees exactly the package mode without setuid/setgid plus owner rw: PRoot's
#      fake root grants the owner rw while root reads such a file (measured
#      2026-10-03 on dbus-daemon-launch-helper: Android-side mode 0110, the
#      extractor's, ctime at the moment of the guest's access).
#   K4 Permissions mismatch on /etc/resolv.conf while /proc/self/mountinfo shows it
#      bind-mounted: the guest sees the app's Android resolver file there.
#
# Anything else -- UID mismatch (patch 0008 fixed those), a GID mismatch on a
# root-group path, a size/checksum/permission/symlink-target mismatch, a missing
# file, a modification time mismatch on a regular file -- is UNKNOWN.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2 ${3:-}"; fi; }
pacman -Qkk > /tmp/qkk.all 2>&1; echo "INFO pacman -Qkk exit $?"
echo "INFO packages checked: $(grep -c ' total files' /tmp/qkk.all)"
grep '^warning: ' /tmp/qkk.all > /tmp/qkk.findings
total=$(grep -c . /tmp/qkk.findings); echo "INFO findings: $total"
: > /tmp/qkk.known1; : > /tmp/qkk.known2; : > /tmp/qkk.known3; : > /tmp/qkk.known4; : > /tmp/qkk.known5; : > /tmp/qkk.unknown
pkgmode() { # pkgmode PKG PATH: the mode the package records for PATH (octal), from its mtree
  d=$(ls -d /var/lib/pacman/local/"$1"-[0-9]* 2>/dev/null | head -1)
  gzip -dc "$d/mtree" 2>/dev/null | awk -v p=".$2" '/^\/set/{for(i=2;i<=NF;i++) if($i~/^mode=/) dm=substr($i,6)} $1==p{m=dm; for(i=2;i<=NF;i++) if($i~/^mode=/) m=substr($i,6); print m}'
}
# warning: PKG: /path (REASON)
sed -n 's/^warning: \([^:]*\): \(\/.*\) (\(.*\))$/\1\t\2\t\3/p' /tmp/qkk.findings > /tmp/qkk.parsed
nparsed=$(grep -c . /tmp/qkk.parsed)
sed 's/^warning: \([^:]*\): \(\/.*\) (\(.*\))$//' /tmp/qkk.findings | grep . > /tmp/qkk.unparsed
# The package-recorded gid of every path that is not root-group, from the mtree.
for d in /var/lib/pacman/local/*/; do
  n=$(basename "$d" | sed 's/-[^-]*-[^-]*$//')
  [ -f "$d/mtree" ] || continue
  gzip -dc "$d/mtree" 2>/dev/null | awk -v n="$n" '
    /^\/set/ { for (i = 2; i <= NF; i++) if ($i ~ /^gid=/) g = substr($i, 5); next }
    /^#/ || NF == 0 { next }
    { gid = g; for (i = 2; i <= NF; i++) if ($i ~ /^gid=/) gid = substr($i, 5)
      if (gid != "" && gid != "0") { p = $1; sub(/^\.\//, "/", p); gsub(/\\040/, " ", p); print n "\t" p "\t" gid } }'
done > /tmp/qkk.nonroot
echo "INFO paths recorded with a non-root group: $(grep -c . /tmp/qkk.nonroot)"
while IFS="$(printf '\t')" read -r pkg path reason; do
  case "$reason" in
    "GID mismatch")
      if grep -qF "$(printf '%s\t%s\t' "$pkg" "$path")" /tmp/qkk.nonroot; then echo "$pkg $path" >> /tmp/qkk.known1
      else echo "$pkg $path ($reason; the package records a root group)" >> /tmp/qkk.unknown; fi ;;
    "Modification time mismatch")
      if [ -L "$path" ]; then echo "$pkg $path" >> /tmp/qkk.known2
      else echo "$pkg $path ($reason; not a symbolic link)" >> /tmp/qkk.unknown; fi ;;
    "Permissions mismatch")
      want=$(pkgmode "$pkg" "$path"); have=$(stat -c %a "$path" 2>/dev/null)
      if [ "$path" = /etc/resolv.conf ] && grep -q ' /etc/resolv.conf ' /proc/self/mountinfo; then
        echo "$pkg $path" >> /tmp/qkk.known4
      elif [ -n "$want" ] && [ -n "$have" ] && [ $((0$want & 06000)) != 0 ] && [ $((0$want & ~06000)) = $((0$have)) ]; then
        echo "$pkg $path (package $want, on disk $have)" >> /tmp/qkk.known3
      elif [ -n "$want" ] && [ -n "$have" ] && [ $((0$want & 0400)) = 0 ] && [ $(((0$want & ~06000) | 0600)) = $((0$have)) ]; then
        echo "$pkg $path (package $want, guest sees $have)" >> /tmp/qkk.known5
      else echo "$pkg $path ($reason; package ${want:-?}, on disk ${have:-?})" >> /tmp/qkk.unknown; fi ;;
    *) echo "$pkg $path ($reason)" >> /tmp/qkk.unknown ;;
  esac
done < /tmp/qkk.parsed
k1=$(grep -c . /tmp/qkk.known1); k2=$(grep -c . /tmp/qkk.known2); k3=$(grep -c . /tmp/qkk.known3); k4=$(grep -c . /tmp/qkk.known4); k5=$(grep -c . /tmp/qkk.known5); u=$(grep -c . /tmp/qkk.unknown); up=$(grep -c . /tmp/qkk.unparsed)
echo "INFO KNOWN K1 non-root-group GID mismatches: $k1"; sed 's/^/    K1 /' /tmp/qkk.known1 | head -20
echo "INFO KNOWN K2 symlink modification-time mismatches: $k2"; sed 's/^/    K2 /' /tmp/qkk.known2 | head -5
echo "INFO packages with K2 findings: $(awk '{print $1}' /tmp/qkk.known2 | sort -u | tr '\n' ' ')"
echo "INFO KNOWN K3 setuid/setgid not applied by the extractor: $k3"; sed 's/^/    K3 /' /tmp/qkk.known3 | head -20
echo "INFO KNOWN K4 bind-mounted resolver: $k4"; sed 's/^/    K4 /' /tmp/qkk.known4
echo "INFO KNOWN K5 PRoot fake-root owner rw on an owner-unreadable file: $k5"; sed 's/^/    K5 /' /tmp/qkk.known5
echo "INFO UNKNOWN findings: $u (unparsed lines: $up)"; sed 's/^/    UNKNOWN /' /tmp/qkk.unknown | head -40; sed 's/^/    UNPARSED /' /tmp/qkk.unparsed | head -10
[ "$total" = "$((k1 + k2 + k3 + k4 + k5 + u + up))" ]; ok $? "every finding is accounted for: $total = $k1 K1 + $k2 K2 + $k3 K3 + $k4 K4 + $k5 K5 + $u unknown + $up unparsed"
[ "$u" = 0 ] && [ "$up" = 0 ]; ok $? "pacman -Qkk: no UNKNOWN finding class ($total findings: $k1 K1, $k2 K2, $k3 K3, $k4 K4, $k5 K5)"
grep -q 'UID mismatch' /tmp/qkk.all; ok $(( $? == 0 )) "no UID mismatch anywhere (patch 0008: link2symlink keeps the guest owner)"
