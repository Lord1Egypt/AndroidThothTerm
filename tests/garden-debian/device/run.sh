#!/system/bin/sh
# run.sh NAME USER SCRIPT -- run /cmds/SCRIPT in rootfs NAME as root or thoth,
# with the app's PRoot argv (provisioning form: --kill-on-exit).
T=/data/local/tmp/gd
N=$1; U=$2; S=$3
export PROOT_TMP_DIR=$T/$N/tmp PROOT_LOADER=$T/bin/loader LD_LIBRARY_PATH=$T/lib
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TERM=xterm-256color LANG=C.UTF-8 LC_ALL=C.UTF-8 TMPDIR=/tmp SYSTEMD_IN_CHROOT=1
BASE="--rootfs=$T/$N/rootfs --root-id --link2symlink --kill-on-exit --kernel-release=6.1.0-thothterm --bind=/dev --bind=/proc --bind=/sys --bind=/proc/self/mounts:/etc/mtab --bind=$T/$N/resolv.conf:/etc/resolv.conf --bind=$T/$N/cmds:/cmds"
if [ "$U" = root ]; then
  HOME=/root exec $T/bin/proot $BASE --cwd=/root /bin/bash /cmds/$S
else
  HOME=/home/thoth exec $T/bin/proot $BASE --cwd=/home/thoth /usr/bin/su -m -s /bin/bash thoth -c "bash /cmds/$S"
fi
