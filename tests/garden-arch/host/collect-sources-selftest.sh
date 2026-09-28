#!/bin/sh
# shellcheck disable=SC2016,SC2034  # the checks are eval'd strings
# Self-test of the part of garden-arch/rootfs/collect-sources.py that runs in
# the container (its COLLECT script), against a synthetic recipe served from
# local file:// and git+file:// sources, so it needs no network beyond the
# archlinux:base-devel image itself.
#
#   collect-sources-selftest.sh [IMAGE]     (default archlinux:base-devel)
#   KEEP=1 keeps the work directory for inspection.
#
# The recipe declares arch=(x86_64) only, like an Arch recipe that Arch Linux
# ARM rebuilds, and has a generic source, a source_aarch64, a source_x86_64, a
# git source pinned by tag and a local file. The collected sources must be
# exactly the generic one, the aarch64 one and the git tree at the tag: the
# corresponding source of an aarch64 binary. Exits 1 on any failure.
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
COLLECTOR="$HERE/../../../garden-arch/rootfs/collect-sources.py"
IMAGE="${1:-archlinux:base-devel}"
W="$(mktemp -d)"
[ -n "${KEEP:-}" ] || trap 'docker run --rm -v "$W:/w" "$IMAGE" rm -rf /w/o /w/r /w/srcdest /w/fixtures >/dev/null 2>&1; rm -rf "$W"' EXIT

python3 -c 'import importlib.util, sys
spec = importlib.util.spec_from_file_location("c", sys.argv[1])
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
open(sys.argv[2], "w").write(m.COLLECT)' "$COLLECTOR" "$W/collect.sh"

cat > "$W/prepare.sh" <<'PREPARE'
set -eu
pacman -Q git >/dev/null 2>&1 || { echo "the image needs git (collect-sources.py adds it)" >&2; exit 2; }
id builder >/dev/null 2>&1 || useradd -m builder
F=/w/fixtures; mkdir -p $F /w/r /w/o/demo/sources /w/srcdest
echo generic > $F/generic.txt; echo aarch64 > $F/arm.txt; echo x86_64 > $F/x86.txt
git init -q -b main $F/repo; cd $F/repo
git -c user.name=t -c user.email=t@t commit -q --allow-empty -m one
echo tree > file; git add file; git -c user.name=t -c user.email=t@t commit -q -m two
git tag v1; git rev-parse HEAD > $F/tag-commit
git -c user.name=t -c user.email=t@t commit -q --allow-empty -m after-tag
sum() { sha256sum "$1" | cut -d' ' -f1; }
echo patch > /w/r/local.patch
cat > /w/r/PKGBUILD <<PKGBUILD
pkgname=demo
pkgver=1
pkgrel=1
arch=(x86_64)
source=(file://$F/generic.txt "repo::git+file://$F/repo#tag=v1" local.patch)
source_aarch64=(file://$F/arm.txt)
source_x86_64=(file://$F/x86.txt)
sha256sums=($(sum $F/generic.txt) SKIP $(sum /w/r/local.patch))
sha256sums_aarch64=($(sum $F/arm.txt))
sha256sums_x86_64=($(sum $F/x86.txt))
package() { :; }
PKGBUILD
cp /w/collect.sh /w/o/collect.sh
chown -R builder /w
PREPARE

# The same invocation as collect-sources.py, with /w/r as the recipe.
docker run --rm -v "$W:/w" "$IMAGE" bash -c '
set -eu
command -v git >/dev/null || pacman -Sy --noconfirm --needed git >/dev/null
bash /w/prepare.sh
ln -s /w/r /r; ln -s /w/o /o; rm -rf /srcdest; ln -s /w/srcdest /srcdest
su builder -c "BASE=demo bash /o/collect.sh"
'

fail=0
check() { if eval "$2"; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi; }
S="$W/o/demo/sources"
commit="$(cat "$W/fixtures/tag-commit")"
check "generic source collected" '[ "$(cat "$S/generic.txt")" = generic ]'
check "aarch64 source collected" '[ "$(cat "$S/arm.txt")" = aarch64 ]'
check "no x86_64 source" '[ ! -e "$S/x86.txt" ]'
check "git tree exported at the tag's commit" '[ -s "$S/repo-$commit.tar.gz" ] && tar -xzOf "$S/repo-$commit.tar.gz" repo/file | grep -qx tree'
check "local file left to the recipe" '[ ! -e "$S/local.patch" ]'
check "sources.tsv lists exactly three" '[ "$(wc -l < "$W/o/demo/sources.tsv")" -eq 3 ]'
check "sources.tsv records the git commit" 'grep -q "^git	.*	$commit	repo-$commit.tar.gz\$" "$W/o/demo/sources.tsv"'
exit $fail
