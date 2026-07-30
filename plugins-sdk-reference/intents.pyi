from dataclasses import dataclass, field
from typing import Callable

class IntentsManager:
    @dataclass
    class HandlerInfo:
        callback: Callable
        scheme: str | None = field(default=None)
        host: str | None = field(default=None)
        path: str | None = field(default=None)
        required_path_args_names: list[str] | None = field(default=None)
        priority: int | None = field(default=0)
        action: str | None = field(default=None)
        whitelist_flags: list[int] | None = field(default=None)
        blacklist_flags: list[int] | None = field(default=None)
        type: str | None = field(default=None)
        categories: list[str] | None = field(default=None)
    @dataclass
    class _HandlerInfo:
        handler_id: str
        callback: Callable = field(compare=False, repr=False)
        hook_type: str
        priority: int = ...
        scheme: str | None = ...
        host: str | None = ...
        path: str | None = ...
        path_regex: str | None = ...
        required_path_args_names: tuple[str, ...] = field(default_factory=tuple)
        action: str | None = ...
        whitelist_flags: tuple[int, ...] = field(default_factory=tuple)
        blacklist_flags: tuple[int, ...] = field(default_factory=tuple)
        type: str | None = ...
        categories: tuple[str, ...] = field(default_factory=tuple)
    @dataclass
    class HandlerHandle:
        handler_id: str
        def unhandle(self) -> None: ...
    class HandlerNotRegistered(Exception):
        def __init__(self, handler_id: str) -> None: ...
    @staticmethod
    def parse(url: str) -> dict[str, str]: ...
    @classmethod
    def new_global_before_handler(cls, info: HandlerInfo) -> IntentsManager.HandlerHandle: ...
    @classmethod
    def new_global_after_handler(cls, info: HandlerInfo) -> IntentsManager.HandlerHandle: ...
    @classmethod
    def unhandle(cls, handler_id: str): ...
