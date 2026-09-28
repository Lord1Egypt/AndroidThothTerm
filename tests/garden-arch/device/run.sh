#!/system/bin/sh
# run.sh NAME USER SCRIPT [MODE] -- run /cmds/SCRIPT in rootfs NAME as root or
# thoth with the app's PRoot (from its native library directory) and argv:
# --kill-on-exit (provisioning form) or, with MODE=window, --hangup-on-exit.
. "$(dirname "$0")/env"
N=$1; U=$2; S=$3; M=${4:-provision}
export PROOT_TMP_DIR=$T/$N/tmp PROOT_LOADER=$NLD/libproot_loader.so LD_LIBRARY_PATH=$T/lib
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TERM=xterm-256color LANG=C.UTF-8 LC_ALL=C.UTF-8 TMPDIR=/tmp
EXIT=--kill-on-exit; [ "$M" = window ] && EXIT=--hangup-on-exit
BASE="--rootfs=$T/$N/rootfs --root-id --link2symlink $EXIT --kernel-release=6.1.0-thothterm --bind=/dev --bind=/proc --bind=/sys --bind=/proc/mounts:/etc/mtab --bind=$T/$N/resolv.conf:/etc/resolv.conf --bind=$T/$N/cmds:/cmds"
if [ "$U" = root ]; then
  HOME=/root USER=root LOGNAME=root exec $NLD/libproot.so $BASE --cwd=/ /bin/bash /cmds/$S
else
  HOME=/home/thoth USER=thoth LOGNAME=thoth exec $NLD/libproot.so $BASE --cwd=/home/thoth /usr/bin/su -m -s /bin/bash thoth -c "bash /cmds/$S"
fi
