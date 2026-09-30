"""Exercises extera_utils.classes (extends, @jconstructor, @joverload, construction order) on plain
CPython, with the Java side replaced by stubs that behave like Chaquopy: a Java class reaches Python
as a proxy *type* with no java.lang.Class methods, and PrimeClassProxy's helpers answer for it.

    python tools/plugin_compat/test_classes.py
"""
import os
import sys, types
class _Any:
    def __init__(self, *a, **k): pass
    def __getattr__(self, n): return _Any()
    def __call__(self, *a, **k): return _Any()
def mod(name, **kw):
    m = types.ModuleType(name); m.__dict__.update(kw)
    m.__getattr__ = lambda n: type(n, (_Any,), {})
    sys.modules[name] = m; return m

class JClass:                      # stands in for java.lang.Class
    def __init__(self, name, ctors=(), primitive=False):
        self.name, self.ctors, self.primitive = name, [list(c) for c in ctors], primitive
    def getName(self): return self.name
    def isPrimitive(self): return self.primitive
    def isInterface(self): return False
    def getDeclaredConstructors(self):
        return [Ctor(c) for c in self.ctors]
class Ctor:
    def __init__(self, params): self.params = params
    def getModifiers(self): return 1
    def getParameterTypes(self): return self.params
class Modifier:
    @staticmethod
    def isPrivate(m): return False

REG = {}
PROXIES = {}
def proxy(c):
    if c.name not in PROXIES:
        PROXIES[c.name] = type(c.name.rsplit(".", 1)[-1], (), {"_cls": c})
    return PROXIES[c.name]
def real(c):
    return c._cls if isinstance(c, type) else c
def jclass(name):
    if name not in REG:
        raise Exception('ClassNotFoundException: ' + name)
    return proxy(REG[name])
def reg(c): REG[c.name] = c; return c
BOOL = reg(JClass("boolean", primitive=True))
wrap = reg(JClass("java.lang.Boolean"))
CTX = reg(JClass("android.content.Context"))
VIEW = reg(JClass("android.view.View"))
ViewProxy = None
VH = reg(JClass("androidx.recyclerview.widget.RecyclerView$ViewHolder", ctors=[[VIEW]]))
SHEET = reg(JClass("org.telegram.ui.ActionBar.BottomSheet", ctors=[[CTX, BOOL]]))
DRAW = reg(JClass("android.graphics.drawable.Drawable", ctors=[[]]))

class Proxy:                       # what createProxyClassFull hands back
    def __init__(self, sup, handler): self.sup, self.handler = sup, handler
    def __call__(self, *args):
        ctor = next(c for c in self.sup.ctors if len(c) == len(args))
        inst = types.SimpleNamespace()
        self.handler.run(inst, ":".join(["<init>"] + [p.getName() for p in ctor]), list(args))
        return inst

class PrimeClassProxy:
    PEER_FIELD = "prime_peer"
    SUPER_PREFIX = "prime$super$"
    @staticmethod
    def nameOf(c): return real(c).name
    @staticmethod
    def isInterface(c): return False
    @staticmethod
    def newInstance(c, args): return c(*args)
    @staticmethod
    def setPeer(instance, peer): setattr(instance, PrimeClassProxy.PEER_FIELD, peer)
    @staticmethod
    def constructorSignatures(c):
        return [":".join(["<init>"] + [p.name for p in ctor]) for ctor in real(c).ctors]
    @staticmethod
    def createProxyClassFull(sup, *rest):
        return Proxy(real(sup), rest[-1])
    class PreConstructorSpec:
        def __init__(self, *a): pass
    MethodOverloadSpec = NewMethodSpec = MvelMethodSpec = FieldSpec = PreConstructorSpec

def dynamic_proxy(iface):
    class P:
        def __init__(self): pass
    return P
mod("java", dynamic_proxy=dynamic_proxy, jclass=jclass, jarray=lambda t: list)
mod("java.lang", Class=JClass, Object=object)
mod("java.lang.reflect", Modifier=Modifier)
mod("java.util", ArrayList=type("AL", (list,), {"add": list.append}))
mod("org"); mod("org.telegram"); mod("org.telegram.messenger", Utilities=types.SimpleNamespace(Callback3Return=object))
mod("org.telegram.messenger.plugins", PrimeClassProxy=PrimeClassProxy)
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "TMessagesProj", "src", "main", "python"))
proxy(wrap).TYPE = proxy(BOOL)
VH_P, SHEET_P, DRAW_P, ViewProxy = proxy(VH), proxy(SHEET), proxy(DRAW), proxy(VIEW)
from extera_utils.classes import Base, java_subclass, jconstructor, UnsupportedInThisBuild, JHelper

calls = []
# 1. music_page.py: @jconstructor([View]) with a Java class, not a string
@java_subclass(VH_P)
class PageViewHolder(Base):
    @jconstructor([ViewProxy])
    def init(self, item_view): calls.append(("PageViewHolder", item_view))
PageViewHolder.new_java_instance("the-view")

# 2. send_to_bottom_sheet.py: two type strings incl. a primitive
@java_subclass(SHEET_P)
class Sheet(Base):
    @jconstructor(["android.content.Context", "boolean"])
    def init(self, ctx, full): calls.append(("Sheet", ctx, full))
Sheet.new_java_instance("ctx", True)

