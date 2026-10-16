"""Kotlin-style properties on Java objects: ``paint.strokeWidth``, ``view.isClickable = True``.

exteraGram's plugins read and write Java beans the way Kotlin does - a getter as if it were a field,
a setter as an assignment. Chaquopy on its own knows only real fields and methods, so each of those
raised AttributeError; reSwaga's settings cards died half-built on ``self.isClickable = True`` (its
``card_actions`` never got assigned) and its preview drawable on ``paint.strokeWidth``.

Only a fallback: Python reaches ``__getattr__`` solely after the normal lookup has already failed,
and the setter is tried only once Chaquopy has refused the assignment. Real fields and methods are
found exactly as before and cost nothing extra.

- read ``x.foo`` -> ``x.getFoo()``, else ``x.isFoo()``
- write ``x.foo = v`` -> ``x.setFoo(v)``; ``x.isFoo = v`` -> ``x.setFoo(v)``
"""

_installed = False


def _capitalize(name):
    return name[0].upper() + name[1:]


def _setter_name(name):
    if len(name) > 2 and name.startswith("is") and name[2].isupper():
        name = name[2:]
    return "set" + _capitalize(name)


def install():
    global _installed
    if _installed:
        return
    from java import jclass
    java_object = jclass("java.lang.Object")
    original_getattribute = java_object.__getattribute__
    original_setattr = java_object.__setattr__

    def __getattr__(self, name):
        if not name or name.startswith("_"):
            raise AttributeError(name)
        capitalized = _capitalize(name)
        for getter in ("get" + capitalized, "is" + capitalized):
            try:
                method = original_getattribute(self, getter)
            except AttributeError:
                continue
            try:
                return method()
            except TypeError:
                # Only overloads that take arguments: not a property getter.
                continue
        raise AttributeError("'%s' object has no attribute '%s'" % (type(self).__name__, name))

    def __setattr__(self, name, value):
        try:
            original_setattr(self, name, value)
            return
        except AttributeError as error:
            if not name or name.startswith("_"):
                raise
            try:
                setter = original_getattribute(self, _setter_name(name))
            except AttributeError:
                raise error
        setter(value)

    # type.__setattr__: Chaquopy's own metaclass treats assignment on a class as a static field write.
    type.__setattr__(java_object, "__getattr__", __getattr__)
    type.__setattr__(java_object, "__setattr__", __setattr__)
    _installed = True
