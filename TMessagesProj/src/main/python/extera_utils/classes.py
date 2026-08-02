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

from java import dynamic_proxy
from org.telegram.messenger import Utilities
from org.telegram.messenger.plugins import PrimeClassProxy

_PEER_FIELD = PrimeClassProxy.PEER_FIELD
_SUPER_PREFIX = "prime$super$"


class UnsupportedInThisBuild(NotImplementedError):
    def __init__(self, what):
        super().__init__("%s needs runtime dex generation, which this build does not have" % what)


def _overridable_names(cls):
    """Every method ``cls`` itself defines, over what every Python class already has - those, and
    only those, are generated as real overrides. A plugin does not declare which methods it is
    overriding; it just defines them, the same as subclassing anything else in Python."""
    skip = {"__init__", "extends", "bind", "java_class", "from_java", "new_java_instance", "new_instance"}
    names = set()
    for klass in cls.__mro__:
        if klass is object or klass is Base:
            continue
        for name, value in vars(klass).items():
            if name.startswith("__") or name in skip or not callable(value):
                continue
            names.add(name)
    return list(names)


class _ConstructorHandler(dynamic_proxy(Utilities.Callback3Return)):
    """One of these per class ``extends`` builds. Chaquopy calls ``run`` for every constructor and
    every overridden method on every instance of that class; the signature's method name is enough
    to tell which, because a fresh Python peer is minted the moment ``"<init>"`` arrives and every
    later call is found through it rather than re-identified from scratch."""

    def __init__(self, python_class):
        super().__init__()
        self._python_class = python_class

    def run(self, instance, signature, args):
        name = signature.split(":", 1)[0]
        call_args = list(args) if args is not None else []
        if name == "<init>":
            peer = self._python_class.__new__(self._python_class)
            peer._prime_java = instance
            self._python_class.__init__(peer, *call_args)
            setattr(instance, _PEER_FIELD, peer)
            return None
        peer = getattr(instance, _PEER_FIELD, None)
        if peer is None:
            return None
        method = getattr(peer, name, None)
        if method is None:
            return None
        return method(*call_args)


class Base:
    """Implements Java interfaces, and now extends Java classes too."""

    __jinterfaces__ = ()

    def __init__(self, *args, **kwargs):
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
    def extends(cls, java_class, *interfaces, **kwargs):
        methods = kwargs.get("methods")
        if methods is None:
            methods = _overridable_names(cls)
        proxy_class = PrimeClassProxy.createProxyClass(
            java_class, list(interfaces) or None, list(methods), _ConstructorHandler(cls))
        cls.__jproxy_class__ = proxy_class
        return proxy_class

    @classmethod
    def bind(cls, java_class, *interfaces, **kwargs):
        """Binds this Python class to a Java *interface*, which Chaquopy does natively."""
        if not java_class.isInterface():
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
        return proxy_class(*args)

    @classmethod
    def new_instance(cls, *args, **kwargs):
        return cls(*args)


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


# These are exteraGram's lower-level per-method annotations - decorate one method at a time, on a
# class already being built some other way. What that other way looks like on their side is not in
# this build to copy from (their own Python SDK source is not in the APK, only the Java it calls
# into), so rather than guess a calling convention and risk a decorator that runs and does the wrong
# thing, they stay unsupported. ``Base.extends`` above is the one path here that is real: whole-class,
# not per-method, and everything a Python subclass defines becomes an override automatically.
class JHelper:
    Override = staticmethod(_unsupported("@joverride"))
    Overload = staticmethod(_unsupported("@joverload"))
    Method = staticmethod(_unsupported("@jmethod"))
    MVELMethod = staticmethod(_unsupported("@jmvelmethod"))
    MVELOverride = staticmethod(_unsupported("@jmveloverride"))
    Constructor = staticmethod(_unsupported("@jconstructor"))
    PreConstructor = staticmethod(_unsupported("@jpreconstructor"))
    Field = staticmethod(_unsupported("@jfield"))
    GetMethod = staticmethod(_unsupported("@jgetmethod"))
    SetMethod = staticmethod(_unsupported("@jsetmethod"))
    ClassBuilder = staticmethod(_unsupported("@jclassbuilder"))


joverride = JHelper.Override
joverload = JHelper.Overload
jmethod = JHelper.Method
jfield = JHelper.Field
jconstructor = JHelper.Constructor
jpreconstructor = JHelper.PreConstructor
jgetmethod = JHelper.GetMethod
jsetmethod = JHelper.SetMethod
jmvelmethod = JHelper.MVELMethod
jmveloverride = JHelper.MVELOverride
jMVELmethod = JHelper.MVELMethod
jMVELoverride = JHelper.MVELOverride
jclassbuilder = JHelper.ClassBuilder


def java_subclass(*args, **kwargs):
    raise UnsupportedInThisBuild("java_subclass")


def class_proxy(clazz, **kwargs):
    raise UnsupportedInThisBuild("class_proxy")
