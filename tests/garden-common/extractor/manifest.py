#!/usr/bin/env python3
"""Expected result of extracting a Garden rootfs archive, independent of the app.

Reads the archive with Python's tarfile (names strict UTF-8) and prints, for
every path the app's extractor must create, one line:

    <path>\t<d|f|l>\t<mode octal: 01777 for a directory, 0777 for a file>\t<size>\t<sha256 | link target>

The app's extractor gate (host and device) walks the tree it extracted and
prints the same lines; the two must be identical. Rules mirrored from
TarballExtractor: setuid/setgid dropped, sticky kept on directories only; device/FIFO entries skipped;
"./" is the root; a hardlink materializes as a regular file with its target's
content; a parent the archive never lists is 0755. An entry the extractor must
refuse (absolute, "..", through a symlink) is reported and fails the script,
because a pinned image must not contain any.
"""
import hashlib
import posixpath
import sys
import tarfile


def main(path):
    tar = tarfile.open(path, "r:*", encoding="utf-8", errors="strict")
    entries = {}
    order = []
    problems = []
    for m in tar:
        raw = m.name
        if raw.startswith("/") or ".." in raw.replace("\\", "/").split("/"):
            problems.append("unsafe name: " + raw)
            continue
        name = "/".join(p for p in raw.split("/") if p not in ("", "."))
        if name == "":
            continue  # the root entry
        if not (m.isdir() or m.isreg() or m.issym() or m.islnk()):
            continue  # devices and FIFOs are skipped
        parts = name.split("/")
        for i in range(1, len(parts)):
            parent = "/".join(parts[:i])
            kind = entries.get(parent, (None,))[0]
            if kind == "l":
                problems.append("through a symlink: " + name)
                break
            if kind is None:
                entries[parent] = ("d", 0o755, 0, "-")
                order.append(parent)
        else:
            if m.isdir():
                entries[name] = ("d", m.mode & 0o1777, 0, "-")
            elif m.isreg():
                data = tar.extractfile(m).read()
                entries[name] = ("f", m.mode & 0o777, len(data), hashlib.sha256(data).hexdigest())
            elif m.issym():
                entries[name] = ("l", 0o777, 0, m.linkname)
            else:
                target = "/".join(p for p in m.linkname.split("/") if p not in ("", "."))
                if target not in entries or entries[target][0] not in ("f", "l"):
                    problems.append("hardlink target missing: %s -> %s" % (name, m.linkname))
                    continue
                entries[name] = entries[target]
            order.append(name)
    if problems:
        for p in problems[:20]:
            print("PROBLEM " + p, file=sys.stderr)
        sys.exit("%d entries the extractor must refuse" % len(problems))
    for name in sorted(entries):
        kind, mode, size, extra = entries[name]
        if kind == "l":
            mode, size = 0, 0  # a symlink's own mode and size are not compared
        print("%s\t%s\t%o\t%d\t%s" % (name, kind, mode, size, extra))


if __name__ == "__main__":
    main(sys.argv[1])
