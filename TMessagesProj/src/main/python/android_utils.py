"""The small Android things a plugin reaches for constantly: listeners, the UI thread, the log.

Every callback a plugin hands to Android has to cross into Java as a real interface implementation,
which is what ``dynamic_proxy`` is for. Wrapping them here rather than leaving each plugin to build
its own proxy is not only convenience: the wrappers swallow exceptions and log them, and a Python
exception escaping into a Java UI callback takes the app down with it.
"""

from java import dynamic_proxy, jclass
from java.lang import Runnable
from android.view import View

from org.telegram.messenger import AndroidUtilities, FileLog, NonIslandHelper, DrawerHelper, MainTabsHelper

_CLIPBOARD_TAG = "plugin"


class _Runnable(dynamic_proxy(Runnable)):
    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def run(self):
        try:
            self._fn()
        except Exception as e:
            log("callback failed: %r" % (e,))


class OnClickListener(dynamic_proxy(View.OnClickListener)):
    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def onClick(self, _view):
        try:
            self._fn(_view)
        except TypeError:
            # Plugins are written both ways - some take the view, some do not - and refusing the
            # zero-argument form would break half of them for no gain.
            self._fn()
        except Exception as e:
            log("onClick failed: %r" % (e,))


class OnLongClickListener(dynamic_proxy(View.OnLongClickListener)):
    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def onLongClick(self, _view):
        try:
            result = self._fn(_view)
        except TypeError:
            result = self._fn()
        except Exception as e:
            log("onLongClick failed: %r" % (e,))
            return False
        return True if result is None else bool(result)


def run_on_ui_thread(func, delay=0):
    """Runs on the main thread. Anything touching a View must go through here."""
    AndroidUtilities.runOnUIThread(_Runnable(func), int(delay))


def log(data):
    """Writes to the app's own log, where a plugin's output ends up next to the app's."""
    try:
        FileLog.d("[plugin] %s" % (data,))
    except Exception:
        pass


def copy_to_clipboard(text):
    AndroidUtilities.addToClipboard(str(text))


# ---------------------------------------------------------------------------
# interface mode
#
# PrimeGram ships more than one interface: the default modern look, an optional classic flat
# style, and an optional classic side-menu drawer that replaces the bottom tab bar. Each changes
# paddings, radii, and which views even exist. A plugin that only uses the official hook and menu
# APIs never needs to care - those already adapt themselves. A plugin that pokes at Telegram's own
# views directly (matching a view by index, hardcoding a pixel offset) does need to care, and
# should check here first rather than assume one fixed layout.

def is_classic_ui():
    """True when the user turned on the classic flat interface (pre-redesign look)."""
    return bool(NonIslandHelper.isEnabled())


def is_navigation_drawer():
    """True when the classic side-menu drawer replaces the bottom tab bar entirely."""
    return bool(DrawerHelper.isEnabled())


def is_bottom_tabs_hidden():
    """True when there is no bottom tab bar at all - either hidden directly, or implied by an
    active is_navigation_drawer()."""
    return bool(MainTabsHelper.isHidden())


def is_bottom_tabs_compact():
    """True when the bottom tab bar is showing icons only, with no labels."""
    return bool(MainTabsHelper.isCompact())


class _Resources:
    """``R.drawable.msg_delete`` and friends, resolved lazily against the app's own resources."""

    def __init__(self):
        self._r = None

    def __getattr__(self, item):
        if self._r is None:
            self._r = jclass("org.telegram.messenger.R")
        return getattr(self._r, item)

    def __call__(self, fn):
        # The SDK exposes R as something callable as well; keeping that shape means a plugin using
        # it either way imports and runs rather than failing at module level.
        return fn


R = _Resources()
