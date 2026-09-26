"""PrimeGram plugin compatibility check against a corpus of real exteraGram plugins.

Static only: parses every plugin with ``ast`` and reports, ranked by how many plugins hit each:

* names imported from an SDK module that our SDK (TMessagesProj/src/main/python) does not define;
* SDK modules plugins import that we do not ship at all;
* Java classes under org.telegram / com.exteragram that plugins import or look up by name and
  that either do not exist in our sources or would be stripped by R8 (proguard-rules.pro).

    python tools/plugin_compat/check_corpus.py <corpus dir> [--json out.json]

The corpus is a directory of ``*.plugin`` / ``*.py`` files (one plugin each) and/or ``*.elyx``
archives. Nothing here runs plugin code.
"""

import ast
import json
import os
import re
import sys
import zipfile
from collections import defaultdict

# PRIME_COMPAT_ROOT points the check at another client's tree (for comparisons).
ROOT = os.environ.get("PRIME_COMPAT_ROOT") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SDK_DIR = os.path.join(ROOT, "TMessagesProj", "src", "main", "python")
JAVA_DIR = os.path.join(ROOT, "TMessagesProj", "src", "main", "java")
PROGUARD = os.path.join(ROOT, "TMessagesProj", "proguard-rules.pro")

JAVA_ROOTS = ("org.telegram.", "com.exteragram.")
# Reflection / Chaquopy surface every Java class proxy answers regardless of its own members.
JAVA_OBJECT_MEMBERS = {
    "getClass", "class_", "getName", "getSimpleName", "getDeclaredMethod", "getDeclaredMethods",
    "getDeclaredField", "getDeclaredFields", "getDeclaredConstructor", "getDeclaredConstructors",
    "getMethod", "getMethods", "getField", "getFields", "getConstructor", "getConstructors",
    "isInstance", "isAssignableFrom", "getSuperclass", "getInterfaces", "cast", "forName",
    "TYPE", "valueOf", "values", "ordinal", "name", "equals", "hashCode", "toString", "setAccessible",
    "get", "set", "getBoolean", "setBoolean", "getInt", "setInt", "invoke", "newInstance",
}
# Chaquopy resolves these from the platform / bundled libraries, not our sources.
JAVA_PLATFORM_ROOTS = ("java.", "javax.", "android.", "androidx.", "com.google.", "kotlin.", "dalvik.", "org.json.")


# ---------------------------------------------------------------- our SDK

def module_names(path):
    """Top-level public names a module defines or re-exports."""
    try:
        tree = ast.parse(open(path, encoding="utf-8").read())
    except Exception:
        return None
    names = set()
    star = False
    for node in ast.walk(tree) if False else tree.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            names.add(node.name)
        elif isinstance(node, (ast.Assign, ast.AnnAssign)):
            targets = node.targets if isinstance(node, ast.Assign) else [node.target]
            for t in targets:
                for n in ast.walk(t):
                    if isinstance(n, ast.Name):
                        names.add(n.id)
        elif isinstance(node, ast.Import):
            for a in node.names:
                names.add((a.asname or a.name).split(".")[0])
        elif isinstance(node, ast.ImportFrom):
            for a in node.names:
                if a.name == "*":
                    star = True
                else:
                    names.add(a.asname or a.name)
        elif isinstance(node, (ast.If, ast.Try)):
            # try/except ImportError fallbacks and platform branches still define names
            for sub in ast.walk(node):
                if isinstance(sub, (ast.FunctionDef, ast.ClassDef)):
                    names.add(sub.name)
                elif isinstance(sub, ast.Assign):
                    for t in sub.targets:
                        if isinstance(t, ast.Name):
                            names.add(t.id)
                elif isinstance(sub, ast.ImportFrom):
                    for a in sub.names:
                        names.add(a.asname or a.name)
    # module-level __getattr__ answers anything
    if "__getattr__" in names:
        star = True
    return names, star


def load_sdk():
    sdk = {}
    for dp, dn, fn in os.walk(SDK_DIR):
        if "__pycache__" in dp:
            continue
        for f in fn:
            if not f.endswith(".py"):
                continue
            p = os.path.join(dp, f)
            mod = os.path.relpath(p, SDK_DIR)[:-3].replace(os.sep, ".")
            if mod.endswith(".__init__"):
                mod = mod[: -len(".__init__")]
            got = module_names(p)
            if got:
                sdk[mod] = got
    return sdk


# ---------------------------------------------------------------- our Java

