"""The door Java knocks on. Nothing else in the SDK is called from the app directly.

Keeping a single module for this means the Java side names one thing, and everything about how a
plugin is executed - what a module is called, where its metadata comes from, what happens when it
raises - can change without touching Java. It also means every entry point can be wrapped: a plugin
throwing during load must produce a message on a settings row, never an exception crossing back
into the app.
"""

import json
import sys
import traceback
import types

import _prime_pip
import base_plugin
import plugin_settings
from base_plugin import registry
from android_utils import log

# Downloaded packages have to be importable before any plugin runs, and this module is imported
# before any of them does.
_prime_pip.ensure_on_path()

#: Loaded plugin instances by id.
_loaded = {}

#: The rows each plugin last described, so Java can refer to them by index.
_settings_rows = {}

_MODULE_PREFIX = "prime_plugin_"


_REQUEST_METHODS = ("pre_request_hook", "post_request_hook", "on_update_hook", "on_updates_hook")


def _publish_request_hooks():
    """Tells Java whether anybody is listening to requests and updates.

    Both paths are hot - every request the client makes, every update the server sends - so the
    cost for a user with no such plugin has to be one field read and nothing else.
    """
    wanted = False
    for plugin in registry.plugins.values():
        for method in _REQUEST_METHODS:
            if getattr(type(plugin), method) is not getattr(base_plugin.BasePlugin, method):
                wanted = True
                break
        if wanted:
            break
    try:
        from org.telegram.messenger.plugins import PrimePluginHooks
        PrimePluginHooks.setRequestHooks(wanted)
    except Exception:
        pass


def _short_error(exc):
    """The last line of a traceback - what a user can act on, without the machinery above it."""
    lines = traceback.format_exception_only(type(exc), exc)
    return lines[-1].strip() if lines else repr(exc)


def load_plugin(plugin_id, path):
    """Runs a plugin file and starts its plugin. Returns None on success, a message on failure."""
    if plugin_id in _loaded:
        unload_plugin(plugin_id)
    try:
        with open(path, "r", encoding="utf-8") as handle:
            source = handle.read()
    except Exception as e:
        return "не удалось прочитать файл: %s" % _short_error(e)

    module_name = _MODULE_PREFIX + plugin_id
    module = types.ModuleType(module_name)
    module.__file__ = path
    # Our own marker for "which plugin is this". Not ``__name__``: a plugin file assigns that
    # itself - it is where the display name comes from - so by the time anyone asks, the module's
    # name is long gone.
    module.__prime_plugin_id__ = plugin_id
    # Registered before execution because a plugin may import itself indirectly, and because a
    # module missing from sys.modules makes every traceback inside it unreadable.
    sys.modules[module_name] = module

    before = len(registry.last_defined)
    try:
        exec(compile(source, path, "exec"), module.__dict__)
    except ModuleNotFoundError as e:
        # One retry, because a plugin's declared requirements are frequently incomplete: it imports
        # something it never listed, and the import name is usually the package name too. Fetching
        # it is the difference between "works" and a plugin the user has no way to repair.
        missing = e.name or ""
        installed, reason = (False, None)
        if missing:
            log("plugin %s wants %s; trying to fetch it" % (plugin_id, missing))
            installed, reason = _prime_pip.install(missing)
        if installed:
            module.__dict__.clear()
            module.__file__ = path
            module.__prime_plugin_id__ = plugin_id
            del registry.last_defined[before:]
            try:
                exec(compile(source, path, "exec"), module.__dict__)
            except Exception as second:
                sys.modules.pop(module_name, None)
                del registry.last_defined[before:]
                log("plugin %s still failed after fetching %s:\n%s"
                    % (plugin_id, missing, traceback.format_exc()))
                return _short_error(second)
        else:
            sys.modules.pop(module_name, None)
            del registry.last_defined[before:]
            log("plugin %s wants a missing module:\n%s" % (plugin_id, traceback.format_exc()))
            return reason or ("плагину нужна библиотека «%s», которой нет в этой сборке" % (missing or "?"))
    except Exception as e:
        sys.modules.pop(module_name, None)
        del registry.last_defined[before:]
        log("plugin %s failed to execute:\n%s" % (plugin_id, traceback.format_exc()))
        return _short_error(e)

    defined = registry.last_defined[before:]
    del registry.last_defined[before:]
    if not defined:
        sys.modules.pop(module_name, None)
        return "в файле нет класса, унаследованного от BasePlugin"

    # The last subclass defined wins. A file with several is either a plugin with helper base
    # classes above it - in which case the last one is the plugin - or a mistake we cannot resolve.
    plugin_class = defined[-1]
    try:
        plugin = plugin_class()
        plugin.id = plugin_id
        for attribute, key in (
            ("name", "__name__"),
            ("description", "__description__"),
            ("author", "__author__"),
            ("version", "__version__"),
            ("min_version", "__min_version__"),
            ("icon", "__icon__"),
        ):
            value = module.__dict__.get(key)
            if value is not None:
                setattr(plugin, attribute, value)
        plugin.requirements = module.__dict__.get("__requirements__") or []
        plugin.enabled = True
        plugin.error_message = None
        _loaded[plugin_id] = plugin
        registry.plugins[plugin_id] = plugin
        plugin.on_plugin_load()
        plugin.initialized = True
        _publish_request_hooks()
    except Exception as e:
        log("plugin %s failed to load:\n%s" % (plugin_id, traceback.format_exc()))
        unload_plugin(plugin_id)
        return _short_error(e)
    return None


