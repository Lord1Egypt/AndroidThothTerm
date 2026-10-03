#!/usr/bin/env python3
"""Collect the corresponding source of every package in a ThothTerm BlackArch rootfs.

    collect-sources.py INPUTS PACKAGES_TSV OUT --reuse ROLLING_SOURCES

INPUTS is the directory build-rootfs.sh built from (its pkg/ holds the Arch
Linux ARM binaries, its blackarch/ the BlackArch keyring package), PACKAGES_TSV
the rootfs's .packages.tsv, OUT a directory for the result.

The rootfs is ThothTerm Rolling's package set plus one BlackArch package,
blackarch-keyring. So there are two kinds of rows:

* An Arch Linux ARM package. Its corresponding source is collected by
  garden-arch/rootfs/collect-sources.py, which needs the recipe at the exact
  version and every upstream source verified against the recipe's checksums.
  When the package (name and version) is one that ThothTerm Rolling already
  collected, ROLLING_SOURCES (a directory holding that SOURCES.tsv and its
  archives) is reused: the row and archive are copied after the archive's size
  and SHA-256 are checked against the row. A package Rolling did not collect
  is UNRESOLVED here: run Rolling's collector for it, then re-run this one.
* blackarch-keyring, collected here. The binary package records in its own
  .BUILDINFO the SHA-256 of the PKGBUILD it was built from. The script finds
  that PKGBUILD in github.com/BlackArch/blackarch (packages/blackarch-keyring),
  at the commit pinned below, requires the SHA-256 to match, downloads the
  sources the recipe lists and checks each against the recipe's checksum
  (sha512; a SKIP is only accepted for a detached signature, which is then
  verified against the pinned BlackArch keys), and archives the recipe, its
  install script and hook, and the sources.

Writes OUT/<base>-<version>.source.tar.gz and OUT/SOURCES.tsv. Anything that
cannot be matched exactly is listed as UNRESOLVED and the script exits 1:
nothing is guessed.
"""
import hashlib
import io
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))

# github.com/BlackArch/blackarch: the commit whose packages/blackarch-keyring/
# PKGBUILD has the SHA-256 recorded in blackarch-keyring-20251011-2's .BUILDINFO
# (pkgver=20251011, pkgrel=2). Found 2026-10-03; the script re-checks it.
BLACKARCH_RECIPE_REPO = "https://github.com/BlackArch/blackarch.git"
BLACKARCH_RECIPE_COMMIT = "eb863fdeb5cc41204d226812d4408d182f1ada46"
BLACKARCH_PACKAGE = "blackarch-keyring"


def sha(data, algo="sha256"):
    return hashlib.new(algo, data).hexdigest()


def file_sha(path, algo="sha256"):
    h = hashlib.new(algo)
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def run(cmd, **kw):
    return subprocess.run(cmd, check=True, capture_output=True, text=True, **kw).stdout


