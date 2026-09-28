#!/usr/bin/env python3
"""Collect the corresponding source of every package in a ThothTerm Rolling rootfs.

    collect-sources.py INPUTS PACKAGES_TSV OUT

INPUTS is the directory build-rootfs.sh captured (its pkg/ holds the exact
binary packages), PACKAGES_TSV the rootfs's .packages.tsv, OUT a directory for
the result. Arch Linux ARM publishes no source packages and removes old
binaries from its mirrors, so the project keeps the source of what it
distributes itself, beside the rootfs.

For every package base in the rootfs (read from each binary's own .PKGINFO):

1. The recipe. Arch Linux ARM builds a package from its own PKGBUILD when it
   keeps one (github.com/archlinuxarm/PKGBUILDs, core/ extra/ alarm/), and
   otherwise from Arch Linux's (gitlab.archlinux.org/archlinux/packaging/
   packages/<base>). The ARM recipe is taken at the newest commit whose
   PKGBUILD declares exactly this epoch, pkgver and pkgrel; the Arch one at the
   tag of that version.
2. The source. `makepkg --allsource` in a throwaway archlinux:base-devel
   container (pinned by digest) downloads every source the recipe lists,
   checks each against the checksums the recipe pins, and writes
   <base>-<version>.src.tar.gz: recipe, patches and upstream sources.

Writes OUT/<base>-<version>.src.tar.gz and OUT/SOURCES.tsv (binary package,
version, base, recipe origin, source archive, size, SHA-256). A base whose
recipe cannot be matched exactly is listed as UNRESOLVED and the script exits
1: nothing is guessed.
"""
import hashlib
import io
import os
import re
import subprocess
import sys
import tarfile

BUILDER = ("archlinux:base-devel@sha256:"
           "{digest}")
ARM_REPO = "https://github.com/archlinuxarm/PKGBUILDs.git"
ARCH_REPO = "https://gitlab.archlinux.org/archlinux/packaging/packages/{base}.git"


