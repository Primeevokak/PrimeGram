"""Subclassing Java from Python.

exteraGram generates dex at run time so a plugin can extend an arbitrary app class - a custom Cell,
a fragment of its own. So does this, now: the same package (``com.android.dx``, the AOSP-derived
``dexmaker`` library) generates a real subclass through ``PrimeClassProxy``, and ``Base.extends``
wires a Python class up to it. Implementing *interfaces* still goes through Chaquopy natively
(``dynamic_proxy``), unchanged.

A class returned by ``extends`` is a real Java class - calling it constructs a real Java object of
that class, immediately. Every method the Python class defines becomes an override that calls back
into a *new* Python instance built for that one Java object, found through ``prime$peer``, a plain
field ``PrimeClassProxy`` puts on every generated instance - not a lookup by Python object identity,
which nothing here relies on being stable across the Chaquopy boundary. ``self.java`` on that Python
instance is the Java object it belongs to; ``self.super_("draw")`` is the original implementation,
still reachable through the ``prime$super$draw`` twin the generator keeps for exactly this.
"""

import sys

from java import dynamic_proxy
from org.telegram.messenger import Utilities
from org.telegram.messenger.plugins import PrimeClassProxy

_PEER_FIELD = PrimeClassProxy.PEER_FIELD
_SUPER_PREFIX = "prime$super$"

# Base.new_instance(init_args=...) -> the <init> hook: per Python class, a stack (constructors can
# nest) of the argument lists __init__ should get instead of the Java constructor's.
_PENDING_INIT_ARGS = {}

# (Java class name, attribute) -> java.lang.reflect.Field or None, so a peer's attribute writes
# do not walk the class hierarchy by reflection every time.
_FIELD_CACHE = {}
_NO_FIELD = object()


def _java_field(java, name):
    cls = java.getClass()
    key = (PrimeClassProxy.nameOf(cls), name)
    field = _FIELD_CACHE.get(key)
    if field is None:
        found = PrimeClassProxy.findDeclaredField(cls, name)
        if found is not None:
            from java.lang.reflect import Modifier
            if Modifier.isStatic(found.getModifiers()):
                found = None
        field = found if found is not None else _NO_FIELD
        _FIELD_CACHE[key] = field
    return None if field is _NO_FIELD else field


def _java_list(items):
    """A real ``java.util.ArrayList`` built from ``items`` (``None`` treated as
    empty) - see ``Base.extends``'s own doc for why this replaced handing Chaquopy a
    plain Python ``list`` at the ``createProxyClassFull`` call boundary."""
    from java.util import ArrayList
    result = ArrayList()
    if items:
        for item in items:
            result.add(item)
    return result


class UnsupportedInThisBuild(NotImplementedError):
    def __init__(self, what):
        super().__init__("%s needs runtime dex generation, which this build does not have" % what)


def _install_super_bridge(cls, java_names, java_to_python):
    """Makes Python's own ``super().onMeasure(...)`` reach the Java superclass, as exteraGram's SDK
    does (its ``__jsuper_bridge_base__``).

    A plugin overriding a Java method calls the original the Python way, ``super().name(...)``.
    Python resolves that along the class's MRO, where the next class after the plugin's is ``Base``
    - which has no such method, so the call raised, the override did nothing, and Java was left
    with e.g. an ``onMeasure`` that never set a size: an IllegalStateException that took the whole
    app down. A class inserted between the plugin's class and ``Base`` carries one method per
    overridden Java method, each calling the generated class's ``prime$super$<name>`` twin.
    """
    namespace = {}

    def make(java_name):
        def call_super(self, *args):
            return getattr(self.java, _SUPER_PREFIX + java_name)(*args)
        call_super.__name__ = java_name
        return call_super

    for java_name in java_names:
        fn = make(java_name)
        namespace[java_name] = fn
        python_name = java_to_python.get(java_name)
        if python_name and python_name != java_name:
            namespace[python_name] = fn
    if not namespace:
        return
    for klass in cls.__mro__:
        if Base not in klass.__bases__ and not any(getattr(b, "__prime_super_bridge__", False) for b in klass.__bases__):
            continue
        for base in klass.__bases__:
            if getattr(base, "__prime_super_bridge__", False):
                # extends() ran again for this class: refresh the existing bridge.
                for name, fn in namespace.items():
                    setattr(base, name, fn)
                return
        namespace["__prime_super_bridge__"] = True
        namespace["__module__"] = klass.__module__
        bridge = type(klass.__name__ + "SuperBridge", (Base,), namespace)
        try:
            klass.__bases__ = tuple(bridge if b is Base else b for b in klass.__bases__)
        except TypeError:
            import traceback
            traceback.print_exc()
        return


