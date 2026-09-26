#!/system/bin/sh
# run.sh VARIANT NAME USER SCRIPT -- run /cmds/SCRIPT in rootfs NAME with PRoot VARIANT
# (old|new), as root or thoth, with the same PRoot argv as the app.
T=/data/local/tmp/l2s
V=$1; N=$2; U=$3; S=$4
export PROOT_TMP_DIR=$T/$N/tmp PROOT_LOADER=$T/$V/loader LD_LIBRARY_PATH=$T/lib
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TERM=xterm-256color LANG=C.UTF-8 TMPDIR=/tmp
BASE="--rootfs=$T/$N/rootfs --root-id --link2symlink --kill-on-exit --kernel-release=6.1.0-thothterm --bind=/dev --bind=/proc --bind=/sys --bind=/proc/mounts:/etc/mtab --bind=$T/$N/resolv.conf:/etc/resolv.conf --bind=$T/$N/cmds:/cmds"
if [ "$U" = root ]; then
  HOME=/root exec $T/$V/proot $BASE --cwd=/root /bin/bash /cmds/$S
else
  HOME=/home/thoth exec $T/$V/proot $BASE --cwd=/home/thoth /usr/bin/su -m -s /bin/bash thoth -c "bash /cmds/$S"
fi
