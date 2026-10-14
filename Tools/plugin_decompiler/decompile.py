"""Decompiles an exteraGram/PrimeGram plugin into readable form and checks it against this project.

Usage:
    python tools/plugin_decompiler/decompile.py <plugin.elyx | plugin.eaf | plugin.plugin | dir> [-o OUT_DIR]

A plugin that carries its own Java code (base64 DEX loaded with InMemoryDexClassLoader, or a .dex
file in the package) gets that code checked too: every app class, field, method and R resource it
calls into - see "Embedded DEX" in the report.

For every compiled module (.pyc) of the plugin it writes, under OUT_DIR (default:
<system temp>/prime_plugin_decompiler/<plugin name>/ - never inside the repository):

    <module>.asm        full disassembly: every code object (module, classes, functions, lambdas,
                        comprehensions), with line numbers, jump targets, constants and names;
    <module>.pseudo.py  pseudo-Python rebuilt from the bytecode by a symbolic stack evaluation:
                        imports, classes with their bases, functions with signatures and decorators,
                        assignments, calls, returns, and conditions/loops as explicit jumps;
    report.md           what the plugin needs from the app - SDK modules and the names it imports
                        from them, Java classes, private fields and methods it looks up by name -
                        each checked against this repository, so missing pieces are listed plainly.

Plain .py sources in the plugin are copied as they are and included in the report.

The bytecode is CPython 3.11's (what Chaquopy runs), so this must run on Python 3.11 too: the
marshal format and opcodes are version-specific, and the script refuses other versions instead of
printing garbage.
"""

import argparse
import dis
import io
import json
import marshal
import os
import re
import shutil
import sys
import types
import zipfile
from collections import defaultdict

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
JAVA_ROOT = os.path.join(REPO, "TMessagesProj", "src", "main", "java")
PY_ROOT = os.path.join(REPO, "TMessagesProj", "src", "main", "python")

if sys.version_info[:2] != (3, 11):
    sys.exit("needs Python 3.11 (the plugins' bytecode is 3.11), this is %s" % sys.version.split()[0])


# --------------------------------------------------------------------------------------------
# unpacking
# --------------------------------------------------------------------------------------------

def unpack(source, dest):
    if os.path.isdir(source):
        shutil.copytree(source, dest, dirs_exist_ok=True)
        return
    if zipfile.is_zipfile(source):
        # .elyx and .eaf (newer elyxbuilder output) are the same zipped package layout.
        with zipfile.ZipFile(source) as z:
            z.extractall(dest)
        return
    # A .plugin is one plain Python source file, not an archive.
    os.makedirs(dest, exist_ok=True)
    name = os.path.splitext(os.path.basename(source))[0].replace(" ", "_")
    shutil.copyfile(source, os.path.join(dest, name + ".py"))


def load_pyc(path):
    data = open(path, "rb").read()
    import importlib.util
    if data[:4] != importlib.util.MAGIC_NUMBER:
        raise ValueError("not 3.11 bytecode (magic %s)" % data[:4].hex())
    return marshal.loads(data[16:])


# --------------------------------------------------------------------------------------------
# disassembly
# --------------------------------------------------------------------------------------------

def write_asm(code, out):
    def emit(c, path):
        out.write("=" * 100 + "\n")
        out.write("code object %s  (%s)  line %s\n" % (c.co_qualname, path, c.co_firstlineno))
        out.write("  args: %s  posonly: %d  kwonly: %d  flags: %s\n" % (
            ", ".join(c.co_varnames[:c.co_argcount + c.co_kwonlyargcount]), c.co_posonlyargcount,
            c.co_kwonlyargcount, dis.pretty_flags(c.co_flags)))
        out.write("  names: %s\n" % (c.co_names,))
        out.write("  locals: %s\n" % (c.co_varnames,))
        if c.co_freevars or c.co_cellvars:
            out.write("  cells: %s  free: %s\n" % (c.co_cellvars, c.co_freevars))
        consts = [k for k in c.co_consts if not isinstance(k, types.CodeType)]
        out.write("  consts: %r\n" % (consts,))
        out.write("-" * 100 + "\n")
        dis.dis(c, file=out, depth=0)
        out.write("\n")
        for k in c.co_consts:
            if isinstance(k, types.CodeType):
                emit(k, path + "." + k.co_name)

    emit(code, "<module>")


# --------------------------------------------------------------------------------------------
# pseudo-Python
# --------------------------------------------------------------------------------------------

class Null:
    def __repr__(self):
        return "NULL"


NULL = Null()


class Expr:
    def __init__(self, text, prec=100):
        self.text = text
        self.prec = prec

    def __str__(self):
        return self.text


class FuncExpr:
    def __init__(self, code, defaults=None):
        self.code = code
        self.defaults = defaults
        self.decorators = []

    def __str__(self):
        return "<function %s>" % self.code.co_name


class ClassExpr:
    def __init__(self, func, name, bases, keywords):
        self.func = func
        self.name = name
        self.bases = bases
        self.keywords = keywords
        self.decorators = []

    def __str__(self):
        return "<class %s>" % self.name


def r(x):
    if isinstance(x, (FuncExpr, ClassExpr)):
        if isinstance(x, FuncExpr) and x.code.co_name == "<lambda>":
            return lambda_text(x)
        return str(x)
    if isinstance(x, Expr):
        return x.text
    return repr(x)