def _overridable_names(cls):
    """Every method ``cls`` itself defines, over what every Python class already has - those, and
    only those, are generated as real overrides. A plugin does not declare which methods it is
    overriding; it just defines them, the same as subclassing anything else in Python.

    A method decorated with ``@joverride("javaName")`` overrides ``javaName`` instead of its own
    name - the pair goes into ``java_to_python``, which the dispatcher consults so a call arriving
    for the Java name still finds the differently-named Python method.
    """
    skip = {"__init__", "extends", "bind", "java_class", "from_java", "new_java_instance", "new_instance",
            "on_pre_init", "on_post_init"}
    java_names = set()
    java_to_python = {}
    for klass in cls.__mro__:
        if klass is object or klass is Base or getattr(klass, "__prime_super_bridge__", False) and "__prime_super_bridge__" in vars(klass):
            continue
        for name, value in vars(klass).items():
            if name.startswith("__") or name in skip or not callable(value):
                continue
            if (getattr(value, "__jnewmethod__", None) is not None
                    or getattr(value, "__joverload__", None) is not None
                    or getattr(value, "__jpreconstructor__", None) is not None
                    or getattr(value, "__jconstructor__", None) is not None):
                continue
            target = getattr(value, "__joverride_target__", name)
            java_names.add(target)
            if target != name:
                java_to_python[target] = name
    return list(java_names), java_to_python


class _FieldSpec:
    """``jfield(...)`` - a real Java field on the generated class, read and written as a plain
    Python attribute through this descriptor. ``default`` is accepted for the documented shape but
    not applied yet: a field starts at Java's own zero value (0, false, null) until the constructor
    hook can set it, which needs ``@jconstructor`` - not built yet - to have somewhere to run. A
    plugin that needs a real starting value sets it explicitly in ``__init__`` for now.
    """

    def __init__(self, type_string, default=None, methods=None):
        self.type_string = type_string
        self.default = default
        self.methods = methods or []
        self.name = None

    def __set_name__(self, owner, name):
        self.name = name

    def __get__(self, instance, owner):
        if instance is None:
            return self
        java = getattr(instance, "_prime_java", None)
        if java is None:
            return self.default
        return getattr(java, self.name)

    def __set__(self, instance, value):
        java = getattr(instance, "_prime_java", None)
        if java is None:
            raise AttributeError("%r has no value until the Java instance exists" % (self.name,))
        setattr(java, self.name, value)


class _AccessorSpec:
    __slots__ = ("java_method_name", "is_getter")

    def __init__(self, java_method_name, is_getter):
        self.java_method_name = java_method_name
        self.is_getter = is_getter


_PRIMITIVE_WRAPPERS = {
    "int": "Integer", "boolean": "Boolean", "long": "Long", "float": "Float",
    "double": "Double", "short": "Short", "byte": "Byte", "char": "Character",
}


def _resolve_java_type(type_string):
    """``"int"`` or ``"java.lang.String"`` to the real ``Class`` object - the primitives by way of
    their boxed wrapper's ``TYPE`` field, since that is the only place Java keeps a ``Class`` for
    something that is not itself a class.

    ``"void"`` is not a class either, and unlike the other primitives Chaquopy's own ``jclass``
    refuses to reflect it at all (``ValueError: Cannot reflect a primitive type``) - there is no
    ``Void.TYPE``-style wrapper trick available the way there is for ``int``/``boolean``/etc.
    Every caller here already treats a falsy return type as "no return class" (``None``), so
    mapping ``"void"`` to ``None`` here means a plugin author writing ``-> "void"`` (a real,
    popular .elyx plugin does exactly this on a ``@jmethod``) gets the same "void method"
    result as leaving the return type off entirely, instead of a crash.
    """
    if type_string == "void":
        return None
    if not isinstance(type_string, str):
        # Plugins also pass the Java class itself (``@jconstructor([View])``). Chaquopy converts
        # its class proxy to java.lang.Class at every Java call, so it is usable as it is.
        return type_string
    from java import jclass
    wrapper = _PRIMITIVE_WRAPPERS.get(type_string)
    if wrapper is not None:
        return jclass("java.lang." + wrapper).TYPE
    return jclass(type_string)


def _collect_field_specs(cls):
    """Every ``jfield(...)`` assigned in ``cls`` or a base of it, as the Java-side ``FieldSpec``
    objects ``PrimeClassProxy`` wants - resolving each one's accessors from whatever ``jgetmethod``/
    ``jsetmethod`` entries its ``methods=`` list carries, if any.
    """
    from org.telegram.messenger.plugins import PrimeClassProxy
    seen = set()
    specs = []
    for klass in cls.__mro__:
        if klass is object or klass is Base:
            continue
        for name, value in vars(klass).items():
            if not isinstance(value, _FieldSpec) or name in seen:
                continue
            seen.add(name)
            getter_name = None
            setter_name = None
            for accessor in value.methods:
                if not isinstance(accessor, _AccessorSpec):
                    continue
                if accessor.is_getter:
                    getter_name = accessor.java_method_name
                else:
                    setter_name = accessor.java_method_name
            java_type = _resolve_java_type(value.type_string)
            specs.append(PrimeClassProxy.FieldSpec(value.name or name, java_type, getter_name, setter_name))
    return specs


class _MvelMethodSpec:
    """``jMVELmethod(...)``/``jMVELoverride(...)`` - a method whose body is MVEL, evaluated by the
    same engine ``HookFilter.Condition`` uses, with no Python call in between. The attribute name it
    is assigned to in the class body is the Java method name, the same way a plain method's own name
    is."""

    __slots__ = ("return_type", "arguments", "code", "is_override")

    def __init__(self, return_type, arguments, code, is_override):
        self.return_type = return_type
        self.arguments = arguments or []
        self.code = code
        self.is_override = is_override