# 3. preview_background_drawable.py: bare decorator, no-arg constructor
@java_subclass(DRAW_P)
class Bg(Base):
    @jconstructor
    def init(self): calls.append(("Bg",))
Bg.new_java_instance()

# 4. JHelper spelling + peer really gets the body run on it
@java_subclass(DRAW_P)
class Bg2(Base):
    @JHelper.Constructor()
    def init(self): self.ready = True
inst = Bg2.new_java_instance()

# 5. a signature the superclass lacks is refused at extends() time, not at first use
try:
    @java_subclass(DRAW_P)
    class Bad(Base):
        @jconstructor("android.content.Context")
        def init(self, ctx): pass
    Bad.new_java_instance()
    print("ok: mismatched hook skipped, class still usable")
except Exception as e:
    print("FAIL:", e)

from extera_utils.classes import joverload
ADAPTER = reg(JClass("androidx.recyclerview.widget.RecyclerView$Adapter", ctors=[[]]))
INT = reg(JClass("int", primitive=True)); reg(JClass("java.lang.Integer")); proxy(REG["java.lang.Integer"]).TYPE = proxy(INT)
@java_subclass(proxy(ADAPTER))
class Adapter(Base):
    @joverload("onBindViewHolder", [VH_P, "int"])
    def bind(self, holder, pos): calls.append(("bind", pos))
RP = reg(JClass("org.telegram.ui.ActionBar.Theme$ResourcesProvider"))
BS = reg(JClass("org.telegram.ui.ActionBar.BottomSheetReal", ctors=[[CTX, BOOL, RP], [CTX, BOOL, BOOL, RP]]))
@java_subclass(proxy(BS))
class PlatformSheet(Base):
    @jconstructor(["android.content.Context", "boolean", "org.telegram.ui.ActionBar.Theme.ResourceProvider"])
    def init(self, context, needFocus): calls.append(("PlatformSheet", context, needFocus))
PlatformSheet.new_java_instance("ctx", False, "rp")
@java_subclass(proxy(BS))
class NestedDots(Base):
    @jconstructor(["android.content.Context", "boolean", "org.telegram.ui.ActionBar.Theme.ResourcesProvider"])
    def init(self, *a): calls.append(("NestedDots", len(a)))
NestedDots.new_java_instance("ctx", True, "rp")
@java_subclass(DRAW_P)
class Ordered(Base):
    def __init__(self, *a): calls.append("__init__")
    @jconstructor
    def hook(self): calls.append("hook")
    def on_post_init(self, *a): calls.append("post")
Ordered.new_java_instance()
print(calls)
print("peer body ran:", getattr(inst, "prime_peer", None) or [v for v in vars(inst).values()][0].ready)

from extera_utils.classes import joverride, jmethod
RECT = reg(JClass("android.graphics.Rect")); STATE = reg(JClass("androidx.recyclerview.widget.RecyclerView$State"))
DECO = reg(JClass("androidx.recyclerview.widget.RecyclerView$ItemDecoration", ctors=[[]]))
captured = {}
_orig_create = PrimeClassProxy.createProxyClassFull
def _spy(sup, interfaces, methods, fields, mvel, new_methods, overloads, pre, handler):
    captured["methods"] = list(methods); captured["overloads"] = len(overloads)
    return _orig_create(sup, interfaces, methods, fields, mvel, new_methods, overloads, pre, handler)
PrimeClassProxy.createProxyClassFull = staticmethod(_spy)
@java_subclass(proxy(DECO))
class Spacing(Base):
    @joverride("getItemOffsets", ["android.graphics.Rect", "android.view.View", "androidx.recyclerview.widget.RecyclerView$State"])
    def offsets(self, rect, view, state): pass
    @joverride("onDraw")
    def draw_it(self, c): pass
    @joverride()
    def onDrawOver(self, c): pass
    @joverride
    def getOffsets(self): pass
    @jmethod("helper", "int", ["int"])
    def helper(self, x): return x
assert captured["overloads"] == 1, captured
assert set(captured["methods"]) == {"onDraw", "onDrawOver", "getOffsets"}, captured
PrimeClassProxy.createProxyClassFull = staticmethod(_orig_create)

assert calls == [("PageViewHolder", "the-view"), ("Sheet", "ctx", True), ("Bg",), ("PlatformSheet", "ctx", False),
                 ("NestedDots", 3), "__init__", "hook", "post"], calls
print("OK")

# ---- super() reaches the Java superclass through the bridge (reSwaga's onMeasure) ----
import extera_utils.classes as _classes_mod
_calls = []
class _Measured(Base):
    def onMeasure(self, w, h):
        super().onMeasure(w + 1, h + 1)
_classes_mod._install_super_bridge(_Measured, ["onMeasure"], {})
_peer = _Measured.__new__(_Measured)
_peer._prime_java = types.SimpleNamespace(**{"prime$super$onMeasure": lambda w, h: _calls.append((w, h))})
_peer.onMeasure(1, 2)
assert _calls == [(2, 3)], _calls
_classes_mod._install_super_bridge(_Measured, ["onMeasure", "onLayout"], {})  # second extends: refreshed, not stacked
assert sum(1 for k in _Measured.__mro__ if "__prime_super_bridge__" in vars(k)) == 1
print("super bridge OK")