def paren(x, prec):
    s = r(x)
    if isinstance(x, Expr) and x.prec < prec:
        return "(" + s + ")"
    return s


def signature(code, defaults=None):
    names = list(code.co_varnames[:code.co_argcount])
    kwonly = list(code.co_varnames[code.co_argcount:code.co_argcount + code.co_kwonlyargcount])
    idx = code.co_argcount + code.co_kwonlyargcount
    parts = []
    for i, n in enumerate(names):
        parts.append(n)
        if i + 1 == code.co_posonlyargcount:
            parts.append("/")
    if code.co_flags & inspect_flag("VARARGS"):
        parts.append("*" + code.co_varnames[idx])
        idx += 1
    elif kwonly:
        parts.append("*")
    parts.extend(kwonly)
    if code.co_flags & inspect_flag("VARKEYWORDS"):
        parts.append("**" + code.co_varnames[idx])
    if defaults:
        parts.append("# defaults: %s" % defaults)
    return ", ".join(parts)


def inspect_flag(name):
    import inspect
    return getattr(inspect, "CO_" + name)


def lambda_text(func):
    lines = decompile_body(func.code)
    body = [l.strip() for l in lines if l.strip() and not l.strip().startswith("#")]
    if len(body) == 1 and body[0].startswith("return "):
        return "lambda %s: %s" % (signature(func.code), body[0][7:])
    return "lambda %s: <%d statements>" % (signature(func.code), len(body))


BINOPS = {op: sym for op, sym in dis._nb_ops} if hasattr(dis, "_nb_ops") else {}
COMPARE = dis.cmp_op


