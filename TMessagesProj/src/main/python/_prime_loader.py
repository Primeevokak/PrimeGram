"""The door Java knocks on. Nothing else in the SDK is called from the app directly.

Keeping a single module for this means the Java side names one thing, and everything about how a
plugin is executed - what a module is called, where its metadata comes from, what happens when it
raises - can change without touching Java. It also means every entry point can be wrapped: a plugin
throwing during load must produce a message on a settings row, never an exception crossing back
into the app.
"""

import json
import os
import re
import sys
import traceback
import types

import _prime_java_compat
import _prime_pip
import base_plugin
import plugin_settings
from base_plugin import registry
from android_utils import log

# Downloaded packages have to be importable before any plugin runs, and this module is imported
# before any of them does.
_prime_pip.ensure_on_path()
# Same reasoning, for exteraGram's own Java classes: registered once, before the first plugin gets
# a chance to ask for one.
_prime_java_compat.install()


def _ensure_plugins_dir_on_path():
    """Some plugins (zwylib among them) write a small support package of their own straight into
    the plugins directory at import time - e.g. ``<plugins_dir>/zwylib_companion/__init__.py`` -
    and then ``import`` it, expecting the plugins directory itself to be a normal import root the
    way it is on exteraGram. Without this, that import fails with ModuleNotFoundError even though
    the file exists on disk one line after being written, and the retry-by-fetching-a-library path
    below then tries to ``pip install`` the plugin's own generated package name - which is not a
    published package and 404s on PyPI every time. Adding the directory here means a plugin's own
    generated subpackages resolve as plain imports and never reach that fallback at all.
    """
    try:
        import file_utils
        plugins_dir = file_utils.get_plugins_dir()
    except Exception:
        return
    if plugins_dir and plugins_dir not in sys.path:
        sys.path.insert(0, plugins_dir)


_ensure_plugins_dir_on_path()


def _harden_path_exists():
    """genericpath.exists() only catches (OSError, ValueError) around os.stat(); a caller that
    hands it something that isn't path-shaped at all (a stray Java class placeholder, most often)
    gets a raw TypeError instead of the False the function exists to provide. That escaped once
    already - through inspect.stack(), called from a plugin's own logging, nowhere near any path
    a plugin actually meant to check - and took the whole plugin down with it for an error that
    had nothing to do with a missing file. Wrapping only exists()/isfile()/isdir()/islink(), not
    os.stat() itself: those four are the ones with an existing "can't tell, so say no" contract,
    they're just missing TypeError from the set of reasons "can't tell" happens.
    """
    for name in ("exists", "isfile", "isdir", "islink"):
        original = getattr(os.path, name, None)
        if original is None:
            continue

        def wrap(fn):
            def wrapped(path, *a, **kw):
                try:
                    return fn(path, *a, **kw)
                except TypeError:
                    return False
            return wrapped

        setattr(os.path, name, wrap(original))


_harden_path_exists()

#: Loaded plugin instances by id.
_loaded = {}

#: The rows each plugin last described, keyed by (plugin_id, path) - "" for its own screen, and a
#: slash-joined index trail for a sub-fragment a row opened - so Java can refer to a row by index
#: on whichever screen it is currently looking at.
_settings_rows = {}

_MODULE_PREFIX = "prime_plugin_"


_REQUEST_METHODS = ("pre_request_hook", "post_request_hook", "on_update_hook", "on_updates_hook")


