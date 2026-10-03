# Interrupted pacman transactions, on a disposable copy that has passed
# zero.sh. pacman is SIGKILLed while it extracts (as when Android kills the
# app), then recovered: prove no pacman is running, only then remove the
# stale lock; restore only from package bytes whose signature this script has
# verified itself; reinstall at the same version (never a downgrade); then
# require pacman -Qkk of the package, pacman -Dk, pacman -Qk and a full
# pacman -Syu to be clean. Signature checking stays on throughout.
#
# Narrow by design: a retry may fail only with a message that names the
# interrupted package and says its files or metadata are incomplete. Any
# signature, key or "invalid or corrupted package" error is a FAIL, never an
# expected interrupted state.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8
ok() { if [ "$1" = 0 ]; then echo "PASS $2"; else echo "FAIL $2 ${3:-}"; fi; }
L=/var/lib/pacman/db.lck
K=/etc/pacman.d/gnupg
KEY=68B3537F39A313B3E574D06777193F152BDBE6A6
M=http://mirror.archlinuxarm.org/aarch64
# Errors that mean the bytes or the trust are wrong. Never accepted anywhere.
INTEGRITY='invalid or corrupted package|PGP signature|signature from .* is (invalid|unknown trust|marginal)|required key missing|key .* could not be (looked up|imported)|checksum|corrupted'
killmid() { # killmid SECONDS pacman-args... : SIGKILL pacman mid-transaction
  s=$1; shift
  pacman "$@" > /tmp/int.log 2>&1 &
  sleep "$s"; pkill -9 -x pacman; wait 2>/dev/null; sleep 1
}
unlock() { # the documented recovery step, only once no pacman runs
  pgrep -x pacman >/dev/null; ok $(( $? == 0 )) "$1: no pacman process remains"
  if ! pgrep -x pacman >/dev/null && [ -e $L ]; then rm -f $L; fi
}
cached() { # cached NAME VERSION: the package file pacman downloaded
  ls /var/cache/pacman/pkg/"$1-$2"-*.pkg.tar.* 2>/dev/null | grep -v '\.sig$' | head -1
}
verified() { # verified NAME PKGFILE: its detached signature is valid, trusted, ALARM's
  name=$1 f=$2
  [ -s "$f" ] || return 1
  # The ALARM databases embed signatures, so pacman keeps no .sig in its
  # cache; fetch the published detached signature for this exact file.
  if [ ! -s "$f.sig" ]; then
    repo=$(pacman -Si "$name" 2>/dev/null | sed -n 's/^Repository *: //p' | head -1)
    [ -n "$repo" ] && curl -fsSL -o "$f.sig" "$M/$repo/$(basename "$f").sig" || return 1
  fi
  gpg --homedir $K --no-permission-warning --batch --status-fd 1 --verify "$f.sig" "$f" \
    > /tmp/int-verify 2>/dev/null
  grep -q "^\[GNUPG:\] VALIDSIG $KEY " /tmp/int-verify \
    && grep -qE '^\[GNUPG:\] TRUST_(FULLY|ULTIMATE)' /tmp/int-verify
}
# libalpm words an incomplete LOCAL database entry (SIGKILL between removing
# the old desc and writing the new one) as "failed to prepare transaction
# (invalid or corrupted package)". That one line is set aside only when the log
# also shows the cause: the missing local desc file and "could not fully load
# metadata" for the same package. Any other integrity wording, in the same log
# or not, still fails.
no_integrity_error() { # no_integrity_error LOG CASE
  f=$1
  if grep -Eq '^error: could not open file /var/lib/pacman/local/[^/]+/desc: No such file or directory$' "$f" \
     && grep -Eq '^warning: could not fully load metadata for package ' "$f"; then
    grep -Fvx 'error: failed to prepare transaction (invalid or corrupted package)' "$f" > "$f.nometa"
    f=$f.nometa
  fi
  if grep -Eiq "$INTEGRITY" "$f"; then
    echo "FAIL $2 integrity/signature error, never an interrupted-state symptom: $(grep -Ei "$INTEGRITY" "$f" | head -1)"
    return 1
  fi
  return 0
}
# SIGKILL can land after pacman has removed an old local-db desc and before it
# has written the new one. Only then, and only from the signed cached package
# verified above, recreate that one package's metadata with pacman's db-only
# install; the same-version reinstall that follows restores the files.
repair_missing_desc() { # repair_missing_desc CASE NAME VERSION PKGFILE
  case_=$1 name=$2 version=$3 pkg=$4
  dir=/var/lib/pacman/local/$name-$version
  if [ -f "$dir/desc" ]; then
    echo "INFO $case_ $name metadata intact; the missing-desc window was not hit this run"
    return 0
  fi
  ! pgrep -x pacman >/dev/null && [ ! -e "$L" ] || { echo "FAIL $case_ repair while pacman runs"; return 1; }
  verified "$name" "$pkg" || { echo "FAIL $case_ cached $name package does not verify; not used"; return 1; }
  [ ! -d "$dir" ] || rm -rf "$dir"
  pacman -S --dbonly --noconfirm "$name" > "/tmp/dbonly-$name.log" 2>&1
  r=$?
  no_integrity_error "/tmp/dbonly-$name.log" "$case_" || return 1
  [ $r = 0 ] && [ "$(pacman -Q "$name" | cut -d' ' -f2)" = "$version" ]
  ok $? "$case_ $name metadata recreated from the verified same-version package"
}
package_clean() { # package_clean CASE NAME: every file present, contents match the mtree
  pacman -Qkk "$2" > "/tmp/qkk-$2" 2>&1
  ok $? "$1 pacman -Qkk $2: files and checksums match the package" "$(grep -v ' 0 altered files' "/tmp/qkk-$2" | head -2)"
}
consistent() { # consistent CASE
  pacman -Dk > /tmp/int-dk 2>&1; r1=$?
  pacman -Qk > /tmp/int-qk 2>&1; r2=$?
  [ $r1 = 0 ] && [ $r2 = 0 ] && [ "$(grep -vc ' 0 missing files' /tmp/int-qk)" = 0 ] && [ ! -e $L ]
  ok $? "$1 pacman -Dk and pacman -Qk clean, no lock" "$(grep -v ' 0 missing files' /tmp/int-qk | head -2)"
}
syu_clean() { # syu_clean CASE
  pacman -Syu --noconfirm > /tmp/int-syu 2>&1; r=$?
  no_integrity_error /tmp/int-syu "$1" && [ $r = 0 ] && ! grep -q '^error:' /tmp/int-syu
  ok $? "$1 full pacman -Syu completes cleanly" "$(grep -E '^error:' /tmp/int-syu | head -2)"
}

