# Interrupted pacman transactions on a disposable copy that has passed zero.sh.
# SIGKILL pacman in the middle of extracting, then recover the documented way:
# prove no pacman is running, only then remove the stale lock, then complete
# with a full pacman -Syu. Signature checking stays on; nothing is downgraded.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
ok() { if [ "$2" = 0 ]; then echo "PASS $1"; else echo "FAIL $1 ${3:-}"; fi; }
L=/var/lib/pacman/db.lck
killmid() { # killmid SECONDS pacman-args...
  s=$1; shift
  pacman "$@" > /tmp/int.log 2>&1 &
  sleep "$s"; pkill -9 -x pacman; wait 2>/dev/null; sleep 1
}
recover() {
  pgrep -x pacman >/dev/null; ok "$1: no pacman process remains" $(( $? == 0 ))
  if ! pgrep -x pacman >/dev/null && [ -e $L ]; then rm -f $L; fi
}

# 1. Interrupted upgrade path: reinstalling an installed package replaces its
#    files while its database entry stays; the most common real interruption.
v=$(pacman -Q icu | cut -d' ' -f2)
pacman -Sw --noconfirm icu >/dev/null 2>&1
killmid 3 -S --noconfirm icu
[ -e $L ]; ok "1 interrupted reinstall of icu $v: lock left behind (detected)" $?
pacman -S --noconfirm tree > /tmp/int2.log 2>&1; grep -q 'unable to lock database' /tmp/int2.log; ok "1 the next transaction refuses: unable to lock database" $?
recover 1
pacman -S --noconfirm icu > /tmp/int3.log 2>&1; r=$?
[ $r = 0 ] && [ "$(pacman -Q icu | cut -d' ' -f2)" = "$v" ]; ok "1 icu reinstalled, same version $v" $?
pacman -Syu --noconfirm > /tmp/int4.log 2>&1; ok "1 pacman -Syu completes" $?
pacman -Qk icu 2>&1 | grep -q ' 0 missing files' && pacman -Dk >/dev/null 2>&1; ok "1 database and icu files consistent" $?

# 2. Interrupted first install: files may be on disk with no database entry,
#    so a plain retry can report conflicts; recovery is documented.
pacman -Sw --noconfirm vim-runtime >/dev/null 2>&1
killmid 4 -S --noconfirm vim-runtime
[ -e $L ]; ok "2 interrupted install of vim-runtime: lock left behind (detected)" $?
recover 2
pacman -Q vim-runtime >/dev/null 2>&1 && echo "INFO 2 vim-runtime was recorded before the kill" || echo "INFO 2 vim-runtime not recorded; files may remain"
if pacman -S --noconfirm vim-runtime > /tmp/int5.log 2>&1; then
  ok "2 plain retry installs vim-runtime" 0
else
  grep -q 'exists in filesystem' /tmp/int5.log; ok "2 plain retry reports leftover files (documented case)" $?
  # Documented recovery: the leftovers are vim-runtime's own files, which pacman
  # was installing; overwrite only that package's paths.
  pacman -S --noconfirm --overwrite '/usr/share/vim/*' vim-runtime > /tmp/int6.log 2>&1; ok "2 pacman -S --overwrite '/usr/share/vim/*' vim-runtime" $?
fi
pacman -Qk vim-runtime 2>&1 | grep -q ' 0 missing files'; ok "2 vim-runtime complete" $?
pacman -Rns --noconfirm vim-runtime >/dev/null 2>&1; ok "2 vim-runtime removed" $?
grep -q '^SigLevel *= *Required' /etc/pacman.conf && ! grep -qi 'SigLevel.*Never' /etc/pacman.conf; ok "signature checking unchanged" $?
pacman -Syu --noconfirm > /tmp/int7.log 2>&1 && grep -q 'nothing to do' /tmp/int7.log; ok "final pacman -Syu: nothing to do" $?
pacman -Dk >/dev/null 2>&1 && [ "$(pacman -Qk 2>&1 | grep -vc ' 0 missing files')" = 0 ] && [ ! -e $L ]; ok "final database integrity, no lock" $?
