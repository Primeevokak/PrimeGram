"""PrimeGram: proves the interpreter is alive, and nothing more.

Kept as a real module rather than a test so the first thing the engine ever does can be checked
on a device: if this import fails, the problem is the build, not the plugin someone just installed.
"""

import sys


def ping() -> str:
    return "python %d.%d ok" % (sys.version_info[0], sys.version_info[1])
