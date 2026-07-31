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
