"""Matches every Dart test (by name) to a Kotlin @Test with the same name in backticks."""
import os, re, sys, json

DART = 'D:/AAAAAAProjects/GIT-HUB/Quran-Time/test'
KT = 'D:/AAAAAAProjects/GIT-HUB/Quran-Time-Kotlin/app/src'

def norm(s):
    s = re.sub(r'\$\{[^}]*\}', '', s)  # Dart string templates
    s = s.replace('\\$', '$').replace("\\'", "'").replace('..', ' to ').replace('.', ' ').replace('_', ' ').lower()
    return re.sub(r'[^a-z0-9]+', ' ', s).strip()

dart = {}  # file -> [names]
pat = re.compile(r"""\b(?:test|testWidgets)\(\s*(?:\n\s*)?(['"])(.*?)(?<!\\)\1""", re.S)
for root, _, files in os.walk(DART):
    for f in files:
        if not f.endswith('_test.dart'):
            continue
        p = os.path.join(root, f).replace('\\', '/')
        src = open(p, encoding='utf-8').read()
        # Adjacent string literals ('a' 'b') are joined in Dart; join the common split form.
        src = re.sub(r"""(['"])\s*\n\s*\1""", '', src)
        names = [m.group(2) for m in pat.finditer(src)]
        dart[p[len(DART) + 1:]] = names

kt = {}  # norm name -> class
ktclass_counts = {}
for root, _, files in os.walk(KT):
    for f in files:
        if not f.endswith('.kt'):
            continue
        p = os.path.join(root, f).replace('\\', '/')
        src = open(p, encoding='utf-8').read()
        cls = None
        for line in src.splitlines():
            m = re.match(r'\s*(?:internal |private )?class (\w+)', line)
            if m:
                cls = m.group(1)
            m = re.search(r'fun `([^`]+)`', line)
            if m and cls:
                kt.setdefault(norm(m.group(1)), cls)
                ktclass_counts[cls] = ktclass_counts.get(cls, 0) + 1

out = {'files': [], 'missing': []}
for f in sorted(dart):
    names = dart[f]
    matched, classes = 0, set()
    for n in names:
        c = kt.get(norm(n)) or next((v for k, v in kt.items() if norm(n) and (k.startswith(norm(n) + ' ') or k.endswith(' ' + norm(n)) or k.replace(' ', '') == norm(n).replace(' ', ''))), None)
        if c:
            matched += 1
            classes.add(c)
        else:
            out['missing'].append((f, n))
    out['files'].append((f, len(names), matched, sorted(classes)))
out['dart_total'] = sum(len(v) for v in dart.values())
out['kt_total'] = sum(ktclass_counts.values())
json.dump(out, open(sys.argv[1], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('dart', out['dart_total'], 'kotlin', out['kt_total'], 'missing', len(out['missing']))