def _publish_request_hooks():
    """Tells Java whether anybody is listening to requests and updates, and how many plugins are
    actually running right now - both only ever change at the same moments load_plugin/unload_plugin
    do, so one publish covers both.

    The request/update path is hot - every request the client makes, every update the server sends -
    so the cost for a user with no such plugin has to be one field read and nothing else.
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
        PrimePluginHooks.setActiveCount(len(_loaded))
    except Exception:
        pass


def _short_error(exc):
    """The last line of a traceback - what a user can act on, without the machinery above it."""
    lines = traceback.format_exception_only(type(exc), exc)
    return lines[-1].strip() if lines else repr(exc)


# plugin_id -> full traceback text for its most recent load failure. The short message returned
# by load_plugin() is what a user can act on without wading through a stack trace; this is what a
# bug report actually needs, kept on the side so the UI can offer both instead of only one.
_load_tracebacks = {}


def get_load_traceback(plugin_id):
    return _load_tracebacks.get(plugin_id, "")

# Roots no real PyPI distribution is published under, because pip's package name and its import
# name are the same thing for every pure-Python package we could install anyway. A plugin that
# fails to import one of these has reached for a Java package that this build does not have -
# most often a class from exteraGram's own app, which is a different codebase under a different
# package name and was never going to be here. Trying to "fetch" it from PyPI used to produce a
# raw 404 with no indication of what actually went wrong.
_JAVA_LOOKING_ROOTS = frozenset((
    "com", "org", "net", "android", "androidx", "java", "javax",
    "kotlin", "kotlinx", "dalvik", "de",
))


def _guess_java_path(source, root):
    """The full dotted path a plugin tried to import, when we can find it in the source.

    Falls back to the bare root: worse than the full path, but still tells the user which family
    of name failed rather than showing them the inside of a stack trace.
    """
    match = re.search(
        r"^\s*(?:from|import)\s+(" + re.escape(root) + r"(?:\.[A-Za-z_][\w]*)+)",
        source, re.M,
    )
    return match.group(1) if match else root


def load_plugin(plugin_id, path):
    """Runs a plugin file and starts its plugin. Returns None on success, a message on failure."""
    if plugin_id in _loaded:
        unload_plugin(plugin_id)
    _load_tracebacks.pop(plugin_id, None)
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
        if missing and missing in _JAVA_LOOKING_ROOTS:
            # Not a PyPI name at all - pip would 404 on it every time, for a reason that has
            # nothing to do with packages. Most often this is a plugin written against
            # exteraGram's own app, reaching for one of its Java classes directly; ours is a
            # different codebase under a different package name and never had it.
            full_path = _guess_java_path(source, missing)
            log("plugin %s wants Java class %s, which this build does not have"
                % (plugin_id, full_path))
            reason = "плагину нужен класс Java «%s», которого нет в этой сборке" % full_path
        elif missing:
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
                _load_tracebacks[plugin_id] = traceback.format_exc()
                log("plugin %s still failed after fetching %s:\n%s"
                    % (plugin_id, missing, traceback.format_exc()))
                return _short_error(second)
        else:
            sys.modules.pop(module_name, None)
            del registry.last_defined[before:]
            _load_tracebacks[plugin_id] = traceback.format_exc()
            log("plugin %s wants a missing module:\n%s" % (plugin_id, traceback.format_exc()))
            return reason or ("плагину нужна библиотека «%s», которой нет в этой сборке" % (missing or "?"))
    except Exception as e:
        sys.modules.pop(module_name, None)
        del registry.last_defined[before:]
        _load_tracebacks[plugin_id] = traceback.format_exc()
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
        _publish_dependencies(plugin_id, module)
    except Exception as e:
        _load_tracebacks[plugin_id] = traceback.format_exc()
        log("plugin %s failed to load:\n%s" % (plugin_id, traceback.format_exc()))
        unload_plugin(plugin_id)
        return _short_error(e)
    return None


def _publish_dependencies(plugin_id, module):
    """Which other loaded plugins ended up in {@code module}'s own namespace - the closest thing to
    "this plugin is a library for that one" this SDK has, since a plugin never declares another
    plugin as a dependency; it just imports it, the same as any other module. Handed to Java so a
    crash can compute the chain to disable without needing Python at all, which matters because the
    crash that triggers it may be happening on a thread with no safe way back into Python.
    """
    other_ids = set()
    for value in vars(module).values():
        name = value.__name__ if isinstance(value, types.ModuleType) else getattr(value, "__module__", None)
        if isinstance(name, str) and name.startswith(_MODULE_PREFIX):
            other_id = name[len(_MODULE_PREFIX):]
            if other_id and other_id != plugin_id and other_id in _loaded:
                other_ids.add(other_id)
    try:
        from org.telegram.messenger.plugins import PrimePluginStore
        PrimePluginStore.setDependencies(plugin_id, list(other_ids))
    except Exception:
        pass


def disable_crashed_plugin(plugin_id, reason):
    """A plugin's own code broke out past every guard meant to keep it from doing that - a
    class-proxy override that raised, most likely. Disables it (and, as a precaution, whatever it is
    joined to by an import) rather than every plugin in the catalogue, and names it specifically:
    exteraGram's Safe Mode goes the blunt route because it has no per-plugin story to tell here; this
    SDK does.
    """
    try:
        from org.telegram.messenger.plugins import PrimePluginsController
        PrimePluginsController.getInstance().disableAfterCrash(plugin_id, reason)
    except Exception:
        log("could not disable crashed plugin %s:\n%s" % (plugin_id, traceback.format_exc()))


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
    for key in [k for k in _settings_rows if k[0] == plugin_id]:
        _settings_rows.pop(key, None)
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
    data["has_sub_fragment"] = getattr(row, "create_sub_fragment", None) is not None
    return data


def _store_rows(plugin_id, path, rows):
    _settings_rows[(plugin_id, path)] = list(rows)


def build_settings(plugin_id):
    """The plugin's own settings screen, as JSON. ``"[]"`` when it offers none."""
    plugin = _loaded.get(plugin_id)
    if plugin is None:
        return "[]"
    try:
        rows = plugin.create_settings() or []
    except Exception:
        log("plugin %s failed to build settings:\n%s" % (plugin_id, traceback.format_exc()))
        return "[]"
    _store_rows(plugin_id, "", rows)
    try:
        return json.dumps([_row_to_dict(i, row) for i, row in enumerate(rows)])
    except Exception:
        log("plugin %s produced settings we cannot describe:\n%s" % (plugin_id, traceback.format_exc()))
        return "[]"


