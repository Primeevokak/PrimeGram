"""Fetching pure-Python packages from PyPI at run time.

A wheel for a pure-Python package is a zip of ``.py`` files and nothing else, so installing one is
downloading it, unzipping it somewhere, and putting that somewhere on ``sys.path``. No compiler, no
pip, no build step - which is why this works on a phone at all.

Packages with compiled extensions are refused, and the refusal is not a limitation we could lift by
trying harder: their ``.so`` is built for one ABI and one interpreter build, and Android will not
load a native library out of a directory the app can write to. Those have to be in the APK, which
is what the ``pip`` block in build.gradle is for. A plugin asking for one gets told which package
it is and that it needs a build of the app that carries it - a true and actionable message, rather
than a download that fails halfway.

Everything lands in one shared directory. Two plugins asking for the same package get one copy, and
the version stays whatever arrived first: resolving a genuine version conflict needs a solver, and
the honest alternative - installing two copies of one module name - cannot work in one interpreter.
"""

import json
import os
import re
import shutil
import sys
import tempfile
import urllib.request
import zipfile

from android_utils import log

_PYPI = "https://pypi.org/pypi/%s/json"
_STATE = "installed.json"
_USER_AGENT = "PrimeGram-plugins/1.0"

#: Wheel tags that mean "runs anywhere Python 3 runs".
_PURE_TAGS = ("-py3-none-any.whl", "-py2.py3-none-any.whl")

_libs_dir = None
_state = None


def libs_dir():
    """Where downloaded packages live: one directory beside the plugins themselves."""
    global _libs_dir
    if _libs_dir is None:
        from org.telegram.messenger.plugins import PrimePluginsController
        _libs_dir = os.path.join(str(PrimePluginsController.pluginsDir().getAbsolutePath()), "pylibs")
        os.makedirs(_libs_dir, exist_ok=True)
    return _libs_dir


def ensure_on_path():
    """Puts the download directory on the import path, after the built-in packages.

    After, not before, on purpose: a package compiled into the APK is the one that works, and a
    stray pure-Python copy of the same name downloaded later must not shadow it.
    """
    path = libs_dir()
    if path not in sys.path:
        sys.path.append(path)


def _state_path():
    return os.path.join(libs_dir(), _STATE)


def state():
    global _state
    if _state is None:
        try:
            with open(_state_path(), "r", encoding="utf-8") as handle:
                _state = json.load(handle)
        except Exception:
            _state = {}
        if not isinstance(_state, dict):
            _state = {}
    return _state


def _save_state():
    try:
        with open(_state_path(), "w", encoding="utf-8") as handle:
            json.dump(state(), handle)
    except Exception:
        pass


def installed():
    """Name to version, for everything this module has downloaded."""
    return dict(state())


def _normalise(name):
    return re.sub(r"[-_.]+", "-", name).strip().lower()


def _split_requirement(requirement):
    """``"tinydb>=4.0"`` into ``("tinydb", ">=4.0")``. Markers and extras are dropped."""
    text = requirement.split(";")[0].strip()
    text = re.sub(r"\[[^\]]*\]", "", text)
    match = re.match(r"^([A-Za-z0-9_.\-]+)\s*(.*)$", text)
    if not match:
        return None, ""
    return match.group(1), match.group(2).strip()


def is_available(name):
    """Whether this package can already be imported - bundled in the APK or downloaded before."""
    package = _normalise(name)
    if package in state():
        return True
    try:
        import importlib.metadata
        importlib.metadata.version(name)
        return True
    except Exception:
        return False