def load_java_classes():
    """Fully qualified names of every top-level and nested class, '$' for nesting."""
    classes = set()
    decl = re.compile(r"\b(?:class|interface|enum|@interface|record)\s+([A-Za-z_][A-Za-z0-9_]*)")
    for dp, dn, fn in os.walk(JAVA_DIR):
        for f in fn:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            pkg_path = os.path.relpath(dp, JAVA_DIR).replace(os.sep, ".")
            top = f[:-5]
            fq_top = pkg_path + "." + top
            classes.add(fq_top)
            try:
                src = open(p, encoding="utf-8", errors="replace").read()
            except Exception:
                continue
            # nesting by brace depth, good enough for well-formatted sources
            src = re.sub(r"//[^\n]*|/\*.*?\*/|\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])'", "", src, flags=re.S)
            stack = []  # (name, depth)
            depth = 0
            i = 0
            pending = None
            for m in re.finditer(r"[{}]|\b(?:class|interface|enum|record)\s+[A-Za-z_][A-Za-z0-9_]*", src):
                tok = m.group(0)
                if tok == "{":
                    depth += 1
                    if pending:
                        stack.append((pending, depth))
                        pending = None
                elif tok == "}":
                    while stack and stack[-1][1] == depth:
                        stack.pop()
                    depth -= 1
                else:
                    name = tok.split()[-1]
                    pending = name
                    chain = [s[0] for s in stack]
                    if not chain:
                        continue  # the top-level class itself
                    classes.add(fq_top + "$" + "$".join(chain[1:] + [name]) if chain[0] == top else fq_top + "$" + name)
    return classes


_MEMBER_DECL = re.compile(
    r"(?:(?:public|protected|private|static|final|synchronized|abstract|native|default|volatile|transient)\s+)+"
    r"(?:<[^>]*>\s*)?[\w<>\[\], .?]+?\s+(\w+)\s*(?:\(|=|;)")
_ENUM_CONST = re.compile(r"\benum\s+\w+[^{]*\{([^;}]*)")
_EXTENDS = re.compile(r"\b(?:class|interface)\s+(\w+)(?:<[^{]*?>)?\s+extends\s+([\w.]+)")


def load_java_members():
    """simple class name -> (declared member names, superclass simple names). Approximate: a
    class's members are pooled per file, which is what a plugin's ``Class.name`` access needs."""
    members = defaultdict(set)
    supers = defaultdict(set)
    for dp, dn, fn in os.walk(JAVA_DIR):
        for f in fn:
            if not f.endswith(".java"):
                continue
            try:
                src = open(os.path.join(dp, f), encoding="utf-8", errors="replace").read()
            except Exception:
                continue
            top = f[:-5]
            names = set(_MEMBER_DECL.findall(src))
            names |= set(re.findall(r"\b(?:class|interface|enum|record)\s+(\w+)", src))
            for body in _ENUM_CONST.findall(src):
                names |= set(re.findall(r"^\s*([A-Z_][A-Z0-9_]*)\s*[,(]?", body, re.M))
                names |= set(re.findall(r"\b([A-Z_][A-Z0-9_]*)\b", body))
            members[top] |= names
            for cls, sup in _EXTENDS.findall(src):
                supers[cls].add(sup.split(".")[-1])
                if cls == top:
                    supers[top].add(sup.split(".")[-1])
            for inner in re.findall(r"\b(?:class|interface|enum)\s+(\w+)", src):
                members[inner] |= names  # nested classes: pooled with the file
    return members, supers


def has_member(members, supers, simple, name, depth=0):
    if name in members.get(simple, ()):
        return True
    if depth > 8:
        return False
    return any(has_member(members, supers, s, name, depth + 1) for s in supers.get(simple, ()))


def load_keep_patterns():
    pats = []
    for line in open(PROGUARD, encoding="utf-8", errors="replace"):
        m = re.match(r"\s*-keep(?:classmembers)?\s+(?:public\s+)?class\s+([A-Za-z0-9_.*$]+)", line)
        if not m:
            continue
        g = re.escape(m.group(1)).replace(r"\*\*", ".+").replace(r"\*", "[^.]+")
        pats.append(re.compile("^" + g + "$"))
    return pats


# ---------------------------------------------------------------- plugins

def iter_corpus(corpus):
    for dp, dn, fn in os.walk(corpus):
        for f in sorted(fn):
            p = os.path.join(dp, f)
            if f.endswith((".plugin", ".py")):
                yield f, {f: open(p, encoding="utf-8", errors="replace").read()}
            elif f.endswith((".elyx", ".eaf", ".zip")):
                try:
                    z = zipfile.ZipFile(p)
                except Exception:
                    continue
                files = {}
                for n in z.namelist():
                    if n.endswith(".py"):
                        files[n] = z.read(n).decode("utf-8", "replace")
                if files:
                    yield f, files


