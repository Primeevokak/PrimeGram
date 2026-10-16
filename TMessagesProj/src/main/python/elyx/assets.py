"""``from elyx import assets`` - typed access to a packaged (.elyx) plugin's own ``res/``
directory (``refmap.yml``'s ``assets:`` field), one attribute per file - the name is the
filename without its extension (``assets.logo_sp`` for ``res/logo_sp.svg``) - and one
attribute per subdirectory, exposed as a namespace whose ``.files`` is a ``{filename:
asset}`` dict for files whose real name is not a valid Python identifier (most fonts:
``Circular-Bold.ttf``).

Confirmed by disassembling a real, popular .elyx's compiled bytecode (the same plugin
that surfaced ``elyx.settings``/``elyx.metainfo``): every asset object
supports ``.path_str`` (the file's real path), ``.content_bytes()`` (raw bytes), and -
for ``.svg`` files - ``.to_svg_drawable(width, height)``.

Caller's plugin id/root directory is recovered the same way ``elyx.strings`` recovers a
locale directory: registered by ``_prime_loader.load_elyx_plugin`` right before a
plugin's package executes, looked up per call via the *calling* frame's own package.
"""

import os
import sys

_MODULE_PREFIX = "prime_plugin_"

# package_name (the loader's synthetic "prime_plugin_<id>") -> res/ root directory.
_roots = {}


def register(package_name, root_dir):
    _roots[package_name] = root_dir


def unregister(package_name):
    _roots.pop(package_name, None)


class _Asset:
    """One file under a plugin's ``res/`` directory."""

    __slots__ = ("_path",)

    def __init__(self, path):
        self._path = path

    @property
    def path_str(self):
        return self._path

    def content_bytes(self):
        with open(self._path, "rb") as handle:
            return handle.read()

    def to_svg_drawable(self, width=None, height=None):
        """A real ``android.graphics.drawable.Drawable`` rendered from this file's SVG -
        only meaningful for a ``.svg`` asset. ``width``/``height`` default to 100px,
        matching how a missing size should not crash a caller that only cares about
        getting *something* drawable back."""
        from org.telegram.messenger import SvgHelper
        from android.graphics.drawable import BitmapDrawable
        from org.telegram.messenger import ApplicationLoader
        w = int(width) if width else 100
        h = int(height) if height else 100
        bitmap = SvgHelper.getBitmap(java_file(self._path), w, h, False)
        return BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap)


def java_file(path):
    from java.io import File
    return File(path)


class _Namespace:
    """A directory under ``res/`` - either the root itself or a subdirectory
    (``assets.fonts``) - lazily building ``_Asset``s for both attribute-style access
    (top-level files with identifier-safe names) and ``.files`` (every file in the
    directory, keyed by its real, possibly non-identifier, filename)."""

    def __init__(self, directory):
        self._dir = directory
        self._entries_cache = None

    def _entries(self):
        if self._entries_cache is None:
            entries = {}
            try:
                for name in os.listdir(self._dir):
                    if name.startswith("."):
                        continue
                    entries[name] = os.path.join(self._dir, name)
            except OSError:
                pass
            self._entries_cache = entries
        return self._entries_cache

    @property
    def files(self):
        result = {}
        for name, path in self._entries().items():
            if os.path.isfile(path):
                result[name] = _Asset(path)
        return result

    def __getattr__(self, name):
        for filename, path in self._entries().items():
            if os.path.isdir(path) and filename == name:
                return _Namespace(path)
            if os.path.isfile(path) and os.path.splitext(filename)[0] == name:
                return _Asset(path)
        raise AttributeError(name)


class _Assets:

    # Methods, not just the module-level functions above: the module replaces itself with this
    # instance below, so `import elyx.assets; elyx.assets.register(...)` lands here. Without these
    # the loader's call fell through to __getattr__, raised, was swallowed by its try/except, and
    # no plugin's res/ was ever registered (reSwaga: every assets.logo_* raised AttributeError).
    def register(self, package_name, root_dir):
        register(package_name, root_dir)

    def unregister(self, package_name):
        unregister(package_name)

    def _caller_plugin_id_and_root(self):
        # frame 0 is this method, frame 1 is __getattr__, frame 2 is the actual caller -
        # same two-hop skip elyx.strings/elyx.settings/elyx.metainfo all use.
        frame = sys._getframe(2)
        while frame is not None:
            package = frame.f_globals.get("__package__") or frame.f_globals.get("__name__")
            if package:
                top = package.split(".")[0]
                if top in _roots:
                    return _roots[top]
            frame = frame.f_back
        return None

    def __getattr__(self, name):
        root = self._caller_plugin_id_and_root()
        if root is None:
            raise AttributeError(name)
        return getattr(_Namespace(root), name)


# Same "a module that behaves like an object" trick elyx.strings/elyx.settings/
# elyx.metainfo use - replacing this module's own sys.modules entry means `from elyx
# import assets; assets.logo_sp` binds the instance.
sys.modules[__name__] = _Assets()
