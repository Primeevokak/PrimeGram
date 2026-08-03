"""Who called. Used by plugins that behave differently depending on where they were invoked from."""

import sys


def get_caller(depth=1):
    """The frame ``depth`` levels above the caller, or None when the stack is shorter than that."""
    frame = sys._getframe(1)
    for _ in range(depth):
        if frame is None:
            return None
        frame = frame.f_back
    return frame


def get_caller_module(depth=1):
    frame = get_caller(depth + 1)
    return frame.f_globals.get("__name__") if frame is not None else None


def get_plugin_id():
    """The id of the plugin whose code is calling, or None from anywhere else.

    exteraGram puts this function here, in ``get_caller``, rather than at the top of
    ``extera_utils`` - so a plugin importing it from where their own SDK keeps it finds the same
    name in the same place. The implementation is shared with ``extera_utils.get_plugin_id``,
    which exists for plugins that import it from there instead; both spellings appear in the wild.
    """
    import extera_utils
    return extera_utils.get_plugin_id()