class Visitor(ast.NodeVisitor):
    def __init__(self):
        self.sdk_imports = []      # (module, name)
        self.module_imports = []   # module
        self.java = set()
        self.aliases = {}          # local name -> java class
        self.member_uses = set()   # (java class, member)
        self.stores = {}           # name -> times assigned
        self.raw_member_uses = []  # (local name, member)
        self.assigned_by_lookup = set()

    def visit_Name(self, node):
        if isinstance(node.ctx, ast.Store):
            self.stores[node.id] = self.stores.get(node.id, 0) + 1

    def visit_Assign(self, node):
        v = node.value
        if isinstance(v, ast.Call) and node.targets and isinstance(node.targets[0], ast.Name):
            fn = v.func
            name = fn.id if isinstance(fn, ast.Name) else fn.attr if isinstance(fn, ast.Attribute) else None
            if name in ("jclass", "find_class") and v.args and isinstance(v.args[0], ast.Constant)                     and isinstance(v.args[0].value, str) and v.args[0].value.startswith(JAVA_ROOTS):
                self.aliases[node.targets[0].id] = v.args[0].value
                self.assigned_by_lookup.add(node.targets[0].id)
        self.generic_visit(node)

    def visit_Attribute(self, node):
        if isinstance(node.value, ast.Name):
            self.raw_member_uses.append((node.value.id, node.attr))
        self.generic_visit(node)

    def resolve_member_uses(self):
        # A name also assigned something else (a loop variable, a widget) is not reliably the
        # class, so its attribute accesses say nothing about the class. find_class/jclass
        # assignments count once, and are expected.
        for name, attr in self.raw_member_uses:
            cls = self.aliases.get(name)
            if cls is None:
                continue
            expected = 1 if name in self.assigned_by_lookup else 0
            if self.stores.get(name, 0) > expected:
                continue
            self.member_uses.add((cls, attr))

    def visit_ImportFrom(self, node):
        mod = node.module or ""
        if node.level:  # relative - the plugin's own package
            return
        if mod.startswith(JAVA_ROOTS):
            for a in node.names:
                self.java.add(mod + "." + a.name)
                self.aliases[a.asname or a.name] = mod + "." + a.name
        elif mod.startswith(JAVA_PLATFORM_ROOTS) or mod == "java":
            pass
        else:
            for a in node.names:
                self.sdk_imports.append((mod, a.name))

    def visit_Import(self, node):
        for a in node.names:
            if a.name.startswith(JAVA_ROOTS):
                self.java.add(a.name)
            else:
                self.module_imports.append(a.name)

    def visit_Call(self, node):
        fn = node.func
        name = fn.id if isinstance(fn, ast.Name) else fn.attr if isinstance(fn, ast.Attribute) else None
        if name in ("jclass", "find_class", "forName") and node.args and isinstance(node.args[0], ast.Constant) \
                and isinstance(node.args[0].value, str) and node.args[0].value.startswith(JAVA_ROOTS):
            self.java.add(node.args[0].value)
        self.generic_visit(node)


def load_alias_resolver():
    """A client that maps exteraGram class names onto its own (extera_utils/class_aliases.py with
    a resolve(name) function) gets its mapping applied before lookup, so it is judged on what its
    plugins actually receive."""
    path = os.path.join(SDK_DIR, "extera_utils", "class_aliases.py")
    if not os.path.exists(path):
        return lambda n: n
    import importlib.util
    spec = importlib.util.spec_from_file_location("_alias_probe", path)
    mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(mod)
        return mod.resolve
    except Exception:
        return lambda n: n


RESOLVE = None


def java_candidates(name):
    if RESOLVE is not None:
        name = RESOLVE(name)
    """a.b.C.D -> a.b.C.D, a.b.C$D, a.b$C$D ... (Chaquopy accepts dotted nested names)."""
    parts = name.split(".")
    for nested in range(0, len(parts) - 2):
        head = ".".join(parts[: len(parts) - nested])
        yield head + ("$" + "$".join(parts[len(parts) - nested:]) if nested else "")


