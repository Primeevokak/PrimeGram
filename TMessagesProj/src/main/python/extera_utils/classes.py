"""Subclassing Java from Python.

exteraGram generates dex at run time so a plugin can extend an arbitrary app class - a custom Cell,
a fragment of its own. PrimeGram does not carry a dex generator, and there is no honest way to fake
one: a plugin that thinks it has subclassed ``FrameLayout`` and has not will fail at the moment it
is drawn, deep inside the app, with a stack trace that names none of this.

So the names exist and say what is missing. Implementing *interfaces* does work, because Chaquopy
can do that natively, and that covers listeners - which is what most plugins reaching for this
actually want.
"""

from java import dynamic_proxy


class UnsupportedInThisBuild(NotImplementedError):
    def __init__(self, what):
        super().__init__("%s needs runtime dex generation, which this build does not have" % what)


class Base:
    """Implements Java interfaces. Extending Java classes is not available in this build."""

    __jinterfaces__ = ()

    def __init__(self, *args, **kwargs):
        pass

    @property
    def java(self):
        return self

    @property
    def this(self):
        return self

    @classmethod
    def extends(cls, java_class, *interfaces, **kwargs):
        raise UnsupportedInThisBuild("extending %s" % java_class)

    @classmethod
    def bind(cls, java_class, *interfaces, **kwargs):
        """Binds this Python class to a Java *interface*, which is supported."""
        if not java_class.isInterface():
            raise UnsupportedInThisBuild("binding to class %s" % java_class)
        return dynamic_proxy(java_class)

    @classmethod
    def java_class(cls):
        return getattr(cls, "__jproxy_class__", None)

    @classmethod
    def from_java(cls, instance, **kwargs):
        raise UnsupportedInThisBuild("from_java")

    @classmethod
    def new_java_instance(cls, *args):
        raise UnsupportedInThisBuild("new_java_instance")

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