def _collect_mvel_specs(cls):
    """Every ``jMVELmethod``/``jMVELoverride`` assigned in ``cls`` or a base of it, as the Java-side
    ``MvelMethodSpec`` objects ``PrimeClassProxy`` wants."""
    from java import jarray
    from java.lang import Class as _JClass, String as _JString
    from org.telegram.messenger.plugins import PrimeClassProxy
    seen = set()
    specs = []
    for klass in cls.__mro__:
        if klass is object or klass is Base:
            continue
        for name, value in vars(klass).items():
            if not isinstance(value, _MvelMethodSpec) or name in seen:
                continue
            seen.add(name)
            param_names = jarray(_JString)([str(arg[0]) for arg in value.arguments])
            param_types = jarray(_JClass)([_resolve_java_type(arg[1]) for arg in value.arguments])
            return_type = _resolve_java_type(value.return_type) if value.return_type else None
            specs.append(PrimeClassProxy.MvelMethodSpec(
                name, return_type, param_types, param_names, value.code, bool(value.is_override)))
    return specs


class _NewMethodSpec:
    """``jmethod(...)`` - a method that exists only on the generated class, with no superclass
    method to override. Dispatch works exactly like an override's - the same ``Callback3Return``
    handler, the same ``java_to_python`` lookup by name - the only difference ``PrimeClassProxy``
    sees is that there is no super trampoline to generate alongside it."""

    __slots__ = ("java_name", "return_type", "arg_types")

    def __init__(self, java_name, return_type, arg_types):
        self.java_name = java_name
        self.return_type = return_type
        self.arg_types = arg_types or []


def _infer_annotation(fn, name):
    import inspect
    try:
        sig = inspect.signature(fn)
    except (TypeError, ValueError):
        return None
    if name == "return":
        ann = sig.return_annotation
        return None if ann is inspect.Signature.empty else ann
    param = sig.parameters.get(name)
    if param is None or param.annotation is inspect.Signature.empty:
        return None
    return param.annotation


def _infer_arg_types(fn):
    """Parameter annotations, in order, skipping ``self`` - the type strings ``jmethod``'s own
    docs example writes right on the method (``value: "int"``) rather than passing separately."""
    import inspect
    try:
        params = list(inspect.signature(fn).parameters.values())[1:]
    except (TypeError, ValueError):
        return []
    types = []
    for param in params:
        if param.annotation is inspect.Signature.empty:
            raise TypeError("jmethod on %r needs arg_types or annotated parameters" % (fn.__name__,))
        types.append(param.annotation)
    return types


def _collect_new_methods(cls):
    """Every ``jmethod(...)``-tagged method in ``cls`` or a base of it, as the Java-side
    ``NewMethodSpec`` objects ``PrimeClassProxy`` wants, plus the ``java_to_python`` entries for any
    whose Java name differs from the Python one."""
    from java import jarray
    from java.lang import Class as _JClass
    from org.telegram.messenger.plugins import PrimeClassProxy
    seen = set()
    specs = []
    java_to_python = {}
    for klass in cls.__mro__:
        if klass is object or klass is Base or getattr(klass, "__prime_super_bridge__", False) and "__prime_super_bridge__" in vars(klass):
            continue
        for name, value in vars(klass).items():
            tag = getattr(value, "__jnewmethod__", None)
            if tag is None or name in seen:
                continue
            seen.add(name)
            java_name, return_type, arg_types = tag
            java_name = java_name or name
            if arg_types is None:
                arg_types = _infer_arg_types(value)
            if return_type is None:
                return_type = _infer_annotation(value, "return")
            return_class = _resolve_java_type(return_type) if return_type else None
            param_types = jarray(_JClass)([_resolve_java_type(t) for t in arg_types])
            specs.append(PrimeClassProxy.NewMethodSpec(java_name, return_class, param_types))
            if java_name != name:
                java_to_python[java_name] = name
    return specs, java_to_python


def _java_signature(java_name, arg_type_strings):
    """The same ``"name:param.Class.Name:..."`` string ``PrimeClassProxy.signature`` builds on the
    Java side, from type strings the way every other spec here takes them - so a ``java_to_python``
    entry keyed on it lines up with the dispatch signature a specific overload's handler call
    actually arrives with."""
    parts = [java_name]
    for type_string in arg_type_strings:
        parts.append(PrimeClassProxy.nameOf(_resolve_java_type(type_string)))
    return ":".join(parts)


def _collect_overloads(cls):
    """Every ``@joverload(...)``-tagged method in ``cls`` or a base of it, as the Java-side
    ``MethodOverloadSpec`` objects ``PrimeClassProxy`` wants, plus ``java_to_python`` entries keyed
    on the *full* signature - not just the name, which is what a plain override or ``jmethod`` uses
    - so dispatch can tell one overload's handler call from another's."""
    from java import jarray
    from java.lang import Class as _JClass
    from org.telegram.messenger.plugins import PrimeClassProxy
    seen = set()
    specs = []
    java_to_python = {}
    for klass in cls.__mro__:
        if klass is object or klass is Base or getattr(klass, "__prime_super_bridge__", False) and "__prime_super_bridge__" in vars(klass):
            continue
        for name, value in vars(klass).items():
            tag = getattr(value, "__joverload__", None)
            if tag is None or name in seen:
                continue
            seen.add(name)
            java_name, arg_types = tag
            java_name = java_name or name
            if arg_types is None:
                arg_types = _infer_arg_types(value)
            param_types = jarray(_JClass)([_resolve_java_type(t) for t in arg_types])
            specs.append(PrimeClassProxy.MethodOverloadSpec(java_name, param_types))
            java_to_python[_java_signature(java_name, arg_types)] = name
    return specs, java_to_python


