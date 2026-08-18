"""``from elyx import metainfo`` - the currently-running (.elyx) plugin's own manifest
fields (id/name/description/author/version/icon), so a plugin can introspect itself
instead of hardcoding the same values twice - once in meta.yml, once in source. Same
situation as ``elyx.settings``: no source-level documentation for elyxbuilder's own
``elyx.metainfo`` to copy the call convention from, so this supports every shape a
plugin author plausibly reaches for - attribute access (``metainfo.version``),
dict-style (``metainfo['version']``/``'version' in metainfo``), and ``.get(key,
default)``.

Real-world failure this fixes: the same real, popular .elyx plugin that first surfaced
the ``elyx.settings`` gap crashed on load with
``ImportError: cannot import name 'metainfo' from 'elyx'`` because this module simply
didn't exist yet.

Caller's plugin id is recovered the same way ``elyx.strings``/``elyx.settings`` do - by
walking the call stack for the nearest frame whose top-level package matches the
``_prime_loader._MODULE_PREFIX + plugin_id`` synthetic package name a loaded plugin
runs as - backed by ``_prime_loader.get_metainfo``, which already has these fields on
the loaded plugin object regardless of whether it came from a ``.plugin`` file's
module-level dunders or a packaged ``.elyx``'s meta.yml.
"""

import sys

import _prime_loader

_MODULE_PREFIX = "prime_plugin_"


class _MetaInfo:

    def _caller_plugin_id(self):
        # frame 0 is this method, frame 1 is the public method that called it, frame 2
        # is the actual caller - same two-hop skip elyx.strings/elyx.settings use.
        frame = sys._getframe(2)
        while frame is not None:
            package = frame.f_globals.get("__package__") or frame.f_globals.get("__name__")
            if package:
                top = package.split(".")[0]
                if top.startswith(_MODULE_PREFIX):
                    return top[len(_MODULE_PREFIX):]
            frame = frame.f_back
        return None

    def _manifest_for(self, plugin_id):
        if plugin_id is None:
            return {}
        return _prime_loader.get_metainfo(plugin_id)

    def __getattr__(self, name):
        manifest = self._manifest_for(self._caller_plugin_id())
        if name in manifest:
            return manifest[name]
        raise AttributeError(name)

    def __getitem__(self, key):
        return self._manifest_for(self._caller_plugin_id()).get(key)

    def get(self, key, default=None):
        return self._manifest_for(self._caller_plugin_id()).get(key, default)

    def __contains__(self, key):
        return key in self._manifest_for(self._caller_plugin_id())

    def as_dict(self):
        return dict(self._manifest_for(self._caller_plugin_id()))


# Same "a module that behaves like an object" trick elyx.strings/elyx.settings use -
# replacing this module's own sys.modules entry means `from elyx import metainfo;
# metainfo.version` binds the instance.
sys.modules[__name__] = _MetaInfo()
