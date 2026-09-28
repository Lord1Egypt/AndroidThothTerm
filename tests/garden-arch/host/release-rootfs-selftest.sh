#!/bin/sh
# shellcheck disable=SC2016,SC2034  # the checks are eval'd strings
# Self-test of garden-arch/rootfs/release-rootfs.py on synthetic build outputs
# in build-rootfs.sh's exact layout: compare, pin, provenance and stage, on a
# copy of distro.properties, never the repository's. Needs python3 only.
# KEEP=1 keeps (and prints) the work directory.
# Exits 1 on any failure.
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$HERE/../../.."
TOOL="$R/garden-arch/rootfs/release-rootfs.py"
W="$(mktemp -d)"
if [ -n "${KEEP:-}" ]; then echo "work: $W"; else trap 'rm -rf "$W"' EXIT; fi
export RELEASE_ROOTFS_DISTRO="$W/distro.properties" RELEASE_ROOTFS_PROVENANCE="$W/ROOTFS_PROVENANCE.md"
cp "$R/garden-arch/src/main/assets/garden/distro.properties" "$RELEASE_ROOTFS_DISTRO"

python3 - "$W" <<'PY'
import gzip, hashlib, io, os, sys, tarfile
w = sys.argv[1]
buf = io.BytesIO()
with tarfile.open(fileobj=buf, mode="w", format=tarfile.GNU_FORMAT) as t:
    data = b"ID=archarm\n"; i = tarfile.TarInfo("./etc/os-release"); i.size = len(data); i.mtime = 1790000000
    t.addfile(i, io.BytesIO(data))
raw = buf.getvalue()
gz = gzip.compress(raw, 9, mtime=0)
sha = hashlib.sha256(gz).hexdigest(); name = "thothterm-arch-aarch64-rootfs-" + sha[:12]
for out in ("out1", "out2"):
    d = os.path.join(w, out); os.makedirs(d)
    open(os.path.join(d, name + ".tar.gz"), "wb").write(gz)
    open(os.path.join(d, name + ".tar.gz.sha256"), "w").write("%s  %s.tar.gz\n" % (sha, name))
    open(os.path.join(d, name + ".packages.tsv"), "w").write("bash\t5.3.3-2\taarch64\tDependency\nsudo\t1.9.17-1\taarch64\tExplicitly installed\n")
    open(os.path.join(d, name + ".scan.txt"), "w").write("machine-id: []\npacman gnupg home: absent\n")
    open(os.path.join(d, name + ".manifest.txt"), "w").write("""# ThothTerm Rolling rootfs build manifest
file: %s.tar.gz
sha256: %s
size: %d
uncompressed_size: %d
architecture: aarch64
state: latest internally consistent Arch Linux ARM aarch64 package state at sync_finished
sync_started: 2026-10-01T08:00:00Z
sync_finished: 2026-10-01T08:10:00Z
mirror: Server = http://mirror.archlinuxarm.org/$arch/$repo
upstream_url: http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz
epoch: 1790842200
builder_tool: pacman 7.1.0-1
installed_packages: 2
build_script_revision: 0123456789abcdef0123456789abcdef01234567
--- /etc/pacman.conf (active lines)
[options]
DisableSandboxFilesystem
--- /etc/pacman.d/mirrorlist (active lines)
Server = http://mirror.archlinuxarm.org/$arch/$repo
""" % (name, sha, len(gz), len(raw)))
inp = os.path.join(w, "inputs"); os.makedirs(os.path.join(inp, "upstream"))
for n, c in (("capture.txt", "captured_by: build-rootfs.sh capture\n"), ("inputs.sha256", "x  y\n"),
             ("builder-tools.txt", "pacman 7.1.0-1\n"), ("upstream/ArchLinuxARM-aarch64-latest.tar.gz.sig", "sig"),
             ("upstream/ArchLinuxARM-aarch64-latest.tar.gz.headers", "Last-Modified: x\n")):
    open(os.path.join(inp, n), "w").write(c)
