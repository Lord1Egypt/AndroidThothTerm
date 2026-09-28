#!/usr/bin/env python3
"""Release checks for a Garden edition APK, with no Android SDK needed.

    verify-apk.py APK --flavour full|fdroid --distro DISTRO_PROPERTIES
                  [--package ID] [--version-name N] [--version-code C]
                  [--signed | --unsigned] [--minified]

Checks, each printed as PASS or FAIL (exit 1 on any FAIL):

- the binary manifest: package, versionCode, versionName, not debuggable;
- arm64-v8a is the only ABI, and every ELF the APK carries (lib/ and the
  PRoot runtime under assets/runtime/) is AArch64 with every PT_LOAD aligned
  to at least 16 KB;
- zip alignment as `zipalign -c -P 16 4` checks it: a stored .so starts on a
  16 KB boundary, every other stored entry on 4 bytes;
- assets/garden/distro.properties is byte for byte the given file;
- full: exactly one rootfs under assets/garden/rootfs/, named assetName,
  stored, with the pinned size and SHA-256; fdroid: none at all;
- the two JNI classes R8 must keep by name (com.thothterm.TermIO$Native and
  com.thothterm.Process$Native) are in the dex with their native methods,
  and with --minified, garden-common's own classes are renamed (R8 ran);
- --signed: an APK Signing Block is present; --unsigned: none is.

When apksigner and zipalign are on PATH they are run as well, as a second
opinion; they are not required.
"""
import argparse
import hashlib
import io
import os
import shutil
import struct
import subprocess
import sys
import zipfile

PAGE_16K = 0x4000
JNI_CLASSES = ("Lcom/thothterm/TermIO$Native;", "Lcom/thothterm/Process$Native;")
# A class R8 renames unless something keeps it: proof that minification ran.
UNKEPT_CLASS = "Lcom/thothterm/linux/RootfsManager;"

results = []


def check(name, ok, detail=""):
    results.append(ok)
    print("%s %s%s" % ("PASS" if ok else "FAIL", name, (" -- " + detail) if detail else ""))


# ---- binary XML (AndroidManifest.xml) ------------------------------------

def _string_pool(data, off):
    count, _styles, flags, strings_start = struct.unpack_from("<IIII", data, off + 8)
    utf8 = flags & 0x100
    offsets = struct.unpack_from("<%dI" % count, data, off + 28)
    base = off + strings_start
    out = []
    for o in offsets:
        p = base + o
        if utf8:
            n = data[p]
            p += 2 if n & 0x80 else 1       # UTF-16 length, skipped
            n = data[p]
            if n & 0x80:
                n = ((n & 0x7F) << 8) | data[p + 1]
                p += 2
            else:
                p += 1
            out.append(data[p:p + n].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", data, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
                p += 2
            out.append(data[p:p + 2 * n].decode("utf-16-le", "replace"))
    return out


def manifest_attributes(data):
    """{element: {attribute: value}} for the first occurrence of each element."""
    strings, elements = [], {}
    p = 8
    while p < len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, p)
        if ctype == 0x0001:
            strings = _string_pool(data, p)
        elif ctype == 0x0102:
            name_idx = struct.unpack_from("<I", data, p + 20)[0]
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", data, p + 24)
            attrs = {}
            for i in range(attr_count):
                a = p + 16 + attr_start + i * attr_size
                _ns, aname, raw, _size, _res0, dtype, value = struct.unpack_from("<IIIHBBI", data, a)
                key = strings[aname] if aname < len(strings) else str(aname)
                if dtype == 0x03:               # string
                    attrs[key] = strings[value]
                elif dtype == 0x12:             # boolean
                    attrs[key] = value != 0
                elif dtype in (0x10, 0x11):     # int dec / hex
                    attrs[key] = value
                elif raw != 0xFFFFFFFF:
                    attrs[key] = strings[raw]
                else:
                    attrs[key] = value
            elements.setdefault(strings[name_idx], attrs)
        p += csize
    return elements


# ---- ELF -----------------------------------------------------------------

