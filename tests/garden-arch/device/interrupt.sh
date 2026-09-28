# Interrupted pacman transactions, on a disposable copy that has passed
# zero.sh. pacman is SIGKILLed while it extracts (as when Android kills the
# app), then recovered the documented way: prove no pacman is running, only
# then remove the stale lock; reinstall at the same version (never a
# downgrade), complete with a full pacman -Syu, check integrity. Signature
# checking stays on throughout.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2 ${3:-}"; fi; }
L=/var/lib/pacman/db.lck
killmid() { # killmid SECONDS pacman-args... : SIGKILL pacman mid-transaction
  s=$1; shift
  pacman "$@" > /tmp/int.log 2>&1 &
  sleep "$s"; pkill -9 -x pacman; wait 2>/dev/null; sleep 1
}
unlock() { # the documented recovery step, only once no pacman runs
  pgrep -x pacman >/dev/null; ok $(( $? == 0 )) "$1: no pacman process remains"
  if ! pgrep -x pacman >/dev/null && [ -e $L ]; then rm -f $L; fi
}
consistent() {
  pacman -Dk >/dev/null 2>&1 && [ "$(pacman -Qk 2>&1 | grep -vc ' 0 missing files')" = 0 ] && [ ! -e $L ]
}

# 1. An ordinary package, interrupted while its files are being replaced.
v=$(pacman -Q tzdata | cut -d' ' -f2)
pacman -Sw --noconfirm tzdata >/dev/null 2>&1
killmid 2 -S --noconfirm tzdata
[ -e $L ]; ok $? "1 interrupted reinstall of tzdata $v: lock left behind (detected)"
pacman -S --noconfirm tree > /tmp/int2.log 2>&1; grep -q 'unable to lock database' /tmp/int2.log; ok $? "1 the next transaction refuses: unable to lock database"
unlock 1
pacman -S --noconfirm tzdata > /tmp/int3.log 2>&1; r=$?
[ $r = 0 ] && [ "$(pacman -Q tzdata | cut -d' ' -f2)" = "$v" ]; ok $? "1 tzdata reinstalled at the same version $v" "$(tail -2 /tmp/int3.log)"
pacman -Syu --noconfirm > /tmp/int4.log 2>&1; ok $? "1 pacman -Syu completes"
consistent; ok $? "1 database and files consistent, no lock"

# 2. A new package, interrupted: its files can be on disk with no database
#    entry, and a plain retry reports them.
pacman -Sw --noconfirm vim-runtime >/dev/null 2>&1
killmid 4 -S --noconfirm vim-runtime
[ -e $L ]; ok $? "2 interrupted install of vim-runtime: lock left behind (detected)"
unlock 2
if pacman -S --noconfirm vim-runtime > /tmp/int5.log 2>&1; then
  ok 0 "2 plain retry installs vim-runtime"
else
  grep -q 'exists in filesystem' /tmp/int5.log; ok $? "2 plain retry reports the leftover files (documented case)"
  # Documented recovery: the leftovers are vim-runtime's own files, which
  # pacman was installing; overwrite only that package's paths.
  pacman -S --noconfirm --overwrite '/usr/share/vim/*' vim-runtime > /tmp/int6.log 2>&1; ok $? "2 pacman -S --overwrite '/usr/share/vim/*' vim-runtime"
fi
pacman -Qk vim-runtime 2>&1 | grep -q ' 0 missing files'; ok $? "2 vim-runtime complete"
pacman -Rns --noconfirm vim-runtime >/dev/null 2>&1; ok $? "2 vim-runtime removed"

# 3. A library pacman itself needs (icu, through libarchive and libxml2),
#    interrupted: pacman cannot start until the library is back. Recovery
#    without pacman: GNU tar and xz do not link icu, so they restore the
#    files from the verified package pacman already downloaded to its cache;
#    then pacman reinstalls it properly.
v=$(pacman -Q icu | cut -d' ' -f2)
pacman -Sw --noconfirm icu >/dev/null 2>&1
pkg=$(ls /var/cache/pacman/pkg/icu-$v-*.pkg.tar.* | grep -v '\.sig$')
killmid 3 -S --noconfirm icu
pacman -V >/dev/null 2>&1; broken=$?
echo "INFO 3 after the kill pacman $( [ $broken = 0 ] && echo still starts || echo 'cannot start:' ) $(pacman -V 2>&1 | grep -m1 'error' || true)"
unlock 3
tar -xJf "$pkg" -C / --exclude=.PKGINFO --exclude=.BUILDINFO --exclude=.MTREE --exclude=.INSTALL; ok $? "3 icu files restored from the cached package with GNU tar"
pacman -V >/dev/null 2>&1; ok $? "3 pacman starts again"
pacman -S --noconfirm icu > /tmp/int7.log 2>&1; r=$?
[ $r = 0 ] && [ "$(pacman -Q icu | cut -d' ' -f2)" = "$v" ]; ok $? "3 icu reinstalled by pacman at the same version $v"
pacman -Syu --noconfirm > /tmp/int8.log 2>&1; ok $? "3 pacman -Syu completes"

grep -q '^SigLevel *= *Required' /etc/pacman.conf && ! grep -qi 'SigLevel.*Never' /etc/pacman.conf; ok $? "signature checking unchanged"
pacman -Syu --noconfirm > /tmp/int9.log 2>&1 && grep -q 'nothing to do' /tmp/int9.log; ok $? "final pacman -Syu: nothing to do"
consistent; ok $? "final database integrity, no lock"
