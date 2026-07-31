"""Files: where the app keeps things, and the small operations plugins need on them.

The directory getters return the app's own folders rather than anywhere on the device. A plugin
writing next to the user's downloads would survive uninstalling the app, which is not what either
of them wants.
"""

import os
from enum import Enum
from dataclasses import dataclass, field
from typing import Callable, List, Optional

from org.telegram.messenger import ApplicationLoader, FileLoader
from org.telegram.messenger.plugins import PrimePluginsController


def get_plugins_dir():
    return str(PrimePluginsController.pluginsDir().getAbsolutePath())


def get_cache_dir():
    return str(ApplicationLoader.applicationContext.getCacheDir().getAbsolutePath())


def get_files_dir():
    return str(ApplicationLoader.getFilesDirFixed().getAbsolutePath())


def _media_dir(kind):
    return str(FileLoader.getDirectory(kind).getAbsolutePath())


def get_images_dir():
    return _media_dir(FileLoader.MEDIA_DIR_IMAGE)


def get_videos_dir():
    return _media_dir(FileLoader.MEDIA_DIR_VIDEO)


def get_audios_dir():
    return _media_dir(FileLoader.MEDIA_DIR_AUDIO)


def get_documents_dir():
    return _media_dir(FileLoader.MEDIA_DIR_DOCUMENT)


def read_file(file_path):
    try:
        with open(file_path, "r", encoding="utf-8") as handle:
            return handle.read()
    except Exception:
        # None rather than an exception: a plugin reading its own cache file for the first time is
        # the normal case, not an error worth a traceback.
        return None


def read_file_bytes(file_path):
    try:
        with open(file_path, "rb") as handle:
            return handle.read()
    except Exception:
        return None


def write_file(file_path, content):
    ensure_dir_exists(os.path.dirname(file_path))
    with open(file_path, "w", encoding="utf-8") as handle:
        handle.write(content)


def write_file_bytes(file_path, content):
    ensure_dir_exists(os.path.dirname(file_path))
    with open(file_path, "wb") as handle:
        handle.write(content)


def delete_file(file_path):
    try:
        os.remove(file_path)
        return True
    except Exception:
        return False


def ensure_dir_exists(dir_path):
    if dir_path:
        os.makedirs(dir_path, exist_ok=True)


def list_dir(path, recursive=False, include_files=True, include_dirs=False, extensions=None):
    found = []
    suffixes = tuple("." + e.lstrip(".").lower() for e in extensions) if extensions else None
    for root, dirs, files in os.walk(path):
        if include_dirs:
            found.extend(os.path.join(root, name) for name in dirs)
        if include_files:
            for name in files:
                if suffixes and not name.lower().endswith(suffixes):
                    continue
                found.append(os.path.join(root, name))
        if not recursive:
            break
    return found


class staticproperty:
    def __init__(self, fget):
        self.fget = fget

    def __get__(self, obj, owner):
        return self.fget()


class FilesController:
    """Claiming a file extension so tapping such a file in a chat runs the plugin.

    Not available in this build - the app's file rows do not consult plugins yet. The class is here
    so a plugin importing it loads and its other features work.
    """

    @staticproperty
    def DIRECT_FILE_ICONS():
        return False

    @staticproperty
    def SUPPORT_ICONS():
        return False

    class Place(Enum):
        UNKNOWN = 1
        ChatActivity = 2
        FilteredSearchView = 3
        SharedMediaLayout = 4
        SearchDownloadsContainer = 5
        ChannelAdminLogActivity = 6

    @dataclass
    class FileInfo:
        ext: str
        on_click: Callable = field(compare=False, repr=False)
        whitelist_places: List["FilesController.Place"] = field(default_factory=list, kw_only=True)
        blacklist_places: List["FilesController.Place"] = field(default_factory=list, kw_only=True)
        get_icon: Optional[Callable] = field(default=None, compare=False, repr=False, kw_only=True)

    @dataclass
    class OnClickArgs:
        place: "FilesController.Place"
        file: object = field(compare=False, repr=False)
        file_name: str = ""
        message: object = field(default=None, compare=False, repr=False)
        activity: object = field(default=None, compare=False, repr=False)
        parent_fragment: object = field(default=None, compare=False, repr=False)

    class ExtensionAlreadyRegistered(Exception):
        def __init__(self, ext):
            super().__init__("extension already registered: %s" % ext)

    class ExtensionNotRegistered(Exception):
        def __init__(self, ext):
            super().__init__("extension not registered: %s" % ext)

    class SecretInvalid(Exception):
        def __init__(self, ext, secret):
            super().__init__("wrong secret for %s" % ext)

    @classmethod
    def register(cls, file_info):
        raise NotImplementedError("file hooks are not available in this build")

    @classmethod
    def unregister(cls, ext, secret):
        raise NotImplementedError("file hooks are not available in this build")
