import importlib.abc
from _typeshed import Incomplete

__version__: str
version: Incomplete
version_str = __version__
__beta__: bool
beta = __beta__

class SafeModeImporter(importlib.abc.MetaPathFinder, importlib.abc.Loader):
    IS_OLD_VERSION: bool
    I: bool
    E: bool
    def find_spec(self, fullname, path, target=...) -> None: ...
    @classmethod
    def c(cls) -> None: ...

Z: Incomplete

class X:
    BUILD_VERSION: Incomplete

def check_safemode(): ...
def setup_hooks() -> None: ...
def __start__(): ...
def __stop__() -> None: ...
