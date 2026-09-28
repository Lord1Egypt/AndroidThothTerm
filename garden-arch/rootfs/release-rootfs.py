#!/usr/bin/env python3
"""Release-day steps for the ThothTerm Rolling rootfs, after build-rootfs.sh.

    release-rootfs.py compare OUT1 OUT2 [OUT3 ...]
        The builds must agree byte for byte: same archive name, SHA-256,
        package list and manifest. Prints the shared name.

    release-rootfs.py pin OUT
        Points garden-arch/src/main/assets/garden/distro.properties at OUT's
        archive: imageId, sourceUrl, assetName, sha256, compressedSize,
        uncompressedSize and the "packages as of" date. Nothing else changes.

    release-rootfs.py provenance --inputs INPUTS --sources SOURCES OUT1 OUT2 [...]
        Writes docs/garden/arch/ROOTFS_PROVENANCE.md from the builds (compared
        first), the captured inputs and collect-sources.py's output.

    release-rootfs.py stage --inputs INPUTS --sources SOURCES OUT DEST
        Copies every asset of the GitHub release arch-rootfs-aarch64-<sha12>
        into DEST, with SHA256SUMS, and prints the gh command that publishes
        it. It publishes nothing itself.

    release-rootfs.py verify-published OUT [DOWNLOAD_DIR]
        Downloads the published archive and SHA256SUMS back from GitHub and
        checks them against OUT and distro.properties.

Standard library only. Paths are resolved from this script's location.
"""
import datetime
import hashlib
import os
import re
import shutil
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
# Overridable for tests/garden-arch/host/release-rootfs-selftest.sh only.
DISTRO = os.environ.get("RELEASE_ROOTFS_DISTRO",
                        os.path.join(REPO, "garden-arch/src/main/assets/garden/distro.properties"))
PROVENANCE = os.environ.get("RELEASE_ROOTFS_PROVENANCE",
                            os.path.join(REPO, "docs/garden/arch/ROOTFS_PROVENANCE.md"))
GITHUB = "https://github.com/Lord1Egypt/AndroidThothTerm/releases/download"
NAME = re.compile(r"^thothterm-arch-aarch64-rootfs-([0-9a-f]{12})\.tar\.gz$")


def die(message):
    sys.exit("release-rootfs: " + message)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


class Build:
    """One build-rootfs.sh output directory."""

    def __init__(self, out):
        self.out = os.path.abspath(out)
        archives = [n for n in sorted(os.listdir(self.out)) if NAME.match(n)]
        if len(archives) != 1:
            die("%s must hold exactly one rootfs archive, not %d" % (out, len(archives)))
        self.file = archives[0]
        self.name = self.file[:-len(".tar.gz")]
        self.path = os.path.join(self.out, self.file)
        self.sha256 = sha256(self.path)
        if not self.sha256.startswith(NAME.match(self.file).group(1)):
            die("%s is not named after its SHA-256 %s" % (self.file, self.sha256))
        self.size = os.path.getsize(self.path)
        recorded = read(self.side(".tar.gz.sha256")).split()[0]
        if recorded != self.sha256:
            die("%s.sha256 records %s" % (self.file, recorded))
        self.manifest_text = read(self.side(".manifest.txt"))
        self.manifest = {}
        self.sections = {}
        section = None
        for line in self.manifest_text.splitlines():
            if line.startswith("--- "):
                section = line[4:]
                self.sections[section] = []
            elif section is not None:
                self.sections[section].append(line)
            elif ": " in line and not line.startswith("#"):
                key, value = line.split(": ", 1)
                self.manifest.setdefault(key, value)
        for key, want in (("sha256", self.sha256), ("size", str(self.size)), ("file", self.file)):
            if self.manifest.get(key) != want:
                die("%s manifest says %s: %s, the file has %s" % (out, key, self.manifest.get(key), want))
        if self.manifest.get("build_script_revision") == "uncommitted":
            die("%s was built from an uncommitted recipe" % out)

    def side(self, suffix):
        return os.path.join(self.out, self.name + suffix)

    def epoch_date(self):
        return datetime.datetime.fromtimestamp(int(self.manifest["epoch"]), datetime.timezone.utc)


def compare(outs):
    if len(outs) < 2:
        die("reproducibility needs at least two builds")
    builds = [Build(o) for o in outs]
    first = builds[0]
    for b in builds[1:]:
        for what, x, y in (("archive", first.sha256, b.sha256),
                           ("package list", sha256(first.side(".packages.tsv")), sha256(b.side(".packages.tsv"))),
                           ("manifest", first.manifest_text, b.manifest_text),
                           ("scan", sha256(first.side(".scan.txt")), sha256(b.side(".scan.txt")))):
            if x != y:
                die("%s differs between %s and %s" % (what, first.out, b.out))
    return builds