def _collect_preconstructors(cls):
    """Every ``@jpreconstructor(...)``-tagged function in ``cls`` or a base of it, as the Java-side
    ``PreConstructorSpec`` objects ``PrimeClassProxy`` wants, plus a ``{"<preinit>:...": function}``
    map ``_ConstructorHandler`` dispatches through directly - there is no Python peer yet at that
    point for the usual by-name lookup to go through."""
    from java import jarray
    from java.lang import Class as _JClass
    from org.telegram.messenger.plugins import PrimeClassProxy
    seen = set()
    specs = []
    preconstructors = {}
    for klass in cls.__mro__:
        if klass is object or klass is Base:
            continue
        for name, value in vars(klass).items():
            tag = getattr(value, "__jpreconstructor__", None)
            if tag is None or name in seen:
                continue
            seen.add(name)
            (arg_types,) = tag
            if arg_types is None:
                arg_types = _infer_arg_types(value)
            param_types = jarray(_JClass)([_resolve_java_type(t) for t in arg_types])
            specs.append(PrimeClassProxy.PreConstructorSpec(param_types))
            preconstructors[_java_signature("<preinit>", arg_types)] = value
    return specs, preconstructors


def _lenient_type_name(type_string):
    """The JVM name of a type given the way plugin authors write them, or None if it cannot be
    found. Nested classes are written with dots (``Theme.ResourcesProvider``) where the JVM wants
    ``$`` (``Theme$ResourcesProvider``), so the trailing segments are retried as nested ones."""
    if not isinstance(type_string, str):
        return PrimeClassProxy.nameOf(type_string)
    if type_string in _PRIMITIVE_WRAPPERS:
        return type_string
    from java import jclass
    parts = type_string.split(".")
    for nested in range(0, len(parts)):
        candidate = ".".join(parts[:len(parts) - nested])
        if nested:
            candidate += "$" + "$".join(parts[len(parts) - nested:])
        try:
            return PrimeClassProxy.nameOf(jclass(candidate))
        except Exception:
            continue
    return None


def _positional_arity(fn):
    """How many arguments after ``self`` ``fn`` accepts, or None if it takes ``*args``."""
    import inspect
    try:
        params = list(inspect.signature(fn).parameters.values())[1:]
    except (TypeError, ValueError):
        return None
    count = 0
    for param in params:
        if param.kind == param.VAR_POSITIONAL:
            return None
        if param.kind in (param.POSITIONAL_ONLY, param.POSITIONAL_OR_KEYWORD):
            count += 1
    return count


def _collect_constructors(cls, java_class):
    """Every ``@jconstructor(...)``-tagged method in ``cls`` or a base of it, as a
    ``{"<init>:...": (function, arity)}`` map ``_ConstructorHandler`` runs in place of ``__init__``
    when the Java constructor with that signature is the one being called.

    Matching is as forgiving as exteraGram's, because real plugins rely on it: a type that does not
    resolve (a typo such as ``Theme.ResourceProvider``) acts as a wildcard, and the constructor is
    taken if exactly one of the superclass's constructors fits the rest. The generated class mirrors
    the superclass's constructors, so nothing else could ever be called - an ambiguous or impossible
    declaration is reported here, at ``extends`` time, not on first instantiation."""
    available = [sig.split(":") for sig in PrimeClassProxy.constructorSignatures(java_class)]
    seen = set()
    constructors = {}
    for klass in cls.__mro__:
        if klass is object or klass is Base:
            continue
        for name, value in vars(klass).items():
            tag = getattr(value, "__jconstructor__", None)
            if tag is None or name in seen:
                continue
            seen.add(name)
            (arg_types,) = tag
            if arg_types is None:
                arg_types = _infer_arg_types(value)
            wanted = [_lenient_type_name(t) for t in arg_types]
            matches = [sig for sig in available if len(sig) == len(wanted) + 1
                       and all(w is None or w == got for w, got in zip(wanted, sig[1:]))]
            if len(matches) != 1:
                # exteraGram skips a hook whose signature fits no constructor rather than failing
                # the class, and plugins ship such hooks (usually debug leftovers) - so do we.
                try:
                    from android_utils import log
                    log("@jconstructor %s on %s: %s matching constructor, hook skipped" % (
                        list(arg_types), PrimeClassProxy.nameOf(java_class), "no" if not matches else "more than one"))
                except Exception:
                    pass
                continue
            constructors.setdefault(":".join(matches[0]), []).append((value, _positional_arity(value)))
    return constructors


