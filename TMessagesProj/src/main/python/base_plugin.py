"""The class every plugin inherits from, and the registries the app dispatches through.

A plugin is a subclass of :class:`BasePlugin`. Subclassing is itself the registration - there is no
call to make, because a plugin file that defines a class and forgets to register it would be a
plugin that silently does nothing, and that failure is impossible to diagnose from the outside.

Hooks are held here rather than on the plugin object so that dispatch never has to walk the list of
loaded plugins asking each one whether it cares. The app asks one question - "who wants this?" - and
gets an already-sorted answer. Unloading a plugin removes its entries, so a plugin that is off costs
nothing at all on the paths it used to be on.
"""

import abc
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Callable, List, Optional

import plugin_settings
from android_utils import log as _log
from org.telegram.messenger.plugins import PrimePluginHooks

# ---------------------------------------------------------------------------
# results and errors


class HookStrategy(Enum):
    """What the app should do with a hooked call once the plugin has looked at it."""

    CANCEL = "cancel"
    MODIFY = "modify"
    DEFAULT = "default"
    MODIFY_FINAL = "modify_final"


class IntentHookType(Enum):
    BEFORE = 0
    AFTER = 1


@dataclass
class HookResult:
    strategy: HookStrategy = HookStrategy.DEFAULT
    request: Any = None
    response: Any = None
    update: Any = None
    updates: Any = None
    error: Any = None
    params: Any = None


class PluginError(Exception):
    def __init__(self, message, plugin_id=None):
        super().__init__(message)
        self.plugin_id = plugin_id


@dataclass
class PluginMetadata:
    id: str
    name: str
    description: str
    author: str
    version: str
    icon: str
    min_version: str
    requirements: List[str]


class AppEvent(Enum):
    START = "start"
    STOP = "stop"
    PAUSE = "pause"
    RESUME = "resume"


class MenuItemType(Enum):
    MESSAGE_CONTEXT_MENU = "message_context_menu"
    # Only rendered when the user has PrimeGram's classic side-menu drawer turned on
    # (android_utils.is_navigation_drawer() to check) - with the default modern interface there
    # is no drawer for this to appear in, and the item is simply never shown, not an error.
    DRAWER_MENU = "drawer_menu"
    MAIN_MENU = "main_menu"
    CHAT_ACTION_MENU = "chat_action_menu"
    PROFILE_ACTION_MENU = "profile_action_menu"


@dataclass
class MenuItemData:
    menu_type: MenuItemType
    text: str
    on_click: Callable[[dict], None]
    item_id: Optional[str] = None
    icon: Optional[str] = None
    subtext: Optional[str] = None
    condition: Optional[str] = None
    priority: int = 0


@dataclass
class PillData:
    """One chip in the pill stack above the chat list. ``icon`` is a drawable resource name, the
    same lookup ``MenuItemData.icon`` uses - there is no default icon, a text-only pill is fine.
    ``color`` is an ARGB hex string (``"#ff2196f3"``) for the chip's background, or ``None`` for
    the theme's own neutral chip color."""
    text: str
    on_click: Optional[Callable[[], None]] = None
    pill_id: Optional[str] = None
    icon: Optional[str] = None
    color: Optional[str] = None
    priority: int = 0


# ---------------------------------------------------------------------------
# method hooking
#
# Real hooking, on LSPlant through PrimePluginXposed: the app rewrites entry points of its own
# methods, in its own process, with no root and nothing installed. Plugins written for exteraGram
# use this without knowing anything is different.
#
# Where LSPlant cannot start - an ART version it has not been taught - hooking reports itself
# unavailable and says so in the log. That is deliberate: a hook that silently never fires is the
# worst of the possible outcomes, because the plugin looks installed and does nothing.


