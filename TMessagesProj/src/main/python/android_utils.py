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


def safe_call(fn, *args, default=None, **kwargs):
    """Calls ``fn`` and returns ``default`` instead of letting an exception out.

    For code a plugin passes into Java itself - its own ``dynamic_proxy`` classes, handlers,
    anything Android calls back. An exception left to escape there lands on the UI thread and
    closes the whole app, not just the plugin. The error still goes to the log. ``fn`` being None
    is not an error: dialog buttons use that to mean "no action".
    """
    if fn is None:
        return default
    try:
        return fn(*args, **kwargs)
    except Exception as e:
        import traceback
        log("plugin callback failed: %r %s" % (e, traceback.format_exc()))
        return default


def _call_flexible(fn, *args, default=None):
    # Plugins pass callbacks written with and without the Android arguments; both are accepted.
    try:
        return fn(*args)
    except TypeError:
        return safe_call(fn, default=default)
    except Exception as e:
        log("plugin callback failed: %r" % (e,))
        return default


class OnTouchListener(dynamic_proxy(View.OnTouchListener)):
    """``fn(view, event)`` -> bool. A failure reports the event as not handled."""

    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def onTouch(self, view, event):
        return bool(_call_flexible(self._fn, view, event, default=False))


class OnKeyListener(dynamic_proxy(View.OnKeyListener)):
    """``fn(view, key_code, event)`` -> bool. A failure reports the key as not handled."""

    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def onKey(self, view, key_code, event):
        return bool(_call_flexible(self._fn, view, key_code, event, default=False))


class OnSeekBarChangeListener(dynamic_proxy(jclass("android.widget.SeekBar$OnSeekBarChangeListener"))):
    """Any of ``on_progress(seek_bar, progress, from_user)``, ``on_start(seek_bar)``,
    ``on_stop(seek_bar)`` - the ones left out are simply not called."""

    def __init__(self, on_progress=None, on_start=None, on_stop=None):
        super().__init__()
        self._progress = on_progress
        self._start = on_start
        self._stop = on_stop

    def onProgressChanged(self, seek_bar, progress, from_user):
        if self._progress is not None:
            _call_flexible(self._progress, seek_bar, int(progress), bool(from_user))

    def onStartTrackingTouch(self, seek_bar):
        if self._start is not None:
            _call_flexible(self._start, seek_bar)

    def onStopTrackingTouch(self, seek_bar):
        if self._stop is not None:
            _call_flexible(self._stop, seek_bar)


class TextWatcher(dynamic_proxy(jclass("android.text.TextWatcher"))):
    """``on_changed(text)`` after every edit, with the new text as a Python str."""

    def __init__(self, on_changed=None, before=None, during=None):
        super().__init__()
        self._after = on_changed
        self._before = before
        self._during = during

    def beforeTextChanged(self, s, start, count, after):
        if self._before is not None:
            _call_flexible(self._before, str(s), int(start), int(count), int(after))

    def onTextChanged(self, s, start, before, count):
        if self._during is not None:
            _call_flexible(self._during, str(s), int(start), int(before), int(count))

    def afterTextChanged(self, s):
        if self._after is not None:
            _call_flexible(self._after, str(s))


def get_context():
    """The application context - always there, even before any screen exists."""
    return jclass("org.telegram.messenger.ApplicationLoader").applicationContext


def get_activity():
    """The activity on screen, or None when there is none (a plugin may run in the background)."""
    try:
        fragment = jclass("org.telegram.ui.LaunchActivity").getLastFragment()
        return fragment.getParentActivity() if fragment is not None else None
    except Exception:
        return None


def run_on_ui_thread(func, delay=0):
    """Runs on the main thread. Anything touching a View must go through here."""
    AndroidUtilities.runOnUIThread(_Runnable(func), int(delay))


def log(data):
    """Writes to the app's own log, where a plugin's output ends up next to the app's."""
    try:
        FileLog.d("[plugin] %s" % (data,))
    except Exception:
        pass
    try:
        from org.telegram.messenger.plugins import PrimePluginLog
        PrimePluginLog.addAuto(str(data))
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


class Callback(dynamic_proxy(jclass("org.telegram.messenger.Utilities$Callback"))):
    """``Utilities.Callback`` around a Python function - the one-argument callback half of
    Telegram's own APIs take. A failure in ``fn`` is logged, not thrown back into Java."""

    def __init__(self, fn, *args, **kwargs):
        super().__init__()
        self._fn = fn
        self._args = args
        self._kwargs = kwargs

    def run(self, arg):
        return safe_call(self._fn, arg, *self._args, **self._kwargs)
