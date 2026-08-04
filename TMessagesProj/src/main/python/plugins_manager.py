"""PrimeGram: compatibility shim for exteraGram's ``plugins_manager`` module.

Some plugins (cactuslib among them) read ``PluginsManager._plugins`` directly to enumerate
everything currently loaded, rather than going through the documented SDK. ``_loaded`` in
``_prime_loader`` is exactly that registry already - this aliases it rather than keeping a second
copy that could drift out of sync.
"""

import _prime_loader


class PluginsManager:
    _plugins = _prime_loader._loaded