def decompile_body(code, indent=""):
    """Symbolic evaluation of one code object; returns its statements as lines."""
    out = []
    stack = []
    kw_names = ()
    targets = {ins.argval for ins in dis.get_instructions(code) if ins.opcode in dis.hasjabs or ins.opcode in dis.hasjrel}

    def emit(line):
        out.append(indent + line)

    def pop(n=1):
        if n == 0:
            return []
        vals = []
        for _ in range(n):
            vals.append(stack.pop() if stack else Expr("<?>"))
        vals.reverse()
        return vals

    for ins in dis.get_instructions(code):
        op = ins.opname
        if ins.offset in targets:
            emit("L%d:" % ins.offset)
        try:
            if op in ("RESUME", "NOP", "CACHE", "PRECALL", "EXTENDED_ARG", "COPY_FREE_VARS", "MAKE_CELL", "RETURN_GENERATOR"):
                continue
            if op == "PUSH_NULL":
                stack.append(NULL)
            elif op == "LOAD_CONST":
                v = ins.argval
                stack.append(FuncExpr(v) if isinstance(v, types.CodeType) else Expr(repr(v)))
            elif op in ("LOAD_FAST", "LOAD_NAME", "LOAD_DEREF", "LOAD_CLOSURE", "LOAD_CLASSDEREF"):
                stack.append(Expr(str(ins.argval)))
            elif op == "LOAD_GLOBAL":
                if ins.arg & 1:
                    stack.append(NULL)
                stack.append(Expr(str(ins.argval)))
            elif op == "LOAD_ATTR":
                obj = pop()[0]
                stack.append(Expr("%s.%s" % (paren(obj, 100), ins.argval)))
            elif op == "LOAD_METHOD":
                obj = pop()[0]
                stack.append(NULL)
                stack.append(Expr("%s.%s" % (paren(obj, 100), ins.argval)))
            elif op == "LOAD_ASSERTION_ERROR":
                stack.append(Expr("AssertionError"))
            elif op == "LOAD_BUILD_CLASS":
                stack.append(Expr("__build_class__"))
            elif op == "KW_NAMES":
                # dis in 3.11 does not resolve KW_NAMES' tuple; it is the constant at ins.arg.
                kw_names = code.co_consts[ins.arg]
            elif op == "CALL":
                n = ins.arg
                args = pop(n)
                fn = pop()[0]
                maybe_null = stack[-1] if stack else None
                if maybe_null is NULL:
                    stack.pop()
                elif isinstance(maybe_null, Expr) is False and maybe_null is not None and not isinstance(maybe_null, (FuncExpr, ClassExpr)):
                    pass
                if isinstance(fn, Expr) and fn.text == "__build_class__" and args and isinstance(args[0], FuncExpr):
                    name = eval(args[1].text) if isinstance(args[1], Expr) else "?"
                    nkw = len(kw_names)
                    bases = args[2:len(args) - nkw] if nkw else args[2:]
                    kws = dict(zip(kw_names, args[len(args) - nkw:])) if nkw else {}
                    stack.append(ClassExpr(args[0], name, bases, kws))
                elif (len(args) == 1 and not kw_names
                      and (isinstance(args[0], ClassExpr)
                           or isinstance(args[0], FuncExpr) and args[0].code.co_name != "<lambda>")):
                    # a decorator applied to a def/class
                    args[0].decorators.insert(0, r(fn))
                    stack.append(args[0])
                else:
                    nkw = len(kw_names)
                    pos = args[:len(args) - nkw] if nkw else args
                    kw = list(zip(kw_names, args[len(args) - nkw:])) if nkw else []
                    parts = [r(a) for a in pos] + ["%s=%s" % (k, r(v)) for k, v in kw]
                    stack.append(Expr("%s(%s)" % (paren(fn, 100), ", ".join(parts))))
                kw_names = ()
            elif op == "CALL_FUNCTION_EX":
                kwargs = pop()[0] if ins.arg & 1 else None
                args = pop()[0]
                fn = pop()[0]
                if stack and stack[-1] is NULL:
                    stack.pop()
                stack.append(Expr("%s(*%s%s)" % (r(fn), r(args), ", **" + r(kwargs) if kwargs is not None else "")))
            elif op == "MAKE_FUNCTION":
                fn = pop()[0]
                defaults = []
                if ins.arg & 0x08:
                    pop()  # closure
                if ins.arg & 0x04:
                    pop()  # annotations
                if ins.arg & 0x02:
                    defaults.append("kwdefaults=" + r(pop()[0]))
                if ins.arg & 0x01:
                    defaults.append("defaults=" + r(pop()[0]))
                if isinstance(fn, FuncExpr):
                    fn.defaults = ", ".join(defaults) or None
                stack.append(fn)
            elif op in ("STORE_FAST", "STORE_NAME", "STORE_GLOBAL", "STORE_DEREF"):
                v = pop()[0]
                name = str(ins.argval)
                if isinstance(v, FuncExpr) and v.code.co_name != "<lambda>":
                    for d in v.decorators:
                        emit("@" + d)
                    emit("def %s(%s):" % (name, signature(v.code, v.defaults)))
                    out.extend(decompile_body(v.code, indent + "    ") or [indent + "    pass"])
                elif isinstance(v, ClassExpr):
                    for d in v.decorators:
                        emit("@" + d)
                    bases = [r(b) for b in v.bases] + ["%s=%s" % (k, r(x)) for k, x in v.keywords.items()]
                    emit("class %s(%s):" % (name, ", ".join(bases)))
                    out.extend(decompile_body(v.func.code, indent + "    ") or [indent + "    pass"])
                else:
                    if not (isinstance(v, Expr) and v.text == name):  # "from m import x" already says it
                        emit("%s = %s" % (name, r(v)))
            elif op == "STORE_ATTR":
                obj, v = pop()[0], None
                v = pop()[0]
                emit("%s.%s = %s" % (paren(obj, 100), ins.argval, r(v)))
            elif op == "STORE_SUBSCR":
                key = pop()[0]
                obj = pop()[0]
                v = pop()[0]
                emit("%s[%s] = %s" % (paren(obj, 100), r(key), r(v)))
            elif op in ("DELETE_FAST", "DELETE_NAME", "DELETE_GLOBAL"):
                emit("del %s" % ins.argval)
            elif op == "DELETE_ATTR":
                emit("del %s.%s" % (r(pop()[0]), ins.argval))
            elif op == "DELETE_SUBSCR":
                key = pop()[0]
                obj = pop()[0]
                emit("del %s[%s]" % (r(obj), r(key)))
            elif op == "BINARY_SUBSCR":
                key = pop()[0]
                obj = pop()[0]
                stack.append(Expr("%s[%s]" % (paren(obj, 100), r(key))))
            elif op == "BINARY_OP":
                b = pop()[0]
                a = pop()[0]
                sym = BINOPS.get(ins.arg, ins.argrepr)
                if sym.endswith("=") and sym not in ("==", "!=", "<=", ">="):
                    stack.append(Expr("%s %s %s" % (r(a), sym, r(b)), 5))  # in-place, stored next
                else:
                    stack.append(Expr("%s %s %s" % (paren(a, 50), sym, paren(b, 51)), 50))
            elif op == "COMPARE_OP":
                b = pop()[0]
                a = pop()[0]
                stack.append(Expr("%s %s %s" % (paren(a, 40), COMPARE[ins.arg], paren(b, 41)), 40))
            elif op == "IS_OP":
                b = pop()[0]
                a = pop()[0]
                stack.append(Expr("%s %s %s" % (r(a), "is not" if ins.arg else "is", r(b)), 40))
            elif op == "CONTAINS_OP":
                b = pop()[0]
                a = pop()[0]
                stack.append(Expr("%s %s %s" % (r(a), "not in" if ins.arg else "in", r(b)), 40))
            elif op == "UNARY_NOT":
                stack.append(Expr("not " + paren(pop()[0], 30), 30))
            elif op == "UNARY_NEGATIVE":
                stack.append(Expr("-" + paren(pop()[0], 90), 90))
            elif op == "UNARY_INVERT":
                stack.append(Expr("~" + paren(pop()[0], 90), 90))
            elif op in ("BUILD_TUPLE", "BUILD_LIST", "BUILD_SET"):
                items = pop(ins.arg)
                o, c = {"BUILD_TUPLE": ("(", ")"), "BUILD_LIST": ("[", "]"), "BUILD_SET": ("{", "}")}[op]
                text = ", ".join(r(i) for i in items)
                if op == "BUILD_TUPLE" and len(items) == 1:
                    text += ","
                stack.append(Expr(o + text + c))
            elif op == "BUILD_MAP":
                items = pop(ins.arg * 2)
                stack.append(Expr("{" + ", ".join("%s: %s" % (r(items[i]), r(items[i + 1])) for i in range(0, len(items), 2)) + "}"))
            elif op == "BUILD_CONST_KEY_MAP":
                keys = pop()[0]
                vals = pop(ins.arg)
                try:
                    ks = eval(keys.text)
                except Exception:
                    ks = ["?"] * len(vals)
                stack.append(Expr("{" + ", ".join("%r: %s" % (k, r(v)) for k, v in zip(ks, vals)) + "}"))
            elif op == "BUILD_STRING":
                parts = pop(ins.arg)
                stack.append(Expr("f\"" + "".join("{" + r(p) + "}" if not (isinstance(p, Expr) and p.text.startswith(("'", '"'))) else eval(p.text).replace("{", "{{") for p in parts) + "\""))
            elif op == "FORMAT_VALUE":
                spec = pop()[0] if (ins.arg & 0x04) else None
                v = pop()[0]
                stack.append(Expr(r(v) + (":" + r(spec) if spec is not None else "")))
            elif op in ("LIST_APPEND", "SET_ADD"):
                v = pop()[0]
                emit("# %s %s" % (op.lower(), r(v)))
            elif op == "MAP_ADD":
                v = pop()[0]
                k = pop()[0]
                emit("# map_add %s: %s" % (r(k), r(v)))
            elif op in ("LIST_EXTEND", "SET_UPDATE", "DICT_UPDATE", "DICT_MERGE"):
                v = pop()[0]
                base = stack.pop() if stack else Expr("[]")
                stack.append(Expr("%s + %s" % (r(base), r(v))))
            elif op == "LIST_TO_TUPLE":
                stack.append(Expr("tuple(%s)" % r(pop()[0])))
            elif op == "BUILD_SLICE":
                parts = pop(ins.arg)
                stack.append(Expr(":".join("" if (isinstance(p, Expr) and p.text == "None") else r(p) for p in parts)))
            elif op == "POP_TOP":
                v = pop()[0]
                if isinstance(v, Expr) and ("(" in v.text or "=" in v.text):
                    emit(v.text)
            elif op == "RETURN_VALUE":
                v = pop()[0]
                if not (isinstance(v, Expr) and v.text == "None" and ins.offset == max(i.offset for i in dis.get_instructions(code))):
                    emit("return %s" % r(v))
            elif op in ("POP_JUMP_FORWARD_IF_FALSE", "POP_JUMP_BACKWARD_IF_FALSE"):
                emit("if not (%s): goto L%d" % (r(pop()[0]), ins.argval))
            elif op in ("POP_JUMP_FORWARD_IF_TRUE", "POP_JUMP_BACKWARD_IF_TRUE"):
                emit("if %s: goto L%d" % (r(pop()[0]), ins.argval))
            elif op in ("POP_JUMP_FORWARD_IF_NONE", "POP_JUMP_BACKWARD_IF_NONE"):
                emit("if %s is None: goto L%d" % (r(pop()[0]), ins.argval))
            elif op in ("POP_JUMP_FORWARD_IF_NOT_NONE", "POP_JUMP_BACKWARD_IF_NOT_NONE"):
                emit("if %s is not None: goto L%d" % (r(pop()[0]), ins.argval))
            elif op in ("JUMP_IF_FALSE_OR_POP", "JUMP_IF_TRUE_OR_POP"):
                v = stack[-1] if stack else Expr("<?>")
                emit("%s (%s) -> keep and goto L%d" % ("if not" if "FALSE" in op else "if", r(v), ins.argval))
                pop()
            elif op in ("JUMP_FORWARD", "JUMP_BACKWARD", "JUMP_BACKWARD_NO_INTERRUPT"):
                emit("goto L%d" % ins.argval)
            elif op == "GET_ITER":
                stack.append(Expr("iter(%s)" % r(pop()[0])))
            elif op == "FOR_ITER":
                it = stack[-1] if stack else Expr("<?>")
                emit("for-next over %s  (exhausted -> L%d)" % (r(it), ins.argval))
                stack.append(Expr("<item>"))
            elif op == "UNPACK_SEQUENCE":
                v = pop()[0]
                for i in reversed(range(ins.arg)):
                    stack.append(Expr("%s[%d]" % (paren(v, 100), i)))
            elif op == "IMPORT_NAME":
                fromlist = pop()[0]
                level = pop()[0]
                stack.append(Expr("import:%s|%s|%s" % (ins.argval, r(level), r(fromlist))))
            elif op == "IMPORT_FROM":
                mod = stack[-1] if stack else Expr("?")
                m = re.match(r"import:([^|]*)\|([^|]*)\|", mod.text) if isinstance(mod, Expr) else None
                modname = m.group(1) if m else "?"
                level = int(m.group(2)) if m and m.group(2).isdigit() else 0
                emit("from %s%s import %s" % ("." * level, modname, ins.argval))
                stack.append(Expr(str(ins.argval)))
            elif op == "IMPORT_STAR":
                mod = pop()[0]
                m = re.match(r"import:([^|]*)", mod.text)
                emit("from %s import *" % (m.group(1) if m else "?"))
            elif op == "COPY":
                stack.append(stack[-ins.arg] if len(stack) >= ins.arg else Expr("<?>"))
            elif op == "SWAP":
                if len(stack) >= ins.arg:
                    stack[-1], stack[-ins.arg] = stack[-ins.arg], stack[-1]
            elif op == "RAISE_VARARGS":
                args = pop(ins.arg)
                emit("raise " + " from ".join(r(a) for a in args))
            elif op == "RERAISE":
                emit("raise  # re-raise")
            elif op == "PUSH_EXC_INFO":
                emit("# except:")
                stack.append(Expr("<exception>"))
            elif op in ("POP_EXCEPT", "CHECK_EXC_MATCH"):
                if op == "CHECK_EXC_MATCH":
                    t = pop()[0]
                    emit("# except %s" % r(t))
                    stack.append(Expr("<exc matches %s>" % r(t)))
            elif op == "BEFORE_WITH":
                ctx = pop()[0]
                emit("with %s:" % r(ctx))
                stack.append(Expr("<exit>"))
                stack.append(Expr("<with-value>"))
            elif op == "YIELD_VALUE":
                v = pop()[0]
                emit("yield %s" % r(v))
                stack.append(Expr("<sent>"))
            elif op == "GET_AWAITABLE":
                stack.append(Expr("await %s" % r(pop()[0])))
            else:
                emit("# %s %s" % (op, ins.argrepr))
        except Exception as e:
            emit("# <could not rebuild %s: %s>" % (op, e))
            stack.clear()
    # imports left on the stack are plain "import x" statements
    return out


