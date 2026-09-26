#!/system/bin/sh
# mkroot.sh NAME -- a clean rootfs from the pinned ubuntu-base tarball, materialized
# like the app's TarballExtractor does it: hard links become independent copies.
set -e
T=/data/local/tmp/l2s
R=$T/$1/rootfs
rm -rf $T/$1
mkdir -p $R $T/$1/tmp $T/$1/cmds
tar -xzf $T/ubuntu-base-26.04.1-base-arm64.tar.gz -C $R 2>/dev/null || true
n=0
while read -r link target; do
  cp -p "$R/$target" "$R/$link"
  n=$((n+1))
done < $T/hardlinks.txt
echo "hard links materialized as copies: $n"
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > $T/$1/resolv.conf
cp $T/debs/*.deb $T/$1/cmds/
echo "rootfs ready: $(du -sm $R | cut -f1) MiB"
