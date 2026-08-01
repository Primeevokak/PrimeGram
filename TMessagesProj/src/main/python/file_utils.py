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

    # ext -> (secret, FileInfo). One plugin per extension: two plugins both claiming ".zip"
    # would give a file two owners and the user no say in which one opens it.
    _registered = {}

    @classmethod
    def register(cls, file_info):
        """Claims an extension. Returns a secret that unregistering asks for."""
        ext = (file_info.ext or "").lower().lstrip(".")
        if not ext:
            raise ValueError("empty extension")
        if ext in cls._registered:
            raise cls.ExtensionAlreadyRegistered(ext)
        secret = "%s_%d" % (ext, id(file_info))
        cls._registered[ext] = (secret, file_info)
        cls._publish()
        return secret

    @classmethod
    def unregister(cls, ext, secret):
        ext = (ext or "").lower().lstrip(".")
        entry = cls._registered.get(ext)
        if entry is None:
            raise cls.ExtensionNotRegistered(ext)
        if entry[0] != secret:
            raise cls.SecretInvalid(ext, secret)
        cls._registered.pop(ext, None)
        cls._publish()

    @classmethod
    def forget_all(cls, infos):
        """Drops the given registrations without asking for secrets. Used when a plugin unloads."""
        for ext, (secret, info) in list(cls._registered.items()):
            if info in infos:
                cls._registered.pop(ext, None)
        cls._publish()

    @classmethod
    def _publish(cls):
        # Tells the Java side whether to bother asking at all. Opening a file is not a hot path,
        # but a check that costs one field read is still better than a call into Python.
        try:
            from org.telegram.messenger.plugins import PrimePluginHooks
            PrimePluginHooks.setFileHooks(bool(cls._registered))
        except Exception:
            pass

    @classmethod
    def dispatch(cls, path, file_name, message, activity, place_name):
        """Called from Java when the user taps a file. True means a plugin took it."""
        if not cls._registered:
            return False
        name = file_name or ""
        dot = name.rfind(".")
        ext = name[dot + 1:].lower() if dot >= 0 else ""
        entry = cls._registered.get(ext)
        if entry is None:
            return False

        info = entry[1]
        try:
            place = FilesController.Place[place_name]
        except Exception:
            place = FilesController.Place.UNKNOWN
        if info.whitelist_places and place not in info.whitelist_places:
            return False
        if info.blacklist_places and place in info.blacklist_places:
            return False

        from java.io import File as JavaFile
        args = FilesController.OnClickArgs(
            place=place,
            file=JavaFile(path),
            file_name=name,
            message=message,
            activity=activity,
        )
        try:
            info.on_click(args)
            return True
        except Exception:
            import traceback
            from android_utils import log
            log("file hook for .%s failed: %s" % (ext, traceback.format_exc()))
            # The plugin claimed the file and then broke. Opening it the ordinary way now would
            # be a surprise, so the tap simply does nothing and the log says why.
            return True