def write_pseudo(code, out, name):
    out.write("# pseudo-Python rebuilt from bytecode of %s - not runnable; conditions and loops are shown\n" % name)
    out.write("# as explicit 'goto L<offset>' jumps, matching the offsets in the .asm file.\n\n")
    lines = decompile_body(code)
    # turn "x = import:mod|0|None" into "import mod as x"
    for line in lines:
        m = re.match(r"^(\s*)(\S+) = import:([^|]*)\|([^|]*)\|(.*)$", line)
        if m:
            indent, target, mod, level, _ = m.groups()
            dots = "." * int(level) if level.isdigit() else ""
            base = mod.split(".")[0]
            if target == base and not dots:
                out.write("%simport %s\n" % (indent, mod))
            else:
                out.write("%simport %s%s as %s\n" % (indent, dots, mod, target))
            continue
        if "import:" in line and line.strip().startswith(("import:", "#")):
            continue
        out.write(line + "\n")


# --------------------------------------------------------------------------------------------
# API usage and the check against this repository
# --------------------------------------------------------------------------------------------

JAVA_NAME = re.compile(r"^(?:[a-z_][a-z0-9_]*\.)+[A-Z][A-Za-z0-9_]*(?:\$[A-Za-z0-9_]+)*$")
LOOKUP_CALLS = {"find_class", "findClass", "jclass", "get_private_field", "set_private_field",
                "get_static_private_field", "set_static_private_field", "find_method", "find_method_exact",
                "find_constructor_exact", "getDeclaredMethod", "getDeclaredField", "getMethod", "getField",
                "hook_all_methods", "findMethodExact", "findField", "get_field", "set_field"}