def set_properties(text, values):
    done = set()
    lines = []
    for line in text.splitlines(keepends=True):
        key = line.split("=", 1)[0] if "=" in line and not line.startswith("#") else None
        if key in values:
            lines.append("%s=%s\n" % (key, values[key]))
            done.add(key)
        else:
            lines.append(line)
    missing = set(values) - done
    if missing:
        die("distro.properties has no " + ", ".join(sorted(missing)))
    return "".join(lines)


def pin(out):
    b = Build(out)
    sha12 = b.sha256[:12]
    values = {
        "imageId": "arch-aarch64-" + sha12,
        "sourceUrl": "%s/arch-rootfs-aarch64-%s/%s" % (GITHUB, sha12, b.file),
        "assetName": b.name + ".tgz",
        "sha256": b.sha256,
        "compressedSize": str(b.size),
        "uncompressedSize": b.manifest["uncompressed_size"],
        "distroVersion": "AArch64 (packages as of %s)" % b.epoch_date().strftime("%Y-%m-%d"),
    }
    text = read(DISTRO)
    new = set_properties(text, values)
    with open(DISTRO, "w", encoding="utf-8") as f:
        f.write(new)
    print("pinned %s (%d bytes) in %s" % (b.file, b.size, os.path.relpath(DISTRO, REPO)))
    return b


def sources_summary(sources):
    tsv = os.path.join(sources, "SOURCES.tsv")
    rows = [l.rstrip("\n").split("\t") for l in read(tsv).splitlines() if l and not l.startswith("#")]
    header = [l for l in read(tsv).splitlines() if l.startswith("# collected with")]
    archives = {}
    for r in rows:
        archives[r[4]] = (int(r[5]), r[6])
    for name, (size, digest) in archives.items():
        path = os.path.join(sources, name)
        if not os.path.isfile(path) or os.path.getsize(path) != size or sha256(path) != digest:
            die("source archive %s does not match SOURCES.tsv" % name)
    arm = len({r[2] for r in rows if r[3].startswith("archlinuxarm/")})
    return {
        "packages": len(rows),
        "bases": len({r[2] for r in rows}),
        "arm_recipes": arm,
        "archives": len(archives),
        "bytes": sum(s for s, _ in archives.values()),
        "collector": header[0][2:].capitalize() if header else "",
    }