def build_sub_settings(plugin_id, parent_path, index):
    """The screen a ``create_sub_fragment`` row opens, as JSON ``{"title", "rows"}``.

    ``parent_path`` is where the row itself lives - ``""`` for the plugin's own screen, or wherever
    an earlier call here landed - so a row two screens deep is found the same way its parent was:
    by walking down from the plugin's own screen one index at a time, never by index alone, because
    the same index means a different row on every screen.
    """
    empty = json.dumps({"title": "", "rows": []})
    row = _row(plugin_id, parent_path, index)
    if row is None:
        return empty
    opener = getattr(row, "create_sub_fragment", None)
    if opener is None:
        return empty
    try:
        rows = opener() or []
    except Exception:
        log("plugin %s failed to build a sub-screen:\n%s" % (plugin_id, traceback.format_exc()))
        return empty
    child_path = "%s/%s" % (parent_path, index) if parent_path else str(index)
    _store_rows(plugin_id, child_path, rows)
    try:
        return json.dumps({
            "title": getattr(row, "text", "") or "",
            "rows": [_row_to_dict(i, r) for i, r in enumerate(rows)],
        })
    except Exception:
        log("plugin %s produced a sub-screen we cannot describe:\n%s" % (plugin_id, traceback.format_exc()))
        return empty


def build_custom_view(plugin_id, path, index):
    """The live View for a ``Custom`` row - can't travel through JSON like the rest of a row, so
    it takes a separate trip, made only once the row is actually about to be shown.

    ``view`` is used directly if the plugin already built one. Otherwise, if ``factory`` is set,
    it is called to build one now - ``factory(context, factory_args)``, or ``factory.create(context,
    factory_args)`` for an object rather than a bare function - which is our own answer to
    exteraGram's ``Factory``: theirs is a Java class the app instantiates, which would need a class
    generated from Python at runtime (the same machinery ``ClassBuilder`` needs, and which this
    build does not have); a Python callable does the same job - build a view lazily, parametrised by
    ``factory_args`` - without needing a Java class to exist at all. A plugin ported from
    exteraGram's exact ``CustomSetting.Factory`` subclass still will not work unmodified, because
    that subclass itself cannot be created here; a plugin written against this callable form does.
    """
    row = _row(plugin_id, path, index)
    if row is None or getattr(row, "type", None) != "custom":
        return None
    view = getattr(row, "view", None)
    if view is not None:
        return view
    factory = getattr(row, "factory", None)
    if factory is None:
        return None
    from org.telegram.messenger import ApplicationLoader
    context = ApplicationLoader.applicationContext
    build = getattr(factory, "create", factory)
    try:
        return build(context, getattr(row, "factory_args", None))
    except Exception:
        log("plugin %s: фабрика настройки #%s упала при создании view:\n%s" %
            (plugin_id, index, traceback.format_exc()))
        return None


