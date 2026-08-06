"""A soft landing for plugins written against exteraGram's own Java classes.

exteraGram is a different app - its own package, its own compiled classes under
``com.exteragram.*``. A handful of its plugins import a few small ones directly, most often to
run an ``isinstance`` check against a custom View their menu-building code creates. We do not have
that code, so we cannot have that class either, and no amount of Python can conjure a real Android
View with real rendering behaviour.

What we can do is stop that import from taking the whole plugin down with it. Registered here is a
:pep:`302` meta path finder that only ever answers for names starting with ``com.exteragram`` -
and only once every real finder, Chaquopy's Java one included, has already said no. It hands back
an inert stand-in for whatever was asked for: a class nothing of ours will ever be an instance of,
so ``isinstance`` against it is simply always false, and a plugin built around "if this optional
exteraGram-only feature is available, use it" degrades exactly the way that phrasing suggests -
quietly, to the feature not being available - instead of failing to load at all.

This is deliberately narrow. ``com.exteragram`` is exteraGram's own application id; nothing else,
ours or any library's, lives under it, so answering for that prefix and nothing else cannot shadow
a real class. Appended to the end of ``sys.meta_path`` rather than inserted at the front, so it is
consulted last - a class that genuinely exists, here or via Chaquopy, is always found first and
this finder never sees the request.
"""

import sys
import types
from importlib.abc import Loader, MetaPathFinder
from importlib.machinery import ModuleSpec

_ROOT = "com.exteragram"


class _PlaceholderMeta(type):
    """Makes attribute access on the class itself as forgiving as on its instances.

    A Java class stands in for both a type (used in ``isinstance``) and, in exteraGram's own code,
    a bag of static constants (``PluginsConstants.SOMETHING``). Without this, only the second use
    would raise - ``__getattr__`` defined on ``_Placeholder`` covers its instances, not the class
    object itself, which Python looks up through its metaclass instead.
    """

    def __getattr__(cls, name):
        if name.startswith("__") and name.endswith("__"):
            raise AttributeError(name)
        return _Placeholder


class _Placeholder(metaclass=_PlaceholderMeta):
    """Stands in for a Java class we do not have.

    A class, not an instance: what a plugin imports here is almost always fed straight to
    ``isinstance``, and ``isinstance`` demands a type as its second argument. Being a real (if
    empty) class also means constructing one does not raise, and nothing will ever be an instance
    of it - the two things a stand-in for an absent class needs to get right.
    """

    def __init__(self, *args, **kwargs):
        pass

    def __getattr__(self, name):
        if name.startswith("__") and name.endswith("__"):
            raise AttributeError(name)
        return _Placeholder

    def __repr__(self):
        return "<not available in PrimeGram>"

    def __bool__(self):
        return False

    def __iter__(self):
        # A method that isinstance-checks against this class is not the only shape a plugin
        # reaches for one of these: reflection calls like Class.getDeclaredMethods() or
        # getInterfaces() are expected to return a (possibly empty) array, and __getattr__ above
        # answers those exactly the same way as everything else - another _Placeholder, standing
        # in for the return value. Without this, the very first "for m in clazz.getDeclaredMethods()"
        # a plugin writes crashes with "'_Placeholder' object is not iterable" instead of the empty
        # result a real reflection call would give for a class with nothing of interest on it -
        # which is what let a single missing class (zwylib's PythonPluginsEngine, exteraGram-only)
        # take the whole plugin down over a hook it was prepared to skip if not found.
        return iter(())

    def __len__(self):
        return 0

    def __contains__(self, item):
        return False


class _CompatLoader(Loader):

    def create_module(self, spec):
        module = types.ModuleType(spec.name)
        cache = {}

        def resolve(name):
            # Dunders are the import system's own introspection protocol, not a class a plugin
            # asked for - answering them the same way as everything else is what handed
            # inspect.stack() a class object where it expected __file__ to be a path string (or
            # None), which is not a "Java class we don't have" situation at all, just a module
            # object that never claimed to have one. AttributeError here is what a module missing
            # an optional dunder is supposed to raise; giving up that distinction is the bug.
            if name.startswith("__") and name.endswith("__"):
                raise AttributeError(name)
            # A distinct placeholder per requested name, not one shared class: two different
            # exteraGram classes are unrelated types in their app, and collapsing them into one
            # would make isinstance(x, A) and isinstance(x, B) agree with each other for no
            # reason. Cached so the same name always yields the same object, the way a real import
            # would.
            found = cache.get(name)
            if found is None:
                found = type(name, (_Placeholder,), {"__module__": spec.name})
                cache[name] = found
            return found

        # A real exteraGram module has a fixed set of names; we cannot know it in advance, so
        # anything asked for is answered rather than guessing which few to define.
        module.__getattr__ = resolve
        module.__file__ = None  # a module with no backing file, same as any Java-only module
        module.__path__ = []  # makes it a package, so a further dotted import can go past it
        return module

    def exec_module(self, module):
        pass


class _ExteraGramCompatFinder(MetaPathFinder):

    def find_spec(self, name, path, target=None):
        if name == _ROOT or name.startswith(_ROOT + "."):
            return ModuleSpec(name, _CompatLoader(), is_package=True)
        if name == "com":
            # Not a claim that "com" is ours - a namespace contribution, offered only as the
            # last finder in line. If Chaquopy's own Java importer resolves "com" first (which it
            # normally would: com.google.*, whatever else the app carries), that wins outright and
            # this is never used. It exists for the one build where bare "com" resolves nowhere at
            # all - which is the failure actually seen - so that "com.exteragram" underneath it has
            # something to attach to.
            spec = ModuleSpec(name, None)
            spec.submodule_search_locations = []
            return spec
        return None


def install():
    for finder in sys.meta_path:
        if isinstance(finder, _ExteraGramCompatFinder):
            return
    sys.meta_path.append(_ExteraGramCompatFinder())
