#!/usr/bin/env python3
"""Exported-component inventory of built APKs, checked against a policy.

    tests/security/exported_components.py APK...          check, exit 1 on any FAIL
    tests/security/exported_components.py --print APK...  print the inventory as JSON

The merged manifest is read from the APK with aapt2, so components contributed
by libraries count as well. A component is exported when android:exported says
so, or, without the attribute, when it has an intent filter (the pre-API 31
default; providers default to not exported). An exported component is guarded
when it, or the application, requires a permission.

The policy for an APK is chosen by its package name, with a ".qa.*" QA or
".devel" debug suffix removed: policy/<package>.json lists the exact exported components that
package may have. Anything added, removed or changed fails, and so does an APK
that has no policy. See docs/security/THORNS.md.
"""
import glob
import json
import os
import re
import subprocess
import sys

ANDROID = "http://schemas.android.com/apk/res/android:"
COMPONENTS = ("activity", "activity-alias", "service", "receiver", "provider")
HERE = os.path.dirname(os.path.abspath(__file__))


def aapt2():
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Android/Sdk")
    tools = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt2")),
                   key=lambda p: [int(x) if x.isdigit() else x
                                  for x in re.split(r"[.-]", p.split(os.sep)[-2])])
    if not tools:
        sys.exit("aapt2 not found under " + sdk)
    return tools[-1]


def parse_xmltree(text):
    """aapt2 'dump xmltree' output -> nested {'tag', 'attrs', 'children'}."""
    root = {"tag": None, "attrs": {}, "children": []}
    stack = [(-1, root)]
    for line in text.splitlines():
        indent = len(line) - len(line.lstrip(" "))
        body = line.strip()
        if body.startswith("E: "):
            node = {"tag": body[3:].split(" ")[0], "attrs": {}, "children": []}
            while stack[-1][0] >= indent:
                stack.pop()
            stack[-1][1]["children"].append(node)
            stack.append((indent, node))
        elif body.startswith("A: "):
            m = re.match(r'A: (\S+?)(?:\(0x[0-9a-f]+\))?=(.*)$', body)
            if not m:
                continue
            name, value = m.group(1), m.group(2)
            raw = re.search(r'\(Raw: "(.*)"\)$', value)
            if raw:
                value = raw.group(1)
            elif value.startswith('"'):
                value = value.split('"')[1]
            while stack[-1][0] >= indent:
                stack.pop()
            stack[-1][1]["attrs"][name.replace(ANDROID, "")] = value
    return root


def find(node, tag):
    for child in node["children"]:
        if child["tag"] == tag:
            yield child


def inventory(apk):
    out = subprocess.run([aapt2(), "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                         check=True, capture_output=True, text=True).stdout
    manifest = next(find(parse_xmltree(out), "manifest"))
    package = manifest["attrs"]["package"]
    application = next(find(manifest, "application"))
    app_permission = application["attrs"].get("permission")
    exported = []
    for kind in COMPONENTS:
        for comp in find(application, kind):
            filters = list(find(comp, "intent-filter"))
            flag = comp["attrs"].get("exported")
            if flag is None:
                is_exported = bool(filters) and kind != "provider"
            else:
                is_exported = flag in ("true", "0xffffffff", "-1")
            if not is_exported:
                continue
            name = comp["attrs"]["name"]
            if name.startswith(package + "."):
                name = name[len(package):]
            actions = sorted({a["attrs"]["name"].replace(package + ".", "${applicationId}.")
                              for f in filters for a in find(f, "action")})
            permission = comp["attrs"].get("permission") or app_permission
            if permission:
                permission = permission.replace(package + ".", "${applicationId}.")
            exported.append({"type": kind, "name": name, "permission": permission,
                             "actions": actions})
    exported.sort(key=lambda c: (c["type"], c["name"]))
    return package, exported


def policy_for(package):
    base = re.sub(r"(\.(qa|rc)\..*|\.devel)$", "", package)
    path = os.path.join(HERE, "policy", base + ".json")
    if not os.path.exists(path):
        return path, None
    with open(path) as f:
        return path, json.load(f)["exported"]


def main(argv):
    show = "--print" in argv
    apks = [a for a in argv if a != "--print"]
    if not apks:
        sys.exit(__doc__)
    fail = False
    for apk in apks:
        package, exported = inventory(apk)
        name = os.path.basename(apk)
        if show:
            print(json.dumps({"package": package, "exported": exported}, indent=2))
            continue
        path, expected = policy_for(package)
        if expected is None:
            print("FAIL %s exported components: no policy %s" % (name, os.path.relpath(path)))
            fail = True
            continue
        for c in exported:
            print("   exported %s %s permission=%s actions=%s"
                  % (c["type"], c["name"], c["permission"], ",".join(c["actions"]) or "-"))
        if exported == expected:
            print("PASS %s exported components match %s (%d)"
                  % (name, os.path.basename(path), len(exported)))
        else:
            fail = True
            for c in exported:
                if c not in expected:
                    print("   unexpected: %s" % json.dumps(c))
            for c in expected:
                if c not in exported:
                    print("   missing:    %s" % json.dumps(c))
            print("FAIL %s exported components differ from %s" % (name, os.path.basename(path)))
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