def main():
    corpus = sys.argv[1]
    out_json = sys.argv[sys.argv.index("--json") + 1] if "--json" in sys.argv else None
    global RESOLVE
    RESOLVE = load_alias_resolver()
    sdk = load_sdk()
    java = load_java_classes()
    members, supers = load_java_members()
    keep = load_keep_patterns()
    missing_members = defaultdict(set)
    local_mods = set()

    missing_names = defaultdict(set)
    missing_mods = defaultdict(set)
    missing_java = defaultdict(set)
    stripped_java = defaultdict(set)
    parse_errors = []
    plugins = 0

    for label, files in iter_corpus(corpus):
        plugins += 1
        own = {os.path.splitext(os.path.basename(n))[0] for n in files} | {n.split("/")[0] for n in files}
        v = Visitor()
        for n, src in files.items():
            try:
                v.visit(ast.parse(src))
            except SyntaxError:
                parse_errors.append(label)
        for mod, name in v.sdk_imports:
            top = mod.split(".")[0]
            if top in own or top in sys.stdlib_module_names or mod in ("__future__",):
                continue
            if mod not in sdk:
                if top in sdk or mod.startswith(("ui", "extera_utils", "elyx")) or top in (
                        "base_plugin", "client_utils", "android_utils", "hook_utils", "markdown_utils",
                        "file_utils", "plugin_settings", "ui", "extera_utils", "elyx", "intents",
                        "plugins_manager", "dev_server", "pip_controller"):
                    missing_mods[mod].add(label)
                continue
            names, star = sdk[mod]
            if star or name in names:
                continue
            # a submodule imported from a package (from ui import settings)
            if mod + "." + name in sdk:
                continue
            missing_names[mod + "." + name].add(label)
        for cls in v.java:
            if re.match(r"^org\.telegram\.messenger\.R(\$|\.|$)", cls):
                continue
            found = next((c for c in java_candidates(cls) if c in java), None)
            if found is None:
                missing_java[cls].add(label)
            elif not any(p.match(found.split("$")[0]) for p in keep):
                stripped_java[found].add(label)
        v.resolve_member_uses()
        for cls, attr in v.member_uses:
            if attr in JAVA_OBJECT_MEMBERS or attr.startswith("__"):
                continue
            found = next((c for c in java_candidates(cls) if c in java), None)
            if found is None:
                continue  # already reported as a missing class
            simple = re.split(r"[.$]", found)[-1]
            if not has_member(members, supers, simple, attr):
                missing_members[simple + "." + attr].add(label)

    def ranked(d):
        return sorted(((k, sorted(v)) for k, v in d.items()), key=lambda kv: (-len(kv[1]), kv[0]))

    report = {
        "plugins": plugins,
        "missing_sdk_names": ranked(missing_names),
        "missing_sdk_modules": ranked(missing_mods),
        "missing_java_classes": ranked(missing_java),
        "java_classes_stripped_by_r8": ranked(stripped_java),
        "missing_java_members": ranked(missing_members),
        "parse_errors": sorted(set(parse_errors)),
    }
    print(f"plugins scanned: {plugins}  (syntax errors: {len(report['parse_errors'])})")
    for key, title in (("missing_sdk_modules", "SDK modules we do not ship"),
                       ("missing_sdk_names", "SDK names we do not define"),
                       ("missing_java_classes", "Java classes we do not have"),
                       ("java_classes_stripped_by_r8", "Java classes R8 would strip"),
                       ("missing_java_members", "Class.member accesses our classes lack")):
        rows = report[key]
        affected = len({p for _, ps in rows for p in ps})
        print(f"\n== {title}: {len(rows)} (affecting {affected} plugins)")
        for name, ps in rows[:60]:
            print(f"  {len(ps):3d}  {name}")
    if out_json:
        json.dump(report, open(out_json, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    # Only gaps not already accepted in baseline.json fail the run.
    baseline_path = os.path.join(os.path.dirname(__file__), "baseline.json")
    baseline = json.load(open(baseline_path, encoding="utf-8")) if os.path.exists(baseline_path) else {}
    buckets = {"missing_sdk_modules": "missing_sdk_modules", "missing_sdk_names": "missing_sdk_names",
               "missing_java_classes": "missing_java_classes", "missing_java_members": "missing_java_members",
               "java_classes_stripped_by_r8": "java_classes_stripped_by_r8"}
    new = []
    closed = []
    for key, base_key in buckets.items():
        accepted = baseline.get(base_key, {})
        seen = {name for name, _ in report[key]}
        new += [(key, name, ps) for name, ps in report[key] if name not in accepted]
        closed += [(base_key, name) for name in accepted if name not in seen]
    if closed:
        print()
        print("baseline entries no longer needed (remove them):")
        for key, name in closed:
            print(f"  {key}: {name}")
    if new:
        print()
        print(f"NEW gaps not in baseline.json: {len(new)}")
        for key, name, ps in new:
            print(f"  {key}: {name}  ({len(ps)} plugins: {', '.join(ps[:4])})")
        sys.exit(1)
    print()
    print("OK: no gaps beyond baseline.json")


if __name__ == "__main__":
    main()