def unload_plugin(plugin_id):
    """Stops a plugin and forgets everything it registered. Never raises."""
    plugin = _loaded.pop(plugin_id, None)
    if plugin is not None:
        try:
            plugin.on_plugin_unload()
        except Exception:
            log("plugin %s failed while unloading:\n%s" % (plugin_id, traceback.format_exc()))
        try:
            # Not the plugin's job to remember. A method left hooked by a plugin that is gone
            # keeps calling into code that no longer exists, and the crash lands far from here.
            plugin.unhook_all()
        except Exception:
            log("plugin %s failed while removing hooks:\n%s" % (plugin_id, traceback.format_exc()))
        plugin.enabled = False
        plugin.initialized = False
    registry.remove_plugin(plugin_id)
    _publish_request_hooks()
    _settings_rows.pop(plugin_id, None)
    plugin_settings.forget(plugin_id)
    sys.modules.pop(_MODULE_PREFIX + plugin_id, None)


def install_requirements(requirements_json):
    """
    Downloads what a plugin declared in ``__requirements__``, before it is first run.

    Takes and returns JSON because it is called from Java. An empty string back means everything
    is there; anything else is a list of reasons to put in front of the user.
    """
    try:
        requirements = json.loads(requirements_json) or []
    except Exception:
        return ""
    problems = _prime_pip.install_all(requirements)
    return "\n".join(problems) if problems else ""


def installed_libraries():
    """Name and version of everything downloaded so far, as JSON, for the libraries screen."""
    try:
        return json.dumps(_prime_pip.installed())
    except Exception:
        return "{}"


def clear_libraries():
    """Throws away every downloaded package. What is still needed comes back on the next load."""
    _prime_pip.clear_all()
    _prime_pip.ensure_on_path()


def is_loaded(plugin_id):
    return plugin_id in _loaded


def loaded_ids():
    return list(_loaded.keys())


# ---------------------------------------------------------------------------
# settings


def _row_to_dict(index, row):
    data = {"index": index, "type": getattr(row, "type", "text")}
    for name in ("key", "text", "subtext", "icon", "hint", "default", "items", "accent", "red",
                 "multiline", "max_length", "link_alias"):
        if hasattr(row, name):
            value = getattr(row, name)
            if isinstance(value, (list, tuple)):
                value = [str(item) for item in value]
            data[name] = value
    data["clickable"] = getattr(row, "on_click", None) is not None
    data["long_clickable"] = getattr(row, "on_long_click", None) is not None
    return data


def build_settings(plugin_id):
    """The plugin's settings as JSON, for the Java screen to draw. ``"[]"`` when it offers none."""
    plugin = _loaded.get(plugin_id)
    if plugin is None:
        return "[]"
    try:
        rows = plugin.create_settings() or []
    except Exception:
        log("plugin %s failed to build settings:\n%s" % (plugin_id, traceback.format_exc()))
        return "[]"
    _settings_rows[plugin_id] = list(rows)
    try:
        return json.dumps([_row_to_dict(i, row) for i, row in enumerate(rows)])
    except Exception:
        log("plugin %s produced settings we cannot describe:\n%s" % (plugin_id, traceback.format_exc()))
        return "[]"


def _row(plugin_id, index):
    rows = _settings_rows.get(plugin_id) or []
    return rows[index] if 0 <= index < len(rows) else None


def on_setting_changed(plugin_id, index, value_json):
    """A row the user moved. The value is already stored; this is only the plugin's chance to react."""
    row = _row(plugin_id, index)
    if row is None:
        return
    handler = getattr(row, "on_change", None)
    if handler is None:
        return
    try:
        handler(json.loads(value_json))
    except Exception:
        log("plugin %s on_change failed:\n%s" % (plugin_id, traceback.format_exc()))


def on_setting_clicked(plugin_id, index):
    row = _row(plugin_id, index)
    if row is None:
        return
    handler = getattr(row, "on_click", None)
    if handler is None:
        return
    try:
        handler(None)
    except Exception:
        log("plugin %s on_click failed:\n%s" % (plugin_id, traceback.format_exc()))


# ---------------------------------------------------------------------------
# dispatch