class _ConstructorHandler(dynamic_proxy(Utilities.Callback3Return)):
    """One of these per class ``extends`` builds. Chaquopy calls ``run`` for every constructor and
    every overridden method on every instance of that class; the signature's method name is enough
    to tell which, because a fresh Python peer is minted the moment ``"<init>"`` arrives and every
    later call is found through it rather than re-identified from scratch.

    A ``"<preinit>"`` call is different - it arrives *before* ``"<init>"``, with no peer and no real
    ``instance`` (Java passes ``null``, since the Dalvik verifier will not let an uninitialized
    ``this`` reach any call before ``super()`` has run) - so it is routed through ``preconstructors``
    to a plain function instead, called as ``fn(python_class, args)``.
    """

    def __init__(self, python_class, java_to_python=None, preconstructors=None, constructors=None):
        super().__init__()
        self._python_class = python_class
        self._java_to_python = java_to_python or {}
        self._preconstructors = preconstructors or {}
        self._constructors = constructors or {}

    def run(self, instance, signature, args):
        call_args = list(args) if args is not None else []
        if signature.startswith("<preinit>"):
            from java import jarray
            from java.lang import Object as _JObject
            fn = self._preconstructors.get(signature)
            try:
                result = fn(self._python_class, call_args) if fn is not None else None
            except Exception:
                self._report_crash(signature)
                result = None
            return jarray(_JObject)(list(result) if result is not None else call_args)
        name = signature.split(":", 1)[0]
        if name == "<init>":
            peer = self._python_class.__new__(self._python_class)
            peer._prime_java = instance
            pending = _PENDING_INIT_ARGS.get(self._python_class)
            init_args = pending.pop() if pending else call_args
            try:
                # exteraGram's documented order: Java constructor -> __init__ -> @jconstructor
                # hooks -> on_post_init. A hook adds to __init__, it does not replace it.
                peer.on_pre_init(*call_args)
                self._python_class.__init__(peer, *init_args)
                for fn, arity in self._constructors.get(signature, ()):
                    # A hook may declare fewer parameters than the constructor has (real plugins
                    # do); it gets the leading ones rather than a TypeError.
                    fn(peer, *(call_args if arity is None or arity > len(call_args) else call_args[:arity]))
                peer.on_post_init(*call_args)
            except Exception:
                self._report_crash(signature)
            # Through Java: Chaquopy will not put a plain Python object into an Object field.
            PrimeClassProxy.setPeer(instance, peer)
            return None
        peer = getattr(instance, _PEER_FIELD, None)
        if peer is None:
            return None
        python_name = self._java_to_python.get(signature, self._java_to_python.get(name, name))
        # Only what the Python class itself defines: Base.__getattr__ would otherwise hand back the
        # Java method - the generated override - and the call would come straight back here.
        if not hasattr(type(peer), python_name) and python_name not in getattr(peer, "__dict__", {}):
            return None
        method = getattr(peer, python_name, None)
        if method is None:
            return None
        try:
            return method(*call_args)
        except Exception:
            self._report_crash(signature)
            # The Java original, rather than nothing: Java still needs what the method was for
            # done - an onMeasure that sets no size throws and takes the app down with it.
            try:
                return getattr(instance, _SUPER_PREFIX + name)(*call_args)
            except Exception:
                return None

    def _report_crash(self, signature):
        """A method the generated class overrides raised - most often from inside an Android
        callback (draw, measure, click) where nothing else stands between here and an uncaught
        crash on whatever thread called it. Logs it, and if the class belongs to a plugin - it does
        not, for a few internal ``Base.extends`` users this SDK has itself - disables exactly that
        plugin (and its import chain) rather than letting the exception continue past this point,
        which is the only thing standing between a plugin bug and the whole app going down.
        """
        import traceback
        trace = traceback.format_exc()
        try:
            from android_utils import log
            log("class %r crashed in %s:\n%s" % (self._python_class, signature, trace))
        except Exception:
            pass
        module = sys.modules.get(getattr(self._python_class, "__module__", None))
        plugin_id = getattr(module, "__prime_plugin_id__", None)
        if plugin_id is None:
            return
        last_line = trace.strip().splitlines()[-1] if trace.strip() else signature
        try:
            import _prime_loader
            _prime_loader.disable_crashed_plugin(plugin_id, last_line)
        except Exception:
            pass


