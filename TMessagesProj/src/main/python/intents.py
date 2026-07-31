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

    @classmethod
    def new_global_before_handler(cls, info):
        raise NotImplementedError("intent hooks are not available in this build")

    @classmethod
    def new_global_after_handler(cls, info):
        raise NotImplementedError("intent hooks are not available in this build")

    @classmethod
    def unhandle(cls, handler_id):
        raise cls.HandlerNotRegistered(handler_id)
