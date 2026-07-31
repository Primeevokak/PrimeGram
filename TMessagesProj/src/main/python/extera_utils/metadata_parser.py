"""Reading a plugin file's header.

exteraGram parses it in Python with ``ast``. PrimeGram already has a reader in Java, because the
install sheet has to name a file before the interpreter is started. Rather than keep two parsers
that can disagree - and they would, on exactly the odd headers where it matters - this one calls
that one.
"""

from org.telegram.messenger.plugins import PluginManifest


def get_metadata(file_path):
    """The header as a dict, or None when the file has no readable one."""
    try:
        with open(file_path, "r", encoding="utf-8") as handle:
            source = handle.read(64 * 1024)
    except Exception:
        return None
    try:
        manifest = PluginManifest.parse(source)
    except Exception:
        return None
    return {
        "id": manifest.id,
        "name": manifest.name,
        "description": manifest.description,
        "author": manifest.author,
        "version": manifest.version,
        "icon": manifest.icon(),
        "min_version": manifest.minVersion,
        "requirements": list(manifest.requirements or []),
    }