class Base:
    """Implements Java interfaces, and now extends Java classes too."""

    __jinterfaces__ = ()

    def __init__(self, *args, **kwargs):
        pass

    def on_pre_init(self, *args):
        """Called on the peer right before ``__init__`` runs, with the Java constructor's own
        arguments - ``self.java`` is already valid at this point, ``__init__`` has not run yet."""
        pass

    def on_post_init(self, *args):
        """Called right after ``__init__`` returns, same arguments as ``on_pre_init``."""
        pass

    @property
    def java(self):
        return getattr(self, "_prime_java", self)

    @property
    def this(self):
        return self.java

    def super_(self, method_name):
        """The original implementation of an overridden method - reflection cannot reach it once
        the method is overridden, so the generated class keeps this twin around instead."""
        return getattr(self.java, _SUPER_PREFIX + method_name)

    @classmethod
    def extends(cls, java_class, *interfaces, custom_name=None, **kwargs):
        methods = kwargs.get("methods")
        java_to_python = {}
        if methods is None:
            methods, java_to_python = _overridable_names(cls)
        fields = kwargs.get("fields")
        if fields is None:
            fields = _collect_field_specs(cls)
        mvel_methods = kwargs.get("mvel_methods")
        if mvel_methods is None:
            mvel_methods = _collect_mvel_specs(cls)
        new_methods = kwargs.get("new_methods")
        if new_methods is None:
            new_methods, new_java_to_python = _collect_new_methods(cls)
            java_to_python.update(new_java_to_python)
        overloads = kwargs.get("overloads")
        if overloads is None:
            overloads, overload_java_to_python = _collect_overloads(cls)
            java_to_python.update(overload_java_to_python)
        preconstructors = kwargs.get("preconstructors")
        preconstructor_fns = None
        if preconstructors is None:
            preconstructors, preconstructor_fns = _collect_preconstructors(cls)
        # createProxyClass has six overloads on the Java side (4 to 9 parameters, each
        # shorter one just appending a null and calling the next) purely so plain Java
        # callers can skip trailing arguments - this Python call always supplies all
        # nine anyway (empty lists instead of omitting any), so it uses
        # createProxyClassFull instead, a distinctly-named method with no overloads.
        # Calling the overloaded name directly used to fail here: Chaquopy's static
        # overload resolution has to pick one of six candidates whose parameter lists
        # differ only in how many trailing `List` arguments they carry - all erased to
        # the same `java.util.List` type at the JVM level - and a real, popular .elyx
        # plugin hit a 9-argument call Chaquopy rejected as "cannot be
        # applied" to *any* of the six, even though the argument count unambiguously
        # picks the 9-argument one. Passing empty lists instead of `None` (an earlier
        # fix attempt) narrowed the symptom but did not resolve it - only removing the
        # overload set entirely did.
        #
        # Even after that, a plain Python `list` handed to a `List<SpecificType>`
        # parameter failed the same way once one of these lists actually held an
        # element (`TypeError: Cannot convert list object to java.util.List`) -
        # confirmed by disassembling the same real plugin's compiled bytecode: it
        # declares one `@jmethod(...)`, which is the first time `new_methods` here was
        # ever non-empty against this new call site, so the implicit Python-list-to-
        # java.util.List conversion Chaquopy does for empty lists was never actually
        # exercised for a populated one. Building real `java.util.ArrayList` instances
        # instead removes the guesswork entirely - an ArrayList already *is* a List,
        # nothing left for Chaquopy to convert.
        constructors = _collect_constructors(cls, java_class)
        proxy_class = PrimeClassProxy.createProxyClassFull(
            java_class, _java_list(interfaces), _java_list(methods), _java_list(fields),
            _java_list(mvel_methods), _java_list(new_methods), _java_list(overloads),
            _java_list(preconstructors),
            _ConstructorHandler(cls, java_to_python, preconstructor_fns, constructors))
        cls.__jproxy_class__ = proxy_class
        _install_super_bridge(cls, methods, java_to_python)
        return proxy_class

    @classmethod
    def bind(cls, java_class, *interfaces, **kwargs):
        """Binds this Python class to a Java *interface*, which Chaquopy does natively."""
        if not PrimeClassProxy.isInterface(java_class):
            raise UnsupportedInThisBuild("binding to class %s" % java_class)
        return dynamic_proxy(java_class)

    @classmethod
    def java_class(cls):
        return getattr(cls, "__jproxy_class__", None)

    @classmethod
    def from_java(cls, instance, **kwargs):
        peer = getattr(instance, _PEER_FIELD, None)
        if peer is None:
            raise UnsupportedInThisBuild("from_java on an instance %r did not come from extends()" % (instance,))
        return peer

    @classmethod
    def new_java_instance(cls, *args):
        """Constructs a Java instance of whatever class ``extends`` last built for ``cls``, with
        these constructor arguments. Call ``extends`` first - there is no class to instantiate
        otherwise, and guessing one from ``args`` would be more likely to construct the wrong thing
        than to fail loudly."""
        proxy_class = cls.java_class()
        if proxy_class is None:
            raise UnsupportedInThisBuild("new_java_instance before extends()")
        # The generated class comes back from Java as a java.lang.Class object, not a Chaquopy
        # proxy type; calling it makes Chaquopy look for a functional interface on the Class
        # ("Class is not callable because it implements no functional interfaces").
        if isinstance(proxy_class, type):
            return proxy_class(*args)
        return PrimeClassProxy.newInstance(proxy_class, list(args))

    @classmethod
    def new_instance(cls, *args, init_args=None, **kwargs):
        """Builds the real Java object and hands back its Python peer - the constructor hook
        already made one, so this is ``from_java(new_java_instance(*args))`` in a single call,
        the shape exteraGram's own SDK documents. ``init_args``, when given, are what ``__init__``
        receives instead of the Java constructor's arguments - plugins rely on it to hand the peer
        Python values the Java constructor has no parameter for (reSwaga:
        ``Pill.new_instance(context, provider, init_args=[id, platform, background])``). The Java
        constructor runs synchronously on this thread, so the hook that builds the peer picks them
        up from a per-class stack."""
        if init_args is None:
            return cls.from_java(cls.new_java_instance(*args))
        entry = list(init_args)
        stack = _PENDING_INIT_ARGS.setdefault(cls, [])
        stack.append(entry)
        try:
            return cls.from_java(cls.new_java_instance(*args))
        finally:
            # The constructor hook pops the entry it used; one still here means the constructor
            # failed before reaching the hook.
            for i in range(len(stack) - 1, -1, -1):
                if stack[i] is entry:
                    del stack[i]
                    break
            if not stack:
                _PENDING_INIT_ARGS.pop(cls, None)

    def __getattr__(self, name):
        """Only reached when normal lookup failed: falls through to the Java object, the way
        exteraGram's peers behave - plugins write ``self.actionBar``, ``self.getContext()``,
        ``self.fragmentView`` on the Python side and expect the Java member. Protected and private
        fields (``BaseFragment.actionBar`` is protected) are invisible to Chaquopy's attribute
        access, so those go through reflection."""
        if name.startswith("_"):
            raise AttributeError(name)
        java = self.__dict__.get("_prime_java")
        if java is None:
            raise AttributeError(name)
        try:
            return getattr(java, name)
        except AttributeError:
            pass
        field = _java_field(java, name)
        if field is None:
            raise AttributeError("%s has no attribute %r (neither the Python peer nor %s)" % (
                type(self).__name__, name, PrimeClassProxy.nameOf(java.getClass())))
        return field.get(java)

    def __setattr__(self, name, value):
        """``self.fragmentView = view`` in a peer has to land in the Java field - ``ActionBarLayout``
        reads ``fragment.fragmentView``, not a Python attribute. A name the Python class itself
        defines, or already holds, stays Python; so does any name with no such Java field."""
        if not name.startswith("_") and name not in self.__dict__ and not hasattr(type(self), name):
            java = self.__dict__.get("_prime_java")
            if java is not None:
                field = _java_field(java, name)
                if field is not None:
                    try:
                        field.set(java, value)
                        return
                    except Exception:
                        pass  # wrong type for the field: keep it as a Python attribute instead
        super().__setattr__(name, value)


