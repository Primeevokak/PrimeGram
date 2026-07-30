import enum
import threading
from _typeshed import Incomplete
from base_plugin import BasePlugin
from typing import Any, Callable

class DebuggerPlatform(enum.Enum):
    PyCharm = 'pycharm'
    VSCode = 'vscode'

class Callback(Incomplete):
    def __init__(self, fn: Callable[[Any], None]) -> None: ...
    def run(self, arg) -> None: ...

class DebuggerEventListener(BasePlugin):
    id: str
    name: str
    enabled: bool
    initialized: bool
    host: Incomplete
    port: Incomplete
    platform: Incomplete
    def __init__(self, host: str, port: int, platform: DebuggerPlatform) -> None: ...
    def on_app_event(self, event_type: str): ...

class DevServer:
    DEFAULT_HOST: str
    DEFAULT_PORT: int
    SOCKET_TIMEOUT: int
    BUFFER_SIZE: int
    PYCHARM_DEBUGGER_DIR: Incomplete
    @classmethod
    def start_server(cls, host: str = None, port: int = None) -> threading.Thread: ...
    @classmethod
    def stop_server(cls) -> bool: ...
    @classmethod
    def setup_remote_debugging(cls, host: str, port: int, platform: DebuggerPlatform) -> bool: ...
    @classmethod
    def stop_remote_debugging(cls, platform: DebuggerPlatform) -> bool: ...