class Usage:
    def __init__(self):
        self.imports = defaultdict(set)       # module -> names (empty set = whole module)
        self.java_strings = set()             # "a.b.C$D" in string constants
        self.lookups = defaultdict(set)       # call name -> string arguments
        self.attributes = set()               # every attribute name used (for grepping)
        self.dex_blobs = []                   # (label, bytes): Java code the plugin carries and loads itself


def _maybe_dex_constant(value):
    """A long string constant that is base64 of a DEX file (plugins ship their own Java code this
    way and load it with InMemoryDexClassLoader), decoded - or None."""
    if not isinstance(value, str) or len(value) < 2000:
        return None
    try:
        import base64
        data = base64.b64decode(value, validate=False)
    except Exception:
        return None
    return data if data[:4] == b"dex\n" else None


def collect_usage(code, usage):
    for c in walk_codes(code):
        usage.attributes.update(c.co_names)
        for k in c.co_consts:
            if isinstance(k, str) and JAVA_NAME.match(k):
                usage.java_strings.add(k)
            dex = _maybe_dex_constant(k)
            if dex is not None and all(dex != b for _, b in usage.dex_blobs):
                usage.dex_blobs.append(("%s (%d bytes)" % (c.co_name, len(dex)), dex))
        insns = list(dis.get_instructions(c))
        last_module = None
        for i, ins in enumerate(insns):
            if ins.opname == "IMPORT_NAME":
                level = insns[i - 2].argval if i >= 2 and insns[i - 2].opname == "LOAD_CONST" else 0
                last_module = ("." * level if isinstance(level, int) else "") + ins.argval
                usage.imports.setdefault(last_module, set())
            elif ins.opname == "IMPORT_FROM" and last_module is not None:
                usage.imports[last_module].add(ins.argval)
            elif ins.opname in ("LOAD_METHOD", "LOAD_ATTR", "LOAD_GLOBAL", "LOAD_NAME", "LOAD_FAST") and ins.argval in LOOKUP_CALLS:
                # the string constants passed to the call that follows
                depth = 0
                for j in range(i + 1, min(i + 40, len(insns))):
                    nj = insns[j]
                    if nj.opname == "CALL":
                        break
                    if nj.opname == "LOAD_CONST" and isinstance(nj.argval, str):
                        usage.lookups[ins.argval].add(nj.argval)


def walk_codes(code):
    yield code
    for k in code.co_consts:
        if isinstance(k, types.CodeType):
            yield from walk_codes(k)


def collect_usage_from_source(path, usage):
    try:
        code = compile(open(path, encoding="utf-8").read(), path, "exec")
    except Exception:
        return
    collect_usage(code, usage)


_java_index = None