def _row(plugin_id, path, index):
    rows = _settings_rows.get((plugin_id, path)) or []
    return rows[index] if 0 <= index < len(rows) else None


def on_setting_changed(plugin_id, path, index, value_json):
    """A row the user moved. The value is already stored; this is only the plugin's chance to react."""
    row = _row(plugin_id, path, index)
    if row is None:
        return
    handler = getattr(row, "on_change", None)
    if handler is None:
        return
    try:
        handler(json.loads(value_json))
    except Exception:
        log("plugin %s on_change failed:\n%s" % (plugin_id, traceback.format_exc()))


def on_setting_clicked(plugin_id, path, index):
    row = _row(plugin_id, path, index)
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


class _watchdog:
    """Brackets one plugin's turn on the dispatch queue, so a Java-side timer checking in from the
    UI thread can tell whether the queue is stuck and, if so, on whom - the only way to notice a
    hang from outside it: everything here runs on one thread, and a plugin that never returns has
    no opportunity to say so itself.

    Silent about its own failure - a watchdog that could crash the thing it is watching would be
    worse than none.
    """

    __slots__ = ("plugin_id",)

    def __init__(self, plugin_id):
        self.plugin_id = plugin_id

    def __enter__(self):
        try:
            from org.telegram.messenger.plugins import PrimePluginWatchdog
            PrimePluginWatchdog.beginDispatch(self.plugin_id)
        except Exception:
            pass
        return self

    def __exit__(self, *exc_info):
        try:
            from org.telegram.messenger.plugins import PrimePluginWatchdog
            PrimePluginWatchdog.endDispatch()
        except Exception:
            pass
        return False


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
                with _watchdog(plugin.id):
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
            with _watchdog(plugin.id):
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


def dispatch_menu_click(item_id, context):
    """A menu item the user picked. ``context`` is the same java.util.Map the app used to decide
    whether the item should show at all - readable from Python like any dict, since Chaquopy wraps
    a Java Map that way. Never raises: whichever screen showed the menu has already dismissed it by
    the time this runs, so there is nothing left here for a plugin's mistake to break."""
    entry = base_plugin.registry.menu_items.get(item_id)
    if entry is None:
        return
    plugin, data = entry
    if not plugin.enabled or data.on_click is None:
        return
    try:
        with _watchdog(plugin.id):
            data.on_click(context)
    except Exception:
        log("plugin %s menu item %s failed:\n%s" % (plugin.id, item_id, traceback.format_exc()))


def dispatch_pill_click(pill_id):
    """A pill in the stack above the chat list was tapped. Never raises, same reasoning as
    ``dispatch_menu_click``."""
    entry = base_plugin.registry.pills.get(pill_id)
    if entry is None:
        return
    plugin, data = entry
    if not plugin.enabled or data.on_click is None:
        return
    try:
        with _watchdog(plugin.id):
            data.on_click()
    except Exception:
        log("plugin %s pill %s failed:\n%s" % (plugin.id, pill_id, traceback.format_exc()))


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
            with _watchdog(plugin.id):
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
            with _watchdog(plugin.id):
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
                    with _watchdog(plugin.id):
                        plugin.on_updates_hook(names[0], account, updates)
            return
        # A list. Walking it here rather than calling into Python once per update keeps the cost
        # of a hundred updates at one crossing instead of a hundred.
        for update in updates:
            names = _request_name(update)
            for plugin in list(registry.plugins.values()):
                if _interested(plugin, "on_update_hook", names):
                    with _watchdog(plugin.id):
                        plugin.on_update_hook(names[0], account, update)
    except Exception:
        log("update dispatch failed:\n%s" % traceback.format_exc())