def _make_callback(plugin, xposed_hook, before, after, before_filters, after_filters):
    """Turns everything a plugin might have passed into one Java-facing callback.

    exteraGram accepts three shapes for the same thing - a hook object, plain before/after
    callables, or a MethodReplacement - and plugins in the wild use all three. They are
    normalised here so the Java side sees exactly one interface.
    """
    from java import dynamic_proxy
    from org.telegram.messenger.plugins import PrimePluginXposed

    if not PrimePluginXposed.isAvailable():
        plugin.log("подмена методов недоступна на этом устройстве")
        return None

    replacement = None
    before_fn = before
    after_fn = after

    if xposed_hook is not None:
        if isinstance(xposed_hook, MethodReplacement):
            replacement = xposed_hook.replace_hooked_method
        else:
            if before_fn is None and hasattr(xposed_hook, "before_hooked_method"):
                before_fn = xposed_hook.before_hooked_method
            if after_fn is None and hasattr(xposed_hook, "after_hooked_method"):
                after_fn = xposed_hook.after_hooked_method
            if before_filters is None:
                before_filters = getattr(xposed_hook, "before_filters", None)
            if after_filters is None:
                after_filters = getattr(xposed_hook, "after_filters", None)

    # @hook_filters(...) on before_hooked_method/after_hooked_method stashes the filter list on
    # the function itself; bound-method attribute access forwards to it, so this reads the same
    # place the decorator wrote to, whether the method came from a hook class or was passed bare.
    if before_filters is None and before_fn is not None:
        before_filters = getattr(before_fn, "__hook_filters__", None)
    if after_filters is None and after_fn is not None:
        after_filters = getattr(after_fn, "__hook_filters__", None)

    def passes(filters, param, is_before):
        if not filters:
            return True
        for item in filters:
            data = item.filter_data if isinstance(item, HookFilter) else item
            if not data.matches(param, is_before):
                return False
        return True

    class _Callback(dynamic_proxy(PrimePluginXposed.Callback)):

        def before(self, param):
            try:
                if replacement is not None:
                    # A replacement runs instead of the method: its return value becomes the
                    # result, and setting a result is what stops the original from running.
                    param.setResult(replacement(param))
                    return
                if before_fn is not None and passes(before_filters, param, True):
                    before_fn(param)
            except Exception as error:
                plugin.log("ошибка в before-хуке: %r" % (error,))

        def after(self, param):
            try:
                if replacement is None and after_fn is not None and passes(after_filters, param, False):
                    after_fn(param)
            except Exception as error:
                plugin.log("ошибка в after-хуке: %r" % (error,))

    return _Callback()


class XposedHook(abc.ABC):
    pass