def java_index():
    """Every Java file in the app, as dotted class names, plus the nested class names in it."""
    global _java_index
    if _java_index is None:
        _java_index = {}
        for root, _, files in os.walk(JAVA_ROOT):
            for f in files:
                if f.endswith(".java"):
                    rel = os.path.relpath(os.path.join(root, f), JAVA_ROOT)[:-5]
                    _java_index[rel.replace(os.sep, ".")] = os.path.join(root, f)
    return _java_index


def java_class_status(name):
    platform = ("android.", "androidx.", "java.", "javax.", "kotlin.", "com.google.", "dalvik.", "org.json.", "org.xmlpull.")
    if name.startswith(platform):
        return "platform"
    outer, _, nested = name.partition("$")
    if outer.endswith((".R", ".BuildConfig")):
        return "generated"
    path = java_index().get(outer)
    if path is None:
        return "MISSING"
    for part in nested.split("$") if nested else []:
        src = open(path, encoding="utf-8", errors="ignore").read()
        if not re.search(r"\b(class|interface|enum|record)\s+%s\b" % re.escape(part), src):
            return "MISSING nested %s" % part
    return "ok"


PIP_IMPORT_NAMES = {"pillow": "PIL", "beautifulsoup4": "bs4", "python-dateutil": "dateutil",
                    "pycryptodome": "Crypto", "pynacl": "nacl"}
_pip_modules = None


def pip_modules():
    """Top-level import names of the packages the app bundles through Chaquopy's pip block."""
    global _pip_modules
    if _pip_modules is None:
        _pip_modules = set()
        gradle = os.path.join(REPO, "TMessagesProj", "build.gradle")
        for m in re.finditer(r'install\s+"([^"=<> ]+)', open(gradle, encoding="utf-8").read()):
            pkg = m.group(1).lower()
            _pip_modules.add(PIP_IMPORT_NAMES.get(pkg, pkg.replace("-", "_")))
    return _pip_modules


PLUGIN_OWN_MODULES = set()


def index_plugin_modules(src_dir):
    """Every dotted name under which one of the plugin's own modules can be imported - packages
    such as Nowfy Lite put their src/ on sys.path themselves and import "addons.Ui" absolutely,
    so each suffix of a module's path counts."""
    for root, _, files in os.walk(src_dir):
        for f in files:
            if not f.endswith((".py", ".pyc")):
                continue
            parts = os.path.relpath(os.path.join(root, f), src_dir).split(os.sep)
            parts[-1] = os.path.splitext(parts[-1])[0]
            if parts[-1] == "__init__":
                parts = parts[:-1]
            for i in range(len(parts)):
                PLUGIN_OWN_MODULES.add(".".join(parts[i:]))


def python_module_status(module):
    if module.startswith(".") or module in PLUGIN_OWN_MODULES:
        return "plugin's own"
    if module.split(".")[0] in pip_modules():
        return "pip"
    top = module.split(".")[0]
    if top in ("java", "com", "org", "android", "androidx", "kotlin", "dalvik", "javax"):
        return "java"
    if top in sys.stdlib_module_names:
        return "stdlib"
    rel = module.replace(".", os.sep)
    for cand in (rel + ".py", os.path.join(rel, "__init__.py")):
        if os.path.exists(os.path.join(PY_ROOT, cand)):
            return "ok"
    return "MISSING"


def python_name_status(module, name):
    rel = module.replace(".", os.sep)
    for cand in (rel + ".py", os.path.join(rel, "__init__.py")):
        path = os.path.join(PY_ROOT, cand)
        if os.path.exists(path):
            src = open(path, encoding="utf-8", errors="ignore").read()
            if re.search(r"^(\s*)(def|class)\s+%s\b|^\s*%s\s*(:[^=]*)?=|import\s.*\b%s\b|\"%s\"" % ((re.escape(name),) * 4), src, re.M):
                return "ok"
            sub = os.path.join(PY_ROOT, rel, name)
            if os.path.exists(sub + ".py") or os.path.isdir(sub):
                return "ok"
            return "MISSING"
    return "?"


def member_declared_anywhere(name):
    """Whether any Java file declares a field or method with this name."""
    pat = re.compile(r"[\s,(<>\]]%s\s*[;=(]" % re.escape(name))
    for path in java_index().values():
        try:
            if pat.search(open(path, encoding="utf-8", errors="ignore").read()):
                return True
        except OSError:
            pass
    return False


def _uleb128(b, o):
    result = shift = 0
    while True:
        x = b[o]
        o += 1
        result |= (x & 0x7f) << shift
        if x < 0x80:
            return result, o
        shift += 7


def parse_dex(b):
    """The parts of a DEX file's id tables this report needs: referenced types, field and method
    references (declaring type, name) and the types the file defines itself."""
    import struct
    u32 = lambda o: struct.unpack_from("<I", b, o)[0]
    u16 = lambda o: struct.unpack_from("<H", b, o)[0]
    strings = []
    for i in range(u32(0x38)):
        _, p = _uleb128(b, u32(u32(0x3C) + 4 * i))
        strings.append(b[p:b.index(0, p)].decode("utf-8", "replace"))
    types = [strings[u32(u32(0x44) + 4 * i)] for i in range(u32(0x40))]
    fields = [(types[u16(u32(0x54) + 8 * i)], strings[u32(u32(0x54) + 8 * i + 4)]) for i in range(u32(0x50))]
    methods = [(types[u16(u32(0x5C) + 8 * i)], strings[u32(u32(0x5C) + 8 * i + 4)]) for i in range(u32(0x58))]
    defined = {types[u32(u32(0x64) + 32 * i)] for i in range(u32(0x60))}
    return types, fields, methods, defined