def run(cmd, cwd=None, check=True):
    return subprocess.run(cmd, cwd=cwd, check=check, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout


def pkginfo(path):
    """The .PKGINFO fields of a binary package (.pkg.tar.xz or .zst)."""
    if path.endswith(".zst"):
        data = run(["bsdtar", "-xOf", path, ".PKGINFO"])
    else:
        with tarfile.open(path) as tar:
            data = tar.extractfile(".PKGINFO").read().decode()
    fields = {}
    for line in data.splitlines():
        if " = " in line and not line.startswith("#"):
            key, value = line.split(" = ", 1)
            fields.setdefault(key, value)
    return fields


def declared_version(pkgbuild):
    """epoch:pkgver-pkgrel from literal assignments, or None if not literal."""
    def value(name):
        m = re.search(r"^%s=([^\s#]+)" % name, pkgbuild, re.M)
        if not m:
            return None
        v = m.group(1).strip("'\"")
        return None if "$" in v or "(" in v else v
    ver, rel, epoch = value("pkgver"), value("pkgrel"), value("epoch")
    if not ver or not rel:
        return None
    return (epoch + ":" if epoch and epoch != "0" else "") + ver + "-" + rel


def arm_recipe(clone, base, version):
    """(dir, commit) in the ARM tree whose PKGBUILD declares exactly version."""
    for repo in ("core", "extra", "alarm"):
        rel = "%s/%s" % (repo, base)
        if not os.path.isdir(os.path.join(clone, rel)):
            continue
        commits = run(["git", "log", "--format=%H", "--", rel + "/PKGBUILD"], cwd=clone).split()
        for commit in commits:
            text = run(["git", "show", "%s:%s/PKGBUILD" % (commit, rel)], cwd=clone, check=False)
            if declared_version(text) == version:
                return rel, commit
        return rel, None
    return None, None


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    inputs, tsv, out = (os.path.abspath(a) for a in sys.argv[1:4])
    digest = os.environ["BUILDER_DIGEST"]
    os.makedirs(out, exist_ok=True)
    work = os.path.join(out, ".work")
    os.makedirs(work, exist_ok=True)

    installed = {}
    with open(tsv) as f:
        for line in f:
            name, version = line.split("\t")[:2]
            installed[name] = version
    files = {}
    for name in os.listdir(os.path.join(inputs, "pkg")):
        if ".pkg.tar." in name and not name.endswith(".sig"):
            info = pkginfo(os.path.join(inputs, "pkg", name))
            if installed.get(info["pkgname"]) == info["pkgver"]:
                files[info["pkgname"]] = info
    missing = sorted(set(installed) - set(files))
    if missing:
        sys.exit("no captured binary for: " + " ".join(missing))

    bases = {}
    for name, info in sorted(files.items()):
        bases.setdefault((info.get("pkgbase", name), info["pkgver"]), []).append(name)

    clone = os.path.join(work, "PKGBUILDs")
    if not os.path.isdir(clone):
        run(["git", "clone", "--quiet", "--filter=blob:none", ARM_REPO, clone])

    rows, unresolved = [], []
    for (base, version), names in sorted(bases.items()):
        recipe = os.path.join(work, "recipes", base)
        run(["rm", "-rf", recipe])
        os.makedirs(recipe)
        rel, commit = arm_recipe(clone, base, version)
        if rel and commit:
            archive = subprocess.run(["git", "archive", "--format=tar", commit, rel],
                                     cwd=clone, check=True, stdout=subprocess.PIPE).stdout
            with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
                tar.extractall(recipe, filter="data")
            recipe_dir = os.path.join(recipe, rel)
            origin = "archlinuxarm/PKGBUILDs %s @ %s" % (rel, commit)
        elif rel:
            unresolved.append("%s %s: ARM recipe %s declares no such version" % (base, version, rel))
            continue
        else:
            tag = version.replace(":", "-")
            url = ARCH_REPO.format(base=base)
            got = subprocess.run(["git", "clone", "--quiet", "--depth", "1", "--branch", tag, url, recipe],
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            if got.returncode != 0:
                unresolved.append("%s %s: no Arch tag %s" % (base, version, tag))
                continue
            commit = run(["git", "rev-parse", "HEAD"], cwd=recipe).strip()
            run(["rm", "-rf", os.path.join(recipe, ".git")])
            recipe_dir = recipe
            origin = "archlinux packaging/%s tag %s @ %s" % (base, tag, commit)

        # makepkg refuses root; the container's own "builder" user runs it.
        script = ("set -e; useradd -m builder 2>/dev/null || true; chown -R builder /r /o; "
                  "cd /r && su builder -c 'makepkg --allsource --skippgpcheck --nocolor SRCPKGDEST=/o' "
                  "> /o/.log-%s 2>&1" % base)
        result = subprocess.run(["docker", "run", "--rm", "-v", recipe_dir + ":/r", "-v", out + ":/o",
                                 BUILDER.format(digest=digest), "bash", "-c", script],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        produced = [n for n in os.listdir(out) if n.endswith(".src.tar.gz") and n.startswith(base + "-")
                    and n[len(base) + 1:].startswith(version.split(":")[-1])]
        if result.returncode != 0 or not produced:
            unresolved.append("%s %s: makepkg --allsource failed (see %s/.log-%s)" % (base, version, out, base))
            continue
        src = produced[0]
        size = os.path.getsize(os.path.join(out, src))
        digest_hex = sha256(os.path.join(out, src))
        for name in names:
            rows.append("\t".join([name, version, base, origin, src, str(size), digest_hex]))
        print("ok %-28s %-40s %s" % (base, version, origin), flush=True)

    with open(os.path.join(out, "SOURCES.tsv"), "w") as f:
        f.write("# package\tversion\tpkgbase\trecipe\tsource archive\tsize\tsha256\n")
        f.write("\n".join(rows) + "\n")
    for u in unresolved:
        print("UNRESOLVED " + u)
    sys.exit(1 if unresolved else 0)


if __name__ == "__main__":
    main()