class PyObj(Base):
    """A Python value passed through Java and back unchanged."""

    def __init__(self, obj=None):
        super().__init__()
        self.obj = obj

    @classmethod
    def create(cls, obj):
        return cls(obj)


def _unsupported(name):
    def decorator(*args, **kwargs):
        raise UnsupportedInThisBuild(name)

    return decorator


# @joverride, @jfield/@jgetmethod/@jsetmethod, jMVELmethod/jMVELoverride, @jmethod, @joverload,
# @jpreconstructor, @jconstructor are real now. Still not: @jclassbuilder, which wants the raw
# DexMaker object handed to Python for arbitrary bytecode changes.
def jconstructor(*signature, arg_types=None):
    """The body of one Java constructor, picked by its argument types - runs on the fresh Python
    peer in place of ``__init__`` when that constructor is the one called, after ``super(...)``
    has already run with the same arguments. Real plugins write ``@jconstructor([View])``; the
    types may also be given loose (``@jconstructor(View)``), as ``arg_types=``, or left to the
    method's own parameter annotations (bare ``@jconstructor`` or ``@jconstructor()``)."""
    import inspect
    if len(signature) == 1 and arg_types is None and inspect.isfunction(signature[0]):
        signature[0].__jconstructor__ = (None,)
        return signature[0]
    if arg_types is None and signature:
        if len(signature) == 1 and isinstance(signature[0], (list, tuple)):
            arg_types = list(signature[0])
        else:
            arg_types = list(signature)

    def decorator(fn):
        fn.__jconstructor__ = (arg_types,)
        return fn
    return decorator


def jpreconstructor(arg_types=None):
    """Rewrites a constructor's arguments before ``super(...)`` runs, picking the matching overload
    by ``arg_types`` (explicit, or inferred from the method's own parameter annotations, same as
    ``jmethod``'s). The decorated function is called as ``fn(python_class, args)`` - a classmethod
    shape, not an instance method, since no Python peer exists yet at this point in construction -
    and must return the replacement argument list; returning ``args`` unchanged is a valid answer.
    """
    def decorator(fn):
        fn.__jpreconstructor__ = (arg_types,)
        return fn
    return decorator


def joverload(name=None, arg_types=None):
    """Picks one specific overload of a superclass method by its exact argument types, where a
    plain override (just defining a same-named method) would route every overload of that name to
    the same Python method. ``arg_types`` works the same as ``jmethod``'s - explicit type strings,
    or inferred from the method's own parameter annotations when left out. exteraGram's ``jMVELoverride``
    has no equivalent of this for MVEL bodies; a plugin needing per-overload MVEL still has to pick
    one overload with this and call ``self.super_(name)`` from Python for the others.
    """
    def decorator(fn):
        fn.__joverload__ = (name, arg_types)
        return fn
    return decorator


def jmethod(*signature, name=None, return_type=None, arg_types=None, modifiers="public", throws=None):
    """A new method on the generated class with no superclass method of the same signature to
    override - plain Python behaviour, reachable from Java code (including other plugins' classes)
    that only sees the generated class. ``return_type``/``arg_types`` are type strings the same way
    ``jfield``'s is; left out, they are inferred from the method's own annotations, the way
    exteraGram's docs write the example (``def f(self, value: "int") -> "java.lang.String":``).
    The signature may also be given positionally, as exteraGram accepts it: name, return type,
    argument type list. Bare ``@jmethod`` works as well.
    """
    if len(signature) == 1 and getattr(signature[0], "__code__", None) is not None:
        fn = signature[0]
        fn.__jnewmethod__ = (None, None, None)
        return fn
    pos_name, pos_return, pos_types = _split_signature(signature)
    final = (name or pos_name, return_type or pos_return, arg_types if arg_types is not None else pos_types)

    def decorator(fn):
        fn.__jnewmethod__ = final
        return fn
    return decorator


