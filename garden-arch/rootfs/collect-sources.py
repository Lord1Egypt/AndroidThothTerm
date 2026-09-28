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
2. The source. In a throwaway container (archlinux:base-devel pinned by
   digest, plus git), `makepkg --verifysource` downloads every source the
   recipe lists, for every architecture, and checks each against the
   checksums the recipe pins. Files are kept as downloaded; a git source is
   exported with `git archive` at exactly the commit or tag the recipe pins,
   which is the tree the package was built from (a whole mirror of, say,
   gcc.git would not fit a release asset).
3. <base>-<version>.source.tar.gz holds recipe/ (PKGBUILD, patches, install
   files as the recipe has them) and sources/.

Writes OUT/<base>-<version>.source.tar.gz and OUT/SOURCES.tsv (binary
package, version, base, recipe origin, source archive, size, SHA-256). A base
whose recipe cannot be matched exactly, or whose sources do not all verify, is
listed as UNRESOLVED and the script exits 1: nothing is guessed.
"""
import hashlib
import io
import os
import re
import subprocess
import sys
import tarfile

BASE_IMAGE = "archlinux:base-devel@sha256:{digest}"
TOOL_IMAGE = "thothterm-source-collector:{digest12}"

# Runs as the unprivileged "builder" user in /r (the recipe). Exports every
# source into /o/<base>/sources.
COLLECT = r"""
set -euo pipefail
cd /r
makepkg --printsrcinfo > /tmp/srcinfo
makepkg --verifysource --skippgpcheck --nocolor SRCDEST=/srcdest > /tmp/verify.log 2>&1 \
    || { tail -20 /tmp/verify.log; exit 1; }
out=/o/$BASE/sources
mkdir -p "$out"
sed -n 's/^\tsource\(_[a-z0-9_]*\)\? = //p' /tmp/srcinfo | sort -u | while read -r entry; do
    name=${entry%%::*}; [ "$name" = "$entry" ] && name=
    url=${entry#*::}
    case $url in
        git+*|git://*)
            frag=${url#*#}; [ "$frag" = "$url" ] && frag=
            frag=${frag%%\?*}
            repo=${url%%#*}; repo=${repo%%\?*}; repo=${repo#git+}
            [ -n "$name" ] || { name=${repo##*/}; name=${name%.git}; }
            case $frag in
                commit=*) ref=${frag#commit=} ;;
                tag=*) ref=refs/tags/${frag#tag=} ;;
                branch=*) ref=refs/heads/${frag#branch=} ;;
                *) ref=HEAD ;;
            esac
            commit=$(git -C "/srcdest/$name" rev-parse "$ref^{commit}")
            git -C "/srcdest/$name" archive --format=tar --prefix="$name/" "$commit" | gzip -n -9 > "$out/$name-$commit.tar.gz"
            printf 'git\t%s\t%s\t%s\n' "$repo" "$commit" "$name-$commit.tar.gz" >> "/o/$BASE/sources.tsv" ;;
        *://*)
            file=${name:-${url##*/}}
            cp -L "/srcdest/$file" "$out/$file"
            printf 'file\t%s\t-\t%s\n' "$url" "$file" >> "/o/$BASE/sources.tsv" ;;
        *) ;;  # a local file: part of the recipe
    esac
done
"""
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
    digest = os.environ["BUILDER_DIGEST"]  # archlinux:base-devel
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

    tool_image = TOOL_IMAGE.format(digest12=digest[:12])
    subprocess.run(["docker", "build", "-q", "-t", tool_image, "-"], check=True, text=True,
                   input="FROM %s\nRUN pacman -Syu --noconfirm git && pacman -Scc --noconfirm "
                         "&& useradd -m builder\n" % BASE_IMAGE.format(digest=digest),
                   stdout=subprocess.DEVNULL)
    tools = run(["docker", "run", "--rm", tool_image, "pacman", "-Q", "pacman", "git"]).strip()
    srcdest = os.path.join(work, "srcdest")
    os.makedirs(srcdest, exist_ok=True)

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

        stage = os.path.join(work, "stage")
        run(["rm", "-rf", stage])
        os.makedirs(stage)
        # makepkg refuses root; the image's own "builder" user runs it.
        os.makedirs(os.path.join(stage, base, "sources"))
        with open(os.path.join(stage, "collect.sh"), "w") as f:
            f.write(COLLECT)
        result = subprocess.run(["docker", "run", "--rm", "-v", recipe_dir + ":/r", "-v", stage + ":/o",
                                 "-v", srcdest + ":/srcdest", tool_image, "bash", "-c",
                                 "chown -R builder /r /o /srcdest && su builder -c 'BASE=%s bash /o/collect.sh'"
                                 % base],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        os.remove(os.path.join(stage, "collect.sh"))
        with open(os.path.join(out, ".log-" + base), "w") as log:
            log.write(result.stdout)
        if result.returncode != 0:
            unresolved.append("%s %s: sources did not all download and verify (see %s/.log-%s)"
                              % (base, version, out, base))
            continue
        # recipe/ as the recipe has it, sources/ as verified and exported.
        run(["cp", "-a", recipe_dir, os.path.join(stage, base, "recipe")])
        src = "%s-%s.source.tar.gz" % (base, version.replace(":", "-"))
        run(["sh", "-c", "tar --sort=name --owner=0 --group=0 --numeric-owner --mtime=@0 "
                         "-C %s -cf - %s | gzip -n -9 > %s" % (stage, base, os.path.join(out, src))])
        size = os.path.getsize(os.path.join(out, src))
        digest_hex = sha256(os.path.join(out, src))
        for name in names:
            rows.append("\t".join([name, version, base, origin, src, str(size), digest_hex]))
        print("ok %-28s %-40s %s" % (base, version, origin), flush=True)

    with open(os.path.join(out, "SOURCES.tsv"), "w") as f:
        f.write("# collected with %s: %s\n" % (BASE_IMAGE.format(digest=digest), tools.replace("\n", ", ")))
        f.write("# package\tversion\tpkgbase\trecipe\tsource archive\tsize\tsha256\n")
        f.write("\n".join(rows) + "\n")
    for u in unresolved:
        print("UNRESOLVED " + u)
    sys.exit(1 if unresolved else 0)


if __name__ == "__main__":
    main()
