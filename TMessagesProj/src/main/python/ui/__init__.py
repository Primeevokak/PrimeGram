"""Everything a plugin can put on screen.

Three submodules, and the split follows who owns the pixels. ``settings`` is pure data - a plugin
describes the rows it wants and the app draws them, which is what keeps a plugin's settings looking
like the rest of PrimeGram rather than like a plugin. ``alert`` and ``bulletin`` are the opposite:
they hand back the app's own dialog and toast machinery, because those are moments, not screens.
"""

from . import alert, bulletin, settings

__all__ = ["alert", "bulletin", "settings"]