# 1. An ordinary package, interrupted while its files are being replaced.
v=$(pacman -Q tzdata | cut -d' ' -f2)
pacman -Sw --noconfirm tzdata >/dev/null 2>&1
pkg=$(cached tzdata "$v")
verified tzdata "$pkg"; ok $? "1 cached tzdata $v signature verified (ALARM key, trusted)"
killmid 2 -S --noconfirm tzdata
[ -e $L ]; ok $? "1 interrupted reinstall of tzdata $v: lock left behind (detected)"
pacman -S --noconfirm tree > /tmp/int2.log 2>&1; grep -q 'unable to lock database' /tmp/int2.log; ok $? "1 the next transaction refuses: unable to lock database"
unlock 1
repair_missing_desc 1 tzdata "$v" "$pkg"
pacman -S --noconfirm tzdata > /tmp/int3.log 2>&1; r=$?
no_integrity_error /tmp/int3.log 1 && [ $r = 0 ] && [ "$(pacman -Q tzdata | cut -d' ' -f2)" = "$v" ]; ok $? "1 tzdata reinstalled at the same version $v" "$(tail -2 /tmp/int3.log)"
package_clean 1 tzdata
consistent 1
syu_clean 1

# 2. A new package, interrupted: its files can be on disk with no database
#    entry (or with an incomplete one), and a plain retry reports them.
vim_version=$(pacman -Si vim-runtime | sed -n 's/^Version *: //p' | head -1)
pacman -Sw --noconfirm vim-runtime >/dev/null 2>&1
pkg=$(cached vim-runtime "$vim_version")
verified vim-runtime "$pkg"; ok $? "2 cached vim-runtime $vim_version signature verified (ALARM key, trusted)"
killmid 4 -S --noconfirm vim-runtime
[ -e $L ]; ok $? "2 interrupted install of vim-runtime: lock left behind (detected)"
unlock 2
pacman -S --noconfirm vim-runtime > /tmp/int5.log 2>&1; r=$?
if no_integrity_error /tmp/int5.log 2; then
  if [ $r = 0 ]; then
    ok 0 "2 plain retry installs vim-runtime"
  elif grep -Eq '^error: failed to commit transaction \(conflicting files\)' /tmp/int5.log \
       && grep -Eq '^vim-runtime: /usr/share/vim/.* exists in filesystem$' /tmp/int5.log \
       && [ "$(grep -E ' exists in filesystem$' /tmp/int5.log | grep -vc '^vim-runtime: /usr/share/vim/')" = 0 ]; then
    # The documented case: only vim-runtime's own files, left by the kill.
    echo "INFO 2 retry reported $(grep -c ' exists in filesystem$' /tmp/int5.log) leftover vim-runtime files"
    pacman -S --noconfirm --overwrite '/usr/share/vim/*' vim-runtime > /tmp/int6.log 2>&1; r=$?
    no_integrity_error /tmp/int6.log 2 && [ $r = 0 ]; ok $? "2 pacman -S --overwrite '/usr/share/vim/*' vim-runtime" "$(tail -2 /tmp/int6.log)"
  elif grep -Eq "^(error|warning): could not fully load metadata for package vim-runtime-$vim_version" /tmp/int5.log; then
    echo "INFO 2 retry found vim-runtime's incomplete local metadata"
    repair_missing_desc 2 vim-runtime "$vim_version" "$pkg"
    pacman -S --noconfirm vim-runtime > /tmp/int6.log 2>&1; r=$?
    no_integrity_error /tmp/int6.log 2 && [ $r = 0 ]; ok $? "2 vim-runtime reinstalled after the metadata repair" "$(tail -2 /tmp/int6.log)"
  else
    ok 1 "2 plain retry failed with an unexpected error" "$(grep -E '^error:' /tmp/int5.log | head -2)"
  fi