APP_DESCRIPTOR_PREFIXES = ("Lorg/telegram/", "Lcom/exteragram/", "Lme/vkryl/")
_res_index = None


def res_index():
    """Resource names per type (drawable, raw, string...) as the app's own res/ declares them."""
    global _res_index
    if _res_index is None:
        _res_index = defaultdict(set)
        res = os.path.join(REPO, "TMessagesProj", "src", "main", "res")
        for d in os.listdir(res):
            full = os.path.join(res, d)
            if not os.path.isdir(full):
                continue
            kind = d.split("-")[0]
            if kind == "values":
                for f in os.listdir(full):
                    if f.endswith(".xml"):
                        text = open(os.path.join(full, f), encoding="utf-8", errors="ignore").read()
                        for t, n in re.findall(r'<(string|color|dimen|bool|integer|array|string-array|plurals)\s+name="([^"]+)"', text):
                            _res_index["array" if t == "string-array" else t].add(n)
            else:
                for f in os.listdir(full):
                    _res_index[kind].add(f.split(".")[0])
    return _res_index


def write_dex_report(usage, w, missing, warnings):
    if not usage.dex_blobs:
        return
    w("\n## Embedded DEX\n\nJava code the plugin carries itself and loads with a DexClassLoader. Its own classes "
      "are fine whatever they are called; what is checked is every class, field, method and resource of the app it calls into.\n\n")
    for label, data in usage.dex_blobs:
        try:
            types, fields, methods, defined = parse_dex(data)
        except Exception as e:
            w("- %s: could not parse (%s)\n" % (label, e))
            continue
        app_types = sorted({t for t in types if t.startswith(APP_DESCRIPTOR_PREFIXES) and t not in defined})
        w("### %s\n\n%d own classes, %d app classes referenced.\n\n" % (label, len(defined), len(app_types)))
        problems = []
        for t in app_types:
            name = t[1:-1].replace("/", ".")
            if "$-CC" in name:
                # D8's companion for interface default methods, only generated below minSdk 24.
                # exteraGram has none either (checked against its APK), so such a call is dead
                # code there too - noted, not counted as something this app is missing.
                warnings.append("%s: calls desugared companion %s (absent in exteraGram too)" % (label, name))
                continue
            st = java_class_status(name)
            if st.startswith("MISSING"):
                problems.append("Java class %s (%s)" % (name, st))
        for kind, items in (("field", fields), ("method", methods)):
            for cls, member in sorted(set(items)):
                if not cls.startswith(APP_DESCRIPTOR_PREFIXES) or cls in defined or member in ("<init>", "<clinit>") or "$-CC" in cls:
                    continue
                m = re.match(r"^Lorg/telegram/messenger/R\$(\w+);$", cls)
                if m:
                    if member not in res_index().get(m.group(1), set()):
                        problems.append("resource R.%s.%s" % (m.group(1), member))
                    continue
                if java_class_status(cls[1:-1].replace("/", ".")) != "ok":
                    continue  # already reported as a missing class
                # Declared anywhere in the app, not just in this class: an inherited platform
                # method (setTextSize on a TextView subclass) is not a miss.
                if not member_declared_anywhere(member):
                    problems.append("%s %s.%s" % (kind, cls[1:-1].replace("/", "."), member))
        for p in problems:
            w("- ❌ %s\n" % p)
            missing.append("DEX %s: %s" % (label, p))
        if not problems:
            w("All app references resolve.\n")
        w("\n")


