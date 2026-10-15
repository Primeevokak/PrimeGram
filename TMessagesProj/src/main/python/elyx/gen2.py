"""``from elyx import gen2`` - builds an ad-hoc Java-interface implementation from plain
Python callables, one per interface method, instead of writing a full ``Base`` subclass
for a single throwaway listener (a ``TextWatcher``, a ``View.OnAttachStateChangeListener``
- exactly what a real, popular .elyx plugin uses it for, confirmed by
disassembling its compiled bytecode since there is no source-level documentation for
elyxbuilder's own ``elyx.gen2`` to read instead).

Two things confirmed directly from that bytecode, not guessed:
- Keys are the Java method's own name, verbatim (``onViewAttachedToWindow``,
  ``beforeTextChanged``/``onTextChanged``/``afterTextChanged``) - no snake_case
  conversion happens.
- ``gen2(Interface, method=impl, ...)`` returns something the plugin always calls again
  with no arguments right after (``gen2(Interface, ...)()``) to get the actual instance -
  so this returns the *class*, matching that two-step shape, the same one
  ``java_subclass``/``Base.extends`` already use elsewhere in this SDK for a full class
  body instead of inline lambdas.

Built on Chaquopy's own ``dynamic_proxy`` (interfaces only - a real Java *class* still
needs ``extras_utils.classes.Base.extends``, unchanged).
"""

import sys

from java import dynamic_proxy


def _void_method_names(java_interface):
    try:
        from org.telegram.messenger.plugins import PrimeClassProxy
        return set(PrimeClassProxy.voidMethodNames(java_interface))
    except Exception:
        return set()


def gen2(java_interface, **method_impls):
    # A void interface method's result is dropped, as elyx does: plugins hand in lambdas such as
    # ``onViewAttachedToWindow=lambda v: (a(), b())`` - a tuple Chaquopy cannot turn into void,
    # which crashed inside View.dispatchAttachedToWindow (reSwaga's keyboard page).
    voids = _void_method_names(java_interface)
    namespace = {}
    for method_name, impl in method_impls.items():
        if method_name in voids:
            namespace[method_name] = (lambda impl: lambda self, *args: (impl(*args), None)[1])(impl)
        else:
            namespace[method_name] = (lambda impl: lambda self, *args: impl(*args))(impl)
    return type("_ElyxGen2Impl", (dynamic_proxy(java_interface),), namespace)


# Not the "module replaces itself with an instance" trick the other elyx.* submodules
# use - gen2 needs no per-plugin state, so the module's own sys.modules entry is
# replaced directly with the function itself, which is already callable.
sys.modules[__name__] = gen2
