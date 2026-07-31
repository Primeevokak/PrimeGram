"""Odds and ends the SDK exposes under one name.

The only thing here with real content is :func:`get_plugin_id`, and it exists because a plugin's
helper code often needs to know which plugin it belongs to without being handed the plugin object -
a decorator, a module-level constant, a logging helper.
"""

import sys


def get_plugin_id():
    """The id of the plugin whose code is calling, or None from anywhere else.

    Answered by walking the call stack for a frame belonging to a plugin module, because that is
    the one question with an unambiguous answer: whoever is on the stack is who is asking. The
    marker is ``__prime_plugin_id__`` rather than ``__name__`` - a plugin file assigns ``__name__``
    itself, since that is where its display name comes from.
    """
    frame = sys._getframe(1)
    while frame is not None:
        plugin_id = frame.f_globals.get("__prime_plugin_id__")
        if plugin_id:
            return plugin_id
        frame = frame.f_back
    return None
