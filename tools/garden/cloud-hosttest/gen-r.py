#!/usr/bin/env python3
"""Generate a non-final R.java (like a library's R) from res/ directories, for host tests."""
import os, re, sys, xml.etree.ElementTree as ET
pkg, out, resdirs = sys.argv[1], sys.argv[2], sys.argv[3:]
types = {}
def add(t, n): types.setdefault(t, set()).add(n.replace('.', '_'))
styleables = {}
for rd in resdirs:
    for d in sorted(os.listdir(rd)):
        base = d.split('-')[0]
        for f in sorted(os.listdir(os.path.join(rd, d))):
            p = os.path.join(rd, d, f)
            if base == 'values':
                try: root = ET.parse(p).getroot()
                except Exception: continue
                for e in root:
                    tag, name = e.tag, e.get('name')
                    if not name: continue
                    if tag == 'item': tag = e.get('type') or 'item'
                    if tag in ('string-array', 'integer-array', 'array'): tag = 'array'
                    if tag == 'declare-styleable':
                        styleables.setdefault(name, [])
                        for a in e.findall('attr'):
                            an = a.get('name'); styleables[name].append(an)
                            if not an.startswith('android:'): add('attr', an)
                        continue
                    if tag == 'public': continue
                    add(tag, name)
            else:
                add(base, f.split('.')[0])
                if f.endswith('.xml'):
                    txt = open(p, encoding='utf-8', errors='replace').read()
                    for m in re.finditer(r'@\+id/([A-Za-z0-9_.]+)', txt): add('id', m.group(1))
os.makedirs(os.path.dirname(out), exist_ok=True)
n = 0x7f000001
with open(out, 'w') as o:
    o.write(f'package {pkg};\npublic final class R {{\n')
    for t in sorted(types):
        o.write(f'  public static final class {t} {{\n')
        for name in sorted(types[t]): o.write(f'    public static int {name} = {n};\n'); n += 1
        o.write('  }\n')
    o.write('  public static final class styleable {\n')
    for s, attrs in sorted(styleables.items()):
        o.write(f'    public static int[] {s} = new int[{len(attrs)}];\n')
        for i, a in enumerate(attrs): o.write(f'    public static int {s}_{a.replace(":", "_")} = {i};\n')
    o.write('  }\n}\n')
