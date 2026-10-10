#!/usr/bin/env python3
"""Static pre-flight checks that catch the most common Android build/runtime breakages without needing the SDK."""
import pathlib, re, sys
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parent.parent
app = root / "app/src/main"
res = app / "res"
errors, warnings = [], []

# 1. every XML file parses
for f in list(res.rglob("*.xml")) + [app / "AndroidManifest.xml"]:
    try:
        ET.parse(f)
    except ET.ParseError as e:
        errors.append(f"XML not well-formed: {f.relative_to(root)}: {e}")

# 2. strings: collect names per locale
def names(path, translatable_only=False):
    return {e.get("name") for e in ET.parse(path).getroot().iter("string")
            if not (translatable_only and e.get("translatable") == "false")}
en = names(res / "values/strings.xml")                       # everything the code may reference
en_translatable = names(res / "values/strings.xml", True)    # what every locale must provide
fa = names(res / "values-fa/strings.xml")
if en_translatable - fa: errors.append(f"strings missing in values-fa: {sorted(en_translatable - fa)}")
if fa - en: errors.append(f"strings only in values-fa: {sorted(fa - en)}")

# 3. Kotlin references
kt = list((app / "java").rglob("*.kt"))
used_strings, used_drawables = set(), set()
for f in kt:
    t = f.read_text(encoding="utf-8")
    used_strings |= set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", t))
    used_drawables |= set(re.findall(r"R\.drawable\.([A-Za-z0-9_]+)", t))
drawables = {p.stem for p in (res / "drawable").glob("*.xml")}
for s in sorted(used_strings - en): errors.append(f"R.string.{s} is used but not defined")
for d in sorted(used_drawables - drawables): errors.append(f"R.drawable.{d} is used but missing")
unused = sorted(en - used_strings - {"app_name"})
if unused: warnings.append(f"unused strings: {unused}")

# 4. manifest references resolve
man = (app / "AndroidManifest.xml").read_text(encoding="utf-8")
for kind, name in re.findall(r'@(mipmap|xml|style|drawable|string|color)/([A-Za-z0-9_.]+)', man):
    if kind == "mipmap": ok = list(res.glob(f"mipmap*/{name}.xml")) or list(res.glob(f"mipmap*/{name}.png"))
    elif kind == "xml": ok = (res / "xml" / f"{name}.xml").exists()
    elif kind == "style":
        ok = any(e.get("name") == name for f in (res / "values").glob("*.xml") for e in ET.parse(f).getroot().iter("style"))
    elif kind == "string": ok = name in en
    else: ok = list(res.glob(f"{kind}*/{name}.xml"))
    if not ok: errors.append(f"AndroidManifest references missing @{kind}/{name}")

# 5. classes named in the manifest exist
for cls in re.findall(r'android:name="\.([A-Za-z0-9_.]+)"', man):
    if not any(f.stem == cls.split(".")[-1] for f in kt): errors.append(f"manifest class .{cls} has no Kotlin file")

# 6. forbidden patterns (things that break in this environment or violate the project's rules)
for f in kt:
    t = f.read_text(encoding="utf-8")
    rel = f.relative_to(root)
    if re.search(r"\blicen[sc]e\w*Key|isPremium|premiumOnly|checkLicense", t): errors.append(f"license/premium logic found in {rel}")
    code_only = "\n".join(l for l in t.splitlines() if not l.strip().startswith(("*", "//", "/*")))
    if re.search(r"import\s+org\.json|org\.json\.", code_only): errors.append(f"org.json used in {rel} (stubbed in JVM unit tests; use core.Json)")
    if re.search(r"READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE", t): errors.append(f"storage permission referenced in {rel}")
perm = re.findall(r'uses-permission android:name="([^"]+)"', man)
if perm != ["android.permission.INTERNET"]:
    errors.append(f"unexpected permissions in manifest: {perm}")

print(f"strings: {len(en)} (en) / {len(fa)} (fa) | drawables: {len(drawables)} | kotlin files: {len(kt)}")
for w in warnings: print("WARN ", w)
for e in errors: print("ERROR", e)
print("RESULT:", "FAILED" if errors else "OK")
sys.exit(1 if errors else 0)