def read_tsv(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            rows.append(line.rstrip("\n").split("\t"))
    return rows


def pins():
    values = {}
    with open(os.path.join(HERE, "keyring.pins"), encoding="utf-8") as f:
        for line in f:
            m = re.match(r"^([A-Z0-9_]+)=(.*)$", line.rstrip("\n"))
            if m:
                values[m.group(1)] = m.group(2).strip("\"'")
    return values


def buildinfo_pkgbuild_sha(package_file):
    # Python's tarfile cannot read zstd before 3.14: let tar (or bsdtar) do it.
    for cmd in (["tar", "--zstd", "-xOf", package_file, ".BUILDINFO"], ["bsdtar", "-xOf", package_file, ".BUILDINFO"]):
        try:
            text = run(cmd)
            break
        except (OSError, subprocess.CalledProcessError):
            continue
    else:
        raise SystemExit("UNRESOLVED blackarch-keyring: cannot read .BUILDINFO (needs tar with zstd, or bsdtar)")
    return re.search(r"^pkgbuild_sha256sum = ([0-9a-f]{64})$", text, re.M).group(1)


def collect_blackarch_keyring(inputs, out, version):
    p = pins()
    if version != p["KEYRING_VERSION"]:
        raise SystemExit("UNRESOLVED blackarch-keyring %s: keyring.pins pins %s" % (version, p["KEYRING_VERSION"]))
    package_file = os.path.join(inputs, "blackarch", p["KEYRING_FILE"])
    want_pkgbuild = buildinfo_pkgbuild_sha(package_file)
    work = tempfile.mkdtemp(prefix="ba-src.")
    try:
        repo = os.path.join(work, "recipe-repo")
        run(["git", "clone", "-q", "--filter=blob:none", "--no-checkout", "--sparse", BLACKARCH_RECIPE_REPO, repo])
        run(["git", "-C", repo, "sparse-checkout", "set", "packages/" + BLACKARCH_PACKAGE])
        run(["git", "-C", repo, "checkout", "-q", BLACKARCH_RECIPE_COMMIT])
        recipe_dir = os.path.join(repo, "packages", BLACKARCH_PACKAGE)
        pkgbuild = open(os.path.join(recipe_dir, "PKGBUILD"), "rb").read()
        if sha(pkgbuild) != want_pkgbuild:
            raise SystemExit("UNRESOLVED blackarch-keyring: PKGBUILD at %s has SHA-256 %s, the binary was built from %s"
                             % (BLACKARCH_RECIPE_COMMIT, sha(pkgbuild), want_pkgbuild))
        text = pkgbuild.decode()
        pkgver = re.search(r"^pkgver=(\S+)$", text, re.M).group(1)
        pkgrel = re.search(r"^pkgrel=(\S+)$", text, re.M).group(1)
        if "%s-%s" % (pkgver, pkgrel) != version:
            raise SystemExit("UNRESOLVED blackarch-keyring: the recipe is %s-%s, not %s" % (pkgver, pkgrel, version))
        sources = re.search(r"^source=\((.*?)\)", text, re.M | re.S).group(1)
        urls = re.findall(r"[\"']([^\"']+)[\"']", sources)
        sums = re.findall(r"[\"']([0-9a-f]{128}|SKIP)[\"']", re.search(r"^sha512sums=\((.*?)\)", text, re.M | re.S).group(1))
        if len(urls) != len(sums):
            raise SystemExit("UNRESOLVED blackarch-keyring: %d sources, %d checksums" % (len(urls), len(sums)))
        srcdir = os.path.join(work, "sources")
        os.makedirs(srcdir)
        fetched = {}
        for url, want in zip(urls, sums):
            url = url.replace("$pkgver", pkgver)
            name = url.rsplit("/", 1)[-1]
            if "://" not in url:  # a local file the recipe ships
                data = open(os.path.join(recipe_dir, url), "rb").read()
            else:
                if not url.startswith("https://"):
                    raise SystemExit("UNRESOLVED blackarch-keyring: refusing non-HTTPS source " + url)
                with urllib.request.urlopen(url, timeout=60) as r:
                    data = r.read()
            if want != "SKIP" and sha(data, "sha512") != want:
                raise SystemExit("UNRESOLVED blackarch-keyring: %s does not match the recipe's sha512" % name)
            if want == "SKIP" and not name.endswith(".sig"):
                raise SystemExit("UNRESOLVED blackarch-keyring: %s is unchecked (SKIP) and is not a signature" % name)
            fetched[name] = data
            open(os.path.join(srcdir, name), "wb").write(data)
        for name, data in fetched.items():
            if name.endswith(".sig"):
                signed = name[:-4]
                gnupg = os.path.join(work, "gnupg")
                os.makedirs(gnupg, mode=0o700)
                env = dict(os.environ, GNUPGHOME=gnupg)
                subprocess.run(["gpg", "-q", "--batch", "--import", os.path.join(HERE, "blackarch.gpg")], check=True, env=env,
                               capture_output=True)
                status = subprocess.run(["gpg", "--batch", "--status-fd", "1", "--verify", os.path.join(srcdir, name),
                                         os.path.join(srcdir, signed)], env=env, capture_output=True, text=True).stdout
                m = re.search(r"^\[GNUPG:\] VALIDSIG (\S+) .* (\S+)$", status, re.M)
                if not m or m.group(2) not in p["KEYRING_ALL_KEYS"].split():
                    raise SystemExit("UNRESOLVED blackarch-keyring: %s is not signed by a pinned BlackArch key" % name)
        archive = "%s-%s.source.tar.gz" % (BLACKARCH_PACKAGE, version)
        with tarfile.open(os.path.join(out, archive), "w:gz", format=tarfile.GNU_FORMAT) as t:
            def add(path, arcname):
                info = t.gettarinfo(path, arcname)
                info.uid = info.gid = 0
                info.uname = info.gname = ""
                info.mtime = 0
                info.mode = 0o644 if info.isfile() else 0o755
                t.addfile(info, open(path, "rb") if info.isfile() else None)
            for root, dirs, files in os.walk(recipe_dir):
                dirs.sort()
                for f in sorted(files):
                    add(os.path.join(root, f), "recipe/" + os.path.relpath(os.path.join(root, f), recipe_dir))
            for f in sorted(os.listdir(srcdir)):
                add(os.path.join(srcdir, f), "sources/" + f)
        size = os.path.getsize(os.path.join(out, archive))
        return [BLACKARCH_PACKAGE, version, BLACKARCH_PACKAGE,
                "BlackArch/blackarch packages/blackarch-keyring @ %s (PKGBUILD sha256 %s)" % (BLACKARCH_RECIPE_COMMIT, want_pkgbuild),
                archive, str(size), file_sha(os.path.join(out, archive))]
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main(argv):
    if len(argv) != 6 or argv[4] != "--reuse":
        sys.stderr.write(__doc__)
        return 2
    inputs, packages_tsv, out, reuse = argv[1], argv[2], argv[3], argv[5]
    os.makedirs(out, exist_ok=True)
    rolling = {(r[0], r[1]): r for r in read_tsv(os.path.join(reuse, "SOURCES.tsv"))}
    rows, unresolved = [], []
    for name, version, _arch, _reason in read_tsv(packages_tsv):
        if name == BLACKARCH_PACKAGE:
            try:
                rows.append(collect_blackarch_keyring(inputs, out, version))
            except SystemExit as e:
                unresolved.append(str(e))
            continue
        row = rolling.get((name, version))
        if row is None:
            unresolved.append("UNRESOLVED %s %s: ThothTerm Rolling did not collect this version; run garden-arch/rootfs/collect-sources.py" % (name, version))
            continue
        archive = os.path.join(reuse, row[4])
        if not os.path.isfile(archive) or os.path.getsize(archive) != int(row[5]) or file_sha(archive) != row[6]:
            unresolved.append("UNRESOLVED %s %s: the reused archive %s does not match its row" % (name, version, row[4]))
            continue
        target = os.path.join(out, row[4])
        if not os.path.exists(target):
            shutil.copyfile(archive, target)
        rows.append(row)
    rows.sort(key=lambda r: (r[0], r[1]))
    with open(os.path.join(out, "SOURCES.tsv"), "w", encoding="utf-8") as f:
        f.write("# ThothTerm BlackArch rootfs: corresponding source of every installed package\n")
        f.write("# Arch Linux ARM rows reused from ThothTerm Rolling's collection (each archive re-checked by size and SHA-256)\n")
        f.write("# package\tversion\tpkgbase\trecipe\tsource archive\tsize\tsha256\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
        for u in unresolved:
            f.write("# " + u + "\n")
    for u in unresolved:
        sys.stderr.write(u + "\n")
    print("%d rows, %d unresolved" % (len(rows), len(unresolved)))
    return 1 if unresolved else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