src = os.path.join(w, "sources"); os.makedirs(src)
rows = []
for base, ver, pk in (("bash", "5.3.3-2", "bash"), ("sudo", "1.9.17-1", "sudo")):
    a = "%s-%s.source.tar.gz" % (base, ver); blob = gzip.compress(base.encode(), mtime=0)
    open(os.path.join(src, a), "wb").write(blob)
    rows.append("\t".join([pk, ver, base, "archlinuxarm/PKGBUILDs core/%s @ abc" % base, a, str(len(blob)), hashlib.sha256(blob).hexdigest()]))
open(os.path.join(src, "SOURCES.tsv"), "w").write("# collected with archlinux:base-devel@sha256:00: pacman 7.1.0-1, git 2.55.0-1\n# package\tversion\n" + "\n".join(rows) + "\n")
open(os.path.join(w, "name"), "w").write(name + "\n" + sha + "\n" + str(len(gz)))
PY
NAME=$(sed -n 1p "$W/name"); SHA=$(sed -n 2p "$W/name"); SIZE=$(sed -n 3p "$W/name")

fail=0
check() { if eval "$2"; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi; }
check "compare accepts identical builds" 'python3 "$TOOL" compare "$W/out1" "$W/out2" >/dev/null'
cp -r "$W/out2" "$W/out3"; echo "extra" >> "$W/out3/$NAME.scan.txt"
check "compare refuses a differing scan" '! python3 "$TOOL" compare "$W/out1" "$W/out3" 2>/dev/null'
check "pin" 'python3 "$TOOL" pin "$W/out1" >/dev/null'
check "pin sets sha256, size, url, asset, date" 'grep -qx "sha256=$SHA" "$RELEASE_ROOTFS_DISTRO" && grep -qx "compressedSize=$SIZE" "$RELEASE_ROOTFS_DISTRO" && grep -qx "sourceUrl=https://github.com/Lord1Egypt/AndroidThothTerm/releases/download/arch-rootfs-aarch64-$(printf %.12s $SHA)/$NAME.tar.gz" "$RELEASE_ROOTFS_DISTRO" && grep -qx "assetName=$NAME.tgz" "$RELEASE_ROOTFS_DISTRO" && grep -qx "distroVersion=AArch64 (packages as of 2026-10-01)" "$RELEASE_ROOTFS_DISTRO"'
check "pin changes nothing else" '[ "$(diff "$R/garden-arch/src/main/assets/garden/distro.properties" "$RELEASE_ROOTFS_DISTRO" | grep -c "^>")" -eq 7 ]'
check "stage refuses before provenance" '! python3 "$TOOL" stage --inputs "$W/inputs" --sources "$W/sources" "$W/out1" "$W/stage" 2>/dev/null'
check "provenance" 'python3 "$TOOL" provenance --inputs "$W/inputs" --sources "$W/sources" "$W/out1" "$W/out2" >/dev/null'
check "provenance records sha256 and size" 'grep -q "$SHA" "$RELEASE_ROOTFS_PROVENANCE" && grep -q "$SIZE bytes" "$RELEASE_ROOTFS_PROVENANCE"'
check "provenance records 2 builds and the pacman.conf line" 'grep -q "was run 2 times" "$RELEASE_ROOTFS_PROVENANCE" && grep -qx "DisableSandboxFilesystem" "$RELEASE_ROOTFS_PROVENANCE"'
check "provenance refuses a package without source" 'printf "curl\t8.16.0-1\taarch64\tExplicitly installed\n" >> "$W/out1/$NAME.packages.tsv"; cp "$W/out1/$NAME.packages.tsv" "$W/out2/"; ! python3 "$TOOL" provenance --inputs "$W/inputs" --sources "$W/sources" "$W/out1" "$W/out2" 2>/dev/null'
check "stage" 'rm -rf "$W/stage"; python3 "$TOOL" stage --inputs "$W/inputs" --sources "$W/sources" "$W/out1" "$W/stage" >/dev/null'
check "staged SHA256SUMS verifies" '(cd "$W/stage" && sha256sum --quiet -c SHA256SUMS)'
check "staged the archive, sources and provenance" '[ -f "$W/stage/$NAME.tar.gz" ] && [ -f "$W/stage/SOURCES.tsv" ] && [ -f "$W/stage/bash-5.3.3-2.source.tar.gz" ] && [ -f "$W/stage/ROOTFS_PROVENANCE.md" ]'
exit $fail
