"""Persistence for plugin settings.

The values live in Java (see ``PrimePluginStore``), not in a file next to the plugin, so that the
settings screen keeps working when the plugin is switched off and its Python side no longer exists.
This module is the Python face of that store: a small cache in front of one JSON blob per plugin.

The signatures follow the exteraGram SDK exactly - ``init`` takes a directory and a preferences
object it does not need here - because plugins in the wild call these directly.
"""

import json

from org.telegram.messenger.plugins import PrimePluginStore

_cache = {}
_plugins_dir = None


def init(plugins_dir_path=None, all_shared_prefs=None):
    global _plugins_dir
    _plugins_dir = plugins_dir_path
    _cache.clear()


def _settings(plugin_id):
    cached = _cache.get(plugin_id)
    if cached is None:
        raw = PrimePluginStore.getSettingsJson(plugin_id)
        try:
            cached = json.loads(raw) if raw else {}
        except Exception:
            # A settings blob we cannot read is a settings blob we throw away. The alternative is a
            # plugin that fails on every launch because of one bad write months ago.
            cached = {}
        if not isinstance(cached, dict):
            cached = {}
        _cache[plugin_id] = cached
    return cached


def _flush(plugin_id):
    try:
        PrimePluginStore.setSettingsJson(plugin_id, json.dumps(_cache.get(plugin_id, {})))
    except Exception:
        pass


def get_setting(plugin_id, key, default=None):
    return _settings(plugin_id).get(key, default)


def set_setting(plugin_id, key, value):
    _settings(plugin_id)[key] = value
    _flush(plugin_id)
    return value


def clear_settings(plugin_id):
    _cache[plugin_id] = {}
    _flush(plugin_id)


def get_all_settings(plugin_id):
    return dict(_settings(plugin_id))


def set_all_settings(plugin_id, settings):
    _cache[plugin_id] = dict(settings or {})
    _flush(plugin_id)


def forget(plugin_id):
    """Drops the cache without touching storage - used when a plugin is unloaded."""
    _cache.pop(plugin_id, None)