def provenance(inputs, sources, outs):
    builds = compare(outs)
    b = builds[0]
    tsv = read(b.side(".packages.tsv")).splitlines()
    for r in tsv:
        pkg = r.split("\t")[0]
        if not any(line.startswith(pkg + "\t") for line in read(os.path.join(sources, "SOURCES.tsv")).splitlines()):
            die("no corresponding source recorded for " + pkg)
    src = sources_summary(sources)
    m = b.manifest
    capture = read(os.path.join(inputs, "capture.txt"))
    manifest_block = "\n".join(l for l in b.manifest_text.splitlines()
                               if not l.startswith("--- ") and not l.startswith("#")
                               and l.split(": ", 1)[0] not in ("file", "sha256", "size")
                               and l not in sum(b.sections.values(), []))
    sha12 = b.sha256[:12]
    doc = f"""# ThothTerm Rolling — rootfs provenance

The Arch Linux ARM userland ThothTerm Rolling installs is built by
`garden-arch/rootfs/build-rootfs.sh` and described here by
`garden-arch/rootfs/release-rootfs.py provenance`, from the build outputs
themselves. Arch Linux ARM publishes no minimal root filesystem for a PRoot
guest, so the project assembles one with pacman, from Arch Linux ARM's own
signed packages, the way `pacstrap` does.

Every package in the archive is an unmodified Arch Linux ARM binary package,
verified by pacman against the Arch Linux ARM keyring when it was installed.
What the build script changes besides is listed under "Changes from a plain
pacstrap".

## The pinned archive

| | |
|---|---|
| File | `{b.file}` |
| SHA-256 | `{b.sha256}` |
| Size | {b.size} bytes (uncompressed tar {m["uncompressed_size"]} bytes) |
| Packages | {m["installed_packages"]} (the exact list: `{b.name}.packages.tsv`) |
| State | {m["state"]}: {m["sync_finished"]} |
| Published at | GitHub release `arch-rootfs-aarch64-{sha12}`, with its manifest, package list, scan, the upstream image's signature and headers, the captured inputs' checksums, the corresponding source and `SHA256SUMS` |
| Pinned in | `garden-arch/src/main/assets/garden/distro.properties` |

The name carries the first 12 hex digits of the SHA-256, so a name can never
stand for two different archives. The release tag does not match
`^arch-v[0-9.]+$`, so F-Droid's update check never mistakes it for an app
release.

## How it was built

1. The official `ArchLinuxARM-aarch64-latest.tar.gz` is verified with `gpgv`
   against the Arch Linux ARM Build System key
   (`68B3537F39A313B3E574D06777193F152BDBE6A6`), taken from the pinned
   `garden-arch/rootfs/archlinuxarm.gpg`, before anything else touches it.
   It is the build environment and the root of trust, not the product.
2. It runs as an arm64 container (qemu-user through binfmt_misc) with a
   throwaway keyring (`pacman-key --init`, `--populate archlinuxarm`), and is
   brought current with one complete `pacman -Syu`.
3. That pacman installs `garden-arch/rootfs/packages.txt` into an empty root,
   at the image's own SigLevel, from the same synced databases; a second
   `-Su` must find nothing to do, and `-Dk`, `-Qk` and `ldd` checks must pass.
4. `capture` keeps the synced databases and every package; `build` repeats
   steps 2-3 offline (`--network none`) from exactly those files.

```
{manifest_block}
```

Upstream image and capture, as recorded by `build-rootfs.sh capture`:

```
{capture.strip()}
```

## Reproducibility

`build-rootfs.sh build` was run {len(builds)} times from the same captured
inputs, each into its own output directory. Every run produced the same
archive byte for byte (SHA-256 above), and the same package list, manifest and
scan. Anyone with the published inputs can re-derive the SHA-256:

    garden-arch/rootfs/build-rootfs.sh build     # docker + aarch64 binfmt (qemu-user)

## Package sources inside the guest

```
{chr(10).join(b.sections.get("/etc/pacman.d/mirrorlist (active lines)", []))}
```

`/etc/pacman.conf`, active lines:

```
{chr(10).join(b.sections.get("/etc/pacman.conf (active lines)", []))}
```

No sync databases ship: the first command is `sudo pacman -Syu`, which
fetches current, signed databases. Until then pacman prints "database file
for 'core' does not exist" (see `PACKAGE_MANAGER.md`).

## Changes from a plain pacstrap

- `/etc/pacman.conf` sets `DisableSandboxFilesystem`: Android kernels have no
  Landlock, and pacman 7's downloader fails without it. The seccomp filter,
  `DownloadUser = alpm` and every signature check stay on.
- `/etc/machine-id` is empty and `/var/lib/dbus/machine-id` absent: a machine
  identity must not be shared.
- `/etc/hostname` is `thothterm`; `/etc/resolv.conf` is a one-line comment
  (the app bind-mounts the resolver Android is using over it);
  `/etc/locale.conf` is `LANG=C.UTF-8`.
- Every account is locked in `/etc/shadow`; there is no password anywhere.
- `/usr/share/thothterm/signature-check/` holds the installed
  `archlinuxarm-keyring` package and its detached signature, exactly as the
  repository served them, so the app can prove offline at first run that the
  keyring it creates verifies the Build System key. pacman does not own them.
- No pacman keyring (`/etc/pacman.d/gnupg`), sync databases, package cache,
  lock or log. Each installation creates its own keyring at first run.
- pacman's `%INSTALLDATE%` and every file time newer than the capture are
  clamped to the capture moment (epoch {m["epoch"]}); the tar is GNU format,
  sorted, numeric owners, `gzip -9 -n`.

The app adds the rest at first run: the `thoth` account (1000:1000,
`/home/thoth`, bash) in `wheel`, a `NOPASSWD` sudoers entry, the Android
supplementary groups, `/etc/hosts` entries, the welcome banner and the edition
file it reads, and the installation's own pacman keyring (`pacman-key
--init`, `--populate archlinuxarm`), which must verify before setup completes.

## Hygiene scan of the archive

```
{read(b.side(".scan.txt")).strip()}
```

## Corresponding source

Arch Linux ARM publishes no source packages and removes superseded binaries
from its mirrors, so the project publishes the source of what it distributes
in the same release. `garden-arch/rootfs/collect-sources.py` took, for each of
the {src["bases"]} package bases, the exact recipe ({src["arm_recipes"]} from
archlinuxarm/PKGBUILDs at the commit declaring the version, the rest from Arch
Linux's packaging repository at the version's tag) and every source it lists
for aarch64, each verified by `makepkg --verifysource` against the recipe's
checksums. {src["archives"]} archives, {src["bytes"]} bytes in all, cover all
{src["packages"]} installed packages; `SOURCES.tsv` maps each package to its
archive and SHA-256. {src["collector"]}.

This is a practical description, not legal advice.
"""
    with open(PROVENANCE, "w", encoding="utf-8") as f:
        f.write(doc)
    print("wrote %s for %s (%d identical builds)" % (os.path.relpath(PROVENANCE, REPO), b.file, len(builds)))