def jMVELmethod(return_type=None, arguments=None, code=""):
    """A new method on the generated class, with no Python call in its body at all - just ``code``,
    MVEL, run by the same engine ``HookFilter.Condition`` uses. ``arguments`` is a list of
    ``(name, type_string)`` pairs, each becoming an MVEL variable of that name; ``return_type`` is a
    type string the same way ``jfield``'s is.
    """
    return _MvelMethodSpec(return_type, arguments, code, is_override=False)


def jMVELoverride(arguments=None, code=""):
    """The ``jMVELmethod`` of overrides: ``code`` replaces the named method's existing
    implementation on the superclass, taking its real return type and parameter types from there
    rather than from ``arguments`` (still worth passing, for the variable names). To call the
    original from inside the expression, use ``this.prime$super$<name>(...)`` - not exteraGram's own
    ``SUPER_<name>(...)`` convention, which is theirs to name and not reachable here; the trampoline
    this generates for exactly this purpose is the same one a Python override's handler reaches
    through ``self.super_(name)``.
    """
    return _MvelMethodSpec(None, arguments, code, is_override=True)


jmvelmethod = jMVELmethod
jmveloverride = jMVELoverride


def jfield(type_string, default=None, methods=None):
    """A new field on the generated Java class, of the type named by ``type_string`` (``"int"``,
    ``"java.lang.String"``, and so on - the primitives spelled the way Java itself spells them, not
    Python's names for them). Read and write it as a plain attribute on ``self``; see ``_FieldSpec``
    for what ``default`` does not do yet.
    """
    return _FieldSpec(type_string, default, methods)


def jgetmethod(java_method_name):
    """In a ``jfield(..., methods=[...])`` list: a real Java method named ``java_method_name`` that
    reads the field, for code that expects a getter rather than direct field access."""
    return _AccessorSpec(java_method_name, True)


def jsetmethod(java_method_name):
    """The ``jgetmethod`` of setters."""
    return _AccessorSpec(java_method_name, False)


def java_subclass(java_class, *interfaces, custom_name=None):
    """``@java_subclass(SomeJavaClass)`` on a ``Base`` subclass - the decorator form of calling
    ``extends`` right after the class body, which is all this does."""
    def decorator(cls):
        cls.extends(java_class, *interfaces, custom_name=custom_name)
        return cls
    return decorator


def _split_signature(signature):
    """exteraGram's decorators take their Java signature positionally, loosely: a method name
    string, then optionally the argument types as a list/tuple (and for jmethod a return type
    string before them). Returns (name, return_type, arg_types)."""
    name = return_type = arg_types = None
    for part in signature:
        if isinstance(part, (list, tuple)):
            arg_types = list(part)
        elif isinstance(part, str):
            if name is None:
                name = part
            elif return_type is None:
                return_type = part
        elif part is not None:
            # a Java class given as a type: the argument list written without brackets
            arg_types = (arg_types or []) + [part]
    return name, return_type, arg_types


def joverride(*signature, arg_types=None, modifiers="public", throws=None, name=None):
    """Marks a method as overriding a Java method: its own name by default, or the name given
    (``@joverride("equals")`` on an ``equals_``), for when the Python name has to differ. Given
    argument types too - ``@joverride("getItemOffsets", ["android.graphics.Rect", ...])`` - it
    overrides only that overload, exactly like ``joverload``. Bare ``@joverride`` works as well.
    ``modifiers``/``throws`` are accepted for exteraGram's signature; the generated method is always
    public and declares nothing."""
    if len(signature) == 1 and callable(signature[0]) and not isinstance(signature[0], str)             and getattr(signature[0], "__code__", None) is not None and arg_types is None and name is None:
        fn = signature[0]
        fn.__joverride_target__ = fn.__name__
        return fn
    java_name, _, positional_types = _split_signature(signature)
    java_name = name or java_name
    types = arg_types if arg_types is not None else positional_types

    def decorator(fn):
        if types is not None:
            fn.__joverload__ = (java_name or fn.__name__, list(types))
        else:
            fn.__joverride_target__ = java_name or fn.__name__
        return fn
    return decorator


class JHelper:
    """The ``@JHelper.X`` namespace exteraGram documents next to the bare ``jX`` decorators -
    both spellings reach the same function, since plugins in the wild use either."""
    Overload = staticmethod(joverload)
    Method = staticmethod(jmethod)
    Constructor = staticmethod(jconstructor)
    PreConstructor = staticmethod(jpreconstructor)
    ClassBuilder = staticmethod(_unsupported("@jclassbuilder"))
    MVELMethod = staticmethod(jMVELmethod)
    MVELOverride = staticmethod(jMVELoverride)
    Override = staticmethod(joverride)
    Field = staticmethod(jfield)
    GetMethod = staticmethod(jgetmethod)
    SetMethod = staticmethod(jsetmethod)


jclassbuilder = JHelper.ClassBuilder


def class_proxy(clazz, **kwargs):
    raise UnsupportedInThisBuild("class_proxy")