def _fetch(url, timeout=25):
    request = urllib.request.Request(url, headers={"User-Agent": _USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def _pick_wheel(metadata, constraint):
    """The newest release that ships a pure wheel, honouring a lower bound if one was given."""
    minimum = None
    if constraint.startswith(">="):
        minimum = constraint[2:].strip()
    elif constraint.startswith("=="):
        minimum = constraint[2:].strip()

    releases = metadata.get("releases") or {}
    versions = sorted(releases.keys(), key=_version_key, reverse=True)
    for version in versions:
        if constraint.startswith("==") and version != minimum:
            continue
        if minimum and not constraint.startswith("==") and _version_key(version) < _version_key(minimum):
            continue
        for entry in releases[version]:
            filename = entry.get("filename", "")
            if entry.get("packagetype") == "bdist_wheel" and filename.endswith(_PURE_TAGS):
                if entry.get("yanked"):
                    continue
                return version, entry.get("url")
    return None, None


def _version_key(version):
    parts = []
    for chunk in re.split(r"[._\-+]", str(version)):
        parts.append(int(chunk) if chunk.isdigit() else 0)
    return tuple(parts + [0] * (6 - len(parts)))[:6]


def install(requirement, seen=None, depth=0):
    """
    Downloads one package and its pure-Python dependencies.

    Returns ``(True, None)`` or ``(False, reason)``. Never raises: this runs while a user is
    watching an install sheet, and a traceback is not an answer to "did it work".
    """
    name, constraint = _split_requirement(requirement)
    if not name:
        return False, "непонятное имя пакета: %s" % requirement
    if depth > 8:
        return False, "слишком длинная цепочка зависимостей"

    seen = seen if seen is not None else set()
    package = _normalise(name)
    if package in seen:
        return True, None
    seen.add(package)

    if is_available(name):
        return True, None

    ensure_on_path()
    try:
        metadata = json.loads(_fetch(_PYPI % name))
    except Exception as e:
        return False, "не удалось получить сведения о «%s»: %s" % (name, e)

    version, url = _pick_wheel(metadata, constraint)
    if not url:
        # Either it has no wheel at all, or every wheel is platform-specific - which for our
        # purposes is the same answer, and the useful half of it is *why*.
        return False, ("«%s» содержит скомпилированный код и может быть только встроен в сборку "
                       "приложения" % name)

    try:
        payload = _fetch(url, timeout=90)
    except Exception as e:
        return False, "не удалось скачать «%s»: %s" % (name, e)

    try:
        with tempfile.NamedTemporaryFile(suffix=".whl", delete=False) as handle:
            handle.write(payload)
            wheel_path = handle.name
        with zipfile.ZipFile(wheel_path) as archive:
            names = archive.namelist()
            if any(entry.endswith(".so") or entry.endswith(".pyd") for entry in names):
                # A wheel tagged pure that carries a binary anyway. Rare, and unloadable here.
                return False, "«%s» содержит нативные файлы" % name
            archive.extractall(libs_dir())
            requires = _requires_from(archive, names)
    except Exception as e:
        return False, "не удалось распаковать «%s»: %s" % (name, e)
    finally:
        try:
            os.remove(wheel_path)
        except Exception:
            pass

    state()[package] = version
    _save_state()
    log("installed %s %s" % (package, version))

    for dependency in requires:
        ok, reason = install(dependency, seen, depth + 1)
        if not ok:
            # The dependency failed but the package itself is on disk. Reporting it rather than
            # rolling back: many declared dependencies are optional in practice, and a plugin that
            # works without one is better than a plugin that refused to install.
            log("dependency of %s not installed: %s" % (package, reason))
    return True, None


def _requires_from(archive, names):
    """Runtime dependencies from the wheel's own METADATA, skipping extras and other platforms."""
    required = []
    for entry in names:
        if not entry.endswith(".dist-info/METADATA"):
            continue
        try:
            text = archive.read(entry).decode("utf-8", "replace")
        except Exception:
            continue
        for line in text.splitlines():
            if not line.startswith("Requires-Dist:"):
                continue
            value = line[len("Requires-Dist:"):].strip()
            if ";" in value:
                marker = value.split(";", 1)[1]
                # "extra == ..." is an optional group nobody asked for; a platform marker that
                # names something other than Android is not for us either.
                if "extra" in marker:
                    continue
                if "sys_platform" in marker and "linux" not in marker:
                    continue
            required.append(value)
        break
    return required


def install_all(requirements):
    """Installs a list, returning the reasons for whichever ones failed."""
    problems = []
    for requirement in requirements or ():
        ok, reason = install(requirement)
        if not ok:
            problems.append(reason)
    return problems


def uninstall_unused(active_requirements):
    """Removes packages no installed plugin asks for any more."""
    wanted = set()
    for requirement in active_requirements or ():
        name, _ = _split_requirement(requirement)
        if name:
            wanted.add(_normalise(name))
    for package in list(state().keys()):
        if package in wanted:
            continue
        state().pop(package, None)
    _save_state()


def clear_all():
    """Throws the whole download directory away. The next install rebuilds what is needed."""
    try:
        shutil.rmtree(libs_dir(), ignore_errors=True)
    except Exception:
        pass
    global _libs_dir, _state
    _libs_dir = None
    _state = None