def dispatch_send_message(account, params):
    """
    Every plugin that asked about outgoing messages, in priority order.

    Returns ``True`` when a plugin cancelled the send. The params object is Java's own, so a plugin
    that edited the text has already edited what will be sent - there is nothing to copy back.
    """
    hooks = registry.send_message_hooks
    if not hooks:
        return False
    import client_utils
    with client_utils.account_scope(account):
        for _, plugin in list(hooks):
            try:
                result = plugin.on_send_message_hook(account, params)
            except Exception:
                log("plugin %s failed on send:\n%s" % (plugin.id, traceback.format_exc()))
                continue
            if result is None:
                continue
            strategy = getattr(result, "strategy", None)
            if strategy == base_plugin.HookStrategy.CANCEL:
                return True
            if strategy == base_plugin.HookStrategy.MODIFY_FINAL:
                # The plugin is saying it has settled the matter; later plugins do not get a turn.
                return False
    return False


def dispatch_app_event(event_name):
    try:
        event = base_plugin.AppEvent(event_name)
    except ValueError:
        return
    for plugin in list(_loaded.values()):
        try:
            plugin.on_app_event(event)
        except Exception:
            log("plugin %s failed on %s:\n%s" % (plugin.id, event_name, traceback.format_exc()))


def dispatch_file_open(path, file_name, message, activity, place):
    """Java asks whether a plugin claimed this file. Never raises - the file must still open."""
    try:
        from file_utils import FilesController
        return bool(FilesController.dispatch(path, file_name, message, activity, place))
    except Exception:
        log("file dispatch failed:\n%s" % traceback.format_exc())
        return False


def dispatch_intent(intent, after):
    """Java asks whether a plugin handled this intent. Never raises."""
    try:
        from intents import IntentsManager
        return bool(IntentsManager.dispatch(intent, bool(after)))
    except Exception:
        log("intent dispatch failed:\n%s" % traceback.format_exc())
        return False


def _request_name(obj):
    """Both shapes of the name a plugin might have registered.

    Telegram's own classes are called ``TL_messages_sendMessage``; plugins are equally likely to
    have written ``messages.sendMessage``, because that is how the method is called in the API
    documentation. Matching on either costs one string operation and saves the author a guess.
    """
    raw = type(obj).__name__
    dotted = raw
    if dotted.startswith("TL_"):
        dotted = dotted[3:]
    dotted = dotted.replace("_", ".", 1)
    return raw, dotted


def _interested(plugin, method_name, names):
    """Whether this plugin should hear about an event with these names.

    A plugin that registered names is asked only about those. One that registered none but
    overrode the method hears everything - otherwise the override would look broken, and the
    plugin author has already said what they want by writing the method.
    """
    if getattr(type(plugin), method_name) is getattr(base_plugin.BasePlugin, method_name):
        return False
    registered = [h for h in registry.request_hooks if h[3] is plugin]
    if not registered:
        return True
    for name, substring, _priority, _plugin in registered:
        for candidate in names:
            if (name in candidate) if substring else (name == candidate):
                return True
    return False


def dispatch_pre_request(account, request):
    """Java asks what to send. Returns the request, a replacement, or None to cancel."""
    if not registry.plugins:
        return request
    names = _request_name(request)
    for plugin in list(registry.plugins.values()):
        if not _interested(plugin, "pre_request_hook", names):
            continue
        try:
            result = plugin.pre_request_hook(names[0], account, request)
        except Exception:
            log("plugin %s failed before a request:\n%s" % (plugin.id, traceback.format_exc()))
            continue
        if result is None:
            continue
        if result.strategy == base_plugin.HookStrategy.CANCEL:
            return None
        if result.request is not None:
            request = result.request
            names = _request_name(request)
        if result.strategy == base_plugin.HookStrategy.MODIFY_FINAL:
            break
    return request


def dispatch_post_request(account, request, response, error):
    """Java asks what to hand back to the caller. Returns the response, possibly replaced."""
    if not registry.plugins:
        return response
    names = _request_name(request)
    for plugin in list(registry.plugins.values()):
        if not _interested(plugin, "post_request_hook", names):
            continue
        try:
            result = plugin.post_request_hook(names[0], account, response, error)
        except Exception:
            log("plugin %s failed after a request:\n%s" % (plugin.id, traceback.format_exc()))
            continue
        if result is None:
            continue
        if result.response is not None:
            response = result.response
        if result.strategy == base_plugin.HookStrategy.MODIFY_FINAL:
            break
    return response


def dispatch_updates(account, updates, container):
    """Java hands over either an updates container or a list of individual updates."""
    if not registry.plugins:
        return
    try:
        if container:
            names = _request_name(updates)
            for plugin in list(registry.plugins.values()):
                if _interested(plugin, "on_updates_hook", names):
                    plugin.on_updates_hook(names[0], account, updates)
            return
        # A list. Walking it here rather than calling into Python once per update keeps the cost
        # of a hundred updates at one crossing instead of a hundred.
        for update in updates:
            names = _request_name(update)
            for plugin in list(registry.plugins.values()):
                if _interested(plugin, "on_update_hook", names):
                    plugin.on_update_hook(names[0], account, update)
    except Exception:
        log("update dispatch failed:\n%s" % traceback.format_exc())
