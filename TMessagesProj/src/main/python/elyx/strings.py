"""``from elyx import strings; strings['some_key']`` - localization lookup for packaged (.elyx)
plugins. The exact contract (subscript access, not a function call) was confirmed by disassembling
a real .elyx's compiled bytecode with a matching CPython 3.11 interpreter (e.g. ``data/platforms.py``
calling ``strings['help_spotify']``), since there is no source-level documentation for elyxbuilder's
own ``elyx`` module to read instead.

A plugin's locale directory is registered here by ``_prime_loader.load_elyx_plugin`` right before
that plugin's package executes, keyed by its synthetic top-level package name
(``_prime_loader._MODULE_PREFIX + plugin_id``) - looked up per call via the *calling* frame's own
package, so two .elyx plugins loaded at the same time never read each other's locale files.
"""

import json
import os
import sys


class _Strings:
    def __init__(self):
        self._locale_dirs = {}
        self._cache = {}

    def register(self, package_name, locales_dir):
        self._locale_dirs[package_name] = locales_dir
        self._cache.pop(package_name, None)

    def unregister(self, package_name):
        self._locale_dirs.pop(package_name, None)
        self._cache.pop(package_name, None)

    def __getitem__(self, key):
        package_name = self._caller_top_package()
        table = self._table_for(package_name)
        # A missing key reads back as itself rather than raising - a plugin's own UI text is not
        # worth crashing over, and a key showing up verbatim is an obvious enough hint to whoever
        # is looking at it that a translation is missing.
        return table.get(key, key)

    def get(self, key, default=None):
        table = self._table_for(self._caller_top_package())
        return table.get(key, default if default is not None else key)

    def _caller_top_package(self):
        # frame 0 is this method, frame 1 is __getitem__/get, frame 2 is the actual caller.
        frame = sys._getframe(2)
        while frame is not None:
            package = frame.f_globals.get("__package__") or frame.f_globals.get("__name__")
            if package:
                top = package.split(".")[0]
                if top in self._locale_dirs:
                    return top
            frame = frame.f_back
        return None

    def _table_for(self, package_name):
        if package_name is None:
            return {}
        if package_name in self._cache:
            return self._cache[package_name]
        locales_dir = self._locale_dirs.get(package_name)
        table = {}
        if locales_dir:
            lang = _current_language()
            table = _read_locale_json(locales_dir, lang) or _read_locale_json(locales_dir, "en") or {}
        self._cache[package_name] = table
        return table


def _current_language():
    try:
        from org.telegram.messenger import LocaleController
        code = str(LocaleController.getLocaleStringIso639())
        return code or "en"
    except Exception:
        return "en"


def _read_locale_json(locales_dir, lang):
    path = os.path.join(locales_dir, "strings_%s.json" % lang)
    if not os.path.isfile(path):
        return None
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else None
    except Exception:
        return None


# The standard "a module that behaves like an object" trick: replacing this module's own entry in
# sys.modules means `from elyx import strings; strings['x']` binds the *instance*, not the module,
# so subscript access works without the caller needing to know that.
sys.modules[__name__] = _Strings()