class MethodReplacement(XposedHook, metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def replace_hooked_method(self, param):
        ...


def invoke_original(param):
    """The un-hooked implementation, called directly - the only way to reach it once a
    ``MethodReplacement`` (or a ``before`` hook that called ``param.setResult(...)``) has taken
    over, since the real method no longer runs on its own from here on.

    Takes the same ``param`` every hook already receives - ``param.method``, ``param.thisObject``
    and ``param.args`` are exactly what the original call needs, so there is nothing else for a
    plugin to supply. This existed on the Java side (``PrimePluginXposed.invokeOriginal``, wrapping
    Xposed's own ``invokeOriginalMethod``) since hooking was first built, with no Python-facing way
    to reach it - a ``MethodReplacement`` that wanted to wrap rather than fully replace the
    original had no path to do that at all.
    """
    from org.telegram.messenger.plugins import PrimePluginXposed
    return PrimePluginXposed.invokeOriginal(param.method, param.thisObject, param.args)


class MethodHook(XposedHook):
    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


class BaseHook(MethodHook):
    def __init__(self, plugin=None, *, before=None, after=None,
                 before_filters=None, after_filters=None):
        self.plugin = plugin
        self.before = before
        self.after = after
        self.before_filters = before_filters
        self.after_filters = after_filters

    def before_hooked_method(self, param):
        if self.before is not None:
            self.before(param)

    def after_hooked_method(self, param):
        if self.after is not None:
            self.after(param)


def fn_hook_filters(field_name):
    def decorator(fn):
        return fn

    return decorator


@dataclass
class HookFilterData:
    filter_type: str
    arg_index: Optional[int] = None
    or_filters: Any = None
    mvel_expression: Optional[str] = None
    instance_of: Any = None
    object: Any = None

    def matches(self, param, is_before=False):
        """Whether this filter lets the hook run for this call.

        Evaluated here rather than handed to a Java filter object, as exteraGram does: their
        filters live in their own class, ours would have to be a copy of it, and the whole thing
        amounts to a handful of comparisons that Python does perfectly well. ``condition`` is the
        one exception - it is MVEL, and rather than write a second interpreter we hand the
        expression to the same engine exteraGram itself uses (org.mvel:mvel2), through
        PrimePluginXposed.evalCondition, so a condition string a plugin author wrote for
        exteraGram evaluates identically here.
        """
        kind = self.filter_type
        try:
            if kind == "result_is_null":
                return param.getResult() is None
            if kind == "result_not_null":
                return param.getResult() is not None
            if kind == "result_is_true":
                return param.getResult() is True
            if kind == "result_is_false":
                return param.getResult() is False
            if kind == "result_equal":
                return param.getResult() == self.object
            if kind == "result_not_equal":
                return param.getResult() != self.object
            if kind == "result_instance_of":
                return self.instance_of.isInstance(param.getResult())

            if kind.startswith("argument"):
                args = param.args
                if self.arg_index is None or self.arg_index >= len(args):
                    return False
                value = args[self.arg_index]
                if kind == "argument_is_null":
                    return value is None
                if kind == "argument_not_null":
                    return value is not None
                if kind == "argument_is_true":
                    return value is True
                if kind == "argument_is_false":
                    return value is False
                if kind == "argument_equal":
                    return value == self.object
                if kind == "argument_not_equal":
                    return value != self.object
                if kind == "argument_instance_of":
                    return self.instance_of.isInstance(value)

            if kind == "or":
                return any(f.matches(param) for f in (self.or_filters or []))

            if kind == "condition":
                from org.telegram.messenger.plugins import PrimePluginXposed
                return bool(PrimePluginXposed.evalCondition(
                    self.mvel_expression, param, is_before, self.object))
        except Exception:
            return False
        return False

    def to_java_filter(self):
        # Kept for plugins that call it directly; ours are evaluated in Python.
        return self


class HookFilter(Enum):
    RESULT_IS_NULL = "result_is_null"
    RESULT_IS_TRUE = "result_is_true"
    RESULT_IS_FALSE = "result_is_false"
    RESULT_NOT_NULL = "result_not_null"

    @property
    def filter_data(self):
        return HookFilterData(self.value)

    @staticmethod
    def ResultIsInstanceOf(clazz):
        return HookFilterData("result_instance_of", instance_of=clazz)

    @staticmethod
    def ResultEqual(value):
        return HookFilterData("result_equal", object=value)

    @staticmethod
    def ResultNotEqual(value):
        return HookFilterData("result_not_equal", object=value)

    @staticmethod
    def ArgumentIsNull(index):
        return HookFilterData("argument_is_null", arg_index=index)

    @staticmethod
    def ArgumentIsTrue(index):
        return HookFilterData("argument_is_true", arg_index=index)

    @staticmethod
    def ArgumentIsFalse(index):
        return HookFilterData("argument_is_false", arg_index=index)

    @staticmethod
    def ArgumentNotNull(index):
        return HookFilterData("argument_not_null", arg_index=index)

    @staticmethod
    def ArgumentIsInstanceOf(index, clazz):
        return HookFilterData("argument_instance_of", arg_index=index, instance_of=clazz)

    @staticmethod
    def ArgumentEqual(index, value):
        return HookFilterData("argument_equal", arg_index=index, object=value)

    @staticmethod
    def ArgumentNotEqual(index, value):
        return HookFilterData("argument_not_equal", arg_index=index, object=value)

    @staticmethod
    def Condition(condition, object=None):
        return HookFilterData("condition", mvel_expression=condition, object=object)

    @staticmethod
    def Or(*filters):
        return HookFilterData("or", or_filters=list(filters))


def hook_filters(*filters):
    def decorator(fn):
        fn.__hook_filters__ = list(filters)
        return fn

    return decorator


# ---------------------------------------------------------------------------
# registries


class _Registry:
    """Who wants what, kept sorted so dispatch is a walk rather than a search."""

    def __init__(self):
        self.plugins = {}
        self.request_hooks = []      # (name, match_substring, priority, plugin)
        self.send_message_hooks = [] # (priority, plugin)
        self.menu_items = {}         # item_id -> (plugin, MenuItemData)
        self.pills = {}              # pill_id -> (plugin, PillData)
        self.last_defined = []

    def remove_plugin(self, plugin_id):
        self.plugins.pop(plugin_id, None)
        self.request_hooks = [h for h in self.request_hooks if h[3].id != plugin_id]
        self.send_message_hooks = [h for h in self.send_message_hooks if h[1].id != plugin_id]
        self.menu_items = {k: v for k, v in self.menu_items.items() if v[0].id != plugin_id}
        self.pills = {k: v for k, v in self.pills.items() if v[0].id != plugin_id}
        self.publish_send_hooks()
        self.publish_menu_items()
        self.publish_pills()

    def publish_send_hooks(self):
        """Tells the app whether the send path has anyone on it, so it can skip us entirely."""
        PrimePluginHooks.setSendMessageHooks(bool(self.send_message_hooks))

    def publish_menu_items(self):
        """Pushes the whole set of menu items to Java - a plain field there, read fresh every time
        one of the app's own menus is about to be shown, because nothing here is on a hot path the
        way a hook is: a menu opens once per tap, not once per frame.
        """
        import json as _json
        rows = []
        for item_id, (plugin, data) in self.menu_items.items():
            rows.append({
                "id": item_id,
                "plugin_id": plugin.id,
                "menu_type": data.menu_type.value,
                "text": data.text,
                "subtext": data.subtext,
                "icon": data.icon,
                "condition": data.condition,
                "priority": data.priority,
            })
        PrimePluginHooks.setMenuItems(_json.dumps(rows))

    def publish_pills(self):
        """Same idea as menu items, for the pill stack above the chat list."""
        import json as _json
        rows = []
        for pill_id, (plugin, data) in self.pills.items():
            rows.append({
                "id": pill_id,
                "plugin_id": plugin.id,
                "text": data.text,
                "icon": data.icon,
                "color": data.color,
                "priority": data.priority,
            })
        PrimePluginHooks.setPills(_json.dumps(rows))


registry = _Registry()


def _sorted(entries, key_index):
    # Higher priority first: a plugin that asked to go first means before the others, not after.
    entries.sort(key=lambda item: -item[key_index])


# ---------------------------------------------------------------------------


class BasePlugin:
    """One plugin. Exactly one instance per class, created by the loader."""

    id = ""
    name = ""
    description = ""
    author = ""
    min_version = ""
    version = ""
    requirements = []
    icon = None
    error_message = None
    enabled = False
    initialized = False

    def __new__(cls):
        # A plugin is a singleton: its hooks and its settings are identified by the plugin, and a
        # second instance would register a second set of both under the same id.
        existing = cls.__dict__.get("_prime_instance")
        if existing is None:
            existing = super().__new__(cls)
            cls._prime_instance = existing
        return existing

    def __init_subclass__(cls, **kwargs):
        super().__init_subclass__(**kwargs)
        registry.last_defined.append(cls)

    def __init__(self):
        # (unhook, callback) for every hook this plugin placed. The callback half is here for a
        # reason: once a hook is in place the only reference to its proxy is from native code,
        # which the garbage collector cannot see, and a collected proxy takes the hook with it.
        self._prime_hooks = []
        self._prime_files = []
        self._prime_intents = []

    # --- lifecycle, overridden by plugins -------------------------------------------------

    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def create_settings(self):
        return []

    def on_app_event(self, event_type):
        pass

    def pre_request_hook(self, request_name, account, request):
        return HookResult()

    def post_request_hook(self, request_name, account, response, error):
        return HookResult()

    def on_update_hook(self, update_name, account, update):
        return HookResult()

    def on_updates_hook(self, container_name, account, updates):
        return HookResult()

    def on_send_message_hook(self, account, params):
        return HookResult()

    # --- final, provided by the SDK -------------------------------------------------------

    def add_hook(self, name, match_substring=False, priority=0):
        registry.request_hooks.append((name, bool(match_substring), int(priority), self))
        _sorted(registry.request_hooks, 2)
        return name

    def add_on_send_message_hook(self, priority=0):
        registry.send_message_hooks.append((int(priority), self))
        _sorted(registry.send_message_hooks, 0)
        registry.publish_send_hooks()

    def remove_hook(self, name):
        registry.request_hooks = [
            h for h in registry.request_hooks if not (h[0] == name and h[3] is self)
        ]

    def add_file_hook(self, file_info):
        """Claims a file extension: tapping such a file anywhere in the app runs the plugin."""
        from file_utils import FilesController
        secret = FilesController.register(file_info)
        self._prime_files.append(file_info)
        return secret

    def remove_file_hook(self, ext, secret):
        from file_utils import FilesController
        FilesController.unregister(ext, secret)
        self._prime_files = [
            info for info in self._prime_files
            if (info.ext or "").lower().lstrip(".") != (ext or "").lower().lstrip(".")
        ]

    def add_intent_hook(self, info, type):
        """Claims incoming intents matching ``info``, before or after the app's own handling."""
        from intents import IntentsManager
        if type == IntentHookType.AFTER:
            handle = IntentsManager.new_global_after_handler(info)
        else:
            handle = IntentsManager.new_global_before_handler(info)
        self._prime_intents.append(info)
        return handle

    def remove_intent_hook(self, handler_id):
        from intents import IntentsManager
        try:
            IntentsManager.unhandle(handler_id)
        except Exception:
            pass

    def get_setting(self, key, default=None):
        return plugin_settings.get_setting(self.id, key, default)

    def set_setting(self, key, value, reload_settings=False):
        plugin_settings.set_setting(self.id, key, value)
        if reload_settings:
            from org.telegram.messenger.plugins import PrimePluginsController
            PrimePluginsController.getInstance()
        return value

    def export_settings(self):
        return plugin_settings.get_all_settings(self.id)

    def import_settings(self, settings, reload_settings=True):
        plugin_settings.set_all_settings(self.id, settings)

    def hook_method(self, method_or_constructor, xposed_hook=None, priority=None, *,
                    before=None, after=None, before_filters=None, after_filters=None):
        from org.telegram.messenger.plugins import PrimePluginXposed
        callback = _make_callback(self, xposed_hook, before, after,
                                  before_filters, after_filters)
        if callback is None:
            return None
        unhook = PrimePluginXposed.hookMethod(method_or_constructor,
                                              10 if priority is None else int(priority), callback)
        if unhook is None:
            self.log("не удалось повесить хук на %s" % method_or_constructor)
            return None
        # Kept alive by the plugin: the proxy is referenced only from native code once the hook is
        # in place, and a garbage-collected callback takes the hook down with it.
        self._prime_hooks.append((unhook, callback))
        return unhook

    def hook_all_methods(self, hook_class, method_name, xposed_hook=None, priority=None, *,
                         before=None, after=None, before_filters=None, after_filters=None):
        from org.telegram.messenger.plugins import PrimePluginXposed
        callback = _make_callback(self, xposed_hook, before, after,
                                  before_filters, after_filters)
        if callback is None:
            return None
        unhooks = PrimePluginXposed.hookAllMethods(
            hook_class, method_name, 10 if priority is None else int(priority), callback)
        result = list(unhooks or [])
        for unhook in result:
            self._prime_hooks.append((unhook, callback))
        if not result:
            self.log("не нашлось методов %s.%s" % (hook_class, method_name))
        return result

    def hook_all_constructors(self, hook_class, xposed_hook=None, priority=None, *,
                              before=None, after=None, before_filters=None, after_filters=None):
        from org.telegram.messenger.plugins import PrimePluginXposed
        callback = _make_callback(self, xposed_hook, before, after,
                                  before_filters, after_filters)
        if callback is None:
            return None
        unhooks = PrimePluginXposed.hookAllConstructors(
            hook_class, 10 if priority is None else int(priority), callback)
        result = list(unhooks or [])
        for unhook in result:
            self._prime_hooks.append((unhook, callback))
        return result

    def unhook_method(self, unhook):
        from org.telegram.messenger.plugins import PrimePluginXposed
        if unhook is None:
            return None
        PrimePluginXposed.unhook(unhook)
        self._prime_hooks = [pair for pair in self._prime_hooks if pair[0] is not unhook]
        return None

    def unhook_all(self):
        """Takes down everything this plugin claimed. Called for you when it is switched off."""
        from org.telegram.messenger.plugins import PrimePluginXposed
        for unhook, _ in self._prime_hooks:
            PrimePluginXposed.unhook(unhook)
        self._prime_hooks = []
        if self._prime_files:
            from file_utils import FilesController
            FilesController.forget_all(self._prime_files)
            self._prime_files = []
        if self._prime_intents:
            from intents import IntentsManager
            IntentsManager.forget_all(self._prime_intents)
            self._prime_intents = []

    @property
    def hooking_available(self):
        """False on devices whose ART version the hooking library does not know."""
        from org.telegram.messenger.plugins import PrimePluginXposed
        return bool(PrimePluginXposed.isAvailable())

    def log(self, message):
        _log("%s: %s" % (self.id or self.__class__.__name__, message))

    def client(self, account=None):
        import client_utils
        return client_utils.get_client(account)

    def add_menu_item(self, menu_item_data):
        item_id = menu_item_data.item_id or ("%s_%d" % (self.id, len(registry.menu_items)))
        menu_item_data.item_id = item_id
        registry.menu_items[item_id] = (self, menu_item_data)
        registry.publish_menu_items()
        return item_id

    def remove_menu_item(self, item_id):
        removed = registry.menu_items.pop(item_id, None) is not None
        if removed:
            registry.publish_menu_items()
        return removed

    def add_pill(self, pill_data):
        pill_id = pill_data.pill_id or ("%s_%d" % (self.id, len(registry.pills)))
        pill_data.pill_id = pill_id
        registry.pills[pill_id] = (self, pill_data)
        registry.publish_pills()
        return pill_id

    def update_pill(self, pill_id, text=None, icon=None, color=None):
        """Changes an existing pill in place - for a value that ticks (a counter, a price, a
        timer) without the flicker of removing and re-adding the chip on every update."""
        entry = registry.pills.get(pill_id)
        if entry is None:
            return False
        plugin, data = entry
        if text is not None:
            data.text = text
        if icon is not None:
            data.icon = icon
        if color is not None:
            data.color = color
        registry.publish_pills()
        return True

    def remove_pill(self, pill_id):
        removed = registry.pills.pop(pill_id, None) is not None
        if removed:
            registry.publish_pills()
        return removed