def elf_problems(blob):
    if blob[:4] != b"\x7fELF":
        return ["not an ELF file"]
    if blob[4] != 2 or blob[5] != 1:
        return ["not a 64-bit little-endian ELF"]
    machine = struct.unpack_from("<H", blob, 18)[0]
    problems = [] if machine == 183 else ["e_machine %d, not AArch64" % machine]
    phoff = struct.unpack_from("<Q", blob, 32)[0]
    phentsize, phnum = struct.unpack_from("<HH", blob, 54)
    loads = 0
    for i in range(phnum):
        ptype, _flags, offset, vaddr, _paddr, _filesz, _memsz, align = \
            struct.unpack_from("<IIQQQQQQ", blob, phoff + i * phentsize)
        if ptype != 1:
            continue
        loads += 1
        if align < PAGE_16K:
            problems.append("PT_LOAD align 0x%x" % align)
        elif offset % align != vaddr % align:
            problems.append("PT_LOAD offset/vaddr disagree modulo 0x%x" % align)
    if not loads:
        problems.append("no PT_LOAD segment")
    return problems


# ---- dex -----------------------------------------------------------------

def _uleb(data, p):
    result = shift = 0
    while True:
        b = data[p]
        p += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, p
        shift += 7


def dex_classes(data):
    """{type descriptor: number of native methods} for every class def."""
    string_ids_size, string_ids_off, type_ids_size, type_ids_off = struct.unpack_from("<IIII", data, 0x38)
    class_defs_size, class_defs_off = struct.unpack_from("<II", data, 0x60)

    def string(i):
        p = struct.unpack_from("<I", data, string_ids_off + 4 * i)[0]
        _, p = _uleb(data, p)
        end = data.index(b"\x00", p)
        return data[p:end].decode("utf-8", "replace")

    out = {}
    for c in range(class_defs_size):
        d = class_defs_off + 32 * c
        class_idx = struct.unpack_from("<I", data, d)[0]
        class_data_off = struct.unpack_from("<I", data, d + 24)[0]
        name = string(struct.unpack_from("<I", data, type_ids_off + 4 * class_idx)[0])
        natives = 0
        if class_data_off:
            p = class_data_off
            sizes = []
            for _ in range(4):
                v, p = _uleb(data, p)
                sizes.append(v)
            for _ in range(sizes[0] + sizes[1]):
                _, p = _uleb(data, p)
                _, p = _uleb(data, p)
            for _ in range(sizes[2] + sizes[3]):
                _, p = _uleb(data, p)
                flags, p = _uleb(data, p)
                _, p = _uleb(data, p)
                if flags & 0x100:
                    natives += 1
        out[name] = natives
    return out


# ---- zip -----------------------------------------------------------------

def data_offset(raw, info):
    """Where an entry's data starts: after its local header, name and extra."""
    name_len, extra_len = struct.unpack_from("<HH", raw, info.header_offset + 26)
    return info.header_offset + 30 + name_len + extra_len


def has_signing_block(raw):
    eocd = raw.rfind(b"PK\x05\x06")
    if eocd < 0:
        return False
    cd_offset = struct.unpack_from("<I", raw, eocd + 16)[0]
    return raw[cd_offset - 16:cd_offset] == b"APK Sig Block 42"


