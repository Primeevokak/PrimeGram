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


# ---------------------------------------------------------------------------
# method hooking
#
# Patching arbitrary app methods needs a hooking runtime, which PrimeGram does not yet carry. The
# classes exist so that a plugin importing them loads and its other features work; the hooking
# calls say so plainly rather than pretending to have worked, because a hook that silently never
# fires is the worst of the three possible outcomes.


class XposedHook(abc.ABC):
    pass


class MethodReplacement(XposedHook, metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def replace_hooked_method(self, param):
        ...


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

    def to_java_filter(self):
        raise NotImplementedError("method hooking is not available in this build")


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
        self.last_defined = []

    def remove_plugin(self, plugin_id):
        self.plugins.pop(plugin_id, None)
        self.request_hooks = [h for h in self.request_hooks if h[3].id != plugin_id]
        self.send_message_hooks = [h for h in self.send_message_hooks if h[1].id != plugin_id]
        self.menu_items = {k: v for k, v in self.menu_items.items() if v[0].id != plugin_id}
        self.publish_send_hooks()

    def publish_send_hooks(self):
        """Tells the app whether the send path has anyone on it, so it can skip us entirely."""
        PrimePluginHooks.setSendMessageHooks(bool(self.send_message_hooks))


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
        pass

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
        raise NotImplementedError("file hooks are not available in this build")

    def remove_file_hook(self, ext, secret):
        pass

    def add_intent_hook(self, info, type):
        raise NotImplementedError("intent hooks are not available in this build")

    def remove_intent_hook(self, handler_id):
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
        self.log("hook_method is not available in this build")
        return None

    def hook_all_methods(self, hook_class, method_name, xposed_hook=None, priority=None, *,
                         before=None, after=None, before_filters=None, after_filters=None):
        self.log("hook_all_methods is not available in this build")
        return None

    def hook_all_constructors(self, hook_class, xposed_hook=None, priority=None, *,
                              before=None, after=None, before_filters=None, after_filters=None):
        self.log("hook_all_constructors is not available in this build")
        return None

    def unhook_method(self, unhook):
        return None

    def log(self, message):
        _log("%s: %s" % (self.id or self.__class__.__name__, message))

    def client(self, account=None):
        import client_utils
        return client_utils.get_client(account)

    def add_menu_item(self, menu_item_data):
        item_id = menu_item_data.item_id or ("%s_%d" % (self.id, len(registry.menu_items)))
        menu_item_data.item_id = item_id
        registry.menu_items[item_id] = (self, menu_item_data)
        return item_id

    def remove_menu_item(self, item_id):
        return registry.menu_items.pop(item_id, None) is not None