def stage(inputs, sources, out, dest):
    b = Build(out)
    os.makedirs(dest, exist_ok=True)
    if os.listdir(dest):
        die(dest + " must be empty")
    files = [b.path] + [b.side(s) for s in (".tar.gz.sha256", ".manifest.txt", ".packages.tsv", ".scan.txt")]
    up = os.path.join(inputs, "upstream")
    files += [os.path.join(up, n) for n in sorted(os.listdir(up))
              if n.endswith((".sig", ".md5", ".headers")) and not n.endswith(".part")]
    files += [os.path.join(inputs, n) for n in ("capture.txt", "inputs.sha256", "builder-tools.txt")]
    files += [os.path.join(sources, n) for n in sorted(os.listdir(sources))
              if n == "SOURCES.tsv" or n.endswith(".source.tar.gz")]
    if os.path.isfile(PROVENANCE) and b.sha256 in read(PROVENANCE):
        files.append(PROVENANCE)
    else:
        die("write ROOTFS_PROVENANCE.md for this archive first (provenance)")
    names = set()
    for f in files:
        name = os.path.basename(f)
        if name in names:
            die("two assets named " + name)
        names.add(name)
        shutil.copy2(f, os.path.join(dest, name))
    with open(os.path.join(dest, "SHA256SUMS"), "w") as f:
        for name in sorted(names):
            f.write("%s  %s\n" % (sha256(os.path.join(dest, name)), name))
    tag = "arch-rootfs-aarch64-" + b.sha256[:12]
    print("staged %d assets in %s" % (len(names) + 1, dest))
    print("publish (not done by this script):")
    print("  gh release create %s --repo Lord1Egypt/AndroidThothTerm --target <commit> --latest=false \\" % tag)
    print("     --title 'Arch Linux ARM aarch64 rootfs %s — for ThothTerm Rolling' --notes-file <notes> %s/*"
          % (b.sha256[:12], dest))


def verify_published(out, download):
    b = Build(out)
    tag = "arch-rootfs-aarch64-" + b.sha256[:12]
    download = download or os.path.join(b.out, "published")
    os.makedirs(download, exist_ok=True)
    props = dict(l.split("=", 1) for l in read(DISTRO).splitlines() if "=" in l and not l.startswith("#"))
    ok = True
    for name in (b.file, "SHA256SUMS"):
        target = os.path.join(download, name)
        with urllib.request.urlopen("%s/%s/%s" % (GITHUB, tag, name)) as r, open(target, "wb") as f:
            shutil.copyfileobj(r, f)
    got = sha256(os.path.join(download, b.file))
    sums = dict(reversed(l.split("  ", 1)) for l in read(os.path.join(download, "SHA256SUMS")).splitlines() if l)
    for what, cond in (("downloaded archive is the built one", got == b.sha256),
                       ("SHA256SUMS lists it", sums.get(b.file) == b.sha256),
                       ("distro.properties pins it", props.get("sha256") == b.sha256
                        and props.get("compressedSize") == str(b.size)
                        and props.get("sourceUrl") == "%s/%s/%s" % (GITHUB, tag, b.file))):
        print("%s %s" % ("PASS" if cond else "FAIL", what))
        ok = ok and cond
    sys.exit(0 if ok else 1)


def main():
    args = sys.argv[1:]
    if not args:
        die("usage: see the docstring")

    def option(name):
        if name not in args:
            die("%s is required" % name)
        i = args.index(name)
        value = args[i + 1]
        del args[i:i + 2]
        return value

    command = args.pop(0)
    if command == "compare":
        builds = compare(args)
        print("%d identical builds: %s %s %d" % (len(builds), builds[0].file, builds[0].sha256, builds[0].size))
    elif command == "pin" and len(args) == 1:
        pin(args[0])
    elif command == "provenance":
        inputs, sources = option("--inputs"), option("--sources")
        provenance(inputs, sources, args)
    elif command == "stage":
        inputs, sources = option("--inputs"), option("--sources")
        if len(args) != 2:
            die("stage needs OUT DEST")
        stage(inputs, sources, args[0], args[1])
    elif command == "verify-published" and len(args) in (1, 2):
        verify_published(args[0], args[1] if len(args) == 2 else None)
    else:
        die("usage: see the docstring")


if __name__ == "__main__":
    main()
