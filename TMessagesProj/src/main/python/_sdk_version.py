"""Which SDK a plugin is running against.

PrimeGram reports the exteraGram SDK version it implements, not a version of its own, because that
is the number plugins compare themselves against. A plugin that says it needs 1.4 is asking about
the shape of the API, and answering with a PrimeGram version number would make every such plugin
either refuse to run or run against an API it has no way to check.
"""

__version__ = "1.4.5.0"
version_str = __version__
__beta__ = False
beta = __beta__

#: What the app itself is, for anything that would rather ask about the client than the API.
HOST = "PrimeGram"


def _parse(text):
    parts = []
    for chunk in str(text).replace("-", ".").split("."):
        try:
            parts.append(int(chunk))
        except ValueError:
            break
    return tuple(parts)


version = _parse(__version__)


def check_safemode():
    """exteraGram disables plugins after a crash loop; here that decision lives in Java."""
    return False
