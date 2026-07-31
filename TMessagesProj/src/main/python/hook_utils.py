"""Reflection: reading and writing fields the app never meant to expose.

This is how a plugin reaches state that has no getter - a private field on a fragment, a static
cache inside a controller. It is unavoidable in a plugin API and it is also the sharpest tool in
it: nothing here checks that a field means what the plugin thinks it means, and a wrong write is a
crash in code that has no idea a plugin exists.

Fields are looked up up the whole class hierarchy, not just the class itself, because the field a
plugin wants is usually declared on a base class several steps up.
"""

from java import jclass


def find_class(name):
    """The class with this name, or None. Plugins use it to test whether a build has something."""
    try:
        return jclass(name)
    except Exception:
        return None


def _declared_field(clazz, name):
    current = clazz
    while current is not None:
        try:
            field = current.getDeclaredField(name)
            field.setAccessible(True)
            return field
        except Exception:
            current = current.getSuperclass()
    raise AttributeError("no field %r on %s" % (name, clazz.getName()))


def get_private_field(obj, field_name):
    return _declared_field(obj.getClass(), field_name).get(obj)


def set_private_field(obj, field_name, value):
    _declared_field(obj.getClass(), field_name).set(obj, value)


def get_static_private_field(clazz, field_name):
    if isinstance(clazz, str):
        clazz = jclass(clazz)
    return _declared_field(clazz, field_name).get(None)


def set_static_private_field(clazz, field_name, value):
    if isinstance(clazz, str):
        clazz = jclass(clazz)
    _declared_field(clazz, field_name).set(None, value)