def load_properties(path):
    props = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    return props


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("apk")
    ap.add_argument("--flavour", required=True, choices=("full", "fdroid"))
    ap.add_argument("--distro", required=True, help="the edition's assets/garden/distro.properties")
    ap.add_argument("--package")
    ap.add_argument("--version-name")
    ap.add_argument("--version-code", type=int)
    sign = ap.add_mutually_exclusive_group()
    sign.add_argument("--signed", action="store_true")
    sign.add_argument("--unsigned", action="store_true")
    ap.add_argument("--minified", action="store_true", help="expect an R8-processed release build")
    args = ap.parse_args()

    with open(args.apk, "rb") as f:
        raw = f.read()
    apk = zipfile.ZipFile(io.BytesIO(raw))
    names = apk.namelist()
    print("# %s  %d bytes  sha256 %s" % (os.path.basename(args.apk), len(raw), hashlib.sha256(raw).hexdigest()))

    m = manifest_attributes(apk.read("AndroidManifest.xml"))
    man, application = m.get("manifest", {}), m.get("application", {})
    print("# package=%s versionCode=%s versionName=%s minSdk=%s targetSdk=%s" % (
        man.get("package"), man.get("versionCode"), man.get("versionName"),
        m.get("uses-sdk", {}).get("minSdkVersion"), m.get("uses-sdk", {}).get("targetSdkVersion")))
    if args.package:
        check("package is " + args.package, man.get("package") == args.package, str(man.get("package")))
    if args.version_code is not None:
        check("versionCode is %d" % args.version_code, man.get("versionCode") == args.version_code,
              str(man.get("versionCode")))
    if args.version_name:
        check("versionName is " + args.version_name, man.get("versionName") == args.version_name,
              str(man.get("versionName")))
    check("not debuggable", not application.get("debuggable", False))

    abis = sorted({n.split("/")[1] for n in names if n.startswith("lib/") and n.count("/") >= 2})
    check("arm64-v8a is the only ABI", abis == ["arm64-v8a"], " ".join(abis))
    elves = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
    elves += [n for n in names if n.startswith("assets/runtime/") and not n.endswith("/")]
    for n in sorted(elves):
        problems = elf_problems(apk.read(n))
        check("16 KB ELF " + n, not problems, "; ".join(problems))
    for want in ("lib/arm64-v8a/libproot.so", "lib/arm64-v8a/libproot_loader.so",
                 "assets/runtime/arm64-v8a/libtalloc.so.2", "assets/runtime/arm64-v8a/libandroid-shmem.so"):
        check("carries " + want, want in names)

    misaligned = []
    for info in apk.infolist():
        if info.compress_type != zipfile.ZIP_STORED or info.filename.endswith("/"):
            continue
        need = PAGE_16K if info.filename.endswith(".so") else 4
        if data_offset(raw, info) % need:
            misaligned.append("%s@%d" % (info.filename, data_offset(raw, info)))
    check("zip alignment (zipalign -c -P 16 4)", not misaligned, " ".join(misaligned[:5]))

    with open(args.distro, "rb") as f:
        distro_bytes = f.read()
    check("distro.properties is the repository's",
          "assets/garden/distro.properties" in names and apk.read("assets/garden/distro.properties") == distro_bytes)
    distro = load_properties(args.distro)
    rootfs = [n for n in names if n.startswith("assets/garden/rootfs/") and not n.endswith("/")]
    if args.flavour == "fdroid":
        check("fdroid embeds no rootfs", not rootfs, " ".join(rootfs))
    else:
        want = "assets/garden/rootfs/" + distro["assetName"]
        check("full embeds exactly one rootfs, " + distro["assetName"], rootfs == [want], " ".join(rootfs))
        if want in names:
            info = apk.getinfo(want)
            check("rootfs is stored, not recompressed", info.compress_type == zipfile.ZIP_STORED)
            blob = apk.read(want)
            check("rootfs size is the pin", len(blob) == int(distro["compressedSize"]), str(len(blob)))
            check("rootfs sha256 is the pin", hashlib.sha256(blob).hexdigest() == distro["sha256"])

    classes = {}
    for n in sorted(n for n in names if n.startswith("classes") and n.endswith(".dex")):
        classes.update(dex_classes(apk.read(n)))
    for c in JNI_CLASSES:
        check("JNI class kept by name " + c, c in classes and classes[c] > 0,
              "%s native methods" % classes.get(c, "no class,"))
    print("# native methods in the JNI classes: %d" % sum(classes.get(c, 0) for c in JNI_CLASSES))
    if args.minified:
        check("R8 ran (%s renamed)" % UNKEPT_CLASS, UNKEPT_CLASS not in classes)

    if args.signed:
        check("APK Signing Block present", has_signing_block(raw))
    if args.unsigned:
        check("unsigned: no APK Signing Block", not has_signing_block(raw))

    for tool, argv in (("zipalign", ["zipalign", "-c", "-P", "16", "4", args.apk]),
                       ("apksigner", ["apksigner", "verify", args.apk] if args.signed else None)):
        if argv and shutil.which(tool):
            got = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            check(" ".join(argv[:-1]), got.returncode == 0, got.stdout.strip().splitlines()[-1] if got.stdout.strip() else "")

    print("%d checks, %d failed" % (len(results), results.count(False)))
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