def write_report(usage, out, plugin_name):
    w = out.write
    w("# %s: what it needs from the app\n\n" % plugin_name)
    missing = []
    warnings = []

    w("## SDK and Python modules\n\n| module | status | imported names |\n|---|---|---|\n")
    java_imports = {}
    for module in sorted(usage.imports):
        status = python_module_status(module)
        if status == "java":
            java_imports[module] = usage.imports[module]
            continue
        names = sorted(usage.imports[module])
        shown = []
        for n in names:
            if status == "ok":
                ns = python_name_status(module, n)
                if ns == "MISSING":
                    shown.append("**%s ❌**" % n)
                    missing.append("SDK name %s.%s" % (module, n))
                    continue
            shown.append(n)
        if status == "MISSING":
            missing.append("SDK module %s" % module)
        w("| %s | %s | %s |\n" % (module, "❌ missing" if status == "MISSING" else status, ", ".join(shown)))

    w("\n## Java classes\n\n| class | status | via |\n|---|---|---|\n")
    java_classes = {}
    for module, names in java_imports.items():
        for n in names:
            java_classes["%s.%s" % (module, n)] = "import"
    for s in usage.java_strings:
        java_classes.setdefault(s, "string")
    for s in usage.lookups.get("find_class", set()) | usage.lookups.get("findClass", set()) | usage.lookups.get("jclass", set()):
        if JAVA_NAME.match(s):
            java_classes[s] = "find_class"
    shipped = set()
    for _, data in usage.dex_blobs:
        try:
            shipped |= {t[1:-1].replace("/", ".") for t in parse_dex(data)[3]}
        except Exception:
            pass
    for name in sorted(java_classes):
        if name in shipped:
            w("| %s | shipped in the plugin's own DEX | %s |\n" % (name, java_classes[name]))
            continue
        if name in PLUGIN_OWN_MODULES:
            w("| %s | plugin's own Python module | %s |\n" % (name, java_classes[name]))
            continue
        if java_classes[name] == "string" and not name.startswith(("org.telegram.", "com.exteragram.", "me.vkryl.")):
            # A bare string that only looks like a class name (an Intent action, a pref key) and is
            # outside every namespace this app could be expected to provide.
            w("| %s | not an app class (string constant) | string |\n" % name)
            continue
        st = java_class_status(name)
        if st.startswith("MISSING"):
            missing.append("Java class %s (%s)" % (name, st))
        w("| %s | %s | %s |\n" % (name, "❌ " + st if st.startswith("MISSING") else st, java_classes[name]))

    w("\n## Members looked up by name\n\nFields and methods the plugin reaches by a string - by reflection or the SDK's private-field helpers. "
      "Checked only by name: a name no Java file in the app declares is certainly missing.\n\n| call | name | declared somewhere |\n|---|---|---|\n")
    for call in sorted(usage.lookups):
        if call in ("find_class", "findClass", "jclass"):
            continue
        for name in sorted(usage.lookups[call]):
            if not re.match(r"^[A-Za-z_$][A-Za-z0-9_$]*$", name):
                continue
            if re.match(r"^this\$\d+$", name):
                w("| %s | %s | synthetic (outer instance) |\n" % (call, name))
                continue
            found = member_declared_anywhere(name)
            if not found:
                # Could be a library's (androidx ViewPager and the like), which is not in this
                # repository's sources: a warning, not a certain miss.
                warnings.append("member %s (%s) is not declared in the app's own sources" % (name, call))
            w("| %s | %s | %s |\n" % (call, name, "yes" if found else "⚠ not in app sources"))

    write_dex_report(usage, w, missing, warnings)

    w("\n## Summary\n\n")
    for m in warnings:
        w("- ⚠ %s\n" % m)
    if warnings:
        w("\n")
    if missing:
        w("Missing in this app (%d):\n\n" % len(missing))
        for m in missing:
            w("- %s\n" % m)
    else:
        w("Nothing the plugin names is missing here.\n")
    return missing


# --------------------------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("plugin")
    ap.add_argument("-o", "--out")
    args = ap.parse_args()

    name = os.path.splitext(os.path.basename(args.plugin.rstrip("/\\")))[0]
    # Outside the repository by default: the output is someone else's plugin code, decompiled,
    # and has no business being one "git add ." away from a commit.
    import tempfile
    out_dir = os.path.abspath(args.out or os.path.join(tempfile.gettempdir(), "prime_plugin_decompiler", name))
    if os.path.commonpath([out_dir, REPO]) == REPO:
        sys.exit("refusing to write decompiled output inside the repository: %s" % out_dir)
    src_dir = os.path.join(out_dir, "_unpacked")
    if os.path.exists(out_dir):
        # A file still open in an editor cannot be deleted on Windows; it is overwritten instead.
        shutil.rmtree(out_dir, ignore_errors=True)
    os.makedirs(src_dir, exist_ok=True)
    unpack(args.plugin, src_dir)
    index_plugin_modules(src_dir)

    usage = Usage()
    done, failed = 0, []
    for root, _, files in os.walk(src_dir):
        for f in sorted(files):
            path = os.path.join(root, f)
            rel = os.path.relpath(path, src_dir)
            target_base = os.path.join(out_dir, os.path.splitext(rel)[0])
            if f.endswith(".pyc"):
                try:
                    code = load_pyc(path)
                except Exception as e:
                    failed.append("%s: %s" % (rel, e))
                    continue
                os.makedirs(os.path.dirname(target_base), exist_ok=True)
                with open(target_base + ".asm", "w", encoding="utf-8") as fh:
                    write_asm(code, fh)
                with open(target_base + ".pseudo.py", "w", encoding="utf-8") as fh:
                    write_pseudo(code, fh, rel)
                collect_usage(code, usage)
                done += 1
            elif f.endswith(".py"):
                os.makedirs(os.path.dirname(target_base), exist_ok=True)
                shutil.copy(path, target_base + ".py")
                collect_usage_from_source(path, usage)
                done += 1
            elif f.endswith(".dex"):
                with open(path, "rb") as fh:
                    data = fh.read()
                if data[:4] == b"dex\n":
                    usage.dex_blobs.append((rel, data))

    with open(os.path.join(out_dir, "report.md"), "w", encoding="utf-8") as fh:
        missing = write_report(usage, fh, name)
    with open(os.path.join(out_dir, "usage.json"), "w", encoding="utf-8") as fh:
        json.dump({"imports": {k: sorted(v) for k, v in usage.imports.items()},
                   "java_strings": sorted(usage.java_strings),
                   "lookups": {k: sorted(v) for k, v in usage.lookups.items()},
                   "missing": missing}, fh, ensure_ascii=False, indent=1)

    print("%s: %d modules -> %s" % (name, done, out_dir))
    for f in failed:
        print("  could not load", f)
    print("missing in this app: %d" % len(missing))
    for m in missing:
        print("  -", m)


if __name__ == "__main__":
    main()
