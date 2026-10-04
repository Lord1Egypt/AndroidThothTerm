#!/system/bin/sh
# mkroot.sh NAME TARBALL -- a clean rootfs from an archive, materialized like
# the app's TarballExtractor does it: hard links become independent copies and
# read-only directory modes are applied last. Runs as the app (run-as).
set -e
. "$(dirname "$0")/env"
R=$T/$1/rootfs
chmod -R u+w $T/$1 2>/dev/null || true
rm -rf $T/$1
mkdir -p $R $T/$1/tmp $T/$1/cmds
# Android refuses hard links here, as it does to the app: tar reports each
# one, and they are made as copies below. Any other error is fatal.
tar -xzf $2 -C $R 2> $T/$1/tar.err || true
if grep -v "can't link" $T/$1/tar.err | grep -v '^tar: had errors$' | grep -q .; then
  grep -v "can't link" $T/$1/tar.err | head -5; exit 1
fi
n=0
if [ -f $2.hardlinks ]; then
  while read -r link target; do
    rm -f "$R/$link"
    cp -p "$R/$target" "$R/$link"
    n=$((n+1))
  done < $2.hardlinks
fi
echo "hard links materialized as copies: $n"
# Read-only directory modes, applied last as the app's extractor does.
if [ -f $2.dirmodes ]; then
  while read -r mode dir; do
    [ -n "$dir" ] && chmod "$mode" "$R/$dir"
  done < $2.dirmodes
fi
echo "directory modes restored: $(grep -c . $2.dirmodes 2>/dev/null || echo 0)"
# The resolvers Android uses; the app bind-mounts its own copy the same way.
cp $S/resolv.conf $T/$1/resolv.conf
echo "rootfs ready: $(du -sm $R | cut -f1) MiB"
