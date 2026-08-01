"""Intercepting links and intents the app receives.

The app's own intent handling does not consult plugins yet, so registering a handler says so. The
one part that works is :meth:`IntentsManager.parse`, which is pure string work and is what plugins
use to pull arguments out of a ``tg://`` link they were given some other way.
"""

from dataclasses import dataclass, field
from typing import Callable, List, Optional
from urllib.parse import parse_qsl, urlparse


class IntentsManager:

    @dataclass
    class HandlerInfo:
        callback: Callable
        scheme: Optional[str] = field(default=None)
        host: Optional[str] = field(default=None)
        path: Optional[str] = field(default=None)
        required_path_args_names: Optional[List[str]] = field(default=None)
        priority: Optional[int] = field(default=0)
        action: Optional[str] = field(default=None)
        whitelist_flags: Optional[List[int]] = field(default=None)
        blacklist_flags: Optional[List[int]] = field(default=None)
        type: Optional[str] = field(default=None)
        categories: Optional[List[str]] = field(default=None)

    @dataclass
    class HandlerHandle:
        handler_id: str

        def unhandle(self):
            IntentsManager.unhandle(self.handler_id)

    class HandlerNotRegistered(Exception):
        def __init__(self, handler_id):
            super().__init__("handler not registered: %s" % handler_id)

    @staticmethod
    def parse(url):
        """Scheme, host, path and query of a link, flattened into one dict of strings."""
        parsed = urlparse(url)
        result = {
            "scheme": parsed.scheme,
            "host": parsed.netloc,
            "path": parsed.path,
        }
        result.update({key: value for key, value in parse_qsl(parsed.query)})
        return result

    # handler_id -> (hook_type, priority, HandlerInfo). Sorted on dispatch rather than on insert:
    # handlers are registered a handful of times and consulted rarely, so the simple thing wins.
    _handlers = {}
    _counter = 0

    @classmethod
    def new_global_before_handler(cls, info):
        return cls._register(info, "before")

    @classmethod
    def new_global_after_handler(cls, info):
        return cls._register(info, "after")

    @classmethod
    def _register(cls, info, hook_type):
        cls._counter += 1
        handler_id = "%s_%d" % (hook_type, cls._counter)
        cls._handlers[handler_id] = (hook_type, info.priority or 0, info)
        cls._publish()
        return IntentsManager.HandlerHandle(handler_id)

    @classmethod
    def unhandle(cls, handler_id):
        if isinstance(handler_id, IntentsManager.HandlerHandle):
            handler_id = handler_id.handler_id
        if handler_id not in cls._handlers:
            raise cls.HandlerNotRegistered(handler_id)
        cls._handlers.pop(handler_id, None)
        cls._publish()

    @classmethod
    def forget_all(cls, infos):
        """Drops these registrations. Used when the plugin that made them unloads."""
        for handler_id, (hook_type, priority, info) in list(cls._handlers.items()):
            if info in infos:
                cls._handlers.pop(handler_id, None)
        cls._publish()

    @classmethod
    def _publish(cls):
        try:
            from org.telegram.messenger.plugins import PrimePluginHooks
            PrimePluginHooks.setIntentHooks(bool(cls._handlers))
        except Exception:
            pass

    @classmethod
    def dispatch(cls, intent, after):
        """Called from Java for every incoming intent. True means a handler took it."""
        if not cls._handlers:
            return False
        wanted = "after" if after else "before"
        entries = sorted(
            (entry for entry in cls._handlers.values() if entry[0] == wanted),
            key=lambda entry: entry[1],
        )
        if not entries:
            return False

        try:
            action = intent.getAction()
            data = intent.getDataString()
            mime = intent.getType()
            flags = intent.getFlags()
            categories = intent.getCategories()
            categories = set(categories) if categories is not None else set()
        except Exception:
            return False

        parsed = cls.parse(data) if data else {}

        for _, _, info in entries:
            if not cls._matches(info, action, parsed, mime, flags, categories):
                continue
            try:
                if info.callback(intent) is True:
                    return True
            except Exception:
                import traceback
                from android_utils import log
                log("intent handler failed: %s" % traceback.format_exc())
        return False

    @classmethod
    def _matches(cls, info, action, parsed, mime, flags, categories):
        if info.action and info.action != action:
            return False
        if info.type and info.type != mime:
            return False
        if info.scheme and info.scheme != parsed.get("scheme"):
            return False
        if info.host and info.host != parsed.get("host"):
            return False
        if info.path and info.path != parsed.get("path"):
            return False
        for name in info.required_path_args_names or ():
            if name not in parsed:
                return False
        for flag in info.whitelist_flags or ():
            if not flags & flag:
                return False
        for flag in info.blacklist_flags or ():
            if flags & flag:
                return False
        for category in info.categories or ():
            if category not in categories:
                return False
        return True
