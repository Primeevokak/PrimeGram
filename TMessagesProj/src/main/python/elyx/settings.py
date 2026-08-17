"""``from elyx import settings`` - persistent per-plugin settings for packaged (.elyx) plugins,
called from a HELPER module that isn't the plugin's own ``BasePlugin`` subclass (which already has
``self.get_setting``/``self.set_setting`` - see ``base_plugin.py``) and so has no plugin instance to
call those on. Real-world failure this fixes: a .elyx plugin (``meeow_reswaga``/reSwaga) crashed on
load with ``ImportError: cannot import name 'settings' from 'elyx'`` because this module simply
didn't exist yet - only ``elyx.strings`` did.

There's no source-level documentation for elyxbuilder's own ``elyx.settings`` to copy the exact
call convention from (unlike ``elyx.strings``, which was confirmed by disassembling a real .elyx's
bytecode), so this supports every call shape a plugin author plausibly reaches for -
``get(key, default)``/``set(key, value)``, the exteraGram-SDK-style ``get_setting``/``set_setting``
aliases, and dict-style ``settings['key']``/``settings['key'] = value``/``'key' in settings`` - all
backed by the exact same per-plugin storage ``BasePlugin.get_setting``/``set_setting`` already use
(``plugin_settings.py`` -> ``PrimePluginStore`` on the Java side), so a helper module's reads/writes
and the plugin's own ``self.get_setting`` calls see the same values.

Caller's plugin id is recovered the same way ``elyx.strings`` recovers which locale table to read -
by walking the call stack for the nearest frame whose top-level package matches the
``_prime_loader._MODULE_PREFIX + plugin_id`` synthetic package name a loaded .elyx plugin runs as.
"""

import sys

import plugin_settings

_MODULE_PREFIX = "prime_plugin_"


class _Settings:

    def get(self, key, default=None):
        plugin_id = self._caller_plugin_id()
        if plugin_id is None:
            return default
        return plugin_settings.get_setting(plugin_id, key, default)

    def set(self, key, value):
        plugin_id = self._caller_plugin_id()
        if plugin_id is None:
            return value
        return plugin_settings.set_setting(plugin_id, key, value)

    # exteraGram-SDK-style aliases - BasePlugin's own get_setting/set_setting use these names.
    get_setting = get
    set_setting = set

    def __getitem__(self, key):
        return self.get(key)

    def __setitem__(self, key, value):
        self.set(key, value)

    def __contains__(self, key):
        _MISSING = object()
        return self.get(key, _MISSING) is not _MISSING

    def _caller_plugin_id(self):
        # frame 0 is this method, frame 1 is get/set/__getitem__/__setitem__, frame 2 is the
        # actual caller - same two-hop skip elyx.strings._Strings._caller_top_package() uses.
        frame = sys._getframe(2)
        while frame is not None:
            package = frame.f_globals.get("__package__") or frame.f_globals.get("__name__")
            if package:
                top = package.split(".")[0]
                if top.startswith(_MODULE_PREFIX):
                    return top[len(_MODULE_PREFIX):]
            frame = frame.f_back
        return None


# Same "a module that behaves like an object" trick elyx.strings uses - replacing this module's
# own sys.modules entry means `from elyx import settings; settings.get('x')` binds the instance.
sys.modules[__name__] = _Settings()