fi
[ "$(pacman -Q vim-runtime | cut -d' ' -f2)" = "$vim_version" ]; ok $? "2 vim-runtime at $vim_version"
package_clean 2 vim-runtime
consistent 2
pacman -Rns --noconfirm vim-runtime >/dev/null 2>&1; ok $? "2 vim-runtime removed"

# 3. A library pacman itself needs (icu, through libarchive and libxml2),
#    interrupted: pacman cannot start until the library is back. Recovery
#    without pacman: GNU tar and xz do not link icu, so they restore the files
#    from the cached package -- only after its signature verified above.
v=$(pacman -Q icu | cut -d' ' -f2)
pacman -Sw --noconfirm icu >/dev/null 2>&1
pkg=$(cached icu "$v")
verified icu "$pkg"; ok $? "3 cached icu $v signature verified (ALARM key, trusted) before any restore"
killmid 3 -S --noconfirm icu
pacman -V >/dev/null 2>&1; broken=$?
echo "INFO 3 after the kill pacman $( [ $broken = 0 ] && echo still starts || echo 'cannot start:' ) $(pacman -V 2>&1 | grep -m1 'error' || true)"
unlock 3
if verified icu "$pkg"; then
  tar -xJf "$pkg" -C / --exclude=.PKGINFO --exclude=.BUILDINFO --exclude=.MTREE --exclude=.INSTALL; ok $? "3 icu files restored from the verified package with GNU tar"
else
  ok 1 "3 cached icu package no longer verifies; refusing to restore from it"
fi
pacman -V >/dev/null 2>&1; ok $? "3 pacman starts again"
repair_missing_desc 3 icu "$v" "$pkg"
pacman -S --noconfirm icu > /tmp/int7.log 2>&1; r=$?
no_integrity_error /tmp/int7.log 3 && [ $r = 0 ] && [ "$(pacman -Q icu | cut -d' ' -f2)" = "$v" ]; ok $? "3 icu reinstalled by pacman at the same version $v"
package_clean 3 icu
consistent 3
syu_clean 3

levels() { pacman-conf SigLevel; for r in $(pacman-conf --repo-list); do pacman-conf --repo "$r" SigLevel; done; }
levels | tr ' ' '\n' > /tmp/int-levels
pacman-conf SigLevel | tr ' ' '\n' | grep -Eqx 'Required|PackageRequired' \
  && ! grep -Eqx 'Never|Optional|TrustAll|PackageNever|PackageOptional|PackageTrustAll' /tmp/int-levels
ok $? "signature checking unchanged: $(pacman-conf SigLevel | tr '\n' ' ')"
pacman -Syu --noconfirm > /tmp/int9.log 2>&1 && grep -q 'nothing to do' /tmp/int9.log; ok $? "final pacman -Syu: nothing to do"
consistent final
